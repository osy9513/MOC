package me.user.moc;

import me.user.moc.ability.AbilityManager;
import me.user.moc.command.MocCommand;
import me.user.moc.config.ConfigManager;
import me.user.moc.game.ArenaManager;
import me.user.moc.game.ClearManager;
import me.user.moc.game.GameManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class MocPlugin extends JavaPlugin {

    // 1. 전 세계 어디서든 이 플러그인을 찾을 수 있게 하는 '공식 명함'입니다.
    private static MocPlugin instance;

    private GameManager gameManager;
    private AbilityManager abilityManager;
    private ArenaManager arenaManager;
    private ClearManager clearManager;
    private ConfigManager configManager;
    private me.user.moc.game.ScoreboardManager scoreboardManager; // [추가]

    /**
     * [매우 중요] 다른 파일(람머스 등)에서 이 플러그인을 부를 때 사용하는 함수입니다.
     */
    public static MocPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        // [순서 1번!!] 명함부터 만듭니다.
        // 이게 다른 것들보다 뒤에 있으면, 다른 파일들이 명함을 찾으러 왔을 때 "명함 없는데요?" 라며 오류가 납니다.
        instance = this;
        // 가장 먼저 설정 장부를 불러와야 합니다.
        saveDefaultConfig(); // [추가] config.yml 없으면 생성
        this.configManager = ConfigManager.getInstance();
        this.configManager.load(getConfig()); // [추가] 내용 읽어오기
        // [순서 3번] 나머지 관리자들을 차례대로 소환합니다.
        this.arenaManager = new ArenaManager(this);
        this.gameManager = new GameManager(this, arenaManager);
        this.abilityManager = new AbilityManager(this);
        this.clearManager = new ClearManager(this);
        // 1. 게임 매니저가 만들어졌으니, 아레나 매니저에게 알려줍니다. (NullPointerException 방지)
        this.arenaManager.setGameManager(this.gameManager);
        // [순서 4번] 게임 관리자에게 능력 관리자를 소개시켜 줍니다.
        this.gameManager.setAbilityManager(abilityManager);

        // [순서 5번] 점수판 매니저 초기화 (시작은 하지 않음)
        this.scoreboardManager = new me.user.moc.game.ScoreboardManager(this);

        // [순서 6번] 명령어 등록
        // /moc 명령어를 처리할 담당자(MocCommand)를 등록합니다.
        if (getCommand("moc") != null) {
            getCommand("moc").setExecutor(new MocCommand());
        }

        // 서버 콘솔창에 로그를 남깁니다.
        getLogger().info("MOC v" + getDescription().getVersion() + " (마크버전 1.21.11) 플러그인이 성공적으로 켜졌습니다!");
    }

    @Override
    public void onDisable() {
        // 서버가 꺼질 때 진행 중인 게임이 있다면 강제로 멈춥니다.
        if (gameManager != null) {
            gameManager.stopGame();
        }
        if (scoreboardManager != null) {
            scoreboardManager.stop();
        }
        getLogger().info("MOC 플러그인이 꺼졌습니다.");
    }

    // --- 다른 파일에서 빌려가기 위한 입구들 (Getter) ---
    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public ClearManager getClearManager() {
        return clearManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public me.user.moc.game.ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
}
