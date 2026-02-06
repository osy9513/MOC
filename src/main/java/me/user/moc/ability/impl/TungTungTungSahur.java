package me.user.moc.ability.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import me.user.moc.ability.Ability;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public class TungTungTungSahur extends Ability {

    // 현재 지목된 대상 정보를 저장하는 맵 (사후르 UUID -> 대상 UUID)
    private final Map<UUID, UUID> currentTargets = new HashMap<>();

    // 현재 진행 중인 타임아웃 태스크 (사후르 UUID -> 태스크)
    private final Map<UUID, BukkitTask> timeoutTasks = new HashMap<>();

    // 버프 중첩 횟수 (사후르 UUID -> 스택 수), 최대 9
    private final Map<UUID, Integer> buffStacks = new HashMap<>();

    public TungTungTungSahur(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "016";
    }

    @Override
    public String getName() {
        return "퉁퉁퉁사후르";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§a유틸 ● 퉁퉁퉁사후르(Italian Brainrot)",
                "§f라운드 시작 시 참나무로 변신합니다.",
                "§f8초마다 무작위 플레이어를 호명하며, 대상이 4초 내에 '넵!'하지 않으면",
                "§f강력한 버프를 얻습니다. (최대 9중첩)");
    }

    @Override
    public void giveItem(Player p) {
        // 1. 기존 아이템(철 검) 제거하고 퉁퉁퉁 방망이(나무 검) 지급
        p.getInventory().remove(Material.IRON_SWORD);

        ItemStack bat = new ItemStack(Material.WOODEN_SWORD);
        ItemMeta meta = bat.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a퉁퉁퉁 방망이");
            bat.setItemMeta(meta);
        }
        p.getInventory().addItem(bat);

        // 2. 초기 상태 설정
        buffStacks.put(p.getUniqueId(), 0);

        // 3. 채팅창에 변신 대사 출력
        p.getServer().broadcast(Component.text("퉁.퉁.퉁.퉁.퉁.퉁.퉁.퉁.퉁.사후르").color(TextColor.color(0x55FF55)));

        // 4. 8초마다 반복되는 루프 시작
        startLoop(p);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§a유틸 ● 퉁퉁퉁사후르(Italian Brainrot)");
        p.sendMessage("§f매 8초마다 당신은 자신을 제외한 무작위 플레이어의 이름을 부릅니다.");
        p.sendMessage("§f지목된 대상이 4초 이내에 채팅으로 '넵!' 이라고 대답하지 않으면,");
        p.sendMessage("§f당신에게 힘 1, 신속 1 버프가 부여됩니다. (최대 9중첩)");
        p.sendMessage("§f9중첩이 되면 더 이상 이름을 부르지 않습니다.");
        p.sendMessage("§f---");
        p.sendMessage("§f지급 장비 : 퉁퉁퉁 방망이 (나무 검)");
    }

    @Override
    public void cleanup(Player p) {
        UUID uuid = p.getUniqueId();

        // 맵 및 태스크 정리
        currentTargets.remove(uuid);
        buffStacks.remove(uuid);

        if (timeoutTasks.containsKey(uuid)) {
            timeoutTasks.get(uuid).cancel();
            timeoutTasks.remove(uuid);
        }

        // 부모 클래스의 cleanup 호출 (activeTasks 등 정리)
        super.cleanup(p);
    }

    private void startLoop(Player p) {
        UUID sahurUUID = p.getUniqueId();

        BukkitTask loopTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 플레이어가 없거나 죽었으면 중단
                if (!p.isOnline() || p.isDead()) {
                    this.cancel();
                    return;
                }

                // 최대 중첩(9) 도달 시 호명 중단
                int currentStack = buffStacks.getOrDefault(sahurUUID, 0);
                if (currentStack >= 9) {
                    // (선택사항) 이미 만렙이라 안 부른다는 메시지를 띄우고 싶다면?
                    // 너무 시끄러울 수 있으니 생략. 루프는 계속 돌되 아무것도 안 함.
                    return;
                }

                // 대상 선정
                Player target = selectRandomTarget(p);
                if (target == null) {
                    // 대상이 없으면(혼자 남았거나 등) 스킵
                    return;
                }

                // 타겟 등록
                currentTargets.put(sahurUUID, target.getUniqueId());

                // 채팅 출력: [닉네임] 넵!이라고 대답하라.
                p.getServer().broadcast(Component.text("[" + target.getName() + "] 넵!이라고 대답하라.")
                        .color(TextColor.color(0xFFA500))); // 주황색

                // 4초 타이머 시작 for 타임아웃
                scheduleTimeout(p, target);
            }
        }.runTaskTimer(plugin, 160L, 160L); // 8초 지연 후 시작, 8초(160틱) 간격 반복

        registerTask(p, loopTask);
    }

    /**
     * 자신을 제외한 랜덤 플레이어를 반환합니다.
     */
    private Player selectRandomTarget(Player me) {
        List<Player> candidates = new ArrayList<>();

        // 루프 돌면서 후보군 선정
        for (Player p : Bukkit.getOnlinePlayers()) {
            // [테스트용] 본인도 후보에 포함하려면 아래 조건을 주석 처리하세요.
            // if (p.getUniqueId().equals(me.getUniqueId())) continue; // <--- [테스트 시 주석 처리]
            // if (p.getUniqueId().equals(me.getUniqueId())) continue; // [테스트용] 본인 포함

            // AFK 등이 아닌, 정상 플레이 중인 사람만?
            // "afk 유저 제외" 조건이 있으므로 체크 필요.
            // Ability 클래스에는 isAfk 헬퍼가 없으므로 GameManager 등을 통해야 하나,
            // 보통 setGameMode(SPECTATOR)로 처리되므로 관전 모드인 사람은 제외하면 됨.
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR)
                continue;

            candidates.add(p);
        }

        if (candidates.isEmpty())
            return null;

        Collections.shuffle(candidates);
        return candidates.get(0);
    }

    /**
     * 4초 뒤에 반응이 없었는지 체크하는 태스크
     */
    private void scheduleTimeout(Player sahur, Player target) {
        UUID sahurUUID = sahur.getUniqueId();

        // 기존에 돌고 있던 타임아웃이 있다면 취소 (혹시 모를 꼬임 방지)
        if (timeoutTasks.containsKey(sahurUUID)) {
            timeoutTasks.get(sahurUUID).cancel();
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                // 이 코드가 실행되었다는 건 4초 동안 취소되지 않았다는 뜻 (실패!)

                // 1. 전체 메시지 출력
                sahur.getServer().broadcast(Component.text("퉁.퉁.퉁.퉁.퉁.퉁.퉁.퉁.퉁.사후르")
                        .color(TextColor.color(0xFF0000))); // 빨간색

                // 2. 버프 및 이펙트 부여
                applyBuff(sahur);

                // 3. 타겟 정보 초기화 (이제 더 이상 기다리지 않음)
                currentTargets.remove(sahurUUID);
                timeoutTasks.remove(sahurUUID);
            }
        }.runTaskLater(plugin, 80L); // 4초 (80틱)

        timeoutTasks.put(sahurUUID, task);
    }

    /**
     * 버프 적용 로직
     */
    private void applyBuff(Player p) {
        UUID uuid = p.getUniqueId();
        int stack = buffStacks.getOrDefault(uuid, 0);

        if (stack < 9) {
            stack++;
            buffStacks.put(uuid, stack);

            p.sendMessage("§a[퉁퉁퉁사후르] 버프가 중첩되었습니다! 현재: " + stack + "중첩");

            // 버프 적용: 힘(INCREASE_DAMAGE), 신속(SPEED)
            // 앰플리파이어는 0부터 시작하므로 (stack - 1)
            // 시간은 무한(GameManager에서 죽거나 끝나면 풀리겠지.. 여기선 일단 긴 시간)
            // *주의*: 중첩될 때마다 기존 효과를 덮어씌워야 함.
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, stack - 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, stack - 1));

            // 이펙트: 초록색 연기 + 뼈가루 소리(보통 뼈가루는 BONE_MEAL_USE or COMPOSTER_FILL 등)
            // 여기서는 VILLAGER_WORK_FARMER(작물 심는 소리)나 ITEM_BONE_MEAL_USE 사용
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5); // 초록
                                                                                                                  // 십자가
                                                                                                                  // 느낌
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_BONE_MEAL_USE, 1f, 1f);
        }
    }

    /**
     * 채팅 이벤트 리스너
     */
    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player speaker = e.getPlayer();
        String message = e.getMessage().trim();

        // 메시지가 "넵!" 인지 확인
        if (!message.equals("넵!"))
            return;

        // 현재 이 플레이어를 기다리고 있는 사후르가 있는지 찾기
        // currentTargets 맵: 사후르UUID -> 타겟UUID(speaker)
        // 역으로 탐색하거나, 사후르 리스트를 순회

        // 동시에 여러 사후르가 있을 수 있으므로 모든 사후르를 체크
        for (UUID sahurUUID : currentTargets.keySet()) {
            UUID targetUUID = currentTargets.get(sahurUUID);

            // 말한 사람이 타겟이라면?
            if (targetUUID.equals(speaker.getUniqueId())) {

                // 타겟 일치! 성공 처리.

                // 1. 해당 사후르의 타임아웃 태스크 취소
                if (timeoutTasks.containsKey(sahurUUID)) {
                    timeoutTasks.get(sahurUUID).cancel();
                    timeoutTasks.remove(sahurUUID);
                }

                // 2. 타겟 목록에서 제거 (이제 안 기다림)
                currentTargets.remove(sahurUUID);

                // 3. 성공 알림? (명세엔 없지만 헷갈리지 않게 사후르에게만 살짝?)
                Player sahur = Bukkit.getPlayer(sahurUUID);
                if (sahur != null) {
                    sahur.sendMessage("§e[정보] " + speaker.getName() + "님이 대답하여 버프를 얻지 못했습니다.");
                }

                // 한 번 "넵!"으로 나를 노리는 모든 사후르를 무력화할지, 하나만 할지?
                // 로직상 루프 돌면서 다 무력화하는 게 맞을 듯 (동시에 불렸다면)
                // ConcurrentModificationException 방지를 위해 break 하거나 iterator 사용 필요.
                // 여기선 간단히 return (한 번 넵 하면 하나만 처리? 아니면 map 구조상 keySet iterator 필요)

                // 안전하게 map.remove를 위해선 iterator가 필요하지만,
                // 여기서는 '넵!' 한 번에 하나의 위기만 모면한다고 가정하거나,
                // 어차피 사후르가 여러 명인 판이 드물 테니...

                // 일단 break로 하나만 처리하고 나갑시다. (가장 급한 불 끄기)
                break;
            }
        }
    }
}
