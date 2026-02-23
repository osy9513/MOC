package me.user.moc.ability.impl;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;

public class KingHassan extends Ability {

    public KingHassan(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "008";
    }

    @Override
    public String getName() {
        return "산의 노인";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§e유틸 ● 산의 노인(FATE)",
                "§f산의 노인으로 변합니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§5전투 ● 산의 노인(FATE)");
        p.sendMessage("§f라운드 시작 시 §7[은신, 구속 3, 재생] §f버프를 영구히 얻습니다.");
        p.sendMessage("§f최대 체력이 §c10칸(20HP)§f으로 고정되며, 핫바가 §b첫 번째 칸§f으로 고정됩니다.");
        p.sendMessage("§f다른 아이템은 사용할 수 없으며, §5산의 노인의 대검§f은 방어력을 무시합니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 산의 노인의 대검");
        p.sendMessage("§f장비 제거 : 모든 기본 지급 장비");
    }

    @Override
    public void giveItem(Player p) {
        // 1. 기존 아이템 제거
        p.getInventory().clear();
        p.getInventory().setHelmet(null);
        p.getInventory().setChestplate(null);
        p.getInventory().setLeggings(null);
        p.getInventory().setBoots(null);

        // 2. 능력 전용 아이템 지급
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName("§5산의 노인의 대검");
        meta.setLore(List.of("§7기본 공격 시 방어력을 무시합니다.", "§c방어력 무시 32 대미지"));
        meta.setUnbreakable(true);
        meta.setCustomModelData(2); // 리소스팩: kinghassan
        sword.setItemMeta(meta);

        // 첫 번째 칸에 지급 및 선택 강제
        p.getInventory().setItem(0, sword);
        p.getInventory().setHeldItemSlot(0);

        // 3. 스탯 및 버프 적용
        if (p.getAttribute(Attribute.MAX_HEALTH) != null) {
            p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
        }
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());

        // 버프 적용
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 2, false, false));
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 0, false, false));

        // 4. 이펙트 및 메시지 출력
        Bukkit.broadcastMessage("§5산의 노인 : §f듣거라. 만종은 그대의 이름을 가리켰다.");
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);

        // [수정됨] 등장(리스폰) 시 연기 이펙트 제거
        // 기존의 particleTask 부분을 삭제하여 죽은 뒤 부활할 때 시야를 가리는 효과를 없앴습니다.
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        if (p.isOnline()) {
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            p.removePotionEffect(PotionEffectType.SLOWNESS); // @@@@@@@@@@@@@@@
            p.removePotionEffect(PotionEffectType.REGENERATION);
            p.sendMessage("§c사망하여 산의 노인 버프가 해제되었습니다."); // 메시지 출력
            // 최대 체력 원상 복구는 AbilityManager에서 처리하므로 여기서는 제거
            // if (p.getAttribute(Attribute.MAX_HEALTH) != null) {
            // p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
            // }
        }
    }

    // === [이벤트 핸들러] ===

    // 1. 공격 로직: 방어력 무시
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker))
            return;

        // [추가] 능력이 봉인된 상태 (침묵)인지 체크
        if (isSilenced(attacker))
            return;
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(attacker, getCode()))
            return;

        // [추가] 전투 시작 전에는 스킬 발동 금지
        if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        if (e.getEntity() instanceof LivingEntity victim) {
            double trueDamage = 32.0;

            e.setDamage(0.0001);

            double newHealth = Math.max(0, victim.getHealth() - trueDamage);
            victim.setHealth(newHealth);

            victim.playHurtAnimation(0);
        }
    }

    // 2. 핫바 고정: 슬롯 변경 방지
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSlotChange(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        // [추가] 능력이 봉인된 상태 (침묵)인지 체크
        if (isSilenced(p))
            return;
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 0번 슬롯(첫 번째 칸)이 아니면 취소하고 되돌림
        if (e.getNewSlot() != 0) {
            e.setCancelled(true);
            p.getInventory().setHeldItemSlot(0);
        }
    }

    // 3. 인벤토리 이동 방지
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p))
            return;
        // [추가] 능력이 봉인된 상태 (침묵)인지 체크
        if (isSilenced(p))
            return;
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 인벤토리 조작 자체를 막거나, 핫바 0번 슬롯 관련만 막음
        // "칼 밖에 못 씀 + 다른템 못 씀" -> 단순히 0번 슬롯 고정이면 충분할 수 있으나,
        // 인벤토리에서 칼을 옮기거나 버리는 행위를 방지.
        e.setCancelled(true);
    }

    // 4. 아이템 버리기 방지
    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (AbilityManager.getInstance((MocPlugin) plugin).hasAbility(e.getPlayer(), getCode())) {
            e.setCancelled(true);
        }
    }

    // 5. 양손 스왑 방지
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (AbilityManager.getInstance((MocPlugin) plugin).hasAbility(e.getPlayer(), getCode())) {
            e.setCancelled(true);
        }
    }

    /**
     * 사망 시 버프 및 상태 해제
     */
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
            cleanup(p);
        }
    }

    /**
     * 관전 모드 전환 시 버프 및 상태 해제
     */
    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();
        if (e.getNewGameMode() == GameMode.SPECTATOR) {
            if (AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
                cleanup(p);
            }
        }
    }
}