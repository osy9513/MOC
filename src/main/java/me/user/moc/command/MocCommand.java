package me.user.moc.command; // 명령어가 들어있는 폴더 주소

import me.user.moc.MocPlugin;
import me.user.moc.game.GameManager;
import me.user.moc.ability.AbilityManager;
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
                if (!gm.isRunning()) { // <--- [여기 변경됨!!!] 게임 중인지 확인
                    p.sendMessage("§c현재 진행 중인 게임이 없습니다.");
                    return true;
                } // [▼▼▼ 추가됨 ▼▼▼]
                if (gm.isAfk(p.getName())) {
                    p.sendMessage("§c[!] 당신은 현재 게임 열외(AFK) 상태입니다.");
                    return true;
                }
                gm.showAbilityDetail(p); // 내 능력이 뭔지 자세한 설명을 보여줍니다.
                return true;
            }

            case "afk" -> { // 잠수 설정: /moc afk [닉네임]
                if (args.length < 2) {
                    p.sendMessage("§c사용법: /moc afk [플레이어이름]");
                    return true;
                }
                gm.toggleAfk(args[1]); // 해당 유저를 게임 인원에서 빼거나 넣습니다.
                p.sendMessage("§e[MOC] §f" + args[1] + "님의 참가 상태를 변경했습니다.");
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
                return true;
            }

            default -> {
                p.sendMessage("§c /moc help 를 입력하세요.");
            }
        }

        return false;
    }
}