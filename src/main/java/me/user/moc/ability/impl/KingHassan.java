package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.List;

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
        return "산의 노인(FATE)";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§e=== §5산의 노인(FATE) §e상세 정보 ===    ",
                "§7[패시브]",
                "§f라운드 시작 시 §7[은신, 구속 3, 재생] §f버프를 영구히 얻습니다.",
                "§f최대 체력이 §c10칸(20HP)§f으로 고정되며, 핫바가 §b첫 번째 칸§f으로 고정됩니다.",
                "§f다른 아이템은 사용할 수 없습니다.",
                " ",
                "§7[무기]",
                "§5산의 노인의 검§f: 기본 대미지 §c16§f을 입히며, §c적의 방어력을 무시§f합니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e=== §5산의 노인(FATE) §e상세 정보 ===");
        p.sendMessage("§7[패시브]");
        p.sendMessage("§f라운드 시작 시 §7[은신, 구속 3, 재생] §f버프를 영구히 얻습니다.");
        p.sendMessage("§f최대 체력이 §c10칸(20HP)§f으로 고정되며, 핫바가 §b첫 번째 칸§f으로 고정됩니다.");
        p.sendMessage("§f다른 아이템은 사용할 수 없습니다.");
        p.sendMessage(" ");
        p.sendMessage("§7[무기]");
        p.sendMessage("§5산의 노인의 검§f: 기본 대미지 §c16§f을 입히며, §c적의 방어력을 무시§f합니다.");
        p.sendMessage("§e==================================");
    }

    @Override
    public void giveItem(Player p) {
        // 1. 기존 아이템 제거 (기본 지급템 모두 제거)
        p.getInventory().clear();
        p.getInventory().setHelmet(null);
        p.getInventory().setChestplate(null);
        p.getInventory().setLeggings(null);
        p.getInventory().setBoots(null);

        // 2. 능력 전용 아이템 지급
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName("§5산의 노인의 검");
        meta.setLore(List.of("§7만종은 그대의 이름을 가리켰다.", "§c방어력 무시 16 대미지"));
        meta.setUnbreakable(true);
        sword.setItemMeta(meta);

        // 첫 번째 칸에 지급 및 선택 강제
        p.getInventory().setItem(0, sword);
        p.getInventory().setHeldItemSlot(0);

        // 3. 스탯 및 버프 적용
        // 최대 체력 20 고정 (기본값이지만 확실하게 설정)
        // [Fix] Attribute.GENERIC_MAX_HEALTH -> Attribute.GENERIC_MAX_HEALTH (Standard)
        // If compilation fails again, it might mean Spigot 1.21 uses GenericAttributes
        // (NMS) or similar,
        // but Attribute.GENERIC_MAX_HEALTH is the standard Bukkit API.
        // Wait, the previous error said 'cannot find symbol variable
        // GENERIC_MAX_HEALTH'.
        // I will try Attribute.MAX_HEALTH as it is the most common alternative in some
        // mappings.
        if (p.getAttribute(Attribute.MAX_HEALTH) != null) {
            p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
        }
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());

        // 버프 적용 (무한 지속)
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 2, false, false)); // 구속
                                                                                                                         // 3
                                                                                                                         // (Amplifier
                                                                                                                         // 2)
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 0, false, false)); // 즉시
                                                                                                                   // 체력
                                                                                                                   // 회복
                                                                                                                   // (재생)

        // 4. 이펙트 및 메시지 출력
        p.sendMessage(" ");
        p.sendMessage(" ");
        p.sendMessage("§5[산의 노인] §f듣거라. 만종은 그대의 이름을 가리켰다.");
        p.sendMessage(" ");
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f); // 웅장한 소리 (위더 소환음 낮게)

        // 4초간 연기 이펙트
        // [Fix] BukkitRunnable -> BukkitTask assignment
        BukkitTask particleTask = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= 80) { // 4초 (20 ticks * 4)
                    this.cancel();
                    return;
                }
                if (!p.isOnline()) {
                    this.cancel();
                    return;
                }

                // 검은색(SMOKE) & 보라색(DRAGON_BREATH or WITCH) 연기
                p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 10, 0.5, 1, 0.5, 0.05);
                p.getWorld().spawnParticle(Particle.DRAGON_BREATH, p.getLocation().add(0, 1, 0), 5, 0.5, 1, 0.5, 0.05);

                tick += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);

        registerTask(p, particleTask);
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        // 능력 해제 시 스탯/인벤토리 복구는 GameManager나 ClearManager에서 일괄 처리되지만,
        // 혹시 모르니 포션 효과 등은 여기서 지워주는 것이 안전함.
        if (p.isOnline()) {
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.REGENERATION);
            if (p.getAttribute(Attribute.MAX_HEALTH) != null) {
                p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
            }
        }
    }

    // === [이벤트 핸들러] ===

    // 1. 공격 로직: 방어력 무시
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker))
            return;
        // [Fix] Added MocPlugin import, so this works now.
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(attacker, getCode()))
            return;

        // 적이 살아있는 엔티티인지 확인
        if (e.getEntity() instanceof LivingEntity victim) {
            // 방어력 무시 대미지 로직
            double trueDamage = 16.0;

            // 기본 대미지 이벤트는 0으로 만들어서 넉백만 적용되게 하거나,
            // 0.0001로 설정하여 피격 모션/넉백만 남김.
            e.setDamage(0.0001);

            // 실제 체력 깎기 (방어력 연산 건너뜀)
            double newHealth = Math.max(0, victim.getHealth() - trueDamage);
            victim.setHealth(newHealth);

            // 피격 효과 수동 재생 (대미지가 0이라 소리가 안 날 수 있으므로)
            // [Fix] Suppress deprecation for HURT or use it if available
            victim.playEffect(org.bukkit.EntityEffect.HURT);
        }
    }

    // 2. 핫바 고정: 슬롯 변경 방지
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSlotChange(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 0번 슬롯(첫 번째 칸)이 아니면 취소하고 되돌림
        if (e.getNewSlot() != 0) {
            e.setCancelled(true);
            p.getInventory().setHeldItemSlot(0);
        }
    }

    // 3. 인벤토리 이동 방지 (아이템 위치 변경 금지)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p))
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
}
