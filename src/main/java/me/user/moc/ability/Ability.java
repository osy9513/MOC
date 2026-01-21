package me.user.moc.ability;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public abstract class Ability implements Listener {
    protected final JavaPlugin plugin;

    public Ability(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    // [수정 1] 여기에 '능력 코드'를 달라고 하는 규칙을 추가합니다.
    // 기존 getName()은 '채팅창에 보여줄 이름' 용도로 남겨두고,
    // getCode()는 '시스템이 알아먹을 번호' 용도로 씁니다.
    public abstract String getCode();
    public abstract String getName();
    public abstract List<String> getDescription();
    
    // 아이템 지급 로직
    public abstract void giveItem(Player p);
}
