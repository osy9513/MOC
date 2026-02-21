package me.user.moc.ability.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location; // [Fix] Missing import
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

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Transformation;

import me.user.moc.ability.Ability;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public class TungTungTungSahur extends Ability {

    // 현재 지목된 대상 정보를 저장하는 맵 (사후르 UUID -> 대상 UUID)
    private final Map<UUID, UUID> currentTargets = new java.util.concurrent.ConcurrentHashMap<>();

    // 현재 진행 중인 타임아웃 태스크 (사후르 UUID -> 태스크)
    private final Map<UUID, BukkitTask> timeoutTasks = new java.util.concurrent.ConcurrentHashMap<>();

    // [추가] 다음 요청을 예약하는 태스크 (사후르 UUID -> 태스크)
    private final Map<UUID, BukkitTask> nextRequestTasks = new java.util.concurrent.ConcurrentHashMap<>();

    // 버프 중첩 횟수 (사후르 UUID -> 스택 수), 최대 9
    private final Map<UUID, Integer> buffStacks = new java.util.concurrent.ConcurrentHashMap<>();

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
                "§f퉁퉁퉁사후르로 변신합니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 1. 기존 아이템(철 검) 제거하고 퉁퉁퉁 방망이(막대기) 지급
        p.getInventory().remove(Material.IRON_SWORD);

        ItemStack bat = new ItemStack(Material.STICK);
        ItemMeta meta = bat.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a퉁퉁퉁 방망이");

            // [수정] 나무 검 스펙 강제 적용 (데미지 4, 공속 1.6)
            // 기본 막대기: 데미지 1, 공속 4.0 (빠름)
            // 목표: 데미지 4 (+3), 공속 1.6 (-2.4)
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(new org.bukkit.NamespacedKey(plugin, "tungtung_damage"), 3.0,
                            AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.HAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                    new AttributeModifier(new org.bukkit.NamespacedKey(plugin, "tungtung_speed"), -2.4,
                            AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.HAND));
            // CustomModelData를 1로 설정 -> 텍스처팩이 이걸 보고 퉁퉁퉁 방망이 그림을 보여줌
            meta.setCustomModelData(1);
            bat.setItemMeta(meta);
        }
        p.getInventory().addItem(bat);

        // [추가] 철 흉갑 제거 (맨몸 시작)
        p.getInventory().setChestplate(null);

        // [추가] 나무 변신 시작
        startDisguise(p);

        // 2. 초기 상태 설정
        buffStacks.put(p.getUniqueId(), 0);

        // 3. 채팅창에 변신 대사 출력
        p.getServer().broadcast(Component.text("퉁.퉁.퉁.퉁.퉁.퉁.퉁.퉁.퉁.사후르").color(TextColor.color(0xc16c15)));

        // 4. 최초 요청 예약 (8초 후 시작, 혹은 4초 후? 기존 8초 였으니 4초 대기 후 시작으로 변경)
        scheduleNextRequest(p, 80L); // 4초 후 첫 번째 질문 시작
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§a유틸 ● 퉁퉁퉁사후르(Italian Brainrot)");
        p.sendMessage("§f매 8초마다 당신은 자신을 제외한 무작위 플레이어의 이름을 부릅니다.");
        p.sendMessage("§f지목된 대상이 4초 이내에 채팅으로 '넵!' 이라고 대답하지 않으면,");
        p.sendMessage("§f당신에게 힘 1, 신속 1 버프가 부여됩니다. (최대 9중첩)");
        p.sendMessage("§f9중첩이 되면 더 이상 이름을 부르지 않습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 퉁퉁퉁 방망이");
        p.sendMessage("§f장비 제거 : 철 흉갑, 철 검");

    }

    // [추가] 변신 엔티티 직접 관리용 맵 (Cleanup 보장)
    private final Map<UUID, BlockDisplay> disguises = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void cleanup(Player p) {
        UUID uuid = p.getUniqueId();

        // [핵심] 변신 엔티티 확실하게 제거
        if (disguises.containsKey(uuid)) {
            BlockDisplay display = disguises.remove(uuid);
            if (display != null && display.isValid()) {
                display.remove();
            }
        }

        // 맵 및 태스크 정리
        currentTargets.remove(uuid);
        buffStacks.remove(uuid);

        if (timeoutTasks.containsKey(uuid)) {
            timeoutTasks.get(uuid).cancel();
            timeoutTasks.remove(uuid);
        }

        if (nextRequestTasks.containsKey(uuid)) {
            nextRequestTasks.get(uuid).cancel();
            nextRequestTasks.remove(uuid);
        }

        // 변신 해제
        p.removePotionEffect(PotionEffectType.INVISIBILITY);

        // [추가] 적용된 버프 제거 (힘, 신속)
        p.removePotionEffect(PotionEffectType.STRENGTH);
        p.removePotionEffect(PotionEffectType.SPEED);

        // 부모 클래스의 cleanup 호출 (activeTasks, activeEntities 등 정리)
        super.cleanup(p);
    }

    // [추가] 나무 변신 로직
    private void startDisguise(Player p) {
        // 1. 투명화 적용 (갑옷은 보일 수 있음 -> 기획상 허용? 보통 벗게 하거나 함. 여기선 일단 투명화만)
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, true,
                true, true));

        // 2. BlockDisplay 소환 (참나무 원목)
        BlockDisplay display = (BlockDisplay) p.getWorld().spawnEntity(p.getLocation(),
                org.bukkit.entity.EntityType.BLOCK_DISPLAY);
        display.setBlock(Material.OAK_LOG.createBlockData());

        // 크기 및 위치 조정
        // 플레이어 키가 약 1.8m, 폭 0.6m. 통나무는 1x1x1.
        // 살짝 줄이거나 늘려서 맞춤. (폭 0.7, 높이 1.9 정도?)
        Transformation transform = display.getTransformation();
        transform.getScale().set(0.7f, 1.9f, 0.7f);
        // 중심점 조정 (발 밑이 기준이므로)
        transform.getTranslation().set(-0.35f, 0f, -0.35f); // 블록 중심 맞추기 (0,0에서 시작하므로 -0.5해야 중앙인데, 스케일 고려)
        // 스케일 되면 원점 기준으로 커짐?? -> BlockDisplay 원점은 모서리(0,0,0).
        // scale 0.7이면 0~0.7 차지.
        // 플레이어 중심은 (0.5, 0, 0.5) 느낌.
        // -0.35 하면: PlayerLoc(중심) + (-0.35) -> Player보다 약간 옆?
        // BlockDisplay default location is center of bottom face? No, corner.
        // 0.7 scale -> center is 0.35. We want center at Player center (0).
        // So translation should be -0.35.

        display.setTransformation(transform);

        // 등록 (Cleanup을 위해 - 부모에도 등록하고, 로컬에도 등록)
        registerSummon(p, display);
        disguises.put(p.getUniqueId(), display);

        // 3. 따라다니기 태스크
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || !display.isValid()) {
                    this.cancel();
                    if (display.isValid())
                        display.remove();
                    disguises.remove(p.getUniqueId()); // 맵에서도 제거
                    return;
                }

                // 위치 동기화 (회전 포함?)
                // 통나무가 회전하면 이상할 수도 있지만, 시선 따라가는게 자연스러움.
                // 다만 BlockDisplay 회전은 Transformation으로 해야 함? 아니면 Entity Rotation?
                // Entity Rotation은 Teleport로 가능.

                Location loc = p.getLocation();
                // BlockDisplay는 모서리 기준이므로, 플레이어 위치 그대로 가면 발 밑 코너에 생김.
                // 아까 Translation으로 조정했으니, 플레이어 위치 그대로 가면 됨.
                // 하지만 회전이 문제.

                // 단순하게: 플레이어 위치 + Yaw만 적용하려 했으나, 요청사항에 따라 "고정" (회전 X)
                loc.setYaw(0);
                loc.setPitch(0);

                // Transformation을 쓰지 않고 setRotation을 쓰거나 teleport.
                display.teleport(loc);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        registerTask(p, task);
    }

    // [추가] 피격 시 나무 타격 효과
    @EventHandler
    public void onPlayerDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p))
            return;

        // 이 능력을 가진 사람인지 확인 (그리고 변신 중인지?)
        // 변신 중 여부는 activeEntities에 BlockDisplay가 있는지 등으로 확인 가능하지만,
        // cleanup 전까진 계속 유지되므로 Ability 보유 여부로 체크.
        if (!me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode())) {
            return;
        }

        // 데미지 0이거나 캔슬된거면 무시? (그래도 맞았으면 소리는 나야지)

        // 나무 타격음 및 파티클
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_WOOD_BREAK, 1f, 1f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);

        p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3,
                Material.OAK_LOG.createBlockData());
    }

    /**
     * 다음 요청을 예약합니다.
     * 
     * @param delayTicks 지연 시간 (틱 단위)
     */
    private void scheduleNextRequest(Player p, long delayTicks) {
        UUID uuid = p.getUniqueId();

        // 기존 예약된 게 있다면 취소
        if (nextRequestTasks.containsKey(uuid)) {
            nextRequestTasks.get(uuid).cancel();
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                // 실행 시점에서 맵에서 제거
                nextRequestTasks.remove(uuid);
                startRequestCycle(p);
            }
        }.runTaskLater(plugin, delayTicks);

        nextRequestTasks.put(uuid, task);
    }

    /**
     * 실제 질문 사이클을 시작합니다.
     */
    private void startRequestCycle(Player p) {
        // 플레이어가 없거나 죽었으면 중단
        if (!p.isOnline() || p.isDead()) {
            return;
        }

        // [추가] 침묵 체크
        if (isSilenced(p)) {
            return;
        }

        UUID sahurUUID = p.getUniqueId();

        // 최대 중첩(9) 도달 시 스케줄링 중단? 아니면 계속 체크?
        // 기획 상 "더 이상 이름을 부르지 않습니다" -> 아예 멈춤.
        int currentStack = buffStacks.getOrDefault(sahurUUID, 0);
        if (currentStack >= 9) {
            return;
        }

        // 대상 선정
        Player target = selectRandomTarget(p);
        if (target == null) {
            // 대상이 없으면(혼자 남았거나 등) 잠시 후 다시 시도 (4초 뒤)
            scheduleNextRequest(p, 80L);
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

    /**
     * 자신을 제외한 랜덤 플레이어를 반환합니다.
     */
    private Player selectRandomTarget(Player me) {
        List<Player> candidates = new ArrayList<>();

        // 루프 돌면서 후보군 선정
        for (Player p : Bukkit.getOnlinePlayers()) {
            // 자신은 지목 대상에서 제외
            if (p.getUniqueId().equals(me.getUniqueId()))
                continue;

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

                // 4. [추가] 사이클 종료 후 4초 뒤 다음 요청 예약
                scheduleNextRequest(sahur, 80L);
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
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, stack - 1,
                    true, true, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, stack - 1, true,
                    true, true));

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
    /**
     * 채팅 이벤트 리스너
     */
    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player speaker = e.getPlayer();
        String message = e.getMessage().trim();

        // 메시지가 정확히 "넵!" 인지 확인
        if (!message.equals("넵!"))
            return;

        // 비동기 이벤트이므로 안전하게 메인 스레드에서 로직 처리
        new BukkitRunnable() {
            @Override
            public void run() {
                // Match된 사후르 저장
                List<UUID> matchedSahurs = new ArrayList<>();
                for (Map.Entry<UUID, UUID> entry : currentTargets.entrySet()) {
                    if (entry.getValue().equals(speaker.getUniqueId())) {
                        matchedSahurs.add(entry.getKey());
                    }
                }

                // 매치된 사후르들에 대해 성공 처리
                for (UUID sahurUUID : matchedSahurs) {
                    // 1. 해당 사후르의 타임아웃 태스크 취소
                    if (timeoutTasks.containsKey(sahurUUID)) {
                        timeoutTasks.get(sahurUUID).cancel();
                        timeoutTasks.remove(sahurUUID);
                    }

                    // 2. 타겟 목록에서 제거 (이제 안 기다림)
                    currentTargets.remove(sahurUUID);

                    // 3. 성공 알림 (전체 공지)
                    plugin.getServer()
                            .broadcast(Component.text("§e[정보] " + speaker.getName() + "님이 대답하여 버프를 얻지 못했습니다."));

                    // 4. 성공했으므로 4초 뒤 다음 요청 예약
                    Player sahur = Bukkit.getPlayer(sahurUUID);
                    if (sahur != null) {
                        scheduleNextRequest(sahur, 80L);
                    }
                }
            }
        }.runTask(plugin);
    }
}
