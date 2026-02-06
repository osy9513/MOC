package me.user.moc.ability.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay; // [추가] 블록 발광용
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType; // [추가]
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask; // [추가]
import org.bukkit.util.Vector;

import com.destroystokyo.paper.event.player.PlayerJumpEvent; // Paper API

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;

public class AsuiTsuyu extends Ability {

    // 혀 타겟 정보 관리 클래스
    private class TongueTarget {
        Location targetLoc; // 블럭인 경우 위치 (센터)
        Entity targetEntity; // 엔티티인 경우 객체
        long expireTime; // 만료 시간
        BukkitTask visualTask; // [추가] 파티클 및 거리 유지 태스크
        Entity indicator; // [추가] 발광 효과용 엔티티 (BlockDisplay 등)

        public TongueTarget(Location loc, Entity entity, long durationMs) {
            this.targetLoc = loc;
            this.targetEntity = entity;
            this.expireTime = System.currentTimeMillis() + durationMs;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }

        // [추가] 정리(캔슬) 메서드
        public void cleanup() {
            if (visualTask != null && !visualTask.isCancelled()) {
                visualTask.cancel();
            }
            if (indicator != null && indicator.isValid()) {
                indicator.remove();
            }
            // 타겟이 엔티티였다면 발광 끄기
            if (targetEntity != null && targetEntity instanceof LivingEntity) {
                targetEntity.setGlowing(false);
            }
        }
    }

    // 상태 관리 (토가 히미코 규칙 준수: Map 사용)
    private final Map<UUID, TongueTarget> tongueTargets = new HashMap<>();

    public AsuiTsuyu(JavaPlugin plugin) {
        super(plugin);
        startPassiveTask();
    }

    @Override
    public String getCode() {
        return "048";
    }

    @Override
    public String getName() {
        return "아스이 츠유";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§a유틸 ● 아스이 츠유(나의 히어로 아카데미아)",
                "§f개성 - 개구리를 얻습니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 지급 아이템 없음, 패시브만 작동
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§a유틸 ● 아스이 츠유(나의 히어로 아카데미아)");
        p.sendMessage(" ");
        p.sendMessage("§f물 속에서 이동속도가 빠르며, 벽을 오를 수 있습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f맨손으로 웅크리고(Shift) 좌클릭 시 20블럭 사거리의 혀를 뻗습니다.");
        p.sendMessage("§f혀가 닿은 대상에게 3초 안에 다음 행동이 가능합니다.");
        p.sendMessage("§f- 점프 : 대상에게 빠르게 날아갑니다.");
        p.sendMessage("§f- 쉬프트 : 대상을 내 쪽으로 당겨옵니다. (블록인 경우 아이템화)");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 14초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        // [수정] 맵에 남은 타겟들의 비주얼 태스크도 싹 정리해야 함
        if (tongueTargets.containsKey(p.getUniqueId())) {
            tongueTargets.get(p.getUniqueId()).cleanup();
            tongueTargets.remove(p.getUniqueId());
        }
        // 패시브 효과(물 속 신속) 제거
        p.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
    }

