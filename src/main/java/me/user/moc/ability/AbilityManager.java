package me.user.moc.ability;

import me.user.moc.MocPlugin;
import me.user.moc.ability.impl.*;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AbilityManager: 게임 내 모든 '능력'을 관리하는 클래스입니다.
 * 플레이어에게 능력을 배정하고, 리롤을 처리하며, 능력 정보를 보여주는 역할을 합니다.
 */
public class AbilityManager {

    private final MocPlugin plugin;
    // 능력 이름(String)을 통해 실제 능력 객체(Ability)를 찾기 위한 지도(Map)
    private final Map<String, Ability> abilities = new ConcurrentHashMap<>();
    // 어떤 플레이어(UUID)가 어떤 능력 코드를 가지고 있는지 저장하는 지도
    private final Map<UUID, String> playerAbilities = new ConcurrentHashMap<>();
    // 플레이어별로 남은 리롤 횟수를 저장하는 지도
    private final Map<UUID, Integer> rerollCounts = new ConcurrentHashMap<>();

    // [추가] 이번 게임 세션 동안 각 능력(코드)이 몇 번 등장했는지 카운트하는 지도 (통계용)
    private final Map<String, Integer> gameUsageCounts = new ConcurrentHashMap<>();

    // [추가] 고죠 사토루 등 능력 봉인 스킬에 걸린 플레이어 목록 (전역 관리)
    public static final Set<UUID> silencedPlayers = ConcurrentHashMap.newKeySet();
    // [추가] 점프가 봉인된 플레이어 목록 (UUID -> 만료 밀리초)
    public static final java.util.Map<UUID, Long> jumpSilenceExpirations = new ConcurrentHashMap<>();

    private static AbilityManager instance;

    public AbilityManager(MocPlugin plugin) {
        this.plugin = plugin;
        instance = this; // 인스턴스 저장
        registerAbilities(); // 클래스가 생성될 때 능력을 자동으로 등록합니다.
    }

    // [추가] MocCommand에서 호출하는 getInstance 함수
    public static AbilityManager getInstance(MocPlugin plugin) {
        if (instance == null) {
            instance = new AbilityManager(plugin);
        }
        return instance;
    }

    public static AbilityManager getInstance() {
        return instance;
    }

