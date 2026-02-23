package me.user.moc.command; // 명령어가 들어있는 폴더 주소

import me.user.moc.MocPlugin;
import me.user.moc.game.GameManager;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * [ 명령어 처리반 ]
 * 플레이어가 채팅창에 /moc 를 입력하면 여기서 모든 것을 처리합니다.
 */
public class MocCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 1. 명령어를 친 사람이 사람(플레이어)인지 확인합니다. (cmd 서버 콘솔창에서 치면 무시)
        if (!(sender instanceof Player p)) {
            return false;
        }

        // 2. [핵심 포인트] 메인 플러그인에서 게임 매니저를 빌려옵니다.
        // MocPlugin.getInstance()는 "지금 켜져 있는 플러그인 나와라!" 하는 호출 버튼입니다.
        GameManager gm = GameManager.getInstance(MocPlugin.getInstance());
        AbilityManager am = AbilityManager.getInstance(MocPlugin.getInstance());

        // 3. /moc 만 치고 뒤에 아무것도 안 적었을 때를 대비합니다.
        if (args.length == 0) {
            // p.sendMessage("§e[MOC] §f사용법: /moc <start|stop|yes|re|check|afk|set>");
            // 나중에 /moc help 만들 예정.
            p.sendMessage("§e[MOC] §f /moc help 라고 입력하세요");
            return true;
        }

        // 4. 입력한 단어(args[0])가 무엇인지에 따라 각기 다른 일을 시킵니다.
        switch (args[0].toLowerCase()) {

            case "start" -> { // 게임 시작: /moc start
                if (!p.isOp()) { // 관리자(OP)만 시작할 수 있게 감시합니다.
                    p.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                gm.startGame(p); // 게임 관리자에게 "게임 시작해!"라고 명령합니다.
                return true;
            }

            case "stop" -> { // 게임 강제 종료: /moc stop
                if (!p.isOp()) {
                    p.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                gm.stopGame(); // 게임 관리자에게 "게임 당장 멈춰!"라고 명령합니다.
                return true;
            }

            case "yes" -> { // 능력 수락: /moc yes
                if (!gm.isRunning()) { // <--- [여기 변경됨!!!] 게임 중인지 확인
                    p.sendMessage("§c현재 진행 중인 게임이 없습니다.");
                    return true;
                } // [▼▼▼ 추가됨 ▼▼▼]
                if (gm.isAfk(p.getName())) {
                    p.sendMessage("§c[!] 당신은 현재 게임 열외(AFK) 상태입니다.");
                    return true;
                }
                gm.playerReady(p); // "저 이 능력으로 할게요"라고 등록합니다.
                return true;
            }

            case "re" -> { // 능력 다시 뽑기: /moc re
                if (!gm.isRunning()) { // <--- [여기 변경됨!!!] 게임 중인지 확인
                    p.sendMessage("§c현재 진행 중인 게임이 없습니다.");
                    return true;
                } // [▼▼▼ 추가됨 ▼▼▼]
                if (gm.isAfk(p.getName())) {
                    p.sendMessage("§c[!] 당신은 현재 게임 열외(AFK) 상태입니다.");
                    return true;
                }
                gm.playerReroll(p); // 능력을 다시 굴립니다.
                return true;
            }

            case "check" -> { // 내 능력 보기: /moc check
                gm.showAbilityDetail(p); // 내 능력이 뭔지 자세한 설명을 보여줍니다.
                return true;
            }

            case "afk" -> { // 잠수 설정: /moc afk [닉네임]
                if (args.length < 2) {
                    p.sendMessage("§c사용법: /moc afk [플레이어이름]");
                    return true;
                }

                String targetName = args[1];
                // [권한 체크] 본인이 아닌 다른 사람의 상태를 변경하려면 OP 권한 필요
                if (!targetName.equals(p.getName()) && !p.isOp()) {
                    p.sendMessage("§c[권한 부족] 다른 플레이어의 상태를 변경하려면 관리자 권한이 필요합니다.");
                    return true;
                }

                gm.toggleAfk(targetName); // 해당 유저를 게임 인원에서 빼거나 넣습니다.
                p.sendMessage("§e[MOC] §f" + targetName + "님의 참가 상태를 변경했습니다.");
                return true;
            }

            case "set" -> { // 능력 강제 부여: /moc set [닉네임] [능력]
                if (!p.isOp()) {
                    p.sendMessage("§c권한이 없습니다.");
                    return true;
                } // [▼▼▼ 추가됨 ▼▼▼]
                if (gm.isAfk(p.getName())) {
                    p.sendMessage("§c[!] 당신은 현재 게임 열외(AFK) 상태입니다.");
                    return true;
                }
                if (args.length < 3) {
                    p.sendMessage("§c사용법: /moc set [플레이어이름] [능력]");
                    return true;
                }
                // 다른 폴더에 있는 능력 배정 도구(AbilityAssigner)를 불러와서 일을 시킵니다.
                String result = me.user.moc.ability.test.AbilityAssigner.assignAbility(args[1], args[2]);
                p.sendMessage(result);

                // [추가] 능력이 부여된 플레이어에게 즉시 아이템 지급
                Player target = org.bukkit.Bukkit.getPlayer(args[1]);
                if (target != null) {
                    am.giveAbilityItems(target);
                }

                // [추가됨] 능력 부여 후 즉시 준비 완료 상태로 변경
                gm.playerReadyTarget(args[1]);
                return true;
            }

            case "skip" -> { // 라운드 스킵: /moc skip
                if (!p.isOp()) {
                    p.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                if (!gm.isRunning()) {
                    p.sendMessage("§c현재 진행 중인 게임이 없습니다.");
                    return true;
                }
                gm.skipRound();
                return true;
            }

            case "list" -> { // 목록 조회: /moc list
                // if (!p.isOp()) { // 관리자(OP)만 시작할 수 있게 감시합니다.
                // p.sendMessage("§c권한이 없습니다.");
                // return true;
                // }
                // if (!gm.isRunning()) { // <--- [여기 변경됨!!!] 게임 중인지 확인
                // p.sendMessage("§c현재 진행 중인 게임이 없습니다.");
                // return true;
                // }
                am.showAbilityList(p);

                p.sendMessage(" ");
                p.sendMessage("§cset 사용법: /moc set [플레이어이름] [능력]");
                p.sendMessage(" ");

                return true;
            }

            case "config" -> { // [신규] 설정 관리: /moc config [set]
                if (!p.isOp()) {
                    p.sendMessage("§c권한이 없습니다.");
                    return true;
                }

                me.user.moc.config.ConfigManager cm = me.user.moc.config.ConfigManager.getInstance();

                // 1. 그냥 /moc config 라고만 쳤을 때 -> 현재 설정 값들 보여주기
                if (args.length == 1) {
                    p.sendMessage(" ");
                    p.sendMessage("§6[ MOC 설정 목록 ]");
                    p.sendMessage("기본 설정");
                    p.sendMessage("§e win_value (승리 목표 점수 - 숫자): §f" + cm.win_value);
                    p.sendMessage("§e spawn_tf (라운드 시작 시 티피 활성화 - true/false): §f" + cm.spawn_tf);
                    p.sendMessage("§e re_point (능력 리롤 횟수 - 숫자): §f" + cm.re_point);
                    p.sendMessage("§e start_time (능력 추첨 시간 - 초): §f" + cm.start_time);
                    p.sendMessage("§e peace_time (능력 추첨 후 평화 시간 - 초): §f" + cm.peace_time);
                    p.sendMessage(
                            "§e disable_attack_cooldown (공격 딜레이 제거 - true/false): §f" + cm.disable_attack_cooldown); // [추가]
                    p.sendMessage("");
                    p.sendMessage("전장 설정");
                    p.sendMessage("§e map_size (맵 크기 - 숫자): §f" + cm.map_size);
                    p.sendMessage("§e battle_map (맵 크기에 맞춰 전장 활성화 - true/false): §f" + cm.battle_map);
                    p.sendMessage("§e random_map (전장의 랜덤 요소 활성화 - true/false): §f" + cm.random_map);
                    p.sendMessage("§e final_fight (맵 끝 자기장 축소 여부 - true/false): §f" + cm.final_fight);
                    p.sendMessage("§e final_time (맵 끝 자기장 축소 시간 - 초): §f" + cm.final_time);
                    p.sendMessage("");
                    p.sendMessage("기타 설정");
                    p.sendMessage("§e hidden (히든 캐릭터 활성화): §f" + cm.hidden);
                    p.sendMessage("§e test (테스트 모드 - true/false): §f" + cm.test); // [추가]
                    p.sendMessage("§6 ※참고: 테스트 모드 시 자동으로 크리에이티브 모드로 전환됩니다. 크리에이티브 모드 일 땐 노쿨로 능력 사용 가능");
                    p.sendMessage(" ");
                    p.sendMessage("§7(변경법: /moc config set [이름] [값])");
                    p.sendMessage(" ");
                    return true;
                }

                // 2. /moc config set [이름] [값]
                if (args[1].equalsIgnoreCase("set")) {
                    if (args.length < 4) {
                        p.sendMessage("§c사용법: /moc config set [설정변수명] [값]");
                        return true;
                    }
                    String key = args[2];
                    String value = args[3];

                    // ConfigManager에게 일을 떠넘깁니다. "이거 바꿔줘!"
                    String result = cm.setValue(key, value);
                    p.sendMessage(result);

                    // [추가] 테스트 모드가 변경되었다면 즉시 게임모드 반영
                    if (key.equalsIgnoreCase("test")) {
                        org.bukkit.GameMode targetMode = cm.test ? org.bukkit.GameMode.CREATIVE
                                : org.bukkit.GameMode.SURVIVAL;
                        for (Player onlineP : Bukkit.getOnlinePlayers()) {
                            onlineP.setGameMode(targetMode);
                        }
                        p.sendMessage(cm.test ? "§e[TEST] §f모든 플레이어가 크리에이티브 모드로 전환되었습니다."
                                : "§e[TEST] §f모든 플레이어가 서바이벌 모드로 전환되었습니다.");
                    }
                    return true;
                }
            }

            case "help" -> { // 도움이 필요할 때: /moc help
                p.sendMessage("§6=== [ MOC 명령어 도움말 ] ===");
                p.sendMessage("§e/moc yes §f: 능력 확정 및 준비 완료");
                p.sendMessage("§e/moc re §f: 능력 다시 뽑기 (횟수 제한 있음)");
                p.sendMessage("§e/moc check §f: 내 능력 상세 정보 확인");
                p.sendMessage("§e/moc list §f: 전체 능력 목록 확인");
                p.sendMessage("§e/moc afk [닉네임] §f: 플레이어 게임 참여/열외 설정");
                if (p.isOp()) {
                    p.sendMessage("§c--- 관리자 명령어 ---");
                    p.sendMessage("§c/moc start §f: 게임 시작");
                    p.sendMessage("§c/moc stop §f: 게임 강제 종료");
                    p.sendMessage("§c/moc skip §f: 현재 라운드 스킵 (생존 점수 X)");
                    p.sendMessage("§c/moc allready §f: 모든 플레이어 준비 완료 처리"); // 추가
                    p.sendMessage("§c/moc set [닉네임] [코드] §f: 능력 강제 부여");
                    p.sendMessage("§c/moc config §f: 게임 설정 확인 및 변경");
                }
                return true;
            }

            case "allready" -> { // 모두 준비 완료: /moc allready
                if (!p.isOp()) {
                    p.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                if (!gm.isRunning()) {
                    p.sendMessage("§c현재 진행 중인 게임이 없습니다.");
                    return true;
                }
                gm.allReady();
                return true;
            }

            case "score" -> { // [디버깅 용도] 점수 확인
                if (!p.isOp())
                    return true;
                p.sendMessage("§e=== 현재 점수판 데이터 ===");
                for (java.util.UUID uuid : gm.getTopScores().stream().map(java.util.Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toList())) {
                    String name = Bukkit.getOfflinePlayer(uuid).getName();
                    p.sendMessage("§f" + name + " : §a" + gm.getScore(uuid));
                }
                if (gm.getTopScores().isEmpty()) {
                    p.sendMessage("§c저장된 점수가 없습니다.");
                }
                return true;
            }

            default ->

            {
                p.sendMessage("§c알 수 없는 명령어입니다. /moc help 를 입력하세요.");
            }
        }

        return false;
    }
}