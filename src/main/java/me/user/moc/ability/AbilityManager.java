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
        addAbility(new Rimuru(plugin)); // 리무르 등록
        addAbility(new Kaneki(plugin)); // 카네키 켄 등록
        addAbility(new PaulPhoenix(plugin)); // 폴 피닉스 등록
        addAbility(new Jjanggu(plugin)); // 짱구 등록
        addAbility(new Naofumi(plugin)); // 이와타니 나오후미 등록
        addAbility(new Yugi(plugin)); // 유희 등록
        addAbility(new Gaara(plugin)); // 036 가아라
        addAbility(new MisakaMikoto(plugin)); // 034 미사카 미코토
        addAbility(new NanayaShiki(plugin)); // 035 나나야 시키
        addAbility(new AizenSosuke(plugin)); // 037 아이젠 소스케
        addAbility(new KurosakiIchigo(plugin)); // 038 쿠로사키 이치고
        addAbility(new KiraYoshikage(plugin)); // 039 키라 요시카게
        addAbility(new KimDokja(plugin)); // 040 김독자
        addAbility(new ErenYeager(plugin)); // 041 에렌 예거
        addAbility(new TogaHimiko(plugin)); // 047 토가 히미코
    }

    private void addAbility(Ability ability) {
        // [핵심] 저장할 때 '코드'를 열쇠로 사용!
        abilities.put(ability.getCode(), ability);
    }

    // * GameManager가 "능력 뭐뭐 있어?" 하고 물어볼 때 사용합니다.
    public Set<String> getAbilityCodes() {
        // 맵에 들어있는 모든 키(능력 코드들)를 복사해서 줍니다.
        return abilities.keySet();
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
        for (Ability ability : abilities.values()) {
            ability.reset();
        }

        playerAbilities.clear();
        rerollCounts.clear();
    }

    // GameManager에서 플레이어에게 능력을 강제로 설정할 때 사용합니다.
    // ... resetAbilities, setPlayerAbility, setRerollCount 함수는 그대로 둬도 됩니다 ...
    // ... 단, setPlayerAbility의 두 번째 인자는 이제 "우에키"가 아니라 "001"이 들어와야 합니다 ...
    public void setPlayerAbility(UUID uuid, String abilityCode) {
        playerAbilities.put(uuid, abilityCode);
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
                // 남은 리롤 횟수를 가져와서 보여줍니다.
                int left = rerollCounts.getOrDefault(p.getUniqueId(), 0);
                p.sendMessage("§f리롤(" + left + "회) : §c/moc re");
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
        List<String> pool = new ArrayList<>(abilities.keySet());
        pool.remove(playerAbilities.get(p.getUniqueId()));
        // @@@ㅁㅁㅁ@@@ 여기서 능력 뽑을 때 다른 사람과 그리고 이전의 자신의 능력과 중복되지 않게 해야함.
        String currentAbility = playerAbilities.get(p.getUniqueId());
        pool.remove(currentAbility); // 자신의 이전 능력 제거
        pool.removeAll(playerAbilities.values()); // 다른 플레이어가 가진 능력 전부 제거

        // 만약 모든 능력이 이미 배정되어 뽑을 게 없다면, 최소한 자신의 직전 능력만이라도 뺍니다.
        if (pool.isEmpty()) {
            pool = new ArrayList<>(abilities.keySet());
            pool.remove(currentAbility);
        }

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
            // 예: 001 | 우에키
            sender.sendMessage("§b" + ability.getCode() + " §f| §f" + ability.getName());
        }

        sender.sendMessage(" "); // 마무리 빈 줄

        // [▲▲▲ 여기까지 변경됨 ▲▲▲]
    }
    // [▲▲▲ 여기까지 변경됨 ▲▲▲]
}