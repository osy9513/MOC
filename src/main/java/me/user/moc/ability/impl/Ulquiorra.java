package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import me.user.moc.MocPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Ulquiorra extends Ability {

    public Ulquiorra(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "010"; // 코드 010
    }

    @Override
    public String getName() {
        return "우르키오라 쉬퍼"; // 능력 이름
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§a전투 ● 우르키오라 쉬퍼(블리치)",
                "§f란사 델 렐람파고를 우클릭 시 전방에 발사합니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 기존 철 칼 제거 (있다면)
        p.getInventory().remove(Material.IRON_SWORD);

        // 란사 델 렐람파고 (삼지창) 지급
        ItemStack lanza = new ItemStack(Material.TRIDENT);
        ItemMeta meta = lanza.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§a란사 델 렐람파고"));
            meta.setLore(List.of("§7우클릭 시 2초간 기를 모아 강력한 창을 던집니다.", "§7적중 시 대상을 끌고 가며 큰 피해를 입힙니다.", "§8(쿨타임 20초)"));
            meta.setUnbreakable(true); // 부서지지 않음
            meta.setCustomModelData(1); // 리소스팩: ulquiorra
            lanza.setItemMeta(meta);
        }
        p.getInventory().addItem(lanza);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§a전투 ● 우르키오라 쉬퍼(블리치)");
        p.sendMessage("§f란사 델 렐람파고를 우클릭하면 2초간 준비 후 거대한 삼지창을 날립니다.");
        p.sendMessage("§f적중한 적을 30블록까지 끌고 가며 체력 1줄(20)의 방어 무시 피해를 줍니다.");
        p.sendMessage("§f준비 중에는 구속 5 효과가 부여됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 20초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 란사 델 렐람파고");
        p.sendMessage("§f장비 제거 : 철 검");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // [Fix] 관전자는 능력 발동 불가
        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR)
            return;

        ItemStack item = e.getItem();

        // 1. 아이템 체크 (삼지창이고 이름이 포함되어야 함)
        if (item == null || item.getType() != Material.TRIDENT)
            return;
        // Paper API 1.21에서 getDisplayName()은 deprecated이지만, 문자열 비교를 위해 임시 사용하거나
        // Adventure API 사용.
        if (item.getItemMeta() == null || !item.getItemMeta().hasDisplayName())
            return;
        @SuppressWarnings("deprecation")
        String displayName = item.getItemMeta().getDisplayName();
        if (!displayName.contains("란사 델 렐람파고"))
            return;

        // 2. 액션 체크 (우클릭)
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // 바닐라 삼지창 투척 방지
            e.setCancelled(true);

            // 3. 쿨타임 체크 (Ability 부모 클래스 메서드 활용)
            if (!checkCooldown(p))
                return;

            // 쿨타임 설정 (20초)
            setCooldown(p, 20); // 테스트 용

            // === [1단계: 시전 준비] ===
            // 2. 구속 255 + 점프 불가 (안전하게 70틱 부여 후 발사 시 해제)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 5, true, true, true));
            applyJumpSilence(p, 70);

            // 3. 메시지 출력
            p.getServer().broadcastMessage("우르키오라 쉬퍼 : §2닫아라, 무르시엘라고");

            // 4. 이펙트 (검녹색 연기) - 3초간
            org.bukkit.scheduler.BukkitTask chargeTask = new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (ticks >= 60) { // 시전 딜레이 3초
                        this.cancel();
                        return;
                    }
                    // 검녹색 연기: DUST 파티클 (RGB: 13, 54, 21) -> 더 진하게 (0, 100, 0)
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0, 50, 0), 2.5f);
                    // 위치: 플레이어 주위에 퍼지게
                    p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 10, 0.5, 0.8, 0.5, dust);
                    // 바닥에도 깔리게
                    p.getWorld().spawnParticle(Particle.DUST, p.getLocation(), 5, 0.5, 0.1, 0.5, dust);

                    // [추가] 연기 나가는 효과음
                    if (ticks % 4 == 0) {
                        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.4f, 0.5f);
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_CHARGE, 0.6f, 0.5f);
                    }

                    ticks += 2;
                }
            }.runTaskTimer(plugin, 0L, 2L);
            registerTask(p, chargeTask); // cleanup을 위해 등록

            // === [2단계: 발사 (3초 후)] ===
            org.bukkit.scheduler.BukkitTask launchTask = new BukkitRunnable() {
                @Override
                public void run() {
                    // 발사 시 구속 해제
                    if (p.isOnline()) {
                        p.removePotionEffect(PotionEffectType.SLOWNESS);
                        AbilityManager.jumpSilenceExpirations.remove(p.getUniqueId());
                    }
                    fireLanza(p);
                }
            }.runTaskLater(plugin, 60L); // 60 ticks = 3 sec
            registerTask(p, launchTask);
        }
    }

    private void fireLanza(Player p) {
        // 메시지 출력
        p.getServer().broadcastMessage("우르키오라 쉬퍼 : §a란사 델 렐람파고");

        Location startLoc = p.getEyeLocation();
        Vector direction = startLoc.getDirection().normalize();

        // 거대한 삼지창 생성 (ItemDisplay)
        ItemDisplay projectile = p.getWorld().spawn(startLoc, ItemDisplay.class);
        ItemStack trident = new ItemStack(Material.TRIDENT);
        ItemMeta tMeta = trident.getItemMeta();
        tMeta.setCustomModelData(1); // 리소스팩: ulquiorra
        trident.setItemMeta(tMeta);
        projectile.setItemStack(trident);

        // [중요] 라운드 종료 시 삭제되도록 등록
        registerSummon(p, projectile);

        // [중앙 정렬] 아이템 디스플레이 타입을 FIXED로 설정하여 피벗을 중앙으로 맞춤
        projectile.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);

        // [크기 조정] 3배 크기
        Transformation transform = projectile.getTransformation();
        transform.getScale().set(3.0f, 3.0f, 3.0f);

        // [회전 및 위치 보정]
        // Y축 -90도에서 대각선이었다면, 45도 오차 보정 시도 -> -135도
        transform.getLeftRotation().set(new AxisAngle4f((float) Math.toRadians(-135), 0, 1, 0));

        // 모델의 중심이 소환 지점에 오도록 이동 (3배 크기에 맞춰 조정)
        transform.getTranslation().set(0, 0, 0f);

        projectile.setTransformation(transform);

        // 끌려간 엔티티 목록 (중복 피격 방지 + 폭발 시 데미지 대상)
        Set<Entity> draggedEntities = new HashSet<>();

        // 발사체 이동 태스크
        org.bukkit.scheduler.BukkitTask projectileTask = new BukkitRunnable() {
            Location currentLoc = startLoc.clone();
            double distanceTravelled = 0;
            final double maxDistance = 30.0;
            double currentSpeed = 1.5; // 속도

            // 벡터(방향) 저장용
            Vector currentDir = direction.clone();

            @Override
            public void run() {
                // 1. 거리 체크
                if (distanceTravelled >= maxDistance || !projectile.isValid()) {
                    explode(currentLoc, draggedEntities, p);
                    projectile.remove();
                    this.cancel();
                    return;
                }

                // 2. 이동 전 위치 저장
                Location prevLoc = currentLoc.clone();

                // 3. 기반암 및 월드보더 충돌 및 슬라이딩 로직
                // 다음 위치 예측
                Location nextLoc = currentLoc.clone().add(currentDir.clone().multiply(currentSpeed));
                boolean hitsBorder = false;

                // [추가] 월드보더 충돌 체크
                WorldBorder border = currentLoc.getWorld().getWorldBorder();
                double size = border.getSize() / 2.0;
                double centerX = border.getCenter().getX();
                double centerZ = border.getCenter().getZ();

                double minX = centerX - size;
                double maxX = centerX + size;
                double minZ = centerZ - size;
                double maxZ = centerZ + size;

                // X축 보더 충돌?
                if (nextLoc.getX() < minX || nextLoc.getX() > maxX) {
                    currentDir.setX(-currentDir.getX() * 0.5);
                    hitsBorder = true;
                }
                // Z축 보더 충돌?
                if (nextLoc.getZ() < minZ || nextLoc.getZ() > maxZ) {
                    currentDir.setZ(-currentDir.getZ() * 0.5);
                    hitsBorder = true;
                }

                // 블럭 체크 (Based on Material) OR 월드보더 충돌
                if (nextLoc.getBlock().getType() == Material.BEDROCK || hitsBorder) {
                    // 기반암 충돌: 슬라이딩 (반사 혹은 미끄러짐)
                    // 충돌 면 계산을 위해 각 축별로 검사 (보더 충돌이 아닐 때만 블록 검사 수행)

                    if (!hitsBorder) {
                        // X축 충돌?
                        Location testX = currentLoc.clone().add(currentDir.getX() * currentSpeed, 0, 0);
                        if (testX.getBlock().getType() == Material.BEDROCK) {
                            currentDir.setX(-currentDir.getX() * 0.5);
                        }
                        // Y축 충돌?
                        Location testY = currentLoc.clone().add(0, currentDir.getY() * currentSpeed, 0);
                        if (testY.getBlock().getType() == Material.BEDROCK) {
                            currentDir.setY(-currentDir.getY() * 0.5);
                        }
                        // Z축 충돌?
                        Location testZ = currentLoc.clone().add(0, 0, currentDir.getZ() * currentSpeed);
                        if (testZ.getBlock().getType() == Material.BEDROCK) {
                            currentDir.setZ(-currentDir.getZ() * 0.5);
                        }
                    }

                    // 방향이 바뀌었으니, 갱신된 방향으로 이동
                    // 만약 갇혀버리면 멈춤
                    if (currentDir.length() < 0.1) {
                        // 멈춤 -> 폭발
                        explode(currentLoc, draggedEntities, p);
                        projectile.remove();
                        this.cancel();
                        return;
                    }
                } else {
                    // 기반암이 아님 -> 관통 (아무 동작 안 함, 그냥 통과)
                    // 다른 블럭들은 모두 무시하고 지나감
                }

                // 이동 적용
                currentLoc.add(currentDir.clone().multiply(currentSpeed));

                // 디스플레이 엔티티 이동 및 회전
                projectile.teleport(currentLoc);
                // 회전: 이동 방향을 바라보게 설정 (Yaw/Pitch 계산)
                currentLoc.setDirection(currentDir);
                projectile.setRotation(currentLoc.getYaw(), currentLoc.getPitch());

                distanceTravelled += currentSpeed;

                // 4. 충돌 감지 (엔티티 - 히트박스 크게 3.0)
                for (Entity entity : currentLoc.getWorld().getNearbyEntities(currentLoc, 3.0, 3.0, 3.0)) {
                    if (entity.equals(p))
                        continue; // 시전자는 제외
                    if (entity.equals(projectile))
                        continue; // 투사체 자체 제외
                    if (entity instanceof LivingEntity) {
                        if (entity instanceof Player
                                && ((Player) entity).getGameMode() == org.bukkit.GameMode.SPECTATOR)
                            continue;
                        draggedEntities.add(entity);
                    }
                }

                // 5. 끌고 가기 (Dragged Entities Teleport)
                for (Entity e : draggedEntities) {
                    if (e.isValid() && !e.isDead()) {
                        Location target = currentLoc.clone();
                        // 끌려갈 때 삼지창 끝부분에 위치하도록 뒤로 밀기
                        target.subtract(currentDir.clone().multiply(1.5));
                        target.setYaw(e.getLocation().getYaw());
                        target.setPitch(e.getLocation().getPitch());
                        e.teleport(target);
                    }
                }

                // 6. 시각 효과 (초록 연기) - 보간(Interpolation) 적용
                // 이전 위치(prevLoc)에서 현재 위치(currentLoc)까지 촘촘하게 파티클 생성
                spawnTrail(prevLoc, currentLoc);
            }

            // 파티클 보간 함수
            private void spawnTrail(Location start, Location end) {
                double distance = start.distance(end);
                Vector vector = end.toVector().subtract(start.toVector());
                int points = (int) (distance * 5); // 1블럭당 5개 파티클 (매우 촘촘함)

                // 검녹색 연기 옵션
                Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0, 50, 0), 2.0f);

                // [수정] 파티클을 전체적으로 뒤로 밀어서 생성 (Offset)
                // 현재 이동 방향(vector)의 반대 방향으로 약간 밀어줌 -> 창이 먼저 가고 연기가 따라가는 효과
                Vector offset = vector.clone().normalize().multiply(-0.5);

                for (int i = 0; i <= points; i++) {
                    double fraction = (double) i / points;
                    Location point = start.clone().add(vector.clone().multiply(fraction));

                    // 오프셋 적용
                    point.add(offset);

                    // 메인 줄기
                    point.getWorld().spawnParticle(Particle.DUST, point, 3, 0.3, 0.3, 0.3, dust);
                    // 삼지창 주변으로 좀 더 넓게 퍼지는 연기
                    point.getWorld().spawnParticle(Particle.ASH, point, 2, 0.6, 0.6, 0.6, 0.01);
                }
            }

        }.runTaskTimer(plugin, 0L, 1L); // 1틱마다 실행
        registerTask(p, projectileTask);
    }

    private void explode(Location loc, Set<Entity> targets, Player attacker) {
        // 1. 폭발 이펙트 (데미지 X, 블록 파괴 X)
        loc.getWorld().createExplosion(loc, 4.0F, false, false);
        // 추가 이펙트
        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);

        // 2. 데미지 처리 (20 고정 피해)
        for (Entity e : targets) {
            if (e instanceof LivingEntity living) {
                applyTrueDamage(living, 20.0, attacker);
            }
        }
    }

    private void applyTrueDamage(LivingEntity target, double damage, Player attacker) {
        // [추가] 킬 판정 연동
        if (attacker != null) {
            target.setMetadata("MOC_LastKiller",
                    new org.bukkit.metadata.FixedMetadataValue(plugin, attacker.getUniqueId().toString()));
        }

        // 방어력 무시 로직
        double currentHealth = target.getHealth();
        double newHealth = currentHealth - damage;

        // 피격 모션 및 넉백을 위해 0 데미지 이벤트 발생
        target.damage(0.1);
        target.setNoDamageTicks(0);

        if (newHealth <= 0) {
            target.setHealth(0);
        } else {
            target.setHealth(newHealth);
        }
    }
}
