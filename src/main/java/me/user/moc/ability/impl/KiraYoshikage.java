package me.user.moc.ability.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
// import org.bukkit.attribute.Attribute; // FQN 사용을 위해 생략

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.game.GameManager;

public class KiraYoshikage extends Ability {

    // 플레이어별 시어하트 어택(좀벌레) 엔티티 저장
    private final Map<UUID, LivingEntity> sheetHeartAttacks = new HashMap<>();

    // 시어하트 어택의 마지막 위치 저장 (증발 시 복구용)
    private final Map<UUID, Location> lastKnownLocations = new HashMap<>();

    public KiraYoshikage(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "키라 요시카게";
    }

    @Override
    public String getCode() {
        return "039";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§e전투 ● §f키라 요시카게 (죠죠의 기묘한 모험)",
                "§f시어하트 어택을 사출하여 적을 자동 추격합니다.");
    }

    @Override
    public void giveItem(Player p) {
        summonSheerHeartAttack(p);
        startAITask();
    }

    private void summonSheerHeartAttack(Player owner) {
        if (!sheetHeartAttacks.containsKey(owner.getUniqueId())) {
            Bukkit.broadcastMessage("§d키라 요시카게 : §f시어하트 어택에게... 약점은 없다…");
        }

        // 처음 소환 시에는 주인 위치 근처
        spawnSheerHeartAttackEntity(owner, owner.getLocation().add(3, 1, 0));
    }

    /**
     * 시어하트 어택 엔티티 생성
     * 
     * @param owner 주인
     * @param loc   소환 위치 (재소환 시 마지막 위치 사용)
     */
    private void spawnSheerHeartAttackEntity(Player owner, Location loc) {
        // 이미 맵에 존재하는데 또 소환하려 하면 패스 (중복 방지)
        if (sheetHeartAttacks.containsKey(owner.getUniqueId()))
            return;

        // 1. 본체 (좀벌레)
        org.bukkit.entity.Silverfish sha = (org.bukkit.entity.Silverfish) owner.getWorld().spawnEntity(loc,
                EntityType.SILVERFISH);
        sha.setInvisible(true); // 투명
        sha.setSilent(true); // 소리 없음
        sha.setCollidable(false);// 밀치기 X
        sha.setRemoveWhenFarAway(false);
        sha.setInvulnerable(true); // 무적

        // [수정] 이동 속도 설정 (0.26 -> 0.312, 약 20% 증가)
        // GENERIC_MOVEMENT_SPEED -> MOVEMENT_SPEED (프로젝트 API 버전 호환)
        if (sha.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED) != null) {
            sha.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(0.312);
        }

        // 좀벌레 이름표는 숨김
        sha.setCustomNameVisible(false);
        sha.setMetadata("SheerHeartAttack", new org.bukkit.metadata.FixedMetadataValue(plugin, owner.getUniqueId()));

        // 2. 외형 (블록 디스플레이 - TNT)
        org.bukkit.entity.BlockDisplay display = (org.bukkit.entity.BlockDisplay) owner.getWorld().spawnEntity(loc,
                EntityType.BLOCK_DISPLAY);
        display.setBlock(org.bukkit.Material.TNT.createBlockData());
        display.setCustomNameVisible(false);

        // 크기 조절 (0.6배), 위치 조정
        display.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(-0.3f, 0, -0.3f), // Translation
                new org.joml.Quaternionf(), // Left Rotation
                new org.joml.Vector3f(0.6f, 0.6f, 0.6f), // Scale
                new org.joml.Quaternionf() // Right Rotation
        ));

        // 3. 이름표 (TextDisplay)
        org.bukkit.entity.TextDisplay text = (org.bukkit.entity.TextDisplay) owner.getWorld().spawnEntity(loc,
                EntityType.TEXT_DISPLAY);
        text.setText("§2시어하트 어택"); // 진한 초록색
        text.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);

        // 이름표 위치를 TNT 위로 띄움
        text.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(0, 0.85f, 0), // Translation
                new org.joml.Quaternionf(),
                new org.joml.Vector3f(1f, 1f, 1f), // Scale
                new org.joml.Quaternionf()));

        sha.addPassenger(display);
        sha.addPassenger(text);

        sheetHeartAttacks.put(owner.getUniqueId(), sha);
        lastKnownLocations.put(owner.getUniqueId(), loc);

        registerSummon(owner, sha);
        registerSummon(owner, display);
        registerSummon(owner, text);
    }

    private BukkitTask aiTask;

    private void startAITask() {
        if (aiTask != null && !aiTask.isCancelled())
            return;

        aiTask = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                GameManager gm = MocPlugin.getInstance().getGameManager();
                if (!gm.isRunning()) {
                    if (!sheetHeartAttacks.isEmpty()) {
                        reset();
                    }
                    if (sheetHeartAttacks.isEmpty()) {
                        this.cancel();
                        aiTask = null;
                        return;
                    }
                    return;
                }

                if (sheetHeartAttacks.isEmpty()) {
                    // 확인: 소환된 SHA가 없으면 태스크 종료?
                    // 하지만 SHA는 giveItem에서 소환됨.
                    // 여기서는 "KiraYoshikage 능력자가 아예 없는지" 확인하는게 더 정확함.
                    // 하지만 로직 상 SHA가 없으면 루프 돌 이유가 없음.
                    // 다만, SHA가 죽었을 때 respawn 로직이 여기 있으므로...
                    // "Kira 능력자가 한명이라도 있는지" 체크가 필요함.

                    boolean kiraExists = false;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p,
                                getCode())) {
                            kiraExists = true;
                            break;
                        }
                    }

                    if (!kiraExists) {
                        this.cancel();
                        aiTask = null;
                        return;
                    }
                }

                for (UUID ownerUUID : new java.util.HashSet<>(sheetHeartAttacks.keySet())) {
                    Player owner = Bukkit.getPlayer(ownerUUID);

                    if (owner == null || !owner.isOnline() || owner.getGameMode() == GameMode.SPECTATOR) {
                        tryRemoveSHA(ownerUUID);
                        continue;
                    }

                    LivingEntity shaEntity = sheetHeartAttacks.get(ownerUUID);

                    // 지속성 & 재소환 관리
                    if (shaEntity == null || !shaEntity.isValid() || shaEntity.isDead()) {
                        tryRemoveSHA(ownerUUID);
                        Location respawnLoc = lastKnownLocations.get(ownerUUID);

                        // [수정] 월드 보더 밖 체크 (공통 유틸리티 사용)
                        if (respawnLoc != null) {
                            respawnLoc = clampLocationToBorder(respawnLoc);
                        } else {
                            respawnLoc = clampLocationToBorder(owner.getLocation());
                        }

                        spawnSheerHeartAttackEntity(owner, respawnLoc);
                        continue;
                    }

                    // 위치 기록
                    lastKnownLocations.put(ownerUUID, shaEntity.getLocation());

                    // 이펙트
                    shaEntity.getWorld().spawnParticle(Particle.SMOKE, shaEntity.getLocation().add(0, 0.3, 0), 3, 0.1,
                            0.1, 0.1, 0);

                    // AI 동작
                    if (shaEntity instanceof org.bukkit.entity.Mob mob) {
                        LivingEntity currentTarget = mob.getTarget();

                        // 1. 타겟 유효성 검사
                        if (currentTarget != null) {
                            if (!currentTarget.isValid() || currentTarget.isDead()) {
                                currentTarget = null;
                                mob.setTarget(null);
                            } else if (currentTarget instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) {
                                currentTarget = null;
                                mob.setTarget(null);
                            }
                        }

                        // 2. 타겟이 없거나, 타겟이 주인인 경우 (1초마다 다른 적 탐색)
                        if (currentTarget == null || (currentTarget.equals(owner) && tick % 20 == 0)) {
                            // 우선 순위 : 플레이어 > 몹 > 주인
                            LivingEntity newTarget = findTargetWithPriority(shaEntity, owner);
                            if (newTarget != null) {
                                mob.setTarget(newTarget);
                            }
                        }
                        // 3. 타겟이 주인 외의 존재라면 죽을 때까지 유지 (락온)
                    }

                    // 수영 로직
                    if (shaEntity instanceof org.bukkit.entity.Mob mob && mob.getTarget() != null
                            && shaEntity.isInWater()) {
                        Vector dir = mob.getTarget().getLocation().toVector()
                                .subtract(shaEntity.getLocation().toVector())
                                .normalize();
                        shaEntity.setVelocity(dir.multiply(0.15).setY(0.1));
                    }

                    // 폭발 (4초)
                    if (tick % 80 == 0) {
                        createExplosion(shaEntity, owner);
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void tryRemoveSHA(UUID ownerUUID) {
        LivingEntity e = sheetHeartAttacks.get(ownerUUID);
        if (e != null) {
            if (e.getPassengers() != null) {
                for (Entity passenger : new java.util.ArrayList<>(e.getPassengers())) {
                    passenger.remove();
                }
            }
            e.remove();
        }
        sheetHeartAttacks.remove(ownerUUID);
    }

    /**
     * 우선순위에 따른 타겟 탐색
     * 1. 다른 플레이어
     * 2. 다른 생명체 (몹)
     * 3. 주인 (1, 2가 없을 때만)
     */
    private LivingEntity findTargetWithPriority(Entity center, Player owner) {
        LivingEntity topPriority = null; // 플레이어
        LivingEntity subPriority = null; // 몹
        double minDistP = Double.MAX_VALUE;
        double minDistM = Double.MAX_VALUE;

        // 반경 50칸 탐색
        for (Entity e : center.getNearbyEntities(50, 50, 50)) {
            if (!(e instanceof LivingEntity le) || e == center)
                continue;
            if (le.getUniqueId().equals(owner.getUniqueId()))
                continue; // 일단 주인 제외

            if (le instanceof Player p) {
                if (p.getGameMode() == GameMode.SPECTATOR)
                    continue;
                double d = center.getLocation().distanceSquared(le.getLocation());
                if (d < minDistP) {
                    minDistP = d;
                    topPriority = le;
                }
            } else {
                // 몹
                double d = center.getLocation().distanceSquared(le.getLocation());
                if (d < minDistM) {
                    minDistM = d;
                    subPriority = le;
                }
            }
        }

        if (topPriority != null)
            return topPriority;
        if (subPriority != null)
            return subPriority;

        // 1, 2번 없으면 주인 리턴
        return owner;
    }

    private void createExplosion(LivingEntity sha, Player owner) {
        sha.getWorld().createExplosion(sha.getLocation(), 2f, false, true);
        sha.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, sha.getLocation(), 1);
        sha.getWorld().playSound(sha.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);

        for (Entity e : sha.getNearbyEntities(3.5, 3.5, 3.5)) {
            if (e instanceof LivingEntity le && e != sha && e != owner) {
                if (le instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR)
                    continue;
                if (le.getUniqueId().equals(owner.getUniqueId()))
                    continue;

                le.damage(10.0, owner);
                le.sendMessage("§c이쪽을 봐라!");
            }
        }
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        tryRemoveSHA(p.getUniqueId());
        lastKnownLocations.remove(p.getUniqueId());
    }

    @Override
    public void reset() {
        super.reset();

        for (UUID uuid : new java.util.HashSet<>(sheetHeartAttacks.keySet())) {
            tryRemoveSHA(uuid);
        }
        sheetHeartAttacks.clear();
        lastKnownLocations.clear();
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● 키라 요시카게(죠죠의 기묘한 모험)");
        p.sendMessage("§f시어하트 어택을 사출하여 적을 자동 추격합니다.");
        p.sendMessage("§f시어하트 어택은 4초마다 10의 폭발 대미지를 입히며 주변 블럭을 폭발 시킵니다.");
        p.sendMessage("§f시어하트 어택은 절대 사라지지 않으며, 파괴될 경우 재소환됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    // === [이벤트 리스너] ===

    @org.bukkit.event.EventHandler
    public void onEntityChangeBlock(org.bukkit.event.entity.EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof org.bukkit.entity.Silverfish) {
            if (e.getEntity().hasMetadata("SheerHeartAttack")) {
                e.setCancelled(true);
            }
        }
    }

    // 시어하트 어택이 '맞는' 경우 (무적)
    @org.bukkit.event.EventHandler
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e.getEntity().hasMetadata("SheerHeartAttack")) {
            e.setCancelled(true);
        }
    }

    // 시어하트 어택이 '때리는' 경우 (직접 공격 무효화)
    @org.bukkit.event.EventHandler
    public void onEntityAttack(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (e.getDamager().hasMetadata("SheerHeartAttack")) {
            e.setCancelled(true);
        }
    }
}
