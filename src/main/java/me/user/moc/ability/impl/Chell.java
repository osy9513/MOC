package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;

public class Chell extends Ability {

    // 플레이어 UUID -> 포탈건 고유 UUID(String) 기준으로 관리 단위 변경
    // 포탈 활성화 상태(파란색=true / 주황색=false) 저장
    private final Map<String, Boolean> isBluePortalNext = new HashMap<>();

    // 각 포탈건별 활성화된 포탈 목록. Map<String, List<PortalData>> (0: 파랑, 1: 주황 등)
    private final Map<String, PortalData> bluePortals = new HashMap<>();
    private final Map<String, PortalData> orangePortals = new HashMap<>();

    // 무한 워프 방지 (이건 대상 생명체 UUID 기준이므로 그대로 맵 유지)
    private final Map<UUID, Long> warpCooldowns = new HashMap<>();

    // 포탈건별 파티클/워프 감지 스케줄러 관리 (Key: 포탈건 UUID)
    private final Map<String, BukkitRunnable> portalTaskMap = new HashMap<>();

    private NamespacedKey portalTypeKey;
    private NamespacedKey portalOwnerKey;
    private NamespacedKey portalGunIdKey;

    public Chell(MocPlugin plugin) {
        super(plugin);
        this.portalTypeKey = new NamespacedKey(plugin, "portalType");
        this.portalOwnerKey = new NamespacedKey(plugin, "portalOwner");
        this.portalGunIdKey = new NamespacedKey(plugin, "portalGunId");
    }

    @Override
    public String getCode() {
        return "070";
    }

    @Override
    public String getName() {
        return "첼";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c유틸 ● 첼(포탈)",
                "§f포탈건으로 포탈을 설치합니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c유틸 ● 첼(포탈)");
        p.sendMessage("§f포탈건 우클릭 시, 매번 파랑색 포탈과 주황색 포탈을 번갈아가며 발사합니다.");
        p.sendMessage("§f발사한 포탈이 블럭에 부딪히면 해당 블럭의 부딪친 면(앞)에 포탈이 설치됩니다.");
        p.sendMessage("§f파랑 포탈로 들어가면 주황 포탈로, 주황 포탈로 들어가면 파랑 포탈로 나옵니다.");
        p.sendMessage("§f(양쪽이 모두 설치되어 있어야만 들어갈 수 있습니다.)");
        p.sendMessage("§f고공 낙하 부츠를 장착하면, 어떠한 낙하 데미지도 받지 않습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 8초.");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 포탈건, 고공 낙하 부츠");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void giveItem(Player p) {
        detailCheck(p);

        // 1. 포탈건(감자) 생성 (고유 UUID 부여)
        ItemStack portalGun = new ItemStack(Material.POTATO);
        ItemMeta gunMeta = portalGun.getItemMeta();
        String newGunId = UUID.randomUUID().toString(); // 이 포탈건만의 고유 ID

        if (gunMeta != null) {
            gunMeta.setDisplayName("§b포탈건");
            gunMeta.setLore(Arrays.asList("§e우클릭 시 파랑/주황 포탈을 번갈아 발사합니다.", "§c설치 후 포탈에 들어가면 워프합니다. (쿨타임: 8초)",
                    "§8[Serial: " + newGunId.substring(0, 8) + "]")); // 로어에 시리얼 앞자리 표시
            gunMeta.setCustomModelData(1); // 리소스팩: potal (potato.json)

            // 핵심 데이터: 해당 총의 고유 UUID 주입
            gunMeta.getPersistentDataContainer().set(portalGunIdKey, PersistentDataType.STRING, newGunId);
            portalGun.setItemMeta(gunMeta);
        }

        // 첫 발사는 무조건 파랑색으로 초기화
        isBluePortalNext.put(newGunId, true);

        // 2. 고공 낙하 부츠 (사슬 부츠)
        ItemStack boots = new ItemStack(Material.CHAINMAIL_BOOTS);
        ItemMeta bootsMeta = boots.getItemMeta();
        if (bootsMeta != null) {
            bootsMeta.setDisplayName("§e고공 낙하 부츠");
            bootsMeta.setLore(Arrays.asList("§7장착 시 낙하 데미지를 받지 않습니다."));
            bootsMeta.setUnbreakable(true);
            boots.setItemMeta(bootsMeta);
        }

