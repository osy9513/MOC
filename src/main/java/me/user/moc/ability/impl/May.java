package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Dolphin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class May extends Ability {

    // 콤보 및 쿨타임 대기 상태 관리
    // UUID -> 돌진 성공 횟수 (최대 5회)
    private final Map<UUID, Integer> comboCount = new HashMap<>();
    // UUID -> 콤보 유지 타이머 태스크
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> comboTask = new HashMap<>();

    public May(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "066";
    }

    @Override
    public String getName() {
        return "메이";
    }

    @Override
    public java.util.List<String> getDescription() {
        return Arrays.asList("§f돌고래로 적을 공중에 띄워 싸웁니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 기존 철 칼 제거
        if (p.getInventory().contains(Material.IRON_SWORD)) {
            p.getInventory().remove(Material.IRON_SWORD);
        }

        // 커스텀 닻 아이템 추가
        ItemStack anchor = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = anchor.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b닻");
            meta.setLore(Arrays.asList(
                    "§7[우클릭] 돌고래를 소환하며 전방으로 돌진합니다.",
                    "§7돌고래 충돌 시 피격 무적을 무시하고 적에게 8 데미지를 줍니다.",
                    "§7적중 시 남은 쿨타임이 초기화되어 5초 내로 재사용할 수 있습니다.(최대 5연속)"));
            meta.setCustomModelData(15); // CustomModelData = 15
            meta.setUnbreakable(true);
            anchor.setItemMeta(meta);
        }
        p.getInventory().addItem(anchor);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§b전투 ● 메이(길티기어)");
        p.sendMessage("§f돌고래로 적을 공중에 띄워 싸웁니다.");
        p.sendMessage(" ");
        p.sendMessage("§f닻을 우클릭하면 돌고래를 타고 전방에 포물선 방향으로 돌진합니다.");
        p.sendMessage("§f돌진 중 적과 부딪치면 상대의 피격 무적을 무시한 채");
        p.sendMessage("§f8 데미지를 주며 적과 함께 공중에 뜹니다.");
        p.sendMessage("§f이때 쿨타임이 초기화 되어 5초 이내 돌진을 또 할 수 있습니다.");
        p.sendMessage("§f이는 최대 5번까지 가능합니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 초기화 후 5초 이내 돌진을 하지 않을 경우,");
        p.sendMessage("§f쿨타임 초기화가 취소되어 15초의 쿨타임을 가집니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 15초 (충돌 시 5초 이내 재사용/최대 5콤보)");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 닻");
        p.sendMessage("§f장비 제거 : 철 칼");
    }

    @Override
    public void cleanup(Player p) {
        // 플레이어 종료 혹은 능력 교체 시 콤보 태스크 취소
        if (comboTask.containsKey(p.getUniqueId())) {
            comboTask.get(p.getUniqueId()).cancel();
            comboTask.remove(p.getUniqueId());
        }
        comboCount.remove(p.getUniqueId());
        super.cleanup(p);
    }

    // 돌진 중인지 여부를 저장해 중복 대쉬 방지
    private final java.util.Set<UUID> isDashing = new java.util.HashSet<>();

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        if (isSilenced(p))
            return;
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.IRON_SWORD || !item.hasItemMeta()
                || item.getItemMeta().getCustomModelData() != 15)
            return;

        // 돌진 중이면 무시
        if (isDashing.contains(p.getUniqueId()))
            return;

        // 쿨타임 체크 (15초 본 쿨타임)
        if (!checkCooldown(p))
            return;

        e.setCancelled(true);
        executeDolphinDash(p);
    }

    private void executeDolphinDash(Player p) {
        UUID uuid = p.getUniqueId();

        // 돌진 시작: 기존 콤보 타이머 취소
        if (comboTask.containsKey(uuid)) {
            comboTask.get(uuid).cancel();
            comboTask.remove(uuid);
        }

        isDashing.add(uuid);

        // 돌고래 소환
        Location loc = p.getLocation();
        Dolphin dolphin = p.getWorld().spawn(loc, Dolphin.class, d -> {
            d.setInvulnerable(true);
            d.setAI(false);
            d.setGravity(true);
            d.setCustomName("§b돌고래씨");
            d.setCustomNameVisible(false);
        });

        dolphin.addPassenger(p);

        // 포물선 벡터 (시선 방향, y축 고정)
        Vector v = loc.getDirection().setY(0).normalize().multiply(1.5).setY(0.8);
        dolphin.setVelocity(v);

        p.getWorld().playSound(loc, Sound.ENTITY_DOLPHIN_PLAY, 1.0f, 1.0f);
        p.getServer().broadcastMessage("§b메이: §f돌고래씨!");

        // 3x3 물 디스플레이 생성
        org.bukkit.entity.BlockDisplay waterDisplay = p.getWorld().spawn(loc.clone().subtract(1.5, 0.5, 1.5),
                org.bukkit.entity.BlockDisplay.class, d -> {
                    d.setBlock(org.bukkit.Bukkit.createBlockData(Material.WATER));
                    org.bukkit.util.Transformation t = d.getTransformation();
                    t.getScale().set(3f, 0.8f, 3f);
                    d.setTransformation(t);
                });

        // 소환된 엔티티/디스플레이 Ability 통합 관리에 등록하여 게임 종료/사망 시 자동 삭제되도록 함
        registerSummon(p, dolphin);
        registerSummon(p, waterDisplay);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (dolphin.isDead() || !dolphin.isValid() || !dolphin.getPassengers().contains(p)) {
                    finishDash(p, dolphin, waterDisplay, false, null);
                    this.cancel();
                    return;
                }

                // 돌진 최대 거리/시간 초과(약 60틱=3초) 또는 돌진 후 바닥에 닿았을 때 (초반 몇 틱은 제외)
                if (ticks > 60 || (ticks > 5 && dolphin.isOnGround())) {
                    finishDash(p, dolphin, waterDisplay, false, null);
                    this.cancel();
                    return;
                }

                Location dLoc = dolphin.getLocation();
                // 물 디스플레이 추적
                waterDisplay.teleport(dLoc.clone().subtract(1.5, 0.5, 1.5));

                // 물 이펙트 흩날림
                p.getWorld().spawnParticle(Particle.SPLASH, dLoc.clone().add(0, 0.5, 0), 15, 0.5, 0.5, 0.5, 0.1);

                // 엔티티 충돌 체크
                for (Entity e : dolphin.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (e instanceof LivingEntity target && e != p && e != dolphin) {
                        if (target instanceof Player tp && tp.getGameMode() == org.bukkit.GameMode.SPECTATOR)
                            continue;

                        // 타격 성공 반환
                        finishDash(p, dolphin, waterDisplay, true, target);
                        this.cancel();
                        return;
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void finishDash(Player p, Dolphin dolphin, org.bukkit.entity.BlockDisplay waterDisplay, boolean hit,
            LivingEntity target) {
        UUID uuid = p.getUniqueId();

        isDashing.remove(uuid);

        if (dolphin.isValid()) {
            dolphin.eject();
            dolphin.remove();
        }
        if (waterDisplay.isValid()) {
            waterDisplay.remove();
        }

        // 임시 낙하 데미지 무시를 위한 조치 (낙하 거리 초기화)
        p.setFallDistance(0);

        if (hit && target != null) {
            // 적중: 데미지 무적 무시 8 부여
            target.setNoDamageTicks(0);
            target.damage(8.0, p);

            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);

            // 공중으로 띄움 (메이, 타겟)
            Vector up = new Vector(0, 0.8, 0);
            p.setVelocity(p.getVelocity().setY(0).add(up));
            target.setVelocity(target.getVelocity().setY(0).add(up));

            // 콤보 카운트 업데이트
            int currentCombo = comboCount.getOrDefault(uuid, 0) + 1;
            if (currentCombo >= 5) {
                // 5타 끝 15초 쿨타임
                comboCount.remove(uuid);
                setCooldown(p, 15);
                p.sendActionBar(net.kyori.adventure.text.Component.text("§c[!] 5콤보 달성! 재사용 대기시간 적용"));
            } else {
                comboCount.put(uuid, currentCombo);
                // 5초 타이머 재시작
                if (comboTask.containsKey(uuid)) {
                    comboTask.get(uuid).cancel();
                }

                org.bukkit.scheduler.BukkitTask task = new BukkitRunnable() {
                    int timeLeft = 50; // 5초 = 50 * 2틱 (100틱)

                    @Override
                    public void run() {
                        if (timeLeft <= 0) {
                            comboCount.remove(uuid);
                            comboTask.remove(uuid);
                            setCooldown(p, 15); // 시간 초과 시 15초 본 쿨타임 지정
                            p.sendActionBar(net.kyori.adventure.text.Component.text("§c[!] 콤보 시간 초과! 재사용 대기시간 15초 적용"));
                            this.cancel();
                            return;
                        }

                        // 요청 포맷: 1/5콤보! 연속 사용 가능! 5초..
                        p.sendActionBar(net.kyori.adventure.text.Component.text(
                                "§b" + currentCombo + "/5콤보! 연속 사용 가능! " + String.format("%.1f", timeLeft / 10.0)
                                        + "초.."));
                        timeLeft--;
                    }
                }.runTaskTimer(plugin, 0L, 2L);

                comboTask.put(uuid, task);
                registerTask(p, task); // 능력 매니저 등록
            }
        } else {
            // 허공 빗나감 혹은 맨 땅에 떨어짐 -> 즉시 15초 쿨타임
            comboCount.remove(uuid);
            setCooldown(p, 15);
        }
    }
}
