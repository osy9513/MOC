package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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

    // Stuck Sword Management
    private static class StuckSword {
        UUID ownerUUID;
        ItemDisplay visual;
        // Location loc; // Unused
        // long timestamp; // Unused

        public StuckSword(UUID owner, ItemDisplay visual) {
            this.ownerUUID = owner;
            this.visual = visual;
            // this.loc = visual.getLocation();
            // this.timestamp = System.currentTimeMillis();
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
                "§7라운드 시작 시 고유 영창을 수행합니다.",
                "§7영창이 완료되면 전장에 수많은 검이 쏟아집니다.",
                "§7떨어진 검을 주워 투척할 수 있습니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 장비 제거: 철 칼
        p.getInventory().remove(Material.IRON_SWORD);

        // 라운드 시작 감지 -> 영창 시작
        // GameManager가 startBattle()에서 giveItem을 호출하므로 여기가 시작점
        startChant(p);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● 에미야 시로(FATE)");
        p.sendMessage("§f라운드가 시작되면 8초간 영창을 시작하며, 완료 시 '무한의 검제'를 전개합니다.");
        p.sendMessage("§f하늘에서 수많은 검이 쏟아져 적들에게 5칸(10) 피해를 입힙니다.");
        p.sendMessage("§f떨어진 검을 주워 공격할 수 있으며, 우클릭으로 직접 투척할 수도 있습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f떨어진 검을 본인이 획득 -> 다이아몬드 검");
        p.sendMessage("§f떨어진 검을 타인이 획득 -> 돌 검");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 철 검");
    }

    private void startChant(Player p) {

        // 하늘 색 변경 (주황색 - 대략 13000~23000 사이, 18000은 밤, 23000은 일출, 12000~13000은 일몰)
        // 13000 근처가 노을질 때
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
                    p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.5f + (index * 0.1f));
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
    }

    private void startUBW(Player p) {
        // 이미 죽었으면 실행 안함
        if (!p.isOnline() || p.isDead())
            return;

        p.sendMessage("§c[System] Unlimited Blade Works 발동!");
        p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f);

        // Start Pickup Task (Global for this ability instance, or per user?)
        // Since Ability instance is singleton, one task handles all.
        // But UBW duration is per user basically.
        // Let's ensure task is running if list not empty, or just always run/start
        // once.
        if (pickupTask == null || pickupTask.isCancelled()) {
            startPickupTask();
        }

        // 30초간 실행
        BukkitTask ubwTask = new BukkitRunnable() {
            int tickMap = 0;
            final int DURATION_TICKS = 30 * 20;

            @Override
            public void run() {
                if (!p.isOnline() || tickMap >= DURATION_TICKS) {
                    p.resetPlayerTime(); // 하늘 원래대로
                    this.cancel();
                    return;
                }

                // 1초(20틱)에 10개 -> 2틱마다 1개
                if (tickMap % 2 == 0) {
                    spawnFallingSword(p);
                }

                tickMap += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        registerTask(p, ubwTask);
    }

    private void spawnFallingSword(Player owner) {
        // 전장 범위 내 랜덤 위치
        double range = 40.0;
        double dx = (Math.random() * range * 2) - range;
        double dz = (Math.random() * range * 2) - range;

        Location spawnLoc = owner.getLocation().add(dx, 0, dz);
        spawnLoc.setY(spawnLoc.getWorld().getHighestBlockYAt(spawnLoc) + 64);

        // Spawn Arrow (Core projectile)
        Arrow arrow = owner.getWorld().spawn(spawnLoc, Arrow.class);
        arrow.setShooter(owner);
        arrow.setPierceLevel(127);
        arrow.setDamage(5.0);
        arrow.setVelocity(new Vector(0, -3, 0));
        arrow.setMetadata(KEY_UBW_SWORD, new FixedMetadataValue(plugin, owner.getUniqueId().toString()));
        arrow.setSilent(true);
        arrow.setCritical(true);

        // Visual: ItemDisplay riding Arrow
        ItemDisplay itemDisplay = owner.getWorld().spawn(spawnLoc, ItemDisplay.class);

        // Random Sword Material (Restricted to Netheite, Diamond, Iron)
        Material[] swords = { Material.NETHERITE_SWORD, Material.DIAMOND_SWORD, Material.IRON_SWORD };
        Material randomSword = swords[(int) (Math.random() * swords.length)];
        itemDisplay.setItemStack(new ItemStack(randomSword));

        // Store material in metadata for safe retrieval
        arrow.setMetadata(KEY_SWORD_MAT, new FixedMetadataValue(plugin, randomSword.name()));

        // Random Rotation for dynamic look
        float randomZ = (float) Math.toRadians((Math.random() * 40) - 20); // +/- 20 degrees tilt
        float randomY = (float) Math.toRadians(Math.random() * 360); // Random facing

        itemDisplay.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(
                        new Quaternionf().rotateX((float) Math.toRadians(180)).rotateZ(randomZ).rotateY(randomY)),
                new Vector3f(1.5f, 1.5f, 1.5f),
                new AxisAngle4f()));

        itemDisplay.setGlowing(true); // Mystical effect

        arrow.addPassenger(itemDisplay);

        registerSummon(owner, arrow);
        registerSummon(owner, itemDisplay);

        // Add trail particle effect runnable
        new BukkitRunnable() {
            @Override
            public void run() {
                if (arrow.isDead() || arrow.isOnGround()) {
                    this.cancel();
                    return;
                }
                arrow.getWorld().spawnParticle(org.bukkit.Particle.CRIT, arrow.getLocation(), 2, 0.1, 0.1, 0.1, 0);
                arrow.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, arrow.getLocation(), 1, 0.1, 0.1, 0.1, 0.1);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // === Event Listeners ===

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (e.getEntity() instanceof Arrow arrow
                && (arrow.hasMetadata(KEY_UBW_SWORD) || arrow.hasMetadata(KEY_SHOOT_SWORD))) {
            String key = arrow.hasMetadata(KEY_UBW_SWORD) ? KEY_UBW_SWORD : KEY_SHOOT_SWORD;

            // Resolve Material: Try Metadata first, then passenger
            Material displayMat = Material.IRON_SWORD;
            if (arrow.hasMetadata(KEY_SWORD_MAT)) {
                try {
                    displayMat = Material.valueOf(arrow.getMetadata(KEY_SWORD_MAT).get(0).asString());
                } catch (IllegalArgumentException ignored) {
                }
            }

            // Always remove visual passengers (Fixes duplication bug)
            if (!arrow.getPassengers().isEmpty()) {
                for (Entity passenger : arrow.getPassengers()) {
                    // Fallback if metadata failed
                    if (!arrow.hasMetadata(KEY_SWORD_MAT) && passenger instanceof ItemDisplay display
                            && display.getItemStack() != null) {
                        displayMat = display.getItemStack().getType();
                    }
                    passenger.remove();
                }
            }

            // If hit block -> Stick to ground (Don't drop item)
            if (e.getHitBlock() != null) {
                Location dropLoc = arrow.getLocation();

                // Impact visuals
                dropLoc.getWorld().spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, dropLoc, 10, 0.2, 0.2, 0.2, 0.05);
                dropLoc.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, dropLoc, 15, 0.2, 0.2, 0.2,
                        e.getHitBlock().getBlockData());
                dropLoc.getWorld().playSound(dropLoc, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f);
                dropLoc.getWorld().playSound(dropLoc, Sound.ENTITY_ITEM_BREAK, 0.5f, 0.5f);

                Location stickLoc = dropLoc.clone().add(0, 0.8, 0); // Raised overlap check (0.5 -> 0.8)

                float randomY = (float) Math.toRadians(Math.random() * 360);
                float randomTilt = (float) Math.toRadians((Math.random() * 30) - 15);

                ItemDisplay newDisplay = dropLoc.getWorld().spawn(stickLoc, ItemDisplay.class);
                newDisplay.setItemStack(new ItemStack(displayMat));
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

            // If hit entity (handled in EntityDamageByEntity generally, but ProjectileHit
            // also fires)
            // Arrow with pierce persists, so we don't remove it yet if it hits entity.
            // But wait, "on block hit -> itemize". "if entity hit -> pierce".
            // Piercing arrow hits entity -> event fires -> arrow continues.
            // If it hits block -> stops -> event fires.
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Arrow arrow) {
            // Case 1: Falling UBW Sword
            if (arrow.hasMetadata(KEY_UBW_SWORD)) {
                String ownerUUIDStr = arrow.getMetadata(KEY_UBW_SWORD).get(0).asString();

                // Damage 5 hearts = 10.0
                e.setDamage(10.0);

                // Immunity for owner
                if (e.getEntity().getUniqueId().toString().equals(ownerUUIDStr)) {
                    e.setCancelled(true);
                    return;
                }
            }

            // Case 2: Shot Sword
            if (arrow.hasMetadata(KEY_SHOOT_SWORD)) {
                String ownerUUIDStr = arrow.getMetadata(KEY_SHOOT_SWORD).get(0).asString();
                // Damage 5 hearts = 10.0 (Same as falling sword)
                e.setDamage(10.0);

                if (e.getEntity().getUniqueId().toString().equals(ownerUUIDStr)) {
                    e.setCancelled(true); // Usually owner can't hit self with arrow anyway, but safety
                }
            }
        }
    }

    // Old ItemPickup event handler removed/ignored since we don't drop items
    // anymore.
    @EventHandler
    public void onItemPickup(EntityPickupItemEvent e) {
        // Legacy cleanup or handling if other items dropped?
        if (e.getEntity() instanceof Player p && e.getItem().hasMetadata(KEY_UBW_SWORD)) {
            e.setCancelled(true);
            e.getItem().remove();
            // Just in case any persist
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        // Check if player is Emiya
        // We can check if their ability is 012 in AbilityManager, OR since this class
        // listener is always active, we need to be careful.
        // Ability Manager listeners are registered globally!
        // We must check if `p` is the ability user associated with THIS instance?
        // No, `Ability` instances are singletons in the manager usually (one instance
        // per plugin load).
        // Wait, `Ability` is registered once. We need to check if `p` has this ability.

        // Helper method verification:
        // AbilityManager.getInstance(plugin).hasAbility(p, getCode());
        // Since `Ability` abstract class doesn't have a direct "isOwner" check helper
        // that links to manager easily without circular dep sometimes.
        // But `AbilityManager` is static accessible or we can get it.

        // Actually, `Ability` listeners fire for EVERYONE.
        // We MUST check
        // `me.user.moc.ability.AbilityManager.getInstance((MocPlugin)plugin).hasAbility(p,
        // getCode())`.

        if (!me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode())) {
            return;
        }

        if (e.getAction().toString().contains("RIGHT_CLICK")
                && p.getInventory().getItemInMainHand().getType().name().contains("SWORD")) {
            // Shoot sword
            ItemStack handItem = p.getInventory().getItemInMainHand();

            // Consume item
            handItem.setAmount(handItem.getAmount() - 1);

            // Shoot Arrow (Visualized as Sword)
            Arrow arrow = p.launchProjectile(Arrow.class);
            arrow.setDamage(5.0); // Base
            arrow.setMetadata(KEY_SHOOT_SWORD, new FixedMetadataValue(plugin, p.getUniqueId().toString()));
            arrow.setPierceLevel(0); // Regular shot

            Material mat = handItem.getType();
            arrow.setMetadata(KEY_SWORD_MAT, new FixedMetadataValue(plugin, mat.name()));

            arrow.setDamage(getSwordDamage(mat));

            // Visual - Schedule 1 tick later to ensure arrow invalidity doesn't glitch
            // passenger
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (arrow.isDead() || !arrow.isValid())
                        return;

                    ItemDisplay itemDisplay = p.getWorld().spawn(arrow.getLocation(), ItemDisplay.class);
                    itemDisplay.setItemStack(new ItemStack(mat));

                    // Rotate to point forward (Item default is Up, Arrow forward is Z? No, local)
                    // -90 X rotation makes top point forward relative to entity look
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
            p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.5f, 2.0f); // High pitch magic sound

            registerSummon(p, arrow);
        }
    }

    private double getSwordDamage(Material mat) {
        switch (mat) {
            case NETHERITE_SWORD:
                return 8.0;
            case DIAMOND_SWORD:
                return 7.0;
            case IRON_SWORD:
                return 6.0;
            case STONE_SWORD:
                return 5.0;
            case WOODEN_SWORD:
                return 4.0;
            case GOLDEN_SWORD:
                return 4.0;
            default:
                return 5.0;
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode())) {
            // If dying during chant, done in runnable mostly, but just in case
            p.resetPlayerTime();
        }
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        p.resetPlayerTime();

        // 1. 실행 중인 태스크(무한의 검제, 영창 등) 강제 취소
        if (activeTasks.containsKey(p.getUniqueId())) {
            List<BukkitTask> tasks = activeTasks.get(p.getUniqueId());
            if (tasks != null) {
                // 리스트 복사본으로 반복하여 안전하게 취소 (ConcurrentModification 방지)
                for (BukkitTask t : new java.util.ArrayList<>(tasks)) {
                    if (t != null && !t.isCancelled()) {
                        t.cancel();
                    }
                }
            }
            activeTasks.remove(p.getUniqueId());
        }

        // Remove swords specific to this player
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

        // Stop task only if no swords left
        if (stuckSwords.isEmpty() && pickupTask != null && !pickupTask.isCancelled()) {
            pickupTask.cancel();
            pickupTask = null;
        }
    }

    @Override
    public void reset() {
        super.reset();
        // [수정] 게임 초기화 시 모든 꽂힌 검 제거
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

                    // Simple optimization: check validity of world
                    if (sword.visual.getWorld() == null) {
                        it.remove();
                        continue;
                    }

                    // Check for nearby players
                    for (Player p : sword.visual.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(sword.visual.getLocation()) <= 2.25) { // 1.5 blocks
                            // Pickup!
                            giveSword(p, sword);
                            sword.visual.remove();
                            // Visual Effect on pickup
                            p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1f, 1.5f);
                            p.getWorld().spawnParticle(org.bukkit.Particle.WAX_OFF,
                                    sword.visual.getLocation().add(0, 0.5, 0), 5, 0.2, 0.2, 0.2, 0.1);
                            it.remove();
                            break; // One sword per person? or one person picks up? One pickup only.
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 4L); // Check every 0.2s
    }

    private void giveSword(Player p, StuckSword sword) {
        if (p.getInventory().firstEmpty() == -1) {
            p.sendMessage("§c인벤토리가 가득 차 검을 주울 수 없습니다.");
            return;
        }

        ItemStack item;
        if (p.getUniqueId().equals(sword.ownerUUID)) {
            item = new ItemStack(Material.DIAMOND_SWORD);
        } else {
            item = new ItemStack(Material.STONE_SWORD);
        }

        p.getInventory().addItem(item);
        // p.sendMessage("§7검을 획득했습니다.");
    }
}