        p.getInventory().addItem(portalGun);
        p.getInventory().addItem(boots);
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
    }

    @Override
    public void reset() {
        super.reset();

        // 전체 포탈건 데이터를 순회하여 본인이 들고있던 시리얼 등과 무관하게 모든 데이터 초기화
        for (BukkitRunnable task : portalTaskMap.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        portalTaskMap.clear();
        isBluePortalNext.clear();
        bluePortals.clear();
        orangePortals.clear();
        warpCooldowns.clear();
    }

    // 아이템 섭취 방지 (포탈건은 감자이므로)
    @EventHandler
    public void onPlayerInteractForEating(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (AbilityManager.silencedPlayers.contains(p.getUniqueId()))
            return;
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        ItemStack item = e.getItem();
        if (item != null && item.getType() == Material.POTATO && item.hasItemMeta()
                && item.getItemMeta().hasCustomModelData()) {
            if (item.getItemMeta().getCustomModelData() == 1) {
                // 식사 액션 취소
                if (e.getAction().name().contains("RIGHT_CLICK")) {
                    e.setCancelled(true);
                }
            }
        }
    }

    // 포탈 발사 (PlayerInteractEvent)
    @EventHandler
    public void onPortalShoot(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (AbilityManager.silencedPlayers.contains(p.getUniqueId()))
            return;
        /*
         * if (!AbilityManager.getInstance().hasAbility(p, getCode()))
         * return;
         */

        if (!e.getAction().name().contains("RIGHT_CLICK"))
            return;

        ItemStack item = p.getInventory().getItemInMainHand();
        ItemMeta meta = item.getItemMeta();
        if (item.getType() != Material.POTATO || meta == null || !meta.hasCustomModelData()
                || meta.getCustomModelData() != 1
                || !meta.getPersistentDataContainer().has(portalGunIdKey, PersistentDataType.STRING))
            return;

        // 투척한 포탈건의 시리얼 넘버 추출
        String gunId = meta.getPersistentDataContainer().get(portalGunIdKey, PersistentDataType.STRING);
        if (gunId == null)
            return;

        // 쿨타임 검사 및 주기
        if (!checkCooldown(p))
            return;
        setCooldown(p, 8); // 쿨타임 8초

        boolean isBlue = isBlueNext(gunId);
        String colorName = isBlue ? "파랑색" : "주황색";
        String colorCode = isBlue ? "§b" : "§6";

        Bukkit.broadcastMessage("§f" + p.getName() + ": " + colorCode + "(포탈 발사 소리 - " + colorName + ")");

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 2.0f); // 첼 포탈 소리 대용

        // 다음 발사 색상 변경 (포탈건 기준)
        isBluePortalNext.put(gunId, !isBlue);

        // 투사체 발사. 파랑/주황 눈덩이처럼 만들기 위해 아이템 설정 가능.
        Snowball snowball = p.launchProjectile(Snowball.class);
        snowball.setVelocity(p.getLocation().getDirection().multiply(2.5));

        // 투사체에 데이터 태그 (어떤 포탈건에서 나간 무슨 색 포탈인지 구별용)
        snowball.getPersistentDataContainer().set(portalOwnerKey, PersistentDataType.STRING, gunId); // owner를 gunId로 대체
        snowball.getPersistentDataContainer().set(portalTypeKey, PersistentDataType.STRING, isBlue ? "BLUE" : "ORANGE");

        // 투사체 비주얼 (임시 방편: 아이템 눈덩이로 보이게). 만약 특별한 아이템으로 보이고 싶다면 setItem을 사용
        ItemStack visual = new ItemStack(Material.SNOWBALL);
        snowball.setItem(visual);

        registerSummon(p, snowball);
    }

    // 투사체 적중 시 포탈 설치
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        Entity projectile = e.getEntity();
        if (!(projectile instanceof Snowball)
                || !projectile.getPersistentDataContainer().has(portalOwnerKey, PersistentDataType.STRING)) {
            return;
        }

        // 이제 ownerUID는 발사한 사람 UUID가 아닌 해당 '포탈건의 GunID' 입니다
        String gunId = projectile.getPersistentDataContainer().get(portalOwnerKey, PersistentDataType.STRING);
        if (gunId == null)
            return;

        // 투사체 피격 시: 블럭에 부딪혀야 함
        Block hitBlock = e.getHitBlock();
        if (hitBlock == null) {
            return; // 엔티티에 직접 맞았거나 기타 상황
        }

        // 해당 블럭 부딪친 면 정보
        BlockFace hitFace = e.getHitBlockFace();
        if (hitFace == null)
            hitFace = BlockFace.UP;

        Location spawnLoc = hitBlock.getLocation().add(0.5, 0.5, 0.5); // 블럭 중앙
        // 부딪친 면의 바로 앞(바깥쪽)으로 밀어내기
        spawnLoc.add(hitFace.getDirection().multiply(0.55));

        // 월드 보더 체크: 부딪힌 블럭이나 포탈 생성 위치가 월드 보더 밖(또는 경계)일 경우 포탈 설치 취소
        WorldBorder border = hitBlock.getWorld().getWorldBorder();
        if (!border.isInside(hitBlock.getLocation()) || !border.isInside(spawnLoc)) {
            return;
        }

        // 해당 좌표 주변을 포탈 중앙으로 지정. 방향은 hitFace 참조
        String type = projectile.getPersistentDataContainer().get(portalTypeKey, PersistentDataType.STRING);
        boolean isBlue = "BLUE".equals(type);

        PortalData newPortal = new PortalData(spawnLoc, hitFace, isBlue, hitBlock.getLocation());

        if (isBlue) {
            bluePortals.put(gunId, newPortal);
        } else {
            orangePortals.put(gunId, newPortal);
        }

        hitBlock.getWorld().playSound(spawnLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, isBlue ? 1.5f : 1.0f);

        // 투사체가 명중하여 포탈이 최소 1개라도 생성된 이 시점에 스케줄러를 가동합니다.
        // (발사 직후에 켜면 어느 쪽 포탈도 없어서 즉시 루프가 취소되는 버그 방지)
        if (((Snowball) projectile).getShooter() instanceof Player shooter) {
            startPortalLoopIfNeeded(gunId, shooter);
        }
    }

    // 블럭 파괴 시 해당 블럭에 부착된 포탈을 찾아서 제거합니다.
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Block brokenBlock = e.getBlock();
        Location brokenLoc = brokenBlock.getLocation();

        // 파랑 포탈 검사 및 제거
        Iterator<Map.Entry<String, PortalData>> blueIt = bluePortals.entrySet().iterator();
        while (blueIt.hasNext()) {
            Map.Entry<String, PortalData> entry = blueIt.next();
            if (entry.getValue().attachedBlockLoc != null && entry.getValue().attachedBlockLoc.equals(brokenLoc)) {
                blueIt.remove();
                // 모든 플레이어에게 안내 (주인이 명확하지 않으므로 주변만 가벼운 사운드)
                brokenLoc.getWorld().playSound(brokenLoc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
            }
        }

        // 주황 포탈 검사 및 제거
        Iterator<Map.Entry<String, PortalData>> orangeIt = orangePortals.entrySet().iterator();
        while (orangeIt.hasNext()) {
            Map.Entry<String, PortalData> entry = orangeIt.next();
            if (entry.getValue().attachedBlockLoc != null && entry.getValue().attachedBlockLoc.equals(brokenLoc)) {
                orangeIt.remove();
                brokenLoc.getWorld().playSound(brokenLoc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
            }
        }
    }

    // 낙하 데미지 무시 로직
    @EventHandler
    public void onFallDamage(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL)
            return;
        if (!(e.getEntity() instanceof Player p))
            return;

        /*
         * if (!AbilityManager.getInstance().hasAbility(p, getCode()))
         * return;
         */

        // 사슬 부츠 확인
        ItemStack boots = p.getInventory().getBoots();
        if (boots != null && boots.getType() == Material.CHAINMAIL_BOOTS) {
            e.setCancelled(true);
        }
    }

    // ======================================
    // 유틸 메서드
    // ======================================

    // 다음 호출이 파란색인지 파악하는 로직
    private boolean isBlueNext(String gunId) {
        return isBluePortalNext.getOrDefault(gunId, true);
    }

    // 포탈 위치에 파티클을 표시하고 워프를 검사하는 무한루프 스케줄러 시작
    private void startPortalLoopIfNeeded(String gunId, Player starter) {
        if (portalTaskMap.containsKey(gunId))
            return;

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                PortalData blue = bluePortals.get(gunId);
                PortalData orange = orangePortals.get(gunId);

                // 두 포탈이 모두 파괴되었거나, 소멸하면 이 타이머는 조기 종료
                if (blue == null && orange == null) {
                    this.cancel();
                    portalTaskMap.remove(gunId);
                    isBluePortalNext.remove(gunId);
                    return;
                }

                // 파티클 출력 1x1x1
                if (blue != null)
                    drawPortalParticle(blue.loc, blue.face, Color.AQUA);
                if (orange != null)
                    drawPortalParticle(orange.loc, orange.face, Color.ORANGE);

                // 워프 체크 로직 (근처 모든 생명체 스캔)
                if (blue != null && orange != null) {
                    checkWarp(blue, orange, gunId);
                    checkWarp(orange, blue, gunId);
                } else {
                    // 한쪽만 설치되었고 플레이어가 한쪽에 들어갔을 때의 피드백
                    if (blue != null)
                        singlePortalCheck(blue, gunId);
                    if (orange != null)
                        singlePortalCheck(orange, gunId);
                }
            }
        };
        // 1틱마다 실행
        BukkitTask bTask = task.runTaskTimer(plugin, 0L, 1L);
        portalTaskMap.put(gunId, task);
        registerTask(starter, bTask); // 혹시 게임 종료 시 강제 취소 묶음용으로 지급자에게 묶어둠 (cleanup에서 전체 해제)
    }

    private void drawPortalParticle(Location center, BlockFace face, Color color) {
        // center 위치를 기준으로 1x1 정사각형을 파티클로 그립니다.
        // face에 수직인 면(wall 2D)에 그림을 그림.
        World w = center.getWorld();
        if (w == null)
            return;

        Vector right;
        Vector up = new Vector(0, 1, 0);

        if (face == BlockFace.UP || face == BlockFace.DOWN) {
            right = new Vector(1, 0, 0);
            up = new Vector(0, 0, 1);
        } else {
            right = face.getDirection().crossProduct(up).normalize();
        }

        double size = 0.5; // 반지름
        double step = 0.25;

        // 테두리
        for (double a = -size; a <= size; a += step) {
            for (double b = -size; b <= size; b += step) {
                // 테두리 조건이 아니면 생략(사방 모서리만 나오게)
                if (Math.abs(a) == size || Math.abs(b) == size) {
                    Location pLoc = center.clone().add(right.clone().multiply(a)).add(up.clone().multiply(b));
                    Particle.DustOptions dust = new Particle.DustOptions(color, 0.8f);
                    w.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0, dust);
                }
            }
        }
    }

    private void checkWarp(PortalData inPortal, PortalData outPortal, String gunId) {
        BoundingBox box = BoundingBox.of(inPortal.loc, 0.6, 0.6, 0.6); // 플레이어 반경 감지
        if (inPortal.loc.getWorld() == null)
            return;

        Collection<Entity> entities = inPortal.loc.getWorld().getNearbyEntities(box);
        for (Entity e : entities) {
            if (e instanceof LivingEntity) {
                UUID eUid = e.getUniqueId();
                long lastWarp = warpCooldowns.getOrDefault(eUid, 0L);
                long now = System.currentTimeMillis();

                // 1.5초 쿨타임 (1500ms)
                if (now - lastWarp > 1500) {
                    warpCooldowns.put(eUid, now);

                    // 워프 위치 계산 (상대방 포탈의 약간 앞쪽 + 시선은 나오려는 곳 향하게)
                    Location tpLoc = outPortal.loc.clone();
                    Vector faceDir = outPortal.face.getDirection();
                    tpLoc.add(faceDir.multiply(0.5)); // 조금 앞쪽으로 배출

                    // 시선은 faceDir 방향이 되도록 yaw/pitch 설정하기
                    tpLoc.setDirection(faceDir);

                    // 워프
                    e.teleport(tpLoc);
                    if (e instanceof Player) {
                        e.sendMessage("§e포탈을 통과했습니다.");
                    }
                    tpLoc.getWorld().playSound(tpLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                }
            }
        }
    }

    private void singlePortalCheck(PortalData singlePortal, String gunId) {
        BoundingBox box = BoundingBox.of(singlePortal.loc, 0.6, 0.6, 0.6);
        if (singlePortal.loc.getWorld() == null)
            return;

        Collection<Entity> entities = singlePortal.loc.getWorld().getNearbyEntities(box);
        for (Entity e : entities) {
            if (e instanceof Player targetSessionPlayer) {
                UUID eUid = targetSessionPlayer.getUniqueId();
                long lastMsg = warpCooldowns.getOrDefault(eUid, 0L);
                long now = System.currentTimeMillis();
                if (now - lastMsg > 3000) { // 3초마다 경고 메시지
                    targetSessionPlayer.sendMessage("§c포탈이 연결되어 있지 않습니다.");
                    warpCooldowns.put(eUid, now); // 메시지 쿨 용도로도 사용.
                }
            }
        }
    }

    // 포탈 위치 정보 저장 클래스
    private static class PortalData {
        Location loc;
        BlockFace face;
        boolean isBlue;
        Location attachedBlockLoc; // 부착된 블럭 위치 (블럭 파괴 시 포탈 제거 용도)

        public PortalData(Location loc, BlockFace face, boolean isBlue, Location attachedBlockLoc) {
            this.loc = loc;
            this.face = face;
            this.isBlue = isBlue;
            this.attachedBlockLoc = attachedBlockLoc;
        }
    }

}
