package me.user.moc.ability.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import net.kyori.adventure.text.Component;

public class Saitama extends Ability {

    // private static final int GOAL_SNEAK = 500;
    // private static final int GOAL_JUMP = 500;
    // private static final int GOAL_DIST_CM = 50000; // 500 blocks = 50000 cm
    private static final int GOAL_JUMP = 100;
    private static final int GOAL_SNEAK = 200;
    private static final int GOAL_DIST_CM = 300; // 300 blocks
    private final Map<UUID, SaitamaProgress> progressMap = new HashMap<>();

    @Override
    public void reset() {
        super.reset();
        progressMap.clear();
    }

    public Saitama(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "005";
    }

    @Override
    public String getName() {
        return "사이타마";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§c유틸 ● 사이타마(원펀맨)",
                "§f사이타마 운동법을 완료하면 매우 강력해집니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c유틸 ● 사이타마(원펀맨)");
        p.sendMessage("§f사이타마 운동법(점프 100회, 웅크리기 200회, 이동 300블록)을 완료하세요.");
        p.sendMessage("§f운동을 모두 완료하면 라운드 종료까지 강력한 버프를 얻습니다.");
        p.sendMessage("§f버프 : 힘V, 이속V, 저항IV, 성급함V");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 철 갑옷, 철 흉갑");
    }

    @Override
    public void giveItem(Player p) {
        // 기존 철 흉갑, 철 칼 제거 (GameManager에서 지급된 것)
        p.getInventory().remove(Material.IRON_SWORD);
        p.getInventory().remove(Material.IRON_CHESTPLATE);
        p.getInventory().setChestplate(null); // 혹시 입고 있을 수 있으므로

        // 데이터 초기화
        progressMap.put(p.getUniqueId(), new SaitamaProgress());

        // 진행 상황 액션바로 보여주기 (팁)
        p.sendMessage("§e[MOC] §f사이타마 운동법을 시작합니다!");
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        // [게임 상태 확인] 전투 시작 전에는 카운트 안 함
        if (!MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        if (!e.isSneaking())
            return; // 웅크릴 때만 카운트
        Player p = e.getPlayer();
        updateProgress(p, 1, 0, 0);
    }

    @EventHandler
    public void onStat(PlayerStatisticIncrementEvent e) {
        // [게임 상태 확인]
        if (!MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        if (e.getStatistic() == Statistic.JUMP) {
            updateProgress(e.getPlayer(), 0, 1, 0);
        }
    }

    @EventHandler
    public void onMove(org.bukkit.event.player.PlayerMoveEvent e) {
        // [게임 상태 확인]
        if (!MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        Player p = e.getPlayer();

        // 최적화: 블록이 변했을 때만 계산 (혹은 일정 거리 이상일 때만)
        // 여기서는 부드러운 갱신을 위해 매 이동마다 계산하되, 연산량을 최소화합니다.
        // from/to가 같거나 회전만 한 경우 무시 (이동 거리 0)
        if (e.getFrom().getX() == e.getTo().getX() &&
                e.getFrom().getZ() == e.getTo().getZ() &&
                e.getFrom().getY() == e.getTo().getY())
            return;

        double dist = e.getFrom().distance(e.getTo()); // 이동 거리 (블록 단위)
        if (dist <= 0)
            return;

        // cm 단위로 변환 (1 block = 100 cm)
        int distCm = (int) (dist * 100);
        if (distCm > 0) {
            updateProgress(p, 0, 0, distCm);
        }
    }

    private void updateProgress(Player p, int sneak, int jump, int distCm) {
        SaitamaProgress data = progressMap.get(p.getUniqueId());

        // 데이터가 없으면 본인이 사이타마 능력자인지 확인하고 초기화 (Lazy Init)
        if (data == null) {
            if (me.user.moc.MocPlugin.getInstance().getAbilityManager().hasAbility(p, getCode())) {
                data = new SaitamaProgress();
                progressMap.put(p.getUniqueId(), data);
            } else {
                return; // 능력자가 아님
            }
        }

        if (data.completed)
            return;

        data.sneaks += sneak;
        data.jumps += jump;
        data.distCm += distCm;

        // 진행 상황 표시 (액션바)
        p.sendActionBar(Component.text(String.format("§e[사이타마] 점프 %d/%d | 웅크리기 %d/%d | 이동 %d/%d",
                Math.min(data.jumps, GOAL_JUMP), GOAL_JUMP,
                Math.min(data.sneaks, GOAL_SNEAK), GOAL_SNEAK,
                Math.min(data.distCm / 100, GOAL_DIST_CM), GOAL_DIST_CM // cm -> block
        )));

        checkCompletion(p, data);
    }

    private void checkCompletion(Player p, SaitamaProgress data) {
        // 이동 거리는 cm 단위이므로 100으로 나누어 블록 단위로 비교
        if (data.sneaks >= GOAL_SNEAK &&
                data.jumps >= GOAL_JUMP &&
                (data.distCm / 100) >= GOAL_DIST_CM) {

            data.completed = true;
            awaken(p);
        }
    }

    private void awaken(Player p) {
        // 1. 메시지 & 사운드
        Bukkit.broadcast(Component.text("§f사이타마 : 취미로 히어로를 하는 사람이다."));
        // 웅장한 소리
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
        p.getWorld().playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

        // 2. 버프 부여
        // 힘V (Strength)
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 4));
        // 이속V (Speed)
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 4));
        // 저항IV (Resistance)
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 3));
        // 성급함V (Haste)
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, PotionEffect.INFINITE_DURATION, 4));
    }

    private static class SaitamaProgress {
        int sneaks = 0;
        int jumps = 0;
        int distCm = 0;
        boolean completed = false;
    }
}
