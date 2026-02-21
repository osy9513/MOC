package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import me.user.moc.MocPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * [능력 코드: 057]
 * 이름: 이슬비 (클로저스)
 * 설명: 전방의 특정 좌표를 조준한 후 지하철을 소환하여 초고속으로 낙하시켜 광역 피해를 입힌다.
 */
public class LeeSeulbi extends Ability {

    // 지하철 소환 상태 Enum
    private enum SubwayState {
        NONE, SUMMONING, SUMMONED, DROPPED
    }

    // 플레이어별 상태 관리
    private final Map<UUID, SubwayState> states = new HashMap<>();

    // 플레이어별 소환된 블록 디스플레이 객체들 보관
    private final Map<UUID, List<BlockDisplay>> subwayBlocks = new HashMap<>();

    // 추적 스케줄러 보관 (소환 후 등 뒤를 따라다니도록 하는 타이머)
    private final Map<UUID, BukkitTask> trackingTasks = new HashMap<>();

    // 데미지 틱 타이머 보관 (낙하 후 5초 대기 타이머 관련 처리를 위함)
    private final Map<UUID, BukkitTask> dropTasks = new HashMap<>();

    public LeeSeulbi(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "057";
    }

    @Override
    public String getName() {
        return "이슬비";
    }

    @Override
    public List<String> getDescription() {
        List<String> list = new ArrayList<>();
        list.add("§a전투 ● 이슬비(클로저스)");
        list.add(" ");
        list.add("§f전방의 특정 좌표를 조준한 후 지하철을 소환하여 초고속으로 낙하시켜 광역 피해를 입힌다.");
        return list;
    }

    @Override
    public void giveItem(Player p) {

        // 상태 초기화
        states.put(p.getUniqueId(), SubwayState.NONE);
        cleanupSubway(p.getUniqueId());
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§a전투 ● 이슬비(클로저스)");
        p.sendMessage("§f전방의 특정 좌표를 조준한 후 지하철을 소환하여 초고속으로 낙하시켜 광역 피해를 입힌다.");
        p.sendMessage(" ");
        p.sendMessage("§f[맨손 쉬프트 좌클릭] 하늘에 지하철을 3초에 걸쳐 소환합니다.");
        p.sendMessage("§f[소환 후 맨손 쉬프트 좌클릭] 8블럭 앞에 지하철을 수직 낙하시켜 2초 동안 반경 내 30 데미지를 주고 5초 후 사라집니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 20초 (낙하 직후 계산)");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        cleanupSubway(p.getUniqueId());
    }

    @Override
    public void onGameEnd(Player p) {
        super.onGameEnd(p);
        cleanupSubway(p.getUniqueId());
    }

    @Override
    public void reset() {
        super.reset();
        for (UUID uuid : new ArrayList<>(states.keySet())) {
            cleanupSubway(uuid);
        }
        states.clear();
    }

    // 소환된 지하철 파츠 완전 제거 및 타이머 취소
    private void cleanupSubway(UUID uuid) {
        // 엔티티 삭제
        if (subwayBlocks.containsKey(uuid)) {
            for (BlockDisplay bd : subwayBlocks.get(uuid)) {
                if (bd != null && bd.isValid()) {
                    bd.remove();
                }
            }
            subwayBlocks.remove(uuid);
        }
        // 추적 타이머 취소
        if (trackingTasks.containsKey(uuid)) {
            BukkitTask task = trackingTasks.get(uuid);
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
            trackingTasks.remove(uuid);
        }
        // 데미지 타이머 취소
        if (dropTasks.containsKey(uuid)) {
            BukkitTask task = dropTasks.get(uuid);
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
            dropTasks.remove(uuid);
        }
        states.put(uuid, SubwayState.NONE);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        if (!me.user.moc.ability.AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
            return;
        }

        Action action = e.getAction();

        // 맨손 확인
        if (p.getInventory().getItemInMainHand().getType() != Material.AIR) {
            return;
        }

        // 쉬프트 좌클릭
        if (p.isSneaking() && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) {
            SubwayState state = states.getOrDefault(p.getUniqueId(), SubwayState.NONE);

            if (state == SubwayState.NONE) {
                // 1. 소환 시작
                startSummoning(p);
            } else if (state == SubwayState.SUMMONED) {
                // 2. 투척 시작
                if (!checkCooldown(p))
                    return;
                startDrop(p);
            }
        }
    }

    private void startSummoning(Player p) {
        if (!checkCooldown(p))
            return; // 쿨타임 중에 소환 금지 (투척 후 쿨타임 돎)

        states.put(p.getUniqueId(), SubwayState.SUMMONING);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_MINECART_RIDING, 1.0f, 0.5f);
        plugin.getServer().broadcastMessage("§c이슬비 : 이게, 진짜 지옥철이야.");

