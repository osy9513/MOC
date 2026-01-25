package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class Magnus extends Ability {

    public Magnus(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "004";
    }

    @Override
    public String getName() {
        return "매그너스";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§복합 ● 매그너스(이터널 리턴)",
                "§멋진 오토바이를 탄다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§복합 ● 매그너스(이터널 리턴)");
        p.sendMessage("광산 수레를 우클릭하여 \n" +
                "\n" +
                "1초에 8칸씩 빠른 속도로 앞으로만 이동하고 \n" +
                "좌우 방향은 마우스로 조종이 가능한 \n" +
                "땅에서 이동하는 광산 수레를 탑니다.\n" +
                "\n" +
                "쉬프트를 눌리거나 10초가 지나면 마인 카트에 자동으로 내려옵니다.\n" +
                "\n" +
                "광산 수레에서 내렸으면 광산 수레는 1초간 앞으로 더 간 뒤 폭발합니다.\n" +
                "\n" +
                "적과 부딪치거나 바닥을 제외한 블럭과 부딪치면 바로 폭발합니다.\n" +
                "\n" +
                "폭발과 함께 광산 수레는 없어지고 해당 폭발 데미지는 매그너스는 받지 않습니다.\n" +
                "\n" +
                "폭발 데미지는 18칸입니다.\n" +
                "\n" +
                "쿨타임 : 30초.\n" +
                "\n" +
                "---\n" +
                "\n" +
                "추가 장비: 광산수레.\n" +
                "\n" +
                "장비 제거: 없음.");
    }

    @Override
    public void giveItem(Player p) {
        p.getInventory().addItem(new ItemStack(Material.GRAY_DYE));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (plugin instanceof MocPlugin moc) {
            if (moc.getAbilityManager() == null || !moc.getAbilityManager().hasAbility(p, getCode()))
                return;
        }

        if (event.getItem() != null && event.getItem().getType() == Material.GRAY_DYE
                && event.getAction().name().contains("RIGHT")) {
            if (activeEntities.containsKey(p.getUniqueId()) && !activeEntities.get(p.getUniqueId()).isEmpty())
                return;

            Horse bike = (Horse) p.getWorld().spawnEntity(p.getLocation(), EntityType.HORSE);
            bike.setTamed(true);
            bike.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            Optional.ofNullable(bike.getAttribute(Attribute.MOVEMENT_SPEED)).ifPresent(a -> a.setBaseValue(0.5));
            bike.addPassenger(p);

            // 부모 메서드로 등록
            registerSummon(p, bike);

            new BukkitRunnable() {
                int t = 0;

                @Override
                public void run() {
                    t++;
                    if (t > 200 || bike.getPassengers().isEmpty() || bike.isDead()) {
                        explodeBike(bike, p);
                        List<org.bukkit.entity.Entity> list = activeEntities.get(p.getUniqueId());
                        if (list != null)
                            list.remove(bike);
                        this.cancel();
                        return;
                    }
                    if (bike.getLocation().add(bike.getLocation().getDirection().multiply(1.2)).getBlock().getType()
                            .isSolid()) {
                        explodeBike(bike, p);
                        List<org.bukkit.entity.Entity> list = activeEntities.get(p.getUniqueId());
                        if (list != null)
                            list.remove(bike);
                        this.cancel();
                    }
                }
            }.runTaskTimer(plugin, 0, 1);
        }
    }

    private void explodeBike(Horse bike, Player owner) {
        Location loc = bike.getLocation();
        new BukkitRunnable() {
            int m = 0;

            @Override
            public void run() {
                m++;
                loc.add(loc.getDirection().multiply(1.0));
                if (m >= 10 || loc.getBlock().getType().isSolid()) {
                    loc.getWorld().createExplosion(loc, 4.0f, false, false);
                    owner.setNoDamageTicks(20);
                    bike.remove();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }
}
