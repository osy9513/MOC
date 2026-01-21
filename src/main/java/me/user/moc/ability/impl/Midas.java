package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

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
        return Arrays.asList(
            "§6유틸 ● 미다스(그리스 신화)",
            "§f금괴가 지급된다. 좌 클릭한 블록을",
            "§f순금 블록으로 바꾸어버린다."
        );
    }
    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§6유틸 ● 미다스(신화)");
        p.sendMessage("손으로 블럭을 때리면 그 블럭은 금 블럭이 된다.\n" +
                "\n" +
                "손으로 상대를 때리면 상대가 입고 있던 방어구는 금 방어구가 된다.\n" +
                "\n" +
                "금괴를 먹을 수 있다. (배고픔 2칸 참)\n" +
                "\n" +
                "쿨타임 : 0초.\n" +
                "\n" +
                "---\n" +
                "\n" +
                "추가 장비: 금 갑옷, 금 칼, 발광포션, 금블럭 10개, 황금 사과 64개\n" +
                "\n" +
                "장비 제거: 철 갑옷, 철 칼, 체력재생포션, 유리 10개, 고기 64개");
    }

    @Override
    public void giveItem(Player p) {
        p.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 64));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (plugin instanceof MocPlugin moc) {
             if (moc.getAbilityManager() == null || !moc.getAbilityManager().hasAbility(p, getCode())) return;
        }

        if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getItem() != null && e.getItem().getType() == Material.GOLD_INGOT) {
            if (e.getClickedBlock() != null) {
                e.getClickedBlock().setType(Material.GOLD_BLOCK);
            }
        }
    }
}
