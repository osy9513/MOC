package me.user.moc.game;

import me.user.moc.MocPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class ArenaManager {
    private final MocPlugin plugin;
    private Location gameCenter;
    private BukkitTask borderShrinkTask;
    private BukkitTask borderDamageTask;

    public ArenaManager(MocPlugin plugin) {
        this.plugin = plugin;
    }

    public void setGameCenter(Location center) {
        this.gameCenter = center;
    }

    public Location getGameCenter() {
        return gameCenter;
    }

    /**
     * 경기장 바닥을 생성하고 플레이어들을 텔레포트시킵니다.
     * @param center 경기장 중심
     * @param radius 반지름
     * @param targetY 바닥 높이
     */
    public void generateCircleFloor(Location center, int radius, int targetY) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        long radiusSq = (long) radius * radius;
        
        // 텔레포트 목적지 (중심)
        Location teleportDest = center.clone();

        new BukkitRunnable() {
            int x = cx - radius;
            @Override
            public void run() {
                for (int i = 0; i < 20; i++) {
                    if (x > cx + radius) {
                        // 생성 완료 후 아이템 제거 및 플레이어 텔레포트
                        world.getEntitiesByClass(Item.class).forEach(Entity::remove);
                        Bukkit.getOnlinePlayers().forEach(p -> p.teleport(teleportDest.clone().add(0.5, 1.0, 0.5)));
                        this.cancel();
                        return;
                    }
                    long dx = (long) (x - cx) * (x - cx);
                    for (int z = cz - radius; z <= cz + radius; z++) {
                        if (dx + (long) (z - cz) * (z - cz) <= radiusSq) {
                            for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                                Block b = world.getBlockAt(x, y, z);
                                if (y == targetY) {
                                    // 텔레포트 위치의 바로 아래(바닥)은 비우지 않음 (혹시 모를 안전장치)
                                    // 원본 코드에서도 비슷하게 동작했으나, 여기서는 단순화하여 바닥 생성
                                     b.setType(Material.BEDROCK, false);
                                } else if (b.getType() != Material.AIR) {
                                    b.setType(Material.AIR, false);
                                }
                            }
                        }
                    }
                    x++;
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /**
     * 자기장 축소를 시작합니다.
     */
    public void startBorderShrink() {
        if (gameCenter == null) return;
        World world = gameCenter.getWorld();
        
        // 5분 후 시작되도록 설정 (호출하는 쪽에서 딜레이 처리하거나 여기서 처리)
        // 여기서는 즉시 시작하는 로직만 담고, 스케줄링은 외부 혹은 별도 메서드로 관리
        
        borderShrinkTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 게임이 진행 중이 아니면 중단해야 함 (체크 로직 필요)
                double size = world.getWorldBorder().getSize();
                if (size <= 1) {
                    this.cancel();
                    return;
                }
                world.getWorldBorder().setSize(size - 2, 1);
            }
        }.runTaskTimer(plugin, 0, 60L);
    }

    /**
     * 자기장 밖 대미지 처리를 시작합니다.
     */
    public void startBorderDamage() {
        if (gameCenter == null) return;
        World world = gameCenter.getWorld();

        borderDamageTask = new BukkitRunnable() {
            @Override
            public void run() {
                WorldBorder b = world.getWorldBorder();
                double s = b.getSize() / 2.0;
                Location c = b.getCenter();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().equals(world)) {
                        Location l = p.getLocation();
                        if (Math.abs(l.getX() - c.getX()) > s || Math.abs(l.getZ() - c.getZ()) > s) {
                            p.damage(6.0);
                            p.sendMessage("§c§l[자기장 밖 대미지]");
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20L);
    }
    
    public void stopTasks() {
        if (borderShrinkTask != null && !borderShrinkTask.isCancelled()) borderShrinkTask.cancel();
        if (borderDamageTask != null && !borderDamageTask.isCancelled()) borderDamageTask.cancel();
        if (gameCenter != null) {
            gameCenter.getWorld().getWorldBorder().setSize(30000000);
        }
    }
}
