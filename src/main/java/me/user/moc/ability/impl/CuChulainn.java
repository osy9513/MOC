package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CuChulainn extends Ability {

    // 좌클릭 전용 쿨타임 관리
    private final Map<UUID, Long> leftClickCooldowns = new HashMap<>();

    public CuChulainn(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "009";
    }

    @Override
    public String getName() {
        return "쿠 훌린";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§e전투 ● 쿠 훌린(FATE)",
                "§f[좌클릭] 적을 공격하면 §8위더 저주§f를 겁니다. (쿨 9초)",
                "§f[우클릭] §c게이 볼그§f를 던져 저주 걸린 적을 끝까지 추격합니다.",
                "§f저주 걸린 적이 없으면 §4본인이 공격받습니다. (쿨타임 무시)",
                "§f우클릭 사용 시 좌클릭 쿨타임도 같이 적용됩니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 장비 제거: 철 검
        p.getInventory().remove(Material.IRON_SWORD);
        ItemStack item = new ItemStack(Material.NETHERITE_SPEAR); // 게이 볼그 (네더라이트 창)
        var meta = item.getItemMeta();
        meta.setDisplayName("§c게이 볼그");
        meta.setLore(List.of("§7좌클릭: 저주 부여", "§7우클릭: 유도 창 발사"));
        meta.setCustomModelData(1); // 리소스팩: cuchulainn (netherite_sword cmd 2 used by rooki? Need to check
                                    // registry)
        // Check registry:
        // 1: kimdokja
        // 2: ?
        // Let's check netherite_sword.json registry

        // [추가] 공격력 및 공격 속도 철검과 동일하게 설정
        // 철검: 대미지 6, 공속 1.6
        // 1.21 API 대응: NamespacedKey 및 EquipmentSlotGroup 사용
        NamespacedKey damageKey = new NamespacedKey(plugin, "gae_bolg_damage");
        NamespacedKey speedKey = new NamespacedKey(plugin, "gae_bolg_speed");

        meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_DAMAGE,
                new org.bukkit.attribute.AttributeModifier(damageKey, 6.0,
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                        org.bukkit.inventory.EquipmentSlotGroup.HAND));
        meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_SPEED,
                new org.bukkit.attribute.AttributeModifier(speedKey, -2.4,
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                        org.bukkit.inventory.EquipmentSlotGroup.HAND));
        // 기본 공속이 4.0 기준이므로 -2.4 하면 1.6이 됨. (철검도 1.6)

        item.setItemMeta(meta);

        p.getInventory().addItem(item);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● 쿠 훌린(FATE)");
        p.sendMessage("§f좌클릭으로 적을 타격하면 10초간 '위더 저주'를 부여합니다.");
        p.sendMessage("§f(쿨타임 : 9초, 우클릭 사용 시 20초)");
        p.sendMessage("§f우클릭 시 저주에 걸린 대상을 추격하여 25의 강력한 피해를 주는");
        p.sendMessage("§f마창 '게이 볼그'를 투척합니다. 만약 대상이 없으면 자신이 피해를 입습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 20초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 게이 볼그");
        p.sendMessage("§f장비 제거 : 철 검");
    }

    // [좌클릭] 저주 부여
    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p))
            return;
        if (!hasAbility(p))
            return;

        // [추가] 전투 시작 전 저주 금지
        if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        if (!(e.getEntity() instanceof LivingEntity target))
            return;

        // 아이템 확인 (네더라이트 창)
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != Material.NETHERITE_SPEAR)
            return;

        // 좌클릭 쿨타임 확인
        if (checkLeftCooldown(p)) {
            // 효과 적용
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 200, 0)); // 10초, 레벨 1
            target.setMetadata("GeiBolgCurse", new FixedMetadataValue(plugin, p.getUniqueId().toString()));

            // [추가] 10초 뒤 메타데이터(저주) 자동 해제 (위더랑 시간 맞춤)
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (target.isValid() && target.hasMetadata("GeiBolgCurse")) {
                        target.removeMetadata("GeiBolgCurse", plugin);
                    }
                }
            }.runTaskLater(plugin, 200L);

            p.sendMessage("§c[!] §f대상에게 게이 볼그의 저주를 걸었습니다. (10초)");
            p.playSound(p.getLocation(), Sound.ENTITY_VEX_CHARGE, 1f, 0.5f);

            // 쿨타임 9초 설정
            setLeftCooldown(p, 9);
        }
    }

    // [우클릭] 게이 볼그 발사
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!hasAbility(p))
            return;

        // [추가] 전투 시작 전 발사 금지
        if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack hand = e.getItem();
            if (hand != null && hand.getType() == Material.NETHERITE_SPEAR) {
                e.setCancelled(true); // 기본 동작 방지 (커스텀 로직 사용)

                // 1. 타겟 탐색 (저주 걸린 적)
                LivingEntity target = findCursedTarget(p);

                // 2. 타겟 없음 -> 자폭 (쿨타임 무시하고 작동!)
                if (target == null) {
                    // [수정] 쿨타임이어도 예외 없이 작동해야 하므로 여기선 checkCooldown을 안함.
                    // 대신 메시지를 좀 더 명확하게 수정
                    p.sendMessage("§4[!] §c저주 걸린 적이 없습니다! 게이 볼그가 당신을 꿰뚫습니다!");
                    damageSelf(p);
                    return;
                }

                // 3. 타겟 있음 -> 쿨타임 체크
                if (!checkCooldown(p))
                    return;

                // 4. 발사 로직
                // 구속 페널티
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3)); // 3초, 구속 4

                // 이펙트 및 메시지
                plugin.getServer().broadcastMessage("§c쿠 훌린 : §c꿰어뚫는 죽음의 나는 창 - 게이 볼그");
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1f, 0.5f);

                new BukkitRunnable() {
                    int count = 0;

                    @Override
                    public void run() {
                        if (count < 4) { // 2초 대기
                            // [수정] 파티클 더 크고 두껍게 (count 20 -> 40, offset 0.5 -> 0.8)
                            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 40, 0.8, 1, 0.8,
                                    new Particle.DustOptions(Color.RED, 1.5f));
                            count++;
                        } else {
                            // 2초 후 발사
                            fireGaeBolg(p, target);
                            this.cancel();
                        }
                    }
                }.runTaskTimer(plugin, 0L, 10L);

                // 쿨타임 적용 (20초) -> 좌클릭도 같이 적용
                setCooldown(p, 20);
                setLeftCooldown(p, 20);
            }
        }
    }

    private void fireGaeBolg(Player shooter, LivingEntity target) {
        // 투사체 (아머스탠드)
        ArmorStand projectile = shooter.getWorld().spawn(shooter.getEyeLocation(), ArmorStand.class);
        projectile.setVisible(false);
        projectile.setGravity(false);
        projectile.setSmall(true);
        projectile.setMarker(true);
        // 투사체 관리 등록
        registerSummon(shooter, projectile);

        new BukkitRunnable() {
            final double speed = 1.5; // 속도
            // 충돌한 엔티티 목록 (중복 타격 방지용)
            final HashMap<UUID, Integer> hitCooldowns = new HashMap<>();

            @Override
            public void run() {
                if (projectile.isDead() || target.isDead() || !target.isValid()) {
                    this.cancel();
                    projectile.remove();
                    return;
                }

                Location current = projectile.getLocation();
                Location dest = target.getEyeLocation();

                // 유도 벡터 계산
                Vector dir = dest.toVector().subtract(current.toVector()).normalize().multiply(speed);

                // 이동
                Location nextPos = current.add(dir);
                nextPos.setDirection(dir); // 머리 방향 맞춤
                projectile.teleport(nextPos);

                // 파티클 트레일 (밀도 증가 및 퍼짐: count 7 -> 20, offset 0.1 -> 0.3)
                projectile.getWorld().spawnParticle(Particle.DUST, current, 20, 0.3, 0.3, 0.3,
                        new Particle.DustOptions(Color.RED, 1));

                // [추가] 경로 상의 다른 엔티티 충돌 처리 (관통)
                // 현재 위치 반경 1.0 이내의 적들
                for (Entity e : projectile.getWorld().getNearbyEntities(current, 1.0, 1.0, 1.0)) {
                    if (e instanceof LivingEntity victim && !e.equals(shooter) && !e.equals(target)) {
                        // 중간 장애물(적) 발견
                        if (!hitCooldowns.containsKey(victim.getUniqueId())) {
                            victim.damage(25.0, shooter);
                            // 피격음
                            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1f);
                            // 중복 타격 방지 (잠깐 쿨타임)
                            hitCooldowns.put(victim.getUniqueId(), 20); // 1초간 무적
                        }
                    }
                }

                // 쿨타임 감소 로직
                hitCooldowns.entrySet().removeIf(entry -> {
                    entry.setValue(entry.getValue() - 1);
                    return entry.getValue() <= 0;
                });

                // 원본 타겟 충돌 체크 (거리 1.0 이내)
                if (current.distanceSquared(dest) < 1.0) {
                    // [수정] 명중 시 저주 및 무적 시간 해제 (대미지 씹힘 방지)
                    target.removePotionEffect(PotionEffectType.WITHER);
                    target.removeMetadata("GeiBolgCurse", plugin);
                    target.setNoDamageTicks(0);

                    // 대미지 적용 (체력 20칸 = 40)
                    target.damage(40.0, shooter);

                    // 번개 효과
                    target.getWorld().strikeLightningEffect(target.getLocation());

                    projectile.remove();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void damageSelf(Player p) {
        // 대미지 25
        double dmg = 25.0;

        // 죽을지 미리 확인 (Death Message 커스텀을 위해)
        if (p.getHealth() - dmg <= 0) {
            // 죽음 처리
            p.setHealth(0); // 즉사
            plugin.getServer().broadcastMessage("§c***랜서가 죽었다!***");
        } else {
            p.damage(dmg);
        }
    }

    private LivingEntity findCursedTarget(Player p) {
        // 월드 내 모든 엔티티 중 내가 건 저주가 있는 놈 찾기
        // 범위 제한이 무한(Infinite)이므로 월드 전체 스캔
        // 성능상 비효율적일 수 있으나... 기획이 무한임.
        for (Entity e : p.getWorld().getLivingEntities()) {
            if (e.equals(p))
                continue;
            if (e.hasMetadata("GeiBolgCurse")) {
                List<org.bukkit.metadata.MetadataValue> meta = e.getMetadata("GeiBolgCurse");
                for (var value : meta) {
                    if (value.asString().equals(p.getUniqueId().toString())) {
                        return (LivingEntity) e;
                    }
                }
            }
        }
        return null; // 없음
    }

    // 좌클릭 쿨타임 헬퍼
    private boolean checkLeftCooldown(Player p) {
        // [추가] 크리에이티브 모드면 무시
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE)
            return true;

        if (!leftClickCooldowns.containsKey(p.getUniqueId()))
            return true;
        long end = leftClickCooldowns.get(p.getUniqueId());
        long now = System.currentTimeMillis();

        if (now < end) {
            // 좌클릭은 쿨타임 메시지 굳이 안띄워도 됨 (너무 자주 뜨니까)
            return false;
        }
        return true;
    }

    private void setLeftCooldown(Player p, long seconds) {
        leftClickCooldowns.put(p.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
    }

    @Override
    public void reset() {
        super.reset();
        leftClickCooldowns.clear();
    }

    private boolean hasAbility(Player p) {
        return me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode());
    }
}
