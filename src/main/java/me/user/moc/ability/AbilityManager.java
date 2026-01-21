package me.user.moc.ability;

import me.user.moc.MocPlugin;
import me.user.moc.ability.impl.*;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * AbilityManager: 게임 내 모든 '능력'을 관리하는 클래스입니다.
 * 플레이어에게 능력을 배정하고, 리롤을 처리하며, 능력 정보를 보여주는 역할을 합니다.
 */
public class AbilityManager {

    private final MocPlugin plugin;
    // 능력 이름(String)을 통해 실제 능력 객체(Ability)를 찾기 위한 지도(Map)
    private final Map<String, Ability> abilities = new HashMap<>();
    // 어떤 플레이어(UUID)가 어떤 능력 코드를 가지고 있는지 저장하는 지도
    private final Map<UUID, String> playerAbilities = new HashMap<>();
    // 플레이어별로 남은 리롤 횟수를 저장하는 지도
    private final Map<UUID, Integer> rerollCounts = new HashMap<>();

    public AbilityManager(MocPlugin plugin) {
        this.plugin = plugin;
        registerAbilities(); // 클래스가 생성될 때 능력을 자동으로 등록합니다.
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
    }

    private void addAbility(Ability ability) {
        // [핵심] 저장할 때 '코드'를 열쇠로 사용!
        abilities.put(ability.getCode(), ability);
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
    public void resetAbilities() {
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
     * [수정 4] 능력 정보를 보여줄 때도 '코드'를 기준으로 검사합니다.
     * 인자로 들어오는 abilityCode는 이제 "001", "002" 같은 녀석들입니다.
     */
    /*** 플레이어에게 배정된 능력의 요약 정보를 채팅창에 예쁘게 보여줍니다.*/
    public void showAbilityInfo(Player p, String abilityCode) {
        p.sendMessage("§f ");
        p.sendMessage("§e=== 당신의 능력은 ===");

        // 능력 이름에 따라 서로 다른 설명을 출력합니다.
        // 유틸, 전투, 복합 3개로만 구분할 예정
        switch (abilityCode) {
            case "001" -> {
                p.sendMessage("§a유틸 ● 우에키(우에키의 법칙/배틀짱)");
                p.sendMessage("§f묘목을 우클릭 시 주변에 떨어진 쓰레기들을 전부 나무로 변경한다.");
            }
            case "002" -> {
                p.sendMessage("§c전투 ● 올라프(리그 오브 레전드)");
                p.sendMessage("§f도끼(눈덩이)를 던져 적에게 강력한 고정 피해를 입힌다.");
            }
            case "003" -> {
                p.sendMessage("§유틸 ● 미다스(그리스 신화)");
                p.sendMessage("§f금괴로 블록을 우클릭하면 황금 블록으로 변환시킨다.");
            }
            case "004" -> {
                p.sendMessage("§b복합 ● 매그너스(이터널 리턴)");
                p.sendMessage("§f오토바이를 소환하여 전방으로 돌진 후 자폭한다.");
            }
            case "005" -> {
                p.sendMessage("§c전투 ● 사이타마(원펀맨)");
                p.sendMessage("§f사이타마 운동법을 완료하면 매우 강력해집니다.");
            }
            case "011" -> {
                p.sendMessage("§a전투 ● 람머스(롤)");
                p.sendMessage("§f거북이 모자를 착용하여 몸 말아 웅크리기를 시전합니다.");
            }

            default -> p.sendMessage("§7등록되지 않은 능력입니다.");
        }

        p.sendMessage("§f ");
        p.sendMessage("§f상세 설명 : §b/moc check");
        p.sendMessage("§f능력 수락 : §a/moc yes");

        // 남은 리롤 횟수를 가져와서 보여줍니다.
        int left = rerollCounts.getOrDefault(p.getUniqueId(), 0);
        p.sendMessage("§f리롤(" + left + "회) : §c/moc re");
        p.sendMessage("§e==================");
    }

    /**
     * 리롤 로직: 현재 능력을 버리고 새로운 능력을 뽑습니다. (고기 소모 없음)
     */
    public void rerollAbility(Player p) {
        int left = rerollCounts.getOrDefault(p.getUniqueId(), 0);

        // 1. 리롤 횟수가 0이면 거절합니다.
        if (left <= 0) {
            p.sendMessage("§c[MOC] 리롤 횟수를 모두 사용했습니다.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);

            // 강제 준비 완료
            plugin.getGameManager().playerReady(p);
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
        if(left - 1 <=0){
            // 강제 준비 완료
            plugin.getGameManager().playerReady(p);
        }
        // 4. 효과음과 함께 새로운 정보를 보여줍니다.
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        showAbilityInfo(p, newAbility);
    }

    /**
     * 전투 시작 시 각 능력에 맞는 고유 아이템을 지급합니다.
     */
    public void giveAbilityItems(Player p) {
        String name = playerAbilities.get(p.getUniqueId());
        if (name != null && abilities.containsKey(name)) {
            // Ability 클래스(Ueki.java 등)에 정의된 giveItem 메서드를 실행합니다.
            abilities.get(name).giveItem(p);
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
        if (code.equals("001")) {
            p.sendMessage("§a유틸 ● 우에키(우에키의 법칙/배틀짱)");
            p.sendMessage("§f묘목을 우클릭 하면 주변 20블럭 이내 모든 생명체와");
            p.sendMessage("§f바닥에 떨어진 아이템들이 나무로 변합니다.");
            p.sendMessage("§a추가 아이템: 묘목 64개.");
        } else if (code.equals("005")) {
            p.sendMessage("§c전투 ● 사이타마(원펀맨)");
            p.sendMessage("§f사이타마 운동법을 완료하면 매우 강력해집니다.");
            p.sendMessage("§f[조건] 웅크리기 500회 / 점프 500회 / 이동 500블럭");
            p.sendMessage("§f[보상] 힘V, 이속V, 저항V, 성급함V (영구)");

        } else if (code.equals("011")) {
            p.sendMessage("§a전투 ● 람머스(롤)");
            p.sendMessage("§f거북이 모자를 착용하여 몸 말아 웅크리기를 시전합니다.");
            p.sendMessage("§f - 모자 착용 시 구속2");
            p.sendMessage("§f - 모자 미착용 시 풀림");
            p.sendMessage("§a추가 아이템: 거북이 모자 (가시 8)");
        }  else {
            p.sendMessage("§7[" + abilityName + "]의 상세 가이드는 존재하지 않습니다.");
        }
        p.sendMessage("§e=================");
    }
}