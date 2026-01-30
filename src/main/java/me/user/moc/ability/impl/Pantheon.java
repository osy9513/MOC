package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import me.user.moc.MocPlugin;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

public class Pantheon extends Ability {

    private static final String KEY_BREAD = "MOC_PANTHEON_BREAD";

    public Pantheon(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "014";
    }

    @Override
    public String getName() {
        return "빵테온";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§e[전투] §f빵테온 (Pantheon of Bread)",
                "§7빵을 우클릭하여 던질 수 있습니다.",
                "§7빵을 적중시키면 피해를 입히고 배고픔을 채워줍니다.",
                "§7빵으로 근접 공격 시 철 검과 동일한 피해를 줍니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 장비 제거: 철 칼
        p.getInventory().remove(Material.IRON_SWORD);

        // 추가 장비: 빵 64 * 3개
        p.getInventory().addItem(new ItemStack(Material.BREAD, 64));
        p.getInventory().addItem(new ItemStack(Material.BREAD, 64));
        p.getInventory().addItem(new ItemStack(Material.BREAD, 64));

        p.sendMessage("난 항상 제빵사가 되고 싶었지.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ㆍ 빵테온(롤)");
        p.sendMessage("빵 우클릭 시 활처럼 당겨서 발사함.");
        p.sendMessage("빵 발사 시 배고픔 3칸 참.");
        p.sendMessage("빵에 맞으면 체력 3칸 깎임.");
        p.sendMessage("빵에 맞으면 배고픔 3칸 참.");
        p.sendMessage("빵으로 때리면 철 칼이랑 같은 데미지를 줌.");
        p.sendMessage("빵 발사하는 만큼 빵 소모.");
        p.sendMessage(" ");
        p.sendMessage("쿨타임 : 0초");
        p.sendMessage("---");
        p.sendMessage("추가 장비 : 빵 64 * 3개 지급.");
        p.sendMessage("장비 제거 : 철 칼.");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 본인 능력 확인
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
            return;
        }

        // 빵 우클릭 감지
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                && e.getItem() != null && e.getItem().getType() == Material.BREAD) {

            e.setCancelled(true); // 먹는 동작 취소

            // 쿨타임 체크 (0초지만 기본 시스템 활용 가능, 여기선 즉발이므로 생략하거나 넣어도 됨)
            // if (!checkCooldown(p)) return;

            // 빵 소모
            ItemStack item = e.getItem();
            item.setAmount(item.getAmount() - 1);

            // 슈터 배고픔 회복 (3칸 = 6)
            p.setFoodLevel(Math.min(20, p.getFoodLevel() + 6));
            p.setSaturation(Math.min(20f, p.getSaturation() + 6f));

            // 투사체 발사 (눈덩이 기반, 아이템 모양 빵)
            Snowball projectile = p.launchProjectile(Snowball.class);
            projectile.setItem(new ItemStack(Material.BREAD));
            projectile.setMetadata(KEY_BREAD, new FixedMetadataValue(plugin, p.getUniqueId().toString()));

            // 발사 소리 (밀 부수는 소리)
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CROP_BREAK, 1f, 1f);

            // "활처럼 당겨서" 느낌을 위해 약간의 피치 조절된 활 소리 추가? (요청엔 없으므로 생략하되, 밀 소리만 냄)
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        // 1. 근접 공격 (빵으로 때릴 때)
        if (e.getDamager() instanceof Player attacker && e.getDamager() == e.getDamager()) {
            // 능력자 확인
            if (AbilityManager.getInstance((MocPlugin) plugin).hasAbility(attacker, getCode())) {
                ItemStack hand = attacker.getInventory().getItemInMainHand();
                if (hand.getType() == Material.BREAD) {
                    // 철 칼 데미지 (6)
                    e.setDamage(6.0);
                }
            }
        }

        // 2. 투사체 적중 (빵 던지기)
        if (e.getDamager() instanceof Snowball snowball && snowball.hasMetadata(KEY_BREAD)) {

            // 맞은 대상 처리
            if (e.getEntity() instanceof Player victim) {
                // 발사한 사람이 맞았는지 확인 (자가 적중)
                if (snowball.getShooter() instanceof Player shooter
                        && shooter.getUniqueId().equals(victim.getUniqueId())) {
                    // 피해 입히지 않음
                    e.setCancelled(true);

                    // 체력 회복 (3칸 = 6.0)
                    double maxHealth = victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                    victim.setHealth(Math.min(maxHealth, victim.getHealth() + 6.0));

                    // 효과음 (먹는 소리 느낌)
                    victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);
                    return;
                }

                // 타인 적중 시: 데미지 3칸 (6)
                e.setDamage(6.0);

                // 맞은 대상 배고픔 회복
                victim.setFoodLevel(Math.min(20, victim.getFoodLevel() + 6));
                victim.setSaturation(Math.min(20f, victim.getSaturation() + 6f));
            }
        }
    }
}
