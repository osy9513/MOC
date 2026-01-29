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
                "유틸 ● 알렉스(이터널 리턴)",
                "§f에메랄드 블럭을 20초간 해킹하면",
                "§f전장의 기반암이 전부 사라집니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 추가 장비 없음
    }

    @Override
    public void detailCheck(Player p) {
        // [디테일 정보 출력] 사용자 요청 포맷에 맞게 수정됨
        p.sendMessage("§a유틸 ㆍ 알렉스(이터널 리턴)");
        p.sendMessage("맨손으로 에메랄드 블록을 20초간 우클릭하여 해킹하면 전장의 기반암을 파괴합니다.");
        p.sendMessage("해킹 성공 시 자신은 20초간 느린 낙하 효과를 얻어 추락사하지 않습니다.");
        p.sendMessage("전장의 판도를 바꾸는 데 특화된 유틸리티 능력입니다.");
        p.sendMessage(" ");
        p.sendMessage("쿨타임 : 0초");
        p.sendMessage("---");
        p.sendMessage("추가 장비 : 없음");
        p.sendMessage("장비 제거 : 없음");
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
        if (activeTasks.containsKey(p.getUniqueId()) && !activeTasks.get(p.getUniqueId()).isEmpty()) {
            lastInteractTime.put(p.getUniqueId(), now);
            return;
        }

        // 4. 해킹 시작
        p.sendMessage("§a임무 시작합니다.");
        hackingStartTime.put(p.getUniqueId(), now);
        lastInteractTime.put(p.getUniqueId(), now);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
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
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 20, 2));
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