    /**
     * 사용할 능력들을 시스템에 등록하는 메서드입니다.
     */
    private void registerAbilities() {
        // [수정 3] 여기서 능력을 등록할 때 getName()이 아니라 getCode()로 등록합니다.
        addAbility(new Ueki(plugin)); // 우에키 등록
        addAbility(new Olaf(plugin)); // 올라프 등록
        addAbility(new Midas(plugin)); // 미다스 등록
        addAbility(new Magnus(plugin)); // 매그너스 등록
        addAbility(new Rammus(plugin)); // 람머스 등록
        addAbility(new Saitama(plugin)); // 사이타마 등록
        addAbility(new Ranga(plugin)); // 란가 등록
        addAbility(new AsuiTsuyu(plugin)); // 아스이 츠유 등록
        addAbility(new Byakuya(plugin)); // 뱌쿠야 등록
        addAbility(new GoldSilverAxe(plugin)); // 금도끼 은도끼 등록
        addAbility(new Alex(plugin)); // 알렉스 등록
        addAbility(new Zenitsu(plugin)); // 아가츠마 젠이츠 등록
        addAbility(new Meliodas(plugin)); // 멜리오다스 등록
        addAbility(new Ulquiorra(plugin)); // 우르키오라 쉬퍼 등록
        addAbility(new TrafalgarLaw(plugin)); // 트라팔가 로우 등록
        addAbility(new CuChulainn(plugin)); // 쿠 훌린 등록
        addAbility(new Naruto(plugin)); // 나루토 등록
        addAbility(new Inuyasha(plugin)); // 이누야샤 등록
        addAbility(new KingHassan(plugin)); // 산의 노인(FATE) 등록
        addAbility(new EmiyaShirou(plugin)); // 에미야 시로 등록
        addAbility(new Windbreaker(plugin)); // 윈드브레이커(메이플) 등록
        addAbility(new Pantheon(plugin)); // 빵테온 등록
        addAbility(new TheKingOfGockgangE(plugin)); // 왕 쩌는 곡갱이 등록
        addAbility(new Rimuru(plugin)); // 리무루 등록
        addAbility(new Kaneki(plugin)); // 카네키 켄 등록
        addAbility(new PaulPhoenix(plugin)); // 폴 피닉스 등록
        addAbility(new Jjanggu(plugin)); // 짱구 등록
        addAbility(new Naofumi(plugin)); // 이와타니 나오후미 등록
        addAbility(new Yugi(plugin)); // 유희 등록
        addAbility(new Spiderman(plugin)); // 030 스파이더맨
        addAbility(new Gaara(plugin)); // 036 가아라
        addAbility(new MisakaMikoto(plugin)); // 034 미사카 미코토
        addAbility(new NanayaShiki(plugin)); // 035 나나야 시키
        addAbility(new AizenSosuke(plugin)); // 037 아이젠 소스케
        addAbility(new KurosakiIchigo(plugin)); // 038 쿠로사키 이치고
        addAbility(new KiraYoshikage(plugin)); // 039 키라 요시카게
        addAbility(new KimDokja(plugin)); // 040 김독자
        addAbility(new ErenYeager(plugin)); // 041 에렌 예거
        addAbility(new FoxDevil(plugin)); // 042 여우의 악마
        addAbility(new Gintoki(plugin)); // 043 긴토키
        addAbility(new TogaHimiko(plugin)); // 047 토가 히미코
        addAbility(new Yesung(plugin)); // H02 예성이
        addAbility(new TungTungTungSahur(plugin)); // 016 퉁퉁퉁 사후르
        addAbility(new DIO(plugin)); // 045 DIO
        addAbility(new Jigsaw(plugin)); // 049 직쏘
        addAbility(new Singed(plugin)); // 033 신지드
        addAbility(new PolarBearAbility(plugin)); // 028 북극곰
        addAbility(new BartholomewKuma(plugin)); // 050 바솔로뮤 쿠마
        addAbility(new Mothership(plugin)); // 032 모선
        addAbility(new Deidara(plugin)); // 044 데이다라
        addAbility(new Bajje(plugin)); // H01 베째
        addAbility(new Jumptaengi(plugin)); // H03 점탱이
        addAbility(new MuhammadAvdol(plugin)); // 046 무함마드 압둘
        addAbility(new Ddangkong(plugin)); // H04 땅콩
        addAbility(new Jihyun(plugin)); // H05 지현
        addAbility(new SpongeBob(plugin)); // 051 스펀지밥
        addAbility(new GojoSatoru(plugin)); // 059 고죠 사토루
        addAbility(new AmanoHina(plugin)); // 056 아마노 히나
        addAbility(new KumagawaMisogi(plugin)); // 053 쿠마가와 미소기
        addAbility(new Topblade(plugin)); // 055 탑블레이드

        // [추가] 토가 히미코 전용 격리 능력 등록 (랜덤 뽑기 제외 대상)
        addAbility(new TH_Rimuru(plugin)); // TH018
        addAbility(new TH_PolarBearAbility(plugin)); // TH028
        addAbility(new TH_TungTungTungSahur(plugin)); // TH016
    }

    private void addAbility(Ability ability) {
        // [핵심] 저장할 때 '코드'를 열쇠로 사용!
        abilities.put(ability.getCode(), ability);
    }

