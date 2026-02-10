package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public class Rammus extends Ability {
    // [추가] 껍질을 착용 중인 플레이어들을 추적하는 장부
    private final java.util.Set<java.util.UUID> wearingShell = new java.util.HashSet<>();

    public Rammus(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "011";
    }

    @Override
    public String getName() {
        return "람머스";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§a유틸 ● 람머스(롤)",
                "§f가시박힌 껍질을 착용하여 몸 말아 웅크리기를 시전합니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§a유틸 ● 람머스(리그 오브 레전드)");
        p.sendMessage("§f가시박힌 껍질을 착용하면 '몸 말아 웅크리기'를 사용하여 구속 2가 걸립니다.");
        p.sendMessage("§f이 상태에서 피격 시 공격자에게 §c받은 피해의 90%§f를 반사합니다.");
        p.sendMessage("§f다른 사람이 이 모자를 쓰면 너무 무거워 느려집니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 가시박힌 껍질");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void giveItem(Player p) {
        ItemStack helmet = new ItemStack(Material.TURTLE_HELMET);
        ItemMeta meta = helmet.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.displayName(Component.text("§a가시박힌 껍질"));
            meta.setLore(List.of("§7착용 시 '몸 말아 웅크리기' 상태가 됩니다.", "§7피격 시 받은 피해의 90%를 반사합니다.", "§c착용 중에는 이동 속도가 느려집니다."));
            helmet.setItemMeta(meta);
            // 1) 가시 레벨 수정 (10레벨 = 150% 확률로 발동 -> 100% 반사)
            helmet.addUnsafeEnchantment(Enchantment.THORNS, 10);
        }
        p.getInventory().addItem(helmet);
        startArmorCheckTask();
    }

    // 2) 90% 반사 로직 (이벤트 핸들러)
    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        // [추가] 전투 시작 전 반사 금지
        if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        // 맞는 사람이 플레이어이고, 때린 사람이 생명체일 때
        if (e.getEntity() instanceof Player victim && e.getDamager() instanceof LivingEntity attacker) {

            // 람머스 능력자인지 확인 (반사 능력은 주인만 사용 가능)
            if (MocPlugin.getInstance().getAbilityManager().hasAbility(victim, getCode())) {

                // 가시박힌 껍질(거북이 등딱지)을 쓰고 있는지 확인
                ItemStack helmet = victim.getInventory().getHelmet();
                if (helmet != null && helmet.getType() == Material.TURTLE_HELMET) {

                    // 받은 데미지의 90% 계산
                    double reflectDamage = e.getDamage() * 0.9;
                    if (reflectDamage > 0) {
                        // 상대방의 무적 시간(피격 후 깜빡이는 시간)을 강제로 0으로 설정
                        attacker.setNoDamageTicks(0);

                        // 그 후 데미지를 입힘 (이제 무적 시간에 막히지 않고 반드시 들어감)
                        attacker.damage(reflectDamage, victim);
                    }
                }
            }
        }
    }

    private BukkitTask armorCheckTask;

    private void startArmorCheckTask() {
        if (armorCheckTask != null && !armorCheckTask.isCancelled())
            return;

        armorCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                // [수정] 월드에 한 명이라도 람머스 능력자가 있는지 확인
                boolean rammusExists = false;
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (MocPlugin.getInstance().getAbilityManager().hasAbility(p, getCode())) {
                        rammusExists = true;
                        break;
                    }
                }

                // 람머스가 없으면 태스크 종료 (최적화)
                if (!rammusExists) {
                    this.cancel();
                    armorCheckTask = null;
                    return;
                }

                // 3) 모든 플레이어 검사 (타인이 모자 쓴 경우 체크를 위해)
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    boolean isRammusOwner = MocPlugin.getInstance().getAbilityManager().hasAbility(p, getCode());
                    checkHelmet(p, isRammusOwner);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L); // 0.25초마다 검사
    }

    private void checkHelmet(Player p, boolean isRammusOwner) {
        ItemStack helmet = p.getInventory().getHelmet();
        // 이름까지 체크하여 일반 거북이 등딱지와 구분
        boolean isSpikedShell = (helmet != null && helmet.getType() == Material.TURTLE_HELMET
                && helmet.hasItemMeta() && helmet.getItemMeta().hasDisplayName()
                && helmet.getItemMeta().getDisplayName().contains("가시박힌 껍질"));

        // [수정] 장부를 사용하여 이전에 껍질을 쓰고 있었는지 확인
        boolean wasWearing = wearingShell.contains(p.getUniqueId());

        if (isSpikedShell) {
            // 모자를 새로 썼을 때
            if (!wasWearing) {
                wearingShell.add(p.getUniqueId()); // 장부에 등록
                if (isRammusOwner) {
                    // [주인] 구속 2 부여
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 1,
                            false, false));
                    p.getServer().broadcastMessage("§a람머스 : 구른다!");
                    p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_TURTLE, 1f, 1f);
                } else {
                    // [타인] 구속 5 부여 (엄청 느려짐)
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 4,
                            false, false));
                    p.sendMessage("§c으윽.. 너무 무거워.."); // 메시지 출력
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 0.5f);
                }
            }
        } else {
            // [중요] 모자를 쓰고 있다가 '벗었을 때만' 효과 해제
            if (wasWearing) {
                wearingShell.remove(p.getUniqueId()); // 장부에서 삭제
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.sendMessage("§c가시박힌 껍질을 벗었습니다."); // 메시지 출력
            }
        }
    }

    @Override
    public void cleanup(Player p) {
        wearingShell.remove(p.getUniqueId());
        super.cleanup(p);
    }

    @Override
    public void reset() {
        wearingShell.clear();
        super.reset();
    }
}