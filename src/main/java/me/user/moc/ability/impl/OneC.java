package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class OneC extends Ability {

    public OneC(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "H08";
    }

    @Override
    public String getName() {
        return "원크";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c유틸 ● 원크(바집소)",
                "§f랜덤 능력 펀치~");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c유틸 ● 원크(바집소)");
        p.sendMessage("§f맨손 쉬프트 좌클릭으로 플레이어를 공격할 경우, 해당 플레이어의 체력을 3칸 깎고");
        p.sendMessage("§f능력을 현재 라운드의 플레이어 능력들과 겹치지 않는 랜덤한 능력으로 변경 시킵니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 3초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void giveItem(Player p) {
        detailCheck(p);
    }

    /**
     * 해당 플레이어가 이 능력을 소유하고 있는지 확인하는 메서드
     */
    private boolean hasAbility(Player p) {
        return AbilityManager.getInstance().hasAbility(p, getCode());
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p))
            return;
        if (!(e.getEntity() instanceof Player target))
            return;

        // 침묵 상태, 능력 소유 여부 검사
        if (isSilenced(p))
            return;
        if (!hasAbility(p))
            return;

        // 관전자 타겟 방지 (안전 장치 추가)
        if (target.getGameMode() == org.bukkit.GameMode.SPECTATOR)
            return;

        // 웅크림(Sneaking) 및 맨손 검사
        if (!p.isSneaking())
            return;
        if (p.getInventory().getItemInMainHand().getType() != Material.AIR)
            return;

        // 쿨타임 검사 및 부여 (3초)
        if (!checkCooldown(p))
            return;
        setCooldown(p, 3);

        // 연출 메세지
        plugin.getServer().broadcastMessage("§f원크: 랜덤 능력 펀치~");

        // 랜덤 능력 뽑기 로직
        AbilityManager am = AbilityManager.getInstance();
        List<String> pool = new ArrayList<>(am.getPlayableAbilityCodes());

        // 현재 게임 내 모든 플레이어의 능력(배정받은 능력 모음) 지우기
        pool.removeAll(am.getPlayerAbilities().values());

        // 뽑을 수 있는 능력이 남아있을 경우에만 능력 변경
        if (!pool.isEmpty()) {
            Collections.shuffle(pool);
            String newAbilityCode = pool.get(0);

            // 기존 능력 정리(Cleanup) 및 새 능력 번호 부여 후 지급
            // changeAbilityTemporary 호출 등으로 깔끔하게 처리
            am.changeAbilityTemporary(target, newAbilityCode);
            am.giveAbilityItems(target);

            // 능력 이름 가져와서 전체 메세지 출력
            Ability newAbility = am.getAbility(newAbilityCode);
            String abilityName = (newAbility != null) ? newAbility.getName() : "알 수 없음";
            plugin.getServer()
                    .broadcastMessage("§e[" + target.getName() + "] 의 능력이 [" + abilityName + "] (으)로 변경되었습니다.");

            target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
        }

        // 이벤트 취소는 하지 않고, 직접적으로 데미지를 주고 이펙트 등을 가함.
        // 추가 피해 3칸 (기존 맨손 데미지 + 6.0 = 체력 3칸 분량)
        // 능력 변경 후에 데미지를 주어야 새로 변경된 능력의 체력에 데미지가 온전히 적용되며,
        // 이를 통해 대상이 사망했을 때 정상적인 죽음 이벤트(관전자 전환 등)가 발동됩니다.
        target.setMetadata("MOC_LastKiller",
                new org.bukkit.metadata.FixedMetadataValue(plugin, p.getUniqueId().toString()));
        target.damage(6.0, p);

        // 파티클 스케줄러 (피격된 플레이어 주위로 임의의 색상 파티클들이 위아래로 3번 스캔하듯 오르내림)
        Location loc = target.getLocation();
        Random rnd = new Random();

        // 파티클 색상 후보
        Color[] colors = { Color.RED, Color.GREEN, Color.AQUA, Color.FUCHSIA, Color.YELLOW };

        BukkitRunnable particleTask = new BukkitRunnable() {
            int ticks = 0;
            // 총 3번 왕복(위로 올랐다 내려왔다) -> 1번 왕복=약 10틱으로 할당 (총 30틱 수행)

            @Override
            public void run() {
                if (ticks >= 30 || !target.isOnline() || target.isDead()) {
                    this.cancel();
                    return;
                }

                // 0 ~ 1.0 사이클을 계속 반복하여 위아래로 움직임 (sin 함수 활용)
                double yOffset = Math.abs(Math.sin((double) ticks / 5.0)) * 2.0;

                // 플레이어 주변(반경 1.0 이내)에 여러 줄기의 파티클 레이저 형성
                for (int i = 0; i < 5; i++) {
                    double angle = (2 * Math.PI / 5) * i;
                    double x = Math.cos(angle) * 1.0;
                    double z = Math.sin(angle) * 1.0;

                    Location particleLoc = target.getLocation().clone().add(x, yOffset, z);

                    // 각 줄기마다 램덤한 색상의 레드스톤(Dust) 파티클 표시
                    Color c = colors[i % colors.length];
                    Particle.DustOptions dustOpt = new Particle.DustOptions(c, 1.5f);
                    particleLoc.getWorld().spawnParticle(Particle.DUST, particleLoc, 3, 0.1, 0.1, 0.1, 0, dustOpt);
                }

                ticks++;
            }
        };
        activeTasks.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>())
                .add(particleTask.runTaskTimer(plugin, 0L, 1L));
    }
}