    /**
     * [추가] 게임에서 실제로 추첨 가능한 능력 코드 목록을 가져옵니다.
     * 콘피그의 hidden 설정이 꺼져(false)있다면 'H'로 시작하는 코드는 제외합니다.
     */
    public List<String> getPlayableAbilityCodes() {
        // 1. 모든 코드를 가져옵니다.
        List<String> allCodes = new ArrayList<>(abilities.keySet());

        // 2. 히든 모드 설정 확인
        // ConfigManager 인스턴스를 가져오는데, 안전하게 가져옵니다.
        me.user.moc.config.ConfigManager config = (plugin.getConfigManager() != null) ? plugin.getConfigManager()
                : me.user.moc.config.ConfigManager.getInstance();

        boolean isHiddenEnabled = config.hidden;

        // 3. 필터링 (히든 모드가 꺼져있다면 'H'로 시작하는 것 제거)
        if (!isHiddenEnabled) {
            // removeIf를 쓰면 조건에 맞는 녀석들을 리스트에서 싹 지워줍니다.
            allCodes.removeIf(code -> code.toUpperCase().startsWith("H"));
        }

        // [추가] 토가 히미코 전용 능력(TH_)은 절대 랜덤으로 나오면 안 됨!
        allCodes.removeIf(code -> code.toUpperCase().startsWith("TH"));

        return allCodes;
    }

    // * GameManager가 "능력 뭐뭐 있어?" 하고 물어볼 때 사용합니다.
    public Set<String> getAbilityCodes() {
        // [수정] 이제 그냥 다 보여주는 게 아니라, 히든 필터링을 거친 목록을 줍니다.
        // 하지만 반환 타입이 Set이라서 변환해서 줍니다.
        return new HashSet<>(getPlayableAbilityCodes());
    }

    // [추가] 김독자 능력 등에서 사용하기 위한 Getter
    public Map<UUID, String> getPlayerAbilities() {
        return playerAbilities;
    }

    public Ability getAbility(String code) {
        return abilities.get(code);
    }

    /**
     * [빌드 에러 해결 포인트]
     * Magnus나 Midas 같은 개별 능력 파일에서 "이 플레이어가 내 능력자인가?"를 확인할 때 사용합니다.
     */
    public boolean hasAbility(Player player, String abilityCode) {
        String assignedCode = playerAbilities.get(player.getUniqueId());
        // 이제 이름 비교가 아니라 코드("001")끼리 비교합니다.
        return assignedCode != null && assignedCode.equals(abilityCode);
    }

    /**
     * 새로운 라운드가 시작될 때 기존 데이터(배정된 능력, 리롤 횟수)를 싹 비웁니다.
     */
    /**
     * 새로운 라운드가 시작될 때 기존 데이터(배정된 능력, 리롤 횟수)를 싹 비웁니다.
     * 라운드 종료 시에도 호출되어야 합니다.
     */
    public void resetAbilities() {
        // [수정] 1. 개별 플레이어에 대한 cleanup (기존 유지)
        for (Map.Entry<UUID, String> entry : playerAbilities.entrySet()) {
            UUID uuid = entry.getKey();
            String code = entry.getValue();
            Ability ability = abilities.get(code);
            if (ability != null) {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p != null) {
                    ability.cleanup(p);
                }
            }
        }

        // [추가] 2. 모든 능력의 전역 상태(쿨타임, 관리 중인 늑대 등) 초기화
        // 특히 토가 히미코의 경우 reset()에서 변신 해제를 수행하므로 필수입니다.
        for (Ability ability : abilities.values()) {
            ability.reset();
        }

