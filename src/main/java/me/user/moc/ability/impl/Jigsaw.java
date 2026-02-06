package me.user.moc.ability.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;

public class Jigsaw extends Ability {

    // [상태 관리] 직쏘의 게임 정보를 관리하는 내부 클래스
    private class GameInfo {
        Player jigsaw; // 시전자
        LivingEntity target; // 피해자
        int requiredBeef; // 요구하는 소고기 개수
        boolean isFinished; // 게임 종료 여부 (성공/실패 결정됨)

        // 톱날 엔티티 (시각 효과)
        BlockDisplay sawTop;
        BlockDisplay sawBottom;

        // 관리용 태스크
        BukkitTask timerTask; // 5초 제한시간
        BukkitTask rotationTask; // 톱날 회전 및 시선 고정
        BukkitTask grindTask; // 처형(갈아버리기) 태스크

        public GameInfo(Player jigsaw, LivingEntity target) {
            this.jigsaw = jigsaw;
            this.target = target;
            this.isFinished = false;
        }

        // 게임 끝날 때 정리 (톱날 삭제 등)
        public void cleanup() {
            if (sawTop != null && sawTop.isValid())
                sawTop.remove();
            if (sawBottom != null && sawBottom.isValid())
                sawBottom.remove();

            if (timerTask != null && !timerTask.isCancelled())
                timerTask.cancel();
            if (rotationTask != null && !rotationTask.isCancelled())
                rotationTask.cancel();
            if (grindTask != null && !grindTask.isCancelled())
                grindTask.cancel();
        }
    }

    // 플레이어별 진행 중인 게임 저장 (키: 직쏘 UUID)
    private final Map<UUID, GameInfo> activeGames = new HashMap<>();
    // 타겟으로 게임 찾기 위한 역참조 맵 (키: 타겟 UUID -> 값: 직쏘 UUID)
    private final Map<UUID, UUID> targetToJigsawMap = new HashMap<>();

    public Jigsaw(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "049";
    }

