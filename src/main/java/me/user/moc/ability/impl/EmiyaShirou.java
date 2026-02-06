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

    // 소환된 검 관리
    private class RisingSword {
        ArmorStand stand;
        boolean fired;

        public RisingSword(ArmorStand stand) {
            this.stand = stand;
            this.fired = false;
        }
    }

    private final Map<UUID, List<RisingSword>> activeSwords = new HashMap<>();
    private final Map<UUID, List<ArmorStand>> flyingSwords = new HashMap<>(); // 날아가는 중인 검 (제거용)

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
    private void startAutoTriggerCheck() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // 플러그인이 비활성화되면 중단
                if (!plugin.isEnabled()) {
                    this.cancel();
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
        if (hasTriggered.getOrDefault(uuid, false))
            return;
        if (isChanting.getOrDefault(uuid, false) || isActive.getOrDefault(uuid, false))
            return;

        // 전투 시작 여부 확인
        if (MocPlugin.getInstance().getGameManager().isBattleStarted()) {
            // [추가] 관전 모드이거나 죽은 상태면 발동 안함
            if (p.getGameMode() == GameMode.SPECTATOR || p.isDead()) {
                hasTriggered.put(uuid, true); // 더 이상 체크하지 않도록 true로 설정
                return;
            }

            hasTriggered.put(uuid, true); // 중복 실행 방지
            // 알림 필요 없음
            // p.sendMessage("§7[System] 15초 후 무한의 검제 영창을 시작합니다...");

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
                    p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f,
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

    private String chantSpeaker() { // removed argument
        return "§e에미야 시로 : ";
    }

    private void cancelChant(Player p) {
        if (p.isOnline())
            p.resetPlayerTime();
        isChanting.put(p.getUniqueId(), false);
        isActive.put(p.getUniqueId(), false);
        p.sendMessage("§c영창이 취소되었습니다.");
    }

    private void startUBW(Player p) {
        if (!p.isOnline() || p.isDead()) {
            cancelChant(p);
            return;
        }

        UUID uuid = p.getUniqueId();
        isChanting.put(uuid, false);
        isActive.put(uuid, true);

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 2.0f, 1.0f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.5f);

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
                Location targetLoc = center.add(dx, 0, dz);

                spawnRisingSwordAction(p, targetLoc);
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1초마다 실행

        registerTask(p, spawnTask);
    }

    private void spawnRisingSwordAction(Player owner, Location xzLoc) {
        // Y좌표 찾기 (Bedrock)
        // 플레이어 높이부터 아래로 탐색
        int startY = xzLoc.getBlockY();
        int bedrockY = xzLoc.getWorld().getMinHeight(); // 기본값

        for (int y = startY; y >= xzLoc.getWorld().getMinHeight(); y--) {
            if (xzLoc.getWorld().getBlockAt(xzLoc.getBlockX(), y, xzLoc.getBlockZ()).getType() == Material.BEDROCK) {
                bedrockY = y;
                break;
            }
        }

        // Bedrock 바로 위 높이 보정
        // [수정] 솟아오르는 높이를 높힘 (Bedrock을 뚫고 올라오는 느낌 강화)

        double startHeight = bedrockY - 1.5;
        double finalHeight = bedrockY + 0.5; // 최종 높이 (거의 바닥까지 올라옴)

        Location spawnLoc = new Location(xzLoc.getWorld(), xzLoc.getX(), startHeight, xzLoc.getZ());
        // [추가] 칼의 방향을 랜덤으로 설정 (단조로움 회피)
        spawnLoc.setYaw((float) (Math.random() * 360));

        // 아머스탠드 생성
        ArmorStand stand = xzLoc.getWorld().spawn(spawnLoc, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true); // 히트박스 제거 (클릭 방지)
        stand.setBasePlate(false);
        stand.setArms(true);

        // 철 칼 장착 (칼날이 아래를 보게? -> ArmorStand 팔 각도 조절 필요)
        // 원작 고증: 칼이 땅에 꽂혀있는 형태에서 솟아오름 -> 핸들이 위, 칼날이 아래.
        // ArmorStand 팔 기본 각도는 앞으로 뻗음.
        // setRightArmPose로 조절.
        // X축 -90도면 위로 듬, 90도면 아래로 내림 logic.
        // 칼날이 위를 향해 솟아올라야 발사될 때 자연스러움. (아니면 요청사항: "칼날이 아래를 바라보는 채")
        // "칼날이 아래를 바라보는 채... 한칸 높게 올라옵니다." -> 거꾸로 솟아오름?
        // 보통 UBW는 칼자루가 하늘, 칼날이 땅에 박힌 상태임.
        stand.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));

        // 칼날이 아래로 가려면: 오른팔을 아래로 내리거나 회전시켜야 함.
        stand.setRightArmPose(new EulerAngle(Math.toRadians(90), 0, 0)); // 팔을 아래로 내림 -> 칼날도 아래?
        // 테스트 필요하지만, 일단 90도면 아래를 가리킬 것임.

        RisingSword rSword = new RisingSword(stand);
        activeSwords.computeIfAbsent(owner.getUniqueId(), k -> new ArrayList<>()).add(rSword);

        // 솟아오르는 애니메이션 (1초에 걸쳐 1칸 위로)
        new BukkitRunnable() {
            int ticks = 0;
            final int DURATION = 20; // 1초
            double step = (finalHeight - startHeight) / DURATION; // 부드럽게 상승

            @Override
            public void run() {
                // [수정] 리스트 포함 여부 체크 (UUID 맵 경유)
                boolean stillActive = false;
                List<RisingSword> mySwords = activeSwords.get(owner.getUniqueId());
                if (mySwords != null && mySwords.contains(rSword)) {
                    stillActive = true;
                }

                if (stand.isDead() || !stillActive) {
                    this.cancel();
                    stand.remove();
                    return;
                }

                if (ticks >= DURATION) {
                    // 상승 완료 -> 감시 모드 시작
                    startScanning(owner, rSword);
                    this.cancel();
                    return;
                }

                Location current = stand.getLocation();
                current.add(0, step, 0);
                stand.teleport(current);

                // 흙먼지 효과
                if (ticks % 5 == 0) {
                    stand.getWorld().spawnParticle(Particle.BLOCK, stand.getLocation(), 3, 0.2, 0.1, 0.2,
                            Material.BEDROCK.createBlockData());
                    // [추가] 칼이 솟아오를 때 파란색 파티클 효과 추가
                    stand.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, stand.getLocation().add(0, 0.5, 0), 5, 0.1,
                            0.1, 0.1, 0.02);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startScanning(Player owner, RisingSword rSword) {
        // 감시 태스크 (매 4틱마다 검사)
        BukkitTask scanTask = new BukkitRunnable() {
            @Override
            public void run() {
                // [수정] 리스트 포함 여부 체크 (UUID 맵 경유)
                boolean stillActive = false;
                List<RisingSword> mySwords = activeSwords.get(owner.getUniqueId());
                if (mySwords != null && mySwords.contains(rSword)) {
                    stillActive = true;
                }

                if (rSword.stand.isDead() || rSword.fired || !stillActive) {
                    this.cancel();
                    return;
                }

                if (owner.isDead() || !owner.isOnline()) { // 주인 사망 시 소멸
                    rSword.stand.remove();
                    this.cancel();
                    return;
                }

                // ArmorStand 칼 기준
                // [수정] 인식 범위 버프 x y z 높이는 높게 잡아서 거의 반드시 맞추게끔
                List<Entity> nearby = rSword.stand.getNearbyEntities(12, 50, 12);
                LivingEntity target = null;

                for (Entity e : nearby) {
                    if (e instanceof LivingEntity le && e != owner && !e.isDead() && !(e instanceof ArmorStand)) {
                        // 타겟팅 조건: 서바이벌 모드 플레이어 등
                        if (e instanceof Player pTarget && pTarget.getGameMode() == GameMode.SPECTATOR)
                            continue;

                        target = le;
                        break; // 한 명만 걸리면 발사
                    }
                }

                if (target != null) {
                    fireSword(owner, rSword, target);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);

        registerTask(owner, scanTask);
    }

    private void fireSword(Player owner, RisingSword rSword, LivingEntity target) {
        if (rSword.fired)
            return;

        List<RisingSword> mySwords = activeSwords.get(owner.getUniqueId());
        if (mySwords != null)
            mySwords.remove(rSword);

        rSword.fired = true;

        ArmorStand stand = rSword.stand;
        flyingSwords.computeIfAbsent(owner.getUniqueId(), k -> new ArrayList<>()).add(stand);

        // 발사 로직
        // 1. 타겟 방향 벡터 계산
        Location startLoc = stand.getLocation().add(0, 1.5, 0); // 눈높이 보정
        Location targetLoc = target.getEyeLocation();
        Vector dir = targetLoc.toVector().subtract(startLoc.toVector()).normalize();

        // 2. 투명 화살(Projectile) 생성하여 태우기
        // 직접 이동시키면 충돌 판정이 어려우므로 Arrow를 사용
        Arrow arrow = stand.getWorld().spawn(startLoc, Arrow.class);
        arrow.setShooter(owner);
        arrow.setDamage(4.0); // [수정] 데미지 너프 (8 -> 4)
        arrow.setVelocity(dir.multiply(1.5)); // [수정] 속도 2배 너프 (3.0 -> 1.5)
        arrow.setSilent(true);
        arrow.setGravity(false); // [추가] 포물선 제거 (직선 비행)
        // [추가] 화살 줍기 방지 (인벤토리 들어오는 것 방지)
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);

        arrow.setMetadata(KEY_UBW_HOMING, new FixedMetadataValue(plugin, owner.getUniqueId().toString()));
        arrow.addPassenger(stand); // 아머스탠드를 화살에 태움

        // 소리 [수정] 칼 날아가는 소리 (날카로운 금속음)
        stand.getWorld().playSound(startLoc, Sound.ITEM_TRIDENT_THROW, 1.0f, 2.0f); // 쉭! (고음)
        stand.getWorld().playSound(startLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f); // 챙!

        stand.getWorld().spawnParticle(Particle.CRIT, startLoc, 5);

        // 디스폰 체크용 태스크 (화살 박히면 제거, 10초 제한 추가)
        new BukkitRunnable() {
            int timer = 0; // [추가] 10초 자동 제거를 위한 타이머

            @Override
            public void run() {
                if (arrow.isDead() || !arrow.isValid() || arrow.isOnGround() || timer >= 200) {
                    stand.remove();
                    List<ArmorStand> myFlying = flyingSwords.get(owner.getUniqueId());
                    if (myFlying != null)
                        myFlying.remove(stand);

                    arrow.remove(); // [추가] 화살도 즉시 제거 (전장에 남지 않게)
                    this.cancel();
                    return;
                }

                // [추가] 10초 뒤 자동 제거를 위한 타이머 증가
                timer++;

                // [추가] 비행 중 파티클 (연한 푸른색)
                Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(150, 255, 255), 0.5f);
                arrow.getWorld().spawnParticle(Particle.DUST, arrow.getLocation(), 3, 0.1, 0.1, 0.1, dust);
            }
        }.runTaskTimer(plugin, 1L, 1L); // 1틱마다 실행 (부드럽게)
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
        // 화살 데미지 처리 (Arrow 데미지 자체로 8.0이 들어가겠지만 확실하게)
        if (e.getDamager() instanceof Arrow arrow && arrow.hasMetadata(KEY_UBW_HOMING)) {
            // [추가] 본인이 본인 칼에 맞는 경우 데미지 무효화 및 칼 소멸
            if (arrow.getShooter() instanceof Player shooter && shooter.equals(e.getEntity())) {
                e.setCancelled(true);
                // 칼(아머스탠드)과 화살 제거
                for (Entity passenger : arrow.getPassengers()) {
                    passenger.remove();
                }
                arrow.remove();
                return;
            }

            // [수정] 데미지 4 고정 (너프)
            e.setDamage(4.0);

            // 아머스탠드 제거
            for (Entity passenger : arrow.getPassengers()) {
                passenger.remove();
            }
        }
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        UUID uuid = p.getUniqueId();
        isChanting.remove(uuid);
        isActive.remove(uuid);
        hasTriggered.remove(uuid);
        p.resetPlayerTime();

        // 대기 중인 검 제거
        List<RisingSword> mySwords = activeSwords.remove(uuid);
        if (mySwords != null) {
            for (RisingSword rs : mySwords) {
                if (rs.stand != null)
                    rs.stand.remove();
            }
            mySwords.clear();
        }

        // 날아가는 검 제거
        List<ArmorStand> myFlying = flyingSwords.remove(uuid);
        if (myFlying != null) {
            for (ArmorStand as : myFlying) {
                if (as != null)
                    as.remove();
            }
            myFlying.clear();
        }
    }

    @Override
    public void reset() {
        super.reset();
        // 전체 초기화 (게임 종료 시) - 모든 맵 클리어
        isChanting.clear();
        isActive.clear();
        hasTriggered.clear();

        for (List<RisingSword> list : activeSwords.values()) {
            for (RisingSword rs : list) {
                if (rs.stand != null)
                    rs.stand.remove();
            }
        }
        activeSwords.clear();

        for (List<ArmorStand> list : flyingSwords.values()) {
            for (ArmorStand as : list) {
                if (as != null)
                    as.remove();
            }
        }
        flyingSwords.clear();
    }
}
