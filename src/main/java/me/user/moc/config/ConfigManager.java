package me.user.moc.config; // 이 파일이 있는 위치

import org.bukkit.Location;

/**
 * [ 설정 장부 ]
 * 게임의 규칙(시간, 점수, 장소 등)을 적어두고 관리하는 곳입니다.
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
    public boolean team_attack = false; // 같은 팀끼리 때릴 수 있나요?
    public boolean teammod = false; // 팀전인가요? (아니오=개인전)
    public int re_point = 1; // 능력 다시 뽑기 기회 (1번)
    public int start_time = 30; // 능력 추첨 제한 시간 (30초)
    public boolean final_fight = true; // 시간이 지나면 자기장이 생기나요?
    public int final_time = 120; // 게임 시작 몇 초 뒤에 자기장이 생기나요? (120초 = 2분)
    public boolean map_end = true; // 전장 외곽 벽을 활성화할까요?
    public int map_size = 75; // 전장(경기장)의 크기
    public int win_value = 40; // 몇 점을 먼저 따면 최종 우승인가요?
    public boolean hidden = false; // 숨겨진 캐릭터가 등장하나요?
}