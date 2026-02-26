// 파일 경로: src/main/java/me/user/moc/game/ClearManager.java
package me.user.moc.game;

import me.user.moc.MocPlugin;
import me.user.moc.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;

import java.util.ArrayList;
import java.util.List;

/**
 * ClearManager: 전장에 떨어진 아이템이나 소환된 몬스터들을 싹 치워주는 '청소 담당자' 클래스입니다.
 */
public class ClearManager {
    private final MocPlugin plugin;
    private final ConfigManager config = ConfigManager.getInstance(); // 설정 파일(config.yml) 정보를 가져옵니다.

    // [추가] 백업된 엔티티들을 저장할 리스트
    private final List<SavedEntity> backedUpEntities = new ArrayList<>();

    // [추가] 엔티티의 위치와 스냅샷 데이터를 보관하는 내부 클래스
    private static class SavedEntity {
        public Location location;
        public EntitySnapshot snapshot;

        public SavedEntity(Location location, EntitySnapshot snapshot) {
            this.location = location;
            this.snapshot = snapshot;
        }
    }

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
     * 
     * @param doBackup 참일 경우 지우기 전에 EntitySnapshot을 이용해 메모리에 보관합니다.
     */
    public void allCear(boolean doBackup) {
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

        // [추가] 백업 모드일 때는 기존 백업 리스트 초기화
        if (doBackup) {
            backedUpEntities.clear();
        }

        // 2. [아이템 청소] 바닥에 떨어져 있는 모든 아이템 처리
        world.getEntitiesByClass(Item.class).forEach(entity -> {
            if (doBackup) {
                backedUpEntities.add(new SavedEntity(entity.getLocation(), entity.createSnapshot()));
            }
            entity.remove();
        });

        // 3. [박힌 투사체 청소] 삼지창(Trident)과 화살(Arrow) 제거 (최적화된 방식)
        world.getEntitiesByClass(Trident.class).forEach(entity -> {
            if (doBackup) {
                backedUpEntities.add(new SavedEntity(entity.getLocation(), entity.createSnapshot()));
            }
            entity.remove();
        });

        world.getEntitiesByClass(Arrow.class).forEach(entity -> {
            if (doBackup) {
                backedUpEntities.add(new SavedEntity(entity.getLocation(), entity.createSnapshot()));
            }
            entity.remove();
        });

        // 4. [생명체 청소] 플레이어를 제외한 모든 몹/동물 제거
        world.getLivingEntities().forEach(entity -> {
            // [필터링] 플레이어가 아닐 때만 삭제 및 보관
            if (!(entity instanceof Player)) {
                if (doBackup) {
                    backedUpEntities.add(new SavedEntity(entity.getLocation(), entity.createSnapshot()));
                }
                entity.remove();
            }
        });
    }

    /**
     * 기존 호환성을 위해 유지하는 백업 없는 기본 청소
     */
    public void allCear() {
        allCear(false);
    }

    /**
     * [추가] 백업해뒀던 엔티티들을 원래 위치에 스냅샷을 기반으로 되살립니다.
     */
    public void restoreEntities() {
        if (backedUpEntities.isEmpty())
            return;

        int restoredCount = 0;
        for (SavedEntity saved : backedUpEntities) {
            try {
                // 저장된 스파운 위치와 스냅샷 데이터를 이용해 복원
                Entity e = saved.snapshot.createEntity(saved.location);
                e.spawnAt(saved.location);
                restoredCount++;
            } catch (Exception ex) {
                // 엔티티 복원 중 드물게 에러가 발생하더라도 루프가 깨지지 않게 방어
                plugin.getLogger().warning("엔티티 복구 중 오류 발생: " + ex.getMessage());
            }
        }

        // 복구 직후 데이터 비우기
        backedUpEntities.clear();
        Bukkit.broadcastMessage("§a[MOC] §f야생 생태계(엔티티 " + restoredCount + "개) 복원 완료!");
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