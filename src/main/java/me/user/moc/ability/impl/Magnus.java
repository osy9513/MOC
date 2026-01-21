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
    private final Map<UUID, Horse> activeBikes = new HashMap<>();

    public Magnus(JavaPlugin plugin) {
        super(plugin);
    }

    // [수정 2] 우에키에게 "001"이라는 번호표를 붙여줍니다.
    // 이 부분은 각 능력 파일마다 다르게 적어야겠죠? (올라프는 "002" 처럼)
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
            "§8이동 ● 매그너스(이터널 리턴)",
            "§f염료가 지급된다. 사용 시 오토바이를 소환해",
            "§f폭발적인 속도로 돌진 후 자폭한다."
        );
    }

    @Override
    public void giveItem(Player p) {
        p.getInventory().addItem(new ItemStack(Material.GRAY_DYE));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (plugin instanceof MocPlugin moc) {
             if (moc.getAbilityManager() == null || !moc.getAbilityManager().hasAbility(p, getName())) return;
        }

        if (event.getItem() != null && event.getItem().getType() == Material.GRAY_DYE && event.getAction().name().contains("RIGHT")) {
            if (activeBikes.containsKey(p.getUniqueId())) return;

            Horse bike = (Horse) p.getWorld().spawnEntity(p.getLocation(), EntityType.HORSE);
            bike.setTamed(true);
            bike.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            Optional.ofNullable(bike.getAttribute(Attribute.MOVEMENT_SPEED)).ifPresent(a -> a.setBaseValue(0.5));
            bike.addPassenger(p);
            activeBikes.put(p.getUniqueId(), bike);

            new BukkitRunnable() {
                int t = 0;
                @Override
                public void run() {
                    t++;
                    if (t > 200 || bike.getPassengers().isEmpty() || bike.isDead()) {
                        explodeBike(bike, p);
                        activeBikes.remove(p.getUniqueId());
                        this.cancel();
                        return;
                    }
                    if (bike.getLocation().add(bike.getLocation().getDirection().multiply(1.2)).getBlock().getType().isSolid()) {
                        explodeBike(bike, p);
                        activeBikes.remove(p.getUniqueId());
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
