package me.user.moc.ability;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import me.user.moc.MocPlugin;
import me.user.moc.game.GameManager;

import java.util.List;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public abstract class Ability implements Listener {
    protected final JavaPlugin plugin;

    public Ability(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // [수정 1] 여기에 '능력 코드'를 달라고 하는 규칙을 추가합니다.
    // 기존 getName()은 '채팅창에 보여줄 이름' 용도로 남겨두고,
    // getCode()는 '시스템이 알아먹을 번호' 용도로 씁니다.
    public abstract String getCode();

    public abstract String getName();

    public abstract List<String> getDescription();

    // 아이템 지급 로직
    public abstract void giveItem(Player p);

    // /moc check 시 출력될 상세 설명입니다.
    public abstract void detailCheck(Player p);

    /**
     * 능력이 해제되거나 게임이 끝날 때 호출되는 정리 메서드입니다.
     * 소환수 제거, 버프 해제 등의 로직을 구현합니다.
     */
    public void cleanup(Player p) {
    }

    // [중앙 집권형 상태 관리] 자식들이 개별 Map을 쓰지 않도록 부모가 통합 관리합니다.
    protected final java.util.Map<java.util.UUID, Long> cooldowns = new java.util.HashMap<>();
    protected final java.util.Map<java.util.UUID, java.util.List<org.bukkit.entity.Entity>> activeEntities = new java.util.HashMap<>();
    protected final java.util.Map<java.util.UUID, java.util.List<org.bukkit.scheduler.BukkitTask>> activeTasks = new java.util.HashMap<>();
    // [추가] 쿨타임 알림용 태스크 관리
    protected final java.util.Map<java.util.UUID, BukkitTask> cooldownNotifyTasks = new java.util.HashMap<>();

    /**
     * [추가] 라운드가 시작되거나 게임이 리셋될 때 호출됩니다.
     * 모든 쿨타임, 파티클, 소환수 등 '능력 자체'에 저장된 상태를 초기화해야 합니다.
     * 자식 클래스에서 특별히 더 비워야 할 게 있다면 override하되, super.reset()을 호출해야 합니다.
     */
    public void reset() {
        // 1. 모든 쿨타임 초기화
        cooldowns.clear();

        // 2. 관리 중인 모든 엔티티 제거 (소환수 등)
        for (java.util.List<org.bukkit.entity.Entity> list : activeEntities.values()) {
            if (list == null)
                continue;
            for (org.bukkit.entity.Entity e : list) {
                if (e != null && !e.isDead())
                    e.remove();
            }
        }
        activeEntities.clear();

        // 3. 관리 중인 모든 태스크 취소 (파티클 등)
        for (java.util.List<org.bukkit.scheduler.BukkitTask> list : activeTasks.values()) {
            if (list == null)
                continue;
            for (org.bukkit.scheduler.BukkitTask t : list) {
                if (t != null && !t.isCancelled())
                    t.cancel();
            }
        }
        activeTasks.clear();

        // 4. 예약된 쿨타임 알림 취소
        for (BukkitTask t : cooldownNotifyTasks.values()) {
            if (t != null && !t.isCancelled()) {
                t.cancel();
            }
        }
        cooldownNotifyTasks.clear();
    }

    // === [쿨타임 관련 헬퍼 메서드] ===
    protected void setCooldown(Player p, long seconds) {
        cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));

        // [추가] 기존 알림 취소
        if (cooldownNotifyTasks.containsKey(p.getUniqueId())) {
            cooldownNotifyTasks.get(p.getUniqueId()).cancel();
        }

        // [추가] 알림 스케줄링
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    p.sendActionBar(net.kyori.adventure.text.Component.text("§a기술이 준비 되었습니다."));
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                }
                cooldownNotifyTasks.remove(p.getUniqueId());
            }
        }.runTaskLater(plugin, seconds * 20L);

        cooldownNotifyTasks.put(p.getUniqueId(), task);
    }

    protected boolean checkCooldown(Player p) {
        // [게임 상태 확인] 전투 전에는 능력 사용 불가
        MocPlugin moc = (MocPlugin) plugin;
        GameManager gm = moc.getGameManager();

        // 게임이 실행 중이 아니거나, 아직 무적(카운트다운) 상태라면
        if (gm == null || !gm.isBattleStarted()) {
            p.sendActionBar(net.kyori.adventure.text.Component.text("§c전투 시작 후에 사용할 수 있습니다."));
            return false;
        }

        if (!cooldowns.containsKey(p.getUniqueId()))
            return true; // 쿨타임 없음 (사용 가능)

        long now = System.currentTimeMillis();
        long endTime = cooldowns.get(p.getUniqueId());
        if (now < endTime) {
            double left = (endTime - now) / 1000.0;
            // 쿨타임 중이면 메시지 보내거나 false 리턴
            p.sendActionBar(
                    net.kyori.adventure.text.Component.text("§c쿨타임이 " + String.format("%.1f", left) + "초 남았습니다."));
            return false;
        }
        // [수정] 자동 알림으로 변경되어 여기선 메시지 출력 제거 (true만 리턴)
        return true;
    }

    // === [소환수/태스크 등록 헬퍼 메서드] ===
    protected void registerSummon(Player p, org.bukkit.entity.Entity e) {
        activeEntities.computeIfAbsent(p.getUniqueId(), k -> new java.util.ArrayList<>()).add(e);
    }

    protected void registerTask(Player p, org.bukkit.scheduler.BukkitTask t) {
        activeTasks.computeIfAbsent(p.getUniqueId(), k -> new java.util.ArrayList<>()).add(t);
    }
}
