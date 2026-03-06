package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Hiccup extends Ability {

    private final Map<UUID, EnderDragon> toothlessMap = new HashMap<>();
    private final Map<UUID, BukkitRunnable> flightTaskMap = new HashMap<>();

    public Hiccup(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "071";
    }

    @Override
    public String getName() {
        return "히컵";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c복합 ● 히컵(드래곤 길들이기)",
                "§f투슬리스를 타고 브레스를 쏩니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c복합 ● 히컵(드래곤 길들이기)");
        p.sendMessage("§f라운드 시작 시 체력 200의 투슬리스가 소환됩니다.");
        p.sendMessage("§f투슬리스는 아쉽게도 현재 날개를 다쳐 높게 날지 못 합니다.");
        p.sendMessage("§f투슬리스를 우클릭 시 3초마다 탑승할 수 있으며 쉬프트를 눌려 내릴 수 있습니다.");
        p.sendMessage("§f투슬리스가 다른 생명체와 부딪치면 박치기를 하여 6의 데미지를 줍니다.");
        p.sendMessage("§f탑승 중 좌클릭 시 투슬리스가 해당 방향에 브레스를 쏩니다.");
        p.sendMessage("§f브레스는 블럭이나 생명체에 부딪치면");
        p.sendMessage("§f폭발하여 10의 데미지를 주고 해당 자리에 초당 8 데미지를 주는 장판이 8초간 설치됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f투슬리스가 공격 받을 시 20% 확률로 히컵이 떨어집니다.");
        p.sendMessage("§f[드래곤의 공포] 몸이 경직되어 투슬리스 얼굴 근처에선 맘처럼 안 움직여집니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 15초.");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 철 칼");
    }

    @Override
    public void giveItem(Player p) {
        detailCheck(p);

        // 철 칼 제거
        p.getInventory().remove(Material.IRON_SWORD);

        Bukkit.broadcastMessage("§c히컵 : §f눈을 공격해 투슬리스!");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);

        // 투슬리스 소환 (에메랄드 블럭 중앙 로드)
        Location centerLoc = me.user.moc.config.ConfigManager.getInstance().spawn_point;
        if (centerLoc == null) {
            // 아직 게임이 시작되지 않아 spawn_point가 없으면 현재 위치 허용 방어코드
            centerLoc = p.getLocation();
        }

        Location spawnLoc = p.getLocation().add(p.getLocation().getDirection().multiply(2));
        // 플레이어가 에메랄드 블럭보다 높은 곳에 있으면 에메랄드 블럭 기준으로 소환하고, 아니면 플레이어 위치 기준으로 소환
        if (p.getLocation().getY() > centerLoc.getBlockY()) {
            spawnLoc.setY(centerLoc.getBlockY() + 1.0);
        } else {
            spawnLoc.setY(p.getLocation().getY() + 1.0);
        }

        EnderDragon dragon = (EnderDragon) p.getWorld().spawnEntity(spawnLoc, EntityType.ENDER_DRAGON);

        // 엔더드래곤은 바닐라 스폰 시 주변 지형과 충돌하면 자동으로 높은 곳이나 공중(공터)으로 스스로를 이동시키는 특성이 있음.
        // 이를 방지하기 위해 생성 직후 의도한 위치로 강제 텔레포트시켜 덮어씌움.
        dragon.teleport(spawnLoc);

        // 커스텀 이름 및 메타데이터 설정 (주인 인식 및 킬러 연동)
        dragon.setCustomName("§8[ §0투슬리스 §8]");
        dragon.setCustomNameVisible(true);
        dragon.setMetadata("HiccupOwner", new FixedMetadataValue(plugin, p.getUniqueId().toString()));
        dragon.setMetadata("MOC_Summon", new FixedMetadataValue(plugin, p.getUniqueId().toString()));

        // 체력 설정 (200)
        AttributeInstance maxHealth = dragon.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(200.0);
        }
        dragon.setHealth(200.0);

        // 크기 설정 (절반)
        AttributeInstance scale = dragon.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(0.5);
        }

        dragon.setPhase(EnderDragon.Phase.HOVER);

        // 시끄러운 기본 소음 제거 (수동으로 재생)
        dragon.setSilent(true);
        // 바닐라 충돌(겹침) 넉백 엔진 비활성화 (대쉬, 점프가 막히는 문제 및 우주로 날아가는 버그 차단)
        dragon.setCollidable(false);

        registerSummon(p, dragon);
        toothlessMap.put(p.getUniqueId(), dragon);

        // 높이 제한 및 강제 제어 태스크 시작
        startFlightTask(p, dragon);
    }

    private void startFlightTask(Player p, EnderDragon dragon) {
        BukkitRunnable taskRunnable = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!dragon.isValid() || dragon.isDead() || !p.isValid()
                        || !AbilityManager.getInstance().hasAbility(p, getCode())) {
                    this.cancel();
                    toothlessMap.remove(p.getUniqueId());
                    flightTaskMap.remove(p.getUniqueId());
                    return;
                }

                // 탑승 중일 때 제어
                if (dragon.getPassengers().contains(p)) {
                    // 날개 펄럭이는 소리 수동 재생 (10틱 = 0.5초 마다)
                    if (ticks % 10 == 0) {
                        dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.0f);
                    }

                    Vector dir = p.getLocation().getDirection();
                    // 속도 조절
                    dir.multiply(0.8);

                    Location loc = dragon.getLocation();

                    // 동적 높이 제한 검사 (에메랄드 기준)
                    Location currentCenter = me.user.moc.config.ConfigManager.getInstance().spawn_point;
                    int currentMaxY = (currentCenter != null ? currentCenter.getBlockY() : loc.getBlockY()) + 5;
                    int currentMinY = (currentCenter != null ? currentCenter.getBlockY() : loc.getBlockY());

                    if (loc.getY() >= currentMaxY && dir.getY() > 0) {
                        // 더 이상 올라가지 못하게 하강 벡터 적용 또는 수평 이동만 허용
                        dir.setY(-0.1);
                        if (loc.getY() > currentMaxY + 1) { // 뚫고 나갔으면 강제로 내림
                            loc.setY(currentMaxY);
                            dragon.teleport(loc);
                        }
                    } else if (loc.getY() <= currentMinY && dir.getY() < 0) {
                        // 에메랄드(기반암) 아래로 내려가지 못하게 제한
                        dir.setY(0.1);
                        if (loc.getY() < currentMinY - 1) { // 뚫고 나갔으면 강제로 올림
                            loc.setY(currentMinY);
                            dragon.teleport(loc);
                        }
                    } // 높이 제한 검사의 닫는 괄호

                    // 박치기 (근접 엔티티 6 데미지 부여)
                    for (org.bukkit.entity.Entity ent : dragon.getWorld().getNearbyEntities(dragon.getLocation(), 4.0,
                            4.0, 4.0)) {
                        if (ent instanceof org.bukkit.entity.LivingEntity le && ent != p
                                && ent.getType() != EntityType.ENDER_DRAGON
                                && ent.getType() != EntityType.ARMOR_STAND) {
                            if (!le.hasMetadata("RamCooldown_" + p.getUniqueId().toString())) {
                                le.setMetadata("MOC_LastKiller",
                                        new FixedMetadataValue(plugin, p.getUniqueId().toString()));
                                le.damage(6.0, p);

                                // 드래곤의 비정상적인 이동 가속도로 인해 상대가 하늘 높이 날아가는 바닐라 넉백 버그 방지
                                // 데미지를 주자마자 플레이어 시선 방향으로 살짝만 밀쳐지게 속도를 고정
                                le.setVelocity(dir.clone().normalize().multiply(0.5).setY(0.3));

                                // 박치기 시 드래곤 소리 수동 재생
                                dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f,
                                        1.0f);

                                // 박치기 연속 데미지 안 들어가게 임시 쿨다운 부여 (1초 = 20틱 기준)
                                final UUID targetUuid = le.getUniqueId();
                                le.setMetadata("RamCooldown_" + p.getUniqueId().toString(),
                                        new FixedMetadataValue(plugin, true));
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        org.bukkit.entity.Entity target = Bukkit.getEntity(targetUuid);
                                        if (target != null && target.isValid()) {
                                            target.removeMetadata("RamCooldown_" + p.getUniqueId().toString(), plugin);
                                        }
                                    }
                                }.runTaskLater(plugin, 20L); // 1초 후 제거
                            }
                        }
                    }

                    // 드래곤의 머리 방향 동기화 및 이동 (teleport로 강제 이동 적용해야 바닐라가 덮어씌우는 것 방지)
                    Location newLoc = dragon.getLocation().add(dir);
                    newLoc.setYaw(p.getLocation().getYaw() - 180f);
                    newLoc.setPitch(p.getLocation().getPitch());
                    dragon.teleport(newLoc);
                } else {
                    // 탑승 중이 아닐 때 제자리 호버링 유지
                    dragon.setPhase(EnderDragon.Phase.HOVER);
                    if (dragon.getLocation().getY() > p.getLocation().getY() + 3) {
                        dragon.setVelocity(new Vector(0, -0.2, 0));
                    } else if (dragon.getLocation().getY() < p.getLocation().getY() + 1) {
                        dragon.setVelocity(new Vector(0, 0.2, 0));
                    } else {
                        dragon.setVelocity(new Vector(0, 0, 0));
                    }
                }
            }
        };
        BukkitTask bTask = taskRunnable.runTaskTimer(plugin, 0L, 1L);
        registerTask(p, bTask);
        flightTaskMap.put(p.getUniqueId(), taskRunnable);
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        if (toothlessMap.containsKey(p.getUniqueId())) {
            EnderDragon dragon = toothlessMap.remove(p.getUniqueId());
            if (dragon != null && dragon.isValid()) {
                dragon.remove();
            }
        }
        if (flightTaskMap.containsKey(p.getUniqueId())) {
            BukkitRunnable task = flightTaskMap.remove(p.getUniqueId());
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
    }

    @Override
    public void reset() {
        super.reset();
        for (EnderDragon dragon : toothlessMap.values()) {
            if (dragon != null && dragon.isValid()) {
                dragon.remove();
            }
        }
        toothlessMap.clear();

        for (BukkitRunnable task : flightTaskMap.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        flightTaskMap.clear();
    }

    // 우클릭 탑승
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (isSilenced(p))
            return;
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        org.bukkit.entity.Entity clicked = e.getRightClicked();
        EnderDragon targetDragon = null;

        // 본체를 클릭한 경우
        if (clicked instanceof EnderDragon dragon) {
            targetDragon = dragon;
        }
        // 부위(Part)를 클릭한 경우 - 1.21버전 호환용 (부위의 getParent가 본체입니다)
        else if (clicked instanceof org.bukkit.entity.EnderDragonPart part) {
            if (part.getParent() instanceof EnderDragon dragon) {
                targetDragon = dragon;
            }
        }

        if (targetDragon != null) {
            if (targetDragon.hasMetadata("HiccupOwner")) {
                String ownerUuid = targetDragon.getMetadata("HiccupOwner").get(0).asString();
                if (p.getUniqueId().toString().equals(ownerUuid)) {
                    // 쿨다운 체크
                    if (p.hasMetadata("HiccupMountCooldown")) {
                        long cooldownTime = p.getMetadata("HiccupMountCooldown").get(0).asLong();
                        if (System.currentTimeMillis() - cooldownTime < 3000) {
                            long remaining = 3000 - (System.currentTimeMillis() - cooldownTime);
                            p.sendMessage("§c[!] §f투슬리스가 휴식 중입니다... (" + String.format("%.1f", remaining / 1000.0)
                                    + "초 후 탑승 가능)");
                            return;
                        }
                    }

                    // 이미 다른 사람이 탔으면 불가
                    if (targetDragon.getPassengers().isEmpty()) {
                        targetDragon.addPassenger(p);
                        p.sendMessage("§e[!] §f투슬리스에 탑승했습니다. (쉬프트키로 하차)");
                    }
                }
            }
        }
    }

    // 쉬프트 하차 (바닐라 로직으로 동작하지만 명시적으로 처리할 경우 추가 가능)
    // 현재는 바닐라 Sneak으로 하차되므로 특별한 처리는 하지 않음

    // 좌클릭 브레스 발사
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (isSilenced(p))
            return;
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            EnderDragon dragon = toothlessMap.get(p.getUniqueId());
            if (dragon == null || !dragon.isValid() || !dragon.getPassengers().contains(p))
                return;

            fireBreath(p, dragon);
        }
    }

    private void fireBreath(Player p, EnderDragon dragon) {
        if (!checkCooldown(p))
            return;
        setCooldown(p, 15);

        // 브레스(DragonFireball) 발사
        // 드래곤 본체 중심이 아니라 머리 위치 부근에서 발사되도록 위치 보정 (전방으로 크게 이동, 위로 약간 이동)
        Location eyeLoc = p.getEyeLocation();
        Vector dir = eyeLoc.getDirection().normalize();

        // 투슬리스 크기가 감소(SCALE=0.5)되어 있으므로 전방으로 3.5, 위로 2.0 정도가 적당 (테스트 결과에 따라 조정 가능)
        Location spawnLoc = dragon.getLocation().add(0, 2.0, 0).add(dir.clone().multiply(3.5));

        DragonFireball fireball = (DragonFireball) p.getWorld().spawnEntity(spawnLoc, EntityType.DRAGON_FIREBALL);
        fireball.setShooter(p);
        // 드래곤이 앞으로 날아가는 속도를 이길 수 있도록 파이어볼 속도를 크게 증가
        fireball.setVelocity(dir.clone().multiply(3.0));
        fireball.setDirection(dir.clone().multiply(3.0));
        fireball.setMetadata("HiccupBreath", new FixedMetadataValue(plugin, p.getUniqueId().toString()));

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.0f, 1.0f);
    }

    // 브레스 적중 시 데미지 및 장판 쿨다운 처리 (사실 DragonFireball 속성상 바닐라에서도 장판생성함 AreaEffectCloud)
    // 데미지 조정 및 AreaEffectCloud 지속시간, 데미지 조정을 위해 Hit 판단 처리
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (e.getEntity() instanceof DragonFireball fireball) {
            if (fireball.hasMetadata("HiccupBreath")) {
                String ownerUuid = fireball.getMetadata("HiccupBreath").get(0).asString();
                Player shooter = Bukkit.getPlayer(UUID.fromString(ownerUuid));

                Location tempHitLoc = fireball.getLocation();
                if (e.getHitBlock() != null)
                    tempHitLoc = e.getHitBlock().getLocation();
                if (e.getHitEntity() != null)
                    tempHitLoc = e.getHitEntity().getLocation();

                final Location hitLoc = tempHitLoc; // effectively final

                // 폭발 데미지 10
                fireball.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, hitLoc, 1);
                fireball.getWorld().playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

                for (org.bukkit.entity.Entity entity : fireball.getWorld().getNearbyEntities(hitLoc, 3.0, 3.0, 3.0)) {
                    if (entity instanceof org.bukkit.entity.LivingEntity le && entity != shooter
                            && entity.getType() != EntityType.ENDER_DRAGON) {
                        le.setMetadata("MOC_LastKiller", new FixedMetadataValue(plugin, ownerUuid));
                        le.damage(10.0, shooter);
                    }
                }

                // 장판은 바닐라 AreaEffectCloud가 생성되는데 이를 찾아서 수정하거나
                // 해당 위치에 직접 태스크로 8초간 장판 구현
                // 바닐라 AreaEffectCloud 놔두고 직접 구현하는 것이 데미지 초당 8 커스텀 제어 용이

                // 바닐라가 만든 AreaEffectCloud 삭제 (깔끔하게 처리)
                // 1틱 뒤에 발생하는 AreaEffectCloud 처리
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (org.bukkit.entity.Entity ent : hitLoc.getWorld().getNearbyEntities(hitLoc, 4.0, 4.0,
                                4.0)) {
                            if (ent instanceof org.bukkit.entity.AreaEffectCloud aec) {
                                aec.remove();
                            }
                        }
                    }
                }.runTaskLater(plugin, 1L);

                // 8초간 8데미지 커스텀 장판 (1초마다 데미지)
                if (shooter != null) {
                    startBreathZoneTask(shooter, hitLoc);
                }
            }
        }
    }

    private void startBreathZoneTask(Player owner, final Location center) {
        BukkitRunnable taskRunnable = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 8 * 20) { // 8초 종료
                    this.cancel();
                    return;
                }

                // 파티클
                // 1.21.11에서 DRAGON_BREATH 파티클은 크기(또는 속도)를 나타내는 Float 데이터를 요구하므로 1.0f를 추가로
                // 넘겨줍니다.
                center.getWorld().spawnParticle(org.bukkit.Particle.DRAGON_BREATH, center, 50, 2.0, 0.5, 2.0, 0.1,
                        1.0f);

                // 1초(20틱)마다 8데미지 부여
                if (ticks % 20 == 0) {
                    for (org.bukkit.entity.Entity ent : center.getWorld().getNearbyEntities(center, 2.5, 1.5, 2.5)) {
                        if (ent instanceof org.bukkit.entity.LivingEntity le && ent != owner
                                && ent.getType() != EntityType.ENDER_DRAGON) {
                            le.setMetadata("MOC_LastKiller",
                                    new FixedMetadataValue(plugin, owner.getUniqueId().toString()));
                            le.damage(8.0, owner);
                        }
                    }
                }
                ticks++;
            }
        };
        BukkitTask bTask = taskRunnable.runTaskTimer(plugin, 0L, 1L);
        registerTask(owner, bTask);
    }

    // 엔더드래곤 바닐라 블럭 파괴 방지 우회
    // 바닐라 엔더드래곤은 날아가면서 블럭을 전부 삭제해버림 (EntityExplodeEvent, EntityChangeBlockEvent)
    @EventHandler
    public void onDragonChangeBlock(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof EnderDragon dragon) {
            if (dragon.hasMetadata("HiccupOwner")) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDragonExplode(EntityExplodeEvent e) {
        if (e.getEntity() instanceof EnderDragon dragon) {
            if (dragon.hasMetadata("HiccupOwner")) {
                e.setCancelled(true);
            }
        }
    }

    // 바닐라 엔더드래곤 접촉 데미지 및 넉백 캔슬
    @EventHandler
    public void onDragonDealVanillaDamage(EntityDamageByEntityEvent e) {
        EnderDragon targetDragon = null;

        if (e.getDamager() instanceof EnderDragon dragon) {
            targetDragon = dragon;
        } else if (e.getDamager() instanceof org.bukkit.entity.EnderDragonPart part) {
            if (part.getParent() instanceof EnderDragon dragon) {
                targetDragon = dragon;
            }
        }

        // 데미지를 입히는 주체가 우리가 소환한 투슬리스일 경우, 바닐라 데미지와 넉백 취소
        // (이로 인해 우리가 직접 구현한 6 데미지 몸통 박치기만 작동하게 됨)
        if (targetDragon != null && targetDragon.hasMetadata("HiccupOwner")) {
            e.setCancelled(true);
        }
    }

    // 소환수가 받는 데미지 처리 및 20% 확률 하차 로직
    @EventHandler
    public void onDragonDamage(EntityDamageEvent e) {
        EnderDragon targetDragon = null;

        if (e.getEntity() instanceof EnderDragon dragon) {
            targetDragon = dragon;
        } else if (e.getEntity() instanceof org.bukkit.entity.EnderDragonPart part) {
            if (part.getParent() instanceof EnderDragon dragon) {
                targetDragon = dragon;
            }
        }

        if (targetDragon != null && targetDragon.hasMetadata("HiccupOwner")) {
            // 낙하 데미지 무시
            if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
                e.setCancelled(true);
                return;
            }

            // 주인이 때린 경우 (브레스 트리거)는 하차 및 데미지 로직 제외
            if (e instanceof EntityDamageByEntityEvent edbe) {
                if (edbe.getDamager() instanceof Player attacker) {
                    String ownerUuid = targetDragon.getMetadata("HiccupOwner").get(0).asString();
                    if (attacker.getUniqueId().toString().equals(ownerUuid)) {
                        return; // onDragonPartDamage에서 브레스 발사 및 취소 처리됨
                    }
                }
            }

            // 데미지를 정상적으로 받았고 취소되지 않았을 때
            if (!e.isCancelled() && e.getDamage() > 0) {
                if (!targetDragon.getPassengers().isEmpty()) {
                    // 20% 확률 판정
                    if (Math.random() <= 0.20) {
                        for (org.bukkit.entity.Entity passenger : targetDragon.getPassengers()) {
                            if (passenger instanceof Player p) {
                                targetDragon.removePassenger(p);
                                Bukkit.broadcastMessage("§f투슬리스가 공격받아 중심을 잃고 히컵이 떨어졌습니다!");
                            }
                        }
                    }
                }
            }
        }
    }

    // 복합 파츠 타격 시 체력 관리 및 브레스 발사 호환용
    @EventHandler
    public void onDragonPartDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            org.bukkit.entity.Entity clicked = e.getEntity();
            EnderDragon targetDragon = null;

            if (clicked instanceof EnderDragon dragon) {
                targetDragon = dragon;
            } else if (clicked instanceof org.bukkit.entity.EnderDragonPart part) {
                if (part.getParent() instanceof EnderDragon dragon) {
                    targetDragon = dragon;
                }
            }

            if (targetDragon != null && targetDragon.hasMetadata("HiccupOwner")) {
                String ownerUuid = targetDragon.getMetadata("HiccupOwner").get(0).asString();
                if (p.getUniqueId().toString().equals(ownerUuid)) {
                    // 주인이 드래곤(자신이 탄 탈것)을 때린 경우 좌클릭으로 간주하여 브레스 발사
                    e.setCancelled(true);
                    if (!AbilityManager.getInstance().hasAbility(p, getCode()))
                        return;
                    if (isSilenced(p))
                        return;
                    if (!targetDragon.getPassengers().contains(p))
                        return;

                    fireBreath(p, targetDragon);
                }
            }
        }
    }

    // 플레이어 사망 시 클린업 (드래곤 제거)
    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (AbilityManager.getInstance().hasAbility(p, getCode())) {
            if (toothlessMap.containsKey(p.getUniqueId())) {
                EnderDragon dragon = toothlessMap.remove(p.getUniqueId());
                if (dragon != null && dragon.isValid()) {
                    dragon.remove();
                }
            }
        }
    }

    // 하차 시 쿨다운 적용 및 튕겨나가는 버그 방지
    @EventHandler
    public void onDragonDismount(org.bukkit.event.entity.EntityDismountEvent e) {
        if (e.getEntity() instanceof Player p && e.getDismounted() instanceof EnderDragon dragon) {
            if (dragon.hasMetadata("HiccupOwner")) {
                // 하차 시 3초 탑승 쿨다운 부여
                p.setMetadata("HiccupMountCooldown", new FixedMetadataValue(plugin, System.currentTimeMillis()));

                // 드래곤의 거대한 힛박스로 인해 튕겨 나가는 것을 방지하기 위해 1틱 뒤에 살짝 위로 텔레포트 및 넉백 무효화
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (p.isValid() && p.isOnline()) {
                            // 플레이어를 드래곤 위쪽 안전한 곳으로 이동
                            Location safeLoc = dragon.getLocation().add(0, 3.5, 0);
                            safeLoc.setYaw(p.getLocation().getYaw());
                            safeLoc.setPitch(p.getLocation().getPitch());
                            p.teleport(safeLoc);

                            // 튕겨나가는 가속도 캔슬 (수직으로 살짝만 띄움)
                            p.setVelocity(new Vector(0, 0.2, 0));
                        }
                    }
                }.runTaskLater(plugin, 1L);
            }
        }
    }

    // 투슬리스와 겹친 상태에서(또는 탑승 중) 피격 시 우주 비행 넉백 버그 강제 차단
    @EventHandler
    public void onPlayerDamageOverlapDragon(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            // 주변에 커스텀 드래곤이 있는지 확인 (탑승 중이거나 근접해 있을 때)
            boolean nearDragon = false;
            if (p.getVehicle() instanceof EnderDragon dragon && dragon.hasMetadata("HiccupOwner")) {
                nearDragon = true;
            } else {
                for (org.bukkit.entity.Entity ent : p.getNearbyEntities(5.0, 5.0, 5.0)) {
                    if (ent instanceof EnderDragon dragon && dragon.hasMetadata("HiccupOwner")) {
                        nearDragon = true;
                        break;
                    }
                }
            }

            if (nearDragon) {
                // 데미지를 입은 직후 1~5틱 동안 비정상적인 가속도(우주 넉백)를 연속으로 초기화하여 완전히 눌러버림
                new BukkitRunnable() {
                    int count = 0;

                    @Override
                    public void run() {
                        if (!p.isValid() || !p.isOnline() || count > 5) {
                            this.cancel();
                            return;
                        }
                        // 완전히 정지하거나 수직으로만 약간 띄움 처리로 바닐라 넉백 강제 제거
                        p.setVelocity(new Vector(0, -0.1, 0));
                        count++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
        }
    }

}
