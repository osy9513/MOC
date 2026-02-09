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

        // [추가] 머리 위 해골 (시각 효과)
        BlockDisplay jigsawSkull;
        BlockDisplay targetSkull;

        // 관리용 태스크
        BukkitTask timerTask; // 5초 제한시간
        BukkitTask rotationTask; // 톱날 회전 및 시선 고정
        BukkitTask grindTask; // 처형(갈아버리기) 태스크

        // 위치 고정용 (이동 방지)
        Location jigsawLoc;
        Location targetLoc;

        // [Fix] 처형 상태 플래그 (즉시 데미지 허용용)
        boolean isGrinding = false;

        public GameInfo(Player jigsaw, LivingEntity target) {
            this.jigsaw = jigsaw;
            this.target = target;
            this.jigsawLoc = jigsaw.getLocation();
            this.targetLoc = target.getLocation();
            this.isFinished = false;
        }

        // 게임 끝날 때 정리 (톱날 삭제 등)
        public void cleanup() {
            if (sawTop != null && sawTop.isValid())
                sawTop.remove();
            if (sawBottom != null && sawBottom.isValid())
                sawBottom.remove();

            // [추가] 해골 제거
            if (jigsawSkull != null && jigsawSkull.isValid())
                jigsawSkull.remove();
            if (targetSkull != null && targetSkull.isValid())
                targetSkull.remove();

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
        meta.setCustomModelData(1); // 리소스팩: jigsaw
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

        // [Fix] 관전자는 대상에서 제외 (게임 시작 불가)
        if (target instanceof Player pTarget && pTarget.getGameMode() == org.bukkit.GameMode.SPECTATOR)
            return;

        // 능력자 체크
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 아이템 체크 (석재 절단기)
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.STONECUTTER)
            return;

        // [Fix] 이미 게임 중인지 먼저 체크 (Cooldown 체크보다 먼저)
        // 처형 중(isFinished)일 때 발생하는 데미지 이벤트가 쿨타임 때문에 캔슬되지 않도록 함.
        if (activeGames.containsKey(p.getUniqueId())) {
            GameInfo info = activeGames.get(p.getUniqueId());
            if (info != null && info.isFinished) {
                return; // 처형 중에는 공격 허용 (쿨타임 무시)
            }

            p.sendMessage("§c이미 게임이 진행 중입니다.");
            e.setCancelled(true);
            return;
        }

        // 쿨타임 체크 (게임 중이 아닐 때만)
        if (!checkCooldown(p)) {
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

        // 1. 미션 설정: 구운 소고기 16개 (고정)
        info.requiredBeef = 16;

        // 2. 메시지 출력
        p.sendMessage("§c[직쏘] §f게임을 시작하지.");
        String targetName = target.getName();
        Bukkit.broadcastMessage("§c직쏘 : " + targetName + "는 8초 안에 구운 소고기 " + info.requiredBeef + "개를 한 번에 버려라.");

        // 3. 톱날 소환 (타겟과 나 사이)
        spawnSaws(info);

        // [추가] 머리 위 해골 소환
        spawnSkulls(info);

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

                // [시선 고정 및 위치 고정]
                if (!info.isFinished) {
                    lookAt(p, target.getLocation(), info.jigsawLoc);
                    if (target instanceof Player tPlayer) {
                        lookAt(tPlayer, p.getLocation(), info.targetLoc);
                    } else {
                        lookAtEntity(target, p.getLocation(), info.targetLoc);
                    }
                } else {
                    // 실패 후 처형 시에도 타겟은 고정
                    if (target instanceof Player tPlayer) {
                        lookAt(tPlayer, p.getLocation(), info.targetLoc);
                    }
                    lookAtEntity(target, p.getLocation(), info.targetLoc);
                }

                // [Fix] 처형 중이면 톱날이 타겟을 따라가야 함 (계속)
                if (info.isFinished) {
                    Location targetLoc = target.getLocation().add(0, 1, 0);
                    if (info.sawBottom != null && info.sawBottom.isValid())
                        info.sawBottom.teleport(targetLoc);
                    if (info.sawTop != null && info.sawTop.isValid())
                        info.sawTop.teleport(targetLoc.clone().add(0, 0.45, 0));
                }

                // [추가] 해골 위치 동기화 (머리 위 고정)
                if (info.jigsawSkull != null && info.jigsawSkull.isValid() && p.isOnline()) {
                    // 회전 없이 위치만 이동
                    info.jigsawSkull.teleport(p.getLocation().add(0, 0.5, 0));
                }
                if (info.targetSkull != null && info.targetSkull.isValid() && target.isValid()) {
                    info.targetSkull.teleport(target.getLocation().add(0, 0.5, 0));
                }

                // [톱날 회전] 엄청 빠르게 회전
                // '10배 빨리' 요청 -> 기존 6.0f -> 60.0f
                angle += 60.0f;
                if (info.sawTop != null && info.sawTop.isValid()) {
                    Transformation t = info.sawTop.getTransformation();
                    t.getLeftRotation().set(new AxisAngle4f(angle, 0, 0, 1));
                    info.sawTop.setTransformation(t);
                }
                // sawBottom 제거됨 (하나만 사용)
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
        }.runTaskLater(plugin, 160L); // 8초 = 160틱

        // 데이터 등록
        activeGames.put(p.getUniqueId(), info);
        targetToJigsawMap.put(target.getUniqueId(), p.getUniqueId());

        // 이동 불가 처리는 onPlayerMove에서 처리
    }

    private void spawnSaws(GameInfo info) {
        Location jigsawLoc = info.jigsaw.getLocation();
        Location targetLoc = info.target.getLocation();

        // 두 플레이어의 중간 지점 계산
        // (A + B) / 2
        Location midLoc = jigsawLoc.clone().add(targetLoc).multiply(0.5);

        // 높이 보정 (+1칸 위)
        Location sawLoc = midLoc.add(0, 1, 0);

        // [아래쪽 톱날]
        // [톱날] - 1개만 사용 (sawTop만 사용)
        // 세로로 서있는 형태 유지 (Z축 회전 사용 중)
        Location sawTopLoc = sawLoc.clone().add(0, 0, 0);
        info.sawTop = (BlockDisplay) info.target.getWorld().spawnEntity(sawTopLoc, EntityType.BLOCK_DISPLAY);
        info.sawTop.setBlock(Bukkit.createBlockData(Material.STONECUTTER));

        Transformation tTop = info.sawTop.getTransformation();
        tTop.getScale().set(1.0f, 1.0f, 1.0f); // 크기 축소 (2.0 -> 1.0)
        // [Fix] 회전 중심을 블록 중앙으로 설정
        tTop.getTranslation().set(-0.5f, -0.5f, -0.5f); // 중심점 보정? 아니면 center 설정?
        // BlockDisplay의 회전축은 Transformation의 Center가 결정함.
        // 블록의 중앙은 (0.5, 0.5, 0.5)

        // Spigot API: Transformation(translation, leftRot, scale, rightRot)
        // Transformation 구조체에는 center가 없음?
        // Display 엔티티 자체의 setTransformationMatrix 등이 있지만
        // Paper/Bukkit API에서는 t.getLeftRotation() 등을 씀.
        // JOML Transformation에는 center 없음?
        // 아, Display 엔티티에는 setTransformation(Transformation) 이 있고...
        // Transformation 객체는 (translation, leftRot, scale, rightRot) 임.
        // 회전 축을 바꾸려면:
        // 1. Translation으로 (-0.5, -0.5, -0.5) 이동 (블록 중심을 엔티티 위치로)
        // 2. Rotation 적용
        // => 이러면 엔티티 위치(EntityLoc)가 블록의 중앙이 됨.

        // [수정]
        // 1. Scale 1.0
        // 2. Translation (-0.5, -0.5, -0.5) -> 블록의 중앙이 엔티티 좌표(0,0,0)에 오도록.
        tTop.getTranslation().set(-0.5f, -0.5f, -0.5f);

        info.sawTop.setTransformation(tTop);

        // sawBottom은 사용 안함
        info.sawBottom = null;

        // 소리 재생 (전기톱 시동)
        info.target.getWorld().playSound(sawLoc, Sound.ITEM_TRIDENT_THUNDER, 1f, 2f);
        info.target.getWorld().playSound(sawLoc, Sound.UI_STONECUTTER_TAKE_RESULT, 2f, 0.5f);
        info.target.getWorld().playSound(sawLoc, Sound.UI_STONECUTTER_TAKE_RESULT, 2f, 0.5f);
    }

    // [추가] 해골 소환 메서드
    private void spawnSkulls(GameInfo info) {
        info.jigsawSkull = createSkull(info.jigsaw);
        info.targetSkull = createSkull(info.target);
    }

    private BlockDisplay createSkull(LivingEntity owner) {
        Location loc = owner.getLocation().add(0, 0.5, 0); // 머리 위? 플레이어 키 고려
        // BlockDisplay로 해골 머리를 씌움.
        // PLAYER_HEAD 블록은 방향 설정이 까다로울 수 있음.
        // SKELETON_SKULL 사용
        BlockDisplay bd = (BlockDisplay) loc.getWorld().spawnEntity(loc, EntityType.BLOCK_DISPLAY);
        bd.setBlock(Bukkit.createBlockData(Material.SKELETON_SKULL));

        // 아이템 디스플레이가 아니라 블록 디스플레이라 SKELETON_SKULL 블록데이터 사용.
        // 회전 고정: Billboard.FIXED
        bd.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);

        // 크기 및 위치 조정
        Transformation t = bd.getTransformation();
        t.getScale().set(0.6f); // 적당한 크기
        // 머리에 딱 맞게 위치 조정 (블록 중심점 고려)
        // SKELETON_SKULL은 바닥에 놓인 형태.
        // Y축 조정 필요.
        t.getTranslation().set(-0.3f, 1.4f, -0.3f); // 대략적인 머리 위치 맞춤

        bd.setTransformation(t);
        return bd;
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

        // 조건: 구운 소고기 (COOKED_BEEF)
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
            // [Fix] 다른 아이템 버려도 실패 처리
            Bukkit.broadcastMessage("§c[직쏘] 그건 구운 소고기가 아니다...");
            failGame(info, "잘못된 아이템");
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
        info.isGrinding = true; // [Fix] 즉시 데미지 허용 플래그 On
        startExecution(info);
    }

    private void startExecution(GameInfo info) {
        // [Fix] 즉시 데미지 시작 (이동 단계 삭제)
        // 톱날을 타겟 위치로 즉시 이동
        Location targetLoc = info.target.getLocation().add(0, 1, 0);
        if (info.sawBottom != null)
            info.sawBottom.teleport(targetLoc);
        if (info.sawTop != null)
            info.sawTop.teleport(targetLoc.clone().add(0, 0.45, 0));

        // 처형 태스크
        info.grindTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!info.target.isValid() || info.target.isDead()) {
                    cleanupGame(info); // 타겟 사망 시 종료
                    this.cancel();
                    return;
                }

                // [갈아버리기 단계]
                ticks++;
                if (ticks > 200) { // 10초로 단축 (너무 김) 또는 30초 유지
                    cleanupGame(info);
                    this.cancel();
                    return;
                }

                // [데미지 즉시 적용] 실패 판정 직후부터 딜이 들어감
                // 1) 무적 시간 제거 (빠른 타격)
                info.target.setNoDamageTicks(0);

                // 2) 데미지 적용
                info.target.damage(1.0, info.jigsaw);

                // 3) 이펙트
                info.target.getWorld().spawnParticle(
                        org.bukkit.Particle.BLOCK,
                        info.target.getLocation().add(0, 1, 0),
                        10, 0.2, 0.2, 0.2,
                        Bukkit.createBlockData(Material.REDSTONE_BLOCK));
                info.target.getWorld().playSound(info.target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 2f);
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

                    // 타겟은 실패해서 갈리는 중이면(isGrinding) 데미지 받아야 함
                    if (isTarget && info.isFinished && info.isGrinding) {
                        return; // 처형 데미지는 허용
                    }

                    e.setCancelled(true); // 그 외엔 무적
                }
            }
        }
    }

    // [Fix] 위치 고정 오버로딩
    private void lookAt(Player p, Location target, Location fixedLoc) {
        Vector dir = target.toVector().subtract(fixedLoc.toVector()).normalize();
        Location lookLoc = fixedLoc.clone();
        lookLoc.setDirection(dir);
        p.teleport(lookLoc);
    }

    private void lookAtEntity(LivingEntity entity, Location target, Location fixedLoc) {
        Vector dir = target.toVector().subtract(fixedLoc.toVector()).normalize();
        Location lookLoc = fixedLoc.clone();
        lookLoc.setDirection(dir);
        entity.teleport(lookLoc);
    }

}