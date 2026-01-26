package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;

import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Meliodas extends Ability {

    private final Set<UUID> preparingPlayers = new HashSet<>();
    private final java.util.Map<UUID, Double> accumulatedDamageMap = new java.util.HashMap<>();

    public Meliodas(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "022";
    }

    @Override
    public String getName() {
        return "멜리오다스";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§e검 우클릭 유지 시(최대 3초) 받은 피해를",
                "§e2배로 증폭하여 되돌려줍니다. (풀 카운터)");
    }

    @Override
    public void giveItem(Player p) {
        // 지급 아이템 없음 (기본 검 사용)
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● 멜리오다스 (일곱 개의 대죄)");
        p.sendMessage("§f검을 우클릭하면 3초간 §d'준비'§f 상태가 됩니다.");
        p.sendMessage("§f준비 상태에서는 받는 피해가 §a50% 감소§f하며, 피해량이 누적됩니다.");
        p.sendMessage("§f3초 후 또는 해제 시 주변 5블록 적에게 §c누적 피해의 200%§f를 입힙니다.");
        p.sendMessage("§f발동 후 3초간 움직일 수 없습니다.");
        p.sendMessage("§f쿨타임 : 20초");
    }

    @Override
    public void cleanup(Player p) {
        preparingPlayers.remove(p.getUniqueId());
        accumulatedDamageMap.remove(p.getUniqueId());
        super.cleanup(p);
    }

    @Override
    public void reset() {
        preparingPlayers.clear();
        accumulatedDamageMap.clear();
        super.reset();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack hand = e.getItem();
        if (hand == null || !hand.getType().name().endsWith("_SWORD"))
            return;

        // 이미 준비 중이면 무시 (또는 조기 발동? 현재 기획은 3초 유지 or 떼기지만, 떼기 감지가 어려워 3초 고정으로 구현)
        if (preparingPlayers.contains(p.getUniqueId()))
            return;

        if (!checkCooldown(p))
            return;

        startFullCounterStance(p);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p))
            return;

        // 멜리오다스 플레이어인지 확인은 preparingPlayers에 있는지만 봐도 됨 (최적화)
        if (!preparingPlayers.contains(p.getUniqueId()))
            return;

        // 대미지 감소 50%
        double originalDamage = e.getDamage();
        e.setDamage(originalDamage * 0.5);

        // 누적 대미지 추가
        // 50% 깎인 후가 아닌 '원래 대미지'를 누적하는 게 보통 반격기의 로직이나,
        // 기획상 "받은 총 피해량"이라 함은 감소된 피해일 수도 있음.
        // 하지만 "증폭하여 돌려준다"는 컨셉상 원본 대미지 누적이 더 '뽕맛'이 좋으므로 원본 기준 누적.
        // 혹은 감소된 대미지를 누적하려면 e.getFinalDamage()를 써야하는데 타이밍상 어려움.
        // 여기서는 originalDamage 사용.
        accumulatedDamageMap.merge(p.getUniqueId(), originalDamage, Double::sum);

        // 시각 효과: 맞았을 때 보라색 히트 이펙트
        p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.1);
    }

    private void startFullCounterStance(Player p) {
        setCooldown(p, 20);
        preparingPlayers.add(p.getUniqueId());
        accumulatedDamageMap.put(p.getUniqueId(), 0.0);

        p.sendMessage("§d풀 카운터 준비!");
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.5f);

        // 3초간 파티클 및 상태 유지 태스크
        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cleanup(p);
                    this.cancel();
                    return;
                }

                // 파티클: 보라색 오라
                // 간단히 원형으로 퍼지거나 몸 주변 감싸기
                Location loc = p.getLocation().add(0, 1, 0);
                p.getWorld().spawnParticle(Particle.DUST, loc, 3, 0.4, 0.8, 0.4,
                        new Particle.DustOptions(Color.PURPLE, 1.0f));

                ticks += 2; // 2틱마다 실행 (runTaskTimer 인자 확인)
                // 3초 = 60틱.
                if (ticks >= 60) {
                    triggerFullCounter(p);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);

        registerTask(p, task);
    }

    private void triggerFullCounter(Player p) {
        if (!preparingPlayers.contains(p.getUniqueId()))
            return;

        double damage = accumulatedDamageMap.getOrDefault(p.getUniqueId(), 0.0);

        // 상태 해제
        preparingPlayers.remove(p.getUniqueId());
        accumulatedDamageMap.remove(p.getUniqueId());

        // 발동 이펙트
        p.sendMessage("§d§l풀 카운터!");
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 2.0f, 0.5f); // 유리 깨지는 소리
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
        p.getWorld().spawnParticle(Particle.EXPLOSION, p.getLocation(), 1);

        // 충격파 파티클 (확산)
        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 20, 2.0, 2.0, 2.0);

        // 반격 대미지: 누적 * 200% (2배)
        double finalDamage = damage * 2.0;

        // 반경 5블록 적 타격
        // 누적 대미지가 0이어도 기본 발동은 함 (대미지만 0)
        if (finalDamage > 0) {
            for (Entity e : p.getNearbyEntities(5, 5, 5)) {
                if (e instanceof LivingEntity target && e != p) {
                    target.damage(finalDamage, p);
                    // 넉백 살짝
                    target.setVelocity(target.getLocation().toVector().subtract(p.getLocation().toVector()).normalize()
                            .multiply(1.5).setY(0.5));
                }
            }
        } else {
            p.sendMessage("§7(받은 피해가 없어 반격하지 않았습니다.)");
        }

        // 패널티: 3초간 움직임 불가 (Self-Stun)
        // 구속(Slowness) 높은 레벨 + 점프 불가
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 255, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 250, false, false)); // 250: 점프 불가
    }
}
