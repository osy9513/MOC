package me.user.moc.ability.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile; // [추가]
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent; // [추가]
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.inventory.InventoryClickEvent;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;

/**
 * [능력 코드: 045]
 * 이름: DIO (더 월드)
 * 설명: 시간을 5초간 멈춥니다.
 */
public class DIO extends Ability {

    private final java.util.Set<UUID> timeStoppers = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private final Map<UUID, Integer> damageAccumulation = new HashMap<>();
    private final Map<UUID, Location> frozenEntities = new HashMap<>(); // 위치 고정용
    // [추가] 정지된 투사체와 원래 속도 저장
    private final Map<Projectile, org.bukkit.util.Vector> frozenProjectiles = new HashMap<>();
    // timeStopTask is generic, but now we need per-player task tracking or just
    // rely on Set.
    // Actually we need to track tasks to cancel them if cleanup is called?
    // Ability class automatically tracks 'activeTasks' via registerTask(p, task).
    // So if cleanUp is called, the task is cancelled. But we need to ensure
    // resumeTime is called for THAT player.
    // Wait, if task is cancelled, the 'run' method checking ticks stops. It doesn't
    // auto-call resumeTime.
    // We need cleanup to call resumeTime(p).

    // We need a helper method isTimeStopped() { return !timeStoppers.isEmpty(); }

    public DIO(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "045";
    }

    @Override
    public String getName() {
        return "DIO";
    }

    @Override
    public List<String> getDescription() {
        List<String> list = new ArrayList<>();
        list.add("§c복합 ● DIO(죠죠의 기묘한 모험)");
        list.add("§f시간을 멈춥니다.");
        return list;
    }

