package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class Alex extends Ability {

    private final Map<UUID, Long> hackingStartTime = new HashMap<>();
    private final Map<UUID, Long> lastInteractTime = new HashMap<>();

    public Alex(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "020";
    }

    @Override
    public String getName() {
        return "알렉스";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§e유틸 ● 알렉스(이터널 리턴)",
                "§fJP에게 전달받은 해킹툴을 이용하여 우클릭으로 해킹을 진행할 수 있습니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 추가 장비 없음
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e유틸 ● 알렉스(이터널 리턴)");
        p.sendMessage("§f맨손으로 에메랄드 블록을 20초간 우클릭하여");
        p.sendMessage("§f해킹에 성공하면 에메랄드를 제외한 전장 내 모든 블럭을 파괴합니다.");
        p.sendMessage("§f해킹 성공 시 자신은 20초간 느린 낙하 효과를 얻습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void cleanup(Player p) {
        hackingStartTime.remove(p.getUniqueId());
        lastInteractTime.remove(p.getUniqueId());
        super.cleanup(p); // 부모 클래스의 map 정리 호출 (activeTasks 등)
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        // [게임 상태 확인] 전투 시작 전에는 해킹 불가
        if (!MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        Player p = e.getPlayer();

        // [추가] 능력이 봉인된 상태 (침묵)인지 체크
        if (isSilenced(p))
            return;
        // 1. 내 능력인지 확인
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 2. 우클릭 & 에메랄드 블록 & 맨손 확인
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.EMERALD_BLOCK)
            return;
        if (p.getInventory().getItemInMainHand().getType() != Material.AIR)
            return;

        // 3. 해킹 시작 또는 유지
        long now = System.currentTimeMillis();

        // 이미 진행 중이라면 시간 갱신만
        if (activeTasks.containsKey(p.getUniqueId())) {
            boolean isRunning = false;
            java.util.List<BukkitTask> tasks = activeTasks.get(p.getUniqueId());
            // [Fix] 실제 실행 중인 태스크가 있는지 검증 (찌꺼기 데이터 방지)
            for (BukkitTask t : tasks) {
                if (plugin.getServer().getScheduler().isQueued(t.getTaskId())
                        || plugin.getServer().getScheduler().isCurrentlyRunning(t.getTaskId())) {
                    isRunning = true;
                    break;
                }
            }

            if (isRunning) {
                lastInteractTime.put(p.getUniqueId(), now);
                return;
            } else {
                // 실행 중인 태스크가 없는데 맵에 남아있다면 제거 (버그 수정)
                activeTasks.remove(p.getUniqueId());
            }
        }

        // 4. 해킹 시작
        p.sendMessage("§a임무 시작합니다.");
        hackingStartTime.put(p.getUniqueId(), now);
        lastInteractTime.put(p.getUniqueId(), now);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // 1. 플레이어 유효성 체크
                    if (!p.isOnline() || p.isDead()) {
                        this.cancel();
                        cleanupData(p.getUniqueId());
                        return;
                    }

                    // 2. 지속성 체크 (0.5초 이상 입력 없으면 중단)
                    long last = lastInteractTime.getOrDefault(p.getUniqueId(), 0L);
                    if (System.currentTimeMillis() - last > 500) {
                        p.sendMessage("§c해킹이 중단되었습니다.");
                        this.cancel();
                        cleanupData(p.getUniqueId());
                        return;
                    }

                    // 3. 진행도 알림 (Actionbar)
                    long start = hackingStartTime.getOrDefault(p.getUniqueId(), 0L);
                    long elapsed = System.currentTimeMillis() - start;
                    double secondsLeft = (20000 - elapsed) / 1000.0;

                    if (secondsLeft <= 0) {
                        // [성공]
                        successHacking(p);
                        this.cancel();
                        cleanupData(p.getUniqueId());
                    } else {
                        p.sendActionBar(net.kyori.adventure.text.Component
                                .text("§a해킹 진행 중... " + String.format("%.1f", secondsLeft) + "초"));
                        // 효과음 (띠띠띠...)
                        if (elapsed % 1000 < 100) { // 대략 1초마다 소리
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 2f);
                        }
                    }
                } catch (Exception e) {
                    // [예외 처리] 실행 중 에러 발생 시 안전하게 정리
                    e.printStackTrace();
                    this.cancel();
                    cleanupData(p.getUniqueId());
                }
            }
        }.runTaskTimer(plugin, 0L, 5L); // 0.25초마다 검사

        registerTask(p, task);
    }

    private void successHacking(Player p) {
        // 메시지 출력
        Bukkit.broadcastMessage("§f ");
        Bukkit.broadcastMessage("§a알렉스 : 이곳의 임무는 종료됐습니다.");
        Bukkit.broadcastMessage("§f ");

        // 효과음
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

        // 기반암 제거 요청
        // ArenaManager 인스턴스를 가져와서 호출
        MocPlugin.getInstance().getArenaManager().removeSquareFloor();

        // 느린 낙하 버프 (20초 = 400틱, 레벨3 = amplifier 2)
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 20, 2, true, true, true));
    }

    private void cleanupData(UUID uuid) {
        hackingStartTime.remove(uuid);
        lastInteractTime.remove(uuid);

        // activeTasks는 부모 클래스의 reset()이나 cleanup 시 정리되지만,
        // 여기서 개별 Task가 끝났을 때 리스트에서 제거해주는 것이 깔끔함 (Optional)
        // 하지만 여기선 복잡하니 놔두고, 다음 시작 시 덮어쓰거나 무시됨.
        // 다만 activeTasks map에 남아있으면 메모리 누수는 아니지만 찌꺼기가 됨.
        activeTasks.remove(uuid);
    }
}
