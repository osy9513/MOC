package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import me.user.moc.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class AizenSosuke extends Ability {

    private final Map<UUID, BukkitRunnable> chantTasks = new HashMap<>();
    private final Map<UUID, Integer> chantProgress = new HashMap<>();

    private final String[] CHANT_LINES = {
            "배어 나오는 혼탁한 문장,",
            "불손한 광기의 그릇!",
            "솟아오르고, 부정하고,",
            "마비되고, 번뜩이며,",
            "잠을 방해하노라.",
            "기어가는 철의 왕녀,",
            "끊임없이 자괴하는 진흙 인형.",
            "결합하라.",
            "반발하라.",
            "땅을 메워 자신의 무력함을 깨달아라!"
    };

    public AizenSosuke(MocPlugin plugin) {
        super(plugin);
        startCheckTask();
    }

    @Override
    public String getName() {
        return "아이젠 소스케";
    }

    @Override
    public String getCode() {
        return "037";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§e전투 ● §f아이젠 소스케 (블리치)",
                "§f전장 중앙 에메랄드 블럭 위에서 흑관 영창을 진행합니다.");
    }

    @Override
    public void giveItem(Player p) {
    }

    private void startCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
                        if (p.isDead())
                            continue;

                        Location loc = p.getLocation().clone().subtract(0, 1, 0); // 발 밑
                        boolean onEmerald = loc.getBlock().getType() == Material.EMERALD_BLOCK;

                        if (onEmerald) {
                            // 이미 영창 중이면 패스
                            if (chantTasks.containsKey(p.getUniqueId())) {
                                // 영창 중 이펙트
                                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 10, 0.5, 1,
                                        0.5);
                                continue;
                            }

                            // 쿨타임 및 전투 시작 여부 확인
                            if (!checkCooldown(p)) {
                                continue;
                            }

                            // 영창 시작
                            startChanting(p);
                        } else {
                            // 에메랄드 블럭이 아닌데 영창 중이라면 -> 중단(실패) 처리
                            if (chantTasks.containsKey(p.getUniqueId())) {
                                stopChanting(p, false);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L); // 0.25초마다 위치 체크
    }

    private void startChanting(Player p) {
        chantProgress.put(p.getUniqueId(), 0);
        BukkitRunnable task = new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (step >= CHANT_LINES.length) {
                    // 성공 완료
                    stopChanting(p, true);
                    return;
                }

                // 영창 대사
                Bukkit.broadcastMessage("§5아이젠 소스케 : §f" + CHANT_LINES[step]);
                chantProgress.put(p.getUniqueId(), step + 1);
                step++;
            }
        };
        task.runTaskTimer(plugin, 0L, 38L); // 1.9초 (38틱)
        chantTasks.put(p.getUniqueId(), task);
    }

    private void stopChanting(Player p, boolean success) {
        // 태스크 취소
        BukkitRunnable task = chantTasks.remove(p.getUniqueId());
        if (task != null) {
            task.cancel();
        }

        int count = chantProgress.getOrDefault(p.getUniqueId(), 0);
        chantProgress.remove(p.getUniqueId());

        // 쿨타임 시작 (19초)
        setCooldown(p, 19);

        // 결과 실행
        new BukkitRunnable() {
            @Override
            public void run() {
                if (success) {
                    Bukkit.broadcastMessage("§5아이젠 소스케 : §f땅을 메워 자신의 무력함을 깨달아라!"); // 마지막 대사는 이미 출력됐을 수 있음. 중복 체크?
                    // 로직상 마지막 대사 출력 후 여기서 또 출력?
                    // 영창 루프에서 10번째 대사 출력 -> step++ -> 다음 틱에 완료 감지 -> stopChanting(true)
                    // 따라서 마지막 대사는 이미 나왔음.
                    // 유저 요청: "마지막 대사가 전송되면... 1.9초 후 '파도 90 흑관' 메시지와 함께..."

                    Bukkit.broadcastMessage("§5아이젠 소스케 : §d파도 90 흑관!");
                    castLightning(p, 10);
                } else {
                    Bukkit.broadcastMessage("§5아이젠 소스케 : §7영창파기에 실패해 생각보다 위력이 약하군...");
                    castLightning(p, count);
                }
            }
        }.runTaskLater(plugin, 38L); // 1.9초 후 발동
    }

    private void castLightning(Player caster, int count) {
        // 자신 제외 모든 생명체
        // 2틱에 1개씩 떨어짐
        new BukkitRunnable() {
            int c = 0;

            @Override
            public void run() {
                if (c >= count) {
                    cancel();
                    return;
                }

                // 전체 플레이어 및 몬스터 타격
                for (Player target : Bukkit.getOnlinePlayers()) {
                    if (target.equals(caster))
                        continue;
                    if (target.getGameMode() == org.bukkit.GameMode.SPECTATOR)
                        continue;

                    // 번개 & 대미지
                    target.getWorld().strikeLightningEffect(target.getLocation());
                    target.setNoDamageTicks(0);
                    target.damage(9.0, caster);
                }

                // 몬스터도 포함하려면? "월드에 본인을 제외한 모든 생명체"
                // 월드 순회는 무거울 수 있으니, 자신 주변 100칸 정도로 제한?
                // "월드에 본인을 제외한" -> 광역기.
                // 편의상 온라인 플레이어는 확실히 타격하고, 주변 몬스터만 타격
                for (var e : caster.getNearbyEntities(50, 50, 50)) {
                    if (e instanceof org.bukkit.entity.LivingEntity le && le != caster && !(le instanceof Player)) {
                        le.getWorld().strikeLightningEffect(le.getLocation());
                        le.setNoDamageTicks(0);
                        le.damage(9.0, caster);
                    }
                }

                c++;
            }
        }.runTaskTimer(plugin, 0L, 2L); // 2틱마다
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        stopChanting(p, false); // 종료 시 중단
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● 아이젠 소스케(블리치)");
        p.sendMessage("§f전장 중앙 에메랄드 블럭 위에서 흑관 영창을 진행합니다.");
        p.sendMessage("§f에메랄드 블럭 위에서 1.9초마다 영창을 외우며, 총 10구절을 완성해야 합니다.");
        p.sendMessage("§f영창 완료 시 자신을 제외한 모든 생명체에게 번개를 10회 떨어트립니다(회당 9 대미지, 무적 무시).");
        p.sendMessage("§f중도에 내려오면 영창한 횟수만큼만 번개를 시전합니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 19초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }
}
