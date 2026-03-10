package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.List;

public class Dodongchan extends Ability {

    public Dodongchan(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "H07";
    }

    @Override
    public String getName() {
        return "도동찬";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList("§d히든 ● 도동찬(바집소)", "§f동!!!!");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§d히든 ● 도동찬(바집소)");
        p.sendMessage("§f배고픈 동찬이가 된다. 검은 정장이 잘 어울린다.");
        p.sendMessage("§f동까스 망치를 우클릭 시 돌풍구를 쏜다.");
        p.sendMessage("§f배고프다. 허기 255 상시 유지");
        p.sendMessage("§f든든하다. 저항 2 상시 유지");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 10초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 정장 풀세트, 동까스 망치, 구운 소고기 128개");
        p.sendMessage("§f장비 제거 : 철 칼, 철 흉갑");
    }

    @Override
    public void giveItem(Player p) {
        // 기존 철 칼, 철 흉갑 제거
        p.getInventory().remove(Material.IRON_SWORD);
        if (p.getInventory().getChestplate() != null
                && p.getInventory().getChestplate().getType() == Material.IRON_CHESTPLATE) {
            p.getInventory().setChestplate(null);
        }
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item != null && item.getType() == Material.IRON_CHESTPLATE) {
                p.getInventory().setItem(i, null);
            }
        }

        // 동사장님의 보호구 세트 (검은색 가죽 풀세트) - 귀속 저주, 소실 저주 적용
        ItemStack helmet = createLeatherArmor(Material.LEATHER_HELMET, "§f동사장님의 모자");
        ItemStack chest = createLeatherArmor(Material.LEATHER_CHESTPLATE, "§f동사장님의 겉옷");
        ItemStack legs = createLeatherArmor(Material.LEATHER_LEGGINGS, "§f동사장님의 바지");
        ItemStack boots = createLeatherArmor(Material.LEATHER_BOOTS, "§f동사장님의 신발");

        p.getInventory().setHelmet(helmet);
        p.getInventory().setChestplate(chest);
        p.getInventory().setLeggings(legs);
        p.getInventory().setBoots(boots);

        // 동까스 망치 - 귀속 저주, 소실 저주 적용
        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta maceMeta = mace.getItemMeta();
        if (maceMeta != null) {
            maceMeta.setDisplayName("§f동까스 망치");
            maceMeta.setLore(Arrays.asList("§f상영이와 원크의 피가 묻은듯 하다"));
            maceMeta.setUnbreakable(true);
            maceMeta.addEnchant(Enchantment.SHARPNESS, 2, true);
            maceMeta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
            maceMeta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            mace.setItemMeta(maceMeta);
        }
        p.getInventory().addItem(mace);

        // 구운 소고기 128개 지급
        p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 64));
        p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 64));

        // 패시브: 저항 2, 허기 255 영구 지속
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 1, false, false, true));
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.HUNGER, PotionEffect.INFINITE_DURATION, 254, false, false, true));

        // 게임 시작 시 배고픔 3칸(6포인트)인 채로 시작
        p.setFoodLevel(6);
        p.setSaturation(0f);

        // 아이템 지급 시 상세 설명 출력
        detailCheck(p);
    }

    /**
     * 가죽 보호구를 생성하는 도우미 메서드. 검은색 염색, 귀속 저주, 소실 저주를 적용합니다.
     */
    private ItemStack createLeatherArmor(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setColor(Color.BLACK);
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 해당 플레이어가 이 능력을 소유하고 있는지 확인하는 메서드
     */
    private boolean hasAbility(Player p) {
        return me.user.moc.ability.AbilityManager.getInstance().hasAbility(p, getCode());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 1. 관전자 및 크리에이티브 여부, 침묵 및 능력 소유 여부 검사
        if (isSilenced(p))
            return;
        if (!hasAbility(p))
            return;

        // 2. 맨손 클릭 등 허공/블록 우클릭 검사
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        // 3. 동까스 망치 아이템 검사
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.MACE)
            return;
        if (!item.hasItemMeta() || item.getItemMeta() == null || !item.getItemMeta().hasDisplayName())
            return;
        if (!item.getItemMeta().getDisplayName().equals("§f동까스 망치"))
            return;

        e.setCancelled(true);

        // 4. 쿨타임 검사 및 부여
        if (!checkCooldown(p))
            return;
        setCooldown(p, 10);

        // 능력 발동 로직 (돌풍구 발사)
        plugin.getServer().broadcastMessage("§f동찬 : 도동파~");

        // 돌풍구를 플레이어가 바라보는 방향으로 발사
        WindCharge windCharge = p.launchProjectile(WindCharge.class);

        // 투사체에 주인의 메타데이터 추가 (킬 판정)
        windCharge.setMetadata("SungJinWooOwner",
                new org.bukkit.metadata.FixedMetadataValue(plugin, p.getUniqueId().toString()));

        // 흰색 오라(END_ROD 파티클) 1~2초간 몸에서 출력
        BukkitRunnable auraTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                // 30틱 = 1.5초
                if (ticks >= 30 || !p.isOnline() || p.isDead()) {
                    this.cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 10, 0.4, 0.5, 0.4, 0);
                ticks += 2;
            }
        };
        activeTasks.computeIfAbsent(p.getUniqueId(), k -> new java.util.ArrayList<>())
                .add(auraTask.runTaskTimer(plugin, 0L, 2L));
    }

    /**
     * 라운드 종료 및 능력 해제 시 호출되는 정리 메서드
     */
    @Override
    public void cleanup(Player p) {
        // 부모 클래스의 기본 cleanup(스케줄러 취소 등) 호출
        super.cleanup(p);

        if (p == null || !p.isOnline())
            return;

        // 패시브로 부여된 영구 포션 효과 제거
        p.removePotionEffect(PotionEffectType.RESISTANCE);
        p.removePotionEffect(PotionEffectType.HUNGER);

        // 배고픔 3칸 시작을 위해 변경했던 포만감/허기 수치 원상 복구 (기본값)
        p.setFoodLevel(20);
        p.setSaturation(5.0f); // 바닐라의 포화도 기본값(대략)
    }
}
