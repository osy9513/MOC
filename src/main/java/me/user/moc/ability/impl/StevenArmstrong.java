package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StevenArmstrong extends Ability {

    private final String CODE = "078";
    private final String NAME = "스티븐 암스트롱";
    private final int COOLDOWN = 15;

    // 쉬프트(웅크리기) 타이밍 체크용 변수
    private final Map<UUID, Long> lastSneakTime = new ConcurrentHashMap<>();

    // 나노머신 활성화 및 남은 시간(틱) 관리 변수
    private BukkitTask passiveTask = null; // 상시 힘 버프 태스크
    private final Map<UUID, BukkitTask> activeNanoTaskMap = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> activeNanoTicks = new ConcurrentHashMap<>();

    // [고도화] 나노머신 최대 지속시간: 7초 = 140틱
    private static final int MAX_NANO_TICKS = 140;

    public StevenArmstrong(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§6유틸 ● 스티븐 암스트롱(메탈기어)",
                "§f상원의원 스티븐 암스트롱이 됩니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§6유틸 ● 스티븐 암스트롱(메탈기어)");
        p.sendMessage("§f상시 힘 1 버프를 유지합니다.");
        p.sendMessage("§f연속으로 쉬프트(웅크리기)를 2번 누르면 나노 머신을 3초간 활성화 합니다.");
        p.sendMessage("§f나노 머신 활성화 중에는 모든 피격 데미지와 밀림(넉백)이 0으로 고정됩니다.");
        p.sendMessage("§f나노 머신 활성화 중 피격당하면 나노 머신의 지속 시간이 1초(20틱) 증가됩니다.");
        p.sendMessage("§f나노 머신 최대 지속시간은 7초이며, 지속시간 중엔 구속 1 디버프가 적용됩니다.");
        p.sendMessage("§f ");
        p.sendMessage("§f쿨타임 : " + COOLDOWN + "초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 상원의원 셔츠, 상원의원 바지, 상원의원 구두");
        p.sendMessage("§f장비 제거 : 철 칼");
    }

    @Override
    public void giveItem(Player p) {
        // [장비 제거 사항 적용] - 철 칼 제거
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == Material.IRON_SWORD) {
                p.getInventory().remove(item);
            }
        }

        // [추가 장비] - 갑옷 착용
        ItemStack tunic = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta tMeta = (LeatherArmorMeta) tunic.getItemMeta();
        if (tMeta != null) {
            tMeta.setColor(Color.WHITE);
            // tMeta.setUnbreakable(true);
            tMeta.setDisplayName("§f상원의원 셔츠");
            tunic.setItemMeta(tMeta);
        }

        ItemStack pants = new ItemStack(Material.LEATHER_LEGGINGS);
        ItemMeta pMeta = pants.getItemMeta();
        if (pMeta != null) {
            pMeta.setUnbreakable(true);
            pMeta.setDisplayName("§f상원의원 바지");
            pants.setItemMeta(pMeta);
        }

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta bMeta = (LeatherArmorMeta) boots.getItemMeta();
        if (bMeta != null) {
            bMeta.setColor(Color.BLACK);
            bMeta.setUnbreakable(true);
            bMeta.setDisplayName("§0상원의원 구두");
            boots.setItemMeta(bMeta);
        }

        p.getInventory().setChestplate(tunic);
        p.getInventory().setLeggings(pants);
        p.getInventory().setBoots(boots);

        // 능력을 처음 부여받으면 상시 힘 버프 루프 (초기 한 번만 실행되도록 체크)
        if (passiveTask == null || passiveTask.isCancelled()) {
            startPassiveLoop();
        }
    }

    // 상시 힘 1 버프 유지 로직
    private void startPassiveLoop() {
        passiveTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (AbilityManager.getInstance().hasAbility(p, getCode())) {
                        if (p.getGameMode() != GameMode.SPECTATOR && !isSilenced(p)) {
                            // 힘 1 버프 (Amplifier 0 = 힘 1)
                            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false, false));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @Override
    public void reset() {
        super.reset();
        lastSneakTime.clear();
        for (BukkitTask task : activeNanoTaskMap.values()) {
            if (!task.isCancelled())
                task.cancel();
        }
        activeNanoTaskMap.clear();
        activeNanoTicks.clear();

        // [버그 수정] 패시브 루프를 여기서 취소만 합니다.
        // 이전 코드에서 reset() 안에서 startPassiveLoop()를 호출하여
        // "플러그인 비활성화 중 태스크 등록 시도" 에러가 발생했었습니다.
        // reset()은 오직 정리(Cancel)만 담당해야 하며,
        // 패시브 루프 재시작은 giveItem()에서 능력이 실제로 지급될 때만 이루어집니다.
        if (passiveTask != null && !passiveTask.isCancelled()) {
            passiveTask.cancel();
        }
        passiveTask = null; // null로 초기화하여 다음 giveItem() 호출 시 새로 시작되도록 준비
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        UUID uuid = p.getUniqueId();
        lastSneakTime.remove(uuid);

        if (activeNanoTaskMap.containsKey(uuid)) {
            BukkitTask task = activeNanoTaskMap.remove(uuid);
            if (!task.isCancelled())
                task.cancel();
            activeNanoTicks.remove(uuid);
        }
    }

    // 연속 쉬프트 활성화
    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!e.isSneaking())
            return; // 일어날 때는 무시
        if (isSilenced(p))
            return;
        if (p.getGameMode() == GameMode.SPECTATOR)
            return;
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();

        // 나노머신 활성화 중이면 무시
        if (activeNanoTicks.containsKey(uuid) && activeNanoTicks.get(uuid) > 0)
            return;

        // 쿨다운 체크
        if (!checkCooldown(p))
            return;

        // 더블 쉬프트 인지 (500ms(0.5초) 이내 연타)
        if (lastSneakTime.containsKey(uuid)) {
            long last = lastSneakTime.get(uuid);
            if (now - last <= 500) {
                // 발동 (쿨타임은 나노머신 종료 후 deactivateNanomachines에서 부여)
                lastSneakTime.remove(uuid);
                activateNanomachines(p);
                return;
            }
        }

        lastSneakTime.put(uuid, now);
    }

    private void activateNanomachines(Player p) {
        UUID uuid = p.getUniqueId();

        // 1. 발동 시 주변 가장 가까운 플레이어 찾고 대사 출력
        Player nearPlayer = null;
        double minDistance = Double.MAX_VALUE;
        for (Entity e : p.getNearbyEntities(30, 30, 30)) {
            if (e instanceof Player target && target.getGameMode() != GameMode.SPECTATOR) {
                double dist = p.getLocation().distanceSquared(target.getLocation());
                if (dist < minDistance) {
                    minDistance = dist;
                    nearPlayer = target;
                }
            }
        }

        String targetName = (nearPlayer != null) ? nearPlayer.getName() : "누군가";
        Bukkit.broadcastMessage("§e" + targetName + " §f: Why won't you die?!");

        // 이펙트/소리
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 0.5f);

        // 기본 3초 (60틱)
        activeNanoTicks.put(uuid, 60);

        BukkitTask oldTask = activeNanoTaskMap.get(uuid);
        if (oldTask != null && !oldTask.isCancelled()) {
            oldTask.cancel();
        }

        BukkitTask nanoTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) {
                    deactivateNanomachines(p);
                    this.cancel();
                    return;
                }

                int left = activeNanoTicks.getOrDefault(uuid, 0);
                if (left <= 0) {
                    deactivateNanomachines(p);
                    this.cancel();
                    return;
                }

                // [고도화] 나노머신 활성화 중 구속 1 디버프 부여 (매 틱마다 갱신)
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 5, 0, false, false, false));

                // 액션바 피드백
                p.sendActionBar(net.kyori.adventure.text.Component
                        .text("§c[나노머신] §e활성화 중! (" + String.format("%.1f", left / 20.0) + "초)"));

                activeNanoTicks.put(uuid, left - 1);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        activeNanoTaskMap.put(uuid, nanoTask);
        registerTask(p, nanoTask);
    }

    private void deactivateNanomachines(Player p) {
        UUID uuid = p.getUniqueId();
        activeNanoTicks.remove(uuid);
        activeNanoTaskMap.remove(uuid);

        // [고도화] 나노머신 종료 시 구속 디버프 제거
        p.removePotionEffect(PotionEffectType.SLOWNESS);

        // [고도화] 쿨타임은 나노머신이 끝난 후 시작
        if (p.getGameMode() != GameMode.CREATIVE) {
            setCooldown(p, COOLDOWN);
        }

        Bukkit.broadcastMessage("§6스티븐 암스트롱 §f: Nanomachines, son.");
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
    }

    // 나노머신 활성화 중 피격 판정
    @EventHandler(priority = EventPriority.HIGHEST) // 다른 모디파이어보다 최우선 처리를 위해 제일 마지막 단계에
    public void onEntityDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p))
            return;
        UUID uuid = p.getUniqueId();

        // 나노머신 활성화 여부
        if (activeNanoTicks.containsKey(uuid) && activeNanoTicks.get(uuid) > 0) {

            // 데미지는 무효화하되 이벤트는 살림 (몸 빨개지는 이펙트)
            e.setDamage(0.0);

            // 파티클 (칼 크리티컬) 생성
            p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1.0, 0), 15, 0.3, 0.5, 0.3, 0.1);

            // 소리 피드백 (단단한 소리)
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.5f, 1.2f);

            // [고도화] 지속시간 1초 연장 (최대 7초 = 140틱 제한)
            int current = activeNanoTicks.get(uuid);
            int extended = Math.min(current + 20, MAX_NANO_TICKS);
            activeNanoTicks.put(uuid, extended);

            // 밀림 현상을 없애기 위해 1틱 뒤에 velocity를 0으로 강제 초기화
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && !p.isDead()) {
                    p.setVelocity(new Vector(0, -0.0784000015258789, 0)); // 약간의 중력값만 유지 (땅에 닿아있게)
                }
            }, 1L);
        }
    }
}
