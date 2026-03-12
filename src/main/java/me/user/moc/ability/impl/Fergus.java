package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class Fergus extends Ability {

    private final Random random = new Random();

    public Fergus(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "075";
    }

    @Override
    public String getName() {
        return "퍼거스";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c유틸 ● 퍼거스(마비노기)",
                "§f무기를 재련 합니다.");
    }

    @Override
    public void giveItem(Player p) {
        p.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c유틸 ● 퍼거스(마비노기)");
        p.sendMessage("§f검 들고 쉬프트 우클릭 시");
        p.sendMessage("§f해당 검을 43% 확률로 강화하거나 57% 확률로 실패(하락) 합니다.");
        p.sendMessage("§f재련한 검은 3초간 퍼거스만 먹을 수 있습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f[강화 리스트]");
        p.sendMessage("§f날카로움 5 네더라이트 검 ↔ 날카로움 4 네더라이트 검 ↔ 날카로움 3 네더라이트 검 ↔ 날카로움 2 네더라이트 검 ↔ 날카로움 1 네더라이트 검 ↔ 네더라이트 검");
        p.sendMessage("§f↔ 다이야 검 ↔ §e철 검 (성공 시 왼쪽으로, 실패 시 우측으로)§f ↔ 돌 검 ↔ 나무 검 ↔ 무기 삭제.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 4초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 나무 검");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        if (isSilenced(p))
            return;
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        // 쉬프트 우클릭 검사
        if (!p.isSneaking())
            return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack item = p.getInventory().getItemInMainHand();
        int tier = getSwordTier(item);

        // 검류가 아니면 무시
        if (tier == -1)
            return;

        if (!checkCooldown(p))
            return;
        setCooldown(p, 4);

        // 손에서 아이템 1개 삭제
        item.setAmount(item.getAmount() - 1);

        double rand = random.nextDouble() * 100;
        final int targetTier;
        boolean success;

        // 성공 : 43% / 실패 : 57%
        if (rand < 43.0) {
            success = true;
            targetTier = Math.min(10, tier + 1); // 최고 등급은 10 (네더라이트 날카 5)
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
        } else {
            success = false;
            targetTier = tier - 1;
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 1f, 1f);
            Bukkit.broadcastMessage("§c퍼거스 : §f**어이쿠, 손이 미끄러졌네**");
        }

        if (success) {
            p.sendMessage("§a[!] 무기 강화에 성공했습니다!");
        } else {
            p.sendMessage("§c[!] 무기 강화에 실패하여 등급이 하락했습니다.");
        }

        // 나무검에서 실패한 경우 (무기 파괴)
        if (targetTier == 0) {
            p.sendMessage("§c[!] 무기가 파괴되었습니다.");
            return;
        }

        // 해당하는 검 생성
        ItemStack resultWeapon = createSwordByTier(targetTier);

        // 머리 위 Y+4 위치에서 드롭
        org.bukkit.Location loc = p.getLocation().clone().add(0, 4, 0);
        Item dropItem = loc.getWorld().dropItem(loc, resultWeapon);
        dropItem.setVelocity(new Vector(0, -0.2, 0)); // 천천히 떨어지게 유도

        // 퍼거스 본인만 먹도록 소유권 보호 데이터 부여 (3초 = System.currentTimeMillis() + 3000)
        dropItem.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "MOC_Fergus_Owner"),
                org.bukkit.persistence.PersistentDataType.STRING,
                p.getUniqueId().toString());
        dropItem.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "MOC_ProtectTime"),
                org.bukkit.persistence.PersistentDataType.LONG,
                System.currentTimeMillis() + 3000L);
    }

    /**
     * 검의 10단계 티어를 확인합니다.
     * 1: 나무검, 2: 돌검, 3: 철검, 4: 다이아검, 5: 네더라이트검, 6~10: 날카 1~5 네더라이트
     */
    private int getSwordTier(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return -1;

        if (item.getType() == Material.WOODEN_SWORD)
            return 1;
        if (item.getType() == Material.STONE_SWORD)
            return 2;
        if (item.getType() == Material.IRON_SWORD)
            return 3;
        if (item.getType() == Material.DIAMOND_SWORD)
            return 4;

        if (item.getType() == Material.NETHERITE_SWORD) {
            int level = item.getEnchantmentLevel(Enchantment.SHARPNESS);
            if (level >= 1 && level <= 5) {
                return 5 + level;
            }
            return 5;
        }

        return -1;
    }

    private ItemStack createSwordByTier(int tier) {
        ItemStack item;
        switch (tier) {
            case 1:
                item = new ItemStack(Material.WOODEN_SWORD);
                break;
            case 2:
                item = new ItemStack(Material.STONE_SWORD);
                break;
            case 3:
                item = new ItemStack(Material.IRON_SWORD);
                break;
            case 4:
                item = new ItemStack(Material.DIAMOND_SWORD);
                break;
            default:
                item = new ItemStack(Material.NETHERITE_SWORD);
                break; // 5 이상
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            if (tier > 5) {
                int enchLevel = tier - 5; // tier 6 = level 1
                meta.addEnchant(Enchantment.SHARPNESS, enchLevel, true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // 아이템 습득 방어 (퍼거스가 아닌 다른 플레이어가 3초 안에 먹는 것 차단)
    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p))
            return;

        Item item = e.getItem();
        org.bukkit.persistence.PersistentDataContainer data = item.getPersistentDataContainer();

        org.bukkit.NamespacedKey ownerKey = new org.bukkit.NamespacedKey(plugin, "MOC_Fergus_Owner");
        org.bukkit.NamespacedKey timeKey = new org.bukkit.NamespacedKey(plugin, "MOC_ProtectTime");

        if (data.has(ownerKey, org.bukkit.persistence.PersistentDataType.STRING) &&
                data.has(timeKey, org.bukkit.persistence.PersistentDataType.LONG)) {

            String ownerUUIDStr = data.get(ownerKey, org.bukkit.persistence.PersistentDataType.STRING);
            long protectTime = data.get(timeKey, org.bukkit.persistence.PersistentDataType.LONG);

            // 시간이 지나지 않았고
            if (System.currentTimeMillis() < protectTime) {
                // 습득자가 주인이 아니라면 차단
                if (!p.getUniqueId().toString().equals(ownerUUIDStr)) {
                    e.setCancelled(true);
                }
            }
        }
    }
}
