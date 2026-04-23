package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [능력 코드: 086]
 * 이름: Raizel (카디스 에트라마 디 라이제르)
 * 설명: 꿇어라. 이것이 너와 나의 눈높이다.
 * 8초간 15x15 범위 내 적들에게 [점프 불가 + 강제 쉬프트 + 이속 저하] 적용.
 */
public class Raizel extends Ability {

    // 쉬프트 더블클릭 감지용 마지막 쉬프트 시간 기록
    private final Map<UUID, Long> lastSneakTime = new HashMap<>();
    // 능력 활성 상태 추적용 (중복 발동 방지)
    private final Map<UUID, Long> activeUntil = new ConcurrentHashMap<>();
    // 현재 영압에 의해 제압당하고 있는 플레이어 목록 (쉬프트 해제 차단에 사용)
    private final Set<UUID> dominatedPlayers = ConcurrentHashMap.newKeySet();

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
        p.sendMessage("§f발동 시 8초간 주변 15*15 범위의 적들은 점프가 불가능해지고,");
        p.sendMessage("§f강제로 쉬프트(웅크리기) 상태가 되며 이동 속도가 크게 저하됩니다.");
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
        // 제압 중이던 플레이어들의 쉬프트 상태 해제
        for (UUID uuid : dominatedPlayers) {
            Player victim = Bukkit.getPlayer(uuid);
            if (victim != null && victim.isOnline()) {
                victim.setSneaking(false);
            }
        }
        dominatedPlayers.clear();
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();

        // [영압 제압] 제압 중인 플레이어가 쉬프트를 풀려고 하면 차단
        // e.isSneaking() == false → 플레이어가 쉬프트를 해제하려는 상태
        if (!e.isSneaking() && dominatedPlayers.contains(p.getUniqueId())) {
            e.setCancelled(true); // 쉬프트 해제 취소 → 계속 웅크린 상태 유지
            return;
        }

        // [능력 발동] 쉬프트를 누르는 경우에만 능력 발동 체크 진행
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

        // 8초간 지속 프로세스 (5틱 = 0.25초 간격으로 32번 반복 = 160틱 = 8초)
        // 쉬프트 강제 적용을 자주 갱신해야 빈틈이 없으므로 0.25초 간격 사용
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                // 32번 반복 × 5틱 간격 = 160틱 = 8초 경과 시 종료
                if (count >= 32 || !p.isOnline()
                        || !AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
                    this.cancel();
                    activeUntil.remove(p.getUniqueId());
                    // 능력 종료 시 제압 중이던 플레이어들의 쉬프트 상태 해제
                    releaseDominatedPlayers();
                    setCooldown(p, 15); // 지속시간 종료 후 15초 쿨타임
                    return;
                }

                // 라이제르 본인 파티클 (영압 방출 느낌)
                // [1.21.11] DRAGON_BREATH 파티클은 Float 크기 데이터가 필수 → data 파라미터 포함 오버로드 사용
                p.getWorld().spawnParticle(Particle.DRAGON_BREATH, p.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3,
                        0.05, 1.0f);
                // 데이터 불요 파티클인 SOUL_FIRE_FLAME으로 어둡고 무거운 영압 분위기 연출
                p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(0, 1, 0), 10, 0.5, 1.0, 0.5, 0.02);

                // 범위 내 적 탐색 및 제압 (0.25초마다 강제 쉬프트 + 점프 봉쇄 + 이속 저하)
                double range = 15.0;
                for (Entity entity : p.getNearbyEntities(range, range, range)) {
                    if (entity instanceof Player victim && !victim.equals(p)) {
                        // 관전자 및 크리에이티브 모드 제외 (시스템 프롬프트 준수)
                        if (victim.getGameMode() == GameMode.SPECTATOR || victim.getGameMode() == GameMode.CREATIVE)
                            continue;

                        applyDomination(victim);
                    }
                }

                // 2초(8회)마다 효과음 재생 (너무 자주 울리지 않도록 제한)
                if (count % 8 == 0) {
                    p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.15f, 0.3f);
                }

                count++;
            }
        }.runTaskTimer(plugin, 0L, 5L); // 5틱(0.25초) 간격으로 실행
    }

    /**
     * 영압 제압 효과 적용 (0.25초마다 호출됨)
     * - [점프 불가] applyJumpSilence로 점프 원천 차단
     * - [강제 쉬프트] setSneaking(true)로 웅크리기 강제 적용 + 이벤트에서 해제 차단
     * - [이속 저하] 슬로우 3단계로 이동 속도 대폭 감소
     * - setPose(SWIMMING) 방식은 서버-클라이언트 시점 디싱크 발생으로 사용하지 않음
     */
    private void applyDomination(Player victim) {
        UUID uuid = victim.getUniqueId();

        // 제압 대상 목록에 등록 (onSneak 이벤트에서 쉬프트 해제를 차단하는 데 사용)
        dominatedPlayers.add(uuid);

        // 강제 쉬프트(웅크리기) 적용 - 모든 클라이언트에서 동일하게 보임
        victim.setSneaking(true);

        // 점프 불가 적용 (다음 발동 주기인 5틱 + 여유 3틱 = 8틱 동안 유지)
        applyJumpSilence(victim, 8);

        // 이동 속도 저하 (슬로우 3단계, 8틱 지속 → 다음 발동까지 끊김 없이 유지)
        victim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOWNESS, 8, 3, false, false, false));

        // 짓눌리는 비주얼 파티클 (연기가 피어오르는 듯한 연출)
        victim.getWorld().spawnParticle(Particle.LARGE_SMOKE, victim.getLocation().add(0, 0.5, 0),
                3, 0.2, 0.1, 0.2, 0.01);
    }

    /**
     * 능력 종료 시 제압 중이던 모든 플레이어의 쉬프트 상태를 해제하는 메서드
     * 능력 지속시간이 끝나거나 라운드가 종료될 때 호출됨
     */
    private void releaseDominatedPlayers() {
        for (UUID uuid : dominatedPlayers) {
            Player victim = Bukkit.getPlayer(uuid);
            if (victim != null && victim.isOnline()) {
                // 쉬프트 상태 해제 → 플레이어가 다시 자유롭게 움직일 수 있음
                victim.setSneaking(false);
            }
        }
        dominatedPlayers.clear();
    }
}
