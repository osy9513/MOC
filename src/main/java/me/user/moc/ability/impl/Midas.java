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

    // [수정 2] 우에키에게 "001"이라는 번호표를 붙여줍니다.
    // 이 부분은 각 능력 파일마다 다르게 적어야겠죠? (올라프는 "002" 처럼)
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
            "§6특수 ● 미다스(그리스 신화)",
            "§f금괴가 지급된다. 좌 클릭한 블록을",
            "§f순금 블록으로 바꾸어버린다."
        );
    }

    @Override
    public void giveItem(Player p) {
        p.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 64));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (plugin instanceof MocPlugin moc) {
             if (moc.getAbilityManager() == null || !moc.getAbilityManager().hasAbility(p, getName())) return;
        }

        if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getItem() != null && e.getItem().getType() == Material.GOLD_INGOT) {
            if (e.getClickedBlock() != null) {
                e.getClickedBlock().setType(Material.GOLD_BLOCK);
            }
        }
    }
}
