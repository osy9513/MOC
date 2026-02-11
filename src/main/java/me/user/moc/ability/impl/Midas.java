package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.inventory.meta.ItemMeta;

public class Midas extends Ability {

    public Midas(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "003";
    }

    @Override
    public String getName() {
        return "미다스";
    }

    @Override
    public List<String> getDescription() {
        List<String> list = new ArrayList<>();
        list.add("§6유틸 ● 미다스(그리스 로마 신화)");
        list.add("§f금괴를 던져 맞춘 모든 것을 §e금§f으로 만듭니다.");
        return list;
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§6유틸 ● 미다스(그리스 로마 신화)");
        p.sendMessage("§f금괴를 우클릭하면 전방으로 던집니다.");
        p.sendMessage("§f던져진 금괴에 맞은 상대가 입고 있던 방어구는 §e금 방어구§f가 됩니다.");
        p.sendMessage("§f플레이어가 아닌 대상을 맞출 시 §e금 블럭§f으로 교체시킵니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0.5초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 금 갑옷, 금 칼, 발광포션, 황금 사과 64개, 금괴 64개");
        p.sendMessage("§f장비 제거 : 철 갑옷, 철 칼, 체력재생포션, 고기 64개");
    }

    @Override
    public void giveItem(Player p) {
        // 1. [기존 장비 제거]
        p.getInventory().remove(Material.IRON_SWORD);
        p.getInventory().remove(Material.COOKED_BEEF);
        p.getInventory().remove(Material.POTION); // 재생 포션
        p.getInventory().remove(Material.IRON_CHESTPLATE);

        // 갑옷 슬롯 비우기 (철 흉갑만)
        if (p.getInventory().getChestplate() != null &&
                p.getInventory().getChestplate().getType() == Material.IRON_CHESTPLATE) {
            p.getInventory().setChestplate(null);
        }

        // 2. [추가 장비 지급]
        // 금 칼 (불괴)
        ItemStack goldSword = new ItemStack(Material.GOLDEN_SWORD);
        ItemMeta swordMeta = goldSword.getItemMeta();
        if (swordMeta != null) {
            swordMeta.setUnbreakable(true);
            goldSword.setItemMeta(swordMeta);
        }
        p.getInventory().addItem(goldSword);

        // 금 갑옷 세트 (입혀줌)
        p.getInventory().setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
        p.getInventory().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
        p.getInventory().setLeggings(new ItemStack(Material.GOLDEN_LEGGINGS));
        p.getInventory().setBoots(new ItemStack(Material.GOLDEN_BOOTS));

        // 황금 사과 64개
        p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 64));

        // 발광 포션 (투척)
        ItemStack glowPotion = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) glowPotion.getItemMeta();
        if (meta != null) {
            meta.addCustomEffect(new PotionEffect(PotionEffectType.GLOWING, 600, 0, true, true, true), true);
            meta.setDisplayName("§e미다스의 빛");
            glowPotion.setItemMeta(meta);
        }

        // 금괴 64개
        ItemStack goldIngot = new ItemStack(Material.GOLD_INGOT, 64);
        ItemMeta goldMeta = goldIngot.getItemMeta();
        if (goldMeta != null) {
            goldMeta.setLore(Arrays.asList("§7우클릭하여 던집니다.", "§7적중 시 대상을 금으로 만듭니다."));
            goldIngot.setItemMeta(goldMeta);
        }
        p.getInventory().addItem(goldIngot);
        p.getInventory().addItem(glowPotion);

        p.updateInventory();
    }

    // 1. 금괴 투척 (우클릭)
    @EventHandler
    public void onThrow(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isMidas(p))
            return;

        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = e.getItem();
            if (item != null && item.getType() == Material.GOLD_INGOT) {
                e.setCancelled(true); // 먹거나 설치 방지

                // 쿨타임 체크 (투척 속도 제한용, 0.5초)
                if (p.hasCooldown(Material.GOLD_INGOT))
                    return;
                p.setCooldown(Material.GOLD_INGOT, 10);

                // 아이템 소모
                item.setAmount(item.getAmount() - 1);

                // 배고픔 회복 (2칸)
                p.setFoodLevel(Math.min(20, p.getFoodLevel() + 4));
                p.setSaturation(Math.min(20, p.getSaturation() + 2));
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);

                // 투사체 발사 (눈덩이 기반, 아이템 모양 변경)
                Snowball projectile = p.launchProjectile(Snowball.class);
                projectile.setItem(new ItemStack(Material.GOLD_INGOT));
                projectile.setShooter(p);

                // 메타데이터로 미다스 투사체 식별
                projectile.addScoreboardTag("midas_gold");

                p.playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 1f);
            }
        }
    }

    // 2. 투사체 적중 (능력 발동)
    @EventHandler
    public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball projectile))
            return;
        if (!projectile.getScoreboardTags().contains("midas_gold"))
            return;

        if (!(projectile.getShooter() instanceof Player p))
            return;

        // 블럭 적중
        if (e.getHitBlock() != null) {
            if (e.getHitBlock().getType() != Material.GOLD_BLOCK && e.getHitBlock().getType() != Material.BEDROCK
                    && e.getHitBlock().getType() != Material.BARRIER) {
                e.getHitBlock().setType(Material.GOLD_BLOCK);
                e.getHitBlock().getWorld().playSound(e.getHitBlock().getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                        0.5f, 1.5f);
            }
        }

        // 엔티티 적중
        if (e.getHitEntity() != null) {
            Entity target = e.getHitEntity();

            // 대미지 1 주기
            if (target instanceof LivingEntity livingTarget) {
                if (livingTarget instanceof Player
                        && ((Player) livingTarget).getGameMode() == org.bukkit.GameMode.SPECTATOR)
                    return;
                livingTarget.damage(1.0, p);
            }

            // 플레이어 적중 -> 방어구 변환
            if (target instanceof Player victim) {
                boolean changed = false;
                ItemStack[] armor = victim.getInventory().getArmorContents();

                for (int i = 0; i < armor.length; i++) {
                    ItemStack piece = armor[i];
                    if (piece != null && piece.getType() != Material.AIR) {
                        Material goldType = getGoldArmorType(piece.getType());
                        if (goldType != null && piece.getType() != goldType) {
                            armor[i].setType(goldType);
                            changed = true;
                        }
                    }
                }

                if (changed) {
                    victim.getInventory().setArmorContents(armor);
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
                    Bukkit.broadcastMessage("§e미다스 : §f반짝이는 건 언제나 옳지, 안 그래?");
                }
            }
            // 몹 적중 -> 황금 블럭으로 교체 (즉사)
            else if (target instanceof LivingEntity && !(target instanceof Player)) {
                target.remove();
                target.getLocation().getBlock().setType(Material.GOLD_BLOCK);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1f, 1f);
                p.sendMessage("§e[MOC] §f대상을 황금으로 만들었습니다.");
            }
        }
    }

    private boolean isMidas(Player p) {
        if (!MocPlugin.getInstance().getGameManager().isBattleStarted())
            return false;
        if (plugin instanceof MocPlugin moc) {
            return moc.getAbilityManager() != null && moc.getAbilityManager().hasAbility(p, getCode());
        }
        return false;
    }

    private Material getGoldArmorType(Material mat) {
        String name = mat.name();
        if (name.contains("HELMET"))
            return Material.GOLDEN_HELMET;
        if (name.contains("CHESTPLATE"))
            return Material.GOLDEN_CHESTPLATE;
        if (name.contains("LEGGINGS"))
            return Material.GOLDEN_LEGGINGS;
        if (name.contains("BOOTS"))
            return Material.GOLDEN_BOOTS;
        return null;
    }
}
