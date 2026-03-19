package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Ghost extends Ability {

    private final String CODE = "076";
    private final String NAME = "고스트";
    private final int COOLDOWN = 60;

    // 핵 신호기 관련 블록 추적 (위치 좌표 정보 저장. 파괴 시 스케줄러를 취소하기 위함)
    private final Map<UUID, Location> activeBeacons = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> nukeTasks = new ConcurrentHashMap<>();

    // 핵 신호기 구분을 위한 네임스페이스 키 (가짜 블록으로 만들 수 있지만 편의를 위해 아이템에 키를 할당해 검사)
    private final NamespacedKey nukeBeaconKey;

    public Ghost(MocPlugin plugin) {
        super(plugin);
        nukeBeaconKey = new NamespacedKey(plugin, "nuke_beacon_076");
    }

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§a전투 ● 고스트(스타크래프트)",
                "§f전술 핵 공격을 유도합니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§a전투 ● 고스트(스타크래프트)");
        p.sendMessage("§f전술 핵 공격을 유도합니다.");
        p.sendMessage("§f ");
        p.sendMessage("§f핵 신호기로 블럭을 때리면 해당 블럭을 핵 신호기로 변경합니다.");
        p.sendMessage("§f30초 뒤에 해당 위치에 전술 핵을 떨어트립니다.");
        p.sendMessage("§f30초 전에 핵 신호기가 부셔지면 핵 공격이 취소됩니다.");
        p.sendMessage("§f전술 핵은 떨어지는 동안 블럭을 관통합니다.");
        p.sendMessage("§f전술 핵 데미지는 폭심지로부터 거리에 따라 999 / 49 / 29이 가해지며 넉백을 동반합니다.");
        p.sendMessage("§f ");
        p.sendMessage("§f쿨타임 : " + COOLDOWN + "초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 핵 신호기");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void giveItem(Player p) {
        ItemStack beacon = new ItemStack(Material.RED_STAINED_GLASS);
        ItemMeta meta = beacon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c핵 신호기");
            meta.setLore(Arrays.asList(
                    "§f블럭을 좌클릭하여 설치합니다.",
                    "§f(우클릭으로 설치 불가)"));
            meta.getPersistentDataContainer().set(nukeBeaconKey, PersistentDataType.BYTE, (byte) 1);
            beacon.setItemMeta(meta);
        }
        p.getInventory().addItem(beacon);
    }

    @Override
    public void reset() {
        super.reset();
        for (BukkitTask task : nukeTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        for (Location loc : activeBeacons.values()) {
            // 게임 종료 시에도 신호기 블럭을 원상 복구 시킬 필요가 있다면 원래 블럭 저장이 필요하나,
            // 맵을 리셋하는 경우가 많으므로 단순 AIR 처리로 마무리함.
            // 필요 시 Arena reset 로직에 맡김.
            if (loc.getBlock().getType() == Material.RED_STAINED_GLASS) {
                loc.getBlock().setType(Material.AIR);
            }
        }
        nukeTasks.clear();
        activeBeacons.clear();
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        UUID uuid = p.getUniqueId();
        BukkitTask task = nukeTasks.remove(uuid);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        Location loc = activeBeacons.remove(uuid);
        if (loc != null && loc.getBlock().getType() == Material.RED_STAINED_GLASS) {
            loc.getBlock().setType(Material.AIR);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (isSilenced(p))
            return;

        // 아이템 확인
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta())
            return;
        if (!item.getItemMeta().getPersistentDataContainer().has(nukeBeaconKey, PersistentDataType.BYTE))
            return;

        // 우클릭 설치 방지
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            return;
        }

        // 좌클릭 발동 (블럭 타격)
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            // 코드 및 권한 확인
            if (!AbilityManager.getInstance().hasAbility(p, getCode()))
                return;
            if (p.getGameMode() == GameMode.SPECTATOR)
                return;

            // 쿨타임 및 진행중 확인
            UUID uuid = p.getUniqueId();
            if (activeBeacons.containsKey(uuid)) {
                p.sendMessage("§c이미 핵 신호기가 활성화되어 있습니다!");
                e.setCancelled(true);
                return;
            }

            if (!checkCooldown(p)) {
                e.setCancelled(true);
                return;
            }

            Block clickedBlock = e.getClickedBlock();
            if (clickedBlock == null)
                return;

            e.setCancelled(true); // 채굴 모션 방지

            // 쿨타임 부여 (대기시간 15초 + 30초 = 45초)
            if (p.getGameMode() != GameMode.CREATIVE) {
                setCooldown(p, COOLDOWN);
            }

            // 블럭을 빨간색 색유리로 변경하고 기록
            Location beaconLoc = clickedBlock.getLocation();
            Material oldMat = clickedBlock.getType(); // 원상태 복구용
            clickedBlock.setType(Material.RED_STAINED_GLASS);
            activeBeacons.put(uuid, beaconLoc);

            // 대사 방송
            Bukkit.broadcastMessage("§a고스트 : §fNuclear Launch Detected.");
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.5f); // 웅장한 알람 느낌 대체

            // 15초 카운트다운 스케줄러 등록
            BukkitTask task = new BukkitRunnable() {
                int tick = 0;
                final int maxTick = 30 * 20; // 600틱 (30초)

                @Override
                public void run() {
                    if (tick >= maxTick) {
                        launchNuke(p, uuid, beaconLoc);
                        this.cancel();
                        return;
                    }

                    // 1초 단위로 액션바 메시지 출력 (카운트다운)
                    if (tick % 20 == 0) {
                        int secondsLeft = (maxTick - tick) / 20;
                        p.sendActionBar(net.kyori.adventure.text.Component
                                .text("§c[경고] §f전술 핵 강하까지... §e" + secondsLeft + "초"));
                    }

                    // 파티클 등으로 신호기 강조
                    if (tick % 10 == 0) {
                        beaconLoc.getWorld().spawnParticle(Particle.DUST, beaconLoc.clone().add(0.5, 1.2, 0.5), 5,
                                0.2, 0.2, 0.2, new Particle.DustOptions(Color.RED, 1.5f));
                        beaconLoc.getWorld().playSound(beaconLoc, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, 0.5f);
                    }
                    tick++;
                }
            }.runTaskTimer(plugin, 0L, 1L);

            nukeTasks.put(uuid, task);
            registerTask(p, task); // 부모 클래스의 activeTasks에도 등록하여 안전성 확보
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.RED_STAINED_GLASS)
            return;

        Location targetLoc = b.getLocation();

        // 부서진 블럭이 활성화된 핵 신호기 중 하나인지 검사
        for (Map.Entry<UUID, Location> entry : activeBeacons.entrySet()) {
            UUID ownerUuid = entry.getKey();
            Location beaconLoc = entry.getValue();

            // 월드와 좌표 완벽 일치 검사
            if (beaconLoc.getWorld().equals(targetLoc.getWorld()) &&
                    beaconLoc.getBlockX() == targetLoc.getBlockX() &&
                    beaconLoc.getBlockY() == targetLoc.getBlockY() &&
                    beaconLoc.getBlockZ() == targetLoc.getBlockZ()) {

                // 핵 신호기 파괴 됨!
                Bukkit.broadcastMessage("§a고스트 : §f핵 공격이 취소되었습니다.");

                // 스케줄러 취소
                BukkitTask task = nukeTasks.remove(ownerUuid);
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                }
                activeBeacons.remove(ownerUuid);

                // 주의: 쿨타임은 이미 돌아가고 있으므로 감소시키거나 초기화시키지 않음 (리스크)
                break;
            }
        }
    }

    private void launchNuke(Player owner, UUID ownerUuid, Location beaconLoc) {
        // 타이머 및 목록 청소
        nukeTasks.remove(ownerUuid);
        activeBeacons.remove(ownerUuid);
        World w = beaconLoc.getWorld();
        if (w == null)
            return;

        // 핵 신호기 부수기
        if (beaconLoc.getBlock().getType() == Material.RED_STAINED_GLASS) {
            beaconLoc.getBlock().setType(Material.AIR);
            w.spawnParticle(Particle.BLOCK, beaconLoc.clone().add(0.5, 0.5, 0.5), 30, 0.5, 0.5, 0.5,
                    Material.RED_STAINED_GLASS.createBlockData());
        }

        // 전술핵 소환 위치 (Y + 30)
        Location spawnLoc = beaconLoc.clone().add(0.5, 30.0, 0.5);

        // BlockDisplay로 거대한 TNT 생성
        BlockDisplay nukeDisplay = (BlockDisplay) w.spawnEntity(spawnLoc, EntityType.BLOCK_DISPLAY);
        nukeDisplay.setBlock(Material.TNT.createBlockData());
        // 변환 매트릭스 설정 (스케일 15배, 가운데 정렬을 위해 중심축 이동)
        // 기본적으로 피봇이 0,0,0(꼭짓점)이므로, -7.5, -7.5, -7.5 만큼 translation 적용
        nukeDisplay.setTransformation(new Transformation(
                new Vector3f(-7.5f, 0f, -7.5f), // translation
                new Quaternionf(), // left rotation
                new Vector3f(15.0f, 15.0f, 15.0f), // scale
                new Quaternionf() // right rotation
        ));

        registerSummon(owner, nukeDisplay); // 삭제 보장

        // 알람음과 함께 거대 핵탄두 하강
        w.playSound(spawnLoc, Sound.ENTITY_ENDER_DRAGON_FLAP, 3.0f, 0.5f);

        BukkitTask fallingTask = new BukkitRunnable() {
            int ticks = 0;
            Location currentLoc = spawnLoc.clone();
            final double targetY = beaconLoc.getY();
            final double fallSpeed = 1.0; // 틱당 1 블럭 (1.5초만에 떨어짐)

            @Override
            public void run() {
                if (currentLoc.getY() <= targetY || ticks > 60) { // 타겟 Y좌표 도달 또는 안전장치 3초 후
                    nukeDisplay.remove();
                    explodeNuke(owner, currentLoc.clone());
                    this.cancel();
                    return;
                }

                currentLoc.subtract(0, fallSpeed, 0);
                nukeDisplay.teleport(currentLoc); // 텔레포트를 통한 이동. 관통을 위해 물리어트리뷰트 미사용
                w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, currentLoc.clone().add(0, 7.5, 0), 20, 3.0, 0.0, 3.0,
                        0.0);

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        registerTask(owner, fallingTask);
    }

    private void explodeNuke(Player owner, Location centerLoc) {
        World w = centerLoc.getWorld();
        if (w == null)
            return;

        // 거대한 폭발 И펙트 (블록 파괴는 없이 이펙트만 크게)
        w.spawnParticle(Particle.EXPLOSION_EMITTER, centerLoc, 5); // EXPLOSION_EMITTER는 그 자체로 매우 큼
        w.playSound(centerLoc, Sound.ENTITY_GENERIC_EXPLODE, 5.0f, 0.5f);

        // 추가적인 파편 연출
        w.spawnParticle(Particle.LAVA, centerLoc, 500, 10.0, 10.0, 10.0, 1.0);
        w.spawnParticle(Particle.FLAME, centerLoc, 1000, 15.0, 15.0, 15.0, 0.5);

        // 데미지 계산 및 넉백
        // 반경 60 이내의 엔티티 로드
        Collection<Entity> targets = w.getNearbyEntities(centerLoc, 60.0, 60.0, 60.0);

        for (Entity e : targets) {
            if (!(e instanceof LivingEntity target))
                continue;

            // 관전자 등 예외 처리
            if (target instanceof Player t && t.getGameMode() == GameMode.SPECTATOR)
                continue;
            if (target instanceof ArmorStand || target.hasMetadata("MOC_NPC"))
                continue;

            double distance = target.getLocation().distance(centerLoc);

            // 안전장치 - Y축 거리도 동일하게 계산되므로 구체(Sphere) 형태 반경 적용
            double damageAmount = 0.0;
            double knockbackPower = 0.0;

            if (distance <= 20.0) {
                // 1단계: 0~20 블록 (폭심지) - 999 데미지
                damageAmount = 999.0;
                knockbackPower = 1.0;
            } else if (distance <= 40.0) {
                // 2단계: 20~40 블록 - 49 데미지 및 강한 넉백
                damageAmount = 49.0;
                knockbackPower = 3.0;
            } else if (distance <= 60.0) {
                // 3단계: 40~60 블록 - 29 데미지 및 적당한 넉백
                damageAmount = 29.0;
                knockbackPower = 1.5;
            } else {
                continue; // 거리 밖은 무시
            }

            // 고정 피해 및 킬 귀속 처리
            if (damageAmount >= target.getHealth()) {
                // 즉사 판정
                target.setMetadata("MOC_LastKiller", new FixedMetadataValue(plugin, owner.getUniqueId().toString()));
                target.setHealth(0);
            } else {
                target.damage(damageAmount, owner);

                // 넉백 벡터 계산 (폭심지로부터 멀어지는 방향)
                Vector pushDir = target.getLocation().toVector().subtract(centerLoc.toVector());
                if (pushDir.lengthSquared() > 0) {
                    pushDir.normalize().multiply(knockbackPower).setY(pushDir.getY() > 0 ? pushDir.getY() + 0.5 : 0.5); // 약간
                                                                                                                        // 위로
                                                                                                                        // 띄움
                    target.setVelocity(target.getVelocity().add(pushDir));
                }
            }
        }
    }
}
