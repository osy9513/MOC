package me.user.moc.ability.test;

import me.user.moc.MocPlugin;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * AbilityAssigner: 테스트를 위해 닉네임으로 능력을 강제 부여하는 기능을 담당합니다.
 * 요청대로 ability 폴더 내에 별도의 폴더(test)를 만들어 분리했습니다.
 */
public class AbilityAssigner {

    /**
     * 특정 플레이어에게 능력을 강제로 배정합니다.
     * 
     * @param targetName  대상 플레이어의 닉네임
     * @param abilityCode 적용할 능력 이름
     * @return 성공 여부 및 결과 메시지
     */
    public static String assignAbility(String targetName, String abilityCode) {
        // 1. 플레이어가 서버에 접속해 있는지 확인합니다.
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            return "§c[오류] " + targetName + "님을 찾을 수 없습니다. (접속 중인가요?)";
        }

        // 2. 능력 매니저를 가져옵니다.
        AbilityManager am = MocPlugin.getInstance().getAbilityManager();

        // 3. 능력을 설정합니다.
        // 참고: AbilityManager에 해당 능력이 실제로 존재하는지 확인하는 로직이 있으면 더 좋지만,
        // 현재 AbilityManager 구조상 문자열로 바로 넣는 방식이므로 그대로 설정합니다.
        // (만약 없는 능력을 넣으면 기능이 작동 안 할 뿐, 에러는 안 날 것입니다)
        am.setPlayerAbility(target.getUniqueId(), abilityCode);

        // 4. 대상 플레이어에게 알림을 보냅니다.
        target.sendMessage("§e[관리자] §f당신의 능력이 변경되었습니다.");

        // 5. 변경된 능력 정보를 보여줍니다.
        am.showAbilityInfo(target, abilityCode, 2);

        // [테스트 편의성] 능력 아이템(소환수 등)을 즉시 지급합니다.
        // 이를 통해 /moc start 없이도 바로 능력을 확인할 수 있습니다.
        am.giveAbilityItems(target);

        return "§a[성공] " + targetName + "님에게 능력을 부여했습니다.";
    }
}
