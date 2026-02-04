package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class EmiyaShirou extends Ability {

    // 상태 관리 변수
    private boolean isChanting = false;
    private boolean isActive = false;
    private long lastEndedTime = 0;
    private static final long COOLDOWN_MS = 15000; // 15초

    private final String[] CHANT_LINES = {
            "§6I am the bone of my sword.",
            "§6Steel is my body, and fire is my blood.",
            "§6I have created over a thousand blades.",
            "§6Unknown to Death.",
            "§6Nor known to Life.",
            "§6Have withstood pain to create many weapons.",
            "§6Yet, those hands will never hold anything.",
            "§c§lSo as I pray, Unlimited Blade Works."
    };

    private static final String KEY_UBW_SWORD = "MOC_UBW_SWORD";
    private static final String KEY_SHOOT_SWORD = "MOC_SHOOT_SWORD";
    private static final String KEY_SWORD_MAT = "MOC_SWORD_MAT";

    // 꽂힌 검 관리 클래스
    private static class StuckSword {
        UUID ownerUUID;
        ItemDisplay visual;

        public StuckSword(UUID owner, ItemDisplay visual) {
            this.ownerUUID = owner;
            this.visual = visual;
        }
    }

    private final java.util.List<StuckSword> stuckSwords = new java.util.ArrayList<>();
    private BukkitTask pickupTask;

    public EmiyaShirou(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "012";
    }

    @Override
    public String getName() {
        return "에미야 시로";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§e전투 ● §f무한의 검제 (Infinite Sword Glitch)",
                "§7웅크리고 우클릭 시 고유 영창을 수행합니다.",
                "§7영창이 완료되면 전장에 수많은 검이 쏟아집니다.",
                "§7떨어진 검을 주워 투척할 수 있습니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 장비 제거: 철 칼
        p.getInventory().remove(Material.IRON_SWORD);

        // 자동 시작 제거: 유저가 직접 발동하도록 변경
        // startChant(p); // REMOVED
        p.sendMessage("§e[Tip] §f웅크리고(Shift) 우클릭하여 영창을 시작할 수 있습니다.");
    }

    // [중요] detailCheck 작성 규칙 준수
    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● 에미야 시로(FATE)");
        p.sendMessage("§f[웅크리고 우클릭] 시 고유 결계 '무한의 검제'를 전개하기 위한 영창을 시작합니다.");
        p.sendMessage("§f영창(8초)이 완료되면 하늘에서 수많은 검이 쏟아져 적들에게 강력한 피해를 입힙니다.");
        p.sendMessage("§f떨어진 검은 직접 주워 사용하거나 [우클릭]으로 투척하여 공격할 수 있습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 15초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 철 검");

        giveItem(p);
    }

    private void startChant(Player p) {
        if (isChanting || isActive) {
            p.sendMessage("§c이미 능력이 발동 중입니다.");
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastEndedTime < COOLDOWN_MS) {
            long remaining = (COOLDOWN_MS - (currentTime - lastEndedTime)) / 1000;
            p.sendMessage("§c쿨타임이 " + remaining + "초 남았습니다.");
            return;
        }

        isChanting = true;

        // 하늘 색 변경 (주황색 - 노을 서린 전장 느낌)
        p.setPlayerTime(12500, false);

        BukkitTask task = new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                // 사망 체크 또는 접속 종료 체크
                if (!p.isOnline() || p.isDead()) {
                    cancelChant(p);
                    this.cancel();
                    return;
                }

                if (index < CHANT_LINES.length) {
                    // 영창 메시지 출력
                    Bukkit.broadcastMessage(CHANT_LINES[index]);

                    // 사운드: 점점 고조되는 느낌으로 피치 조절
                    p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f,
                            0.6f + (index * 0.1f));

                    // [시각 효과] "Trace On" 마력 회로 (청록색) - 몸 주변에서 빛남
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0, 255, 255), 1.0f); // Cyan
                    // 플레이어 머리 위쪽에서 회로가 연결되는 듯한 효과
                    p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1.5, 0), 10, 0.5, 0.5, 0.5, dust);

                    index++;
                } else {
                    // 영창 끝 -> 무한의 검제 시작
                    startUBW(p);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1초마다 실행

        registerTask(p, task);
    }

    private void cancelChant(Player p) {
        if (p.isOnline()) {
            p.resetPlayerTime();
        }
        Bukkit.broadcastMessage("§c에미야 시로가 사망하여 영창이 취소되었습니다.");
        isChanting = false;
        isActive = false;
    }

    private void startUBW(Player p) {
        // 이미 죽었으면 실행 안함
        if (!p.isOnline() || p.isDead()) {
            cancelChant(p);
            return;
        }

        isChanting = false;
        isActive = true;

        p.sendMessage("§c[System] Unlimited Blade Works 발동!");

        // 발동 시 웅장한 사운드 레이어링
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 2.0f, 1.0f);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.8f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.5f); // 웅~ 하는 로우 톤

        if (pickupTask == null || pickupTask.isCancelled()) {
            startPickupTask();
        }

        // 30초간 실행
        BukkitTask ubwTask = new BukkitRunnable() {
            int tickMap = 0;
            final int DURATION_TICKS = 30 * 20;

            @Override
            public void run() {
                if (!p.isOnline() || tickMap >= DURATION_TICKS || p.isDead()) {
                    p.resetPlayerTime(); // 하늘 원래대로
                    isActive = false;
                    lastEndedTime = System.currentTimeMillis(); // 쿨타임 시작 타이머
                    p.sendMessage("§7무한의 검제가 종료되었습니다. (쿨타임 15초)");
                    this.cancel();
                    return;
                }

                // 2틱마다 검 소환 (초당 10개)
                if (tickMap % 2 == 0) {
                    spawnFallingSword(p);
                }

                tickMap += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        registerTask(p, ubwTask);
    }

    private void spawnFallingSword(Player owner) {
        Location targetLoc = null;

        // [스마트 타겟팅] 주변 30칸 내의 적 탐색
        List<Entity> nearby = owner.getNearbyEntities(30, 30, 30);
        List<LivingEntity> enemies = new java.util.ArrayList<>();

        for (Entity e : nearby) {
            if (e instanceof LivingEntity le && e != owner && !e.isDead()) {
                // 관전자 제외
                if (e instanceof Player && ((Player) e).getGameMode() == org.bukkit.GameMode.SPECTATOR)
                    continue;
                enemies.add(le);
            }
        }

        if (!enemies.isEmpty()) {
            // 랜덤 적 선택
            LivingEntity target = enemies.get((int) (Math.random() * enemies.size()));
            targetLoc = target.getLocation();

            // 너무 정확하면 피하기 힘드므로 약간의 오차(산포도) 적용 (반경 3블록)
            double offsetX = (Math.random() * 6) - 3;
            double offsetZ = (Math.random() * 6) - 3;
            targetLoc.add(offsetX, 0, offsetZ);
        } else {
            // 적이 없으면 기존대로 랜덤 폭격
            double range = 40.0;
            double dx = (Math.random() * range * 2) - range;
            double dz = (Math.random() * range * 2) - range;
            targetLoc = owner.getLocation().add(dx, 0, dz);
        }

        Location spawnLoc = targetLoc.clone();
        // 하늘 높이 설정 (조금 더 높게)
        spawnLoc.setY(spawnLoc.getWorld().getHighestBlockYAt(spawnLoc) + 40);

        // [시각 효과] Trace On - 투영 시작 (청록색)
        // 검이 생성되기 전 마력 회로가 모이는 연출
        Particle.DustOptions traceColor = new Particle.DustOptions(Color.fromRGB(0, 255, 255), 1.5f);
        spawnLoc.getWorld().spawnParticle(Particle.DUST, spawnLoc, 5, 0.2, 0.2, 0.2, traceColor);
        // 투영되는 맑은 소리
        spawnLoc.getWorld().playSound(spawnLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 3.0f, 2.0f);

        // 화살 소환 (물리 엔진 및 데미지 판정용)
        Arrow arrow = owner.getWorld().spawn(spawnLoc, Arrow.class);
        arrow.setShooter(owner);
        arrow.setPierceLevel(127);
        arrow.setDamage(5.0);
        arrow.setVelocity(new Vector(0, -3.5, 0)); // 낙하 속도 증가
        arrow.setMetadata(KEY_UBW_SWORD, new FixedMetadataValue(plugin, owner.getUniqueId().toString()));
        arrow.setSilent(true);
        arrow.setCritical(true);

        // 시각 효과: ItemDisplay가 화살에 탑승
        ItemDisplay itemDisplay = owner.getWorld().spawn(spawnLoc, ItemDisplay.class);

        // 검 종류 랜덤 설정 (네더라이트, 다이아, 철, 금)
        Material[] swords = { Material.NETHERITE_SWORD, Material.DIAMOND_SWORD, Material.IRON_SWORD,
                Material.GOLDEN_SWORD };
        Material randomSword = swords[(int) (Math.random() * swords.length)];
        itemDisplay.setItemStack(new ItemStack(randomSword));

        // 메타데이터에 재질 저장 (나중에 꽂힐 때 사용)
        arrow.setMetadata(KEY_SWORD_MAT, new FixedMetadataValue(plugin, randomSword.name()));

        // 랜덤 각도 설정
        float randomZ = (float) Math.toRadians((Math.random() * 40) - 20); // 좌우 기울기
        float randomY = (float) Math.toRadians(Math.random() * 360); // 회전

        itemDisplay.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(
                        new Quaternionf().rotateX((float) Math.toRadians(180)).rotateZ(randomZ).rotateY(randomY)),
                new Vector3f(1.5f, 1.5f, 1.5f),
                new AxisAngle4f()));

        itemDisplay.setGlowing(true); // 마법적인 느낌

        arrow.addPassenger(itemDisplay);

        registerSummon(owner, arrow);
        registerSummon(owner, itemDisplay);

        // [시각 효과] 낙하 궤적 (청록색 마법 라인 + 약간의 스파크)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (arrow.isDead() || arrow.isOnGround()) {
                    this.cancel();
                    return;
                }
                // 청록색 궤적
                arrow.getWorld().spawnParticle(Particle.DUST, arrow.getLocation(), 1, 0, 0, 0, traceColor);
                // 가끔 튀는 전기 스파크 (마력 불안정/강력함 표현)
                if (Math.random() < 0.3) {
                    arrow.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, arrow.getLocation(), 1, 0.1, 0.1, 0.1, 0.1);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // 매 틱마다 부드럽게
    }

    // === 이벤트 리스너 ===

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (e.getEntity() instanceof Arrow arrow
                && (arrow.hasMetadata(KEY_UBW_SWORD) || arrow.hasMetadata(KEY_SHOOT_SWORD))) {
            String key = arrow.hasMetadata(KEY_UBW_SWORD) ? KEY_UBW_SWORD : KEY_SHOOT_SWORD;

            // 재질 확인
            Material displayMat = Material.IRON_SWORD;
            if (arrow.hasMetadata(KEY_SWORD_MAT)) {
                try {
                    displayMat = Material.valueOf(arrow.getMetadata(KEY_SWORD_MAT).get(0).asString());
                } catch (IllegalArgumentException ignored) {
                }
            }

            // 기존 낙하 비주얼 제거
            if (!arrow.getPassengers().isEmpty()) {
                for (Entity passenger : arrow.getPassengers()) {
                    // 메타데이터가 없는 경우 패신저에서 재질 추론 (백업 로직)
                    if (!arrow.hasMetadata(KEY_SWORD_MAT) && passenger instanceof ItemDisplay display
                            && display.getItemStack() != null) {
                        displayMat = display.getItemStack().getType();
                    }
                    passenger.remove();
                }
            }

            // 블록에 꽂혔을 때 (아이템화 말고 꽂힌 상태 유지)
            if (e.getHitBlock() != null) {
                Location dropLoc = arrow.getLocation();

                // [시각 효과] 착지 임팩트
                // 1. 묵직한 금속 타격음 + 폭발음 레이어
                dropLoc.getWorld().playSound(dropLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 1.5f);
                dropLoc.getWorld().playSound(dropLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2.0f); // 작고 날카로운 폭발음

                // 2. 파티클 임팩트
                dropLoc.getWorld().spawnParticle(Particle.FLASH, dropLoc, 1); // 번쩍!
                dropLoc.getWorld().spawnParticle(Particle.BLOCK, dropLoc, 20, 0.3, 0.3, 0.3,
                        e.getHitBlock().getBlockData()); // 파편

                // 꽂힐 위치 계산 (바닥보다 살짝 위)
                Location stickLoc = dropLoc.clone().add(0, 0.7, 0);

                float randomY = (float) Math.toRadians(Math.random() * 360);
                float randomTilt = (float) Math.toRadians((Math.random() * 30) - 15);

                // 꽂힌 검 생성
                ItemDisplay newDisplay = dropLoc.getWorld().spawn(stickLoc, ItemDisplay.class);
                newDisplay.setItemStack(new ItemStack(displayMat));
                // 약간 비스듬하게 꽂힌 연출
                newDisplay.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(new Quaternionf().rotateZ((float) Math.toRadians(135)).rotateY(randomY)
                                .rotateX(randomTilt)),
                        new Vector3f(1.5f, 1.5f, 1.5f),
                        new AxisAngle4f()));
                newDisplay.setGlowing(true);

                UUID ownerUUID = UUID.fromString(arrow.getMetadata(key).get(0).asString());
                stuckSwords.add(new StuckSword(ownerUUID, newDisplay));

                arrow.remove();
            }
            // 엔티티 히트는 EntityDamageByEntityEvent에서 처리됨 (화살 관통 속성 때문)
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Arrow arrow) {
            // Case 1: 낙하하는 검
            if (arrow.hasMetadata(KEY_UBW_SWORD)) {
                String ownerUUIDStr = arrow.getMetadata(KEY_UBW_SWORD).get(0).asString();

                // 5칸 = 10.0 데미지
                e.setDamage(10.0);

                // 시전자 면역
                if (e.getEntity().getUniqueId().toString().equals(ownerUUIDStr)) {
                    e.setCancelled(true);
                    return;
                }
            }

            // Case 2: 직접 투척한 검
            if (arrow.hasMetadata(KEY_SHOOT_SWORD)) {
                String ownerUUIDStr = arrow.getMetadata(KEY_SHOOT_SWORD).get(0).asString();
                e.setDamage(10.0);

                if (e.getEntity().getUniqueId().toString().equals(ownerUUIDStr)) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent e) {
        // 아이템 줍기 방지 (ItemDisplay 방식이라 필요 없을 수 있으나 안전장치)
        if (e.getEntity() instanceof Player p && e.getItem().hasMetadata(KEY_UBW_SWORD)) {
            e.setCancelled(true);
            e.getItem().remove();
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 능력 보유 확인 (AbilityManager를 통해)
        if (!me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode())) {
            return;
        }

        if (e.getAction().toString().contains("RIGHT_CLICK")) {
            // 1. Shift + 우클릭 -> 영창 시작 (발동)
            if (p.isSneaking()) {
                startChant(p);
                return;
            }

            // 2. 그냥 우클릭 -> 검 투척 (검을 들고 있을 때만)
            if (p.getInventory().getItemInMainHand().getType().name().contains("SWORD")) {
                ItemStack handItem = p.getInventory().getItemInMainHand();

                // 아이템 소모
                handItem.setAmount(handItem.getAmount() - 1);

                // 화살 발사 (검으로 위장)
                Arrow arrow = p.launchProjectile(Arrow.class);
                arrow.setDamage(5.0); // 기본값
                arrow.setMetadata(KEY_SHOOT_SWORD, new FixedMetadataValue(plugin, p.getUniqueId().toString()));
                arrow.setPierceLevel(0); // 투척은 관통 불가

                Material mat = handItem.getType();
                arrow.setMetadata(KEY_SWORD_MAT, new FixedMetadataValue(plugin, mat.name()));

                arrow.setDamage(getSwordDamage(mat));

                // 비주얼 생성 (1틱 딜레이로 화살 안정화 대기)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (arrow.isDead() || !arrow.isValid())
                            return;

                        ItemDisplay itemDisplay = p.getWorld().spawn(arrow.getLocation(), ItemDisplay.class);
                        itemDisplay.setItemStack(new ItemStack(mat));

                        // 진행 방향으로 검 끝이 향하도록 회전 (-90도 X축 회전)
                        itemDisplay.setTransformation(new Transformation(
                                new Vector3f(0, 0, 0),
                                new AxisAngle4f(new Quaternionf().rotateX((float) Math.toRadians(-90))),
                                new Vector3f(1.2f, 1.2f, 1.2f),
                                new AxisAngle4f()));
                        itemDisplay.setGlowing(true);

                        arrow.addPassenger(itemDisplay);
                        registerSummon(p, itemDisplay);
                    }
                }.runTaskLater(plugin, 1L);

                p.playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.5f);
                p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.5f, 2.0f); // 투척 시 마법음

                registerSummon(p, arrow);
            }
        }
    }

    private double getSwordDamage(Material mat) {
        return switch (mat) {
            case NETHERITE_SWORD -> 8.0;
            case DIAMOND_SWORD -> 7.0;
            case IRON_SWORD -> 6.0;
            case STONE_SWORD -> 5.0;
            case WOODEN_SWORD -> 4.0;
            case GOLDEN_SWORD -> 4.0;
            default -> 5.0;
        };
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode())) {
            cancelChant(p); // 사망 시 영창 취소
            p.resetPlayerTime();
        }
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        cancelChant(p); // 상태 초기화
        p.resetPlayerTime();

        // 1. 실행 중인 태스크 강제 취소
        if (activeTasks.containsKey(p.getUniqueId())) {
            List<BukkitTask> tasks = activeTasks.get(p.getUniqueId());
            if (tasks != null) {
                for (BukkitTask t : new java.util.ArrayList<>(tasks)) {
                    if (t != null && !t.isCancelled()) {
                        t.cancel();
                    }
                }
            }
            activeTasks.remove(p.getUniqueId());
        }

        // 플레이어 소유의 꽂힌 검 제거
        java.util.Iterator<StuckSword> it = stuckSwords.iterator();
        while (it.hasNext()) {
            StuckSword sword = it.next();
            if (sword.ownerUUID.equals(p.getUniqueId())) {
                if (sword.visual != null && !sword.visual.isDead()) {
                    sword.visual.remove();
                }
                it.remove();
            }
        }

        // 검이 없으면 줍기 태스크도 종료
        if (stuckSwords.isEmpty() && pickupTask != null && !pickupTask.isCancelled()) {
            pickupTask.cancel();
            pickupTask = null;
        }
    }

    @Override
    public void reset() {
        super.reset();
        isChanting = false;
        isActive = false;

        // 게임 초기화 시 모든 검 제거
        for (StuckSword sword : stuckSwords) {
            if (sword.visual != null && !sword.visual.isDead()) {
                sword.visual.remove();
            }
        }
        stuckSwords.clear();

        if (pickupTask != null && !pickupTask.isCancelled()) {
            pickupTask.cancel();
            pickupTask = null;
        }
    }

    private void startPickupTask() {
        pickupTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (stuckSwords.isEmpty())
                    return;

                java.util.Iterator<StuckSword> it = stuckSwords.iterator();
                while (it.hasNext()) {
                    StuckSword sword = it.next();
                    if (sword.visual == null || sword.visual.isDead()) {
                        it.remove();
                        continue;
                    }

                    if (sword.visual.getWorld() == null) {
                        it.remove();
                        continue;
                    }

                    // 근처 플레이어 감지 (검 줍기)
                    for (Player p : sword.visual.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(sword.visual.getLocation()) <= 2.25) { // 1.5블록 이내
                            // 줍기 성공
                            giveSword(p, sword);
                            sword.visual.remove();

                            // 획득 이펙트
                            p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1f, 1.5f);
                            p.getWorld().spawnParticle(Particle.WAX_OFF,
                                    sword.visual.getLocation().add(0, 0.5, 0), 5, 0.2, 0.2, 0.2, 0.1);
                            it.remove();
                            break;
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 4L); // 0.2초마다 체크
    }

    private void giveSword(Player p, StuckSword sword) {
        if (p.getInventory().firstEmpty() == -1) {
            p.sendMessage("§c인벤토리가 가득 차 검을 주울 수 없습니다.");
            return;
        }

        ItemStack item;
        // 본인 검은 다이아몬드, 남의 검은 돌
        if (p.getUniqueId().equals(sword.ownerUUID)) {
            item = new ItemStack(Material.DIAMOND_SWORD);
        } else {
            item = new ItemStack(Material.STONE_SWORD);
        }

        p.getInventory().addItem(item);
    }
}
