// 파일 경로: src/main/java/me/user/moc/game/ArenaManager.java
package me.user.moc.game;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import me.user.moc.MocPlugin;
import me.user.moc.config.ConfigManager;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

public class ArenaManager implements Listener {
    private final MocPlugin plugin;
    private final ConfigManager config = ConfigManager.getInstance();
    private final ClearManager clearManager;
    private Location gameCenter;
    private BukkitTask borderShrinkTask; // 자기장 및 진행 관련 타이머
    private BukkitTask borderDamageTask; // 대미지 체크 타이머
    private BukkitTask generateTask; // 맵 공사 타이머 (추가됨!)
    private org.bukkit.entity.Entity centerMarker; // [추가] 중앙 마커 엔티티
    private GameManager gm;

    public ArenaManager(MocPlugin plugin) {
        this.plugin = plugin;
        this.clearManager = new ClearManager(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin); // 이벤트 리스너 등록
    }

    // [이벤트] 자기장 밖 몬스터 소환 금지
    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent e) {
        if (gameCenter == null || e.getEntity() instanceof Player)
            return;

        WorldBorder border = gameCenter.getWorld().getWorldBorder();
        double size = border.getSize() / 2.0; // 반지름
        Location center = border.getCenter();
        Location loc = e.getLocation();

        // 자기장 밖이면 스폰 취소
        if (Math.abs(loc.getX() - center.getX()) > size || Math.abs(loc.getZ() - center.getZ()) > size) {
            e.setCancelled(true);
        }
    }

    public void setGameManager(GameManager gm) {
        this.gm = gm;
    }

    /**
     * [핵심] 전장을 생성하고 환경을 초기화합니다.
     */
    public void prepareArena(Location center) {
        this.gameCenter = center;
        World world = center.getWorld();
        if (world == null)
            return;

        // 1. [환경] 날씨 맑음, 시간 낮(1000)
        world.setStorm(false);
        world.setThundering(false);
        world.setTime(1000L);
        // 난이도 노말로 변경.
        world.setDifficulty(org.bukkit.Difficulty.NORMAL);

        // 2. [자기장] 기존 작업 종료 후, 설정값보다 2칸 작게 초기화
        stopTasks();
        // 게임이 끝나고 다시 시작할 때, 이전 게임의 '즉사 대미지' 설정이 남아있지 않게 안전하게 초기화/ 현재는 굳이 할 필요 없음.
        // world.getWorldBorder().setDamageBuffer(5.0);
        // world.getWorldBorder().setDamageAmount(0.2);
        world.getWorldBorder().setCenter(center);
        world.getWorldBorder().setSize(config.map_size - 2);

        // [추가] 배틀맵 설정이 켜져있을 때만 바닥 공사를 합니다. (야생맵 보존)
        // [추가] 배틀맵 설정이 켜져있을 때만 바닥 공사를 합니다. (야생맵 보존)
        if (config.battle_map) {
            // 3. [건축] 기반암 바닥 생성 시작 + 아이템 청소 + 몬스터 청소.
            // 특정 좌표기준으로 , 어느 높이에 설정할지 하는 함수.
            generateSquareFloor(center, center.getBlockY() - 1);
        } else {
            // 배틀맵이 아니면 바로 청소만 하고 끝냅니다.
            clearManager.allCear();

            // [추가] 야생맵이어도 중앙(스폰포인트) 표시를 위해 에메랄드 블럭 하나는 설치합니다.
            // 일부 능력에서 에메랄드 블럭을 참조하기도 합니다.
            center.getBlock().setType(Material.EMERALD_BLOCK);

            // 플레이어들을 위한 스폰 이동 등은 GameManager에서 처리하거나 여기서 보조
            if (config.spawn_tf) {
                // 야생맵이어도 중앙으로 모으고 싶다면 이동
                // 하지만 보통 야생맵은 랜덤 텔포로 퍼지니까 굳이 안 모아도 됨.
                // 그래도 prepareArean 호출 시점에는 모으는게 맞음 (대기 시간)
                // 다만 기반암이 없으므로 Y좌표 안전하게 잡는 게 중요.
                // 일단 기존 로직인 generateSquareFloor completion 부분과 맞추기 위해
                // 여기서 바로 텔포 시키지 않고 GameManager 흐름을 따릅니다.
                // (GameManager.startRound에서 prepareArena 호출 후 텔포 로직이 또 있음)
            }
        }
    }

    /**
     * [전장 바닥 설정 기능 - 정사각형 버전]
     * 기반암 바닥을 사각형으로 깔고 위아래를 청소한 뒤, 중앙에 에메랄드를 설치합니다.
     */
    public void generateSquareFloor(Location center, int targetY) {
        // [추가] 설정 확인
        if (!config.battle_map)
            return;

        this.gameCenter = center;
        World world = center.getWorld();
        if (world == null)
            return;

        // 1. 범위 계산 (map_size가 75라면 중심에서 양옆으로 37칸씩)
        int halfSize = config.map_size / 2;

        int cx = center.getBlockX();
        int cz = center.getBlockZ();

        // [추가] 만약 기존에 맵 생성 중인 태스크가 있다면 취소합니다.
        if (generateTask != null) {
            generateTask.cancel();
        }

        // 그냥 new BukkitRunnable() 하지 않고, generateTask 변수에 담습니다.
        // 그래야 stopTasks()가 얘를 멈출 수 있습니다.
        generateTask = new BukkitRunnable() {
            // 시작 지점: 중심에서 -halfSize 만큼 떨어진 곳
            int x = cx - halfSize;

            @Override
            public void run() {
                // [추가] 시작 전 기존 마커 제거 (첫 실행 시)
                if (x == cx - halfSize) {
                    removeCenterMarker();
                }

                // 서버 렉 방지를 위해 한 번에 20줄씩 공사
                for (int i = 0; i < 20; i++) {
                    // 모든 X축 범위 공사가 끝났다면 (중심 + halfSize를 넘었을 때)
                    if (x > cx + halfSize) {
                        // 2. [완성] 기반암 작업 완료 후 중앙에 에메랄드 설치
                        center.getBlock().setType(Material.EMERALD_BLOCK);
                        // 아이템, 몹 다 정리.
                        clearManager.allCear();

                        // 플레이어들을 에메랄드 위로 소환
                        if (config.spawn_tf) {
                            Bukkit.getOnlinePlayers().forEach(p -> p.teleport(center.clone().add(0.5, 1.0, 0.5)));
                        }

                        // [추가] 맵 생성 완료 후 중앙 마커 생성 (텍스트 디스플레이 빛 기둥)
                        createCenterMarker(center.clone().add(0.5, 3.0, 0.5)); // 바닥보다 좀 더 위에

                        this.cancel();
                        return;
                    }

                    // Z축 공사
                    for (int z = cz - halfSize; z <= cz + halfSize; z++) {
                        // [추가] 해당 (x, z) 좌표의 무작위 지형 높이 및 블록 결정
                        int randomHeight = 0;
                        Material randomMaterial = Material.GRASS_BLOCK;

                        if (config.random_map) {
                            // [추가] 중앙 주변 8*8 (반경 4) 구역은 블록 생성 금지
                            boolean isSafeZone = Math.abs(x - cx) < 4 && Math.abs(z - cz) < 4;

                            if (!isSafeZone) {
                                // [수정] 웅덩이 발생 로직을 일반 블록 확률(10%)과 분리하여 독립적으로 처리합니다.
                                // 웅덩이는 흐름 방지를 위해 16x16 격자 기반 유지 권장
                                int gridX = x >> 4;
                                int gridZ = z >> 4;
                                java.util.Random cellRand = new java.util.Random(
                                        gridX * 341873128712L + gridZ * 132897987541L + world.getSeed());

                                double poolProb = cellRand.nextDouble();
                                boolean isPartOfPool = false;

                                if (poolProb < 0.03) { // 전체의 3% 확률로 웅덩이 후보 발생
                                    int poolX = (gridX << 4) + cellRand.nextInt(2, 12);
                                    int poolZ = (gridZ << 4) + cellRand.nextInt(2, 12);
                                    boolean isLava = cellRand.nextDouble() < 0.33; // 1% 용암 (3% 중 1/3)

                                    // 3x3 액체 구역 (반드시 3*3 형태로 출력)
                                    if (x >= poolX && x < poolX + 3 && z >= poolZ && z < poolZ + 3) {
                                        randomHeight = 1;
                                        randomMaterial = isLava ? Material.LAVA : Material.WATER;
                                        isPartOfPool = true;
                                    }
                                    // 액체의 테두리 (흐름 방지를 위한 흙블럭)
                                    else if (x >= poolX - 1 && x < poolX + 4 && z >= poolZ - 1 && z < poolZ + 4) {
                                        randomHeight = 1;
                                        randomMaterial = Material.DIRT;
                                        isPartOfPool = true;
                                    }
                                }

                                // 웅덩이 구역이 아닐 때만 일반 블록 생성 로직 적용
                                if (!isPartOfPool) {
                                    // [수정] 기본적으로 1층(배드락 위)은 무조건 잔디로 채움 (높이 1)
                                    randomHeight = 1;
                                    randomMaterial = Material.GRASS_BLOCK;

                                    // [수정] 1.5% 확률로 2층(장애물) 생성 (기존 로직: 10% * 15% = 1.5%)
                                    double rand = java.util.concurrent.ThreadLocalRandom.current().nextDouble();
                                    if (rand < 0.015) {
                                        randomHeight = 2;

                                        // 2층 재질은 랜덤하게 결정 (기존 로직 유지)
                                        double matRand = java.util.concurrent.ThreadLocalRandom.current().nextDouble();
                                        if (matRand < 0.7)
                                            randomMaterial = Material.GRASS_BLOCK; // 2층도 잔디일 수 있음
                                        else if (matRand < 0.85)
                                            randomMaterial = Material.DIRT;
                                        else if (matRand < 0.95)
                                            randomMaterial = Material.COBBLESTONE;
                                        else
                                            randomMaterial = Material.SAND;
                                    }
                                }
                            }
                        }

                        // 3. [청소 및 건설] 해당 좌표의 높이 정리
                        // [복구] 사용자의 요청에 따라 월드 전체 높이(Min~Max)를 모두 초기화합니다.
                        int minHeight = world.getMinHeight();
                        int maxHeight = world.getMaxHeight();

                        for (int y = minHeight; y < maxHeight; y++) {
                            Block b = world.getBlockAt(x, y, z);

                            if (y == targetY) {
                                // 기반암 설치
                                b.setType(Material.BEDROCK, false);

                                // 중앙 1칸만 에메랄드로 유지
                                if (x == cx && z == cz) {
                                    b.setType(Material.EMERALD_BLOCK, false);
                                }
                            } else if (config.random_map && y > targetY && y <= targetY + randomHeight) {
                                // [수정] 무작위 지형 생성
                                if (y == targetY + 1) {
                                    // 1층은 웅덩이(Liquid)가 아니면 무조건 잔디 블럭
                                    // (웅덩이 로직에서는 randomMaterial이 Water/Lava로 설정됨 -> 이 경우엔 그대로 유지해야 함)
                                    // 웅덩이는 randomHeight가 1이므로,
                                    // 풀밭 로직에서 randomHeight=2가 걸렸을 때, randomMaterial이 Stone이어도 1층은 Grass여야 함.
                                    if (randomMaterial == Material.WATER || randomMaterial == Material.LAVA
                                            || randomMaterial == Material.DIRT) {
                                        // 웅덩이 관련 재질이면 그대로 적용 (DIRT는 웅덩이 테두리일 수 있음)
                                        // 하지만 위의 로직에서 '일반 구역'은 1.5% 확률로 2층이 되고 재질이 바뀜.
                                        // 일반 구역의 1층(GRASS_BLOCK)과 2층(COBBLESTONE)을 분리해야 함.
                                        b.setType(randomMaterial, false);
                                    } else {
                                        // 일반 구역 1층은 무조건 잔디
                                        b.setType(Material.GRASS_BLOCK, false);
                                    }
                                } else {
                                    // 2층 이상은 랜덤 재질 적용
                                    b.setType(randomMaterial, false);
                                }
                            } else {
                                // 기반암 및 지형 외 구역은 공기로 싹 비우기 (이전 판 블록 제거)
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
     * [전장 바닥 제거 기능 - 알렉스 능력 전용]
     * 생성된 기반암 바닥을 모두 공기(AIR)로 제거합니다.
     */
    public void removeSquareFloor() {
        // [추가] 설정 확인 (야생맵이면 지우면 큰일남)
        if (!config.battle_map)
            return;

        if (this.gameCenter == null)
            return;
        World world = gameCenter.getWorld();
        if (world == null)
            return;

        int targetY = gameCenter.getBlockY() - 1;
        int halfSize = config.map_size / 2;
        int cx = gameCenter.getBlockX();
        int cz = gameCenter.getBlockZ();

        // 한 번에 제거 (비동기 말고 동기로 처리해도 되지만, 양이 많으면 렉 유발 가능성 있음 -> 일단 루프 돌려서 제거)
        // 알렉스 능력은 게임 중 발동되므로 Task로 나누는게 안전함.
        new BukkitRunnable() {
            int x = cx - halfSize;

            @Override
            public void run() {
                for (int i = 0; i < 20; i++) {
                    if (x > cx + halfSize) {
                        this.cancel();
                        return;
                    }

                    for (int z = cz - halfSize; z <= cz + halfSize; z++) {
                        Block b = world.getBlockAt(x, targetY, z);
                        if (b.getType() == Material.BEDROCK) {
                            b.setType(Material.AIR, false);
                        }
                    }
                    x++;
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /**
     * [추가] 전장 바닥 전체 재질 변경 (룰렛 연출용)
     */
    public void setArenaFloor(Material mat) {
        // [추가] 설정 확인 (야생맵이면 바닥 바꾸면 안됨)
        if (!config.battle_map)
            return;

        if (this.gameCenter == null || mat == null)
            return;
        World world = gameCenter.getWorld();
        if (world == null)
            return;

        int targetY = gameCenter.getBlockY() - 1;
        int halfSize = config.map_size / 2;
        int cx = gameCenter.getBlockX();
        int cz = gameCenter.getBlockZ();

        // 룰렛은 0.3초마다 바뀌므로, 즉시 변경해야 연출이 자연스럽습니다.
        // 다만 범위가 크면(75x75) 렉이 걸릴 수 있으니 주의해야 합니다.
        // 여기서는 루프를 사용하여 최대한 빠르게 처리합니다.
        for (int x = cx - halfSize; x <= cx + halfSize; x++) {
            for (int z = cz - halfSize; z <= cz + halfSize; z++) {
                // 기존 바닥이 공기가 아닐 때만 변경 (이미 바닥이 있는 곳만)
                Block b = world.getBlockAt(x, targetY, z);
                if (b.getType() != Material.AIR) {
                    b.setType(mat, false); // false = 물리 업데이트 안 함 (렉 감소)
                }
            }
        }
    }

    /**
     * [추가] 중앙 블록 복구 (에메랄드)
     */
    public void resetCenterBlock() {
        // [추가] 설정 확인 (삭제: 야생맵이어도 중앙 에메랄드는 복구함)
        // if (!config.battle_map)
        // return;

        if (this.gameCenter == null)
            return;
        this.gameCenter.getBlock().setType(Material.EMERALD_BLOCK);
    }

    /**
     * 자기장 좁아지기 및 최종 결전 시스템 (메시지 -> 7초 텔포 -> 1분 결투 -> 종료)
     */
    public void startBorderShrink() {
        if (gameCenter == null)
            return;

        // [추가] 테스트 모드일 때는 자기장이 줄어들지 않습니다.
        if (config.test) {
            Bukkit.broadcastMessage("§e[TEST] §f테스트 모드이므로 자기장이 줄어들지 않습니다.");
            return;
        }

        if (borderShrinkTask != null) {
            borderShrinkTask.cancel();
        }

        WorldBorder border = gameCenter.getWorld().getWorldBorder();
        double currentSize = border.getSize();
        double targetSize = 5.0;

        // [수정] 서서히 줄어드는 로직 (1초에 1칸씩)
        // 기존: 3초에 2칸씩 (약 0.66칸/초) -> 변경: 1초에 1칸씩 (1.0칸/초)
        // 부드럽게 줄어들도록 API 사용
        if (currentSize > targetSize) {
            long durationSeconds = (long) (currentSize - targetSize); // 1 block per second
            border.setSize(targetSize, durationSeconds);
            Bukkit.broadcastMessage("§c[!] §f자기장이 " + durationSeconds + "초 동안 서서히 줄어듭니다.");
        }

        borderShrinkTask = new BukkitRunnable() {
            @Override
            public void run() {

                // 1. [게임 종료 체크]
                if (!gm.isRunning()) {
                    // 게임 끝났으면 보더 줄어드는 것도 멈춰야 함 (현재 크기로 고정)
                    border.setSize(border.getSize());
                    this.cancel();
                    return;
                }

                double size = border.getSize();

                // 2. 자기장 크기가 5.5 이하가 되면 (최소 크기 도달 근접)
                if (size <= 5.5) {
                    border.setSize(5.0); // 확실하게 5로 고정
                    this.cancel(); // 감시 태스크 종료

                    // 여기서부터 '자기장 줄이기' 역할은 끝났으니, '메시지 출력' 역할을 borderShrinkTask에 맡깁니다.
                    startFinalBattleMessages();
                    return;
                }

                // [추가] 자기장이 줄어들 때 밖의 생명체들 확실히 제거 (요청사항 반영)
                // 보더가 움직이고 있으므로 매초 확인
                double currentRadius = size / 2.0;
                Location center = border.getCenter();
                for (org.bukkit.entity.LivingEntity entity : gameCenter.getWorld().getLivingEntities()) {
                    if (entity instanceof Player)
                        continue;
                    // ArmorStand 등 살아있는 걸로 취급되는 비생물체 제외하고 싶으면 추가 필터링
                    if (!entity.isValid())
                        continue;

                    Location loc = entity.getLocation();
                    // 오차범위 0.5 정도 둠
                    if (Math.abs(loc.getX() - center.getX()) > currentRadius + 0.5
                            || Math.abs(loc.getZ() - center.getZ()) > currentRadius + 0.5) {
                        entity.setHealth(0);
                        entity.remove();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1초마다 체크
    }

    /**
     * [분리] 최종 전투 알림 메시지 태스크
     */
    private void startFinalBattleMessages() {
        borderShrinkTask = new BukkitRunnable() {
            String[] messages = {
                    "§c[!] §f자기장이 최대로 줄어들었습니다.",
                    "§c[!] §e1분 간 §f최종 전투를 시작합니다.",
                    "§c[!] §e1분간 승자가 정해지지 않는 경우,",
                    "§e    자동으로 라운드가 종료됩니다.",
                    "§c[!] 잠시 후 시작합니다.",
                    "§a최종 전장 활성화 카운트 다운 시작."
            };
            int index = 0;

            @Override
            public void run() {
                if (!gm.isRunning()) {
                    this.cancel();
                    return;
                }

                if (index < messages.length) {
                    plugin.getServer().broadcastMessage(messages[index]);
                    index++;
                } else {
                    this.cancel();
                    startTeleportCountdown(gm); // 다음 단계로
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * [단계 2] 7초 카운트다운 후 텔레포트
     */
    private void startTeleportCountdown(GameManager gm) {
        // 이 타이머도 borderShrinkTask에 덮어씌웁니다.
        borderShrinkTask = new BukkitRunnable() {
            int timeLeft = 7;

            @Override
            public void run() {
                if (!gm.isRunning()) {
                    this.cancel();
                    return;
                }

                if (timeLeft > 0) {
                    plugin.getServer().broadcastMessage("§e" + timeLeft);
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 2f);
                    }
                    timeLeft--;
                } else {
                    plugin.getServer().broadcastMessage("§c⚔ §l최종 전장 활성화 §c⚔");

                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        p.teleport(gameCenter.clone().add(0, 1, 0));
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    }

                    this.cancel();
                    startFinalBattleTimer(gm); // 다음 단계로
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * [단계 3] 1분 제한시간 체크 및 강제 종료
     */
    private void startFinalBattleTimer(GameManager gm) {
        // 마지막 전투 타이머도 borderShrinkTask가 관리하게 합니다.
        borderShrinkTask = new BukkitRunnable() {
            int battleTime = 60;

            @Override
            public void run() {
                if (!gm.isRunning()) {
                    this.cancel();
                    return;
                }

                if (battleTime <= 5 && battleTime > 0) {
                    plugin.getServer().broadcastMessage("§c[!] 최종 전투 종료까지 §e" + battleTime + "초");
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 2f);
                    }
                }

                if (battleTime <= 0) {
                    this.cancel();
                    if (gm.isRunning()) {
                        plugin.getServer().broadcastMessage("§c[!] §l최종 전투가 종료되었습니다.");
                        // 1. 승자들을 담을 '명단(List)'을 만듭니다.
                        java.util.List<Player> survivors = new java.util.ArrayList<>();
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            // 관전 모드가 아닌 플레이어만 생존자로 간주하여 명단에 넣습니다.
                            if (p.getGameMode() != GameMode.SPECTATOR) {
                                survivors.add(p);
                            }
                        }
                        // 2. 찾아낸 생존자 명단을 들고 GameManager의 endRound를 호출합니다.
                        // (주의: GameManager의 endRound 함수가 public 이어야 합니다!)
                        // [추가] 테스트 모드일 때는 1분이 지나도 라운드를 끝내지 않습니다.
                        if (!me.user.moc.config.ConfigManager.getInstance().test) {
                            gm.endRound(survivors);
                        } else {
                            plugin.getServer().broadcastMessage("§e[TEST] §f테스트 모드이므로 라운드가 종료되지 않습니다.");
                        }
                    }
                }
                battleTime--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * 자기장 밖 대미지 처리를 시작합니다.
     */
    public void startBorderDamage() {
        if (gameCenter == null)
            return;
        World world = gameCenter.getWorld();
        if (world == null)
            return;

        WorldBorder border = world.getWorldBorder();

        // 1. [핵심] 마인크래프트 자체 장벽 대미지 설정 (가장 정확함)
        // [수정] 안전거리(버퍼)를 2.0칸으로 늘려서 억울한 죽음 방지 (텔포 오차 등)
        border.setDamageBuffer(2.0);
        // 초당 대미지 설정 (한 칸 나갈 때마다 추가 대미지)
        border.setDamageAmount(2.0); // 3.0 -> 1.0으로 하향 조정 (즉사 방지)

        // 2. [메시지 알림 전용] 0.25초마다 검사하여 즉각 경고 메시지 전송
        if (borderDamageTask != null)
            borderDamageTask.cancel(); // 중복 방지

        borderDamageTask = new BukkitRunnable() {
            int tickCount = 0; // [최적화] 엔티티 청소 주기 관리용 카운터
            int graceSeconds = 5; // [추가] 초기 5초간 대미지 무시 (유예 시간)

            @Override
            public void run() {
                // 게임이 끝났으면 태스크 종료 (중요)
                if (!gm.isRunning()) {
                    this.cancel();
                    return;
                }

                // 초기 유예 시간 체크
                if (graceSeconds > 0) {
                    if (tickCount % 4 == 0) { // 1초마다 감소
                        graceSeconds--;
                    }
                    tickCount++;
                    return; // 아직은 대미지 로직 실행 안 함
                }

                double size = border.getSize() / 2.0;
                Location center = border.getCenter();

                // 1. 플레이어 대미지 및 경고 처리 (기존 로직 유지 - 0.25초마다 실행)
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().equals(world))
                        continue;

                    // 관전자는 무시
                    if (p.getGameMode() == GameMode.SPECTATOR)
                        continue;

                    Location loc = p.getLocation();
                    // 3. [수학적 계산] 장벽 중심에서 플레이어의 거리가 장벽 반지름보다 큰지 확인
                    if (Math.abs(loc.getX() - center.getX()) > size || Math.abs(loc.getZ() - center.getZ()) > size) {
                        // 액션바에 경고 표시 (메시지 도배 방지)
                        p.sendActionBar(net.kyori.adventure.text.Component.text("!!! 자기장 구역 밖입니다 !!!")
                                .color(net.kyori.adventure.text.format.NamedTextColor.RED)
                                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));

                        // [추가] 2초당 1뎀씩 수동 부여 (버퍼랑 별개로 확실하게 아프게 하기 위해)
                        if (tickCount % 8 == 0) {
                            p.damage(1.0);
                        }
                    }
                }

                // 2. [추가] 자기장 밖 생명체(플레이어 제외) 삭제 처리 (1초마다 실행)
                if (tickCount++ % 4 == 0) {
                    int removedCount = 0;
                    // [중요] ConcurrentModificationException 방지를 위해 리스트 복사본 사용
                    List<org.bukkit.entity.LivingEntity> entities = new ArrayList<>(world.getLivingEntities());

                    for (org.bukkit.entity.LivingEntity entity : entities) {
                        // 플레이어는 건드리지 않음
                        if (entity instanceof Player)
                            continue;
                        if (!entity.isValid())
                            continue; // 이미 죽거나 소멸된 엔티티 패스

                        // [추가] 보스몹이나 중요 엔티티는 예외 처리? (일단 보류)

                        Location loc = entity.getLocation();
                        // 자기장 밖인지 검사 (약간의 여유 0.5칸 줌)
                        if (Math.abs(loc.getX() - center.getX()) > size + 0.5
                                || Math.abs(loc.getZ() - center.getZ()) > size + 0.5) {
                            // [수정] 확실한 제거를 위해 체력을 0으로 만들어 '사망' 처리 후 삭제
                            entity.setHealth(0);
                            entity.remove();
                            removedCount++;
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 5L); // 5틱(0.25초)마다 검사해서 반응 속도 4배 향상!
    }

    /**
     * 자기장 멈추기 및 모든 진행중인 타이머 강제 종료
     */
    public void stopTasks() {
        // [▼▼▼ 변경됨: 모든 종류의 타이머를 확실하게 취소 ▼▼▼]

        // 1. 자기장 줄어들기 + (메시지출력/텔포카운트/전투타이머 포함) 취소
        if (borderShrinkTask != null) {
            borderShrinkTask.cancel();
            borderShrinkTask = null;
        }

        // 2. 대미지 체크 취소
        if (borderDamageTask != null) {
            borderDamageTask.cancel();
            borderDamageTask = null;
        }

        // 3. 맵 공사(블록 설치) 중이었다면 그것도 취소! (중요)
        if (generateTask != null) {
            generateTask.cancel();
            generateTask = null;
        }

        // 4. [추가] 중앙 마커(텍스트 디스플레이) 제거
        removeCenterMarker();
    }

    /**
     * [추가] 중앙 마커(텍스트 디스플레이) 제거
     */
    public void removeCenterMarker() {
        if (centerMarker != null && !centerMarker.isDead()) {
            centerMarker.remove();
            centerMarker = null;
        }
    }

    /**
     * [추가] 중앙 마커(텍스트 디스플레이) 생성 - 빛 기둥 효과
     */
    private void createCenterMarker(Location loc) {
        removeCenterMarker(); // 혹시 모를 중복 제거

        if (loc.getWorld() == null)
            return;

        centerMarker = loc.getWorld().spawn(loc, org.bukkit.entity.TextDisplay.class, display -> {
            // 텍스트 설정 (위아래좌우 공백을 넣어 기둥처럼 보이게 함)
            display.setText("\n\n\n\n§a§l     CENTER     \n\n\n\n");

            // 항상 플레이어를 바라보도록 설정
            display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);

            // 배경색을 반투명한 초록색으로 설정하여 "빛 기둥" 느낌 연출
            display.setBackgroundColor(org.bukkit.Color.fromARGB(100, 0, 255, 0));

            // 벽 뒤에서도 보이게 설정 (위치 식별 용이)
            display.setSeeThrough(true);

            // 그림자 없음
            display.setShadowed(false);

            // 발광 효과 (멀리서도 테두리 보임)
            display.setGlowing(true);

            // 크기 조정 (잘 보이게) - Transformation 클래스 호환성 문제로 잠시 제거 (기본 크기 사용)
            // display.setTransformation(new org.bukkit.util.transformation.Transformation(
            // new org.joml.Vector3f(0f, 0f, 0f),
            // new org.joml.AxisAngle4f(),
            // new org.joml.Vector3f(2f, 2f, 2f), // 2배 크기
            // new org.joml.AxisAngle4f()));
        });
    }
}