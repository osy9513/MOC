package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class SpongeBob extends Ability {

    private final Map<UUID, ItemStack> originalHelmets = new HashMap<>();
    private final Set<UUID> cookingPlayers = new HashSet<>();

    public SpongeBob(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "051";
    }

    @Override
    public String getName() {
        return "스펀지밥";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§e유틸 ● 스펀지밥(네모바지 스폰지밥)",
                "§f게살버거를 만들 수 있어요!");
    }

    @Override
    public void giveItem(Player p) {
        ItemStack spatula = new ItemStack(Material.IRON_SHOVEL);
        ItemMeta meta = spatula.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f뒤집개");
            meta.setLore(Arrays.asList("§f블럭을 5초간 우클릭하여 차징하면", "§f게살버거를 만듭니다!"));
            meta.setCustomModelData(1); // 리소스팩: spongebob
            spatula.setItemMeta(meta);
        }
        p.getInventory().addItem(spatula);

        // [추가] 기본 지급되는 구운 소고기 제거 (요청사항)
        p.getInventory().remove(Material.COOKED_BEEF);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e유틸 ● 스펀지밥(네모바지 스폰지밥)");
        p.sendMessage("§f뒤집개로 아무 블럭이나 5초간 키를 누르고 있으면");
        p.sendMessage("§f해당 블럭에서 게살버거가 한개 나옵니다!");
        p.sendMessage("§f게살버거 우클릭 시 즉시 섭취하며 배고픔, 행운, 신속, 힘을 얻습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쉬프트를 누르면 머리에 스펀지를 착용하며,");
        p.sendMessage("§f주변 5x5 범위의 물을 흡수합니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 5초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 뒤집개");
        p.sendMessage("§f장비 제거 : 철 칼, 구운 소고기 64개");
    }

    @Override
    public void cleanup(Player p) {
        if (originalHelmets.containsKey(p.getUniqueId())) {
            ItemStack helmet = originalHelmets.remove(p.getUniqueId());
            if (p.getInventory().getHelmet() != null && p.getInventory().getHelmet().getType() == Material.SPONGE) {
                p.getInventory().setHelmet(helmet);
            } else if (p.getInventory().getHelmet() == null) {
                // If they have no helmet now, but we saved one (or null), restore it?
                // Wait, if they swaped helmet manually?
                // Safe bet: Only restore if they still have the sponge we gave them?
                // Or just force restore.
                p.getInventory().setHelmet(helmet);
            }
        }
        cookingPlayers.remove(p.getUniqueId());
        super.cleanup(p);
    }

    @Override
    public void reset() {
        originalHelmets.clear();
        cookingPlayers.clear();
        super.reset();
    }

    @EventHandler
    public void onConvertInteract(PlayerInteractEvent e) {
        // Handle Krabby Patty eating
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player p = e.getPlayer();
            if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
                return;

            ItemStack item = e.getItem();
            if (item != null && item.getType() == Material.COOKED_BEEF && item.hasItemMeta()
                    && "§f게살버거".equals(item.getItemMeta().getDisplayName())) {

                e.setCancelled(true);
                item.setAmount(item.getAmount() - 1);

                p.setFoodLevel(Math.min(p.getFoodLevel() + 5, 20));
                p.setSaturation(Math.min(p.getSaturation() + 5, 20));

                // 15 seconds = 300 ticks
                p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 300, 4)); // 행운 5
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 4)); // 신속 5
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 0)); // 힘 1

                p.sendMessage("§d햄버거 좋아~");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1f);
                return;
            }
        }
    }

    @EventHandler
    public void onCookInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR)
            return;

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.IRON_SHOVEL)
            return;
        if (!item.hasItemMeta() || !"§f뒤집개".equals(item.getItemMeta().getDisplayName()))
            return;

        // Check if already cooling down?
        if (!checkCooldown(p))
            return;

        if (cookingPlayers.contains(p.getUniqueId()))
            return;

        startCooking(p, e.getClickedBlock());
    }

    private void startCooking(Player p, Block b) {
        if (b == null)
            return;

        cookingPlayers.add(p.getUniqueId());
        Location startLoc = p.getLocation();

        // 5 seconds task
        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || !cookingPlayers.contains(p.getUniqueId())) {
                    cookingPlayers.remove(p.getUniqueId());
                    cancel();
                    return;
                }

                // Movement Check
                if (p.getLocation().distanceSquared(startLoc) > 0.5) {
                    p.sendMessage("§c움직여서 요리가 취소되었습니다.");
                    cookingPlayers.remove(p.getUniqueId());
                    cancel();
                    return;
                }

                // Item Check
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType() != Material.IRON_SHOVEL || !hand.hasItemMeta()
                        || !"§f뒤집개".equals(hand.getItemMeta().getDisplayName())) {
                    p.sendMessage("§c뒤집개를 들고 있어야 합니다.");
                    cookingPlayers.remove(p.getUniqueId());
                    cancel();
                    return;
                }

                // Particle/Sound
                if (ticks % 10 == 0) {
                    p.playSound(p.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.5f, 1.5f);
                    p.spawnParticle(Particle.SMOKE, b.getLocation().add(0.5, 1, 0.5), 5, 0.2, 0.2, 0.2, 0);
                }

                ticks += 5;
                if (ticks >= 100) { // 5 seconds
                    completeCooking(p, b);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
        registerTask(p, task);
    }

    private void completeCooking(Player p, Block b) {
        cookingPlayers.remove(p.getUniqueId());

        setCooldown(p, 5);

        ItemStack burger = new ItemStack(Material.COOKED_BEEF);
        ItemMeta meta = burger.getItemMeta();
        meta.setDisplayName("§f게살버거");
        meta.setLore(Arrays.asList("§7섭취 시 강력한 버프를 얻습니다."));
        meta.setCustomModelData(1); // 리소스팩: spongebob2
        burger.setItemMeta(meta);

        p.getWorld().dropItem(b.getLocation().add(0.5, 1, 0.5), burger);
        Bukkit.broadcastMessage("§e스펀지밥: 주문하신 게살버거 나왔습니다!");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR)
            return;

        if (e.isSneaking()) {
            // Start Sneaking
            ItemStack helmet = p.getInventory().getHelmet();
            if (helmet == null || helmet.getType() != Material.SPONGE) {
                originalHelmets.put(p.getUniqueId(), helmet); // Check if we already have it?
                // A logic flaw: if they sneak, unsneak, sneak fast?
                // If containsKey, we already have their 'true' helmet. Don't overwrite with
                // Sponge.
            }
            p.getInventory().setHelmet(new ItemStack(Material.SPONGE));

            startWaterAbsorb(p);

        } else {
            // Stop Sneaking
            if (originalHelmets.containsKey(p.getUniqueId())) {
                p.getInventory().setHelmet(originalHelmets.get(p.getUniqueId()));
                originalHelmets.remove(p.getUniqueId());
            } else {
                // If for some reason we missed the start, but helper is sponge?
                // Better to clear if it is sponge
                if (p.getInventory().getHelmet() != null && p.getInventory().getHelmet().getType() == Material.SPONGE) {
                    p.getInventory().setHelmet(null);
                }
            }
        }
    }

    private void startWaterAbsorb(Player p) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || !p.isSneaking()) {
                    cancel();
                    return;
                }

                Location loc = p.getLocation();
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        for (int y = -1; y <= 3; y++) { // Height range check? "5x5 radius" implies area. I'll simply do
                                                        // -2 to +2 y as well
                            Block b = loc.clone().add(x, y, z).getBlock();
                            if (b.getType() == Material.WATER) {
                                b.setType(Material.AIR);
                                p.getWorld().spawnParticle(Particle.SPLASH, b.getLocation().add(0.5, 0.5, 0.5), 5);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
        registerTask(p, task);
    }
}
