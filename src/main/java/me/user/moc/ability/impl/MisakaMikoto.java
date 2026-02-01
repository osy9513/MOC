package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import org.bukkit.Color;
import org.bukkit.Particle.DustOptions;

import java.util.Arrays;
import java.util.List;

import org.bukkit.GameMode;

public class MisakaMikoto extends Ability {

    public MisakaMikoto(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "미사카 미코토";
    }

    @Override
    public String getCode() {
        return "034";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§e전투 ● §f미사카 미코토 (어떤 과학의 초전자포)",
                "§f레일건을 쏩니다.",
                "§7[우클릭] §f코인(프리즈머린 수정) 1개를 소모하여",
                "§f전면 직선으로 레일건을 발사합니다. (사거리 15, 대미지 8)",
                "§7[전력] §f전력(네더의 별)은 소모되지 않으며 15초마다 사용 가능합니다.",
                "§f3초간 차징 후 엄청난 위력의 전기를 발사합니다. (사거리 50, 대미지 28)",
                "§8쿨타임: 3초 (코인), 15초 (전력)");
    }

    @Override
    public void giveItem(Player p) {
        // 코인 10개 지급
        ItemStack coin = new ItemStack(Material.PRISMARINE_CRYSTALS, 10);
        ItemMeta meta = coin.getItemMeta();
        meta.setDisplayName("§b코인");
        coin.setItemMeta(meta);
        p.getInventory().addItem(coin);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 양손 이벤트 중복 방지
        if (e.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND)
            return;

        ItemStack item = e.getItem();
        if (item == null)
            return;

        // 1. 코인 사용 (레일건)
        if (item.getType() == Material.PRISMARINE_CRYSTALS) {
            if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                // 쿨타임 체크
                if (!checkCooldown(p))
                    return;

                // 코인 1개 소모
                item.setAmount(item.getAmount() - 1);

                // 레일건 발사
                fireRailgun(p);

                // [▼▼▼ 수정됨: 코인 쿨타임 3초 ▼▼▼]
                setCooldown(p, 3);
                // [▲▲▲ 여기까지 수정됨 ▲▲▲]

                // 코인 전부 소모 체크
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        checkCoinsEmpty(p);
                    }
                }.runTaskLater(plugin, 1L);
            }
        }

        // 2. 전력 사용 (네더의 별)
        if (item.getType() == Material.NETHER_STAR) {
            if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                if (item.getItemMeta() != null && "§e전력".equals(item.getItemMeta().getDisplayName())) {

                    // [▼▼▼ 수정됨: 전력 쿨타임 및 소모 제거 ▼▼▼]
                    // 쿨타임 체크 (코인 쿨타임과 공유됨)
                    if (!checkCooldown(p))
                        return;

                    // 아이템 소모 코드 삭제됨 (무한 사용)
                    // item.setAmount(item.getAmount() - 1);

                    fireFullPower(p);
                    setCooldown(p, 15); // 전력 쿨타임 15초
                    // [▲▲▲ 여기까지 수정됨 ▲▲▲]
                }
            }
        }
    }

    private void checkCoinsEmpty(Player p) {
        int totalCoins = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == Material.PRISMARINE_CRYSTALS) {
                if (item.getItemMeta() != null && "§b코인".equals(item.getItemMeta().getDisplayName())) {
                    totalCoins += item.getAmount();
                }
            }
        }

        if (totalCoins <= 0) {
            // [수정] 안내 메시지 변경
            p.sendMessage("§b[MOC] §f코인을 모두 소모했습니다. 15초 후 전력을 사용할 수 있습니다.");

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
                        ItemStack fullPower = new ItemStack(Material.NETHER_STAR);
                        ItemMeta meta = fullPower.getItemMeta();
                        meta.setDisplayName("§e전력");
                        fullPower.setItemMeta(meta);
                        p.getInventory().addItem(fullPower);
                        p.sendMessage("§e[MOC] §f전력(네더의 별)이 활성화되었습니다!");
                        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
                    }
                }
            }.runTaskLater(plugin, 300L); // 15초 대기
        }
    }

    // 일반 레일건
    private void fireRailgun(Player p) {
        Bukkit.broadcastMessage("§b미사카 미코토 : §f있지, 레일건이라는 말 알아?");
        p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f);

        // [안전한 파티클 유지] CRIT (오류 없음)
        p.getWorld().spawnParticle(Particle.CRIT, p.getEyeLocation(), 10, 0.5, 0.5, 0.5, 0.1);

        var result = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 15, 0.5,
                e -> e instanceof LivingEntity && e != p
                        && !(e instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR));

        Vector dir = p.getEyeLocation().getDirection();
        for (int i = 0; i < 15; i++) {
            // [안전한 파티클 유지] END_ROD (오류 없음)
            p.getWorld().spawnParticle(Particle.END_ROD, p.getEyeLocation().add(dir.clone().multiply(i)), 1, 0, 0, 0,
                    0);
        }

        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            target.damage(8.0, p);
            target.getWorld().spawnParticle(Particle.FLASH, target.getLocation(), 1);
        }
    }

    // 전력 발사
    private void fireFullPower(Player p) {
        Bukkit.broadcastMessage("§b미사카 미코토 : §e§l이게 나의 전력이다!!!");

        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 3) {
                    shootFullPowerVerify(p);
                    cancel();
                    return;
                }

                p.getWorld().strikeLightningEffect(p.getLocation());
                // [안전한 파티클 유지] CRIT
                p.getWorld().spawnParticle(Particle.CRIT, p.getLocation(), 20, 1, 1, 1, 0.1);

                count++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void shootFullPowerVerify(Player p) {
        Vector dir = p.getEyeLocation().getDirection();
        DustOptions electricOptions = new DustOptions(Color.YELLOW, 1.5f);

        for (int i = 0; i < 50; i++) {
            org.bukkit.Location particleLoc = p.getEyeLocation().add(dir.clone().multiply(i));
            p.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0.1, 0.1, 0.1, 0, electricOptions);

            if (i % 2 == 0) {
                // [안전한 파티클 유지] CRIT
                p.getWorld().spawnParticle(Particle.CRIT, particleLoc, 1, 0.3, 0.3, 0.3, 0.1);
            }
        }

        for (int i = 1; i <= 50; i++) {
            var loc = p.getEyeLocation().add(dir.clone().multiply(i));
            for (Entity e : p.getWorld().getNearbyEntities(loc, 1.5, 1.5, 1.5)) {
                if (e instanceof LivingEntity le && e != p) {
                    if (le instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR)
                        continue;

                    le.damage(28.0, p);
                    le.getWorld().strikeLightningEffect(le.getLocation());
                }
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p
                && AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
            if (e.getCause() == EntityDamageEvent.DamageCause.LIGHTNING) {
                e.setCancelled(true);
            }
        }
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● §f미사카 미코토 (어떤 과학의 초전자포)");
        p.sendMessage("§f레일건을 쏩니다.");
        p.sendMessage("§7[우클릭] §f코인 1개를 소모하여 전면 직선으로 레일건 발사 (사거리 15, 대미지 8)");
        p.sendMessage("§7[전력] §f코인을 모두 소모하면 15초 후 전력 사용 가능 (소모X)");
        p.sendMessage("§f- 3초 차징 후 대미지 28 발사 (사거리 50)");
        p.sendMessage("§8쿨타임: 3초 (코인), 15초 (전력)");
    }
}