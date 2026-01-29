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
                "§e[전투] §f무한의 검제 (Infinite Sword Glitch)",
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
        p.sendMessage("§7떨어진 검을 본인이 획득 -> 다이아몬드 검");
        p.sendMessage("§7떨어진 검을 타인이 획득 -> 돌 검");
        p.sendMessage(" ");
        p.sendMessage("§7쿨타임 : 0초");
        p.sendMessage("---");
        p.sendMessage("§7추가 장비 : 없음");
        p.sendMessage("§7장비 제거 : 철 검");
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
        // 전장 범위 내 랜덤 위치 (GameManager의 spawn point 기준 적당히.. 하지만 여기선 그냥 owner 주변이나 맵 전체가
        // 나을듯)
        // Description says "전장 내 랜덤한 위치". ArenaManager knows the center/radius usually,
        // but standard is current world border or config spawn.
        // Let's use config spawn point from Main plugin if possible, or owner location
        // if not.

        // Since we can't easily access ArenaManager's radius here without a getter,
        // let's assume a reasonable range around the spawn point.
        // Or just assume the active play area is around 50-60 blocks.
        // Try to get center from config if available (via plugin instance is hard
        // without public getter chain, but I can ask owner loc)
        // Usually battle is around 0,0 or wherever players are.
        // Let's use owner location as center for now to ensure swords fall near action,
        // or purely random if players are scattered is better.
        // Requirements say "전장 내".

        // Let's pick a random x, z within 40 blocks of owner for now to be effective.
        double range = 40.0;
        double dx = (Math.random() * range * 2) - range;
        double dz = (Math.random() * range * 2) - range;

        Location spawnLoc = owner.getLocation().add(dx, 0, dz);
        spawnLoc.setY(spawnLoc.getWorld().getHighestBlockYAt(spawnLoc) + 64);

        // Spawn Arrow
        Arrow arrow = owner.getWorld().spawn(spawnLoc, Arrow.class);
        arrow.setShooter(owner); // Shooter makes it not damage owner usually? No, arrow self-damage exists.
        arrow.setPierceLevel(127); // High pierce
        arrow.setDamage(5.0); // Base damage, but we'll override in event
        arrow.setVelocity(new Vector(0, -3, 0)); // Fast drop
        arrow.setMetadata(KEY_UBW_SWORD, new FixedMetadataValue(plugin, owner.getUniqueId().toString()));
        arrow.setSilent(true);
        arrow.setCritical(true); // Particle effect

        // Visual: ItemDisplay riding Arrow
        ItemDisplay itemDisplay = owner.getWorld().spawn(spawnLoc, ItemDisplay.class);
        itemDisplay.setItemStack(new ItemStack(Material.IRON_SWORD));
        // Rotate to look down
        itemDisplay.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f((float) Math.toRadians(180), 1, 0, 0), // Rotate 180 x
                new Vector3f(1, 1, 1),
                new AxisAngle4f()));

        arrow.addPassenger(itemDisplay);

        registerSummon(owner, arrow);
        registerSummon(owner, itemDisplay);
    }

    // === Event Listeners ===

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (e.getEntity() instanceof Arrow arrow && arrow.hasMetadata(KEY_UBW_SWORD)) {
            // Remove visual passenger
            if (!arrow.getPassengers().isEmpty()) {
                arrow.getPassengers().forEach(Entity::remove);
            }

            // If hit block -> Drop Item
            if (e.getHitBlock() != null) {
                // Drop sword item
                Location dropLoc = arrow.getLocation();
                ItemStack swordItem = new ItemStack(Material.IRON_SWORD); // Visual item on ground
                // We need to mark this item so we know to convert it when picked up
                org.bukkit.entity.Item droppedItem = dropLoc.getWorld().dropItem(dropLoc, swordItem);
                droppedItem.setMetadata(KEY_UBW_SWORD,
                        new FixedMetadataValue(plugin, arrow.getMetadata(KEY_UBW_SWORD).get(0).value()));

                arrow.remove();
            }

            // If hit entity (handled in EntityDamageByEntity generally, but ProjectileHit
            // also fires)
            // Arrow with pierce persists, so we don't remove it yet if it hits entity.
            // But wait, "on block hit -> itemize". "if entity hit -> pierce".
            // Piercing arrow hits entity -> event fires -> arrow continues.
            // If it hits block -> stops -> event fires.
        }

        // Handle Shooting Sword (Launched by player)
        if (e.getEntity() instanceof Arrow arrow && arrow.hasMetadata(KEY_SHOOT_SWORD)) {
            if (e.getHitBlock() != null) {
                // Drop item
                Location dropLoc = arrow.getLocation();
                ItemStack swordItem = new ItemStack(Material.IRON_SWORD);
                org.bukkit.entity.Item droppedItem = dropLoc.getWorld().dropItem(dropLoc, swordItem);
                droppedItem.setMetadata(KEY_UBW_SWORD,
                        new FixedMetadataValue(plugin, arrow.getMetadata(KEY_SHOOT_SWORD).get(0).value())); // Recycle
                                                                                                            // as UBW
                                                                                                            // sword for
                                                                                                            // pickup
                arrow.remove();
            }
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

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && e.getItem().hasMetadata(KEY_UBW_SWORD)) {
            String ownerUUIDStr = e.getItem().getMetadata(KEY_UBW_SWORD).get(0).asString();
            UUID ownerUUID = UUID.fromString(ownerUUIDStr);

            e.setCancelled(true); // Cancel default pickup
            e.getItem().remove(); // Remove world item

            ItemStack newItem;
            if (p.getUniqueId().equals(ownerUUID)) {
                // Owner gets Diamond Sword
                newItem = new ItemStack(Material.DIAMOND_SWORD);
            } else {
                // Others get Stone Sword
                newItem = new ItemStack(Material.STONE_SWORD);
            }

            // Mark the item as a UBW sword so Emiya can shoot it (and maybe others can't?)
            // Requirement: "Emiya Shirou can right click ALL swords to shoot" -> "Every
            // sword he picks up"? Or just these swords?
            // "Emiya Shirou is able to shoot all swords by right clicking immediately like
            // crossbow" -> The wording implies specifically these dropped swords or maybe
            // any sword?
            // Prompt: "Emiya Shirou can right click all swords...".
            // "Fallen swords converted to Diamond... Others Stone... Emiya can right click
            // all swords".
            // It says "all swords" contextually might mean "these swords".
            // Let's verify: "Dropped swords when picked up by Emiya -> Diamond. By others
            // -> Stone."
            // "Emiya can right click all swords...".
            // Text says "모든 검을 우클릭하여".
            // Let's add lore or NBT to identify it as a UBW projectile capable sword, OR
            // just check if player is Emiya and holding a sword.
            // Simpler: Check if player is Emiya in InteractEvent.

            // Add to inventory
            p.getInventory().addItem(newItem);
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1f);
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
            // Visual
            ItemDisplay itemDisplay = p.getWorld().spawn(arrow.getLocation(), ItemDisplay.class);
            itemDisplay.setItemStack(new ItemStack(Material.IRON_SWORD)); // Visual always iron? Or matches held item?
            // Prompt says: "Hit target takes damage same as that sword".
            // "Matches sword damage"?
            // "Fallen sword -> Diamond (7 dmg) / Stone (5 dmg)".
            // "Target hit by thrown sword takes damage same as that sword".
            // We should set damage based on material.
            Material mat = handItem.getType();
            // Prompt says "Falling sword 5 hearts (10 damage)".
            // "Thrown sword... same damage as that sword".
            // If Diamond Sword is 7 damage. Stone is 5 damage.
            // We'll use vanilla values or just map them.
            arrow.setDamage(getSwordDamage(mat));

            // Visual adjustment
            itemDisplay.setItemStack(new ItemStack(mat));
            itemDisplay.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f((float) Math.toRadians(-90), 1, 0, 0), // Adjust for projectile (point forward)
                    new Vector3f(1, 1, 1),
                    new AxisAngle4f()));
            arrow.addPassenger(itemDisplay);
            p.playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.5f);

            registerSummon(p, arrow); // Track for cleanup
            registerSummon(p, itemDisplay);
        }
    }

    private double getSwordDamage(Material mat) {
        switch (mat) {
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
    }
}