    @Override
    public String getName() {
        return "직쏘";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c복합 ● 직쏘(쏘우)",
                "§f게임을 시작합니다.");
    }

    @Override
    public void giveItem(Player p) {
        ItemStack item = new ItemStack(Material.STONECUTTER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c게임 시작");
        item.setItemMeta(meta);
        p.getInventory().addItem(item);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c복합 ● 직쏘(쏘우)");
        p.sendMessage(" ");
        p.sendMessage("§f'게임 시작'으로 생명체를 좌클릭하여 게임을 시작합니다.");
        p.sendMessage("§f게임 중에는 이동할 수 없으며 서로를 바라봅니다.");
        p.sendMessage(" ");
        p.sendMessage("§f[게임 룰] 5초 안에 상대가 랜덤한 개수의 구운 소고기를 '한 번에' 버려야 합니다.");
        p.sendMessage("§a성공 시: §2모두 풀려납니다.");
        p.sendMessage("§c실패 시: §4상대는 톱날에 갈려 죽습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 25초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 게임 시작");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        // 내가 진행 중인 게임 정리
        if (activeGames.containsKey(p.getUniqueId())) {
            GameInfo info = activeGames.get(p.getUniqueId());
            targetToJigsawMap.remove(info.target.getUniqueId()); // 역참조 제거
            info.cleanup();
            activeGames.remove(p.getUniqueId());
        }
    }

    // [1] 게임 시작 트리거 (좌클릭)
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p))
            return;
        if (!(e.getEntity() instanceof LivingEntity target))
            return;

        // 능력자 체크
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 아이템 체크 (석재 절단기)
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.STONECUTTER)
            return;

        // 쿨타임 체크
        if (!checkCooldown(p)) {
            e.setCancelled(true); // 쿨타임이면 때리지도 못하게? (선택사항)
            return;
        }

        // 이미 게임 중인지 체크
        if (activeGames.containsKey(p.getUniqueId())) {
            p.sendMessage("§c이미 게임이 진행 중입니다.");
            e.setCancelled(true);
            return;
        }

        // [게임 시작]
        e.setCancelled(true); // 데미지는 주지 않음 (게임 시작 트리거)
        startGame(p, target);
        setCooldown(p, 25);
    }

    // [2] 게임 시작 로직
    private void startGame(Player p, LivingEntity target) {
        GameInfo info = new GameInfo(p, target);

        // 1. 랜덤 미션 설정 (구운 소고기 10~30개)
        info.requiredBeef = new Random().nextInt(21) + 10; // 10 ~ 30

        // 2. 메시지 출력
        p.sendMessage("§c[직쏘] §f게임을 시작하지.");
        String targetName = target.getName();
        Bukkit.broadcastMessage("§c직쏘 : " + targetName + "는 5초 안에 구운 소고기 " + info.requiredBeef + "개를 한 번에 버려라.");

        // 3. 톱날 소환 (타겟의 우측)
        spawnSaws(info);

        // 4. 반복 태스크 (시선 고정 + 톱날 회전)
        info.rotationTask = new BukkitRunnable() {
            float angle = 0;

            @Override
            public void run() {
                if (!p.isOnline() || !target.isValid()) {
                    failGame(info, "대상 소실");
                    this.cancel();
                    return;
                }

                // [시선 고정] 서로 바라보게 함
                if (!info.isFinished) { // 게임 중일 때만 고정
                    lookAt(p, target.getLocation());
                    if (target instanceof Player tPlayer) {
                        lookAt(tPlayer, p.getLocation());
                    } else {
                        // 몹인 경우 강제 회전 (텔레포트 필요)
                        lookAtEntity(target, p.getLocation());
                    }
                } else {
                    // 실패 후 처형 단계에서는 타겟만 톱날을 보게 하거나 고정 유지 (기획: 상대는 고정 안 풀림)
                    if (target instanceof Player tPlayer) {
                        // 실패 시 타겟은 계속 고정
                        lookAt(tPlayer, p.getLocation()); // 혹은 톱날을 보게? 일단 직쏘를 보게 유지
                    }
                    lookAtEntity(target, p.getLocation());
                }

                // [톱날 회전] 미친 듯이 회전 (Y축 회전)
                angle += 0.5f; // 회전 속도
                if (info.sawTop != null && info.sawTop.isValid()) {
                    // 위쪽 톱날: 정방향 회전
                    Transformation t = info.sawTop.getTransformation();
                    t.getLeftRotation().set(new AxisAngle4f(angle, 0, 1, 0));
                    info.sawTop.setTransformation(t);
                }
                if (info.sawBottom != null && info.sawBottom.isValid()) {
                    // 아래쪽 톱날: 역방향 회전 + 뒤집힘
                    Transformation t = info.sawBottom.getTransformation();
                    // 뒤집힌 상태(Scale Y = -1)에서 회전
                    t.getLeftRotation().set(new AxisAngle4f(-angle, 0, 1, 0));
                    info.sawBottom.setTransformation(t);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // 5. 제한시간 5초 타이머
        info.timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!info.isFinished) {
                    // 시간 초과 -> 실패
                    failGame(info, "시간 초과");
                }
            }
        }.runTaskLater(plugin, 100L); // 5초 = 100틱

        // 데이터 등록
        activeGames.put(p.getUniqueId(), info);
        targetToJigsawMap.put(target.getUniqueId(), p.getUniqueId());

        // 이동 불가 처리는 onPlayerMove에서 처리
    }

    private void spawnSaws(GameInfo info) {
        Location targetLoc = info.target.getLocation();
        Vector dir = targetLoc.getDirection().setY(0).normalize();

        // 오른쪽 벡터 구하기 (Cross Product)
        // (x, 0, z) cross (0, 1, 0) -> 우측 벡터
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();

        // 타겟 우측 1.5칸 거리
        Location sawLoc = targetLoc.clone().add(right.multiply(1.5)).add(0, 1, 0); // 높이 보정

        // [아래쪽 톱날]
        info.sawBottom = (BlockDisplay) info.target.getWorld().spawnEntity(sawLoc, EntityType.BLOCK_DISPLAY);
        info.sawBottom.setBlock(Bukkit.createBlockData(Material.STONECUTTER));
        // 크기 조정 및 초기 회전
        Transformation tBottom = info.sawBottom.getTransformation();
        tBottom.getScale().set(1.5f, 1.5f, 1.5f); // 좀 크게
        info.sawBottom.setTransformation(tBottom);

        // [위쪽 톱날] - 바닥면끼리 겹치게 (위에서 아래로 뒤집음)
        Location sawTopLoc = sawLoc.clone().add(0, 0.8, 0); // 살짝 위
        info.sawTop = (BlockDisplay) info.target.getWorld().spawnEntity(sawTopLoc, EntityType.BLOCK_DISPLAY);
        info.sawTop.setBlock(Bukkit.createBlockData(Material.STONECUTTER));

        Transformation tTop = info.sawTop.getTransformation();
        tTop.getScale().set(1.5f, -1.5f, 1.5f); // Y축 반전 (뒤집기)
        info.sawTop.setTransformation(tTop);

        // 소리 재생 (전기톱 시동)
        info.target.getWorld().playSound(sawLoc, Sound.ITEM_TRIDENT_THUNDER, 1f, 2f);
        info.target.getWorld().playSound(sawLoc, Sound.UI_STONECUTTER_TAKE_RESULT, 2f, 0.5f);
    }

    // [3] 아이템 버리기 감지 (정답 체크)
    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player dropper = e.getPlayer();

        // 이 사람이 타겟인지 확인
        if (!targetToJigsawMap.containsKey(dropper.getUniqueId()))
            return;

        UUID jigsawUUID = targetToJigsawMap.get(dropper.getUniqueId());
        GameInfo info = activeGames.get(jigsawUUID);

        if (info == null || info.isFinished)
            return;

        ItemStack dropped = e.getItemDrop().getItemStack();

        // 조건: 구운 소고기
        if (dropped.getType() == Material.COOKED_BEEF) {
            // 조건: 한 번에 버린 개수 일치
            if (dropped.getAmount() == info.requiredBeef) {
                successGame(info); // 성공
            } else {
                // 개수 틀림 -> 즉시 실패
                Bukkit.broadcastMessage("§c[직쏘] 개수가 틀렸다... 요구: " + info.requiredBeef + ", 제출: " + dropped.getAmount());
                failGame(info, "개수 불일치");
            }
        } else {
            // 다른 아이템 버림 -> 봐줌? 아니면 그냥 무시?
            // 기획상 "한 번에 버리지 못 한 경우" 이므로 다른 템 버리는 건 횟수 차감이 없으니 무시하거나,
            // "기회는 한 번"이라면 여기서도 실패 처리 가능.
            // 여기서는 '소고기'를 버릴 때만 체크하도록 유하게 처리.
        }
    }

    // [4] 성공 처리
    private void successGame(GameInfo info) {
        info.isFinished = true;
        Bukkit.broadcastMessage("§a직쏘 : 성공이다.");

        // 직쏘 먼저 해방
        info.jigsaw.sendMessage("§a무적과 고정이 풀렸습니다.");

        // 타겟은 2초 후 해방
        new BukkitRunnable() {
            @Override
            public void run() {
                if (info.target.isValid()) {
                    info.target.sendMessage("§a무적과 고정이 풀렸습니다.");
                }
                // 게임 데이터 정리
                cleanupGame(info);
            }
        }.runTaskLater(plugin, 40L); // 2초
    }

    // [5] 실패 처리 (처형 시작)
    private void failGame(GameInfo info, String reason) {
        if (info.isFinished)
            return;
        info.isFinished = true;

        Bukkit.broadcastMessage("§c직쏘 : 실패다.");

        // 직쏘 해방
        info.jigsaw.sendMessage("§a당신의 속박이 풀렸습니다.");

        // 타겟은 계속 속박됨 (onMove에서 처리)

        // 처형 애니메이션 시작
        startExecution(info);
    }

    private void startExecution(GameInfo info) {
        // 톱날이 타겟에게 이동 (2초간)
        Location startLoc = info.sawBottom.getLocation(); // 톱날 위치

        // 처형 태스크
        info.grindTask = new BukkitRunnable() {
            int ticks = 0;
            boolean reached = false;

            @Override
            public void run() {
                if (!info.target.isValid() || info.target.isDead()) {
                    cleanupGame(info); // 타겟 사망 시 종료
                    this.cancel();
                    return;
                }

                if (!reached) {
                    // [이동 단계] 2초(40틱) 동안 타겟에게 접근
                    ticks++;
                    double progress = (double) ticks / 40.0;

                    Location targetLoc = info.target.getLocation().add(0, 1, 0); // 타겟 허리춤
                    // 선형 보간 (Lerp) 이동
                    Vector vec = targetLoc.toVector().subtract(startLoc.toVector()).multiply(progress);
                    Location current = startLoc.clone().add(vec);

                    if (info.sawBottom.isValid())
                        info.sawBottom.teleport(current);
                    if (info.sawTop.isValid())
                        info.sawTop.teleport(current.clone().add(0, 0.8, 0));

                    if (ticks >= 40) {
                        reached = true;
                        ticks = 0; // 리셋해서 30초 카운트용으로 씀
                    }
                } else {
                    // [갈아버리기 단계] 30초 동안
                    ticks++;
                    if (ticks > 600) { // 30초(600틱) 경과
                        cleanupGame(info);
                        this.cancel();
                        return;
                    }

                    // 대미지 주기 (매 틱 실행됨)
                    // "0.1틱당 1뎀" -> 1틱(0.05초)당 10번? 불가능.
                    // 1틱당 1뎀으로 구현하되, setNoDamageTicks(0)으로 무적 무시.
                    // 기획의도: 엄청 빠르게 갈아버림.

                    info.target.setNoDamageTicks(0); // 무적 시간 제거
                    info.target.damage(1.0, info.jigsaw); // 1 데미지 (하트 0.5칸)

                    // 피 튀기는 효과
                    // 설명: 보내주신 Particle 클래스에 'BLOCK_CRACK'이 없습니다.
                    // 블럭이 부서지는 효과는 이제 'Particle.BLOCK'을 사용합니다.
                    info.target.getWorld().spawnParticle(
                            org.bukkit.Particle.BLOCK, // BLOCK_CRACK -> BLOCK 으로 변경
                            info.target.getLocation().add(0, 1, 0),
                            10, 0.2, 0.2, 0.2,
                            Bukkit.createBlockData(Material.REDSTONE_BLOCK));
                    info.target.getWorld().playSound(info.target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f,
                            2f);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // 매 틱 실행
    }

    private void cleanupGame(GameInfo info) {
        targetToJigsawMap.remove(info.target.getUniqueId());
        activeGames.remove(info.jigsaw.getUniqueId());
        info.cleanup();
    }

    // [이동 제한 & 시점 고정 로직]
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        boolean isJigsaw = activeGames.containsKey(p.getUniqueId());
        boolean isTarget = targetToJigsawMap.containsKey(p.getUniqueId());

        if (!isJigsaw && !isTarget)
            return;

        // 게임 찾기
        GameInfo info = isJigsaw ? activeGames.get(p.getUniqueId())
                : activeGames.get(targetToJigsawMap.get(p.getUniqueId()));
        if (info == null)
            return;

        // [직쏘] 실패나 성공으로 게임이 끝났으면 이동 가능
        if (isJigsaw && info.isFinished)
            return;

        // [타겟] 성공해서 끝났으면 이동 가능 (실패시는 계속 고정)
        // info.isFinished == true && 실패 상황이면 타겟은 아직 못 움직임.
        // 성공 시에는 successGame()에서 2초 뒤 정리되므로 그때까지 못 움직임.
        // 로직: info가 살아있는 한 움직임 통제.
        // 단, 직쏘는 isFinished가 true면 바로 해방됨.

        // 이동 취소 (좌표 고정)
        if (e.hasChangedBlock()) {
            e.setCancelled(true);
            p.sendMessage("§c움직일 수 없습니다.");
        }
    }

    // [데미지 무효화] 게임 중 무적
    @EventHandler
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            boolean isJigsaw = activeGames.containsKey(p.getUniqueId());
            boolean isTarget = targetToJigsawMap.containsKey(p.getUniqueId());

            if (isJigsaw || isTarget) {
                GameInfo info = isJigsaw ? activeGames.get(p.getUniqueId())
                        : activeGames.get(targetToJigsawMap.get(p.getUniqueId()));
                if (info != null) {
                    // 직쏘는 끝났으면 무적 해제
                    if (isJigsaw && info.isFinished)
                        return;

                    // 타겟은 실패해서 갈리는 중이면(grindTask 실행 중) 데미지 받아야 함
                    if (isTarget && info.isFinished && info.grindTask != null && !info.grindTask.isCancelled()) {
                        return; // 처형 데미지는 허용
                    }

                    e.setCancelled(true); // 그 외엔 무적
                }
            }
        }
    }

    // 시선 고정 유틸
    private void lookAt(Player p, Location target) {
        Location loc = p.getLocation();
        Vector dir = target.toVector().subtract(loc.toVector()).normalize();
        Location lookLoc = loc.clone();
        lookLoc.setDirection(dir);
        p.teleport(lookLoc); // 시점 강제 변경
    }

    private void lookAtEntity(LivingEntity entity, Location target) {
        Location loc = entity.getLocation();
        Vector dir = target.toVector().subtract(loc.toVector()).normalize();
        loc.setDirection(dir);
        entity.teleport(loc);
    }
}