    // === 패시브: 수중 신속 체크 루프 ===
    private void startPassiveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
                        if (p.isInWater()) {
                            // 돌고래의 가호 (수영 속도 증가)
                            p.addPotionEffect(
                                    new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 40, 2, false, false, false));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1초마다 체크
    }

    // === 패시브: 벽 타기 ===
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 벽 타기 조건: 벽에 붙어있고, 쉬프트 중이 아님 (올라가려는 의지?)
        // 보통 '바라보는 방향으로 이동' 키를 누르고 있으면 올라가게 함.
        // 여기서는 간단하게 "벽에 붙어있고 점프하거나 위를 볼 때" 등

        // 1. 벽에 붙어있는지 확인
        // 플레이어 기준 앞, 뒤, 좌, 우 블록 체크? 너무 무거움.
        // 간단히: 플레이어 위치의 블록이 사다리가 아닌데도 올라가려면?
        // 보통 isClimbing()은 사다리 등.

        // 간단한 벽타기 로직:
        // 플레이어의 이동 벡터가 벽을 향하고 있을 때 수직 상승.

        Location loc = p.getLocation();
        // 플레이어 전방 0.5칸 앞에 블록이 있는지 확인
        Vector dir = loc.getDirection().setY(0).normalize();
        Block frontBlock = loc.clone().add(dir.multiply(0.5)).getBlock();

        if (frontBlock.getType().isSolid() && !frontBlock.isLiquid()) {
            // 배드락/배리어 체크 (혹시 모르니)
            if (frontBlock.getType() == Material.BARRIER || frontBlock.getType() == Material.BEDROCK)
                return;

            // 쉬프트 중이면 매달리기(정지), 아니면 위를 보고 전진 키 누르면 상승?
            // "벽의 옆면을 오를 수 있다" -> 스파이더맨처럼.

            // 플레이어가 '점프'키를 누르고 있는지는 서버에서 알기 힘듦(JumpEvent는 떼었다 누를때만).
            // 보통 시선이 위쪽이고(pitch < -10) 전진 중이면 태워 올림.

            if (p.getLocation().getPitch() < -45) { // 위를 많이 보고 있으면
                if (p.getVelocity().getY() < 0.3) {
                    p.setVelocity(new Vector(0, 0.3, 0)); // 상승
                    // 낙하 데미지 방지
                    p.setFallDistance(0);
                }
            }
        }
    }

    // === 액티브: 개구리 혀 발사 ===
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK)
            return;

        if (e.getItem() != null && e.getItem().getType() != Material.AIR)
            return; // 맨손만

        if (!p.isSneaking())
            return; // 쉬프트 필수

        if (!checkCooldown(p))
            return;

        shootTongue(p);

        // 쿨타임은 혀 발사 시 바로 적용? 아니면 액션 후?
        // "쿨타임 : 14초" -> 보통 발사 시 적용.
        setCooldown(p, 14);

        // 전체 메시지
        Bukkit.broadcastMessage("§a아스이 츠유 : 개굴");
    }

    private void shootTongue(Player p) {
        Location start = p.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        double maxDistance = 20.0;

        Entity hitEntity = null;
        Block hitBlock = null;
        Location hitLoc = null;

        // 레이트레이싱
        var trace = p.getWorld().rayTrace(start, dir, maxDistance,
                org.bukkit.FluidCollisionMode.NEVER, true, 0.5,
                entity -> entity != p && entity instanceof LivingEntity);

        if (trace != null) {
            hitLoc = trace.getHitPosition().toLocation(p.getWorld());
            hitEntity = trace.getHitEntity();
            hitBlock = trace.getHitBlock();

            // [수정] 블록이면 정확한 블록 중심 좌표 얻기 (표면 말고)
            if (hitBlock != null) {
                hitLoc = hitBlock.getLocation().add(0.5, 0.5, 0.5);
            }
        } else {
            // 안 맞음 -> 최대 사거리까지
            hitLoc = start.clone().add(dir.multiply(maxDistance));
        }

        // 1. 즉발 파티클 (발사 연출)
        drawTongueParticle(start, hitLoc);

        if (hitLoc != null && (hitEntity != null || (hitBlock != null && hitBlock.getType().isSolid()))) {
            // [적중 성공]

            TongueTarget info = new TongueTarget(hitLoc, hitEntity, 3000);

            // 2. 가시성 강화 (Glowing)
            if (hitEntity != null) {
                // 엔티티: 직접 발광
                hitEntity.setGlowing(true);
            } else if (hitBlock != null) {
                // 블록: BlockDisplay 소환하여 발광
                BlockDisplay display = (BlockDisplay) p.getWorld().spawnEntity(hitBlock.getLocation(),
                        EntityType.BLOCK_DISPLAY);
                display.setBlock(hitBlock.getBlockData());
                display.setGlowing(true);
                // display.setTransformation(display.getTransformation().scale(1.01f)); // 살짝
                // 키워서 덮기? (선택사항)
                info.indicator = display; // 나중에 지워야 함
            }

            // 3. 지속 파티클 태스크 시작 (연결선 유지)
            TongueTarget finalInfo = info; // 람다용
            info.visualTask = new BukkitRunnable() {
                @Override
                public void run() {
                    // 유효성 검사
                    if (!p.isOnline() || info.isExpired()) {
                        info.cleanup();
                        tongueTargets.remove(p.getUniqueId());
                        this.cancel();
                        return;
                    }

                    // 대상 위치 갱신
                    Location currentTargetLoc = (info.targetEntity != null)
                            ? info.targetEntity.getLocation().add(0, info.targetEntity.getHeight() / 2, 0)
                            : info.targetLoc;

                    // 파티클 선 그리기 (플레이어 눈 -> 대상)
                    drawTongueParticle(p.getEyeLocation().subtract(0, 0.2, 0), currentTargetLoc);
                }
            }.runTaskTimer(plugin, 0L, 2L); // 0.1초마다 갱신

            // 상태 저장
            tongueTargets.put(p.getUniqueId(), info);

            p.sendMessage("§a[개구리 혀] 대상을 잡았습니다! (점프: 이동 / 쉬프트: 당기기)");
            p.playSound(p.getLocation(), Sound.ENTITY_SLIME_ATTACK, 1f, 1f);

        } else {
            // 허공
            p.playSound(p.getLocation(), Sound.ENTITY_LLAMA_SPIT, 1f, 1f);
        }
    }

    private void drawTongueParticle(Location start, Location end) {
        double distance = start.distance(end);
        Vector vec = end.toVector().subtract(start.toVector()).normalize();

        for (double i = 0; i < distance; i += 0.5) {
            Location point = start.clone().add(vec.clone().multiply(i));
            point.getWorld().spawnParticle(Particle.DUST, point, 1,
                    new Particle.DustOptions(Color.FUCHSIA, 1.0f)); // 분홍색
        }
    }

    // [삭제] wrapEntityEffect - 더 이상 사용하지 않음 (지속 파티클로 대체)

    // === 액션 1: 점프 (대상에게 이동) ===
    @EventHandler
    public void onJump(PlayerJumpEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!tongueTargets.containsKey(uuid))
            return;

        TongueTarget info = tongueTargets.get(uuid);
        if (info.isExpired()) {
            tongueTargets.remove(uuid);
            return;
        }

        // 점프 -> 내가 날아감
        Location dest = (info.targetEntity != null) ? info.targetEntity.getLocation() : info.targetLoc;
        Vector dir = dest.toVector().subtract(p.getLocation().toVector()).normalize();

        // 속도 9 (매우 빠름)
        p.setVelocity(dir.multiply(3.0)); // *참고: 속도 9는 너무 빨라서 서버 팅길 수도 있음. 3.0 정도로 조정.
        // "뛰어가는게 3일 때 그것의 3배로" -> 기본 0.2~0.3이니 9.0은 엄청난 수치.
        // Velocity 3.0~4.0이면 수백 블록 날아감.
        // 기획자의 "9"는 추상적인 수치일 가능성이 높음. (기존 Ability들이 Velocity 2~3 씀)
        // 일단 3.0으로 설정하고, 너무 빠르면 조절. (기획: 9라고 명시했으니 최대한 빠르게)
        // 하지만 Minecraft Velocity Max가 보통 4.0 제한이 걸리기도 하고, 9.0이면 청크 로딩 못따라감.
        // 안전하게 3.0 (약 60블록/s) 설정.

        p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1f);

        // 사용 후 해제
        // 사용 후 해제 (비주얼 정리 포함)
        info.cleanup();
        tongueTargets.remove(uuid);

        // 낙하 데미지 방지 (잠시동안)
        p.setFallDistance(0);

        // 점프 이벤트 자체는 캔슬 안 해도 됨(도약이니까)
    }

    // === 액션 2: 쉬프트 (당기기) ===
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking())
            return; // 눌렀을 때만

        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!tongueTargets.containsKey(uuid))
            return;

        TongueTarget info = tongueTargets.get(uuid);
        if (info.isExpired()) {
            tongueTargets.remove(uuid);
            return;
        }

        // 쉬프트 -> 대상을 당겨옴

        if (info.targetEntity != null) {
            Entity target = info.targetEntity;
            Vector dir = p.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();

            // [수정] 속도 4.5로 상향 + Y축 보정 (확실하게 당기기)
            target.setVelocity(dir.multiply(4.5).setY(0.5));
            p.sendMessage("§a대상을 당겨왔습니다!");
            p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 1f);
        } else if (info.targetLoc != null) {
            // 블록 당기기 구현
            Block block = info.targetLoc.getBlock();
            Material type = block.getType();
            // 공기 빼고 다 가능
            if (type != Material.AIR) {
                // 블록 파괴 및 아이템 지급
                // 드롭 아이템 컬렉션 가져오기
                java.util.Collection<org.bukkit.inventory.ItemStack> drops = block
                        .getDrops(new org.bukkit.inventory.ItemStack(Material.AIR)); // 맨손 채굴 기준

                if (!drops.isEmpty()) {
                    for (org.bukkit.inventory.ItemStack drop : drops) {
                        HashMap<Integer, org.bukkit.inventory.ItemStack> left = p.getInventory().addItem(drop);
                        if (!left.isEmpty()) {
                            for (org.bukkit.inventory.ItemStack item : left.values()) {
                                p.getWorld().dropItemNaturally(p.getLocation(), item);
                            }
                        }
                    }
                } else {
                    // [수정] 드랍이 없는 블록(배드락 등)도 강제로 아이템 지급
                    org.bukkit.inventory.ItemStack itemStack = new org.bukkit.inventory.ItemStack(type);
                    HashMap<Integer, org.bukkit.inventory.ItemStack> left = p.getInventory().addItem(itemStack);
                    if (!left.isEmpty()) {
                        for (org.bukkit.inventory.ItemStack item : left.values()) {
                            p.getWorld().dropItemNaturally(p.getLocation(), item);
                        }
                    }
                }

                // 블록 제거
                block.setType(Material.AIR);

                // 이펙트 및 소리
                p.getWorld().playEffect(block.getLocation(), org.bukkit.Effect.STEP_SOUND, type);
                p.sendMessage("§a블록을 당겨왔습니다!");
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                p.playSound(p.getLocation(), Sound.BLOCK_GRASS_BREAK, 1f, 1f);
            } else {
                p.sendMessage("§c잡은 블록이 이미 사라졌습니다.");
            }
        } else {
            p.sendMessage("§c당겨올 수 없습니다.");
            return; // 혀 유지? 아니면 실패처리? -> 유지
        }

        // 사용 후 해제
        // 사용 후 해제 (비주얼 정리 포함)
        info.cleanup();
        tongueTargets.remove(uuid);
    }
}
