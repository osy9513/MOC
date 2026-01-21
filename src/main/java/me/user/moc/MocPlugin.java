package me.user.moc;

import me.user.moc.ability.AbilityManager;
import me.user.moc.command.MocCommand;
import me.user.moc.config.ConfigManager;
import me.user.moc.game.ArenaManager;
import me.user.moc.game.ClearManager;
import me.user.moc.game.GameManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MocPlugin: 플러그인의 메인 클래스입니다.
 * 마인크래프트가 플러그인을 로드할 때 가장 먼저 실행되는 곳입니다.
 */
public final class MocPlugin extends JavaPlugin {

    // 어디서든 MocPlugin.getInstance()를 통해 이 클래스에 접근할 수 있게 저장하는 변수입니다.
    private static MocPlugin instance;

    // 게임 운영에 필요한 핵심 매니저들을 담는 변수입니다.
    private GameManager gameManager;
    private AbilityManager abilityManager;
    private ArenaManager arenaManager;
    private ClearManager clearManager;
    private ConfigManager configManager;

    /**
     * 다른 클래스에서 플러그인의 기능(매니저 등)을 가져다 쓰고 싶을 때 사용합니다.
     */
    public static MocPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        // 1. 자기 자신을 instance 변수에 저장합니다.
        instance = this;

        // 2. 매니저들을 순서대로 생성합니다.
        // Arena(맵), GameManager(진행), Ability(능력) 순서입니다.
        this.arenaManager = new ArenaManager(this);
        this.gameManager = new GameManager(this, arenaManager);
        this.abilityManager = new AbilityManager(this);
        // 클리어 매니져
        this.clearManager = new ClearManager(this);

        // 3. GameManager에게 "능력 관리는 이 AbilityManager가 할 거야"라고 알려줍니다. (의존성 주입)
        this.gameManager.setAbilityManager(abilityManager);

        // 4. /moc 명령어를 처리할 담당자(MocCommand)를 등록합니다.
        // [수정] MocCommand() 생성자에 인자를 넣지 않도록 하여 빌드 에러를 방지했습니다.
        if (getCommand("moc") != null) {
            getCommand("moc").setExecutor(new MocCommand());
        }

        // 서버 콘솔창에 로그를 남깁니다.
        getLogger().info("MOC v0.1.1 (마크버전 1.21.1) 플러그인이 성공적으로 켜졌습니다!");
    }

    @Override
    public void onDisable() {
        // 서버가 꺼질 때 진행 중인 게임이 있다면 강제로 멈춥니다.
        if (gameManager != null) {
            gameManager.stopGame();
        }
        getLogger().info("MOC 플러그인이 꺼졌습니다.");
    }

    // 아래 메서드들은 다른 클래스에서 매니저들을 꺼내 쓰기 위한 통로(Getter)입니다.
    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    // 다른 곳에서 쓸 수 있게 getter 추가
    public ClearManager getClearManager() {
        return clearManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}