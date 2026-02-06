package me.user.moc.ability.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import me.user.moc.ability.Ability;

public class MuhammadAvdol extends Ability {

    private final java.util.Map<UUID, Chicken> stands = new java.util.HashMap<>();
    private final java.util.Map<UUID, BukkitTask> standTasks = new java.util.HashMap<>();
    private final Random random = new Random();

    public MuhammadAvdol(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "046";
    }

    @Override
    public String getName() {
        return "무함마드 압둘";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c전투 ● 무함마드 압둘(죠죠의 기묘한 모험)",
                "§f『매지션즈 레드』는 봐주지 않는다.");
    }

    @Override
    public void giveItem(Player p) {
        p.getInventory().remove(Material.IRON_SWORD);

        // 스탠드 소환
        summonStand(p);

        // 공격 태스크 시작
        startAutoAttack(p);

        p.sendMessage("§c『매지션즈 레드』는 봐주지 않는다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 무함마드 압둘");
        p.sendMessage("§f우측 후방에 『매지션즈 레드』(거대한 닭)를 소환합니다.");
        p.sendMessage("§f스탠드는 4초마다 근처 적에게 불꽃(8 데미지)을 날립니다.");
        p.sendMessage("§f40% 확률로 **크로스파이어 허리케인**(21 데미지)을 시전합니다.");
        p.sendMessage("§f[크로스파이어 허리케인]: 앙크 모양의 불꽃, 21블록 관통, 21초간 화염 지속.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 4초 (자동)");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    private void summonStand(Player p) {
        removeStand(p); // 기존 스탠드 제거

        Location loc = getStandLocation(p);
        Chicken chicken = (Chicken) p.getWorld().spawnEntity(loc, EntityType.CHICKEN);

        // 속성 설정
        chicken.setAI(false);
        chicken.setGravity(false);
        chicken.setInvulnerable(true);
        chicken.setVisualFire(true); // 항상 불탐
        chicken.setCustomName("§c매지션즈 레드");
        chicken.setCustomNameVisible(true);
        // 더운지방 닭.
        chicken.setVariant(Chicken.Variant.WARM);
        chicken.setGlowing(true);
        // 팀 색상은 Scoreboard로 설정해야 붉게 나오지만, 여기서는 패킷 없이 기본 Glow(흰색/팀색) 따름.
        // 불타고 있어서 붉은 느낌 남.

        // 거대화 (SCALE 2.0)
        if (chicken.getAttribute(Attribute.SCALE) != null) {
            chicken.getAttribute(Attribute.SCALE).setBaseValue(2.0);
        }

        stands.put(p.getUniqueId(), chicken);

        // 스탠드 위치 동기화 태스크
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || !chicken.isValid()) {
                    removeStand(p);
                    this.cancel();
                    return;
                }

                // 관전 모드면 스탠드 제거
                if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    removeStand(p);
                    this.cancel();
                    return;
                }

                // 위치 동기화
                Location targetLoc = getStandLocation(p);
                // 부드러운 이동을 위해 teleport 사용 (Entity가 따라오는 느낌)
                // Yaw는 플레이어와 같게
                targetLoc.setYaw(p.getLocation().getYaw());
                chicken.teleport(targetLoc);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        standTasks.put(p.getUniqueId(), task);
        // Ability 클래스의 activeTasks에도 등록하여 관리하면 좋으나,
        // 여기서는 개별 Map으로 관리 (cleanup에서 처리)
    }

    private Location getStandLocation(Player p) {
        // 우측 후방
        Location pLoc = p.getLocation();
        Vector dir = pLoc.getDirection().setY(0).normalize();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize(); // 우측 벡터

        // 뒤로 1칸, 우측으로 1칸
        Vector offset = dir.clone().multiply(-1).add(right).multiply(1.0);

        return pLoc.clone().add(offset).add(0, 1.5, 0); // 공중 부양
    }

    private void startAutoAttack(Player p) {
        UUID pid = p.getUniqueId();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    this.cancel();
                    return;
                }
                // 스탠드 없으면 스킵 (관전 등)
                Chicken stand = stands.get(pid);
                if (stand == null || !stand.isValid())
                    return;

                // 타겟 탐색 (15칸)
                LivingEntity target = findTarget(p, stand.getLocation(), 15.0);
                if (target == null)
                    return; // 타겟 없으면 대기

                // 40% 확률로 크로스파이어 허리케인
                if (random.nextDouble() < 0.4) {
                    performCrossfireHurricane(p, stand, target);
                } else {
                    performNormalAttack(p, stand, target);
                }
            }
        }.runTaskTimer(plugin, 80L, 80L); // 4초마다

        // activeTasks에 등록 (Ability 부모 클래스 기능 활용)
        if (activeTasks.containsKey(pid)) {
            activeTasks.get(pid).add(task);
        } else {
            // AbilityManager 구조상 activeTasks가 초기화 되어 있어야 함.
            // Ability.giveItem 호출 시점에 보통 초기화되지 않음 (AbilityManager가 함)
            // 그러나 여기서는 Ability 상속 구조상 activeTasks 접근 가능.
            // 안전하게:
            activeTasks.computeIfAbsent(pid, k -> new java.util.ArrayList<>()).add(task);
        }
    }

    private LivingEntity findTarget(Player owner, Location center, double range) {
        LivingEntity closest = null;
        double minDst = Double.MAX_VALUE;

        for (Entity e : center.getWorld().getNearbyEntities(center, range, range, range)) {
            if (!(e instanceof LivingEntity le))
                continue;
            if (e.equals(owner))
                continue;
            if (e.equals(stands.get(owner.getUniqueId())))
                continue; // 본인 스탠드
            if (e instanceof Player && ((Player) e).getGameMode() == org.bukkit.GameMode.SPECTATOR)
                continue;

            double dst = e.getLocation().distanceSquared(center);
            if (dst < minDst) {
                minDst = dst;
                closest = le;
            }
        }
        return closest;
    }

    private void performNormalAttack(Player owner, Chicken stand, LivingEntity target) {
        // 일반 불꽃 (SmallFireball)
        Location start = stand.getLocation().add(0, 0.5, 0);
        Vector dir = target.getEyeLocation().subtract(start).toVector().normalize();

        SmallFireball fireball = stand.getWorld().spawn(start.add(dir), SmallFireball.class);
        fireball.setDirection(dir);
        fireball.setShooter(owner); // Shooter는 주인으로 설정
        // 데미지는 이벤트에서 처리? SmallFireball 기본 데미지 낮음.
        // EntityDamageByEntityEvent에서 처리하거나 직접 구현.
        // 여기서는 메타 데이터로 구분 (metadata는 복잡하니 이름으로?)
        fireball.setCustomName("ABDOL_NORMAL");

        stand.getWorld().playSound(stand.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
    }

    private void performCrossfireHurricane(Player owner, Chicken stand, LivingEntity target) {
        Bukkit.broadcastMessage("§c무함마드 압둘: 크로스파이어 허리케인!!");

        Location start = stand.getLocation().add(0, 0.5, 0);
        // 방향: 타겟 방향
        Vector dir = target.getEyeLocation().subtract(start).toVector().normalize().multiply(1.0); // 속도 1.0 (블록/틱)

        // 커스텀 투사체 태스크
        new BukkitRunnable() {
            Location current = start.clone();
            int dist = 0;
            final int maxDist = 21;

            @Override
            public void run() {
                if (dist >= maxDist) {
                    this.cancel();
                    return;
                }

                // 2. 1틱당 1블록 전진 (블록 관통)
                current.add(dir);
                dist++;

                // 3. 앙크(Ankh) 파티클 (매 틱마다 현재 위치에 그림)
                spawnAnkhParticle(current, dir);

                // 4. 충돌 체크 (엔티티만, 블록 무시)
                for (Entity e : current.getWorld().getNearbyEntities(current, 1.0, 1.0, 1.0)) {
                    if (!(e instanceof LivingEntity le))
                        continue;
                    if (e.equals(owner))
                        continue; // 주인 면역
                    if (e.equals(stand))
                        continue;

                    // 히트!
                    le.damage(21.0, owner);
                    le.setFireTicks(21 * 20); // 21초 화상 (꺼지지 않게 하려면 매우 길게 주거나 이벤트 캔슬 필요, 일단 21초)

                    // 피격음
                    le.getWorld().playSound(le.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);

                    // 관통하므로 계속 진행? "해당 공격은 블럭을 관통하며 21블러까지만 날라가고 사라진다"
                    // 적을 맞췄을 때 사라진다는 말은 없음 -> 다수 타격 가능 (관통)
                    // 만약 단일 타격이면 cancel, 여기선 관통으로 해석 (블록만 관통? "블럭을 관통하며... 날라가고 사라진다")
                    // 보통 스킬 설명에서 "날아가고 사라진다"는 최대 사거리에 대한 설명일 수 있고, "관통"은 다 뚫는다는 의미가 강함.
                    // 다수 타격 허용.
                }

                // 소리
                // current.getWorld().playSound(current, Sound.ITEM_FIRECHARGE_USE, 0.2f, 1f);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnAnkhParticle(Location loc, Vector dir) {
        // 진행 방향(dir)을 기준으로 앙크 모양(십자가 + 위쪽 고리)을 그림
        // 복잡한 회전 계산 대신, 간단하게 구(Sphere) + 선(Line)으로 앙크 느낌 냄.
        // 혹은 FLAME 파티클 뭉치.

        World w = loc.getWorld();
        // 중앙 점
        w.spawnParticle(Particle.FLAME, loc, 3, 0.1, 0.1, 0.1, 0.05); // 중심
        w.spawnParticle(Particle.LAVA, loc, 1, 0, 0, 0, 0); // 핵

        // 십자가 가로
        // 대략적인 십자가 형태 (빌보드 처리 없이 그냥 월드 좌표계 기준 십자가여도 화려하면 됨)
        // 앙크는 위쪽이 고리.
        // 그냥 붉은색 파티클(REDSTONE)로 그리는게 명확할 수 있음.

        // 성능상 FLAME 덩어리로 날아가는게 더 "불꽃" 같음.
        // 여기서는 "앙크 모양의 거대 불꽃" -> 붉은색(REDSTONE) + 주황(FLAME) 섞어서.

        Particle.DustOptions dust = new Particle.DustOptions(Color.RED, 1.5f);
        w.spawnParticle(Particle.DUST, loc, 5, 0.3, 0.3, 0.3, dust);

        // 조금 더 큰 화염
        w.spawnParticle(Particle.SMALL_FLAME, loc, 5, 0.2, 0.2, 0.2, 0.02);
    }

    // 데미지 조정 (일반 공격)
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof SmallFireball sf && sf.getCustomName() != null
                && sf.getCustomName().equals("ABDOL_NORMAL")) {
            e.setDamage(8.0);
        }
    }

    // 플레이어 나갈 때 / 죽을 때 처리는 AbilityManager가 cleanup 호출함.
    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        removeStand(p);
        if (standTasks.containsKey(p.getUniqueId())) {
            standTasks.get(p.getUniqueId()).cancel();
            standTasks.remove(p.getUniqueId());
        }
    }

    private void removeStand(Player p) {
        Chicken stand = stands.remove(p.getUniqueId());
        if (stand != null && stand.isValid()) {
            stand.remove();
        }
    }

    @Override
    public void reset() {
        super.reset();
        for (Chicken c : stands.values()) {
            if (c.isValid())
                c.remove();
        }
        stands.clear();
        standTasks.clear();
    }
}
