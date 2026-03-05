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

    // 플레이어의 포탈 활성화 상태(파란색=true / 주황색=false) 저장
    private final Map<UUID, Boolean> isBluePortalNext = new HashMap<>();

    // 각 플레이어별 활성화된 포탈 목록. Map<UUID, List<PortalData>> (0: 파랑, 1: 주황 등)
    // 최대 2개를 유지합니다. 파랑/주황 한쌍.
    private final Map<UUID, PortalData> bluePortals = new HashMap<>();
    private final Map<UUID, PortalData> orangePortals = new HashMap<>();

    // 무한 워프 방지 쿨타임 (UUID -> 최근 워프 시간 밀리초)
    private final Map<UUID, Long> warpCooldowns = new HashMap<>();

    // 포탈 파티클 소환용 스케줄러를 담기 위함
    private final Map<UUID, BukkitRunnable> portalTaskMap = new HashMap<>();

    private NamespacedKey portalTypeKey;
    private NamespacedKey portalOwnerKey;

    public Chell(MocPlugin plugin) {
        super(plugin);
        this.portalTypeKey = new NamespacedKey(plugin, "portalType");
        this.portalOwnerKey = new NamespacedKey(plugin, "portalOwner");
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

        // 첫 발사는 무조건 파랑색으로 초기화
        isBluePortalNext.put(p.getUniqueId(), true);

        // 1. 포탈건(감자)
        ItemStack portalGun = new ItemStack(Material.POTATO);
        ItemMeta gunMeta = portalGun.getItemMeta();
        if (gunMeta != null) {
            gunMeta.setDisplayName("§b포탈건");
            gunMeta.setLore(Arrays.asList("§e우클릭 시 파랑/주황 포탈을 번갈아 발사합니다.", "§c설치 후 포탈에 들어가면 워프합니다. (쿨타임: 8초)"));
            gunMeta.setCustomModelData(1); // 리소스팩: potal (potato.json)
            portalGun.setItemMeta(gunMeta);
        }

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
        // 기존 쿨타임 등은 super.cleanup에서 정리됩니다 (cooldowns, activeEntities 등)
        super.cleanup(p);

        UUID uid = p.getUniqueId();
        isBluePortalNext.remove(uid);
        bluePortals.remove(uid);
        orangePortals.remove(uid);
        warpCooldowns.remove(uid);

        // 파티클 태스크 끄기
        if (portalTaskMap.containsKey(uid)) {
            portalTaskMap.get(uid).cancel();
            portalTaskMap.remove(uid);
        }
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
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        if (!e.getAction().name().contains("RIGHT_CLICK"))
            return;

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.POTATO || !item.hasItemMeta() || !item.getItemMeta().hasCustomModelData()
                || item.getItemMeta().getCustomModelData() != 1)
            return;

        // 쿨타임 검사 및 주기
        if (!checkCooldown(p))
            return;
        setCooldown(p, 8); // 쿨타임 8초

        boolean isBlue = isBlueNext(p);
        String colorName = isBlue ? "파랑색" : "주황색";
        String colorCode = isBlue ? "§b" : "§6";

        Bukkit.broadcastMessage("§f첼" + colorCode + "(포탈 발사 소리 - " + colorName + ")");

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 2.0f); // 첼 포탈 소리 대용

        // 다음 발사 색상 변경
        isBluePortalNext.put(p.getUniqueId(), !isBlue);

        // 투사체 발사. 파랑/주황 눈덩이처럼 만들기 위해 아이템 설정 가능.
        Snowball snowball = p.launchProjectile(Snowball.class);
        snowball.setVelocity(p.getLocation().getDirection().multiply(2.5));

        // 투사체에 데이터 태그 (누구의 무슨 포탈인지 구별용)
        snowball.getPersistentDataContainer().set(portalOwnerKey, PersistentDataType.STRING,
                p.getUniqueId().toString());
        snowball.getPersistentDataContainer().set(portalTypeKey, PersistentDataType.STRING, isBlue ? "BLUE" : "ORANGE");

        // 투사체 비주얼 (임시 방편: 아이템 눈덩이로 보이게). 만약 특별한 아이템으로 보이고 싶다면 setItem을 사용
        ItemStack visual = new ItemStack(Material.SNOWBALL);
        snowball.setItem(visual);

        registerSummon(p, snowball);
        // 포탈이 계속 켜져있어야하므로 스케줄러가 없다면 하나 켜줍니다.
        startPortalLoopIfNeeded(p);
    }

    // 투사체 적중 시 포탈 설치
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        Entity projectile = e.getEntity();
        if (!(projectile instanceof Snowball)
                || !projectile.getPersistentDataContainer().has(portalOwnerKey, PersistentDataType.STRING)) {
            return;
        }

        String ownerUID = projectile.getPersistentDataContainer().get(portalOwnerKey, PersistentDataType.STRING);
        if (ownerUID == null)
            return;

        Player p = plugin.getServer().getPlayer(UUID.fromString(ownerUID));
        if (p == null || !AbilityManager.getInstance().hasAbility(p, getCode()))
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

        // 해당 좌표 주변을 포탈 중앙으로 지정. 방향은 hitFace 참조
        String type = projectile.getPersistentDataContainer().get(portalTypeKey, PersistentDataType.STRING);
        boolean isBlue = "BLUE".equals(type);

        PortalData newPortal = new PortalData(spawnLoc, hitFace, isBlue);

        if (isBlue) {
            bluePortals.put(p.getUniqueId(), newPortal);
        } else {
            orangePortals.put(p.getUniqueId(), newPortal);
        }

        p.getWorld().playSound(spawnLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, isBlue ? 1.5f : 1.0f);
    }

    // 낙하 데미지 무시 로직
    @EventHandler
    public void onFallDamage(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL)
            return;
        if (!(e.getEntity() instanceof Player p))
            return;

        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

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
    private boolean isBlueNext(Player p) {
        return isBluePortalNext.getOrDefault(p.getUniqueId(), true);
    }

    // 포탈 위치에 파티클을 표시하고 워프를 검사하는 무한루프 스케줄러 시작
    private void startPortalLoopIfNeeded(Player p) {
        UUID uid = p.getUniqueId();
        if (portalTaskMap.containsKey(uid))
            return;

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || !AbilityManager.getInstance().hasAbility(p, getCode())) {
                    this.cancel();
                    portalTaskMap.remove(uid);
                    return;
                }

                PortalData blue = bluePortals.get(uid);
                PortalData orange = orangePortals.get(uid);

                // 파티클 출력 1x1x1
                if (blue != null)
                    drawPortalParticle(blue.loc, blue.face, Color.AQUA);
                if (orange != null)
                    drawPortalParticle(orange.loc, orange.face, Color.ORANGE);

                // 워프 체크 로직 (근처 모든 생명체 스캔)
                if (blue != null && orange != null) {
                    checkWarp(blue, orange, p);
                    checkWarp(orange, blue, p);
                } else {
                    // 한쪽만 설치되었고 플레이어가 한쪽에 들어갔을 때의 피드백 (조건부)
                    if (blue != null)
                        singlePortalCheck(blue, p);
                    if (orange != null)
                        singlePortalCheck(orange, p);
                }
            }
        };
        // 1틱마다 실행
        task.runTaskTimer(plugin, 0L, 1L);
        portalTaskMap.put(uid, task);
        registerTask(p, (BukkitTask) task);
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

    private void checkWarp(PortalData inPortal, PortalData outPortal, Player owner) {
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
                        e.sendMessage("§b[MOC] 포탈을 통과했습니다.");
                    }
                    tpLoc.getWorld().playSound(tpLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                }
            }
        }
    }

    private void singlePortalCheck(PortalData singlePortal, Player owner) {
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

        public PortalData(Location loc, BlockFace face, boolean isBlue) {
            this.loc = loc;
            this.face = face;
            this.isBlue = isBlue;
        }
    }

}
