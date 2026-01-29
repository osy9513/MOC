package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class Rammus extends Ability {

    public Rammus(JavaPlugin plugin) {
        super(plugin);
        startArmorCheckTask();
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
                "§a유틸● 람머스(롤)",
                "§f가시박힌 껍질을 착용하여 몸 말아 웅크리기를 시전합니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§a유틸 ● 람머스(리그 오브 레전드)");
        p.sendMessage("§f가시박힌 껍질(가시 20)를 착용하면 '몸 말아 웅크리기'를 사용하여 구속 2가 걸립니다.");
        p.sendMessage("§f가시박힌 껍질에는 강력한 가시 인챈트가 부여되어 있습니다.");
        p.sendMessage("§f모자를 벗으면 구속 효과가 즉시 해제됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§7쿨타임 : 0초");
        p.sendMessage("---");
        p.sendMessage("§7추가 장비 : 가시박힌 껍질");
        p.sendMessage("§7장비 제거 : 없음");
    }

    @Override
    public void giveItem(Player p) {
        ItemStack helmet = new ItemStack(Material.TURTLE_HELMET);
        ItemMeta meta = helmet.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§a가시박힌 껍질"));
            meta.addEnchant(Enchantment.THORNS, 20, true);
            helmet.setItemMeta(meta);
        }
        p.getInventory().addItem(helmet);
    }

    private void startArmorCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    // 이 플레이어가 람머스 능력자인지 확인해야 하지만,
                    // Ability 클래스 구조상 여기서 직접 확인하기 어려울 수 있음.
                    // 다만, AbilityManager를 통해 확인하거나,
                    // 간단히 '람머스 능력자만 모자를 받음' -> '모자를 쓴 사람은 람머스일 가능성이 높음'으로 퉁칠 수도 있으나
                    // 정석대로면 AbilityManager를 통해 확인하는 게 좋음.
                    // 하지만 현재 구조상 AbilityManager 접근이 필요함.
                    // 일단 간단하게 '이 능력을 가진 사람'을 식별하는 로직이 필요함.
                    // 여기서는 AbilityManager 인스턴스를 가져오거나,
                    // 단순히 로직 구현에 집중하기 위해 메타데이터 등을 쓸 수도 있음.

                    // 개선: AbilityManager.hasAbility(p, "람머스")를 호출하고 싶지만
                    // Ability 클래스는 plugin만 알고 있음.
                    // MocPlugin.getInstance().getAbilityManager() 로 접근 가능.

                    if (!me.user.moc.MocPlugin.getInstance().getAbilityManager().hasAbility(p, getCode())) {
                        continue;
                    }

                    checkHelmet(p);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L); // 0.25초마다 검사
    }

    private void checkHelmet(Player p) {
        ItemStack helmet = p.getInventory().getHelmet();
        boolean isWearingTurtle = (helmet != null && helmet.getType() == Material.TURTLE_HELMET);
        boolean hasSlowness = p.hasPotionEffect(PotionEffectType.SLOWNESS);

        if (isWearingTurtle) {
            // 가시박힌 껍질을 쓰고 있는데, 슬로우가 없다면 (방금 썼다는 뜻)
            if (!hasSlowness) {
                // 효과 적용
                p.addPotionEffect(
                        new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 1, false, false));
                p.getServer().broadcastMessage("§a람머스 : 구른다!");
                p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_TURTLE, 1f, 1f);
            }
        } else {
            // 가시박힌 껍질을 안 쓰고 있는데, 슬로우가 있다면 (방금 벗었다는 뜻)
            // 주의: 다른 이유로 슬로우가 걸렸을 수도 있으니 조심해야 하지만,
            // 람머스 능력 메커니즘상 '모자 미착용 시 풀린다'라고 명시됨.
            if (hasSlowness) {
                p.removePotionEffect(PotionEffectType.SLOWNESS);
            }
        }
    }
}
