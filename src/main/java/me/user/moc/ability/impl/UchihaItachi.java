package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.*;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public class UchihaItachi extends Ability {

    // 아마테라스 발동 중인 플레이어 (이타치)
    private final Set<UUID> activeAmaterasu = new HashSet<>();
    // 현재 불타고 있는 대상 (엔티티 UUID -> 스케줄러) 관리용
    private final Map<UUID, BukkitTask> burningTargets = new HashMap<>();

    public UchihaItachi(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "067"; // 부여할 고유 번호
    }

    @Override
    public String getName() {
        return "우치하 이타치";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c전투 ● 우치하 이타치(나루토)",
                "§f아마테라스를 사용합니다.");
    }

    @Override
    public void giveItem(Player p) {
        detailCheck(p);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 우치하 이타치(나루토)");
        p.sendMessage("§f맨손 쉬프트 좌클릭 시 10초 동안 바라본 블럭 및 생명체가");
        p.sendMessage("§f검은 불꽃에 의해 4초간 0.5초에 1데미지를 받으며 불탑니다.");
        p.sendMessage("§f지속시간(10초)이 끝난 뒤 쿨타임이 돌며 6초간 실명에 걸립니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 20초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 1. 발동 조건: 능력 봉인 확인
        if (isSilenced(p))
            return;

        // 2. 능력 보유자 확인
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        // 3. 발동 조건: 맨손, 쉬프트(웅크리기), 좌클릭
        if (!p.isSneaking())
            return;
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK)
            return;
        if (p.getInventory().getItemInMainHand().getType() != Material.AIR)
            return;

        // 4. 관전자 불가 및 쿨타임 검사
        if (!checkCooldown(p))
            return;

        // 이미 발동 중이면 중복 실행 방지
        if (activeAmaterasu.contains(p.getUniqueId()))
            return;

        // 5. 능력 사용 성공
        startAmaterasu(p);
    }

    private void startAmaterasu(Player p) {
        activeAmaterasu.add(p.getUniqueId());

        // 전체 메세지 출력 ("우치하 이타치: 아마테라스")
        Bukkit.broadcastMessage("§c우치하 이타치: §8아마테라스");

        // 발동 사운드 (선택적)
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);

        BukkitTask mainTask = new BukkitRunnable() {
            int ticks = 0;
            final int MAX_TICKS = 200; // 10초 = 200틱

            @Override
            public void run() {
                // 이타치가 죽거나 다른 능력을 가지게 되면 취소
                if (!p.isOnline() || p.isDead() || !AbilityManager.getInstance().hasAbility(p, getCode())) {
                    activeAmaterasu.remove(p.getUniqueId());
                    setCooldown(p, 20);
                    cancel();
                    return;
                }

                // 2틱마다 레이캐스트 진행 (서버 부하 완화, 0.1초 간격)
                if (ticks % 2 == 0) {
                    Location eyeLoc = p.getEyeLocation();
                    Vector dir = eyeLoc.getDirection();
                    // 레이캐스트 (시야 범위 30블록으로 가정)
                    RayTraceResult result = p.getWorld().rayTrace(eyeLoc, dir, 300.0, FluidCollisionMode.NEVER, true,
                            0.5, entity -> {
                                // 조건절: 자기 자신 및 관전자 무시
                                if (entity.equals(p))
                                    return false;
                                if (entity instanceof Player targetPlayer
                                        && targetPlayer.getGameMode() == GameMode.SPECTATOR)
                                    return false;
                                return entity instanceof LivingEntity;
                            });

                    double maxDist = 300.0;
                    if (result != null) {
                        // [블록에 명중]
                        if (result.getHitBlock() != null) {
                            Location hitBlockLoc = result.getHitPosition().toLocation(p.getWorld());
                            maxDist = eyeLoc.distance(hitBlockLoc);
                            spawnAmaterasuParticles(hitBlockLoc); // 검은 무리 불꽃 파티클 발생

                            // 부딪힌 블록의 해당 면에 직접 불 붙이기
                            org.bukkit.block.Block hitB = result.getHitBlock();
                            org.bukkit.block.BlockFace face = result.getHitBlockFace();
                            if (hitB != null && face != null) {
                                org.bukkit.block.Block fireTarget = hitB.getRelative(face);
                                if (fireTarget.getType().isAir()) {
                                    fireTarget.setType(Material.FIRE);
                                }
                            }
                        } else if (result.getHitEntity() != null) {
                            maxDist = eyeLoc.distance(result.getHitEntity().getLocation());
                        }
                    }

                    // 지나간 경로(허공)의 바로 아래가 꽉 찬 단위 블록이면 불 붙이기 (잔불 생성)
                    // 서버 부하 완화를 위해 300블록 전체를 1칸씩 검사하지 않고 3칸 간격으로 검사합니다.
                    for (double d = 1.0; d <= maxDist; d += 3.0) {
                        Location traceLoc = eyeLoc.clone().add(dir.clone().multiply(d));
                        org.bukkit.block.Block traceBlock = traceLoc.getBlock();
                        if (traceBlock.getType().isAir()) {
                            org.bukkit.block.Block below = traceBlock.getRelative(org.bukkit.block.BlockFace.DOWN);
                            if (below.getType().isSolid()) {
                                traceBlock.setType(Material.FIRE);
                            }
                        }
                    }

                    if (result != null) {
                        // [엔티티에 명중]
                        if (result.getHitEntity() != null && result.getHitEntity() instanceof LivingEntity target) {
                            applyAmaterasuBurn(p, target);
                        }
                    }
                }

                ticks++;

                if (ticks >= MAX_TICKS) {
                    // 10초 종료 시
                    activeAmaterasu.remove(p.getUniqueId());
                    // 쿨타임 측정 시작
                    setCooldown(p, 20);
                    // 이타치 본인에게 6초간 실명 (120틱)
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 120, 0, false, false));
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // 태스크 관리 목록에 등록 (라운드 리셋 시 일괄 취소용)
        registerTask(p, mainTask);
    }

    /**
     * 시선이 닿은 블럭에 파티클 생성
     */
    private void spawnAmaterasuParticles(Location loc) {
        World w = loc.getWorld();
        if (w == null)
            return;
        w.spawnParticle(Particle.SQUID_INK, loc, 20, 0.5, 0.5, 0.5, 0.05);
        w.spawnParticle(Particle.LARGE_SMOKE, loc, 10, 0.5, 0.5, 0.5, 0.02);
        w.spawnParticle(Particle.FLAME, loc, 5, 0.3, 0.3, 0.3, 0.02);
    }

    /**
     * 타겟에게 지정된 시간 동안 검은 불꽃(피격 피해)을 입히는 별도 스케줄러 실행
     * 파티클 4초, 도트뎀 4초(0.5초당 1데미지)
     */
    private void applyAmaterasuBurn(Player itachi, LivingEntity target) {
        // 이미 불타고 있으면 스케줄 중복 방지 (원한다면 갱신 처리 가능)
        if (burningTargets.containsKey(target.getUniqueId()))
            return;

        target.setFireTicks(80); // 바닥 바닐라 불 효과도 4초로 변경

        BukkitTask burnTask = new BukkitRunnable() {
            int intervals = 0;
            final int MAX_INTERVALS = 8; // 4초 (0.5초 * 8회 = 4초)

            @Override
            public void run() {
                if (target.isDead() || !target.isValid()) {
                    burningTargets.remove(target.getUniqueId());
                    cancel();
                    return;
                }

                // 매 0.5초 (10틱)마다 1데미지 무적 무시 판정
                if (target instanceof Damageable dmg) {
                    // MOC_LastKiller 주입 (킬 판정)
                    target.setMetadata("MOC_LastKiller",
                            new FixedMetadataValue(plugin, itachi.getUniqueId().toString()));

                    target.setNoDamageTicks(0); // 무적 판정 무시
                    dmg.damage(1.0, itachi); // 1.0 데미지
                }

                // 지정된 시간(4초) 동안 무조건 파티클 뿜어내기
                spawnAmaterasuParticles(target.getLocation().add(0, 1, 0));

                intervals++;

                if (intervals >= MAX_INTERVALS) {
                    burningTargets.remove(target.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // 0.5초(10틱) 간격

        burningTargets.put(target.getUniqueId(), burnTask);

        // 이타치의 관리 태스크에도 살짝 등록해둠 (이타치가 리셋 시 이 불꽃도 끄고 싶으면 유지, 보통 자립형으로 두지만 등록해도 무방)
        registerTask(itachi, burnTask);
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        // 이타치의 상태가 해제될 때 맵에서 제거
        activeAmaterasu.remove(p.getUniqueId());
    }

    @Override
    public void reset() {
        super.reset();
        activeAmaterasu.clear();
        // 잔존 도트뎀 스케줄러 취소
        for (BukkitTask task : burningTargets.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        burningTargets.clear();
    }
}
