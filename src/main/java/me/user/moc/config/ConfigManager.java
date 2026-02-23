package me.user.moc.config; // 이 파일이 있는 위치

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import me.user.moc.MocPlugin;
import java.lang.reflect.Field;

/**
 * [ 설정 장부 ]
 * 게임의 규칙(시간, 점수, 장소 등)을 적어두고 관리하는 곳입니다.
 * 이제 config.yml 파일과 내용을 공유하여 서버가 꺼져도 기억할 수 있습니다.
 */
public class ConfigManager {

    // 이 장부를 딱 하나만 만들어서 공유하기 위한 '보관함'입니다.
    private static ConfigManager instance;

    /**
     * 다른 파일에서 "장부 좀 보여줘!"라고 할 때 쓰는 함수입니다.
     * 장부가 없으면 새로 만들고, 있으면 원래 있던 걸 보여줍니다.
     */
    public static ConfigManager getInstance() {
        if (instance == null)
            instance = new ConfigManager();
        return instance;
    }

    // --- [ 게임의 규칙들 (기본값) ] ---

    public boolean spawn_tf = true; // 게임 시작할 때 스폰 장소로 이동할까요?
    public Location spawn_point = null; // 사람들이 모일 장소 (좌표)
    public int peace_time = 3; // 싸우기 전 평화 시간 (초)
    // public boolean team_attack = false; // 같은 팀끼리 때릴 수 있나요?
    // public boolean teammod = false; // 팀전인가요? (아니오=개인전)
    public int re_point = 1; // 능력 다시 뽑기 기회 (1번)
    public int start_time = 30; // 능력 추첨 제한 시간 (30초)
    public boolean final_fight = true; // 시간이 지나면 자기장이 줄어드나요?
    public int final_time = 120; // 게임 시작 몇 초 뒤에 자기장이 줄어드나요? (120초 = 2분)
    // public boolean map_end = true; // 전장 외곽 벽을 활성화할까요?
    public int map_size = 75; // 전장(경기장)의 크기
    public int win_value = 40; // 몇 점을 먼저 따면 최종 우승인가요?
    public boolean hidden = false; // 숨겨진 캐릭터가 등장하나요?
    public boolean battle_map = true; // [추가] 전장 바닥(배드락)을 생성할까요? (false=야생맵 사용)
    public boolean random_map = true; // [추가] 전장 생성 시 자연 환경(흙, 물, 용암 등)을 생성할까요?
    public boolean test = false; // [추가] 테스트 모드 여부 (true=혼자 남아도 라운드 안 끝남)
    public boolean disable_attack_cooldown = true; // [추가] 1.8 버전처럼 공격 딜레이를 없앨까요?

    /**
     * [설정 불러오기]
     * 플러그인이 켜질 때, 파일(config.yml)에 적힌 내용을 읽어서 변수에 넣습니다.
     * 
     * @param config 플러그인의 설정 파일 객체
     */
    public void load(FileConfiguration config) {
        // config.get("이름", 기본값) -> 파일에 없으면 기본값을 씁니다.
        spawn_tf = config.getBoolean("spawn_tf", true);
        peace_time = config.getInt("peace_time", 3);
        // team_attack = config.getBoolean("team_attack", false);
        // teammod = config.getBoolean("teammod", false);
        re_point = config.getInt("re_point", 1);
        start_time = config.getInt("start_time", 30);
        final_fight = config.getBoolean("final_fight", true);
        final_time = config.getInt("final_time", 120);
        // map_end = config.getBoolean("map_end", true);
        map_size = config.getInt("map_size", 75);
        win_value = config.getInt("win_value", 40);
        hidden = config.getBoolean("hidden", false);
        battle_map = config.getBoolean("battle_map", true);
        random_map = config.getBoolean("random_map", true); // [추가]
        test = config.getBoolean("test", false); // [추가] 테스트 모드 (혼자 남았을 때 종료 X)
        disable_attack_cooldown = config.getBoolean("disable_attack_cooldown", true); // [추가]

        // 위치 정보는 좌표 X, Y, Z가 다 필요해서 따로 저장합니다. 지금은 일단 패스.
        // spawn_point는 나중에 Location 저장 로직 추가 필요.
    }

    /**
     * [설정 저장하기]
     * 게임 도중에 바뀐 규칙을 파일(config.yml)에도 적어서 영원히 기억하게 합니다.
     */
    public void save() {
        MocPlugin plugin = MocPlugin.getInstance();
        FileConfiguration config = plugin.getConfig();

        config.set("spawn_tf", spawn_tf);
        config.set("peace_time", peace_time);
        // config.set("team_attack", team_attack);
        // config.set("teammod", teammod);
        config.set("re_point", re_point);
        config.set("start_time", start_time);
        config.set("final_fight", final_fight);
        config.set("final_time", final_time);
        // config.set("map_end", map_end);
        config.set("map_size", map_size);
        config.set("win_value", win_value);
        config.set("hidden", hidden);
        config.set("battle_map", battle_map);
        config.set("random_map", random_map); // [추가]
        config.set("test", test); // [추가] 테스트 모드 저장
        config.set("disable_attack_cooldown", disable_attack_cooldown); // [추가]

        plugin.saveConfig(); // 실제로 파일에 씁니다.
    }

    /**
     * [만능 수정 도구]
     * 명령어로 "re_point를 5로 바꿔줘"라고 하면,
     * 'Refection(거울 비추기)' 기술을 써서 re_point라는 이름표를 가진 변수를 찾아 5를 넣습니다.
     * 
     * @param key   바꾸고 싶은 변수의 이름 (예: "re_point")
     * @param value 바꾸고 싶은 값 (예: "5")
     * @return 성공했는지 결과 메시지
     */
    public String setValue(String key, String value) {
        try {
            // 1. 내 장부(this)에 있는 모든 항목들 중에서...
            Field field = this.getClass().getDeclaredField(key);
            field.setAccessible(true); // "잠깐 실례합니다" 하고 자물쇠를 엽니다.

            // 2. 그 항목이 숫자인지, 참거짓(boolean)인지 확인하고 값을 넣습니다.
            if (field.getType() == int.class) {
                try {
                    int intValue = Integer.parseInt(value);
                    field.setInt(this, intValue); // 숫자 값 넣기
                } catch (NumberFormatException e) {
                    return "§c[오류] '" + value + "'는(은) 숫자가 아닙니다.";
                }
            } else if (field.getType() == boolean.class) {
                // "true", "yes", "on" 이면 켜진 걸로 칩니다.
                boolean boolValue = value.equalsIgnoreCase("true") ||
                        value.equalsIgnoreCase("t") ||
                        value.equalsIgnoreCase("yes") ||
                        value.equalsIgnoreCase("y") ||
                        value.equalsIgnoreCase("on") ||
                        value.equalsIgnoreCase("o");
                field.setBoolean(this, boolValue); // 참/거짓 값 넣기
            } else {
                return "§c[오류] '" + key + "' 항목은 문자열이나 다른 타입이라 지금은 못 바꿉니다.";
            }

            // 3. 바뀐 내용을 파일에도 저장합니다.
            save();
            return "§a[성공] " + key + " 값이 " + value + "(으)로 변경되었습니다.";

        } catch (NoSuchFieldException e) {
            return "§c[오류] '" + key + "'라는 설정 항목을 찾을 수 없습니다.";
        } catch (IllegalAccessException e) {
            return "§c[오류] 보안 문제로 값을 변경할 수 없습니다.";
        }
    }
}