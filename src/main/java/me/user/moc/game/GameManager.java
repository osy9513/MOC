package me.user.moc.game;

import me.user.moc.MocPlugin;
import me.user.moc.ability.AbilityManager;
import me.user.moc.config.ConfigManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

public class GameManager implements Listener {

    private final MocPlugin plugin;
    private final ArenaManager arenaManager;
    // 데이터 관리
    // 플레이어 점수 저장소 (UUID, 점수)
    private final Map<UUID, Integer> scores = new HashMap<>(); // 점수판
    private final Set<String> afkPlayers = new HashSet<>(); // 잠수 유저(닉네임)
    private final Set<UUID> readyPlayers = new HashSet<>(); // 준비 완료(/moc yes) 유저
    // 게임에 참여 중인 플레이어 목록 (접속 끊겨도 여기엔 남아있을 수 있음)
    private final Set<UUID> players = new HashSet<>();
    // 현재 라운드에서 '실제로 살아서 뛰고 있는' 플레이어 목록
    private final Set<UUID> livePlayers = new HashSet<>();
    private ConfigManager configManager;
    private AbilityManager abilityManager; // 의존성 주입

    // [Scoreboard 전용 Getter]
    public int getScore(UUID uuid) {
        return scores.getOrDefault(uuid, 0);
    }