        // [강화] 모든 데이터 초기화
        playerAbilities.clear();
        rerollCounts.clear();
        silencedPlayers.clear(); // [추가] 봉인 상태도 초기화
        jumpSilenceExpirations.clear(); // [추가] 점프 봉인 상태도 초기화
        gameUsageCounts.clear(); // [추가] 통계 초기화 (필요 시 유지할 수도 있지만 일단 초기화)
    }

    /**
     * [추가] 게임 통계 초기화 (GameManager.startGame에서 호출)
     */
    public void resetUsageCounts() {
        gameUsageCounts.clear();
    }

    /**
     * [추가] 특정 능력의 게임 내 등장 횟수 반환
     */
    public int getUsageCount(String code) {
        return gameUsageCounts.getOrDefault(code, 0);
    }

    // GameManager에서 플레이어에게 능력을 강제로 설정할 때 사용합니다.
    // ... resetAbilities, setPlayerAbility, setRerollCount 함수는 그대로 둬도 됩니다 ...
    // ... 단, setPlayerAbility의 두 번째 인자는 이제 "우에키"가 아니라 "001"이 들어와야 합니다 ...
    public void setPlayerAbility(UUID uuid, String abilityCode) {
        Player p = plugin.getServer().getPlayer(uuid);

        // [Fix] 기존 능력이 있다면 정리(cleanup)
        // 특히 토가 히미코 변신 상태라면 강제로 원래대로 되돌린 후 변경해야 꼬이지 않음
        Ability togaAb = abilities.get("047");
        if (togaAb instanceof TogaHimiko toga && toga.isTransformed(uuid)) {
            if (p != null) {
                toga.cleanup(p); // 원래 상태(047)로 복구 + 변신 능력 cleanup
            }
        }

        String oldCode = playerAbilities.get(uuid);
        if (oldCode != null) {
            Ability oldAbility = abilities.get(oldCode);
            if (oldAbility != null && p != null) {
                oldAbility.cleanup(p);
            }
        }

        playerAbilities.put(uuid, abilityCode);

        // [추가] 능력 등장 횟수 카운트 (통계용)
        if (abilityCode != null) {
            gameUsageCounts.put(abilityCode, gameUsageCounts.getOrDefault(abilityCode, 0) + 1);
        }
    }

    // GameManager에서 설정값(Config)에 따라 리롤 횟수를 부여할 때 사용합니다.
    public void setRerollCount(UUID uuid, int count) {
        rerollCounts.put(uuid, count);
    }

    /**
     * [토가 히미코 전용] // <- 게임 중 능력 변경할 로직.
     * 도망가거나 변신할 때 기존 능력을 안전하게 정리(cleanup)하고 새로운 능력 코드를 부여합니다.
     * 
     * @param p              대상 플레이어
     * @param newAbilityCode 변경할 새로운 능력 코드
     */
    public void changeAbilityTemporary(Player p, String newAbilityCode) {
        String oldCode = playerAbilities.get(p.getUniqueId());

        // 1. 기존 능력이 있다면 정리(소환수 제거, 태스크 취소 등)
        if (oldCode != null) {
            Ability oldAbility = abilities.get(oldCode);
            if (oldAbility != null) {
                oldAbility.cleanup(p);
            }
        }

        // 2. 능력 코드 교체
        playerAbilities.put(p.getUniqueId(), newAbilityCode);

        // 3. 안내 (선택사항, 생략 가능)
        // p.sendMessage("§e[Ability] 능력이 변경되었습니다.");
    }

    // [추가] 특정 플레이어의 능력 관련 요소를 정리합니다.
    public void cleanup(Player p) {
        String code = playerAbilities.get(p.getUniqueId());
        if (code != null) {
            Ability ability = abilities.get(code);
            if (ability != null) {
                ability.cleanup(p);
            }
            // 주의: playerAbilities에서 제거하지 않음 (게임 끝나고 통계 볼 수도 있으니)
            // 다만 죽은 상태라 능력 발동은 안 됨.
        }
    }

    /**
     * [수정 4] 능력 정보를 보여줄 때도 '코드'를 기준으로 검사합니다.
     * 인자로 들어오는 abilityCode는 이제 "001", "002" 같은 녀석들입니다.
     */
    /*** 플레이어에게 배정된 능력의 요약 정보를 채팅창에 예쁘게 보여줍니다. */
    public void showAbilityInfo(Player p, String abilityCode, int massgeType) {
        p.sendMessage("§f ");
        p.sendMessage("§e=== 당신의 능력은 ===");

        // [수정] 하드코딩 제거 -> 각 능력 파일의 getDescription() 활용
        Ability ability = abilities.get(abilityCode);
        if (ability != null) {
            for (String line : ability.getDescription()) {
                p.sendMessage(line);
            }
        } else {
            p.sendMessage("§7등록되지 않은 능력입니다.");
        }

        p.sendMessage("§f ");
        p.sendMessage("§f ");
        switch (massgeType) {
            case 1 -> {
                // 선택 여부 확인 메세지 안 보냄.
            }
            case 2 -> {
                // 체크 가능할 수 있음만 알림.
                p.sendMessage("§f상세 설명 : §b/moc check");
            }
            default -> {
                p.sendMessage("§f상세 설명 : §b/moc check");
                p.sendMessage("§f능력 수락 : §a/moc yes");

                // [요청사항 반영] 리롤 포인트가 0보다 클 때만 리롤 안내 메시지를 보여줍니다.
                int left = rerollCounts.getOrDefault(p.getUniqueId(), 0);
                if (left > 0) {
                    p.sendMessage("§f리롤(" + left + "회) : §c/moc re");
                }
            }
        }

        p.sendMessage("§e==================");
    }

    /**
     * 리롤 로직: 현재 능력을 버리고 새로운 능력을 뽑습니다. (고기 소모 없음)
     */
    public void rerollAbility(Player p) {
        int left = rerollCounts.getOrDefault(p.getUniqueId(), 0);

        // 1. 리롤 횟수가 0이면 거절합니다.
        if (left <= 0) {
            p.sendMessage("§c[MOC] 리롤 횟수를 모두 사용했습니다. ");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // 2. 현재 능력을 제외한 나머지 능력 중에서 하나를 랜덤으로 뽑습니다.
        // [수정] getRandomAbility 대신 getPlayableAbilityCodes()를 사용하여 필터링된 목록을 가져옵니다.
        List<String> pool = new ArrayList<>(getPlayableAbilityCodes());

        // @@@ㅁㅁㅁ@@@ 여기서 능력 뽑을 때 다른 사람과 그리고 이전의 자신의 능력과 중복되지 않게 해야함.
        String currentAbility = playerAbilities.get(p.getUniqueId());
        pool.remove(currentAbility); // 자신의 이전 능력 제거
        pool.removeAll(playerAbilities.values()); // 다른 플레이어가 가진 능력 전부 제거

        // 만약 모든 능력이 이미 배정되어 뽑을 게 없다면, 최소한 자신의 직전 능력만이라도 뺍니다.
        if (pool.isEmpty()) {
            pool = new ArrayList<>(getPlayableAbilityCodes());
            pool.remove(currentAbility);
        }

        if (pool.isEmpty())
            return;

        Collections.shuffle(pool); // 목록을 섞습니다.
        if (pool.isEmpty())
            return;

        Collections.shuffle(pool); // 목록을 섞습니다.
        String newAbility = pool.get(0); // 맨 앞의 것을 선택합니다.

        // 3. 새로운 능력을 저장하고 리롤 횟수를 1 차감합니다.
        playerAbilities.put(p.getUniqueId(), newAbility);
        rerollCounts.put(p.getUniqueId(), left - 1);

        // 리롤 횟수 사용 시 0 이하일 때
        if (left - 1 <= 0) {
            // 강제 준비 완료
            p.sendMessage("§f ");
            p.sendMessage("§f ");
            p.sendMessage("§f ");
            p.sendMessage("§f ");
            p.sendMessage("§f ");
            p.sendMessage("§f ");
            p.sendMessage("§c[MOC] 리롤 횟수를 모두 사용했습니다. ");
            p.sendMessage("§c[MOC] 5초 후 자동으로 준비됩니다. ");
            // [자바 버전의 setTimeout: BukkitRunnable]
            new BukkitRunnable() {
                @Override
                public void run() {
                    // 3초 후에 실행될 내용을 여기에 적습니다.
                    plugin.getGameManager().playerReady(p);
                }
            }.runTaskLater(plugin, 100L);
        }

        // 4. 효과음과 함께 새로운 정보를 보여줍니다.
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        showAbilityInfo(p, newAbility, 2);
    }

    /**
     * 전투 시작 시 각 능력에 맞는 고유 아이템을 지급합니다.
     */
    public void giveAbilityItems(Player p) {
        String abilityCode = playerAbilities.get(p.getUniqueId()); // 001 등을 가져옴
        if (abilityCode != null && abilities.containsKey(abilityCode)) {
            // Ability 클래스(Ueki.java 등)에 정의된 giveItem 메서드를 실행합니다.
            abilities.get(abilityCode).giveItem(p);
            // [고도화 1] 아이템 지급 시 상세 설명도 한 번 보여줍시다.
            abilities.get(abilityCode).detailCheck(p);
        }
    }

    /**
     * /moc check 입력 시 보여줄 상세 가이드입니다.
     */
    public void showAbilityDetail(Player p) {
        String code = playerAbilities.get(p.getUniqueId()); // 코드를 가져옴 ("001")
        String abilityName = playerAbilities.get(p.getUniqueId());
        if (code == null) {
            p.sendMessage("§c배정된 능력이 없습니다.");
            return;
        }

        p.sendMessage(" ");
        p.sendMessage(" ");
        p.sendMessage(" ");
        p.sendMessage(" ");
        p.sendMessage(" ");
        p.sendMessage(" ");
        p.sendMessage(" ");
        p.sendMessage(" ");
        p.sendMessage("§e=== 상세 설명 ===");

        Ability ability = abilities.get(abilityName);

        if (ability != null) {
            // 3. 하드코딩 대신, 능력자 파일 안에 있는 detailCheck()를 실행합니다!
            ability.detailCheck(p);
        } else {
            p.sendMessage("§c" + abilityName + "의 상세 능력을 찾을 수 없습니다.");
        }

        p.sendMessage("§e=================");
    }

    // [▼▼▼ 여기서부터 변경됨 ▼▼▼]

    /**
     * 모든 능력자 목록을 번호 순서대로 채팅창에 출력합니다.
     *
     * @param sender 메시지를 받을 대상 (명령어 사용자)
     */
    /**
     * 모든 능력자 목록을 번호 순서대로 채팅창에 출력합니다.
     * 
     * @param sender 메시지를 받을 대상 (명령어 사용자)
     */
    public void showAbilityList(org.bukkit.command.CommandSender sender) {
        // [▼▼▼ 여기서부터 변경됨 ▼▼▼]

        // 1. [지도에서 알맹이 빼기]
        // abilities는 '지도'라서 바로 반복문을 돌릴 수 없습니다.
        // abilities.values()를 사용하여 지도 안의 '능력 객체'들만 쏙 뽑아 새로운 리스트를 만듭니다.
        List<Ability> sortedList = new ArrayList<>(this.abilities.values());

        // 2. [정렬] 리스트에 담긴 능력들을 번호(getCode) 순서대로 정렬합니다.
        sortedList.sort((a, b) -> a.getCode().compareTo(b.getCode()));

        // 3. 채팅창 디자인 (여백을 주어 깔끔하게 보이게 함)
        for (int i = 0; i < 5; i++)
            sender.sendMessage(" "); // 빈 줄 출력
        sender.sendMessage("§a§l[ 능력자 목록 ]");
        sender.sendMessage("§7코드 §f| §e능력자명");
        sender.sendMessage("§7--------------------");

        // 4. [반복문 실행]
        // 이제 'abilities'가 아니라, 위에서 정렬한 'sortedList'를 사용해야 에러가 안 납니다!
        for (Ability ability : sortedList) {
            // [Fix] 토가 히미코 전용 능력(TH로 시작)은 리스트에서 숨김
            if (ability.getCode().startsWith("TH")) {
                continue;
            }
            // 예: 001 | 우에키
            sender.sendMessage("§b" + ability.getCode() + " §f| §f" + ability.getName());
        }

        sender.sendMessage(" "); // 마무리 빈 줄

        // [▲▲▲ 여기까지 변경됨 ▲▲▲]
    }
    // [▲▲▲ 여기까지 변경됨 ▲▲▲]
}