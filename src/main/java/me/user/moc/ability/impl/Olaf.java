package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

public class Olaf extends Ability {

    public Olaf(JavaPlugin plugin) {
        super(plugin);
    }

    // [수정 2] 우에키에게 "001"이라는 번호표를 붙여줍니다.
    // 이 부분은 각 능력 파일마다 다르게 적어야겠죠? (올라프는 "002" 처럼)
    @Override
    public String getCode() {
        return "002";
    }
    @Override
    public String getName() {
        return "올라프";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
            "§c공격 ● 올라프(리그 오브 레전드)",
            "§f철 도끼가 지급된다. 우 클릭 시 도끼를 던져",
            "§f적에게 강력한 대미지를 입힌다."
        );
    }

    @Override
    public void giveItem(Player p) {
        p.getInventory().addItem(new ItemStack(Material.IRON_AXE, 9));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (plugin instanceof MocPlugin moc) {
            if (moc.getAbilityManager() == null || !moc.getAbilityManager().hasAbility(p, getName())) return;
        }

        if (e.getItem() != null && e.getItem().getType() == Material.IRON_AXE && e.getAction().name().contains("RIGHT")) {
            Snowball axe = p.launchProjectile(Snowball.class);
            axe.setItem(new ItemStack(Material.IRON_AXE));
            axe.setCustomName("olaf_axe");
        }
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Snowball s && "olaf_axe".equals(s.getCustomName())) {
            e.setDamage(10.0);
        }
    }
}