    public List<Map.Entry<UUID, Integer>> getTopScores() {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(scores.entrySet());
        list.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());
        return list;
    }

    public int getRound() {
        return round;
    }

    // [Scoreboard 전용 Getter 끝]
    // 게임 상태 변수
    private boolean isRunning = false;
    private boolean isInvincible = false;
    private int round = 0;
    // 태스크 관리 (타이머)
    private BukkitTask selectionTask; // 능력 추첨 타이머
    private BukkitTask startGameTask; // [추가] 게임 시작 카운트다운 타이머
    private BukkitTask borderStartTask; // [추가] 자기장 시작 대기 타이머

    public GameManager(MocPlugin plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        // [중요!] 여기서 장부를 가져올 때, 메인 플러그인에 있는 걸 직접 가져옵니다.
        // 만약 메인에 없다면(null), 설정 매니저에서 직접 '가져오기 버튼'을 누릅니다.
        this.configManager = (plugin.getConfigManager() != null)
                ? plugin.getConfigManager()
                : ConfigManager.getInstance();

        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // [추가] 로그를 남겨서 장부가 잘 들어왔는지 확인합니다.
        if (this.configManager == null) {
            plugin.getLogger().warning("!!! [경고] GameManager가 설정 장부를 찾지 못했습니다 !!!");
        }
    }

    /**
     * [수정 포인트 1] 이 함수가 실행될 때 장부(ConfigManager)를
     * 확실하게 챙기도록 고쳤습니다.
     */
    public static GameManager getInstance(MocPlugin plugin) {
        return plugin.getGameManager(); // 메인 클래스를 통해 가져오거나 싱글톤 패턴 적용 가능
    }

    // GameManager.java 파일 안에 추가 (어느 곳이든 상관없음)
    public boolean isRunning() {
        return isRunning;
    }

    public boolean isInvincible() {
        return isInvincible;
    }

    /**
     * 전투가 실제로 시작되었는지 확인합니다.
     * (게임이 실행 중이고, 무적/대기 시간이 끝난 상태)
     */
    public boolean isBattleStarted() {
        return isRunning && !isInvincible;
    }

    /**
     * 다른 클래스(MocCommand)에서 특정 플레이어가 AFK인지 확인할 수 있게 해주는 함수입니다.
     */
    public boolean isAfk(String playerName) {
        return afkPlayers.contains(playerName);
    }

    // MocPlugin에서 AbilityManager를 설정해주기 위한 세터
    public void setAbilityManager(AbilityManager abilityManager) {
        this.abilityManager = abilityManager;
    }

    // =========================================================================
    // 1. 게임 시작 단계 (/moc start)
    // =========================================================================
    public void startGame(Player starter) {
        // [안전장치] 만약 게임 시작 직전에 장부가 비어있다면, 여기서 마지막으로 한 번 더 챙깁니다.
        if (this.configManager == null) {
            this.configManager = ConfigManager.getInstance();
        }
        if (isRunning) {
            starter.sendMessage("§c이미 게임이 진행 중입니다.");
            return;
        }

        // [버그 수정] 이전에 예약된 시작 태스크가 있다면 취소 (빠른 재시작 방지)
        if (startGameTask != null && !startGameTask.isCancelled()) {
            startGameTask.cancel();
        }

        isRunning = true;
        round = 0;
        scores.clear();

        // 1-1. 게임 설정 정보 출력 (기획안 양식)
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage("§e=== 마인크래프트 오브 캐릭터즈 (버전 0.1.1) ===");
        Bukkit.broadcastMessage("§e=== 제작 : 원크, 알아서해 ===");
        Bukkit.broadcastMessage("§f기본 체력: 3줄(60칸)");
        Bukkit.broadcastMessage("§f기본 지급: 철칼, 구운 소고기64개, 물 양동이, 유리5개, 재생포션, 철 흉갑");

        // [추가] 통계 초기화
        if (abilityManager != null) {
            abilityManager.resetUsageCounts();
        }

        // 참가 인원 목록 만들기 (AFK 제외)
        List<String> participants = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> !afkPlayers.contains(name))
                .collect(Collectors.toList());
        Bukkit.broadcastMessage("§f참가 인원 : (총 " + participants.size() + "명) " + String.join(", ", participants));

        // 스폰 좌표 확인
        Location spawn = configManager.spawn_point != null ? configManager.spawn_point : starter.getLocation();
        // 만약 콘피그에 스폰이 없으면 시작한 사람 위치를 임시 스폰으로 잡음
        if (configManager.spawn_point == null)
            configManager.spawn_point = spawn;

        Bukkit.broadcastMessage("§f스폰 위치 : " + spawn.getBlockX() + ", " + spawn.getBlockY() + ", " + spawn.getBlockZ());
        // Bukkit.broadcastMessage("§f게임 모드 : 개인전");
        Bukkit.broadcastMessage("§7잠시 후 능력을 추첨합니다.");
        Bukkit.broadcastMessage("§e========================================");

        // 1-2. 2초 뒤 라운드 시작
        startGameTask = new BukkitRunnable() {
            @Override
            public void run() {
                startRound();
            }
        }.runTaskLater(plugin, 40L); // 40 ticks = 2 seconds
    }

    // =========================================================================
    // 2. 라운드 시작 & 능력 추첨 단계
    // =========================================================================
    private void startRound() {
        if (!isRunning)
            return;
        round++;
        readyPlayers.clear();

        // [추가] 라운드 시작 시마다 랜덤 전장 재생성 (새로운 지형 설치)
        if (configManager.spawn_point != null) {
            arenaManager.prepareArena(configManager.spawn_point);
        }

        // [무적 시작] 능력 추첨 중에는 서로 공격할 수 없게 설정합니다.
        this.isInvincible = true;
        // Bukkit.broadcastMessage("§e[정보] 능력 추첨 중에는 무적 상태가 됩니다.");

        // AbilityManager에게 능력 초기화 요청 (리롤 횟수 등 리셋)
        if (abilityManager != null)
            abilityManager.resetAbilities();

        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage("§a§l=== " + round + "라운드 시작! ===");
        if (afkPlayers.isEmpty()) {
            Bukkit.broadcastMessage("§7열외(AFK) 대상자 : 없음");
        } else {
            // String.join을 사용하면 ["A", "B"] 리스트를 "A, B" 문자열로 예쁘게 바꿔줍니다.
            String afkNames = String.join(", ", afkPlayers);
            Bukkit.broadcastMessage("§7열외(AFK) 대상자 : " + afkNames);
        }
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        // 2-1. 맵 및 플레이어 상태 초기화
        Location center = configManager.spawn_point;
        if (center == null)
            center = Bukkit.getOnlinePlayers().iterator().next().getLocation();

        // [수정 포인트] 아레나 매니저에게 전장 준비 명령!
        // 여기서 날씨, 시간, 기반암, 에메랄드, 자기장, 월드 바닥의 아이템, 몬스터 초기화가 다 일어납니다.
        arenaManager.prepareArena(center);

        // 플레이어 초기화 및 능력 배정 전 덱 생성
        List<String> deck = new ArrayList<>();
        if (abilityManager != null) {
            deck.addAll(abilityManager.getAbilityCodes());

            // [추가] 배틀맵(기반암)이 없으면 알렉스(020) 능력 제외 (바닥 파괴 불가능)
            if (!configManager.battle_map) {
                deck.remove("020");
                // Bukkit.getLogger().info("[MocPlugin] 배틀맵 미사용으로 알렉스(020) 능력이 제외되었습니다.");
            }
        } else {
            // 만약 매니저가 없으면 비상용으로 기본 코드만 넣음 (안전장치)
            deck.add("001");
            Bukkit.getLogger().warning("AbilityManager가 연결되지 않아 덱을 생성하지 못했습니다.");
        }

        // 덱 섞기
        Collections.shuffle(deck);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isAfk(p.getName())) {
                p.setGameMode(GameMode.SPECTATOR); // 관전 모드 변경
                p.sendMessage("§7[MOC] 게임 열외(AFK) 상태이므로 관전 모드로 전환됩니다.");
                p.sendMessage("§7게임에 참여하고 싶으시면 다음 판에 '/moc afk 본인닉네임'을 입력해 해제하세요.");
                continue;
            }

            // [추가] 죽어있는 플레이어 강제 리스폰 (게임 참여를 위해)
            if (p.isDead()) {
                p.spigot().respawn();
            }

            // [수정] 테스트 모드면 크리에이티브, 아니면 서바이벌
            if (configManager.test) {
                p.setGameMode(GameMode.CREATIVE);
                p.sendMessage("§e[TEST] §f테스트 모드가 활성화되어 '크리에이티브' 모드로 설정됩니다.");
            } else {
                p.setGameMode(GameMode.SURVIVAL); // 관전 -> 서바이벌
            }
            if (configManager.spawn_point != null) {
                p.teleport(configManager.spawn_point); // 스폰 지점으로 이동
            }

            p.getInventory().clear();
            p.setHealth(20); // 기본 체력으로 일단 리셋 (전투 시작 시 60으로 늘어남)
            p.setFoodLevel(20);
            // [추가] 피격 무적 시간 초기화 (버그 방지)
            p.setMaximumNoDamageTicks(20);
            p.setNoDamageTicks(0);

            for (PotionEffect effect : p.getActivePotionEffects())
                p.removePotionEffect(effect.getType());
        }

        // [수정] 바로 능력을 주지 않고, 3초간 룰렛 연출을 시작합니다.
        startRouletteAnimation(deck);
    }

    /**
     * [추가] 능력 추첨 전 3초간의 룰렛 애니메이션을 재생합니다.
     */
    private void startRouletteAnimation(List<String> deck) {
        // 순환할 블럭 리스트: 에메랄드 -> 다이아 -> 금 -> 철
        final Material[] cycleBlocks = {
                Material.EMERALD_BLOCK,
                Material.DIAMOND_BLOCK,
                Material.GOLD_BLOCK,
                Material.IRON_BLOCK
        };

        new BukkitRunnable() {
            int ticks = 0;
            final int DURATION = 60; // 3초 (20 ticks * 3)

            @Override
            public void run() {
                if (!isRunning) {
                    this.cancel();
                    return;
                }

                // 3초가 지나면 종료하고 능력을 배정합니다.
                if (ticks >= DURATION) {
                    this.cancel();

                    // [효과] 종료음: 빰! (레벨업 + 다이아)
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1f);
                    }

                    // [효과] 바닥 전체를 옵시디언으로 변경 (글로벌 룰렛)
                    arenaManager.setArenaFloor(Material.OBSIDIAN);

                    // [효과] 마지막으로 옵시디언 파티클 펑!
                    spawnBlockParticles(Material.OBSIDIAN);

                    // 진짜 능력 배정 시작
                    assignAbilities(deck);
                    return;
                }

                // 0.3초 (6 ticks) 마다 블럭 종류 변경
                int cycleIndex = (ticks / 6) % cycleBlocks.length;
                Material currentMat = cycleBlocks[cycleIndex];

                // 1. 바닥 변경 (0.3초마다)
                if (ticks % 6 == 0) {
                    // [변경] 전장 바닥 전체 변경 (글로벌 룰렛)
                    arenaManager.setArenaFloor(currentMat);

                    // [효과] 회전음 (띵 띵)
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f); // 2f = 높은 음
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 2f);
                    }
                }

                // 2. 파티클 효과 (매 틱마다, 현재 블럭 재질로)
                spawnBlockParticles(currentMat);

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * [추가] 모든 플레이어 주변에 해당 블럭의 파편 파티클을 튀깁니다.
     */
    private void spawnBlockParticles(Material mat) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isAfk(p.getName()))
                continue;

            p.spawnParticle(Particle.BLOCK, p.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, mat.createBlockData());
        }
    }

    /**
     * [분리] 실제 능력을 배정하고 안내 메시지를 띄우는 로직
     */
    private void assignAbilities(List<String> deck) {
        int deckIndex = 0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isAfk(p.getName()))
                continue;

            // 능력 배정 (AbilityManager 연동)
            String abilityCode = deck.get(deckIndex % deck.size());
            if (abilityManager != null) {
                abilityManager.setPlayerAbility(p.getUniqueId(), abilityCode);

                // 리롤 횟수 설정 (콘피그 값)
                abilityManager.setRerollCount(p.getUniqueId(), configManager.re_point);

                // 능력 정보 출력
                abilityManager.showAbilityInfo(p, abilityCode, 0);
            }
            deckIndex++;
            deckIndex++;
        }

        // [추가] 리롤 포인트가 0인 경우 자동 준비 완료 처리
        if (configManager.re_point <= 0) {
            Bukkit.broadcastMessage("§a[MOC] 리롤 기회가 없어 자동으로 준비 완료됩니다.");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!afkPlayers.contains(p.getName())) { // AFK 제외
                    // 강제로 레디 처리
                    if (!readyPlayers.contains(p.getUniqueId())) {
                        readyPlayers.add(p.getUniqueId());
                        p.sendMessage("§a[System] 자동 준비 완료되었습니다.");
                    }
                }
            }
            // 타이머도 짧게 단축 (5초 후 시작)
            if (selectionTask != null)
                selectionTask.cancel();
            selectionTask = new BukkitRunnable() {
                int count = 5;

                @Override
                public void run() {
                    if (count <= 0) {
                        this.cancel();
                        prepareBattle();
                        return;
                    }
                    Bukkit.broadcastMessage("§e" + count + "초 후 전투 준비 단계로 넘어갑니다.");
                    count--;
                }
            }.runTaskTimer(plugin, 0, 20L);
            return; // 기존 타이머 로직 스킵
        }

        // 2-2. 능력 추첨 시간 (콘피그 start_time 초)
        if (selectionTask != null)
            selectionTask.cancel();
        selectionTask = new BukkitRunnable() {
            int timeLeft = configManager.start_time;

            @Override
            public void run() {
                // 모든 참가자가 준비 완료(yes)했으면 즉시 시작
                // AFK 제외 참가자 수 계산
                long activePlayerCount = Bukkit.getOnlinePlayers().size() - afkPlayers.size();

                if (activePlayerCount <= 0)
                    activePlayerCount = 1;

                if ((readyPlayers.size() >= activePlayerCount && activePlayerCount > 0) || timeLeft <= 0) {
                    this.cancel();
                    prepareBattle(); // 전투 준비 단계로 이동
                    return;
                }

                // 25초 경과 (남은 시간 5초) 카운트다운
                if (timeLeft <= 5) {
                    Bukkit.broadcastMessage("§c능력 자동 수락까지 " + timeLeft + "초...");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                    }
                }
                timeLeft--;
            }

        }.runTaskTimer(plugin, 0, 20L);
    }

    // =========================================================================
    // 3. 전투 준비 단계 (텔레포트 & 무적)
    // =========================================================================
    private void prepareBattle() {
        if (!isRunning)
            return;
        // 여기에 준비 안 된 플레이어들 전부 레디상태로 변경시켜줘.
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!afkPlayers.contains(p.getName()) && !readyPlayers.contains(p.getUniqueId())) {
                readyPlayers.add(p.getUniqueId());
            }
        }
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage("§6모든 플레이어가 준비되었습니다. 전장으로 이동합니다!");

        Location spawn = configManager.spawn_point;
        if (spawn == null)
            spawn = Bukkit.getOnlinePlayers().iterator().next().getLocation();

        // 3-1. 랜덤 좌표 텔레포트
        // 3-1. 랜덤 좌표 텔레포트
        // [수정] 자기장 크기(map_size) 내에서 랜덤하게 뿌립니다.
        // map_size는 지름이므로 반으로 나누면 반지름입니다. 안전을 위해 2블록 여유를 둡니다.
        int radius = (configManager.map_size / 2) - 2;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (afkPlayers.contains(p.getName()))
                continue;

            // [추가] 테스트 모드일 때는 자동으로 크리에이티브 모드로 전환합니다.
            if (configManager.test) {
                p.setGameMode(GameMode.CREATIVE);
            }

            // 스폰 포인트(자기장 중심) 기준 랜덤 좌표 생성
            double rx = (Math.random() * (radius * 2)) - radius; // -radius ~ +radius
            double rz = (Math.random() * (radius * 2)) - radius;

            Location targetLoc = spawn.clone().add(rx, 0, rz);

            // [사용자 요청 반영] 동굴이나 블럭 사이에 끼지 않도록 반드시 '맨 윗 블럭' 위로 잡습니다.
            // world.getHighestBlockYAt은 하늘에서부터 내려오며 처음 만나는 블록의 Y좌표를 줍니다.
            int highestY = targetLoc.getWorld().getHighestBlockYAt(targetLoc.getBlockX(), targetLoc.getBlockZ());

            // [안전장치] 만약 바닥이 없어서(공허) Y좌표가 너무 낮다면, 임시 발판을 생성합니다.
            if (highestY <= targetLoc.getWorld().getMinHeight()) {
                highestY = 64; // 안전한 높이 설정
                targetLoc.setY(highestY + 20.0); // 플레이어 발 위치 (블록 위 + 20칸)

                // [고도화] 단순 유리판 생성이 아니라, 해당 위치를 새로운 전장의 중심으로 잡고 아레나를 생성합니다.
                arenaManager.prepareArena(targetLoc);

                // [추가] 스폰 포인트도 안전한 곳으로 변경 (죽어도 여기로 오도록)
                p.setBedSpawnLocation(targetLoc, true);

                Bukkit.getLogger()
                        .warning("[MocPlugin] " + p.getName() + "님의 스폰 위치 하단에 블록이 없어 안전지대(Y=64)에 전장을 생성했습니다.");
            } else {
                // 정상적인 경우: 타겟 Y좌표 설정 (블록 위 + 20칸)
                targetLoc.setY(highestY + 20.0);
            }

            p.teleport(targetLoc);
        }

        // [수정] 평화 시간(무적)을 이 카운트다운 타이머에 적용합니다.
        // configManager.peace_time이 10이면, 10초 카운트다운을 합니다.
        // (최소 5초는 보장)
        int waitSeconds = Math.max(configManager.peace_time, 5);

        // 안내 메시지도 상황에 맞게 변경
        Bukkit.broadcastMessage("§b[알림] §f전투 시작 전 " + waitSeconds + "초간 준비 시간(무적)을 갖습니다.");

        new BukkitRunnable() {
            int count = waitSeconds;

            @Override
            public void run() {
                if (count <= 0) {
                    this.cancel();
                    startBattle(); // 전투 시작!
                    return;
                }

                // 5초 이하일 때만 타이틀 표시 (너무 길면 화면 가리니까)
                // 또는 원하시면 전체 카운트다운 보여줘도 됩니다. 지금은 5초 이하만.
                if (count <= 5) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle("§c" + count, "§7전투 시작 임박", 0, 20, 0);
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    }
                } else if (count % 10 == 0) {
                    // 10초 단위로는 채팅으로 알려줌
                    Bukkit.broadcastMessage("§7전투 시작까지 " + count + "초 남았습니다.");
                }

                count--;
            }
        }.runTaskTimer(plugin, 0, 20L);
    }

    // =========================================================================
    // 4. 전투 진행 단계
    // =========================================================================
    private void startBattle() {
        if (!isRunning)
            return;

        // [수정] 평화 시간(카운트다운)이 끝났으므로 즉시 무적 해제!
        isInvincible = false;

        // [추가] 전투 시작 시 월드의 모든 생명체(플레이어 제외) 제거 (드랍 x)
        // 첫 번째 플레이어의 월드를 기준으로 잡습니다.
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            org.bukkit.World world = Bukkit.getOnlinePlayers().iterator().next().getWorld();
            // 모든 살아있는 엔티티 제거 (플레이어 제외)
            for (org.bukkit.entity.LivingEntity le : world.getLivingEntities()) {
                if (!(le instanceof Player)) {
                    le.remove(); // drops nothing
                }
            }
            // [추가] 월드에 떨어진 모든 아이템 제거
            for (org.bukkit.entity.Entity item : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                item.remove();
            }
        }

        // [추가] 전투 시작 시 바닥을 기반암으로 초기화하고 중앙 에메랄드 복구
        arenaManager.setArenaFloor(Material.BEDROCK);
        arenaManager.resetCenterBlock();

        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        // 웅장한 소리와 함께 시작 알림
        Bukkit.broadcastMessage("§c§l[전투 시작] §f모든 적을 처치하십시오!");
        Bukkit.broadcastMessage("§c§l[경고] §f무적 시간이 종료되었습니다! 이제 공격이 가능합니다."); // 명확한 알림

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1f); // 무적 해제 느낌 팍팍

            if (afkPlayers.contains(p.getName()))
                continue;

            // 시작 전 기존 버프 다 제거.
            p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
            // 불이 붙어 있다면 불도 꺼주는 매너!
            p.setFireTicks(0);

            // 배고픔, 풀로 회복.
            p.setFoodLevel(20); // 허기 게이지를 20(가득)으로 설정합니다.
            p.setSaturation(10.0f); // 포화도를 높여서 허기가 금방 닳지 않게 서비스!

            // 체력 3줄(60) 설정
            AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH); // [수정] 1.21.11 표준 상수 사용
            if (maxHealth != null)
                maxHealth.setBaseValue(60.0);
            p.setHealth(60.0);

            // 아이템 지급 (칼-고기-물-유리-포션-갑옷-능력템) + 능력부여.
            giveBattleItems(p);
        }

        // 태스크 관리 (타이머)

        // 4-3. 자기장 타이머 시작 (콘피그 final_time 후 줄어듦)
        if (configManager.final_fight) {
            // [버그 수정] 기존에 예약된 자기장 시작 태스크가 있다면 취소
            if (borderStartTask != null) {
                borderStartTask.cancel();
            }
            borderStartTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!isRunning)
                        return;

                    // [추가] 테스트 모드일 때는 경고 메시지를 띄우지 않고 로직을 ArenaManager에게 위임합니다.
                    // ArenaManager.startBorderShrink() 내부에서도 test 체크를 하므로 안전합니다.
                    if (!configManager.test) {
                        Bukkit.broadcastMessage("§4§l[경고] §c자기장이 줄어들기 시작합니다!");
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.playSound(p.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 1f, 1f);
                        }
                    }
                    // 드디어 아레나 매니저의 그 함수를 여기서 부릅니다!
                    arenaManager.startBorderShrink();
                }
            }.runTaskLater(plugin, configManager.final_time * 20L); // ex) 5분 하고 싶으면 300 입력하면 됨.
        }

        // 자기장 대미지 체크 태스크 시작 (ArenaManager 기능 활용 권장)
        arenaManager.startBorderDamage();
    }

    // [추가] 무적 상태일 때 대미지 막기
    @EventHandler
    public void onEntityDamage(EntityDamageEvent e) {
        // 게임 중이 아니면 관여 안 함
        if (!isRunning)
            return;

        // 무적 상태(능력 추첨 중 or 평화 시간)라면 대미지 무효화
        // 단, 낙사나 공허는 죽을 수도 있으니 놔둘까요? -> 보통 평화 시간에는 완전 무적을 원함.
        if (isInvincible) {
            if (e.getEntity() instanceof Player) {
                e.setCancelled(true);
            }
        }
    }

    // 아이템 지급 로직 (요청하신 순서 준수)
    private void giveBattleItems(Player p) {
        p.getInventory().clear();

        // 1. 철 칼
        p.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
        // 3. 물 양동이
        p.getInventory().addItem(new ItemStack(Material.WATER_BUCKET));
        // 4. 유리 5개
        p.getInventory().addItem(new ItemStack(Material.GLASS, 5));
        // 5. 고기 64개
        p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 64));

        // 6. [추가] 1레벨 체력 재생 포션 (1분)
        // 1.21 버전 대응 코드
        ItemStack regenPotion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) regenPotion.getItemMeta();
        if (meta != null) {
            // 커스텀 이펙트로 정확히 1분(1200틱), 레벨 1(amplifier 0) 부여
            meta.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 60, 0), true);
            meta.setDisplayName("§d재생의 물약 (1분)");
            regenPotion.setItemMeta(meta);
        }
        p.getInventory().addItem(regenPotion);

        // 7. 철 흉갑 자동으로 입혀주기
        p.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));

        // [추가] 점수판 가동
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().start();
        }
        // // [▲▲▲ 여기까지 변경됨 ▲▲▲]

        // 인벤토리 새로고침 (아이템이 바뀐 걸 유저 화면에 즉시 적용)
        p.updateInventory();

        // 8. 고유 능력 아이템 (AbilityManager가 지급)
        if (abilityManager != null) {
            abilityManager.giveAbilityItems(p);
        }

        p.sendMessage("§a[MOC] 모든 전투 아이템이 지급되었습니다! 행운을 빕니다.");
    }

    // =========================================================================
    // 5. 게임 종료 및 승리 체크
    // =========================================================================
    public void stopGame() {
        if (!isRunning) {
            Bukkit.broadcastMessage("§b게임이 시작되지 않았습니다.");
            return;
        }

        // [추가] 점수판 중지 (동기화 이슈 방지 위해 가장 먼저 끔)
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().stop();
        }

        // [버그 수정] 예약된 시작 태스크가 있다면 취소
        if (startGameTask != null && !startGameTask.isCancelled()) {
            startGameTask.cancel();
        }

        // [버그 수정] 자기장 시작 대기 태스크 취소
        if (borderStartTask != null) {
            borderStartTask.cancel();
            borderStartTask = null;
        }

        if (selectionTask != null)
            selectionTask.cancel();
        arenaManager.stopTasks(); // 자기장 등 정지
        // 자기장=월드보더 초기화. 클리어매니저에서 가져옴
        plugin.getClearManager().worldBorderCear();

        // [버그 수정] 능력 관련 엔티티(소환수, 투사체 등) 모두 제거
        if (abilityManager != null) {
            abilityManager.resetAbilities();
        }

        // 점수 내림차순 정렬 및 출력
        List<Map.Entry<UUID, Integer>> sortedScores = new ArrayList<>(scores.entrySet());
        sortedScores.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());

        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage("§b=== MOC 게임 종료! ===");
        if (!sortedScores.isEmpty()) {
            Map.Entry<UUID, Integer> first = sortedScores.get(0);
            Bukkit.broadcastMessage(
                    "§e1등 : " + Bukkit.getOfflinePlayer(first.getKey()).getName() + " [" + first.getValue() + "점]");

            // 1등 축하 폭죽
            Player winner = Bukkit.getPlayer(first.getKey());
            if (winner != null) {
                spawnFireworks(winner.getLocation());
            }
        }

        if (sortedScores.size() > 1) {
            Map.Entry<UUID, Integer> second = sortedScores.get(1);
            Bukkit.broadcastMessage(
                    "§f2등 : " + Bukkit.getOfflinePlayer(second.getKey()).getName() + " [" + second.getValue() + "점]");
        }
        if (sortedScores.size() > 2) {
            Map.Entry<UUID, Integer> third = sortedScores.get(2);
            Bukkit.broadcastMessage(
                    "§63등 : " + Bukkit.getOfflinePlayer(third.getKey()).getName() + " [" + third.getValue() + "점]");
        }
        Bukkit.broadcastMessage("§d✿ 고생하셨습니다! ✿");

        // 유저 초기화
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null)
                maxHealth.setBaseValue(20.0);
            p.setHealth(20.0);

            // [추가] 방어 속성 초기화 (토가 히미코 버그 방지)
            // 1.21.11 대응: Registry 사용
            Attribute armorAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.armor"));
            Attribute toughnessAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.armor_toughness"));
            Attribute knockbackAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.knockback_resistance"));

            if (armorAttr != null) {
                AttributeInstance armor = p.getAttribute(armorAttr);
                if (armor != null)
                    armor.setBaseValue(0.0);
            }
            if (toughnessAttr != null) {
                AttributeInstance toughness = p.getAttribute(toughnessAttr);
                if (toughness != null)
                    toughness.setBaseValue(0.0);
            }
            if (knockbackAttr != null) {
                AttributeInstance knockback = p.getAttribute(knockbackAttr);
                if (knockback != null)
                    knockback.setBaseValue(0.0);
            }
        }

        // [강화] 게임 데이터 초기화
        scores.clear();
        afkPlayers.clear();
        readyPlayers.clear();
        players.clear();
        livePlayers.clear();

        isRunning = false;
        configManager.spawn_point = null;
        isInvincible = false;
    }

    // 사람 죽였을 때 - 사망 이벤트 핸들러 (점수 계산)
    // 사람 죽였을 때 - 사망 이벤트 핸들러 (점수 계산)
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        // [중요] 좀비 리스너 차단: 플러그인이 비활성화된 상태라면 이벤트를 무시합니다.
        if (!plugin.isEnabled()) {
            return;
        }

        if (!isRunning)
            return;

        Player victim = e.getEntity();
        Player killer = victim.getKiller();

        // [디버그] 사망 로그 출력 (원인 파악용)
        org.bukkit.event.entity.EntityDamageEvent lastDamage = victim.getLastDamageCause();
        String cause = (lastDamage != null) ? lastDamage.getCause().name() : "UNKNOWN";
        plugin.getLogger().info("[MocPlugin] Player died: " + victim.getName() + ", Cause: " + cause);

        // 킬 점수 +1
        if (killer != null && !killer.equals(victim)) {
            scores.put(killer.getUniqueId(), scores.getOrDefault(killer.getUniqueId(), 0) + 1);
            killer.sendMessage("§e[MOC] §f적을 처치하여 +1점!");
        }

        // e.setDrops(Collections.emptyList()); // 아이템 떨구기 방지 (깔끔하게)
        e.setDeathMessage(null); // 기본 데스메시지 끄기
        Bukkit.broadcastMessage("§c☠ §f" + victim.getName() + "님이 탈락했습니다.");
        // [▼▼▼ 여기서부터 변경됨 ▼▼▼]
        // 1. 즉시 리스폰 및 관전 모드 전환 (1틱 뒤 실행)
        new BukkitRunnable() {
            @Override
            public void run() {
                victim.spigot().respawn(); // <--- [여기 변경됨!!!] 자동 리스폰

                // [추가] 죽은 플레이어의 능력(소환수 등) 정리
                if (abilityManager != null) {
                    abilityManager.cleanup(victim);
                }

                victim.setGameMode(GameMode.SPECTATOR); // <--- [여기 변경됨!!!] 관전 모드 변경

                // 2. 생존자 체크 (스펙테이터가 아닌 사람만 필터링)
                List<Player> survivors = Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.getGameMode() == GameMode.SURVIVAL && !afkPlayers.contains(p.getName()))
                        .collect(Collectors.toList());

                // 최후의 1인 확인
                // [수정] 낙사 등 자가 사망 시에도 1명만 남으면 라운드가 종료되도록 설정
                // 테스트 모드여도 1명이 남으면 라운드를 종료하여 결과를 보여줍니다.
                if (survivors.size() <= 1) {
                    Player winner = survivors.isEmpty() ? null : survivors.get(0);
                    if (winner != null) {
                        endRound(java.util.Collections.singletonList(winner));
                    } else {
                        Bukkit.broadcastMessage("§7다른 생존자가 없어 라운드를 종료합니다.");
                        startRoundAfterDelay();
                    }
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * 라운드를 종료하고 승자를 처리합니다.
     *
     * @param winners 마지막까지 살아남은 플레이어들의 명단
     */
    public void endRound(List<Player> winners) {
        // 장벽이 줄어드는 작업이 있다면 모두 멈춥니다.
        arenaManager.stopTasks();

        // [버그 수정] 자기장 시작 대기 태스크가 돌고 있다면 취소
        if (borderStartTask != null) {
            borderStartTask.cancel();
            borderStartTask = null;
        }

        if (!isRunning)
            return;
        isInvincible = true; // 무적 상태 활성화.;

        // [추가] 승자들의 능력 '게임 종료' 훅 실행 (토가 히미코 변신 해제 등)
        if (abilityManager != null) {
            for (Player winner : winners) {
                String code = abilityManager.getPlayerAbilities().get(winner.getUniqueId());
                if (code != null) {
                    me.user.moc.ability.Ability ability = abilityManager.getAbility(code);
                    if (ability != null) {
                        ability.onGameEnd(winner);
                    }
                }
            }
        }

        // 2. 승자 이름 합치기 (예: "오승엽, 남상도, 박연준")
        // stream().map(Player::getName) -> 플레이어 객체에서 이름만 쏙쏙 뽑아내기
        // joining(", ") -> 뽑아낸 이름들 사이에 쉼표와 공백을 넣어서 하나로 합치기
        String winnerNames = winners.stream()
                .map(Player::getName)
                .collect(java.util.stream.Collectors.joining(", "));

        // 3. 점수 계산 및 메시지 출력 로직 (단수/복수 구분)
        boolean onlyOneWinner = (winners.size() == 1); // 승자가 딱 한 명인지 확인

        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage("§6==========================");
        // 여러 명이면 "오승엽, 남상도" 처럼 출력됩니다.
        Bukkit.broadcastMessage("§e마지막까지 살아남은 플레이어 : " + winnerNames);

        // 딱 한 명일 때만 실행되는 보너스 구간
        if (onlyOneWinner) {
            Player winner = winners.get(0); // 명단에서 첫 번째(유일한) 사람 꺼내기

            // 점수 부여 (+2점)
            scores.put(winner.getUniqueId(), scores.getOrDefault(winner.getUniqueId(), 0) + 2);

            // 보너스 메시지 출력
            Bukkit.broadcastMessage("§e최종 생존자 [" + winner.getName() + "] 추가 점수 +2점");

            // 승자 위치에 폭죽 발사
            spawnFireworks(winner.getLocation());
        } else {
            // 여러 명인 경우: 점수 지급 메시지 없이 그냥 축하 폭죽만 모든 승자 위치에 발사
            for (Player p : winners) {
                spawnFireworks(p.getLocation());
            }
            Bukkit.broadcastMessage("§e최종 전장의 생존 점수는 없습니다.");
        }

        // 4. 점수 현황판 출력
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage("§e이번 라운드에 나온 능력은 아래와 같습니다.");
        Bukkit.broadcastMessage(" ");

        // 데이터 수집용 리스트
        // 점수 및 통계 출력 (공통 함수 사용)
        printRoundStats(winners);

        Bukkit.broadcastMessage("§6==========================");

        // 5. 승리 조건 체크
        boolean gameShouldStop = false;
        for (Player p : winners) {
            if (scores.getOrDefault(p.getUniqueId(), 0) >= configManager.win_value) {
                gameShouldStop = true;
                break;
            }
        }

        if (gameShouldStop) {
            stopGame();
        } else {
            Bukkit.broadcastMessage("§75초 뒤 다음 라운드가 시작됩니다.");
            startRoundAfterDelay();
        }
    }

    /**
     * [추가] 라운드 강제 스킵 (관리자용)
     * 생존 점수 지급 없이 바로 다음 라운드로 넘어갑니다.
     */
    /**
     * [추가] 라운드 강제 스킵 (관리자용)
     * 생존 점수 지급 없이 바로 다음 라운드로 넘어갑니다.
     */
    /**
     * [추가] 라운드 강제 스킵 (관리자용)
     * 생존 점수 지급 없이 바로 다음 라운드로 넘어갑니다.
     */
    public void skipRound() {
        // 장벽이 줄어드는 작업이 있다면 모두 멈춥니다.
        arenaManager.stopTasks();

        // [버그 수정] 자기장 시작 대기 태스크가 돌고 있다면 취소
        if (borderStartTask != null) {
            borderStartTask.cancel();
            borderStartTask = null;
        }

        if (!isRunning)
            return;
        isInvincible = true; // 무적 상태 활성화

        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage("§6==========================");
        Bukkit.broadcastMessage("§c[관리자] §e라운드가 강제로 종료되었습니다.");
        Bukkit.broadcastMessage("§7(생존 점수는 지급되지 않습니다)");

        // 점수 및 통계 출력 (공통 함수 사용 - 승자 없음)
        printRoundStats(null);

        Bukkit.broadcastMessage("§6==========================");

        // 5. 승리 조건 체크 (혹시 킬 점수로 끝났을 수도 있으니)
        boolean gameShouldStop = false;
        // scores 맵을 순회하며 점수 체크
        for (Map.Entry<UUID, Integer> entry : scores.entrySet()) {
            if (entry.getValue() >= configManager.win_value) {
                gameShouldStop = true;
                break;
            }
        }

        if (gameShouldStop) {
            stopGame();
        } else {
            Bukkit.broadcastMessage("§75초 뒤 다음 라운드가 시작됩니다.");
            startRoundAfterDelay();
        }
    }

    // [리팩토링] 라운드 통계 출력 공통 메서드
    private void printRoundStats(List<Player> winners) {
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage("§e이번 라운드에 나온 능력은 아래와 같습니다.");
        Bukkit.broadcastMessage(" ");

        // 데이터 수집용 리스트
        class ResultEntry {
            String name;
            int score;
            String abilityName;
            int usage;
            boolean isWinner;

            public ResultEntry(String name, int score, String abilityName, int usage, boolean isWinner) {
                this.name = name;
                this.score = score;
                this.abilityName = abilityName;
                this.usage = usage;
                this.isWinner = isWinner;
            }
        }

        List<ResultEntry> results = new ArrayList<>();

        Set<UUID> checkSet = new HashSet<>(scores.keySet());
        for (Player p : Bukkit.getOnlinePlayers()) {
            checkSet.add(p.getUniqueId());
        }

        for (UUID uuid : checkSet) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null)
                continue;

            int score = scores.getOrDefault(uuid, 0);

            String abilityCode = (abilityManager != null && abilityManager.getPlayerAbilities() != null)
                    ? abilityManager.getPlayerAbilities().get(uuid)
                    : null;
            String abilityName = "없음";
            int usage = 0;

            if (abilityCode != null && abilityManager != null) {
                me.user.moc.ability.Ability ab = abilityManager.getAbility(abilityCode);
                if (ab != null) {
                    abilityName = ab.getName();
                }
                usage = abilityManager.getUsageCount(abilityCode);
            }

            // 승자 여부 체크
            boolean isWinner = false;
            if (winners != null) {
                for (Player w : winners) {
                    if (w.getUniqueId().equals(uuid)) {
                        isWinner = true;
                        break;
                    }
                }
            }

            results.add(new ResultEntry(name, score, abilityName, usage, isWinner));
        }

        // 정렬: 승자 우선, 그 다음 점수 높은 순
        results.sort((a, b) -> {
            if (a.isWinner != b.isWinner)
                return a.isWinner ? -1 : 1;
            return Integer.compare(b.score, a.score);
        });

        // 라운드 정보 출력.
        // [수정] 픽셀 기반 정렬로 변경
        // 1. 헤더 출력
        String header = String.format("§7%s | %s | %s",
                padRightPixel("플레이어", 100),
                padRightPixel("능력명", 130),
                "횟수");
        Bukkit.broadcastMessage(header);

        for (ResultEntry e : results) {
            // 승자는 노란색(§e), 나머지는 흰색(§f)
            String color = e.isWinner ? "§e" : "§f";

            String paddedName = padRightPixel(e.name, 100);
            String paddedAbility = padRightPixel(e.abilityName, 130);

            String line = String.format("%s%s §7| %s%s §7| %s%s",
                    color, paddedName,
                    color, paddedAbility,
                    color, e.usage + "회");
            Bukkit.broadcastMessage(line);
        }
    }

    // 시작 전 잠시 대기 시간.
    private void startRoundAfterDelay() {
        if (!isRunning)
            return;
        new BukkitRunnable() {

            // @@@@@@ 해당 부분은 라운드 시작할 때랑 지금이랑 유저를 비교하여 플레이할 유저가 없어진 경우에만 실행하게 수정 필요함. 아래의 문구를
            // 참고.
            /*
             * afk 완성 시 `startRoundAfterDelay` 함수 수정 할 부분.
             * 
             * 라운드 종료 후 이전 라운드에 플레이한 사람이
             * 죽어서 관전 상태가 된 사람을 제외하고
             * 서버에 나감 or 죽은 상태로 리스폰 안함인 경우
             * 10초 의 대기 시간을 준다.
             * 
             * 해당 대기 시간 안에 접속하지 않은 경우,
             * 해당 서버 미접속 유저들을 전부 afk 상태로 변경하여 새로운 라운드에 참여하지 않게 구현.
             * 이때 게임 중 afk가 된 유저의 스코어 점수는 초기화 되지 않음.
             * 
             * 이후에 재참가 가능하도록. 대신 라운드 종료 후 점수 출력 시 기존과 동일하게 출력하면서 맨 마지막줄에 다음과 같은 문구를 추가함.
             * 
             * (붉은 색으로)afk 유저 : 닉네임, 닉네임, 닉네임
             * 
             * 이후 afk로 지정된 해당 유저가 다시 접속하여 /moc afk 유저이름 명령어를 통해 afk 상태에서 해제된 경우엔
             * 현재 진행 중인 라운드를 제외한 다음 라운드부터 다시 능력 배정되며 게임에 참가됨.
             * 
             * 라운드 종료 후 사람 수를 체크할 때 서버에 신규 유저가 접속한 경우
             * 해당 신규 유저를 afk 상태로 지정해둔게 아닌 이상 해당 신규 유저도 다음 라운드에 자동으로 추가되어 같이 게임하게 됨.
             */
            @Override
            public void run() {
                // 접속 끊긴 사람이 있는지 확인
                List<String> missingPlayers = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    // 여기서는 기존 참여자 중 나간 사람을 체크하는 로직이 필요하지만,
                    // 간단하게 현재 접속 인원 중 게임 가능 인원을 체크합니다.
                }

                // 5초 대기 후 startRound() 실행
                // Bukkit.broadcastMessage("§e[MOC] 잠시 후 다음 라운드가 시작됩니다. (재접속 대기)"); // <--- [여기
                // 변경됨!!!]

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        startRound();
                    }
                }.runTaskLater(plugin, 100L); // 100틱 = 5초 기다림
            }
        }.runTaskLater(plugin, 100L); // 기본 5초 대기 .
    }

    // =========================================================================
    // 유틸리티 및 이벤트 리스너
    // =========================================================================

    // 무적 시간 대미지 방지 isInvincible <ㅡ t 면 무적 f 면 무적해제
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (isInvincible && e.getEntity() instanceof Player) {
            e.setCancelled(true);
        }
    }

    // 핫바 0번 슬롯 고정 (요청하신 기능)
    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent e) {
        // if (isRunning && !isInvincible) { // 전투 중일 때만
        // // 핫바를 1칸으로 변경하는 효과
        // e.getPlayer().getInventory().setHeldItemSlot(0);
        // }
    }

    // AFK 관리 메소드 (커맨드에서 호출)
    public void toggleAfk(String name) {
        if (afkPlayers.contains(name))
            afkPlayers.remove(name);
        else
            afkPlayers.add(name);
    }

    // Yes, Re, Check 등은 AbilityManager로 위임하거나 여기서 처리
    public void playerReady(Player p) {
        if (!isRunning)
            return;
        if (!readyPlayers.contains(p.getUniqueId())) {
            readyPlayers.add(p.getUniqueId());
            p.sendMessage("§a[MOC] 준비 완료!");
            p.sendMessage(" ");
            p.sendMessage(" ");
            p.sendMessage(" ");
            p.sendMessage(" ");
            p.sendMessage(" ");
            p.sendMessage(" ");
        }
    }

    /**
     * [추가됨] 관리자 명령(/moc set) 등에 의해 강제로 준비 완료 상태로 변경합니다.
     */
    public void playerReadyTarget(String name) {
        Player p = Bukkit.getPlayer(name);
        if (p != null && isRunning) {
            if (!readyPlayers.contains(p.getUniqueId())) {
                readyPlayers.add(p.getUniqueId());
                p.sendMessage("§e[MOC] §f관리자에 의해 능력이 확정되어 준비 완료 상태로 변경되었습니다.");
            }
        }
    }

    /**
     * [추가] 모든 플레이어를 강제로 준비 완료 상태로 만듭니다.
     */
    public void allReady() {
        if (!isRunning)
            return;

        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            // AFK가 아니고 아직 준비 안 된 사람만
            if (!afkPlayers.contains(p.getName()) && !readyPlayers.contains(p.getUniqueId())) {
                readyPlayers.add(p.getUniqueId());
                p.sendMessage("§e[MOC] §f관리자에 의해 모든 플레이어가 준비 완료 처리되었습니다.");
                count++;
            }
        }
        Bukkit.broadcastMessage("§a[관리자] §f총 " + count + "명의 플레이어를 강제로 준비 완료 시켰습니다.");
    }

    // 능력 리롤.
    public void playerReroll(Player p) {
        if (!isRunning)
            return;
        // [▼▼▼ 추가됨: 이미 준비 완료(Yes)한 경우 리롤 차단 ▼▼▼]
        if (readyPlayers.contains(p.getUniqueId())) {
            p.sendMessage("§c[!] 준비 완료 후 능력을 바꿀 수 없습니다.");
            return;
        }
        // [▲▲▲ 여기까지 추가됨 ▲▲▲]
        if (abilityManager != null)
            abilityManager.rerollAbility(p);
    }

    // AbilityManager에게 현재 플레이어의 능력을 물어봐서 출력
    public void showAbilityDetail(Player p) {
        /*
         * if (!isRunning)
         * return;
         */
        // (현재 구조상 AbilityManager가 담당하는게 맞음)
        if (abilityManager != null) {
            abilityManager.showAbilityDetail(p);
        }
    }

    // 승리 축하 폭죽 로직 (생략 가능, 단순화)
    /**
     * 승리 축하 폭죽 로직 (슈슈슈슈슉!)
     * 승리한 플레이어 머리 위로 별 모양 폭죽 10개를 연속으로 발사합니다.
     */
    private void spawnFireworks(Location loc) {
        // 0.2초(4틱) 간격으로 폭죽을 쏘기 위한 반복 작업 시작!
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 10) { // 10개를 다 쐈으면 종료
                    this.cancel();
                    return;
                }

                // 폭죽 소환! (플레이어 머리 위 약간 랜덤한 위치)
                Location spawnLoc = loc.clone().add(Math.random() * 2 - 1, 3, Math.random() * 2 - 1);
                org.bukkit.entity.Firework fw = spawnLoc.getWorld().spawn(spawnLoc, org.bukkit.entity.Firework.class);
                org.bukkit.inventory.meta.FireworkMeta fm = fw.getFireworkMeta();

                // 별 모양(STAR) 폭죽 효과 설정! 색깔도 알록달록하게 섞어볼게요.
                fm.addEffect(FireworkEffect.builder()
                        .withColor(Color.YELLOW, Color.ORANGE, Color.RED) // 기본 색상
                        .withFade(Color.WHITE, Color.FUCHSIA) // 사라질 때 색상
                        .with(FireworkEffect.Type.STAR) // ★별 모양★
                        .flicker(true) // 반짝임 효과 추가
                        .trail(true) // 꼬리 효과 추가
                        .build());

                fm.setPower(1); // 폭죽 발사 강도
                fw.setFireworkMeta(fm);

                count++;
            }
        }.runTaskTimer(plugin, 0, 4L); // 0틱부터 시작해서 4틱(0.2초)마다 실행!
    }

    // 1. [일반 블록 설치 검사] 유리가 아니면 설치를 막습니다.
    @EventHandler
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        // 게임 중이 아니라면 검사하지 않고 통과시킵니다.
        if (!isRunning)
            return;

        // 플레이어가 설치하려는 블록의 종류를 확인합니다.
        org.bukkit.Material blockType = e.getBlock().getType();

        // 만약 설치하려는 블록이 '유리(GLASS)'가 아니라면?
        if (blockType != org.bukkit.Material.GLASS) {
            // 이벤트를 취소하여 블록이 설치되지 않게 합니다.
            e.setCancelled(true);

            // (선택 사항) 플레이어에게 경고 메시지를 보냅니다.
            e.getPlayer().sendMessage("§c[경고] 게임 중에는 유리와 물만 설치할 수 있습니다!");
        }
    }

    // 2. [양동이 사용 검사] 물 양동이가 아니면 붓기를 막습니다.
    @EventHandler
    public void onBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent e) {
        // 게임 중이 아니라면 통과
        if (!isRunning)
            return;

        // 플레이어가 들고 있는 양동이의 종류를 확인합니다.
        org.bukkit.Material bucketType = e.getBucket();

        // 만약 사용하려는 양동이가 '물 양동이(WATER_BUCKET)'가 아니라면? (용암 등 금지)
        if (bucketType != org.bukkit.Material.WATER_BUCKET) {
            // 물 붓기를 취소시킵니다.
            e.setCancelled(true);
            e.getPlayer().sendMessage("§c[경고] 게임 중에는 물만 부을 수 있습니다!");
        }
    }

    // [유틸리티] 채팅창 줄맞춤을 위한 도우미 메서드들

    /**
     * 문자열의 픽셀 너비를 계산합니다. (마인크래프트 기본 폰트 기준 근사치)
     */
    private int getPixelWidth(String s) {
        if (s == null)
            return 0;
        int width = 0;
        for (char c : s.toCharArray()) {
            if (c >= 0xAC00 && c <= 0xD7A3) { // 한글
                width += 9; // 한글은 보통 9~10px
            } else if (c == 'f' || c == 'k' || c == '{' || c == '}' || c == '<' || c == '>') {
                width += 5;
            } else if (c == 'i' || c == ':' || c == ';' || c == '.' || c == ',' || c == '!' || c == '|') {
                width += 2;
            } else if (c == 'l' || c == '\'') {
                width += 3;
            } else if (c == 't' || c == 'I' || c == '[' || c == ']') {
                width += 4;
            } else if (c == ' ') {
                width += 4;
            } else if (c >= 'A' && c <= 'Z') {
                width += 6; // 대문자 평균
            } else if (c >= 'a' && c <= 'z') {
                width += 6; // 소문자 평균
            } else if (c >= '0' && c <= '9') {
                width += 6; // 숫자
            } else {
                // 기타 특수문자 or CJK
                if (Character.isIdeographic(c))
                    width += 9;
                else
                    width += 6;
            }
        }
        return width;
    }

    /**
     * 목표 픽셀 너비(targetPixelWidth)가 될 때까지 공백(4px)을 추가합니다.
     */
    private String padRightPixel(String s, int targetPixelWidth) {
        int currentWidth = getPixelWidth(s);
        if (currentWidth >= targetPixelWidth) {
            return s;
        }

        StringBuilder sb = new StringBuilder(s);
        while (getPixelWidth(sb.toString()) < targetPixelWidth) {
            sb.append(" ");
        }
        return sb.toString();
    }

    /**
     * [추가] 플레이어가 서버를 나갈 때 능력 관련 요소를 정리합니다.
     */
    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (abilityManager != null) {
            abilityManager.cleanup(p);
        }

        // [추가] 퇴장 시 게임 진행 중이라면 생존자 체크하여 라운드 종료 처리
        if (isRunning) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!isRunning)
                        return;

                    List<Player> survivors = Bukkit.getOnlinePlayers().stream()
                            .filter(player -> player.getGameMode() == GameMode.SURVIVAL
                                    && !afkPlayers.contains(player.getName()))
                            .collect(Collectors.toList());

                    if (survivors.size() <= 1) {
                        Player winner = survivors.isEmpty() ? null : survivors.get(0);
                        if (winner != null) {
                            endRound(java.util.Collections.singletonList(winner));
                        } else {
                            startRoundAfterDelay();
                        }
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }
}