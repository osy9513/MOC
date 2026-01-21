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
            "§전투 ● 올라프(리그 오브 레전드)",
            "§f철 도끼가 지급된다. 우 클릭 시 도끼를 던져",
            "§f적에게 강력한 대미지를 입힌다."
        );
    }
    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§전투 ● 올라프(리그 오브 레전드)");
        p.sendMessage("우클릭 시 도끼를 상대에게 던져 체력8칸의 피해를 준다.\n" +
                "\n" +
                "맞은 상대는 구속 1이 1초간 걸린다.\n" +
                "\n" +
                "우클릭을 통해 날라간 도끼는 플레이어 혹은 블럭에 접촉하면 땅에 떨어지며 \n" +
                "\n" +
                "만약 부딪친 블럭이 유리 블럭이면 유리가 부셔지고 도끼는 계속 날라간다.\n" +
                "\n" +
                "우클릭으로 떨어진 도끼는 올라프만 주울 수 있다.\n" +
                "\n" +
                "올라프가 도끼를 주우면 쿨타임이 초기회 된다.\n" +
                "\n" +
                "쿨타임 : 5초.\n" +
                "\n" +
                "---\n" +
                "\n" +
                "추가 장비: 철 도끼 2개\n" +
                "\n" +
                "장비 제거: 철 검");
    }

    @Override
    public void giveItem(Player p) {
        p.getInventory().addItem(new ItemStack(Material.IRON_AXE, 9));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (plugin instanceof MocPlugin moc) {
            if (moc.getAbilityManager() == null || !moc.getAbilityManager().hasAbility(p, getCode())) return;
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
