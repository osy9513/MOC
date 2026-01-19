package me.user.moc.ability.impl;

import me.user.moc.ability.AbilityManager;
import me.user.moc.ability.Ability;
import me.user.moc.MocPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

public class Ueki extends Ability {

    public Ueki(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "우에키";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
            "§b유틸 ● 우에키(우에키의 법칙/배틀짱)",
            "§f묘목이 지급된다. 묘목을 우 클릭 시,",
            "§f주변 나무와 쓰레기들을 변환한다.",
            "§a- 쓰레기를 나무로 바꾸는 힘!"
        );
    }

    @Override
    public void giveItem(Player p) {
        p.getInventory().addItem(new ItemStack(Material.OAK_SAPLING, 16));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        // AbilityManager를 통해 플레이어가 이 능력을 가지고 있는지 확인해야 함.
        // 하지만 Ability 클래스 구조상 직접 접근이 어려우므로, 
        // 여기서직접 HashMap을 조회하거나, 플러그인 인스턴스를 통해 매니저에 접근해야 함.
        // 여기서는 캐스팅하여 접근.
        if (plugin instanceof MocPlugin) {
            MocPlugin moc = (MocPlugin) plugin;
            // MocPlugin에 getAbilityManager()가 필요하거나, GameManager를 통하거나 해야 함.
            // 일단 임시로직: MocPlugin에서 abilityManager를 public으로 노출하거나 getter 필요.
            // 리팩토링 단계에서 MocPlugin에 getter를 추가할 예정.
             if (moc.getAbilityManager() == null || !moc.getAbilityManager().hasAbility(p, getName())) return;
        }

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getItem() != null && e.getItem().getType() == Material.OAK_SAPLING) {
             if (e.getClickedBlock() != null) {
                e.getClickedBlock().getRelative(0, 1, 0).setType(Material.OAK_LOG);
                e.getClickedBlock().getRelative(0, 2, 0).setType(Material.OAK_LOG);
                e.getClickedBlock().getRelative(0, 3, 0).setType(Material.AZALEA_LEAVES);
             }
        }
    }
}
