// 파일 경로: src/main/java/me/user/moc/game/ClearManager.java
package me.user.moc.game;

import me.user.moc.MocPlugin;
import me.user.moc.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;

/**
 * ClearManager: 전장에 떨어진 아이템이나 소환된 몬스터들을 싹 치워주는 '청소 담당자' 클래스입니다.
 */
public class ClearManager {
    private final ConfigManager config = ConfigManager.getInstance(); // 설정 파일(config.yml) 정보를 가져옵니다.

    public ClearManager(MocPlugin plugin) {
    }

    // 월드 정보를 가져오는 기능을 자바 문법에 맞게 수정했습니다.
    private World getThisWorld() { // Object 대신 정확하게 World라고 적어줘야 합니다.
        // 1. 청소할 기준점을 정합니다. 설정된 스폰 지점을 먼저 찾습니다.
        Location center = config.spawn_point;

        // 만약 스폰 지점이 설정 안 되어 있다면, 현재 접속 중인 플레이어 중 한 명의 위치를 기준점으로 삼습니다.
        if (center == null) {
            if (Bukkit.getOnlinePlayers().isEmpty()) return null; // 접속자가 없으면 null 반환
            center = Bukkit.getOnlinePlayers().iterator().next().getLocation();
        }

        // 2. 기준점이 속한 '월드(세계)' 정보를 가져와서 바로 보내줍니다.
        return center.getWorld();
    }

    /**
     * 월드 내의 아이템과 생명체(플레이어 제외)를 전부 제거하는 핵심 청소 기능입니다.
     */
    public void allCear() {
        // [수정] 자바에서는 'let' 대신 변수의 종류(World)를 직접 적어줘야 합니다.
        World world = getThisWorld();
        // 월드를 못 찾았을 경우를 대비해 안전장치를 추가합니다.
        if (world == null) return;

        // 3. [아이템 청소] 바닥에 떨어져 있는 모든 아이템(Item 클래스)을 찾아서 제거합니다.
        world.getEntitiesByClass(Item.class).forEach(Entity::remove);

        // 4. [생명체 청소] 월드 내의 모든 살아있는 생명체(LivingEntity)를 하나씩 검사합니다.
        world.getLivingEntities().forEach(entity -> {

            // 5. [필터링] 만약 이 생명체가 '플레이어'가 아니라면 삭제합니다.
            // (사람까지 지워지면 안 되니까 "사람이 아닐 때만 삭제해!"라고 명령하는 것입니다.)
            if (!(entity instanceof org.bukkit.entity.Player)) {
                entity.remove(); // 좀비, 스켈레톤, 동물 등을 삭제함
            }
        });
    }


    /**
     * 월드 보더 = 자기장 초기화.
     */
    public void worldBorderCear() {
        // [수정] 자바에서는 'let' 대신 변수의 종류(World)를 직접 적어줘야 합니다.
        World world = getThisWorld();
        // 월드를 못 찾았을 경우를 대비해 안전장치를 추가합니다.
        if (world == null) return;

        // 월드보더 원래대로 키우기 (크기 30000000으로 복구 예시)
        world.getWorldBorder().setSize(30000000);
        world.getWorldBorder().setCenter(config.spawn_point); // 설정된 스폰 지점 중심
    }

}