package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Lurker extends Ability {

    // 플레이어의 잠복 상태 관리 및 스니킹 트리거 관리
    private final Map<UUID, Boolean> burrowedState = new HashMap<>();
    private final Map<UUID, Long> lastSneakTime = new HashMap<>();

    public Lurker(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "080";
    }

    @Override
    public String getName() {
        return "럴커";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c전투 ● 럴커(스타크래프트)",
                "§f잠복하여 공격합니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 럴커(스타크래프트)");
        p.sendMessage("§f쉬프트 연속 두 번 시 잠복합니다.");
        p.sendMessage("§f잠복 시 은신 상태가 되고 공격 및 블럭 파괴가 안 되며 점프할 수 없습니다.");
        p.sendMessage("§f잠복 중 럴커 주위 21*21 블럭 범위 내에 생명체가 있을 경우");
        p.sendMessage("§f1.25초당 한 번 씩 바닥을 통해 9 데미지의 공격을 합니다.");
        p.sendMessage("§f잠복 중 쉬프트 연속 두 번 시 잠복을 해제하며 쿨타임이 돕니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 5초.");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음.");
        p.sendMessage("§f장비 제거 : 없음.");
    }

    @Override
    public void giveItem(Player p) {
        detailCheck(p);
        burrowedState.put(p.getUniqueId(), false);
        lastSneakTime.put(p.getUniqueId(), 0L);
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        burrowedState.remove(p.getUniqueId());
        lastSneakTime.remove(p.getUniqueId());
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        // [추가] 잠복 중 이동 차단용 이속 감소 효과도 같이 제거
        p.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    @Override
    public void onGameEnd(Player p) {
        super.onGameEnd(p);
        // 게임 종료 시에도 확실히 잠복을 풀어주고 이펙트 해제
        if (isBurrowed(p)) {
            unburrow(p, false); // 쿨타임 주기엔 늦었으니 false
        }
    }

    private boolean isBurrowed(Player p) {
        return burrowedState.getOrDefault(p.getUniqueId(), false);
    }

    // 잠복 중 공격 취소 (기획안 요구사항)
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p))
            return;
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        // 잠복 상태일 때는 평타 공격(EntityDamageByEntity) 불가
        if (isBurrowed(p)) {
            e.setCancelled(true);
            p.sendActionBar(net.kyori.adventure.text.Component.text("§c잠복 중에는 공격할 수 없습니다!"));
        }
    }

    // 소환사의 송곳니가 넣는 데미지 재조정 (9 고정) 및 킬러 어트리뷰션
    @EventHandler
    public void onFangsDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof EvokerFangs fangs))
            return;
        if (!(e.getEntity() instanceof LivingEntity target))
            return;

        // 주인이 럴커인지 확인
        if (fangs.hasMetadata("LurkerOwner")) {
            String ownerUuid = fangs.getMetadata("LurkerOwner").get(0).asString();
            
            // 데미지 9 적용을 위해 e.setDamage(9) 호출
            e.setDamage(9.0);
            
            // 킬귀속 (MOC_LastKiller)
            target.setMetadata("MOC_LastKiller", new FixedMetadataValue(plugin, ownerUuid));
        }
    }

    // 잠복 중 블럭 파괴 취소 (기획안 요구사항)
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        // 잠복 상태일 때는 블록 파괴 불가
        if (isBurrowed(p)) {
            e.setCancelled(true);
        }
    }

    // 쉬프트 2번 입력 시 잠복/해제 토글
    @EventHandler
    public void onSneakToggle(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        // Sneak 이벤트는 누를때(true), 뗄때(false) 일어난다. 누르는 순간(true)만 캐치
        if (!e.isSneaking()) return;

        // 이미 잠복 상태인지 체크
        boolean burrowed = isBurrowed(p);

        long now = System.currentTimeMillis();
        long last = lastSneakTime.getOrDefault(p.getUniqueId(), 0L);

        // 연타 제한: 400ms(0.4초) 이내에 다시 누르면 더블 쉬프트로 인정
        if (now - last < 400) {
            // 연타 검출
            lastSneakTime.put(p.getUniqueId(), 0L); // 타이머 초기화 (안그러면 1초후 눌러도 3연타 버그됨)
            
            if (isSilenced(p)) {
                if(burrowed) {
                    // 고죠 등으로 침묵 걸려있으면 잠복 푸는건 가능하나(사실 위협 검사에 의해 풀리겠지만) 못들어가게 막음
                    unburrow(p, true);
                }
                return;
            }

            if (burrowed) {
                // 이미 잠복중이라면 해제하고 쿨타임 시작
                unburrow(p, true);
            } else {
                // 잠복 상태가 아니면, 쿨타임 체크 (잠복 돌입 시에는 쿨타임 검사를 함. 잠복이 끝나야 쿨이 돔)
                if(!checkCooldown(p)) return;
                
                // 잠복 돌입
                burrow(p);
            }
        } else {
            // 이번 누름을 첫 번째 누름으로 저장
            lastSneakTime.put(p.getUniqueId(), now);
        }
    }

    // 잠복 돌입
    private void burrow(Player p) {
        p.sendMessage("§e[럴커] §f잠복 상태에 돌입했습니다.");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_TURTLE_EGG_BREAK, 1f, 0.5f); // 껍질 파뭍히는 소리
        
        burrowedState.put(p.getUniqueId(), true);
        
        // 은신 부여
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));

        // [고도화] 이동 차단 - Slowness 255(최대 레벨) 무한 부여
        // 마인크래프트에서 Slowness 255는 이동 속도를 사실상 0으로 만들어 움직일 수 없게 합니다.
        // 잠복 중 이동이 가능했던 문제를 이 방법으로 해결합니다.
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 254, false, false));
        p.sendActionBar(net.kyori.adventure.text.Component.text("§2잠복 상태 - 이동 불가 / 공격 불가"));

        // 잠복 및 공격 스케줄러 등록
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                // 온라인 체크, 능력 소유 체크 등 방어
                if (!p.isOnline() || !AbilityManager.getInstance().hasAbility(p, getCode())) {
                    this.cancel();
                    burrowedState.put(p.getUniqueId(), false);
                    return;
                }

                // 침묵 상태 체크 (고죠 등에 당하면 강제 해제)
                if (isSilenced(p)) {
                    // 해제 시 쿨타임 5초 진입
                    unburrow(p, true);
                    this.cancel();
                    return;
                }

                // 잠복이 해제되었으면 취소
                if (!isBurrowed(p)) {
                    this.cancel();
                    return;
                }

                // 살아있으면 지속적으로 점프 락 갱신(applyJumpSilence 요구사항 - 약 1.5초 락)
                // 이 태스크가 25틱(1.25초) 반복하므로 30틱 봉인이면 무한유지
                applyJumpSilence(p, 30L);

                // --- 주변 탐색 & 럴커 공격 ---
                LivingEntity target = findNearestTarget(p);
                if (target != null) {
                    // 타겟이 존재하면 푸슉푸슉 메세지와 공격 전개
                    Bukkit.broadcastMessage("§c럴커 : §f푸슉푸슉");
                    executeLurkerAttack(p, target);
                }
            }
        }.runTaskTimer(plugin, 0L, 25L); // 25틱 (1.25초당 한번)

        registerTask(p, task);
    }

    // 잠복 해제
    private void unburrow(Player p, boolean giveCooldown) {
        burrowedState.put(p.getUniqueId(), false);
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        // [고도화] 이동 차단 해제 - 잠복 해제 시 Slowness 제거
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        
        p.sendMessage("§e[럴커] §f잠복이 해제되었습니다.");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_TURTLE_EGG_BREAK, 1f, 1.2f); // 흙 튀기는 소리

        // 진행중인 버로우 테스크들을 모두 종료 (부모 능력쪽에서 처리하면 전부 죽으니 일단 개별 종료를 하고싶으나
        // activeTasks는 전체 캔슬이 편의상 있다. 어짜피 이 스킬은 테스크가 버로우시 켜는 루프 1개뿐이다.
        // 그러므로 activeTasks에 들어있던 본인 태스크들 루프를 날린다.
        if (activeTasks.containsKey(p.getUniqueId())) {
            for (BukkitTask t : activeTasks.get(p.getUniqueId())) {
                if (t != null && !t.isCancelled()) {
                    t.cancel();
                }
            }
            activeTasks.get(p.getUniqueId()).clear();
        }

        // 잠복 해제 시 쿨타임 시작 (기획안 요구사항)
        if (giveCooldown) {
            setCooldown(p, 5.0); // 5초
        }
    }

    // 21*21 내에서 가장 가까운 LivingEntity 적(관전자 제외)을 찾습니다.
    private LivingEntity findNearestTarget(Player p) {
        // 10.5 반경이란건 기준점에서 상하좌우 10.5칸. 즉 지름 21칸 (21x21 범위)이다.
        List<org.bukkit.entity.Entity> nearby = p.getNearbyEntities(10.5, 10.5, 10.5);
        LivingEntity closest = null;
        double minDistanceSq = Double.MAX_VALUE;

        for (org.bukkit.entity.Entity e : nearby) {
            if (!(e instanceof LivingEntity le)) continue;
            
            // 시체(죽은거) 제외
            if (le.isDead()) continue;
            
            // 관전자 (SPECTATOR) 예외 처리 최우선 지침
            if (le instanceof Player targetPlayer) {
                if (targetPlayer.getGameMode() == GameMode.SPECTATOR) continue;
            }

            double distSq = le.getLocation().distanceSquared(p.getLocation());
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq;
                closest = le;
            }
        }
        return closest;
    }

    // 타겟을 향해 소환사의 송곳니 라인을 발사합니다.
    private void executeLurkerAttack(Player p, LivingEntity target) {
        Location startLoc = p.getLocation();
        Location targetLoc = target.getLocation();
        
        // 방향 벡터 구하기
        Vector dir = targetLoc.toVector().subtract(startLoc.toVector());
        // 만약 둘이 완벽히 겹쳤거나 하면 방향을 정할 수 없으므로 무시하거나 랜덤 처리
        if (dir.lengthSquared() == 0) {
            dir = p.getLocation().getDirection(); // 바라도는 방향으로 돌린다
        }
        dir.setY(0).normalize(); // 지상 타겟팅용: 높이 차이를 평탄화하고 정규화

        // 소환사의 송곳니 생성 지점 간격 & 길이 설정 (최대거리 10.5 블럭 쭈욱)
        double maxDistance = 10.5;
        double step = 1.25; // 약 1.25 블럭마다 송곳니 솟게 함

        // 한 번에 모든 이보스 팡을 쏘면 이펙트가 너무 동시에 터짐. 지연을 두고 1열로 나아가게 함.
        for (double d = step; d <= maxDistance; d += step) {
            Vector offset = dir.clone().multiply(d);
            Location spawnLoc = startLoc.clone().add(offset);
            
            // 땅위에 제대로 서기 위해 Y좌표를 보정
            // 위아래로 블록 검색 (원래 위치에서 +1 시작, 밑으로 -3 까지 체크)
            double topY = startLoc.getY() + 1;
            boolean foundGround = false;
            
            for (double yOffset = 1; yOffset >= -3; yOffset -= 1) {
                spawnLoc.setY(startLoc.getY() + yOffset);
                if (spawnLoc.getBlock().getType().isSolid()) {
                    // 블럭 상단에 딱 붙임
                    spawnLoc.setY(spawnLoc.getBlockY() + 1.0);
                    foundGround = true;
                    break;
                }
            }

            if (!foundGround) {
                // 검색 범위를 벗어나 너무 파였거나 너무 위면 적당히 p 높이에 생성
                spawnLoc.setY(startLoc.getY());
            }

            // 거리 비례해서 딜레이 부여: 1당 1틱 정도 (0, 1, 2... 순서)
            long delayTicks = (long)(d / step); 
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    // 만약 플레이어가 중간에 아프거나 잠복 해제했을때 멈출까?
                    // (럴커 컨셉상 이미 침 나간건 끝까지 나가는걸로 진행)
                    EvokerFangs fangs = p.getWorld().spawn(spawnLoc, EvokerFangs.class);
                    // 킬 귀속과 데미지를 위해 메타데이터 삽입
                    fangs.setMetadata("LurkerOwner", new FixedMetadataValue(plugin, p.getUniqueId().toString()));
                }
            }.runTaskLater(plugin, delayTicks);
        }
    }
}
