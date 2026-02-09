package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class Yesung extends Ability {

    private final java.util.Map<UUID, List<UUID>> activeMoons = new java.util.HashMap<>();

    public Yesung(JavaPlugin plugin) {
        super(plugin);
    }

    // ... (rest of class)

    // In activateSkill:
    // List<UUID> entityUuids = spawnVoxelMoon(center);
    // activeMoons.put(p.getUniqueId(), entityUuids);

    // In cleanupMoonEntities(Player p):
    // List<UUID> uuids = activeMoons.remove(p.getUniqueId());
    // if (uuids != null) { ... remove entities ... }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        cleanupMoonEntities(p);
    }

    // In applyGlobalDamage:
    // target.setNoDamageTicks(0);
    // target.damage(13.0, attacker);

    @Override
    public String getCode() {
        return "H02";
    }

    @Override
    public String getName() {
        return "루키";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§d히든 ● 루키(바집소)",
                "§f메이플 용사가 되.");
    }

    @Override
    public void giveItem(Player p) {
        // 기존 철 칼 제거
        p.getInventory().remove(Material.IRON_SWORD);

        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.displayName(Component.text("§f제네시스 두손검"));
        meta.lore(Arrays.asList(Component.text("§7우클릭 시 소울 이클립스/솔루나 디바이드를 사용합니다.")));
        meta.setCustomModelData(2); // 리소스팩: rooki
        sword.setItemMeta(meta);

        p.getInventory().addItem(sword);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§d히든 ● 루키(바집소)");
        p.sendMessage("§f메이플 용사가 되어 소드마스터 5차 스킬를 사용합니다.");
        p.sendMessage(" ");
        p.sendMessage("§f전용 검을 들고 우클릭 시 공중으로 도약하여 거대한 달을 가릅니다.");
        p.sendMessage("§f달이 갈라지면 월드 전체 적에게 13의 데미지를 줍니다.");
        p.sendMessage("§f(시전 중 낙하 데미지 무시)");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 40초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 루키의 검");
        p.sendMessage("§f장비 제거 : 철 칼");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack item = e.getItem();
        if (item == null)
            return;

        // 전용 아이템 체크 (루키의 검)
        if (item.getType() != Material.IRON_SWORD)
            return;
        if (item.getItemMeta() == null || !"§f루키의 검".equals(item.getItemMeta().getDisplayName()))
            return;

        if (!checkCooldown(p))
            return;

        setCooldown(p, 40);
        activateSkill(p);
    }

    private void activateSkill(Player p) {
        Bukkit.broadcast(Component.text("§e루키 : §f님들 메이플 하쉴?"));

        // 1. 도약
        Vector jumpVel = p.getLocation().getDirection().multiply(0.5).setY(1.5);
        p.setVelocity(jumpVel);

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_HORSE_JUMP, 1.0f, 0.8f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.5f, 1.2f);

        // 2. 공중 체류 및 이펙트 연출 (0.5초 뒤)
        var task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline())
                    return;

                // 머리 위 20칸 (더 높게)
                Location center = p.getLocation().add(0, 20, 0);
                // [Fix] 시선에 따라 기울어지지 않도록 회전값 초기화 (항상 수평 유지)
                center.setYaw(0);
                center.setPitch(0);
                World w = p.getWorld();

                // 중력 제어
                p.setVelocity(new Vector(0, 0.1, 0));
                p.setFallDistance(0);

                // 달 생성 (BlockDisplay)
                List<UUID> entityUuids = spawnVoxelMoon(center);
                activeMoons.put(p.getUniqueId(), entityUuids);

                // 생성된 엔티티 관리 10초 뒤 강제 삭제 태스크 예약
                var cleanupTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        cleanupMoonEntities(p);
                    }
                }.runTaskLater(plugin, 200L);
                registerTask(p, cleanupTask);

                w.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 5.0f, 1.5f);
                w.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 5.0f, 0.5f);

                // 3. 베기 및 분리 애니메이션 (달 생성 1.5초 후)
                var splitTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!p.isOnline())
                            return;

                        w.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 5.0f, 0.5f);
                        w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 5.0f, 0.8f);
                        w.playSound(center, Sound.ITEM_TRIDENT_THUNDER, 5.0f, 1.0f);

                        // 분리 애니메이션 실행
                        animateSplit(w, entityUuids);

                        // 데미지 처리
                        applyGlobalDamage(p);
                    }
                }.runTaskLater(plugin, 30L); // 1.5초
                registerTask(p, splitTask);
            }
        }.runTaskLater(plugin, 10L);
        registerTask(p, task);

        // 낙하 데미지 방지용 태스크
        var immunityTask = new BukkitRunnable() {
            @Override
            public void run() {
            }
        }.runTaskLater(plugin, 100L);
        registerTask(p, immunityTask);
    }

    // 복셀 문 생성 (BlockDisplay)
    private List<UUID> spawnVoxelMoon(Location center) {
        World w = center.getWorld();
        List<UUID> createdEntities = new ArrayList<>();

        // [변경] Scale 3.0 -> 4.0 (더 크게)
        float scale = 4.0f;

        // [변경] Spacing 2.8 -> 2.5 (Scale보다 작게 설정하여 겹침 Overlap 발생 시킴 -> 틈새 제거)
        double spacing = 2.5;

        // 반지름 개수 조정 (Scale이 커졌으니 개수는 조절하거나 유지)
        int r = 12;

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r)
                    continue;

                Location spawnLoc = center.clone().add(x * spacing, 0, z * spacing);

                if (!spawnLoc.getChunk().isLoaded()) {
                    spawnLoc.getChunk().load(); // Force load
                }

                BlockDisplay bd = w.spawn(spawnLoc, BlockDisplay.class);

                // [변경] SEA_LANTERN -> OCHRE_FROGLIGHT (황금빛 텍스처)
                bd.setBlock(Bukkit.createBlockData(Material.OCHRE_FROGLIGHT));
                bd.setBrightness(new Display.Brightness(15, 15));
                bd.setViewRange(1000.0f);
                bd.setBillboard(Display.Billboard.FIXED);

                // [추가] 발광 효과 (Golden Glow)
                bd.setGlowing(true);
                bd.setGlowColorOverride(Color.fromRGB(255, 200, 50)); // 따뜻한 금색

                // 크기 설정 (겹치도록)
                Transformation mutation = bd.getTransformation();
                mutation.getScale().set(scale, 0.5f, scale);
                bd.setTransformation(mutation);

                // 태그 부여 (좌우 분리)
                if (x < 0) {
                    bd.addScoreboardTag("moon_left");
                    bd.addScoreboardTag("moc_yesung_moon");
                } else {
                    bd.addScoreboardTag("moon_right");
                    bd.addScoreboardTag("moc_yesung_moon");
                }

                createdEntities.add(bd.getUniqueId());
            }
        }
        return createdEntities;
    }

    // 분리 애니메이션
    private void animateSplit(World w, List<UUID> uuids) {
        // 이동 벡터
        Vector3f moveLeft = new Vector3f(-10f, -8f, 0f);
        Vector3f moveRight = new Vector3f(10f, -8f, 0f);

        // Z축 회전 (Roll) - Quaternionf 사용
        Quaternionf rotLeft = new Quaternionf().rotateAxis((float) Math.toRadians(30), 0, 0, 1);
        Quaternionf rotRight = new Quaternionf().rotateAxis((float) Math.toRadians(-30), 0, 0, 1);

        for (UUID uid : uuids) {
            Entity e = w.getEntity(uid);
            if (!(e instanceof BlockDisplay bd))
                continue;

            Transformation start = bd.getTransformation();

            Vector3f targetTrans = new Vector3f(start.getTranslation());
            Quaternionf targetRot = new Quaternionf(start.getLeftRotation());

            if (bd.getScoreboardTags().contains("moon_left")) {
                targetTrans.add(moveLeft);
                targetRot.mul(rotLeft);
            } else {
                targetTrans.add(moveRight);
                targetRot.mul(rotRight);
            }

            // 새로운 Transformation 생성
            Transformation end = new Transformation(
                    targetTrans,
                    targetRot,
                    start.getScale(),
                    start.getRightRotation());

            bd.setInterpolationDelay(0);
            bd.setInterpolationDuration(60); // 3초간 부드럽게 (60 ticks)
            bd.setTransformation(end);
        }
    }

    // 엔티티 정리
    private void cleanupMoonEntities(Player p) {
        UUID pid = p.getUniqueId();
        if (activeMoons.containsKey(pid)) {
            List<UUID> uuids = activeMoons.remove(pid);
            if (uuids != null) {
                World w = p.getWorld();
                for (UUID uid : uuids) {
                    Entity e = w.getEntity(uid);
                    if (e != null)
                        e.remove();
                }
            }
        }

        // 혹시 모르니 태그로 한번 더 청소 (찌꺼기 방지)
        for (Entity e : p.getWorld().getEntities()) {
            if (e.getScoreboardTags().contains("moc_yesung_moon")) {
                e.remove();
            }
        }
    }

    private void applyGlobalDamage(Player attacker) {
        World w = attacker.getWorld();
        int count = 0;

        for (Entity e : w.getEntities()) {
            if (e.equals(attacker))
                continue;
            if (!(e instanceof LivingEntity target))
                continue;
            // BlockDisplay 등은 제외
            if (e instanceof Display)
                continue;

            // [추가] 무적 시간 무시 (True Damage 효과)
            target.setNoDamageTicks(0);
            target.damage(13.0, attacker);
            w.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
            count++;
        }

        attacker.sendMessage("§f소울 이클립스/솔루나 디바이드로 " + count + "명의 적에게 피해를 입혔습니다!");
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p))
            return;
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL)
            return;

        if (AbilityManager.getInstance().hasAbility(p, getCode())) {
            if (activeTasks.containsKey(p.getUniqueId()) && !activeTasks.get(p.getUniqueId()).isEmpty()) {
                e.setCancelled(true);
            }
        }
    }
}
