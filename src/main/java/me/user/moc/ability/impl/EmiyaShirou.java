package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.*;
import org.bukkit.util.Transformation;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class EmiyaShirou extends Ability {

    // 상태 관리
    private final Map<UUID, Boolean> isChanting = new HashMap<>();
    private final Map<UUID, Boolean> isActive = new HashMap<>();
    private final Map<UUID, Boolean> hasTriggered = new HashMap<>(); // 이번 라운드 자동 발동 여부

    // 영창 대사
    private final String[] CHANT_LINES = {
            "§6I am the bone of my sword.",
            "§6Steel is my body, and fire is my blood.",
            "§6I have created over a thousand blades.",
            "§6Unknown to Death.",
            "§6Nor known to Life.",
            "§6Have withstood pain to create many weapons.",
            "§6Yet, those hands will never hold anything.",
            "§c§lSo as I pray, Unlimited Blade Works."
    };

    // 키값
    private static final String KEY_UBW_HOMING = "MOC_UBW_HOMING";

    public EmiyaShirou(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "012";
    }

    @Override
    public String getName() {
        return "에미야 시로";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§e전투 ● 에미야 시로(FATE)",
                "§f라운드 시작 15초 후 무한의 검제 영창을 전개합니다.");
    }

    @Override
    public void giveItem(Player p) {
        p.getInventory().remove(Material.IRON_SWORD);
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        org.bukkit.inventory.meta.ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c막야");
            meta.setLore(List.of("§7라운드 시작 15초 후 무한의 검제를 전개합니다."));
            meta.setCustomModelData(10); // 리소스팩: emiyashirou
            sword.setItemMeta(meta);
        }
        p.getInventory().addItem(sword);

        startAutoTriggerCheck();
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● 에미야 시로(FATE)");
        p.sendMessage("§f라운드 시작 후 무적 시간이 끝난 배틀 시간이 되었을 때 15초 후");
        p.sendMessage("§f기존의 무한 검제 영창을 자동으로 시작합니다.");
        p.sendMessage(" ");
        p.sendMessage("§f[무한의 검제]");
        p.sendMessage("§f영창 완료 후 자신 주변 5x5 지형에서 검이 1초 간격으로 바닥에서 솟아오릅니다.");
        p.sendMessage("§f검 주변에 적이 발견되면 자동으로 날아가 꽂히며 §c4의 데미지§f를 줍니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 20초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    // === 자동 발동 체크 ===
    private BukkitTask autoCheckTask; // [추가] 태스크 참조 저장

    private void startAutoTriggerCheck() {
        // [Fix] 이미 돌고 있으면 중복 실행 방지
        if (autoCheckTask != null && !autoCheckTask.isCancelled()) {
            return;
        }

        autoCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 플러그인이 비활성화되면 중단
                if (!plugin.isEnabled()) {
                    this.cancel();
                    autoCheckTask = null;
                    return;
                }

                // 모든 플레이어 체크 (사실상 1명만 할당되겠지만 구조상 Loop)
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
                        checkAndTrigger(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 1초마다 체크
    }

    private void checkAndTrigger(Player p) {
        UUID uuid = p.getUniqueId();
        // [Fix] 이미 발동했거나 진행 중이면 패스
        if (hasTriggered.getOrDefault(uuid, false))
            return;
        if (isChanting.getOrDefault(uuid, false) || isActive.getOrDefault(uuid, false))
            return;

        // 전투 시작 여부 확인
        if (MocPlugin.getInstance().getGameManager().isBattleStarted()) {
            // [추가] 관전 모드이거나 죽은 상태면 발동 안함
            if (p.getGameMode() == GameMode.SPECTATOR || p.isDead()) {
                // 죽어서 발동 못한 경우에도 발동한 것으로 처리할지?
                // 아니면 부활하면 발동하게 할지? -> 일단 발동 처리하여 무한 루프 방지
                hasTriggered.put(uuid, true);
                return;
            }

            // [핵심] 여기서 true로 만들어서 중복 진입 차단
            hasTriggered.put(uuid, true);

            // 15초 카운트다운 후 영창 시작
            new BukkitRunnable() {
                int timeLeft = 15;

                @Override
                public void run() {
                    // [수정] 체크 강화
                    if (!p.isOnline() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) {
                        this.cancel();
                        return;
                    }

                    if (timeLeft <= 0) {
                        startChant(p);
                        this.cancel();
                        return;
                    }

                    // 액션바에 카운트다운 표시
                    p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            net.md_5.bungee.api.chat.TextComponent
                                    .fromLegacyText("§c[무한의 검제] 개방까지 §e" + timeLeft + "초"));

                    timeLeft--;
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }
    }

    // [복구] 삭제된 startChant 메서드 및 관련 메서드 복구
    private void startChant(Player p) {
        UUID uuid = p.getUniqueId();
        if (isChanting.getOrDefault(uuid, false) || isActive.getOrDefault(uuid, false))
            return; // 이중 안전장치

        isChanting.put(uuid, true);
        p.setPlayerTime(12500, false); // 노을

        // 영창 태스크
        BukkitTask task = new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancelChant(p);
                    this.cancel();
                    return;
                }

                if (index < CHANT_LINES.length) {
                    Bukkit.broadcastMessage(chantSpeaker() + CHANT_LINES[index]);
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f,
                            0.6f + (index * 0.1f));

                    // 파티클
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0, 255, 255), 1.0f);
                    p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1.5, 0), 10, 0.5, 0.5, 0.5, dust);

                    index++;
                } else {
                    // [추가] 영창 완료 시 노란색 폭발 이펙트 & 사운드 (3초간 유지)
                    p.getWorld().spawnParticle(Particle.EXPLOSION, p.getLocation().add(0, 1, 0), 3);
                    p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 50, 0.5, 1, 0.5,
                            new Particle.DustOptions(Color.YELLOW, 2));

                    // 사운드 시퀀스 (3초간 펑펑펑)
                    new BukkitRunnable() {
                        int count = 0;

                        @Override
                        public void run() {
                            if (count >= 3) {
                                // 3초 후 하늘 색 원상복구
                                if (p.isOnline()) {
                                    p.resetPlayerTime();
                                    // 완료 사운드
                                    p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 1.0f);
                                }
                                this.cancel();
                                return;
                            }

                            if (p.isOnline()) {
                                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f,
                                        1.0f - (count * 0.2f));
                                p.getWorld().spawnParticle(Particle.EXPLOSION, p.getLocation().add(0, 1 + count, 0), 1);
                            }
                            count++;
                        }
                    }.runTaskTimer(plugin, 0L, 20L); // 1초 간격으로 3번 실행 + 종료

                    startUBW(p);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1초 간격

        registerTask(p, task);
    }

    private String chantSpeaker() {
        return "§e에미야 시로 : ";
    }

    private void cancelChant(Player p) {
        if (p.isOnline())
            p.resetPlayerTime();
        // [Fix] isChanting, isActive map handling
        isChanting.put(p.getUniqueId(), false);
        isActive.put(p.getUniqueId(), false);
        p.sendMessage("§c영창이 취소되었습니다.");
    }

    private void startUBW(Player p) {
        UUID uuid = p.getUniqueId();
        // 검 생성 스케줄러 (1초마다 1개씩, 무제한)
        BukkitTask spawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || !isActive.getOrDefault(uuid, false)) { // 사망 시 능력 중단
                    cleanup(p);
                    this.cancel();
                    return;
                }

                // [수정] 실시간 플레이어 위치 추적하여 주변에 소환
                Location center = p.getLocation();
                // 5x5 범위 (반경 2.5) 내 랜덤 위치
                double dx = (Math.random() * 5.0) - 2.5;
                double dz = (Math.random() * 5.0) - 2.5;

                // [Fix] Y좌표 보정: getHighestBlockYAt은 실내에서 천장을 잡거나 땅굴에서 지상을 잡는 문제가 있음
                // 따라서 플레이어의 Y좌표 기준으로 아래로 탐색하여 바닥을 찾음
                int targetY = center.getBlockY();
                // 플레이어 발밑부터 아래로 5칸 탐색
                for (int i = 0; i < 5; i++) {
                    Location check = center.clone().add(dx, -i, dz);
                    if (check.getBlock().getType().isSolid()) {
                        targetY = check.getBlockY();
                        break;
                    }
                }
                // 만약 위쪽으로 바닥이 있을 수도 있음 (반블록 등) -> 위로 2칸 탐색
                if (targetY == center.getBlockY()) { // 아래에서 못 찾았거나 바로 발밑인 경우
                    for (int i = 1; i <= 2; i++) {
                        Location check = center.clone().add(dx, i, dz);
                        if (check.getBlock().getType().isSolid()) {
                            targetY = check.getBlockY(); // 더 높은 바닥이 있으면 거기로
                        }
                    }
                }

                // 바닥 + 1.2 (약간 위)
                Location spawnLoc = new Location(p.getWorld(), center.getX() + dx, targetY + 1.2, center.getZ() + dz);
                // 혹시 블록 속에 묻히면 안 되니까
                if (spawnLoc.getBlock().getType().isSolid()) {
                    spawnLoc.add(0, 1, 0);
                }

                spawnRisingSwordAction(p, spawnLoc);
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1초마다 실행

        registerTask(p, spawnTask);
    }

    private void spawnRisingSwordAction(Player owner, Location targetLoc) {
        // 1. ItemDisplay 생성 (땅 속에 박힌 상태로 시작)
        // 시작 높이: 목표 높이보다 1.5칸 아래
        Location startLoc = targetLoc.clone().add(0, -1.5, 0);

        ItemDisplay display = owner.getWorld().spawn(startLoc, ItemDisplay.class);
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        org.bukkit.inventory.meta.ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(10); // 리소스팩: emiyashirou
            sword.setItemMeta(meta);
        }
        display.setItemStack(sword);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED); // 제어 용이성

        // 크기 및 회전 (검이 거꾸로 박혀있거나, 위로 솟아오르는 형태)
        Transformation transform = display.getTransformation();
        transform.getScale().set(1.5f, 1.5f, 1.5f);
        // X축 135도 회전 시 검이 수직으로 꽂힌 모양이 됨
        // AxisAngle4f 사용 (joml)
        transform.getLeftRotation().set(new AxisAngle4f((float) Math.toRadians(135), 0, 0, 1));
        display.setTransformation(transform);

        registerSummon(owner, display);

        // 2. 상승 애니메이션 (Ground -> +2 높이까지) & 조준/발사 로직
        BukkitTask riseTask = new BukkitRunnable() {
            int tick = 0;
            boolean rising = true;
            Location currentLoc = startLoc.clone();

            @Override
            public void run() {
                if (!display.isValid() || !owner.isOnline()) {
                    display.remove();
                    this.cancel();
                    return;
                }

                if (rising) {
                    // 상승 로직
                    if (tick < 20) {
                        currentLoc.add(0, 0.15, 0); // 20틱 * 0.15 = 3칸 이동 (-2 -> +1)
                        display.teleport(currentLoc);
                        tick++;
                    } else {
                        // 상승 완료 -> 타겟팅 모드로 전환
                        rising = false;
                        tick = 0; // 틱 초기화 (대기 시간 용)
                    }
                } else {
                    // 3. 조준 및 발사 대기
                    if (tick > 100) { // 5초 동안 적 못 찾으면 삭제
                        display.remove();
                        this.cancel();
                        return;
                    }

                    // 적 탐색 (반경 10칸)
                    LivingEntity target = null;
                    double closestDist = 100.0;

                    for (Entity e : display.getWorld().getNearbyEntities(display.getLocation(), 10, 10, 10)) {
                        if (e != owner && e instanceof LivingEntity le) {
                            if (le instanceof Player pTarget
                                    && (pTarget.getGameMode() == GameMode.SPECTATOR || pTarget.isDead()))
                                continue;

                            // 시야 조건 없이 거리만 체크
                            double d = e.getLocation().distance(display.getLocation());
                            if (d < closestDist) {
                                closestDist = d;
                                target = le;
                            }
                        }
                    }

                    if (target != null) {
                        // 적 발견! 발사!
                        fireSword(owner, display, target);
                        this.cancel(); // 이 태스크는 종료하고 발사 태스크로 넘김
                    }

                    tick++;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        registerTask(owner, riseTask);
    }

    private void fireSword(Player owner, ItemDisplay display, LivingEntity target) {
        owner.getWorld().playSound(display.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.5f);

        // 발사 태스크
        BukkitTask fireTask = new BukkitRunnable() {
            Location currentLoc = display.getLocation().clone();
            Vector dir = target.getEyeLocation().subtract(currentLoc).toVector().normalize();
            double speed = 1.5; // 투사체 속도
            double distanceTraveled = 0;
            double maxDistance = 30;

            @Override
            public void run() {
                if (!display.isValid() || distanceTraveled > maxDistance) {
                    display.remove();
                    this.cancel();
                    return;
                }

                // 이동
                Vector move = dir.clone().multiply(speed);
                Location nextLoc = currentLoc.clone().add(move);

                // 회전 (날아가는 방향으로)
                Transformation t = display.getTransformation();
                t.getRightRotation().rotateZ((float) Math.toRadians(45)); // Z축 회전 추가
                display.setTransformation(t);

                // 충돌 체크
                // 1. 블록 충돌
                if (!nextLoc.getBlock().isPassable()) {
                    display.getWorld().playSound(nextLoc, Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                    display.getWorld().spawnParticle(Particle.CRIT, nextLoc, 10);
                    display.remove();
                    this.cancel();
                    return;
                }

                // 2. 엔티티 충돌
                for (Entity e : currentLoc.getWorld().getNearbyEntities(currentLoc, 1.0, 1.0, 1.0)) {
                    if (e != owner && e instanceof LivingEntity le) {
                        if (le instanceof Player pTarget && (pTarget.getGameMode() == GameMode.SPECTATOR))
                            continue;

                        // 타격
                        le.damage(4.0, owner);
                        le.getWorld().playSound(le.getLocation(), Sound.ENTITY_ARROW_HIT, 1f, 1f);

                        // [추가] 타격 이펙트 (피 튀기는 효과 대신 레드스톤 블록 파괴 효과)
                        le.getWorld().spawnParticle(Particle.BLOCK, le.getLocation().add(0, 1, 0), 10,
                                Material.REDSTONE_BLOCK.createBlockData());

                        display.remove();
                        this.cancel();
                        return;
                    }
                }

                currentLoc = nextLoc;
                display.teleport(currentLoc);
                distanceTraveled += speed;

                // [유도력] 타겟이 움직였으면 방향 약간 보정 (약한 유도)
                if (target.isValid() && !target.isDead()) {
                    Vector targetDir = target.getEyeLocation().subtract(currentLoc).toVector().normalize();
                    // 기존 방향 80% + 타겟 방향 20%
                    dir.multiply(0.8).add(targetDir.multiply(0.2)).normalize();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        registerTask(owner, fireTask);
    }

    // === 이벤트 처리 ===

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
            // 사망 시 즉시 능력 종료 및 소환물 제거
            cleanup(p);
            p.sendMessage("§c사망하여 무한의 검제가 소멸했습니다.");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        // 기존 화살 로직 제거됨.
        // ItemDisplay가 직접 충돌 체크하고 damage()를 주므로,
        // 여기서 별도로 처리할 것은 없음. (ItemDisplay는 damage 이벤트를 발생시키지 않음 -> direct damage)
        // 만약 damage() 호출로 인해 이 이벤트가 다시 발생한다면?
        // -> Damager가 owner(Player)로 전달됨.
        // -> 특별히 막을 건 없음.
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        UUID uuid = p.getUniqueId();
        isChanting.remove(uuid); // remove or put false, removing is fine as getOrDefault(uuid, false) handles it
        isActive.remove(uuid);
        hasTriggered.remove(uuid);
        p.resetPlayerTime();

        // activeEntities와 activeTasks는 부모 클래스(Ability)에서 처리하므로
        // 별도의 activeSwords, flyingSwords 정리 필요 없음.
    }

    @Override
    public void reset() {
        super.reset();
        // 전체 초기화 (게임 종료 시)
        isChanting.clear();
        isActive.clear();
        hasTriggered.clear();

        // 부모의 reset 호출 (있다면)
        // Ability.reset()은 없지만, Global Map들은 AbilityManager 등에서 관리되거나 개별 cleanup 호출로
        // 정리됨.
        // 여기선 static/전역 상태만 정리.
    }
}
