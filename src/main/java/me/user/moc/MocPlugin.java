package me.user.moc;

import me.user.moc.ability.AbilityManager;
import me.user.moc.command.MocCommand;
import me.user.moc.game.ArenaManager;
import me.user.moc.game.GameManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class MocPlugin extends JavaPlugin {

    private GameManager gameManager;
    private AbilityManager abilityManager;
    private ArenaManager arenaManager;

    @Override
    public void onEnable() {
        // 매니저 초기화
        this.arenaManager = new ArenaManager(this);
        this.gameManager = new GameManager(this, arenaManager);
        this.abilityManager = new AbilityManager(this);

        // 상호 의존성 주입 (GameManager가 AbilityManager를 필요로 함)
        this.gameManager.setAbilityManager(abilityManager);

        // 커맨드 등록
        getCommand("moc").setExecutor(new MocCommand(gameManager, abilityManager));

        getLogger().info("MOC 시즌 2 시스템 로딩 완료! (Refactored)");
    }
    
    // 능력 구현체 등에서 접근을 위해 getter 제공
    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }
}