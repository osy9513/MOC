package me.user.moc.ability.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;

public class Mario extends Ability {

    // 현재 커져있는(슈퍼 마리오) 상태인지 확인하는 맵
    private final Set<java.util.UUID> isSuperMario = ConcurrentHashMap.newKeySet();
    // 힙드롭/스매시 진행 중인지 확인하는 맵
    private final Set<java.util.UUID> isSmashing = ConcurrentHashMap.newKeySet();
    // 힙드롭 직후 1초간 낙하 대미지 면역을 부여하기 위한 타임스탬프 맵
    private final java.util.Map<java.util.UUID, Long> smashLandingTimes = new ConcurrentHashMap<>();

    // 힙드롭 1초 쿨타임을 위한 타임스탬프 맵
    private final java.util.Map<java.util.UUID, Long> smashCooldowns = new ConcurrentHashMap<>();

    // 15초 지속 타이머 관리용 (체력이 10칸 이하가 되면 이 타이머를 강제 중지해야 함)
    private final java.util.Map<java.util.UUID, org.bukkit.scheduler.BukkitTask> superMarioTasks = new ConcurrentHashMap<>();

    public Mario(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "073";
    }

    @Override
    public String getName() {
        return "마리오";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList("§e복합 ● 마리오(마리오)", "§f버섯을 먹고 커집니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§b복합 ● 마리오(마리오)");
        p.sendMessage("§f슈퍼버섯 우클릭 시 15초간 추가 체력 5칸과 점프 강화2 버프가 걸립니다.");
        p.sendMessage("§f점프 상태에서 쉬프트 누를 경우 힙 드롭을 합니다.");
        p.sendMessage("§f힙 드롭 시 반경 11x11 범위에 8 데미지를 주고 적중 시 자동으로 점프합니다.");
        p.sendMessage("§f힙 드롭은 1.5초의 쿨타임이 있습니다.");
        p.sendMessage("§f힙 드롭 착지 시 1.5초간 낙하 데미지는 받지 않습니다.");
        p.sendMessage(" ");
        p.sendMessage("§7[패시브] 마리오의 체력이 10칸 이하가 되면 크기가 작아집니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 15초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 슈퍼버섯");
        p.sendMessage("§f제거 장비 : 철칼");
    }

