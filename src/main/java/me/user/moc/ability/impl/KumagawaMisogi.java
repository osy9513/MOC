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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
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

public class KumagawaMisogi extends Ability {

    // 북 메이커 활성화 여부를 저장하는 맵 (UUID -> 활성화 여부)
    private final Map<UUID, Boolean> bookMakerActive = new HashMap<>();

    // 나사 엔티티(아머스탠드)가 누구 것인지 추적 (Screw Entity ID -> Owner UUID)
    private final Map<Integer, UUID> screwProjectiles = new HashMap<>();

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

        // 나사 (엔드 막대기) 지급
        ItemStack screw = new ItemStack(Material.END_ROD);
        ItemMeta meta = screw.getItemMeta();
        meta.displayName(Component.text("§f나사"));
        meta.setLore(List.of("§7우클릭 시 나사를 전방으로 발사합니다.", "§75의 데미지를 주며 15칸 날아갑니다."));
        // meta.setCustomModelData(1); // 나중에 리소스팩 적용 시
        screw.setItemMeta(meta);
        p.getInventory().addItem(screw);

        // 최대 체력 초기화 (20HP / 1번 줄었다고 가정? 기획서엔 '체력이 1줄로 줄어듭니다'라고 되어 있음)
        // 마인크래프트 기본 체력은 20(하트 10칸)입니다. '체력 1줄'이 하트 10칸을 의미하는 것 같습니다.
        // 기획서: "체력이 1줄로 줄어듭니다." -> 원래 20이니 그대로 둠.
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
        p.sendMessage("§f체력이 1줄(20)로 시작합니다.");
        p.sendMessage("§f사망에 이르는 피해를 입으면 '올 픽션'이 발동하여");
        p.sendMessage("§f최대 체력이 1칸(2) 줄어든 상태로 부활합니다.");
        p.sendMessage("§f최대 체력이 5칸(10)이 되면 5초 후 '북 메이커'가 활성화됩니다.");
        p.sendMessage("§f북 메이커 활성화 시 나사로 생명체를 공격하면");
        p.sendMessage("§f대상의 최대 체력을 5칸(10)으로 만들고 능력을 제거하며");
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
        super.cleanup(p);
        bookMakerActive.remove(p.getUniqueId());
        // 최대 체력 복구는 하지 않음 (게임 끝나면 알아서 리셋될 것)
    }

    @Override
    public void reset() {
        super.reset();
        bookMakerActive.clear();
        screwProjectiles.clear();
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
        Location loc = p.getEyeLocation().subtract(0, 0.2, 0);
        Vector dir = p.getLocation().getDirection().normalize().multiply(1.5); // 눈덩이 속도 정도

        ArmorStand screw = p.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false); // 중력 영향 X (직선 발사)
            as.setSmall(true);
            as.setMarker(true);
            as.getEquipment().setHelmet(new ItemStack(Material.END_ROD));
            // 진행 방향으로 머리 회전
            as.setHeadPose(getHeadPose(dir));
        });

        // 투사체 등록 (누가 쐈는지)
        screwProjectiles.put(screw.getEntityId(), p.getUniqueId());
        registerSummon(p, screw); // cleanup시 제거되도록 등록

        // 15칸(약 15틱 ~ 20틱) 날아가도록 스케줄러 실행
        new BukkitRunnable() {
            int tick = 0;
            final int maxTick = 20; // 대충 15칸 거리 (속도 1.5 * 20 = 30칸? 좀 빠름. 1틱당 1.5칸 -> 10틱=15칸)
            // 15칸 날라가며 -> 속도가 눈덩이(약 1.5) 정도라면 10틱이면 15칸 도달.

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
        // 아머스탠드 헤드포즈는 라디안 단위 (x=pitch, y=yaw, z=roll)
        // 하지만 setHeadPose의 x는 수직 회전, y는 수평 회전
        // 대략적인 방향만 맞춤. 엔드 막대기는 세로로 긴 형태라 회전이 중요.
        // x축 회전(위아래)을 -pitch로 설정.
        return new org.bukkit.util.EulerAngle(-pitch + Math.PI / 2, 0, 0); // +90도 해서 눕힘
    }

    // 북 메이커 효과 적용 (공격 성공 시)
    private void applyBookMakerEffect(Player attacker, LivingEntity target) {
        if (!bookMakerActive.getOrDefault(attacker.getUniqueId(), false))
            return;

        // "북 메이커 대상이 된 사람은 위더 1이 영구 지속됩니다."
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, PotionEffect.INFINITE_DURATION, 0));

        // "북 메이커에 걸린 대상은 또 북 메이커에 걸릴 수 없음."
        // -> 위더 효과로 판별 가능하거나 별도 마킹 필요. 위더가 있으면 이미 걸린 걸로 간주?
        // 기획상 "또 걸릴 수 없음"은 중복 적용 안된다는 뜻으로 해석. 그냥 덮어씌움.

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
            // 소환수 제거 로직
            // "해당 플레이어의 소환된 소환수는 제거됨." -> cleanup에서 처리됨.

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

    // 올 픽션 (사망 방지 및 부활)
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p))
            return;
        if (!hasAbility(p))
            return;

        // 치명적인 데미지인지 확인
        if (p.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true); // 사망 취소

            // "사망 시 올 픽션이 자동 발동되며"
            plugin.getServer().broadcast(Component.text("§b올 픽션. 없던 걸로 했어."));

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
                    if (!bookMakerActive.getOrDefault(p.getUniqueId(), false)) {
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
        // "올 픽션이 사라질 때 아래의 메세지 전체 채팅에 출력."
        // "쿠마가와 미소기: 나는 나쁘지 않아"
        // 5초 뒤 활성화 전에 미리 말하는게 자연스러움 or 활성화 시점에?
        // 문맥상 "5칸이 되면... 사라지며, (즉시) 메시지 출력 -> 5초 뒤 활성화"로 해석됨.

        plugin.getServer().broadcast(Component.text("§c쿠마가와 미소기: 나는 나쁘지 않아"));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline() && hasAbility(p)) {
                    bookMakerActive.put(p.getUniqueId(), true);
                    p.sendMessage("§5[System] 북 메이커가 활성화되었습니다.");
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.0f);
                }
            }
        }.runTaskLater(plugin, 100L); // 5초 = 100틱
    }

    private boolean hasAbility(Player p) {
        return AbilityManager.getInstance().hasAbility(p, getCode());
    }
}
