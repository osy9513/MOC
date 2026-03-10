package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Arrays;
import java.util.List;

public class SangYoung extends Ability {

    public SangYoung(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "H09";
    }

    @Override
    public String getName() {
        return "상영";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§d히든 ● 상영(바집소)",
                "§f갈필드ㄱ?");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§d히든 ● 상영(바집소)");
        p.sendMessage("§f상영이가 죽을 때 까지 3초마다 모든 생명체 머리 위에");
        p.sendMessage("§f모루가 떨어짐.");
        p.sendMessage("§f해당 떨어지는 모루는 유리를 부수고 떨어짐.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 3초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    // [버그 수정] 상영 전용 강력한 스케줄러 관리 (중복 실행 원천 차단)
    private static final java.util.Map<java.util.UUID, org.bukkit.scheduler.BukkitTask> sangYoungTasks = new java.util.HashMap<>();

    @Override
    public void giveItem(Player p) {
        detailCheck(p);

        // 기존 실행 중인 스케줄러가 있다면 안전하게 100% 무조건 종료
        super.cleanup(p);
        cancelTaskSecurely(p.getUniqueId());

        // 라운드 시작 시 메세지 출력
        plugin.getServer().broadcastMessage("§f상영: §l위험한 절굿공이!!!");

        // 3초(60틱)마다 모루 떨어지는 스케줄러 실행
        BukkitRunnable anvilTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 상영이가 죽었거나 오프라인이거나 능력이 해제되었으면 종료
                if (p == null || !p.isOnline() || p.isDead() || !hasAbility(p)) {
                    this.cancel();
                    sangYoungTasks.remove(p.getUniqueId());
                    return;
                }

                // 게임이 시작되었거나(라운드 진행 중) 크리에이티브 모드일 때만 발동
                boolean isGameStarted = me.user.moc.game.GameManager.getInstance((MocPlugin) plugin).isBattleStarted();
                if (!isGameStarted && p.getGameMode() != GameMode.CREATIVE) {
                    return;
                }

                World world = p.getWorld();
                // 맵 내의 모든 살아있는 생명체 검색
                for (LivingEntity target : world.getLivingEntities()) {
                    // 관전자는 제외
                    if (target instanceof Player targetPlayer) {
                        if (targetPlayer.getGameMode() == GameMode.SPECTATOR) {
                            continue;
                        }
                    }
                    // MOC 플러그인 등에서 사용하는 투명 아머스탠드/마네킹(NPC) 제외 (모루 중복 투하 버그 원인)
                    if (target.getType() == org.bukkit.entity.EntityType.ARMOR_STAND)
                        continue;
                    if (target.getType().name().equals("MANNEQUIN"))
                        continue;
                    // 능력을 발동한 본인(상영)에게도 떨어집니다 (요청 사항: "본인을 포함한")

                    Location dropLoc = target.getLocation().clone();

                    // 에메랄드 블록 기준 Y+30에서 떨어짐
                    int emeraldY = dropLoc.getBlockY(); // 기본적으로는 현재 높이
                    for (int y = dropLoc.getBlockY(); y >= world.getMinHeight(); y--) {
                        if (world.getBlockAt(dropLoc.getBlockX(), y, dropLoc.getBlockZ())
                                .getType() == Material.EMERALD_BLOCK) {
                            emeraldY = y;
                            break;
                        }
                    }
                    dropLoc.setY(emeraldY + 30);

                    // FallingBlock 모루 생성
                    FallingBlock anvil = world.spawnFallingBlock(dropLoc, Material.ANVIL.createBlockData());

                    // 낙하 데미지 적용 및 킬 연동("MOC_LastKiller")
                    anvil.setHurtEntities(true);
                    anvil.setDamagePerBlock(2.0f); // 모루 기본 데미지 배율 설정
                    anvil.setMaxDamage(40);
                    anvil.setMetadata("MOC_LastKiller", new FixedMetadataValue(plugin, p.getUniqueId().toString()));

                    // 유리를 깨뜨리는 로직을 위해 해당 모루 엔티티 추적 스케줄러
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!anvil.isValid() || anvil.isDead() || anvil.isOnGround()) {
                                this.cancel();
                                return;
                            }

                            // 떨어지는 모루의 바로 아래 블록이 유리 관련 블록이면 파괴
                            Location checkLoc = anvil.getLocation().clone().subtract(0, 1, 0);
                            Material type = checkLoc.getBlock().getType();

                            if (type == Material.GLASS || type.name().endsWith("_STAINED_GLASS")
                                    || type == Material.GLASS_PANE || type.name().endsWith("_STAINED_GLASS_PANE")) {
                                // 유리 파괴 소리 추가
                                checkLoc.getWorld().playSound(checkLoc, org.bukkit.Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
                                checkLoc.getBlock().breakNaturally();
                            }
                        }
                    }.runTaskTimer(plugin, 1L, 1L); // 1틱마다 검사
                }
            }
        };

        // 전용 추적 맵에 저장
        sangYoungTasks.put(p.getUniqueId(), anvilTask.runTaskTimer(plugin, 60L, 60L));
        // 혹시 모를 누락을 위해 기존 activeTasks에도 넣긴 합니다
        activeTasks.computeIfAbsent(p.getUniqueId(), k -> new java.util.ArrayList<>())
                .add(sangYoungTasks.get(p.getUniqueId()));
    }

    private void cancelTaskSecurely(java.util.UUID uuid) {
        org.bukkit.scheduler.BukkitTask oldTask = sangYoungTasks.remove(uuid);
        if (oldTask != null && !oldTask.isCancelled()) {
            oldTask.cancel();
        }
    }

    /**
     * 해당 플레이어가 이 능력을 소유하고 있는지 확인하는 메서드
     */
    private boolean hasAbility(Player p) {
        return AbilityManager.getInstance().hasAbility(p, getCode());
    }

    /**
     * 라운드 종료 및 능력 해제 시 호출되는 정리 메서드
     */
    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        if (p != null) {
            cancelTaskSecurely(p.getUniqueId());
        }
    }

    /**
     * [버그 수정] 전역 초기화(GameManager 라운드 종료 시) 호출
     */
    @Override
    public void reset() {
        super.reset();
        for (org.bukkit.scheduler.BukkitTask t : sangYoungTasks.values()) {
            if (t != null && !t.isCancelled()) {
                t.cancel();
            }
        }
        sangYoungTasks.clear();
    }
}
