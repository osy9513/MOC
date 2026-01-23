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
    // 게임 상태 변수
    private boolean isRunning = false;
    private boolean isInvincible = false;
    private int round = 0;
    // 태스크 관리 (타이머)
    private BukkitTask selectionTask; // 능력 추첨 타이머
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
        Bukkit.broadcastMessage("§f기본 체력: 3줄(60칸)");
        Bukkit.broadcastMessage("§f기본 지급: 철칼, 구운 소고기64개, 물 양동이, 유리10개, 재생포션, 철 흉갑");

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
        Bukkit.broadcastMessage("§f게임 모드 : 개인전");
        Bukkit.broadcastMessage("§7잠시 후 능력을 추첨합니다.");
        Bukkit.broadcastMessage("§e========================================");

        // 1-2. 2초 뒤 라운드 시작
        new BukkitRunnable() {
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
        round++;
        readyPlayers.clear();

        // [무적 시작] 능력 추첨 중에는 서로 공격할 수 없게 설정합니다.
        this.isInvincible = true;
        //Bukkit.broadcastMessage("§e[정보] 능력 추첨 중에는 무적 상태가 됩니다.");

        // AbilityManager에게 능력 초기화 요청 (리롤 횟수 등 리셋)
        if (abilityManager != null)
            abilityManager.resetAbilities();

        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage("§a§l=== " + round + "라운드 시작! ===");
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


        // 플레이어 초기화 및 능력 배정
        // (중복 안 나오게 셔플)
        List<String> deck = new ArrayList<>(Arrays.asList("001", "002", "003", "004"));
        Collections.shuffle(deck);
        int deckIndex = 0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (afkPlayers.contains(p.getName()))
                continue;

            // [▼▼▼ 여기서부터 변경됨 ▼▼▼]
            // 관전 모드였던 사람도 다시 참여시켜야 하므로 모드 변경 필수!
            p.setGameMode(GameMode.SURVIVAL); // <--- [여기 변경됨!!!] 관전 -> 서바이벌

            // 텔레포트 (이미 소스에 있지만 확실히 체크)
            if (configManager.spawn_point != null) {
                p.teleport(configManager.spawn_point); // <--- [여기 변경됨!!!] 스폰 지점으로 이동
            }

            p.getInventory().clear();
            p.setHealth(20); // 기본 체력으로 일단 리셋 (전투 시작 시 60으로 늘어남)
            p.setFoodLevel(20);
            // [▲▲▲ 여기까지 변경됨 ▲▲▲]

            for (PotionEffect effect : p.getActivePotionEffects())
                p.removePotionEffect(effect.getType());

            // 능력 배정 (AbilityManager 연동)
            String abilityCode = deck.get(deckIndex % deck.size());
            if (abilityManager != null) {
                // AbilityManager에 '이 유저는 이 능력이다'라고 설정하는 메소드가 있다고 가정하거나
                // AbilityManager가 public map을 가지고 있다면 직접 put
                // 여기서는 AbilityManager에 setPlayerAbility 메소드를 추가했다고 가정합니다.
                // 만약 없다면 AbilityManager.java에 추가가 필요합니다!
                abilityManager.setPlayerAbility(p.getUniqueId(), abilityCode);

                // 리롤 횟수 설정 (콘피그 값)
                abilityManager.setRerollCount(p.getUniqueId(), configManager.re_point);

                // 설명 메세지 출력 (AbilityManager가 담당)
                abilityManager.showAbilityInfo(p, abilityCode, 0);
            }
            deckIndex++;
        }

        // 2-2. 능력 추첨 시간 (콘피그 start_time 초)
        if (selectionTask != null)
            selectionTask.cancel();
        selectionTask = new BukkitRunnable() {
            int timeLeft = configManager.start_time;

            @Override
            public void run() {
                // 모든 참가자가 준비 완료(yes)했으면 즉시 시작
                long activePlayerCount = Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !afkPlayers.contains(p.getName())).count();

                if ((readyPlayers.size() >= activePlayerCount && activePlayerCount > 0) || timeLeft <= 0) {
                    this.cancel();

                    // [무적 해제] 추첨 시간이 끝났으니 이제 데미지가 들어가게 합니다.
                    isInvincible = false;
                    //Bukkit.broadcastMessage("§c[정보] 무적 상태가 해제되었습니다! 곧 배틀이 시작됩니다.");

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
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage("§6모든 플레이어가 준비되었습니다. 전장으로 이동합니다!");

        Location spawn = configManager.spawn_point;
        if (spawn == null)
            spawn = Bukkit.getOnlinePlayers().iterator().next().getLocation();

        // 3-1. 랜덤 좌표 텔레포트
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (afkPlayers.contains(p.getName()))
                continue;

            // 스폰 포인트 주변 랜덤 산개
            double rx = (Math.random() * 20) - 10;
            double rz = (Math.random() * 20) - 10;
            Location tpLoc = spawn.clone().add(rx, 1, rz);
            p.teleport(tpLoc);
        }

        // 3-2. 5초 카운트다운 및 무적 설정
        isInvincible = true;

        new BukkitRunnable() {
            int count = 5;

            @Override
            public void run() {
                if (count <= 0) {
                    this.cancel();
                    startBattle(); // 전투 시작!
                    return;
                }

                // 화면 중앙 타이틀 및 소리
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle("§c" + count, "§7전투 시작 임박", 0, 20, 0);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                }
                count--;
            }
        }.runTaskTimer(plugin, 0, 20L);
    }

    // =========================================================================
    // 4. 전투 진행 단계
    // =========================================================================
    private void startBattle() {
        isInvincible = false;

        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        // 웅장한 소리와 함께 시작 알림
        Bukkit.broadcastMessage("§c§l[전투 시작] §f모든 적을 처치하십시오!");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);

            if (afkPlayers.contains(p.getName()))
                continue;

            // 시작 전 기존 버프 다 제거.
            p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
            // 불이 붙어 있다면 불도 꺼주는 매너!
            p.setFireTicks(0);

            // 배고픔, 풀로 회복.
            p.setFoodLevel(20);      // 허기 게이지를 20(가득)으로 설정합니다.
            p.setSaturation(10.0f);   // 포화도를 높여서 허기가 금방 닳지 않게 서비스!

            // 체력 3줄(60) 설정
            AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null)
                maxHealth.setBaseValue(60.0);
            p.setHealth(60.0);

            // 아이템 지급 (칼-고기-물-유리-포션-갑옷-능력템) + 능력부여.
            giveBattleItems(p);
        }

        // 4-3. 자기장 타이머 시작 (콘피그 final_time 후 줄어듦)
        if (configManager.final_fight) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!isRunning)
                        return;
                    Bukkit.broadcastMessage("§4§l[경고] §c자기장이 줄어들기 시작합니다!");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 1f, 1f);
                    }
                    // 드디어 아레나 매니저의 그 함수를 여기서 부릅니다!
                    arenaManager.startBorderShrink();
                }
            }.runTaskLater(plugin, configManager.final_time * 20L); // 5분(300초) * 20
        }

        // 자기장 대미지 체크 태스크 시작 (ArenaManager 기능 활용 권장)
        arenaManager.startBorderDamage();
    }

    // 아이템 지급 로직 (요청하신 순서 준수)
    private void giveBattleItems(Player p) {
        p.getInventory().clear();

        // 1. 철 칼
        p.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
        // 2. 구운 소고기 64개
        p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 64));
        // 3. 물 양동이
        p.getInventory().addItem(new ItemStack(Material.WATER_BUCKET));
        // 4. 유리 10개
        p.getInventory().addItem(new ItemStack(Material.GLASS, 10));

        // 5. [추가] 1레벨 체력 재생 포션 (1분)
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

        // 6. 철 흉갑 자동으로 입혀주기
        p.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        // // [▲▲▲ 여기까지 변경됨 ▲▲▲]

        // 인벤토리 새로고침 (아이템이 바뀐 걸 유저 화면에 즉시 적용)
        p.updateInventory();

        // 7. 고유 능력 아이템 (AbilityManager가 지급)
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
        if (selectionTask != null)
            selectionTask.cancel();
        arenaManager.stopTasks(); // 자기장 등 정지
        // 자기장=월드보더 초기화. 클리어매니저에서 가져옴
        plugin.getClearManager().worldBorderCear();

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
                    "§72등 : " + Bukkit.getOfflinePlayer(second.getKey()).getName() + " [" + second.getValue() + "점]");
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
        }

        isRunning = false;
        configManager.spawn_point = null;
    }

    // 사망 이벤트 핸들러 (점수 계산)
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!isRunning)
            return;

        Player victim = e.getEntity();
        Player killer = victim.getKiller();

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
                victim.setGameMode(GameMode.SPECTATOR); // <--- [여기 변경됨!!!] 관전 모드 변경

                // 2. 생존자 체크 (스펙테이터가 아닌 사람만 필터링)
                List<Player> survivors = Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.getGameMode() == GameMode.SURVIVAL && !afkPlayers.contains(p.getName()))
                        .collect(Collectors.toList());

                // 최후의 1인 확인
                if (survivors.size() <= 1) {
                    Player winner = survivors.isEmpty() ? killer : survivors.get(0);
                    if (winner != null) {
                        endRound(winner);
                    } else {
                        Bukkit.broadcastMessage("§7생존자가 없어 라운드를 종료합니다.");
                        startRoundAfterDelay();
                    }
                }
            }
        }.runTaskLater(plugin, 1L); // <--- [여기 변경됨!!!] 1틱 뒤 실행 로직으로 감싸기
        // [▲▲▲ 여기까지 변경됨 ▲▲▲]
        //        // 생존자 체크 (스펙테이터가 아닌 사람)
        //        List<Player> survivors = Bukkit.getOnlinePlayers().stream()
        //                .filter(p -> p.getGameMode() == GameMode.SURVIVAL && !afkPlayers.contains(p.getName())
        //                        && !p.equals(victim))
        //                .collect(Collectors.toList());
        //
        //        // 최후의 1인 확인
        //        if (survivors.size() <= 1) {
        //            Player winner = survivors.isEmpty() ? killer : survivors.get(0);
        //            if (winner != null) {
        //                endRound(winner);
        //            } else {
        //                // 모두 죽은 경우 그냥 재시작
        //                Bukkit.broadcastMessage("§7생존자가 없어 라운드를 종료합니다.");
        //                startRoundAfterDelay();
        //            }
        //        }
    }

    private void endRound(Player winner) {
        // 장벽이 줄어드는 작업이 있다면 모두 멈춥니다.
        arenaManager.stopTasks();

        // 라운드 승리 점수 +2
        scores.put(winner.getUniqueId(), scores.getOrDefault(winner.getUniqueId(), 0) + 2);

        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage("§6==========================");
        Bukkit.broadcastMessage("§e마지막까지 살아남은 플레이어 : " + winner.getName() + " +2점");
        spawnFireworks(winner.getLocation());

        // 점수 현황판 출력
        Bukkit.broadcastMessage("§7[현재 점수]");
        scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                    Bukkit.broadcastMessage("§f- " + name + " : §e" + entry.getValue() + "점");
                });
        Bukkit.broadcastMessage("§6==========================");

        // 우승 점수 체크
        if (scores.get(winner.getUniqueId()) >= configManager.win_value) {
            stopGame();
        } else {
            // 5초 뒤 다음 라운드
            Bukkit.broadcastMessage("§75초 뒤 다음 라운드가 시작됩니다.");
            startRoundAfterDelay();
        }
    }

    private void startRoundAfterDelay() {
        new BukkitRunnable() {

            // @@@@@@ 해당 부분은 라운드 시작할 때랑 지금이랑 유저를 비교하여 플레이할 유저가 없어진 경우에만 실행하게 수정 필요함. 아래의 문구를 참고.
            /*
            * afk 완성 시 `startRoundAfterDelay` 함수 수정 할 부분.

라운드 종료 후 이전 라운드에 플레이한 사람이
죽어서 관전 상태가 된 사람을 제외하고
서버에 나감 or 죽은 상태로 리스폰 안함인 경우
10초 의 대기 시간을 준다.

해당 대기 시간 안에 접속하지 않은 경우,
해당 서버 미접속 유저들을 전부 afk 상태로 변경하여 새로운 라운드에 참여하지 않게 구현.
이때 게임 중 afk가 된 유저의 스코어 점수는 초기화 되지 않음.

이후에 재참가 가능하도록. 대신 라운드 종료 후 점수 출력 시 기존과 동일하게 출력하면서 맨 마지막줄에 다음과 같은 문구를 추가함.

(붉은 색으로)afk 유저 : 닉네임, 닉네임, 닉네임

이후 afk로 지정된 해당 유저가 다시 접속하여 /moc afk 유저이름 명령어를 통해 afk 상태에서 해제된 경우엔
현재 진행 중인 라운드를 제외한 다음 라운드부터 다시 능력 배정되며 게임에 참가됨.

라운드 종료 후 사람 수를 체크할 때 서버에 신규 유저가 접속한 경우
해당 신규 유저를 afk 상태로 지정해둔게 아닌 이상 해당 신규 유저도 다음 라운드에 자동으로 추가되어 같이 게임하게 됨.
            * */
            @Override
            public void run() {
                // 접속 끊긴 사람이 있는지 확인
                List<String> missingPlayers = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    // 여기서는 기존 참여자 중 나간 사람을 체크하는 로직이 필요하지만,
                    // 간단하게 현재 접속 인원 중 게임 가능 인원을 체크합니다.
                }

                // 5초 대기 후 startRound() 실행
                Bukkit.broadcastMessage("§e[MOC] 10초 후 다음 라운드가 시작됩니다. (재접속 대기)"); // <--- [여기 변경됨!!!]

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        startRound();
                    }
                }.runTaskLater(plugin, 200L); // 200틱 = 10초 기다림 // <--- [여기 변경됨!!!]
            }
        }.runTaskLater(plugin, 100L); // 기본 5초 대기 후 10초 추가 대기 시작
    }

    // =========================================================================
    // 유틸리티 및 이벤트 리스너
    // =========================================================================

    // 무적 시간 대미지 방지
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
        if (!readyPlayers.contains(p.getUniqueId())) {
            readyPlayers.add(p.getUniqueId());
            p.sendMessage("§a[MOC] 준비 완료!");
            Bukkit.broadcastMessage(" ");
            Bukkit.broadcastMessage(" ");
            Bukkit.broadcastMessage(" ");
            Bukkit.broadcastMessage(" ");
            Bukkit.broadcastMessage(" ");
            Bukkit.broadcastMessage(" ");
        }
    }

    // 능력 리롤.
    public void playerReroll(Player p) {
        if (abilityManager != null)
            abilityManager.rerollAbility(p);
    }

    // AbilityManager에게 현재 플레이어의 능력을 물어봐서 출력
    public void showAbilityDetail(Player p) {
        // (현재 구조상 AbilityManager가 담당하는게 맞음)
        if (abilityManager != null) {
            abilityManager.showAbilityDetail(p);
        }
    }

    // 승리 축하 폭죽 로직 (생략 가능, 단순화)
    private void spawnFireworks(Location loc) {
        org.bukkit.entity.Firework fw = loc.getWorld().spawn(loc, org.bukkit.entity.Firework.class);
        org.bukkit.inventory.meta.FireworkMeta fm = fw.getFireworkMeta();
        fm.addEffect(FireworkEffect.builder().withColor(Color.RED).withFade(Color.ORANGE).with(FireworkEffect.Type.BALL)
                .build());
        fm.setPower(1);
        fw.setFireworkMeta(fm);
    }

    // 1. [일반 블록 설치 검사] 유리가 아니면 설치를 막습니다.
    @EventHandler
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        // 게임 중이 아니라면 검사하지 않고 통과시킵니다.
        if (!isRunning) return;

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
        if (!isRunning) return;

        // 플레이어가 들고 있는 양동이의 종류를 확인합니다.
        org.bukkit.Material bucketType = e.getBucket();

        // 만약 사용하려는 양동이가 '물 양동이(WATER_BUCKET)'가 아니라면? (용암 등 금지)
        if (bucketType != org.bukkit.Material.WATER_BUCKET) {
            // 물 붓기를 취소시킵니다.
            e.setCancelled(true);
            e.getPlayer().sendMessage("§c[경고] 게임 중에는 물만 부을 수 있습니다!");
        }
    }


}