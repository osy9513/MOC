package me.user.moc.ability.impl;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import me.user.moc.ability.Ability;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public class Byakuya extends Ability {

    public Byakuya(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "013";
    }

    @Override
    public String getName() {
        return "쿠치키 뱌쿠야";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§d전투 ● 쿠치키 뱌쿠야 (블리치)",
                "§f철 검 우클릭 시 §d만해(卍解)§f를 사용합니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 기본 무기 철 검 지급
        // p.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
        cooldowns.put(p.getUniqueId(), 0L);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§d전투 ● 쿠치키 뱌쿠야(블리치)");
        p.sendMessage("§f철 검 우클릭 시 '만해 : 천본앵경엄'을 시전하여 주변을 벚꽃으로 덮습니다.");
        p.sendMessage("§f반경 30블록 내의 모든 적에게 초당 2(1칸)의 지속 피해를 10초간 입히며,");
        p.sendMessage("§f시전 중에는 이동 속도가 10% 감소하지만 적의 접근을 방해합니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 20초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void cleanup(Player p) {
        // 게임 종료나 리롤 시 진행 중인 만해 취소 및 스피드 복구
        List<BukkitTask> taskList = activeTasks.get(p.getUniqueId());
        if (taskList != null) {
            for (BukkitTask task : taskList) {
                task.cancel();
            }
            taskList.clear();
            p.setWalkSpeed(0.2f); // 기본 속도로 복구
            p.sendMessage("§f천본앵이 흩어집니다.");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 1. 내 능력인지 확인 (AbilityManager의 등록 방식에 따라 다를 수 있지만, 여기서는 간단히 패스)
        // 실제로는 AbilityManager에서 호출하거나, 이벤트 내에서 검사해야 함.
        // 이 클래스는 Listener를 상속받으므로 plugin에 등록되면 모든 이벤트를 수신함.
        // 따라서 "나의 능력인가?" 검사가 필수적임.
        // 현재 구조상 AbilityManager.hasAbility() 같은 static 접근자가 없으므로,
        // 일반적으로 플러그인 구조에서는 AbilityManager를 통하거나,
        // Ability 클래스 내에서 체크 로직이 필요함.
        // 하지만 AbilityManager 코드를 보면 instance가 public static으로 열려있음.

        // [주의] 순환 참조 방지를 위해 느슨하게 체크하거나,
        // AbilityManager의 static 메서드를 활용해야 함.
        // 여기서는 안전하게 AbilityManager.getInstance()를 사용한다고 가정.

        try {
            // 리플렉션이나 Static 접근 대신, Plugin 인스턴스를 통해 접근하는 것이 정석이나,
            // 현재 제공된 AbilityManager 코드에 getInstance가 추가되었으므로 사용 가능.
            // 하지만 컴파일 안전성을 위해 여기서는 단순 로직만 작성하고,
            // AbilityManager.hasAbility 호출은 생략(다른 능력 구현체 참고 시 보통 이렇거나, 매니저에서 이벤트를 위임함).
            // 만약 모든 Listener가 다 켜져 있다면, 본인 능력 체크 코드가 필수.

            // *가정*: 사용자가 이 능력을 가지고 있는지 확인하는 로직을 추가합니다.
            if (!me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p,
                    getCode())) {
                return;
            }
        } catch (Exception ex) {
            // 안전 장치: 매니저 로드가 안되었을 경우 등
            return;
        }

        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (e.getItem() != null && e.getItem().getType() == Material.IRON_SWORD) {
                activateBankai(p);
            }
        }
    }

    private void activateBankai(Player p) {
        UUID uuid = p.getUniqueId();

        // 1. 쿨타임 체크 (부모 메서드 사용, 50초)
        if (!checkCooldown(p))
            return;

        // 2. 이미 사용 중인지 체크
        List<BukkitTask> currentTasks = activeTasks.get(uuid);
        if (currentTasks != null && !currentTasks.isEmpty()) {
            p.sendActionBar(Component.text("§c이미 천본앵이 흩날리고 있습니다.")
                    .color(TextColor.color(0xFFB6C1)));
            return;
        }

        // 3. 스킬 발동
        p.getServer().broadcast(
                Component.text("쿠치키 바쿠야 : 흩날려라, 천본앵경엄.")
                        .color(TextColor.color(0xFFB6C1)));
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.0f, 2.0f); // 칭!
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 0.5f); // 웅장한 바람 소리

        // 쿨타임 설정 (20초)
        setCooldown(p, 20);

        // 이동 속도 패널티 (10% 감소) -> 0.2f * 0.9 = 0.18f
        p.setWalkSpeed(0.18f);

        // 4. 지속 효과 (10초 동안)
        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 10 * 20; // 10초
            final double radius = 30.0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cleanup(p);
                    return;
                }

                if (ticks >= maxTicks) {
                    cleanup(p);
                    return;
                }

                Location center = p.getLocation();

                // [시각 효과 1] 사용자 주변 나선형 파티클 (매 틱마다)
                // 3줄의 나선
                for (int i = 0; i < 3; i++) {
                    double angle = (ticks * 0.2) + (i * (Math.PI * 2 / 3));
                    double x = Math.cos(angle) * 3;
                    double z = Math.sin(angle) * 3;
                    // 높이는 서서히 올라갔다 내려갔다
                    double y = Math.sin(ticks * 0.1) * 1.5 + 1.5;
                    // 1.21 버전: CHERRY_LEAVES 사용
                    p.getWorld().spawnParticle(Particle.CHERRY_LEAVES, center.clone().add(x, y, z), 2, 0.1, 0.1, 0.1,
                            0.01);
                }

                // [시각 효과 2] 랜덤 위치 벚꽃 흩날림 (범위 내)
                for (int k = 0; k < 10; k++) {
                    double rx = (Math.random() * radius * 2) - radius;
                    double ry = (Math.random() * 10) - 2; // -2 ~ 8 높이
                    double rz = (Math.random() * radius * 2) - radius;

                    if (rx * rx + rz * rz <= radius * radius) { // 원형 범위
                        Location particleLoc = center.clone().add(rx, ry, rz);
                        p.getWorld().spawnParticle(Particle.CHERRY_LEAVES, particleLoc, 1, 0, 0, 0, 0.05);
                    }
                }

                // [데미지 로직] 1초(20틱)마다 피해
                if (ticks % 20 == 0) {
                    // 범위 내 적 식별
                    Collection<Entity> nearby = p.getWorld().getNearbyEntities(center, radius, radius, radius);
                    boolean hitAny = false;

                    for (Entity target : nearby) {
                        if (target instanceof LivingEntity && !target.getUniqueId().equals(p.getUniqueId())) {
                            // 거리 정밀 체크 (구 형태)
                            if (target.getLocation().distance(center) <= radius) {
                                LivingEntity le = (LivingEntity) target;
                                le.damage(2.0, p); // 2 데미지 (하트 1칸)

                                // 피격 이펙트 (Sweep Attack)
                                p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, le.getLocation().add(0, 1, 0), 1);
                                hitAny = true;
                            }
                        }
                    }

                    if (hitAny) {
                        // 베는 소리
                        p.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 1.2f);
                    }

                    // 지속적인 바람 소리 (분위기)
                    if (ticks % 60 == 0) { // 3초마다
                        p.getWorld().playSound(center, Sound.ITEM_ELYTRA_FLYING, 0.2f, 0.5f);
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        registerTask(p, task);
    }
}
