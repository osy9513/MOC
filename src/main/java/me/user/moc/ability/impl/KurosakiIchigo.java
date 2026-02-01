package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class KurosakiIchigo extends Ability {

    private final Set<UUID> isHollow = new HashSet<>();

    public KurosakiIchigo(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "038";
    }

    @Override
    public String getName() {
        return "쿠로사키 이치고";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§e전투 ● §f쿠로사키 이치고 (블리치)",
                "§f호로의 힘을 빌려 잠시 동안 강해집니다.",
                "§7[우클릭] §f참월(철검)을 사용하여 15초 동안 호로화 (신속2, 재생2)",
                "§f- 공격 시 대상 근처로 순간이동",
                "§f- 종료 시 5초간 구속 3",
                "§8쿨타임: 30초");
    }

    @Override
    public void giveItem(Player p) {
        detailCheck(p);

        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b참월");
            meta.setUnbreakable(true);
            sword.setItemMeta(meta);
        }
        p.getInventory().addItem(sword);
    }

    @EventHandler
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        if (e.getAction().name().contains("RIGHT")) {
            ItemStack item = e.getItem();
            if (item != null && item.getType() == Material.IRON_SWORD && item.getItemMeta() != null
                    && "§b참월".equals(item.getItemMeta().getDisplayName())) {
                onUse(p);
            }
        }
    }

    public void onUse(Player p) {
        if (!checkCooldown(p))
            return;

        // 호로화 발동
        if (!isHollow.contains(p.getUniqueId())) {
            activateHollow(p);
            setCooldown(p, 30);
        } else {
            p.sendMessage("§c이미 호로화 상태입니다.");
        }
    }

    private void activateHollow(Player p) {
        isHollow.add(p.getUniqueId());
        p.getServer().broadcastMessage("§c§l쿠로사키 이치고 : §f내가 지킨다!!");
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f);

        // 지속 효과 (15초)
        // 신속 2, 재생 2
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 300, 1));

        // 파티클 효과 (검붉은색 연기 & 번개)
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!p.isOnline() || !isHollow.contains(p.getUniqueId()) || ticks >= 15 * 5) { // 15초 동안 (0.2초마다 실행) ->
                                                                                               // 75번
                    cancel();
                    if (isHollow.contains(p.getUniqueId())) {
                        deactivateHollow(p);
                    }
                    return;
                }

                // 검붉은색 연기
                // 1.21: SMOKE_LARGE -> CAMPFIRE_COSY_SMOKE or SMOKE?
                // Particle.SMOKE_LARGE exists in 1.21 but error says cannot find symbol?
                // Maybe it's just SMOKE and use extra data?
                // Let's use CAMPFIRE_SIGNAL_SMOKE or similar if SMOKE_LARGE is gone.
                // Actually, SMOKE_LARGE is likely LEGACY or removed. Use SMOKE + extra
                // speed/count or CAMPFIRE_COSY_SMOKE.
                // Trying CAMPFIRE_COSY_SMOKE (appears as white smoke, maybe not ideal).
                // Or try SQUID_INK (black smoke).
                p.getWorld().spawnParticle(Particle.SQUID_INK, p.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0);
                // REDSTONE -> DUST
                p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3,
                        new Particle.DustOptions(org.bukkit.Color.RED, 1));

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 4L); // 0.2초마다

        // 15초 후 자동 해제
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isHollow.contains(p.getUniqueId())) {
                    deactivateHollow(p);
                }
            }
        }.runTaskLater(plugin, 300L);
    }

    private void deactivateHollow(Player p) {
        isHollow.remove(p.getUniqueId());
        p.sendMessage("§7호로화가 해제되었습니다.");
        // 구속 3 부여 (5초) -> SLOW 는 구버전, SLOW_FALLING 아님, SLOWNESS임. 1.20+ Type: SLOWNESS
        // 1.21: PotionEffectType.SLOWNESS
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && isHollow.contains(p.getUniqueId())) {
            // 호로화 상태 공격 시
            if (e.getEntity() instanceof LivingEntity target) {
                // 공격 대상 뒤로 순간이동 (앞 1.5칸)
                Vector dir = p.getLocation().getDirection().setY(0).normalize(); // Y축 제외하여 수평 이동
                Location teleLoc = target.getLocation().add(dir.multiply(1.5));

                // 블럭 끼임 방지 (도착 지점이 고체면 대상의 위치로)
                if (teleLoc.getBlock().getType().isSolid()
                        || teleLoc.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                    teleLoc = target.getLocation();
                }

                teleLoc.setYaw(p.getLocation().getYaw()); // 시선 유지
                teleLoc.setPitch(p.getLocation().getPitch());
                p.teleport(teleLoc);

                p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f);
            }
        }
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        isHollow.remove(p.getUniqueId());
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● §f쿠로사키 이치고 (블리치)");
        p.sendMessage("§f호로의 힘을 빌려 잠시 동안 강해집니다.");
        p.sendMessage("§7[우클릭] §f참월을 사용하여 15초 동안 호로화 (신속2, 재생2)");
        p.sendMessage("§f- 공격 시 대상 근처로 순간이동");
        p.sendMessage("§f- 종료 시 5초간 구속 3");
        p.sendMessage("§8쿨타임: 30초");
    }
}
