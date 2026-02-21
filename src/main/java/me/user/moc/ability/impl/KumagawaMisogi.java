package me.user.moc.ability.impl;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class KumagawaMisogi extends Ability {

    // 플레이어 UUID -> 북 메이커 활성화 여부
    private final Map<UUID, Boolean> bookMakerActive = new HashMap<>();

    // 플레이어 UUID -> 북 메이커 발동 대기 중 (무적 상태)
    private final Set<UUID> bookMakerPending = new HashSet<>();

    // 플레이어 UUID -> 이펙트 태스크 (파티클)
    private final Map<UUID, BukkitRunnable> effectTasks = new HashMap<>();

    // 나사 투사체 ID -> 발사한 플레이어 UUID
    private final Map<Integer, UUID> screwProjectiles = new HashMap<>();

    // 북 메이커 피격 대상 UUID -> 위더 머리 시각 효과 (ItemDisplay)
    private final Map<UUID, ItemDisplay> witherVisuals = new HashMap<>();

    public KumagawaMisogi(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "053";
    }

    @Override
    public String getName() {
        return "쿠마가와 미소기";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§c유틸 ● 쿠마가와 미소기(메다카 박스)",
                "§f올 픽션과 북 메이커를 사용합니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 기존 아이템 제거 (철 칼, 철 흉갑)
        p.getInventory().remove(Material.IRON_SWORD);
        p.getInventory().remove(Material.IRON_CHESTPLATE);
        // [FIX] 장착 중인 아이템도 확실하게 제거
        if (p.getInventory().getChestplate() != null
                && p.getInventory().getChestplate().getType() == Material.IRON_CHESTPLATE) {
            p.getInventory().setChestplate(null);
        }
        if (p.getInventory().getItemInMainHand().getType() == Material.IRON_SWORD) {
            p.getInventory().setItemInMainHand(null);
        }

        // 나사 (엔드 막대기) 지급
        ItemStack screw = new ItemStack(Material.END_ROD);
        ItemMeta meta = screw.getItemMeta();
        meta.displayName(Component.text("§f나사"));
        meta.setLore(List.of("§7우클릭 시 나사를 전방으로 발사합니다.", "§75의 데미지를 주며 15칸 날아갑니다."));
        meta.setCustomModelData(1); // 리소스팩 적용
        screw.setItemMeta(meta);
        p.getInventory().addItem(screw);

        // 최대 체력 초기화 (20HP / 1번 줄었다고 가정? 기획서엔 '체력이 1줄로 줄어듭니다'라고 되어 있음)
        // 마인크래프트 기본 체력은 20(하트 10칸)입니다. '체력 1줄'이 하트 10칸을 의미하는 것 같습니다.
        // 기획서: "체력 1줄로 줄어듭니다." -> 원래 20이니 그대로 둠.
        // 하지만 "체력 10칸 = 체력 1줄"이라는 설명이 있으므로, 20HP로 설정.
        AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0);
        }
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());

        // 상태 초기화
        bookMakerActive.put(p.getUniqueId(), false);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c유틸 ● 쿠마가와 미소기(메다카 박스)");
        p.sendMessage("§f체력이 1줄로 시작합니다.");
        p.sendMessage("§f사망에 이르는 피해를 입으면 '올 픽션'이 발동하여");
        p.sendMessage("§f최대 체력이 1칸 줄어든 상태로 부활합니다.");
        p.sendMessage("§f최대 체력이 5칸이 되면 5초 후 '북 메이커'가 활성화됩니다.");
        p.sendMessage("§f북 메이커 활성화 시 나사로 생명체를 공격하면");
        p.sendMessage("§f대상의 최대 체력을 5칸으로 만들고 능력을 제거하며");
        p.sendMessage("§f영구적으로 위더 상태를 부여합니다.");
        p.sendMessage("§f쿠마가와 미소기는 위더에 면역입니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 나사 발사 5초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 나사(엔드 막대기)");
        p.sendMessage("§f장비 제거 : 철 칼, 철 흉갑");
    }

    @Override
    public void cleanup(Player p) {
        bookMakerActive.remove(p.getUniqueId());
        bookMakerPending.remove(p.getUniqueId());
        screwProjectiles.values().removeIf(uuid -> uuid.equals(p.getUniqueId()));
        if (effectTasks.containsKey(p.getUniqueId())) {
            effectTasks.get(p.getUniqueId()).cancel();
            effectTasks.remove(p.getUniqueId());
        }

        // 내가 건 위더 비주얼 제거? (아니면 대상이 죽을 때 제거)
        // cleanup은 플레이어(능력자)가 나갈 때 호출됨.
        // 여기서는 위더 비주얼 맵을 정리하는 로직이 필요하지만, 위더 비주얼의 키는 '대상'임.
        // 따라서 '나'의 cleanup 시점에서는 내가 건 비주얼을 추적할 수 없음 (설계상).
        // 다만 기획 의도상 북 메이커는 '영구 지속'이므로, 내가 나가도 대상의 위더는 유지되어야 함이 맞을 수 있음.
        // 만약 내가 '대상'인 경우라면?
        if (witherVisuals.containsKey(p.getUniqueId())) {
            ItemDisplay display = witherVisuals.remove(p.getUniqueId());
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
    }

    @Override
    public void reset() {
        super.reset();
        bookMakerActive.clear();
        screwProjectiles.clear();

        // 모든 위더 비주얼 제거
        for (ItemDisplay display : witherVisuals.values()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        witherVisuals.clear();
    }

    // 나사(엔드 막대기) 설치 방지 및 발사 처리
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!hasAbility(p))
            return;

        if (e.hasItem() && e.getItem().getType() == Material.END_ROD) {
            // 설치 방지
            if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                e.setCancelled(true);
            }

            // 우클릭 발사
            if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                if (!checkCooldown(p))
                    return;

                launchScrew(p);
                setCooldown(p, 5); // 쿨타임 5초
            }
        }
    }

    private void launchScrew(Player p) {
        // [수정] 머리(EyeLocation)가 아닌, 몸의 중앙(Location + Y 오프셋 1.0)에서 발사되도록 수정
        Location loc = p.getLocation().add(0, 0.7, 0);
        Vector dir = p.getLocation().getDirection().normalize().multiply(1.5); // 눈덩이 속도 정도

        ArmorStand screw = p.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false); // 중력 영향 X (직선 발사)
            as.setSmall(true);
            as.setMarker(true);
            as.setMarker(true);
            ItemStack helmet = new ItemStack(Material.END_ROD);
            ItemMeta meta = helmet.getItemMeta();
            meta.setCustomModelData(1); // 리소스팩 적용
            helmet.setItemMeta(meta);
            as.getEquipment().setHelmet(helmet);
            // 진행 방향으로 머리 회전
            as.setHeadPose(getHeadPose(dir));
        });

        // 투사체 등록 (누가 쐈는지)
        screwProjectiles.put(screw.getEntityId(), p.getUniqueId());
        registerSummon(p, screw); // cleanup시 제거되도록 등록

        // 15칸(약 15틱 ~ 20틱) 날아가도록 스케줄러 실행
        new BukkitRunnable() {
            int tick = 0;
            final int maxTick = 20;

            @Override
            public void run() {
                if (tick >= 10 || screw.isDead() || !screw.isValid()) {
                    screw.remove();
                    screwProjectiles.remove(screw.getEntityId());
                    this.cancel();
                    return;
                }

                // 이동
                Location current = screw.getLocation();
                Location next = current.clone().add(dir);

                // 충돌 검사 (블록)
                if (!next.getBlock().isPassable()) {
                    screw.remove();
                    screwProjectiles.remove(screw.getEntityId());
                    this.cancel();
                    return;
                }

                // 이동 반영
                screw.teleport(next);

                // 충돌 검사 (엔티티)
                for (Entity target : screw.getWorld().getNearbyEntities(next, 0.8, 0.8, 0.8)) {
                    if (target instanceof LivingEntity living && !target.equals(p)) {
                        if (target instanceof ArmorStand)
                            continue; // 다른 투사체 무시
                        if (target instanceof Player tp && tp.getGameMode() == GameMode.SPECTATOR)
                            continue;

                        // 데미지 5 적용
                        living.damage(5.0, p);

                        // 북 메이커 로직 적용
                        applyBookMakerEffect(p, living);

                        screw.remove();
                        screwProjectiles.remove(screw.getEntityId());
                        this.cancel();
                        return;
                    }
                }

                tick++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // 머리 각도 계산 (벡터 -> 오일러 각)
    private org.bukkit.util.EulerAngle getHeadPose(Vector v) {
        double x = v.getX();
        double y = v.getY();
        double z = v.getZ();
        double dist = Math.sqrt(x * x + z * z);
        double pitch = Math.atan2(y, dist); // 마인크래프트는 위가 -pitch일 수 있음. 테스트 필요.
        double yaw = Math.atan2(-x, z);
        return new org.bukkit.util.EulerAngle(-pitch + Math.PI / 2, 0, 0); // +90도 해서 눕힘
    }

    // 북 메이커 효과 적용 (공격 성공 시)
    private void applyBookMakerEffect(Player attacker, LivingEntity target) {
        if (!bookMakerActive.getOrDefault(attacker.getUniqueId(), false))
            return;

        // "북 메이커 대상이 된 사람은 위더 1이 영구 지속됩니다."
        // [New] 중복 적중 시 위더 레벨 강화
        int amplifier = 0;
        PotionEffect currentWither = target.getPotionEffect(PotionEffectType.WITHER);
        if (currentWither != null) {
            amplifier = Math.min(4, currentWither.getAmplifier() + 1); // 최대 4 (Wither V)

            // 강화 이펙트
            target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getEyeLocation(), 5, 0.2, 0.2, 0.2, 0.1);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_HURT, 0.5f, 1.2f);

            if (target instanceof Player pTarget) {
                pTarget.sendMessage("§c§l[!] §c북 메이커가 더욱 강력하게 파고듭니다! (위더 레벨: " + (amplifier + 1) + ")");
            }
        }
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, PotionEffect.INFINITE_DURATION, amplifier));

        // [New] 머리 위 위더 스켈레톤 해골 표시 (ItemDisplay)
        if (!witherVisuals.containsKey(target.getUniqueId())) {
            ItemDisplay display = target.getWorld().spawn(target.getEyeLocation().add(0, 0.5, 0), ItemDisplay.class,
                    d -> {
                        d.setItemStack(new ItemStack(Material.WITHER_SKELETON_SKULL));
                        d.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                        // Transformation 관련 코드 제거 (API 호환성 문제)
                    });
            witherVisuals.put(target.getUniqueId(), display);

            // 따라다니는 태스크
            new BukkitRunnable() {
                float yaw = 0;

                @Override
                public void run() {
                    if (!target.isValid() || target.isDead() || !display.isValid()
                            || !witherVisuals.containsKey(target.getUniqueId())) {
                        if (display.isValid())
                            display.remove();
                        witherVisuals.remove(target.getUniqueId());
                        this.cancel();
                        return;
                    }

                    Location loc = target.getEyeLocation().add(0, 0.5, 0);
                    loc.setYaw(yaw);
                    display.teleport(loc);
                    yaw += 5; // 빙글빙글 회전
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }

        // "해당 생명체의 최대 체력은 미소기와 동일하게 5칸(10)이 되고"
        if (target instanceof Player playerTarget) {
            plugin.getServer().broadcast(Component.text("§c북 메이커를 피하지 않고 받아주면 안될까?")); // 전체 출력

            AttributeInstance maxHealth = playerTarget.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(10.0);
            }
            if (playerTarget.getHealth() > 10.0) {
                playerTarget.setHealth(10.0);
            }

            // "만약 플레이어인 경우엔 능력이 제거됩니다."
            AbilityManager.getInstance().cleanup(playerTarget); // 능력 정리
            AbilityManager.getInstance().setPlayerAbility(playerTarget.getUniqueId(), null); // 능력 삭제

            // 갑옷 제거 (투구, 흉갑, 레깅스, 부츠 -> null)
            playerTarget.getInventory().setArmorContents(null);

            // 머리에 검은 가루 이펙트 (1.5초)
            new BukkitRunnable() {
                int tick = 0;

                @Override
                public void run() {
                    if (tick >= 30) { // 1.5초 = 30틱
                        this.cancel();
                        return;
                    }
                    Location head = playerTarget.getEyeLocation();
                    head.getWorld().spawnParticle(Particle.ASH, head, 5, 0.3, 0.3, 0.3, 0.0);
                    // 발 끝으로 내려감? 랜덤하게 몸 주변에 뿌려줌
                    Location body = playerTarget.getLocation().add(0, 1.0 - (tick / 30.0), 0);
                    body.getWorld().spawnParticle(Particle.ASH, body, 2, 0.2, 0.2, 0.2, 0.0);
                    tick++;
                }
            }.runTaskTimer(plugin, 0L, 1L);

        } else {
            // 생명체(몹)인 경우
            AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(10.0);
            }
            if (target.getHealth() > 10.0) {
                target.setHealth(10.0);
            }
        }
    }

    // [New] 나사 평타 공격 시 북 메이커 발동
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker))
            return;
        if (!hasAbility(attacker))
            return;
        if (!(e.getEntity() instanceof LivingEntity target))
            return;

        // 나사(END_ROD)로 공격했는지 확인
        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (mainHand.getType() == Material.END_ROD) {
            // 북 메이커 로직 적용
            applyBookMakerEffect(attacker, target);
        }
    }

    // 올 픽션 (사망 방지 및 부활)
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p))
            return;
        if (!hasAbility(p))
            return;

        // [New] 북 메이커가 활성화된 상태라면 올 픽션 발동 불가 (일반 사망 처리)
        if (bookMakerActive.getOrDefault(p.getUniqueId(), false)) {
            return;
        }

        // [New] 북 메이커 발동 대기 중(각성 중)에는 무적
        if (bookMakerPending.contains(p.getUniqueId())) {
            e.setCancelled(true);
            return;
        }

        // 치명적인 데미지인지 확인
        if (p.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true); // 사망 취소

            // "사망 시 올 픽션이 자동 발동되며"
            plugin.getServer().broadcast(Component.text("§b쿠마가와 미소기: 올 픽션. 없던 걸로 했어."));

            // "최대 체력 1칸(2)이 줄어든 채 제자리에서 되살아납니다."
            AttributeInstance maxHealthAttr = p.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                double currentMax = maxHealthAttr.getValue();
                double newMax = Math.max(2.0, currentMax - 2.0); // 최소 1칸(2) 유지
                maxHealthAttr.setBaseValue(newMax);

                // "올 픽션으로 부활 시 풀 피로 부활함."
                p.setHealth(newMax);

                // "부활 시 몸에 1초 정도 나뭇잎 떨어지듯 책이 흩날리며 떨어짐."
                playResurrectionEffect(p);

                // "최대 체력이 5칸(10)이 되면 올 픽션이 사라지며,"
                if (newMax <= 10.0) {
                    // "5초 뒤 북 메이커가 활성화 됩니다."
                    if (!bookMakerActive.getOrDefault(p.getUniqueId(), false)
                            && !bookMakerPending.contains(p.getUniqueId())) {
                        scheduleBookMakerActivation(p);
                    }
                } else {
                    // 올 픽션이 사라지지 않았으므로, 추가 패널티 없음
                }
            }
        }
    }

    // 위더 면역
    @EventHandler
    public void onPotionEffect(org.bukkit.event.entity.EntityPotionEffectEvent e) {
        if (e.getEntity() instanceof Player p && hasAbility(p)) {
            if (e.getNewEffect() != null && e.getNewEffect().getType() == PotionEffectType.WITHER) {
                e.setCancelled(true);
            }
        }
    }

    // 대상 사망 시 위더 머리 제거
    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (witherVisuals.containsKey(e.getEntity().getUniqueId())) {
            ItemDisplay display = witherVisuals.remove(e.getEntity().getUniqueId());
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
    }

    private void playResurrectionEffect(Player p) {
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= 20) { // 1초
                    this.cancel();
                    return;
                }
                Location loc = p.getLocation().add(0, 2.0, 0);
                // 책장 파괴 효과 or 인챈트 효과로 대체 (책 흩날리는 느낌)
                p.getWorld().spawnParticle(Particle.ENCHANT, loc, 10, 0.5, 0.5, 0.5, 0.5);
                // 또는 아이템 파티클 (책)
                p.getWorld().spawnParticle(Particle.ITEM, loc, 3, 0.3, 0.3, 0.3, 0.1, new ItemStack(Material.BOOK));
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void scheduleBookMakerActivation(Player p) {
        // 중복 방지
        if (bookMakerPending.contains(p.getUniqueId()))
            return;

        bookMakerPending.add(p.getUniqueId());

        // "올 픽션이 사라질 때 아래의 메세지 전체 채팅에 출력."
        // "쿠마가와 미소기: 나는 나쁘지 않아"
        plugin.getServer().broadcast(Component.text("§c쿠마가와 미소기: 나는 나쁘지 않아"));

        new BukkitRunnable() {
            @Override
            public void run() {
                // 대기 상태 해제
                bookMakerPending.remove(p.getUniqueId());

                if (p.isOnline() && hasAbility(p)) {
                    bookMakerActive.put(p.getUniqueId(), true);
                    p.sendMessage("§5[System] 북 메이커가 활성화되었습니다.");
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.0f);

                    // [New] 활성화 이펙트 (경험치 파티클) 태스크 시작
                    BukkitRunnable task = new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!p.isOnline() || !bookMakerActive.getOrDefault(p.getUniqueId(), false)) {
                                this.cancel();
                                return;
                            }
                            // [New] 몸에서 책이 떨어지는 이펙트
                            // Enchantment Table 파티클 + 책 아이템 파티클 섞기
                            p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1, 0), 10, 0.5, 0.5,
                                    0.5, 0.5);
                            p.getWorld().spawnParticle(Particle.ITEM, p.getLocation().add(0, 2.5, 0), 3, 0.3, 0.3, 0.3,
                                    0.1, new ItemStack(Material.BOOK));
                        }
                    };
                    task.runTaskTimer(plugin, 0L, 5L); // 0.25초마다
                    effectTasks.put(p.getUniqueId(), task);
                }
            }
        }.runTaskLater(plugin, 100L); // 5초 = 100틱
    }

    private boolean hasAbility(Player p) {
        return AbilityManager.getInstance().hasAbility(p, getCode());
    }

    // [New] 나사(End Rod) 설치 방지
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (hasAbility(e.getPlayer()) && e.getBlock().getType() == Material.END_ROD) {
            e.setCancelled(true);
        }
    }
}