        Location spawnLoc = p.getEyeLocation().add(p.getLocation().getDirection().multiply(2)); // 눈 앞 2블록
        float yaw = p.getLocation().getYaw();
        List<BlockDisplay> train = new ArrayList<>();

        // 지하철 3량 생성 (앞에서부터 뒤로 Z 오프셋)
        // 1량 (앞)
        train.addAll(createCar(spawnLoc, 0, yaw));
        // 연결부 1
        train.add(
                createPart(spawnLoc, new Vector3f(3f, 3f, 3f), new Vector3f(0f, 0f, -6.5f), Material.COAL_BLOCK, yaw));
        // 2량 (중간)
        train.addAll(createCar(spawnLoc, -13, yaw));
        // 연결부 2
        train.add(
                createPart(spawnLoc, new Vector3f(3f, 3f, 3f), new Vector3f(0f, 0f, -19.5f), Material.COAL_BLOCK, yaw));
        // 3량 (뒤)
        train.addAll(createCar(spawnLoc, -26, yaw));

        subwayBlocks.put(p.getUniqueId(), train);

        // 3초 뒤 소환 완료 상태로 변경
        new BukkitRunnable() {
            @Override
            public void run() {
                if (states.getOrDefault(p.getUniqueId(), SubwayState.NONE) == SubwayState.SUMMONING) {
                    states.put(p.getUniqueId(), SubwayState.SUMMONED);
                    p.sendMessage("§a[!] 지하철 소환 완료. 다시 쉬프트+좌클릭하여 투척하세요!");
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.5f);
                }
            }
        }.runTaskLater(plugin, 60L);

        // 등 뒤 상공을 따라다니는 추적 스케줄러 가동
        startTrackingTask(p, train);
    }

    private List<BlockDisplay> createCar(Location origin, float zOffset, float yaw) {
        List<BlockDisplay> parts = new ArrayList<>();
        // 메인 몸체 (철 블록, 6x6x10)
        parts.add(
                createPart(origin, new Vector3f(6f, 6f, 10f), new Vector3f(0f, 0f, zOffset), Material.IRON_BLOCK, yaw));

        // 좌우 창문 (유리, 두께 6.2로 양옆으로 튀어나오게, z축으로 3개씩 배치)
        for (float wZ : new float[] { -3f, 0f, 3f }) {
            parts.add(createPart(origin, new Vector3f(6.2f, 2f, 2f), new Vector3f(0f, 1f, zOffset + wZ),
                    Material.LIGHT_BLUE_STAINED_GLASS, yaw));
        }
        return parts;
    }

    private BlockDisplay createPart(Location origin, Vector3f size, Vector3f offset, Material mat, float yaw) {
        BlockDisplay bd = origin.getWorld().spawn(origin, BlockDisplay.class, entity -> {
            entity.setBlock(Bukkit.createBlockData(mat));

            // 초기 아주 작게 시작 (0.01배)
            Transformation initT = new Transformation(
                    new Vector3f(offset).mul(0.01f).sub(new Vector3f(size).mul(0.005f)), // center offset
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(size).mul(0.01f),
                    new AxisAngle4f(0, 0, 0, 1));
            entity.setTransformation(initT);

            // 빠른 틱 보간 사용
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(1);
        });

        // 60틱 동안 고죠 사토루처럼 부드럽게 커지는 틱 단위 스케줄러 가동
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!bd.isValid() || ticks > 60) {
                    this.cancel();
                    return;
                }

                float scale = Math.max(0.01f, (float) ticks / 60.0f);

                bd.setInterpolationDelay(0);
                bd.setInterpolationDuration(1);
                Transformation currentT = new Transformation(
                        new Vector3f(offset).mul(scale).sub(new Vector3f(size).mul(scale * 0.5f)),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(size).mul(scale),
                        new AxisAngle4f(0, 0, 0, 1));
                bd.setTransformation(currentT);

                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);

        bd.setCustomName("SubwayPart"); // 메타데이터용

        return bd;
    }

    private void startTrackingTask(Player p, List<BlockDisplay> train) {
        UUID uuid = p.getUniqueId();
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (states.getOrDefault(uuid, SubwayState.NONE) == SubwayState.DROPPED
                        || states.getOrDefault(uuid, SubwayState.NONE) == SubwayState.NONE) {
                    this.cancel();
                    return;
                }

                // 플레이어 등 뒤 위쪽 목표 좌표
                Location targetLoc = p.getLocation().clone();
                targetLoc.add(targetLoc.getDirection().setY(0).normalize().multiply(-4)); // 뒤로 4칸
                targetLoc.add(0, 14, 0); // 위로 14칸 (기존 6칸의 2배 이상 높임)

                // 지하철 몸체 회전 (Yaw 적용, Pitch는 0 유지)
                targetLoc.setPitch(0);

                for (BlockDisplay bd : train) {
                    if (bd.isValid()) {
                        bd.teleport(targetLoc);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // 1틱마다 부드럽게 추적

        trackingTasks.put(uuid, task);
    }

    private void startDrop(Player p) {
        states.put(p.getUniqueId(), SubwayState.DROPPED);

        // 쿨타임 즉시 등록 (20초)
        setCooldown(p, 20);

        List<BlockDisplay> train = subwayBlocks.get(p.getUniqueId());
        if (train == null || train.isEmpty())
            return;

        // 추적 타이머 종료
        if (trackingTasks.containsKey(p.getUniqueId())) {
            trackingTasks.get(p.getUniqueId()).cancel();
            trackingTasks.remove(p.getUniqueId());
        }

        // 1. 목표 지점 계산: 이슬비 전방 8블록 앞
        Location targetLoc = p.getLocation().clone();
        targetLoc.add(targetLoc.getDirection().setY(0).normalize().multiply(8));
        targetLoc.setY(targetLoc.getWorld().getHighestBlockYAt(targetLoc));

        // 바닥에 약간 파묻히게
        targetLoc.subtract(0, 8, 0);

        // 낙하 회전 각도 (Pitch를 아래로)
        float dropPitch = 70f;
        float yaw = p.getLocation().getYaw();

        // 현재 기차 위치의 대략적인 중심 (1량 기준)
        Location currentLoc = train.get(0).getLocation();

        // 이동 벡터 및 속도 설정
        Vector dir = targetLoc.toVector().subtract(currentLoc.toVector());
        double distance = dir.length();
        Vector velocity = dir.normalize().multiply(2.5); // 틱당 2.5블럭 속도 (매우 빠름)

        // 2. 낙하 애니메이션 태스크
        BukkitTask dropTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks > distance / 2.5) {
                    // 바닥 도달
                    handleGroundImpact(p, targetLoc, train);
                    this.cancel();
                    return;
                }

                for (BlockDisplay bd : train) {
                    if (bd.isValid()) {
                        Location loc = bd.getLocation().clone().add(velocity);
                        loc.setPitch(dropPitch);
                        loc.setYaw(yaw);
                        bd.teleport(loc);
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        dropTasks.put(p.getUniqueId(), dropTask);
    }

    private void handleGroundImpact(Player p, Location impactLoc, List<BlockDisplay> train) {
        UUID uuid = p.getUniqueId();

        // 강한 폭발음
        impactLoc.getWorld().playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.5f);

        BukkitTask impactTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                // 게임 끝났거나 정리된 경우 취소
                if (states.getOrDefault(uuid, SubwayState.NONE) == SubwayState.NONE) {
                    this.cancel();
                    return;
                }

                // [1] 지면 타격 즉시 8x64x8 범위에 30 데미지 한 번에 꽂기 (단일 폭딜)
                if (ticks == 0) {
                    // 파티클
                    impactLoc.getWorld().spawnParticle(Particle.EXPLOSION, impactLoc.clone().add(0, 10, 0), 10, 4, 10,
                            4, 0.1);

                    for (Entity ent : impactLoc.getWorld().getNearbyEntities(impactLoc.clone().add(0, 10, 0), 4, 32,
                            4)) {
                        if (ent instanceof LivingEntity target && target != p && !(target instanceof Player targetPlayer
                                && targetPlayer.getGameMode() == org.bukkit.GameMode.SPECTATOR)) {
                            target.damage(30.0, p);
                            target.setFireTicks(40);
                        }
                    }
                }

                // [2] 0~100틱 (5초) 내내 가까이 오면 밀쳐내며 데미지 (10틱 마다 체크결과 5데미지)
                if (ticks <= 100 && ticks % 10 == 0) {
                    for (Entity ent : impactLoc.getWorld().getNearbyEntities(impactLoc.clone().add(0, 10, 0), 6, 20,
                            6)) {
                        if (ent instanceof LivingEntity target && target != p && !(target instanceof Player targetPlayer
                                && targetPlayer.getGameMode() == org.bukkit.GameMode.SPECTATOR)) {
                            target.damage(5.0, p);
                            // 바깥으로 튕겨내는 넉백
                            Vector kb = target.getLocation().toVector().subtract(impactLoc.toVector()).normalize()
                                    .multiply(1.2).setY(0.5);
                            target.setVelocity(kb);
                        }
                    }
                }

                // [3] 100틱(5초) 도달 시 연기와 함께 소멸
                if (ticks >= 100) {
                    impactLoc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, impactLoc.clone().add(0, 15, 0),
                            100, 5, 10, 5, 0.05);
                    impactLoc.getWorld().playSound(impactLoc, Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.0f);
                    cleanupSubway(uuid);
                    this.cancel();
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        dropTasks.put(uuid, impactTask);
    }
}
