package me.user.moc.ability;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public abstract class Ability implements Listener {
    protected final JavaPlugin plugin;

    public Ability(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public abstract String getName();
    public abstract List<String> getDescription();
    
    // 아이템 지급 로직
    public abstract void giveItem(Player p);
}
