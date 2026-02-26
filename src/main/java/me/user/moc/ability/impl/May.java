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
        p.sendMessage("§f이때 쿨타임이 초기화 되어 3초 이내 돌진을 또 할 수 있습니다.");
        p.sendMessage("§f이는 최대 5번까지 가능합니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 초기화 후 3초 이내 돌진을 하지 않을 경우,");
        p.sendMessage("§f쿨타임 초기화가 취소되어 15초의 쿨타임을 가집니다.");
        p.sendMessage("§f돌고래 돌진 시 낙하 데미지를 받지 않습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 15초 (충돌 시 3초 이내 재사용/최대 5콤보)");
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

    // 돌진 중인지 여부와 핑, 랙으로 인한 낙하 데미지 무시 유예 시간을 함께 저장 (UUID -> 유예 만료 시간(밀리초))
    private final Map<UUID, Long> isDashing = new HashMap<>();

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

        // 돌진 중이거나 유예 시간이 안 끝났으면 무시 (1초 유예)
        if (isDashing.containsKey(p.getUniqueId()) && System.currentTimeMillis() < isDashing.get(p.getUniqueId()))
            return;

        // 이미 무언가(돌고래 등)에 탑승 중이면 돌진 불가
        if (p.getVehicle() != null)
            return;

        // 쿨타임 체크 (수동 확인: checkCooldown()은 쿨타임을 자동으로 부여하기도 하므로 직접 검사)
        if (cooldowns.containsKey(p.getUniqueId())) {
            long now = System.currentTimeMillis();
            long endTime = cooldowns.get(p.getUniqueId());
            if (now < endTime) {
                // 이미 checkCooldown 내부에서 액션바 알림을 주지만 수동으로 할 땐 직접 알림
                if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    double left = (endTime - now) / 1000.0;
                    p.sendActionBar(net.kyori.adventure.text.Component
                            .text("§c쿨타임이 " + String.format("%.1f", left) + "초 남았습니다."));
                    return;
                }
            } else {
                cooldowns.remove(p.getUniqueId());
            }
        }

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

        // 돌진 시작: 아주 넉넉하게 5초(5000ms) 동안 대쉬 판정 유지 - finishDash에서 1초 유예로 줄어듦
        isDashing.put(uuid, System.currentTimeMillis() + 5000);

        // 돌고래 소환
        Location loc = p.getLocation();
        Dolphin dolphin = p.getWorld().spawn(loc.clone().add(0, 0.5, 0), Dolphin.class, d -> {
            d.setInvulnerable(true);
            // NoAI가 켜져 있으면 velocity 연산이 무시되므로 제거하고 구속으로 이동을 억제합니다.
            d.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 100, 255,
                    false, false, false));
            d.setGravity(true);
            d.setCustomName("§b돌고래씨");
            d.setCustomNameVisible(false);
        });

        dolphin.addPassenger(p);

        // 포물선 벡터 (시선 방향, y축 고정) - 거리를 10칸 정도로 맞추기 위해 Y 폭을 낮춤
        Vector baseVel = loc.getDirection().setY(0).normalize().multiply(1.5).setY(0.6);
        dolphin.setVelocity(baseVel);

        // 현재 콤보 횟수 확인 (최초 사용 시 1)
        int combo = comboCount.getOrDefault(uuid, 0) + 1;

        p.getWorld().playSound(loc, Sound.ENTITY_DOLPHIN_PLAY, 1.0f, 1.0f);
        p.getServer().broadcastMessage("§b메이: §f돌고래씨! (" + combo + "/5)");

        // 3x3 물 디스플레이 생성
        org.bukkit.entity.BlockDisplay waterDisplay = p.getWorld().spawn(loc.clone().subtract(0, 0.5, 0),
                org.bukkit.entity.BlockDisplay.class, d -> {
                    // 유체인 WATER는 BlockDisplay에서 투명하게 렌더링되므로 CYAN 유리로 대체합니다.
                    d.setBlock(org.bukkit.Bukkit.createBlockData(Material.CYAN_STAINED_GLASS));
                    org.bukkit.util.Transformation t = d.getTransformation();
                    t.getScale().set(3f, 1f, 3f);
                    t.getTranslation().set(-1.5f, -0.2f, -1.5f);
                    d.setTransformation(t);
                    d.setTeleportDuration(1); // 1틱 보간(부드러운 이동)
                });

        // 소환된 엔티티/디스플레이 Ability 통합 관리에 등록하여 게임 종료/사망 시 자동 삭제되도록 함
        registerSummon(p, dolphin);
        registerSummon(p, waterDisplay);

        new BukkitRunnable() {
            int ticks = 0;
            int groundTicks = 0; // 땅에 닿아있는 시간 누적
            Vector currentVel = baseVel.clone();

            @Override
            public void run() {
                if (dolphin.isDead() || !dolphin.isValid() || !dolphin.getPassengers().contains(p)) {
                    finishDash(p, dolphin, waterDisplay, false, null);
                    this.cancel();
                    return;
                }

                // 돌진 최대 시간 초과(약 60틱=3초)
                if (ticks > 60) {
                    finishDash(p, dolphin, waterDisplay, false, null);
                    this.cancel();
                    return;
                }

                Location dLoc = dolphin.getLocation();

                // 2) 플레이어 낙하 데미지 지속 무시
                p.setFallDistance(0);

                // 벽 충돌 (가시선 내 블록 확인) 또는 땅에 닿은 시간 누적 (0.5초 = 10틱)
                boolean isCollidingWithBlock = false;

                // 돌고래의 현재 위치와 진행 방향 앞쪽 블록(벽), 그리고 아래쪽 블록(땅) 검사
                org.bukkit.block.Block currentBlock = dLoc.getBlock();
                org.bukkit.block.Block frontBlock = dLoc.clone().add(currentVel.clone().normalize().multiply(0.5))
                        .getBlock();
                org.bukkit.block.Block belowBlock = dLoc.clone().subtract(0, 0.5, 0).getBlock();

                if (currentBlock.getType().isSolid() || frontBlock.getType().isSolid() || belowBlock.getType().isSolid()
                        || dolphin.isOnGround()) {
                    isCollidingWithBlock = true;
                }

                if (isCollidingWithBlock) {
                    // 낙하 데미지를 확실히 무효화
                    p.setFallDistance(0);
                    groundTicks++;
                    if (groundTicks >= 10) {
                        finishDash(p, dolphin, waterDisplay, false, null);
                        this.cancel();
                        return;
                    }
                } else {
                    groundTicks = 0; // 공중에 뜨면 초기화
                }

                // 강제 전진 유지 및 중력 적용 (돌고래의 마찰을 이겨냄) - 중력을 조금 더 줘서 10칸 쯤에 떨어지게 유도
                currentVel.setY(currentVel.getY() - 0.09); // 중력 스케일
                dolphin.setVelocity(currentVel);

                // 물 디스플레이 추적 (translation에 의해 중심 정렬됨)
                waterDisplay.teleport(dLoc);

                // 물 이펙트 흩날림 (물기둥 느낌)
                p.getWorld().spawnParticle(Particle.SPLASH, dLoc.clone().add(0, 0.5, 0), 45, 1.0, 0.5, 1.0, 0.1);

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

        // 돌진 종료 시, 약간의 유예 시간(1초 = 1000ms)을 주어 핑이나 서버 랙으로 인한 뒤늦은 낙뎀 방지
        isDashing.put(uuid, System.currentTimeMillis() + 1000);

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

            // 1) 둘 다 기존보다 2칸 정도(Y값 0.4~0.5 가량) 덜 띄우도록 수정.
            // 기존 1.3에서 0.4를 뺀 0.9로 설정합니다.
            Vector targetUp = new Vector(0, 0.9, 0);

            // 2) 서로 높이 차이를 15%로 설정 (메이가 15% 덜 띄워지도록 0.85 곱함)
            Vector selfUp = new Vector(0, 0.9 * 0.85, 0);

            p.setVelocity(p.getVelocity().setY(0).add(selfUp));
            target.setVelocity(target.getVelocity().setY(0).add(targetUp));

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
                    int timeLeft = 30; // 3초 = 30 * 2틱 (60틱)

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

                        // 요청 포맷: 1/5콤보! 연속 사용 가능! 3초..
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

    @EventHandler
    public void onPlayerDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p))
            return;

        // 낙하 데미지일 경우
        if (e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) {
            UUID uuid = p.getUniqueId();
            // 메이 능력 돌진 중이거나 돌진이 끝난 직후 유예 시간(1초) 내라면
            if (isDashing.containsKey(uuid) && System.currentTimeMillis() < isDashing.get(uuid)) {
                e.setCancelled(true);
            }
        }
    }
}
