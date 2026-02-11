package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Windbreaker extends Ability {

    private final Map<UUID, Long> lastSneakTime = new HashMap<>();

    public Windbreaker(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "031";
    }

    @Override
    public String getName() {
        return "윈드브레이커";
    }

    @Override
    public List<String> getDescription() {
        List<String> lore = new ArrayList<>();
        lore.add("§b전투 ● §f윈드브레이커 (메이플 스토리)");
        lore.add("§f천공의 노래를 사용합니다.");
        return lore;
    }

    @Override
    public void giveItem(Player p) {
        // 1. 기존 철 칼 제거
        p.getInventory().remove(Material.IRON_SWORD);

        // 2. 메이플 보우 지급
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§a메이플 보우"));
            meta.setLore(List.of("§7웅크리기 2회 연타 시 '천공의 노래'를 시전합니다.", "§75초간 전방으로 화살을 난사합니다.", "§8(쿨타임 15초)"));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.setCustomModelData(1); // 리소스팩: windbreaker
            bow.setItemMeta(meta);
        }
        // 무한 인챈트 부여 (화살 1개 필요)
        bow.addEnchantment(Enchantment.INFINITY, 1);
        p.getInventory().addItem(bow);

        // 3. 화살 1개 지급 (무한 활 사용을 위해 필수)
        if (!p.getInventory().contains(Material.ARROW)) {
            p.getInventory().addItem(new ItemStack(Material.ARROW));
        }

        // 4. 신속 1 버프 (상시)
        applyPassiveBuff(p);
    }

    private void applyPassiveBuff(Player p) {
        // 죽거나 효과가 사라질 수 있으므로 반복 태스크로 유지
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead())
                    return;
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, true, true, true));
            }
        }.runTaskTimer(plugin, 0L, 20L);
        registerTask(p, task);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§a전투 ● 윈드브레이커(메이플스토리)");
        p.sendMessage("§f활을 들고 쉬프트를 빠르게 두 번 누르면 '천공의 노래'가 발동됩니다.");
        p.sendMessage("§f5초 동안 전방으로 화살을 50발 발사하며, 신속 I 효과를 상시 받습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 15초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 메이플 보우, 화살 1개");
        p.sendMessage("§f장비 제거 : 철 검");
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!e.isSneaking())
            return;

        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR)
            return;

        if (!me.user.moc.ability.AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
            return;
        }

        ItemStack mainHand = p.getInventory().getItemInMainHand();
        if (mainHand.getType() != Material.BOW)
            return;

        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastSneakTime.getOrDefault(uuid, 0L);

        if (now - last < 300) {
            startSongOfHeaven(p);
            lastSneakTime.remove(uuid);
        } else {
            lastSneakTime.put(uuid, now);
        }
    }

    private void startSongOfHeaven(Player p) {
        if (!checkCooldown(p))
            return;
        setCooldown(p, 15);
        p.getServer().broadcastMessage("§a윈드브레이커 : 천공의 노래!");

        BukkitTask rapidFire = new BukkitRunnable() {
            int count = 0;
            // 2틱(0.1초)마다 발사. 5초 = 50회.

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    this.cancel();
                    return;
                }
                if (count >= 50) {
                    this.cancel();
                    return;
                }

                launchGreenArrow(p);
                p.playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1.5f);
                count++;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        registerTask(p, rapidFire);
    }

    private void launchGreenArrow(Player p) {
        Arrow arrow = p.launchProjectile(Arrow.class);
        arrow.setDamage(2.0);
        arrow.setVelocity(p.getLocation().getDirection().multiply(2.5));
        arrow.setShooter(p);

        registerSummon(p, arrow);

        BukkitTask trailTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (arrow.isDead() || arrow.isOnGround()) {
                    this.cancel();
                    return;
                }
                // 초록색 파티클 (VILLAGER_HAPPY -> HAPPY_VILLAGER in 1.20+)
                p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, arrow.getLocation(), 1, 0, 0, 0, 0);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        registerTask(p, trailTask);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!me.user.moc.ability.AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
            return;
        }

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.BOW)
            return;

        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // 화살이 없으면 지급하여 발사 가능하게 함
            if (!p.getInventory().contains(Material.ARROW)) {
                p.getInventory().addItem(new ItemStack(Material.ARROW));
            }
        }
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        lastSneakTime.remove(p.getUniqueId());
        p.removePotionEffect(PotionEffectType.SPEED);
    }
}
