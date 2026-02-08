package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * [능력 코드: 024]
 * 이름: Kaneki Ken (카네키 켄)
 * 설명: 구울. 썩은 고기만 먹을 수 있으며 배고픔이 낮으면 카구네가 발현되어 전투력이 상승함.
 */
public class Kaneki extends Ability {

    // 유언 메시지 중복 방지용
    private final Set<UUID> starvingPlayers = new HashSet<>();

    public Kaneki(JavaPlugin plugin) {
        super(plugin);
        startTickTask();
    }

    @Override
    public String getCode() {
        return "024";
    }

    @Override
    public String getName() {
        return "카네키 켄";
    }

    @Override
    public List<String> getDescription() {
        List<String> list = new ArrayList<>();
        list.add("§c전투 ● 카네키 켄(도쿄 구울)");
        list.add("§f구울이 됩니다.");
        return list;
    }

    @Override
    public void giveItem(Player p) {
        // 1. 인벤토리 초기화
        p.getInventory().clear();

        // 2. 썩은 고기 100개 지급 (64 + 36)
        ItemStack flesh = new ItemStack(Material.ROTTEN_FLESH, 36);
        org.bukkit.inventory.meta.ItemMeta meta = flesh.getItemMeta();
        if (meta != null) {
            meta.setLore(Arrays.asList("§7구울의 주식입니다.", "§7이것으로만 배고픔을 채울 수 있습니다."));
            flesh.setItemMeta(meta);
        }
        p.getInventory().addItem(flesh); // 36개
        flesh.setAmount(64);
        p.getInventory().addItem(flesh); // 64개

        // 3. 기본 버프 (재생 5, 허기 11)
        // 재생 5 (Amplifier 4)
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 4, false, false));
        // 허기 20 (Amplifier 0) - 시각 효과용
        p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, PotionEffect.INFINITE_DURATION, 19, false, false));

        // 4. 메시지
        Bukkit.broadcastMessage("§c카네키 켄 : 나는 '구울'이다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 카네키 켄(도쿄 구울)");
        p.sendMessage("§f상시 체력 재생 및 상시 허기가 걸려있습니다.");
        p.sendMessage("§f썩은 고기로만 배고픔을 채울 수 있습니다.");
        p.sendMessage("§f배고픔이 5칸(10) 이하면 폭주하여 힘 3, 신속 3 버프를 얻습니다.");
        p.sendMessage("§f배고픔이 0이 되면 매 순간 고통을 받으며 죽어갑니다.");
        p.sendMessage("§f폭주 시 카구네가 활성화 되며, 좌클릭 시 카구네로 상대를 끌고 옵니다 (사거리 11블럭).");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 3초 (카구네)");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 썩은 고기 100개");
        p.sendMessage("§f장비 제거 : 철 칼, 철 갑옷, 구운 소고기, 체력 재생 포션");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        // 버프 해제
        p.removePotionEffect(PotionEffectType.REGENERATION);
        p.removePotionEffect(PotionEffectType.HUNGER);
        p.removePotionEffect(PotionEffectType.SPEED);
        p.removePotionEffect(PotionEffectType.STRENGTH);

        starvingPlayers.remove(p.getUniqueId());

        WorldBorder border = p.getWorld().getWorldBorder();
        if (p.isOnline()) {
            // 보더 관련 로직 생략 (안전성)
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        // AbilityManager를 사용하여 확실하게 능력 확인
        boolean isKaneki = me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p,
                getCode());

        if (!isKaneki)
            return;

        if (e.getItem().getType() != Material.ROTTEN_FLESH) {
            if (e.getItem().getType().isEdible()) {
                e.setCancelled(true);
                p.sendMessage("§c[MOC] 구울은 썩은 고기만 먹을 수 있습니다.");
            }
        }
    }

    /**
     * 카구네 발사 (좌클릭)
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK)
            return;

        if (p.getFoodLevel() > 10)
            return;

        // AbilityManager를 사용하여 확실하게 능력 확인
        if (!me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        if (!checkCooldown(p))
            return;

        shootKagune(p);
        setCooldown(p, 3);
    }

    private void shootKagune(Player p) {
        Location start = p.getEyeLocation();
        Vector dir = start.getDirection().normalize();

        double range = 11.0;

        for (double i = 0; i < range; i += 0.5) {
            Location point = start.clone().add(dir.clone().multiply(i));
            p.getWorld().spawnParticle(Particle.DUST, point, 1,
                    new Particle.DustOptions(Color.RED, 1.5f));
        }

        Entity target = null;
        var trace = p.getWorld().rayTraceEntities(start, dir, range, 0.5, e -> e != p && e instanceof LivingEntity);
        if (trace != null && trace.getHitEntity() != null) {
            target = trace.getHitEntity();
        }

        if (target != null) {
            Vector pull = p.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
            target.setVelocity(pull.multiply(1.5).setY(0.3));

            p.sendMessage("§c카구네로 대상을 포착했습니다!");
            p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.5f);
            ((LivingEntity) target).damage(1.0, p);
        } else {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f);
        }
    }

    private void startTickTask() {
        new BukkitRunnable() {
            private int tickCount = 0;

            @Override
            public void run() {
                // 2틱마다 실행 (0.1초)
                tickCount += 2;

                // 50틱(5초) -> 2틱씩 25번 호출이 아니라, tickCount += 2 이므로
                // tickCount 100을 맞추려면 50번 실행되어야 함.
                boolean drainHunger = (tickCount % 100 == 0);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    // AbilityManager를 사용하여 확실하게 능력 확인
                    if (!me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p,
                            getCode())) {
                        continue;
                    }

                    // 카네키 켄이라면 재생 버프 유지 (없을 경우 다시 부여)
                    if (p.getLocation().getY() >= -64) {
                        PotionEffect regen = p.getPotionEffect(PotionEffectType.REGENERATION);
                        if (regen == null || regen.getAmplifier() != 4) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                                    PotionEffect.INFINITE_DURATION, 4, false, false));
                        }
                    } else {
                        // 공허에서는 버프 제거
                        p.removePotionEffect(PotionEffectType.REGENERATION);
                    }

                    int food = p.getFoodLevel();

                    // 1. 배고픔 강제 감소 (5초마다 1칸씩)
                    if (drainHunger && food > 0) {
                        p.setFoodLevel(Math.max(0, food - 1));
                    }

                    // 2. 사망 위기 (배고픔 0) -> 지속 데미지
                    if (p.getFoodLevel() <= 0) {
                        // 유언 메시지
                        if (!starvingPlayers.contains(p.getUniqueId())) {
                            Bukkit.broadcastMessage("§c카네키 켄 : 커피라도 마실 걸 그랬나..");
                            starvingPlayers.add(p.getUniqueId());
                        }

                        // 무적 무시 1 데미지 (True Damage)
                        if (p.getHealth() > 0) {
                            p.setNoDamageTicks(0);
                            p.damage(10.0); // 방어력 적용됨. 완전 고정 데미지 원하면 health 직접 깎아야 함.
                            // 기획: "1틱당 무적 무시 1데미지 들어가게 해줘"
                            // -> 방어력은 뚫으라는 말은 없었지만, 보통 이런 건 고정댐.
                            // 일단 일반 damage(1.0) 호출 (방어구엔 막힘).
                            // *사용자가 '무적 무시'라고만 했으므로 NoDamageTicks=0가 핵심.*
                        }
                    } else {
                        // 배고픔 회복 시 메시지 플래그 초기화
                        starvingPlayers.remove(p.getUniqueId());
                    }

                    // 3. 폭주 모드
                    if (p.getFoodLevel() <= 10) {
                        // 지속시간도 짧게 갱신 (2틱이므로 40틱(2초)면 충분)
                        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 2, false, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2, false, false, false));
                        drawKaguneWings(p);
                        p.sendActionBar(net.kyori.adventure.text.Component.text("§4§l[ 폭주 상태 ]"));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L); // 0.1초마다 실행
    }

    private void drawKaguneWings(Player p) {
        Location loc = p.getLocation().add(0, 0.7, 0); // 엉덩이 높이 (0.7)
        Vector dir = loc.getDirection().setY(0).normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Location back = loc.clone().subtract(dir.multiply(0.3));
        double time = (System.currentTimeMillis() % 1000) / 1000.0;

        for (int i = 0; i < 4; i++) {
            double angle = (i - 1.5) * 0.5;
            Vector tailDir = dir.clone().multiply(-1).add(right.clone().multiply(Math.tan(angle))).normalize();

            for (double d = 0; d < 2.0; d += 0.2) {
                double wave = Math.sin(time * Math.PI * 2 + d) * 0.2;
                Location point = back.clone().add(tailDir.clone().multiply(d)).add(0, wave + (d * 0.3), 0);
                p.getWorld().spawnParticle(Particle.DUST, point, 1,
                        new Particle.DustOptions(Color.RED, 1.0f));
            }
        }
    }
}