    @Override
    public void giveItem(Player p) {
        ItemStack clock = new ItemStack(Material.CLOCK);
        org.bukkit.inventory.meta.ItemMeta meta = clock.getItemMeta();
        meta.setDisplayName("§e§l[ 더 월드 ]");
        meta.setCustomModelData(1); // 리소스팩: dio
        clock.setItemMeta(meta);

        // 시계 지급
        p.getInventory().addItem(clock);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c복합 ● DIO(죠죠의 기묘한 모험)");
        p.sendMessage("§f시간을 멈춥니다.");
        p.sendMessage(" ");
        p.sendMessage("§f더 월드(시계)를 우클릭하면 5초간 시간이 멈춥니다.");
        p.sendMessage("§fDIO를 제외한 모든 플레이어와 엔티티는 움직일 수 없습니다.");
        p.sendMessage("§f멈춘 시간 동안 공격한 데미지는 시간이 다시 흐를 때");
        p.sendMessage("§f한꺼번에 들어갑니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 20초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 더 월드");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        // If this player was stopping time, remove them and potentially resume
        if (timeStoppers.contains(p.getUniqueId())) {
            resumeTime(p);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        if (!AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        if (e.getItem() == null || e.getItem().getType() != Material.CLOCK)
            return;

        // 이미 내가 시간을 멈췄는지 확인
        if (timeStoppers.contains(p.getUniqueId())) {
            p.sendMessage("§c이미 시간을 멈췄습니다.");
            return;
        }

        // *주의*: 다른 사람이 멈췄을 때 내가 쓸 수 있는가?
        // 기획상 "유일하게 플레이어중 DIO만 움직일 수 있습니다" -> DIO는 움직일 수 있음.
        // 그리고 쿨타임이 20초.
        // 내가 멈춘게 아니면 쓸 수 있게 해줘야 함 (중첩 가능).

        if (!checkCooldown(p))
            return;

        stopTime(p);
    }

    private void stopTime(Player caster) {
        boolean wasAlreadyStopped = !timeStoppers.isEmpty();
        timeStoppers.add(caster.getUniqueId());

        damageAccumulation.clear(); // *주의*: 이건 공유됨. 맵은 괜찮지만 로직상 "누구의 공격인가"가 중요.
        // damageAccumulation 키는 TargetUUID. Value는 Hits.
        // 여러 DIO가 때리면 스택이 합쳐짐. 이건 OK (다구리).

        frozenEntities.clear();
        frozenProjectiles.clear(); // [추가] 투사체 맵 초기화

        // 1. 시각/청각 효과 (최초 발동 시에만? 아니면 매번?)
        // 매번 출력해서 임팩트 줌.
        Bukkit.broadcastMessage("§eDIO : §l더 월드! 시간이여 멈춰라!");

        // 월드 전체 효과음 및 시각 효과
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 2.0f, 0.5f);

            // DIO가 아닌 경우 야간 투시 효과 -> timeStoppers에 없는 사람만?
            // 아니면 그냥 Ability 보유자 제외?
            // 기획: "유일하게 플레이어중 DIO만 움직일 수 있습니다"
            // -> DIO 능력이 있는 사람은 다 움직일 수 있어야 함. (Toga 포함)

            if (!AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode())) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 100, 0, true, true, true));
            }
        }

        World world = caster.getWorld();
        // Rain effects removed as per user request
        // world.setStorm(true);
        // world.setThundering(true);

        // 2. 엔티티 정지 로직
        // 이미 루프가 돌고 있다면 또 돌릴 필요가 있는가?
        // -> DIO A가 멈춤 (0~5s). DIO B가 3s에 멈춤 (3~8s).
        // A의 태스크는 5s에 끝남. B의 태스크는 8s에 끝남.
        // A가 끝날 때 resumeTime을 호출하면 B도 풀려버리는 문제 발생했었음.
        // 이제 resumeTime에서 Set을 체크하므로,
        // A 태스크가 끝나도 B가 남아있으면 resumeTime은 효과음만 내고 실제로 풀지 않음?
        // 아니면 "WRYYY"는 완전히 끝났을 때만?

        // 엔티티 고정 루프는 "누군가 멈추고 있는 동안" 계속 돌아야 함.
        // 단순히 각자 태스크를 돌리면 됨. (중복 적용되어도 setVelocity(0)은 매한가지)

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                // 내가 취소했거나(cleanup), 시간이 다 됐으면
                if (!timeStoppers.contains(caster.getUniqueId()) || ticks >= 100) {
                    this.cancel();
                    // 정상 종료(시간 다 됨) 시 resumeTime 호출
                    // cleanup으로 제거된 경우엔 이미 remove되었으므로 resumeTime 호출해도 안전(Set체크)
                    // 하지만 여기서 호출하면 중복 호출 될 수 있음.
                    // 명시적으로: 시간이 다 돼서 끝나는 경우에만 resumeTime.
                    if (ticks >= 100) {
                        resumeTime(caster);
                    }
                    return;
                }

                // 1초마다 째깍 소리 -> 0.1초 단위 표시를 위해 매 틱 계산
                if (ticks % 20 == 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 2.0f);
                    }
                }

                // [추가] 남은 시간 액션바 출력
                double remainSec = (100 - ticks) / 20.0;
                // String formatted = String.format("%.1f", remainSec); // 할당 줄이기 위해 단순 계산 권장하지만
                // 편의상
                String msg = "§e§l[ THE WORLD : " + String.format("%.1f", remainSec) + "s ]";
                caster.sendActionBar(msg);

                // 엔티티 고정 & 투사체 고정
                for (Entity entity : world.getEntities()) {
                    // DIO 제외
                    if (entity instanceof Player) {
                        Player pPlayer = (Player) entity;
                        if (AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(pPlayer, getCode())) {
                            continue;
                        }
                        // [너프] 관전자 제외
                        if (pPlayer.getGameMode() == GameMode.SPECTATOR) {
                            continue;
                        }
                    }

                    // [추가] 투사체 정지
                    if (entity instanceof Projectile) {
                        Projectile proj = (Projectile) entity;
                        if (!frozenProjectiles.containsKey(proj)) {
                            // 처음 발견함 -> 속도 저장
                            frozenProjectiles.put(proj, proj.getVelocity());
                        }
                        // 정지 상태 유지
                        proj.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                        proj.setGravity(false); // 중력 끄기 (공중에 멈춤)
                        continue; // 투사체는 밑에 LivingEntity 로직 탈 필요 없음
                    }

                    if (entity instanceof LivingEntity) {
                        LivingEntity le = (LivingEntity) entity;
                        le.setAI(false); // AI 정지
                        le.setGravity(false); // 중력 정지
                        le.setVelocity(new org.bukkit.util.Vector(0, 0, 0)); // 속도 0

                        // [▼▼▼ 핵심 수정 부분 ▼▼▼]
                        // 1. 해당 엔티티가 처음 감지되었다면, 현재 위치를 '고정 위치'로 저장
                        if (!frozenEntities.containsKey(le.getUniqueId())) {
                            frozenEntities.put(le.getUniqueId(), le.getLocation());
                        }

                        // 2. 저장된 고정 위치로 강제 이동 (텔레포트)
                        // 플레이어의 경우 시야(Yaw, Pitch)는 자유롭게 돌릴 수 있어야 하므로,
                        // 저장된 위치 좌표에 현재 쳐다보는 방향을 덮어씌워서 텔레포트합니다.
                        Location fixedLoc = frozenEntities.get(le.getUniqueId());

                        // 위치는 고정하되, 고개 돌리는 건 허용하려면 아래 로직 사용
                        Location teleportLoc = fixedLoc.clone();
                        teleportLoc.setYaw(le.getLocation().getYaw());
                        teleportLoc.setPitch(le.getLocation().getPitch());

                        le.teleport(teleportLoc);
                        // [▲▲▲ 여기까지 수정됨 ▲▲▲]

                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 5, 255, true, true, true));
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 5, 5, true, true, true));
                        if (le instanceof Player player) {
                            applyJumpSilence(player, 5);
                        }
                    }

                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        registerTask(caster, task); // cleanup 시 자동 cancel을 위해 등록
    }

    private void resumeTime(Player caster) {
        // 내 정지 상태 해제
        timeStoppers.remove(caster.getUniqueId());

        // 쿨타임 적용 (끝나고 적용)
        setCooldown(caster, 20);

        // 아직 다른 DIO가 시간을 멈추고 있다면? 완전히 풀지 않음.
        if (!timeStoppers.isEmpty()) {
            caster.sendMessage("§e시간 정지가 끝났지만, 다른 DIO의 세계가 지속되고 있습니다...");
            return;
        }

        // --- 여기부터는 "진짜로 시간이 흐르기 시작할 때" ---

        // 1. 효과음 및 메시지
        Bukkit.broadcastMessage("§eDIO : §lWRYYYYYYYYYY");

        // Rain effects removal logic simplified
        World mainWorld = Bukkit.getWorlds().get(0);
        // if (mainWorld != null) {
        // mainWorld.setStorm(false);
        // mainWorld.setThundering(false);
        // }

        // [추가] 투사체 복구
        for (Map.Entry<Projectile, org.bukkit.util.Vector> entry : frozenProjectiles.entrySet()) {
            Projectile proj = entry.getKey();
            if (proj.isValid()) {
                proj.setVelocity(entry.getValue()); // 원래 속도 복구
                proj.setGravity(true); // 중력 복구
            }
        }
        frozenProjectiles.clear();

        // 2. 엔티티 복구 및 데미지 정산 (한 번만!)
        for (Entity entity : mainWorld.getEntities()) {
            if (entity instanceof LivingEntity) {
                LivingEntity le = (LivingEntity) entity;
                // AI 복구는 해야 함. (모든 DIO가 멈췄으므로)
                // 근데 원래 AI가 없던 놈(마네킹 등)이면?
                // 뭐 일단 true로 돌려놓는게 안전.
                le.setAI(true);
                le.setGravity(true);

                // 정산 데미지 처리
                if (damageAccumulation.containsKey(le.getUniqueId())) {
                    int hits = damageAccumulation.get(le.getUniqueId());
                    double finalDamage = hits * 2.0; // [수정] 성 성능 너프 (3.0 -> 2.0)

                    // 폭발 연출 태스크
                    new BukkitRunnable() {
                        int count = 0;

                        @Override
                        public void run() {
                            if (count >= hits || le.isDead()) {
                                this.cancel();
                                return;
                            }
                            le.setNoDamageTicks(0);
                            le.damage(2.0); // [수정] 성 성능 너프 (3.0 -> 2.0)

                            // [수정] 피 튀기는 이펙트 & 타격음 변경
                            mainWorld.playSound(le.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 1.0f);
                            mainWorld.playSound(le.getLocation(), Sound.BLOCK_SLIME_BLOCK_BREAK, 1.0f, 1.0f); // 퍽 소리

                            // 붉은색 파티클 (피)
                            mainWorld.spawnParticle(Particle.BLOCK, le.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3,
                                    Material.REDSTONE_BLOCK.createBlockData());

                            count++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L); // [수정] 4L -> 1L (매우 빠르게)
                }
            }
        }
        damageAccumulation.clear();
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent e) {
        // 시간이 멈췄는지 확인 (누구든)
        if (timeStoppers.isEmpty())
            return;

        if (!(e.getDamager() instanceof Player))
            return;
        Player attacker = (Player) e.getDamager();

        // 공격자가 DIO라면?
        if (AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(attacker, getCode())) {
            if (e.getEntity() instanceof LivingEntity) {
                e.setDamage(0);
                e.setCancelled(true);

                attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.0f);
                attacker.spawnParticle(Particle.CRIT, e.getEntity().getLocation().add(0, 1, 0), 5);

                UUID targetId = e.getEntity().getUniqueId();
                int currentStack = damageAccumulation.getOrDefault(targetId, 0);

                // [너프] 무다무다 스택 최대 20제한
                if (currentStack < 20) {
                    damageAccumulation.put(targetId, currentStack + 1);
                    attacker.sendMessage("§e무다무다무다무다무다!!! " + (currentStack + 1) + "회 타격!");
                } else {
                    attacker.sendMessage("§e무다무다무다무다무다!!! (최대 스택 도달)");
                }
            }
        } else {
            // DIO가 아닌데 공격했다면? (화살 등)
            e.setCancelled(true);
        }
    }

    // [추가] 투사체 발사 시 즉시 정지 (발사되자마자 멈춤)
    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (timeStoppers.isEmpty())
            return;

        Projectile proj = e.getEntity();
        if (proj.getShooter() instanceof Player) {
            Player shooter = (Player) proj.getShooter();
            // DIO가 쏜거면 안 멈춤?
            if (AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(shooter, getCode())) {
                return;
            }
        }

        // 일단 속도가 설정되기 전일 수 있으므로 1틱 뒤에 처리하거나, 여기서 즉시 처리
        // 보통 LaunchEvent 때는 Velocity가 있음.
        frozenProjectiles.put(proj, proj.getVelocity());
        proj.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        proj.setGravity(false);
    }

    // [추가] 정지된 시간 속에서 물/용암 흐름 방지
    @EventHandler
    public void onBlockFromTo(org.bukkit.event.block.BlockFromToEvent e) {
        if (!timeStoppers.isEmpty()) {
            Material type = e.getBlock().getType();
            if (type == Material.WATER || type == Material.LAVA) {
                e.setCancelled(true);
            }
        }
    }

    // === [입력 차단 로직] ===
    // 타임 스톱 중일 때, DIO가 아닌 플레이어의 모든 조작을 차단합니다.

    private boolean isFrozen(Player p) {
        // 시간이 멈추지 않았다면 패스
        if (timeStoppers.isEmpty())
            return false;

        // [너프] 관전자는 멈추지 않음
        if (p.getGameMode() == GameMode.SPECTATOR)
            return false;

        // DIO 능력을 가진 플레이어는 면역
        return !AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode());
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (isFrozen(e.getPlayer()))
            e.setCancelled(true);
    }

    @EventHandler
    public void onSprint(PlayerToggleSprintEvent e) {
        if (isFrozen(e.getPlayer()))
            e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isFrozen(e.getPlayer()))
            e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (isFrozen(e.getPlayer()))
            e.setCancelled(true);
    }

    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent e) {
        if (isFrozen(e.getPlayer()))
            e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && isFrozen(p)) {
            e.setCancelled(true);
        }
    }

    // [중요] Interact는 기존 onInteract와 별개로 차단 로직 필요
    // Priority를 낮게 설정하여 먼저 차단해버리거나, 별도 핸들러 사용
    @EventHandler
    public void onInteractBlock(PlayerInteractEvent e) {
        if (isFrozen(e.getPlayer())) {
            e.setCancelled(true);
        }
    }
}
