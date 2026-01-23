// 파일 경로: src/main/java/me/user/moc/game/ArenaManager.java
package me.user.moc.game;

import me.user.moc.MocPlugin;
import me.user.moc.config.ConfigManager;
import me.user.moc.game.GameManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class ArenaManager {
    private final MocPlugin plugin;
    private final ConfigManager config = ConfigManager.getInstance();
    private final ClearManager clearManager;
    private Location gameCenter;
    private BukkitTask borderShrinkTask;
    private BukkitTask borderDamageTask;

    public ArenaManager(MocPlugin plugin) {
        this.plugin = plugin;
        this.clearManager = new ClearManager(plugin);
    }

    /**
     * [핵심] 전장을 생성하고 환경을 초기화합니다.
     */
    public void prepareArena(Location center) {
        this.gameCenter = center;
        World world = center.getWorld();
        if (world == null) return;

        // 1. [환경] 날씨 맑음, 시간 낮(1000)
        world.setStorm(false);
        world.setThundering(false);
        world.setTime(1000L);
        // 난이도 노말로 변경.
        world.setDifficulty(org.bukkit.Difficulty.NORMAL);

        // 2. [자기장] 기존 작업 종료 후, 설정값보다 2칸 작게 초기화
        stopTasks();
        world.getWorldBorder().setCenter(center);
        world.getWorldBorder().setSize(config.map_size - 2);

        // 3. [건축] 기반암 바닥 생성 시작 + 아이템 청소 + 몬스터 청소.
        // 특정 좌표기준으로 , 어느 높이에 설정할지 하는 함수.
        generateSquareFloor(center, center.getBlockY() - 1);
    }

    /**
     * [전장 바닥 설정 기능 - 정사각형 버전]
     * 기반암 바닥을 사각형으로 깔고 위아래를 청소한 뒤, 중앙에 에메랄드를 설치합니다.
     */
    public void generateSquareFloor(Location center, int targetY) {
        this.gameCenter = center;
        World world = center.getWorld();
        if (world == null) return;

        // 1. 범위 계산 (map_size가 75라면 중심에서 양옆으로 37칸씩)
        int halfSize = config.map_size / 2;

        int cx = center.getBlockX();
        int cz = center.getBlockZ();

        new BukkitRunnable() {
            // 시작 지점: 중심에서 -halfSize 만큼 떨어진 곳
            int x = cx - halfSize;

            @Override
            public void run() {
                // 서버 렉 방지를 위해 한 번에 20줄씩 공사
                for (int i = 0; i < 20; i++) {
                    // 모든 X축 범위 공사가 끝났다면 (중심 + halfSize를 넘었을 때)
                    if (x > cx + halfSize) {
                        // 2. [완성] 기반암 작업 완료 후 중앙에 에메랄드 설치
                        center.getBlock().setType(Material.EMERALD_BLOCK);
                        // 아이템, 몹 다 정리.
                        clearManager.allCear();

                        // 플레이어들을 에메랄드 위로 소환
                        if(config.spawn_tf){
                            Bukkit.getOnlinePlayers().forEach(p -> p.teleport(center.clone().add(0.5, 1.0, 0.5)));
                        }
                        this.cancel();
                        return;
                    }

                    // Z축 공사 (원형 체크 로직 삭제 -> 사각형으로 일괄 처리)
                    for (int z = cz - halfSize; z <= cz + halfSize; z++) {
                        // 3. [청소 및 건설] 해당 좌표의 하늘부터 땅끝까지
                        for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                            Block b = world.getBlockAt(x, y, z);
                            if (y == targetY) {
                                // 기반암 설치
                                b.setType(Material.BEDROCK, false);
                            } else {
                                // 기반암 위아래는 공기로 싹 비우기
                                if (b.getType() != Material.AIR) {
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
     * 자기장 좁아지기
     */
    public void startBorderShrink() {
        if (gameCenter == null) return;
        stopTasks(); // 중복 방지

        borderShrinkTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 1. [게임 종료 체크] 게임이 이미 끝났다면 자기장 줄이기를 멈춥니다.
                GameManager gm = GameManager.getInstance(MocPlugin.getInstance());
                if (!gm.isRunning()) {
                    stopTasks(); // 기존 3초마다 줄어드는 작업 중지
                    return;
                }
                double size = gameCenter.getWorld().getWorldBorder().getSize();
                if (size <= 5) {// 설정한 최소 크기인 5에 도달하면.
                    this.cancel();
                    // 안내 메시지 출력
                    plugin.getServer().broadcastMessage("§c[!] §f자기장이 최대로 줄어들었습니다.");
                    plugin.getServer().broadcastMessage("§c[!] §e7초 후 §f맵 중앙으로 모두 텔레포트 됩니다.");
                    plugin.getServer().broadcastMessage("§a텔레포트 카운트 다운 시작.");

                    // 7초 카운트다운을 위한 새로운 타이머 시작 (1초 = 20틱)
                    new BukkitRunnable() {
                        int timeLeft = 7; // 7초부터 시작

                        @Override
                        public void run() {
                            // 카운트다운 도중 게임이 끝나면 중단
                            if (!gm.isRunning()) {
                                this.cancel();
                                return;
                            }

                            if (timeLeft > 0) {
                                // 카운트다운 숫자 출력 (7, 6, 5...)
                                plugin.getServer().broadcastMessage("§e" + timeLeft);
                                // (선택사항) 째깍거리는 효과음 추가
                                for (Player p : plugin.getServer().getOnlinePlayers()) {
                                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 2f);
                                }
                                timeLeft--; // 시간 1초 감소
                            } else {
                                // 시간이 0이 되면 텔레포트 실행!
                                plugin.getServer().broadcastMessage("§c⚔ §l최종 전장 활성화 §c⚔");

                                // 모든 플레이어를 중앙(gameCenter)으로 이동
                                for (Player p : plugin.getServer().getOnlinePlayers()) {
                                    // 안전하게 이동시키기 위해 높이를 살짝 조정하거나 그대로 이동
                                    // (gameCenter가 에메랄드 블럭 위치라면, 그 위로 이동됩니다)
                                    p.teleport(gameCenter.clone().add(0, 1, 0));
                                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                                }

                                this.cancel(); // 카운트다운 타이머 종료
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 20L); // 0초 딜레이 후, 1초(20L)마다 실행
                    return;
                }
                // 아직 5보다 크다면 계속 2씩 줄여나갑니다.
                gameCenter.getWorld().getWorldBorder().setSize(size - 2, 1);
            }
        }.runTaskTimer(plugin, 0, 60L);
    }

    /**
     * 자기장 밖 대미지 처리를 시작합니다.
     */
    public void startBorderDamage() {
        if (gameCenter == null) return;
        World world = gameCenter.getWorld();
        if (world == null) return;

        WorldBorder border = world.getWorldBorder();

        // 1. [핵심] 마인크래프트 자체 장벽 대미지 설정 (가장 정확함)
        // 버퍼(안전거리)를 0으로 설정해서 선을 밟자마자 아프게 합니다.
        border.setDamageBuffer(0.0);
        // 초당 대미지 설정 (한 칸 나갈 때마다 추가 대미지)
        border.setDamageAmount(3.0);

        // 2. [메시지 알림 전용] 0.25초마다 검사하여 즉각 경고 메시지 전송
        if (borderDamageTask != null) borderDamageTask.cancel(); // 중복 방지

        borderDamageTask = new BukkitRunnable() {
            @Override
            public void run() {
                double size = border.getSize() / 2.0;
                Location center = border.getCenter();

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().equals(world)) continue;

                    // 관전자는 무시
                    if (p.getGameMode() == GameMode.SPECTATOR) continue;

                    Location loc = p.getLocation();
                    // 3. [수학적 계산] 장벽 중심에서 플레이어의 거리가 장벽 반지름보다 큰지 확인
                    if (Math.abs(loc.getX() - center.getX()) > size || Math.abs(loc.getZ() - center.getZ()) > size) {

                        // 직접 대미지를 주는 코드는 주석 처리하거나 보조용으로만 씁니다.
                        // 왜냐하면 위에서 설정한 border.setDamageAmount가 훨씬 정확하게 때려줍니다.
                        // p.damage(2.0); // (선택사항) 장벽 대미지가 약하다면 추가로 사용

                        // 액션바에 경고 표시 (메시지 도배 방지)
                        p.sendActionBar(net.kyori.adventure.text.Component.text("!!! 자기장 구역 밖입니다 !!!").color(net.kyori.adventure.text.format.NamedTextColor.RED).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 5L); // 5틱(0.25초)마다 검사해서 반응 속도 4배 향상!
    }

    /**
     * 자기장 멈추기 및
     */
    public void stopTasks() {
        if (borderShrinkTask != null) borderShrinkTask.cancel();
        if (borderDamageTask != null) borderDamageTask.cancel();
        borderShrinkTask = null;
        borderDamageTask = null;
    }
}