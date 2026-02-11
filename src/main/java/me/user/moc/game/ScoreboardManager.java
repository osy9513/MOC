package me.user.moc.game;

import me.user.moc.MocPlugin;
import me.user.moc.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.stream.Collectors;

public class ScoreboardManager {

    private final MocPlugin plugin;
    private final GameManager gameManager;
    private BukkitTask updateTask;

    public ScoreboardManager(MocPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
    }

    // 점수판 가동 시작
    public void start() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }

        // 1초(20 ticks)마다 점수판 갱신
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllPlayers();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // 점수판 가동 중지
    public void stop() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
            updateTask = null;
        }
        // 모든 플레이어 점수판 제거
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    // 모든 플레이어에게 점수판 업데이트
    private void updateAllPlayers() {
        // 게임 중이 아니면 점수판을 띄우지 않음 (또는 로비용 점수판을 띄울 수도 있음)
        if (!gameManager.isRunning()) {
            // 게임 중이 아닐 때는 메인 점수판이나 빈 것을 보여줌
            for (Player p : Bukkit.getOnlinePlayers()) {
                // 만약 로비에서도 보여주고 싶다면 조건문 수정 필요.
                // 여기서는 게임 중에만 보여주는 것으로 가정.
                // 하지만 "참가 인원 수" 등을 보여달라고 했으므로 로비에서도 보여주는 게 좋을 수 있음.
                // 일단 항상 보여주도록 처리.
                updateScoreboard(p);
            }
            return;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            updateScoreboard(p);
        }
    }

    private void updateScoreboard(Player p) {
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null)
            return;

        Scoreboard board = p.getScoreboard();
        // 기존에 커스텀 점수판이 없거나 메인 점수판이라면 새로 생성
        if (board.equals(manager.getMainScoreboard())) {
            board = manager.getNewScoreboard();
            p.setScoreboard(board);
        }

        Objective obj = board.getObjective("MocScoreboard");
        if (obj == null) {
            obj = board.registerNewObjective("MocScoreboard", Criteria.DUMMY, "§e§l[ MOC ]"); // 제목은 공간 부족 시 수정
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            // [추가] 숫자 숨기기 (1.20.4+ Paper 기능)
            obj.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        }

        // 타이틀 업데이트 (애니메이션 가능하지만 일단 고정)
        // [마오캐 버전 - 동적 로딩]
        String version = plugin.getDescription().getVersion();
        obj.setDisplayName("§e§l[ 마오캐 - " + version + " ]");

        // 라인 관리 (스코어보드는 라인 수정이 번거로우므로, Entry 이름을 변경하는 방식 사용)
        // 여기서는 매번 싹 지우고 다시 세팅하는 방식은 깜빡임이 있을 수 있으나,
        // 1초 간격이면 덮어쓰기(Team prefix/suffix 활용)가 좋음.
        // 하지만 구현 편의성을 위해 라인별로 Score를 설정하는 간단한 방식 사용.
        // (깜빡임 방지를 위해선 Team 사용을 권장하지만, 코드가 길어지므로 우선 기본 방식 적용)

        // 기존 엔트리 제거 (초기화)
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        List<String> lines = new ArrayList<>();

        // 1. 참가 인원 수
        int currentPlayers = Bukkit.getOnlinePlayers().size(); // AFK 포함 여부는 기획 확인 필요. 일단 전체 접속자.
        // int realPlayers = (int) Bukkit.getOnlinePlayers().stream().filter(pl ->
        // !gameManager.isAfk(pl.getName())).count();

        lines.add("§7----------------------");
        lines.add("§f참가 인원 수 : §a" + currentPlayers + "명");
        lines.add(" "); // 공백

        // 2. 현재 본인 점수
        // GameManager의 scores 맵 접근 필요. (public getter 없으면 추가 필요)
        // GameManager 내부에 scores가 private임. -> GameManager에 getScore(UUID) 추가하거나, 여기서
        // Reflection/Access 필요.
        // 일단 GameManager에서 getScore 메서드를 만들어야 함. 지금은 0으로 표시하고 나중에 수정.
        int myScore = getPlayerScore(p.getUniqueId());
        lines.add("§f내 점수 : §e" + myScore + "점");

        // [추가] 내 능력 표시
        String abilityName = "없음";
        if (plugin.getAbilityManager() != null) {
            // AbilityManager에서 플레이어의 능력 코드 가져오기
            String code = plugin.getAbilityManager().getPlayerAbilities().get(p.getUniqueId());
            if (code != null) {
                me.user.moc.ability.Ability ability = plugin.getAbilityManager().getAbility(code);
                if (ability != null) {
                    abilityName = ability.getName();
                }
            }
        }
        lines.add("§f내 능력 : §b" + abilityName);

        // [추가] 라운드 표시
        int currentRound = 0;
        if (plugin.getGameManager() != null) {
            currentRound = plugin.getGameManager().getRound();
        }
        lines.add("§f라운드  : §a" + currentRound + "R");

        lines.add("§7----------------------");
        lines.add("  "); // 공백 (중복 방지 위해 공백 개수 조절)

        // 3. 탑 5 스코어
        lines.add("§e[ 탑 5 점수 ]");

        // 점수 정렬
        List<Map.Entry<UUID, Integer>> top5 = getTopScores();

        // 1위 ~ 5위 (없으면 빈칸 or 표시 안함)
        // 1위 (다이아)
        lines.add(formatRank(1, top5, "§b■")); // 다이아 색
        lines.add(formatRank(2, top5, "§e■")); // 금 색
        lines.add(formatRank(3, top5, "§f■")); // 철 색
        lines.add(formatRank(4, top5, "§6■")); // 구리 색
        lines.add(formatRank(5, top5, "§8■")); // 나무/돌 색

        // 역순으로 Score 할당 (리스트의 마지막이 점수가 낮으므로 맨 아래)
        // 스코어보드는 점수가 높은게 위로 올라감.
        // 따라서 lines의 0번 인덱스가 가장 위(점수 15), 마지막이 가장 아래(점수 1).
        int score = 15;
        for (String line : lines) {
            obj.getScore(line).setScore(score--);
        }
    }

    private String formatRank(int rank, List<Map.Entry<UUID, Integer>> top5, String icon) {
        if (top5.size() < rank) {
            return "§7" + rank + "위. -";
        }
        Map.Entry<UUID, Integer> entry = top5.get(rank - 1);
        String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
        if (name == null)
            name = "Unknown";
        int s = entry.getValue();
        // 포맷: 1위 닉네임 (아이콘) : 점수
        // 예: 1위 홍길동 (§b■) : 100
        return "§f" + rank + "위 " + name + " (" + icon + "§f) : §a" + s;
    }

    // GameManager의 scores에 접근하기 위한 헬퍼 (GameManager 수정 필요)
    // 임시로 Reflection을 쓰거나, GameManager에 getter를 추가해야 함.
    // 여기서는 GameManager에 getter가 있다고 가정하고 작성 후 GameManager를 수정하러 가겠음.
    private int getPlayerScore(UUID uuid) {
        return gameManager.getScore(uuid);
    }

    private List<Map.Entry<UUID, Integer>> getTopScores() {
        return gameManager.getTopScores();
    }
}
