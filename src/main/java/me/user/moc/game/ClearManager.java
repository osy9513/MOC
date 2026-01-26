// 파일 경로: src/main/java/me/user/moc/game/ClearManager.java
package me.user.moc.game;

import me.user.moc.MocPlugin;
import me.user.moc.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;

/**
 * ClearManager: 전장에 떨어진 아이템이나 소환된 몬스터들을 싹 치워주는 '청소 담당자' 클래스입니다.
 */
public class ClearManager {
    private final MocPlugin plugin;
    private final ConfigManager config = ConfigManager.getInstance(); // 설정 파일(config.yml) 정보를 가져옵니다.

    public ClearManager(MocPlugin plugin) {
        this.plugin = plugin;
    }

    // 월드 정보를 가져오는 기능을 자바 문법에 맞게 수정했습니다.
    private World getThisWorld() { // Object 대신 정확하게 World라고 적어줘야 합니다.
        // 1. 청소할 기준점을 정합니다. 설정된 스폰 지점을 먼저 찾습니다.
        Location center = config.spawn_point;

        // 만약 스폰 지점이 설정 안 되어 있다면, 현재 접속 중인 플레이어 중 한 명의 위치를 기준점으로 삼습니다.
        if (center == null) {
            if (Bukkit.getOnlinePlayers().isEmpty())
                return null; // 접속자가 없으면 null 반환
            center = Bukkit.getOnlinePlayers().iterator().next().getLocation();
        }

        // 2. 기준점이 속한 '월드(세계)' 정보를 가져와서 바로 보내줍니다.
        return center.getWorld();
    }

    /**
     * 월드 내의 아이템과 생명체(플레이어 제외), 그리고 박힌 투사체를 전부 제거하는 핵심 청소 기능입니다.
     */
    public void allCear() {
        // 1. [월드 가져오기] 설정된 스폰 포인트의 월드를 가져옵니다.
        World world = null;
        if (plugin.getConfigManager().spawn_point != null) {
            world = plugin.getConfigManager().spawn_point.getWorld();
        }

        // 만약 설정이 안 되어 있다면, 서버의 첫 번째 월드를 기본으로 잡습니다.
        if (world == null && !plugin.getServer().getWorlds().isEmpty()) {
            world = plugin.getServer().getWorlds().get(0);
        }

        // 그래도 월드가 없으면(매우 희박함) 종료
        if (world == null)
            return;

        // 2. [아이템 청소] 바닥에 떨어져 있는 모든 아이템 제거
        world.getEntitiesByClass(Item.class).forEach(Entity::remove);

        // 3. [박힌 투사체 청소] 삼지창(Trident)과 화살(Arrow) 제거 (최적화된 방식)
        world.getEntitiesByClass(Trident.class).forEach(Entity::remove);
        world.getEntitiesByClass(Arrow.class).forEach(Entity::remove);

        // 4. [생명체 청소] 플레이어를 제외한 모든 몹/동물 제거
        world.getLivingEntities().forEach(entity -> {
            // [필터링] 플레이어가 아닐 때만 삭제
            if (!(entity instanceof Player)) {
                entity.remove();
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
        if (world == null)
            return;

        // 월드보더 원래대로 키우기 (크기 30000000으로 복구 예시)
        world.getWorldBorder().setSize(30000000);
        world.getWorldBorder().setCenter(config.spawn_point); // 설정된 스폰 지점 중심
    }

}