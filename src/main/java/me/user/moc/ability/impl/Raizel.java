package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.entity.Pose;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [능력 코드: 086]
 * 이름: Raizel (카디스 에트라마 디 라이제르)
 * 설명: 꿇어라. 이것이 너와 나의 눈높이다. 15x15 범위 내 적들을 강제로 엎드리게 함.
 */
public class Raizel extends Ability {

    private final Map<UUID, Long> lastSneakTime = new HashMap<>();
    private final Map<UUID, Long> activeUntil = new ConcurrentHashMap<>();
    private final Set<UUID> forcedPronePlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public Raizel(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "086";
    }

    @Override
    public String getName() {
        return "카디스 에트라마 디 라이제르";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c유틸 ● 라이제르(노블레스)",
                "§f꿇어라. 이것이 너와 나의 눈높이다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c유틸 ● 카디스 에트라마 디 라이제르(노블레스)");
        p.sendMessage("§f쉬프트 연속 2번 입력 시 능력이 발동됩니다.");
        p.sendMessage("§f발동 시 8초간 주변 15*15 범위의 적들은 점프가 불가능해지고 강제로 엎드린 상태가 됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 15초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void giveItem(Player p) {
        // 아이템 없음
    }

    @Override
    public void reset() {
        super.reset();
        lastSneakTime.clear();
        activeUntil.clear();
        forcedPronePlayers.clear();
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!e.isSneaking())
            return;
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;
        if (isSilenced(p))
            return; // 침묵 상태 검사
        
        // 이미 능력이 발동 중인지 체크 (중복 발동 방지 보호 로직)
        if (activeUntil.containsKey(p.getUniqueId()))
            return;

        if (!checkCooldown(p))
            return;

        long now = System.currentTimeMillis();
        long lastTime = lastSneakTime.getOrDefault(p.getUniqueId(), 0L);

        if (now - lastTime < 400) { // 0.4초 내 재입력 시 발동
            activateAbility(p);
            lastSneakTime.remove(p.getUniqueId());
        } else {
            lastSneakTime.put(p.getUniqueId(), now);
        }
    }

    private void activateAbility(Player p) {
        // 전체 메시지
        Bukkit.broadcastMessage("§c카디스 에트라마 디 라이제르 : §f꿇어라. 이것이 너와 나의 눈높이다.");

        // 효과음 및 파티클
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.5f);

        activeUntil.put(p.getUniqueId(), System.currentTimeMillis() + 8000);

        // 8초간 지속 프로세스
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 160 || !p.isOnline()
                        || !AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
                    this.cancel();
                    activeUntil.remove(p.getUniqueId());
                    setCooldown(p, 15); // 지속시간 종료 후 15초 쿨타임
                    return;
                }

                // 라이제르 본인 파티클 (영압 방출 느낌)
                p.getWorld().spawnParticle(Particle.DRAGON_BREATH, p.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3,
                        0.05);
                p.getWorld().spawnParticle(Particle.SQUID_INK, p.getLocation().add(0, 1, 0), 10, 0.5, 1.0, 0.5, 0.02);

                // 범위 내 적 탐색 및 제압
                double range = 15.0;
                for (Entity entity : p.getNearbyEntities(range, range, range)) {
                    if (entity instanceof Player victim && !victim.equals(p)) {
                        // 관전자 및 크리에이티브 모드 제외 (시스템 프롬프트 준수)
                        if (victim.getGameMode() == GameMode.SPECTATOR || victim.getGameMode() == GameMode.CREATIVE)
                            continue;

                        applyDomination(victim);
                    }
                }
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void applyDomination(Player victim) {
        UUID uuid = victim.getUniqueId();

        // 이미 제압 중이라면 시간만 갱신 (이미 굴러가고 있는 태스크가 있을 것이므로 생략 가능하나 안전을 위해)
        if (forcedPronePlayers.contains(uuid)) {
            applyJumpSilence(victim, 10);
            return;
        }

        forcedPronePlayers.add(uuid);
        applyJumpSilence(victim, 10); // 10틱씩 갱신

        // 강제 제압 태스크 (10틱 = 0.5초간 매 틱 실행)
        new BukkitRunnable() {
            int subTicks = 0;

            @Override
            public void run() {
                if (subTicks >= 10 || !victim.isOnline() || victim.getGameMode() == GameMode.SPECTATOR) {
                    forcedPronePlayers.remove(uuid);
                    this.cancel();
                    return;
                }

                // 매 틱마다 강제로 수영(엎드리기) 포즈 주입
                victim.setPose(Pose.SWIMMING, true);
                victim.setSneaking(true);

                // 영압에 짓눌리는 효과
                victim.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS,
                        5, 4, false, false, false));

                // 아래로 누르는 힘
                victim.setVelocity(victim.getVelocity().add(new Vector(0, -0.5, 0)));

                // 짓눌리는 비주얼
                if (subTicks % 2 == 0) {
                    victim.getWorld().spawnParticle(Particle.LARGE_SMOKE, victim.getLocation().add(0, 1, 0), 2, 0.2,
                            0.1, 0.2, 0.01);
                }

                subTicks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
