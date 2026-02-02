package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class KimDokja extends Ability {

    private final Set<UUID> hasStory = new HashSet<>();

    public KimDokja(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "김독자";
    }

    @Override
    public String getCode() {
        return "040";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§e유틸 ● §f김독자 (전지적 독자 시점)",
                "§f절대왕좌를 파괴하세요.",
                "§f성공 시 : 설화, ‘왕이 없는 세계의 왕’이 탄생",
                "§f실패 시 : ???");
    }

    @Override
    public void giveItem(Player p) {
        p.getInventory().remove(Material.IRON_SWORD);

        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName("§b사인참사검");
        sword.setItemMeta(meta);
        p.getInventory().addItem(sword);

        // 아직 설화 획득 전이므로 대미지 낮춤 (철검급으로) <- 안 낮추는 걸로 처리.
        // 속성 수정은 복잡하므로, EntityDamageByEntityEvent에서 처리
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (!e.getBlock().getType().equals(Material.EMERALD_BLOCK))
            return;

        Player p = e.getPlayer();
        if (AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
            ItemStack item = p.getInventory().getItemInMainHand();
            if (item.getType() == Material.NETHERITE_SWORD && item.getItemMeta() != null
                    && "§b사인참사검".equals(item.getItemMeta().getDisplayName())) {

                if (!hasStory.contains(p.getUniqueId())) {
                    completeQuest(p, item);
                }
            }
        }
    }

    private void completeQuest(Player p, ItemStack sword) {
        hasStory.add(p.getUniqueId());

        Bukkit.broadcastMessage("§b[설화, ‘왕이 없는 세계의 왕’이 탄생했습니다.]");

        // 검 강화 (날카로움 2 추가)
        // Enchantment.DAMAGE_ALL -> SHARPNESS
        sword.addEnchantment(Enchantment.SHARPNESS, 2);

        // 이펙트 (대미지 0 번개 5회)
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 5) {
                    cancel();
                    return;
                }
                p.getWorld().strikeLightningEffect(p.getLocation());
                count++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        /*
         * // 1. 김독자의 공격 대미지 조정 <- 그냥 없는 걸로 함. 기본딜이 네더검이라 강하도록
         * if (e.getDamager() instanceof Player attacker
         * && AbilityManager.getInstance((MocPlugin) plugin).hasAbility(attacker,
         * getCode())) {
         * if (!hasStory.contains(attacker.getUniqueId())) {
         * // 설화 획득 전: 네더라이트(8) -> 철검(6) 수준으로 너프
         * // 대충 -2 정도 깎음
         * e.setDamage(e.getDamage() - 2.0);
         * }
         * return;
         * }
         */

        // 2. 김독자가 피격될 때 방어 로직
        if (e.getEntity() instanceof Player defender
                && AbilityManager.getInstance((MocPlugin) plugin).hasAbility(defender, getCode())) {
            if (hasStory.contains(defender.getUniqueId())) {
                // 공격자가 플레이어 외 (몬스터 등) -> 대미지 1
                if (!(e.getDamager() instanceof Player)) {
                    e.setDamage(1.0);
                    return;
                }

                // 공격자가 플레이어인데, 능력 타입이 "유틸"이 아니면 -> 대미지 1 ???
                // 조건: "능력 유형이 유틸이 아닌 플레이어의 공격 또한 데미지가 1로 고정"
                // 즉, 전투형 등의 공격을 1로 만듦 (매우 강력). 유틸형의 공격만 제대로 들어옴.
                Player pAttacker = (Player) e.getDamager();
                String code = AbilityManager.getInstance((MocPlugin) plugin).getPlayerAbilities()
                        .get(pAttacker.getUniqueId());
                if (code != null) {
                    Ability ability = AbilityManager.getInstance((MocPlugin) plugin).getAbility(code);
                    if (ability != null) {
                        // 능력 타입을 알아야 함. 설명문에서 파싱? "§e유틸 ●"
                        // [수정] 단순 "유틸" 포함 여부만 보면 설명문에 유틸이란 단어가 들어갈 때 오작동할 수 있음.
                        // 따라서 "유틸"과 구분자 "●"가 같이 있는지 확인하거나, 첫 줄만 확인하는 것이 안전함.
                        boolean isUtility = false;
                        for (String line : ability.getDescription()) {
                            // "유틸 ●" 패턴이 있는지 확인
                            if (line.contains("유틸") && line.contains("●")) {
                                isUtility = true;
                                break;
                            }
                        }

                        if (!isUtility) {
                            e.setDamage(1.0);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e유틸 ● 김독자(전지적 독자 시점)");
        p.sendMessage("§f절대왕좌를 파괴하여 설화를 완성하세요.");
        p.sendMessage("§f사인참사검으로 맵의 에메랄드 블럭 파괴 시 설화를 획득합니다.");
        p.sendMessage("§f[설화 획득 효과]");
        p.sendMessage("§f1. 사인참사검 강화(날카로움 2, 네더라이트)");
        p.sendMessage("§f2. 타 플레이어의 공격 대미지를 1로 고정(단, 유틸형 능력자의 공격은 제외)");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 사인참사검");
        p.sendMessage("§f장비 제거 : 철검");
    }
}
