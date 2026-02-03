package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.attribute.Attribute;

import java.util.*;

public class KurosakiIchigo extends Ability {

    private final Set<UUID> isHollow = new HashSet<>();

    public KurosakiIchigo(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "038";
    }

    @Override
    public String getName() {
        return "쿠로사키 이치고";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§e전투 ● §f쿠로사키 이치고 (블리치)",
                "§f호로의 힘을 빌려 잠시 동안 강해집니다.");
    }

    @Override
    public void giveItem(Player p) {
        // detailCheck(p); // AbilityManager에서 자동 호출되므로 제거
        p.getInventory().remove(Material.IRON_SWORD); // 기본 철검 제거

        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b참월");
            meta.setUnbreakable(true);
            sword.setItemMeta(meta);
        }
        p.getInventory().addItem(sword);
    }

    // ... skipping verification of other methods ...

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● 쿠로사키 이치고(블리치)");
        p.sendMessage("§f호로의 힘을 빌려 잠시 동안 강해집니다.");
        p.sendMessage("§f참월 우클릭 시 15초 동안 호로화 상태가 됩니다(신속2, 재생2).");
        p.sendMessage("§f호로화 상태에서 공격 시 대상 근처로 순간이동하며 공격 속도가 대폭 증가합니다.");
        p.sendMessage("§f호로화 종료 시 5초간 구속 3 효과를 받습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 30초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : §b참월");
        p.sendMessage("§f장비 제거 : 철검");
    }

    @EventHandler
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // [추가] 전투 시작 전 사용 금지
        if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        if (e.getAction().name().contains("RIGHT")) {
            ItemStack item = e.getItem();
            if (item != null && item.getType() == Material.IRON_SWORD && item.getItemMeta() != null
            /* && "§b참월".equals(item.getItemMeta().getDisplayName()) */) { // 테스트 시 귀찮움
                onUse(p);
            }
        }
    }

    public void onUse(Player p) {
        if (!checkCooldown(p))
            return;

        // 호로화 발동
        if (!isHollow.contains(p.getUniqueId())) {
            activateHollow(p);
            setCooldown(p, 30);
        } else {
            p.sendMessage("§c이미 호로화 상태입니다.");
        }
    }

    private void activateHollow(Player p) {
        isHollow.add(p.getUniqueId());
        p.getServer().broadcastMessage("§c§l쿠로사키 이치고 : §f내가 지킨다!!");
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f);

        // 지속 효과 (15초)
        // 신속 2, 재생 2
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 300, 1));

        // 공격 속도 대폭 증가 (딜레이 제거)
        // 기본 4.0 -> 100.0 (거의 무한)
        if (p.getAttribute(Attribute.ATTACK_SPEED) != null) {
            p.getAttribute(Attribute.ATTACK_SPEED).setBaseValue(100.0);
        }

        // 파티클 효과
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!p.isOnline() || !isHollow.contains(p.getUniqueId()) || ticks >= 15 * 5) { // 15초 동안
                    cancel();
                    if (isHollow.contains(p.getUniqueId())) {
                        deactivateHollow(p);
                    }
                    return;
                }

                p.getWorld().spawnParticle(Particle.SQUID_INK, p.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0);
                p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3,
                        new Particle.DustOptions(org.bukkit.Color.RED, 1.0f));

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 4L); // 0.2초마다

        // 15초 후 자동 해제
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isHollow.contains(p.getUniqueId())) {
                    deactivateHollow(p);
                }
            }
        }.runTaskLater(plugin, 300L);
    }

    private void deactivateHollow(Player p) {
        isHollow.remove(p.getUniqueId());
        p.sendMessage("§7호로화가 해제되었습니다.");

        // 공격 속도 원복 (기본값)
        if (p.getAttribute(Attribute.ATTACK_SPEED) != null) {
            p.getAttribute(Attribute.ATTACK_SPEED).setBaseValue(4.0);
        }

        // 구속 3 부여 (5초)
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent e) {
        // [추가] 전투 시작 전 공격 효과 방지
        if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        if (e.getDamager() instanceof Player p && isHollow.contains(p.getUniqueId())) {
            // 호로화 상태 공격 시
            if (e.getEntity() instanceof LivingEntity target) {

                // 1. 피격 무적 시간 무시 (강화됨)
                // 만약 데미지가 0이거나 캔슬된 상태라면(무적이라서) -> 강제로 데미지 적용
                if (e.getDamage() == 0 || e.isCancelled()) {
                    e.setCancelled(false); // 캔슬 해제
                    e.setDamage(5.0); // 최소 데미지 보장 5 (사용자 커스텀 값)
                }

                // 2. 무적 시간 0으로 초기화 (연타 가능)
                target.setNoDamageTicks(0);
                target.setMaximumNoDamageTicks(0);

                // 3. 1틱 뒤에 원래대로 돌려놓기
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (target.isValid()) {
                            target.setMaximumNoDamageTicks(20);
                        }
                    }
                }.runTaskLater(plugin, 1L);

                // [추가] 텔레포트 전 이치고 자리에 보라색 이펙트 (잔상)
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.5);

                // [수정] 파티클 오류 수정: DRAGON_BREATH는 Float 데이터가 필요합니다.
                // data 0.5f 추가 (브레스 크기/강도)
                p.getWorld().spawnParticle(Particle.DRAGON_BREATH, p.getLocation().add(0, 0.5, 0), 10, 0.3, 0.3, 0.3,
                        0.1, 0.5f);

                // [수정] 순간이동 위치 다채롭게 (Target 주변 랜덤)
                double distance = 1.6; // 거리 1.6칸
                double angle = Math.random() * 2 * Math.PI; // 360도 랜덤

                double dx = distance * Math.cos(angle);
                double dz = distance * Math.sin(angle);

                Location targetLoc = target.getLocation();
                // Y축은 타겟과 동일하게 유지하거나, 살짝 위? (사용자 요청: Y축 이동 제외 -> 타겟 Y축 따라감)
                Location teleLoc = targetLoc.clone().add(dx, 0, dz);

                // 블럭 끼임 방지 (도착 지점이 고체면 대상의 위치로)
                if (teleLoc.getBlock().getType().isSolid()
                        || teleLoc.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                    teleLoc = targetLoc.clone();
                }

                // [수정] 순간이동 후 타겟을 바라보도록 시점 변경
                // teleportPos에서 targetPos를 바라보는 벡터 계산
                Vector lookDir = targetLoc.toVector().subtract(teleLoc.toVector());
                if (lookDir.lengthSquared() > 0) { // 0 벡터 방지
                    teleLoc.setDirection(lookDir);
                }

                p.teleport(teleLoc);

                p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f);
            }
        }
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        isHollow.remove(p.getUniqueId());
        // 공속 초기화
        if (p.getAttribute(Attribute.ATTACK_SPEED) != null) {
            p.getAttribute(Attribute.ATTACK_SPEED).setBaseValue(4.0);
        }
    }

}