    @Override
    public void giveItem(Player p) {
        detailCheck(p);
        // 장비 제거: 철칼
        p.getInventory().remove(Material.IRON_SWORD);

        // 추가 장비: 슈퍼버섯 (붉은 버섯, CustomModelData 1)
        ItemStack mushroom = new ItemStack(Material.RED_MUSHROOM);
        ItemMeta meta = mushroom.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c슈퍼버섯");
            meta.setCustomModelData(1); // 리소스팩: supermraio.json
            meta.setLore(Arrays.asList("§f우클릭을 하여", "§f15초간 슈퍼 마리오로 변신합니다."));
            mushroom.setItemMeta(meta);
        }
        p.getInventory().addItem(mushroom);
    }

    // 버섯 팝콘 이펙트 2초 지속 헬퍼 메서드
    private void spawnPopcornParticle(Player p, Material mushroomType) {
        ItemStack item = new ItemStack(mushroomType);
        if (mushroomType == Material.RED_MUSHROOM) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(1);
                item.setItemMeta(meta);
            }
        }

        org.bukkit.scheduler.BukkitTask particleTask = new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 10 || !p.isOnline() || p.isDead()) {
                    this.cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.ITEM, p.getLocation().add(0, 1, 0), 10, 0.4, 0.5, 0.4, 0.15, item);
                count++;
            }
        }.runTaskTimer(plugin, 0L, 4L); // 4틱 단위 10번 = 40틱(2초)
        registerTask(p, particleTask);
    }

    /**
     * 기본 크기(1.0)로 복구 (꼬마 탈출)
     */
    private void restoreToNormal(Player p) {
        if (p.getAttribute(Attribute.SCALE) != null) {
            p.getAttribute(Attribute.SCALE).setBaseValue(1.0);
        }
        spawnPopcornParticle(p, Material.RED_MUSHROOM);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        p.sendMessage("§a다시 커졌다!");
    }

    /**
     * 꼬마 마리오로 변신하는 로직 (크기 0.5배, 추가 버프 해제)
     */
    private void shrinkToMini(Player p) {
        boolean wasSuper = isSuperMario.contains(p.getUniqueId());

        isSuperMario.remove(p.getUniqueId());

        // 크기 조절: 0.5배
        if (p.getAttribute(Attribute.SCALE) != null) {
            p.getAttribute(Attribute.SCALE).setBaseValue(0.5);
        }

        spawnPopcornParticle(p, Material.BROWN_MUSHROOM);

        if (wasSuper) {
            // 작아지면 슈퍼버섯 효과 제거.
            p.removePotionEffect(PotionEffectType.JUMP_BOOST);
            // 작동 중인 슈퍼버섯 15초 유지 타이머가 있다면 즉시 취소
            if (superMarioTasks.containsKey(p.getUniqueId())) {
                org.bukkit.scheduler.BukkitTask t = superMarioTasks.remove(p.getUniqueId());
                if (t != null && !t.isCancelled()) {
                    t.cancel();
                }
            }
            p.playSound(p.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1.0f, 0.5f);
        }
    }

    /**
     * 슈퍼 마리오로 성장하는 로직 (크기 1.5배, 체력 40(기본 30+10), 점프 강화 부여)
     */
    private void growToSuper(Player p) {
        isSuperMario.add(p.getUniqueId());

        // 크기 조절: 1.5배
        if (p.getAttribute(Attribute.SCALE) != null) {
            p.getAttribute(Attribute.SCALE).setBaseValue(1.5);
        }

        spawnPopcornParticle(p, Material.RED_MUSHROOM);

        // 효과음 재생
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        // 추가 장비(버프): 기존 60HP(30칸)에서 -> 70HP(35칸)으로 5칸(10) 증가. 체력 +10 회복, 점프 강화2
        if (p.getAttribute(Attribute.MAX_HEALTH) != null) {
            p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(70.0);
        }
        // 버섯을 먹을 때만 +10 무조건 추가
        p.setHealth(Math.min(70.0, p.getHealth() + 10.0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 300, 1, false, false, true)); // 15초 = 300틱, 증폭
                                                                                                      // 1 = 레벨 2

        // 기존 유지 타이머 취소 (중첩 방지)
        if (superMarioTasks.containsKey(p.getUniqueId())) {
            superMarioTasks.get(p.getUniqueId()).cancel();
        }

        // 15초 후 지속시간 종료 시 원상복구(크기 1.0) 태스크 등록
        org.bukkit.scheduler.BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline() && isSuperMario.contains(p.getUniqueId())) {
                    // 15초가 온전히 지나면 꼬마 마리오가 아니라 기본 크기(1.0) 및 체력복구.
                    // shrinkToMini는 꼬마 마리오(0.5) 강제 세팅이므로 여기서는 별도 처리해야 함.
                    isSuperMario.remove(p.getUniqueId());

                    if (p.getAttribute(Attribute.SCALE) != null) {
                        p.getAttribute(Attribute.SCALE).setBaseValue(1.0);
                    }
                    if (p.getAttribute(Attribute.MAX_HEALTH) != null) {
                        p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(60.0);
                    }
                    if (p.getHealth() > 60.0) {
                        p.setHealth(60.0);
                    }
                    p.removePotionEffect(PotionEffectType.JUMP_BOOST);
                    p.playSound(p.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1.0f, 0.5f);

                    spawnPopcornParticle(p, Material.BROWN_MUSHROOM);

                    p.sendMessage("§c다시 크기가 줄었다!");
                }
                superMarioTasks.remove(p.getUniqueId());
            }
        }.runTaskLater(plugin, 300L); // 15초 후 실행
        superMarioTasks.put(p.getUniqueId(), task);
        registerTask(p, task);
    }

    // 발동 이벤트 (슈퍼버섯 우클릭)
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        if (isSilenced(p))
            return;

        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack handItem = p.getInventory().getItemInMainHand();
        if (handItem.getType() != Material.RED_MUSHROOM)
            return;

        ItemMeta meta = handItem.getItemMeta();
        if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != 1)
            return;

        e.setCancelled(true);

        if (!checkCooldown(p))
            return;

        // 발동
        growToSuper(p);

        // 쿨타임 15초 적용
        setCooldown(p, 15.0);
    }

    // 힙드롭 (쉬프트 감지) 이벤트
    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();

        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR)
            return;

        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        if (isSilenced(p))
            return;
        // [수정] 전투 시작 전(평화 시간 등)에는 힙 드롭 사용 불가
        if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        // 크기(1.5, 1.0, 0.5 상관없이) 점프 상태에서 스니킹을 시도할 때 발동
        if (e.isSneaking()) {
            // 공중에 있는지 확인
            if (!((org.bukkit.entity.Entity) p).isOnGround()) {
                // 이미 스매싱 중이면 중복 실행 방지
                if (isSmashing.contains(p.getUniqueId()))
                    return;

                // 쿨타임 1.5초(1500ms) 확인
                Long lastSmash = smashCooldowns.get(p.getUniqueId());
                if (lastSmash != null && System.currentTimeMillis() - lastSmash < 1500L) {
                    long remain = 1500L - (System.currentTimeMillis() - lastSmash);
                    p.sendActionBar("§c[힙 드롭] 쿨타임 (" + String.format("%.1f", remain / 1000.0) + "초)");
                    return; // 쿨타임 중
                }

                isSmashing.add(p.getUniqueId());
                smashCooldowns.put(p.getUniqueId(), System.currentTimeMillis());

                // 1.5초 후 쿨타임 초기화 알림
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline() && AbilityManager.getInstance().hasAbility(p, getCode())) {
                        p.sendActionBar("§a[힙 드롭] 사용 가능");
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    }
                }, 30L); // 1.5초

                // 대사 출력 (힙 드롭 할 때 이얏후~)
                Bukkit.broadcastMessage("§c마리오: 이얏후~");

                // 강하 (Velocity -y)
                p.setVelocity(new Vector(0, -1.8, 0));
                p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 0.5f);

                // 바닥 착지를 감지하는 틱 검사 스케줄러
                org.bukkit.scheduler.BukkitTask smashCheckTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!p.isOnline() || p.isDead() || !isSmashing.contains(p.getUniqueId())) {
                            isSmashing.remove(p.getUniqueId());
                            this.cancel();
                            return;
                        }

                        if (((org.bukkit.entity.Entity) p).isOnGround()) {
                            // 착지!
                            isSmashing.remove(p.getUniqueId());

                            // [수정] 낙하 데미지 면역 시간 연장 (1초 -> 1.5초) 빛 중복 힙드롭 시 낙뎀 피격 방지
                            smashLandingTimes.put(p.getUniqueId(), System.currentTimeMillis() + 1500L); // 1.5초간 낙하 대미지
                                                                                                        // 피격 면역

                            Location loc = p.getLocation();
                            // 돌풍구 파티클 효과 11x11 (반경 5.5) 범위로 화려하게 출력
                            p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
                            p.getWorld().spawnParticle(Particle.GUST, loc, 120, 5.5, 0.5, 5.5, 0.1);
                            p.getWorld().spawnParticle(Particle.CLOUD, loc, 100, 5.5, 0.5, 5.5, 0.1);
                            p.getWorld().playSound(loc, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 0.8f);
                            p.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);

                            // 착지 시 주변 반경 11x11 범위(직경 약 11, 박스 충돌)에 데미지 적용
                            boolean hitSomething = false;
                            for (org.bukkit.entity.Entity target : p.getNearbyEntities(5.5, 5.5, 5.5)) {
                                if (target instanceof LivingEntity le && target != p) {
                                    le.damage(8.0, p);
                                    hitSomething = true;
                                }
                            }

                            // 데미지를 줬다면 자동 점프(Velocity +y) 발동
                            if (hitSomething) {
                                p.setVelocity(new Vector(0, 1.0, 0));
                                p.playSound(loc, Sound.ENTITY_SLIME_JUMP, 1.0f, 1.0f);
                            }
                            this.cancel();
                        }
                    }
                }.runTaskTimer(plugin, 1L, 1L);
                registerTask(p, smashCheckTask);
            }
        }
    }

    // 패시브: 피격/회복 시 체력 계산하여 작아지는 판정 추가
    @EventHandler
    public void onHealthCheck(EntityDamageEvent e) {
        // [수정] 전투 시작 전(평화 시간 등)에는 작아지는 판정을 하지 않음
        if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        if (e.getEntity() instanceof Player p) {
            if (AbilityManager.getInstance().hasAbility(p, getCode())) {

                // 힙 드롭 직후 낙하 대미지 면역
                if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    Long immuneTime = smashLandingTimes.get(p.getUniqueId());
                    // 힙드롭 중이거나 면역 시간 내라면 무조건 낙뎀 무시
                    if (isSmashing.contains(p.getUniqueId())
                            || (immuneTime != null && System.currentTimeMillis() <= immuneTime)) {
                        e.setDamage(0);
                        e.setCancelled(true);
                        return;
                    }
                }

                // 피해가 들어간 후 체력을 확인해야 하므로 1틱 딜레이 검사 수행
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!p.isOnline() || p.isDead())
                        return;
                    // 크기가 정상 이상(1.0 이상)일 때 체력이 20 이하가 되면 꼬마로 축소
                    if (p.getHealth() <= 20.0) {
                        if (p.getAttribute(Attribute.SCALE) != null
                                && p.getAttribute(Attribute.SCALE).getBaseValue() > 0.6) {
                            shrinkToMini(p);
                            p.sendMessage("§c작아졌다!");
                        }
                    }
                }, 1L);
            }
        }
    }

    // 체력 회복 시 원래 크기로 돌아감
    @EventHandler
    public void onHealthRegain(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (AbilityManager.getInstance().hasAbility(p, getCode())) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!p.isOnline() || p.isDead())
                        return;
                    // 체력이 21 이상 회복되었고, 현재 꼬마 마리오(0.5)라면 원래 크기(1.0) 복구
                    if (p.getHealth() >= 21.0) {
                        if (p.getAttribute(Attribute.SCALE) != null
                                && p.getAttribute(Attribute.SCALE).getBaseValue() <= 0.6) {
                            restoreToNormal(p);
                        }
                    }
                }, 5L);
            }
        }
    }

    @Override
    public void reset() {
        super.reset();
        isSuperMario.clear();
        isSmashing.clear();
        smashLandingTimes.clear();
        superMarioTasks.clear();
        smashCooldowns.clear();
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        if (p == null)
            return;

        // 능력 제거 시 몸집 원상 복구 (크기 1.0)
        boolean wasSuper = isSuperMario.contains(p.getUniqueId());

        isSuperMario.remove(p.getUniqueId());
        isSmashing.remove(p.getUniqueId());

        if (p.getAttribute(Attribute.SCALE) != null) {
            p.getAttribute(Attribute.SCALE).setBaseValue(1.0);
        }

        p.removePotionEffect(PotionEffectType.JUMP_BOOST);

        // 이전 크기(슈퍼냐 꼬마냐)에 따라 파티클 출력
        if (wasSuper) {
            ItemStack brownMushroom = new ItemStack(Material.BROWN_MUSHROOM);
            p.getWorld().spawnParticle(Particle.ITEM, p.getLocation().add(0, 1, 0), 40, 0.4, 0.5, 0.4, 0.15,
                    brownMushroom);
        } else {
            ItemStack superMushroom = new ItemStack(Material.RED_MUSHROOM);
            ItemMeta meta = superMushroom.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(1);
                superMushroom.setItemMeta(meta);
            }
            p.getWorld().spawnParticle(Particle.ITEM, p.getLocation().add(0, 1, 0), 50, 0.4, 0.5, 0.4, 0.15,
                    superMushroom);
        }
    }
}
