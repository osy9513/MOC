package me.user.moc.ability.impl;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 065 파이로 (Pyro) 능력 클래스입니다.
 * 팀 포트리스 2의 파이로를 모티브로 하며, 가스를 소모하는 화염방사기를 사용합니다.
 */
public class Pyro extends Ability {

    // 플레이어별 남은 가스 양 관리 (최대 200)
    private final Map<UUID, Integer> gasMap = new ConcurrentHashMap<>();
    // 플레이어별 재장전(충전) 중 여부 확인
    private final Map<UUID, Boolean> isReloading = new ConcurrentHashMap<>();

    public Pyro(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "065";
    }

    @Override
    public String getName() {
        return "파이로";
    }

    @Override
    public List<String> getDescription() {
        // [규칙] 기획안의 "능력 설명" 부분을 고대로 출력합니다.
        return Arrays.asList(
                "§c전투 ● 파이로(팀 포트리스2)",
                "§f화염방사를 쏩니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 1. 기존 철 칼 제거
        p.getInventory().remove(Material.IRON_SWORD);

        // 2. 화염 방사기(부싯돌과 부스러기) 지급
        ItemStack flamethrower = new ItemStack(Material.FLINT_AND_STEEL);
        ItemMeta meta = flamethrower.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6화염 방사기");
            // [규칙] 아이템 사용법 및 설명(Lore) 추가
            meta.setLore(Arrays.asList(
                    "§f좌클릭 시 약 1초 동안 화염을 20회 연속 발사합니다.",
                    "§f발사당 가스를 1 소모하며, 200 가스 소모 시 15초간 재충전됩니다.",
                    "§c데미지: 적중 시 3"));
            meta.setCustomModelData(1); // 리소스팩: firefire
            flamethrower.setItemMeta(meta);
        }

        // 3. 첫 번째 슬롯에 지급
        p.getInventory().setItem(0, flamethrower);

        // 4. 가스 초기화 (최대 200)
        gasMap.put(p.getUniqueId(), 200);
        isReloading.put(p.getUniqueId(), false);

        // 5. 상세 설명 출력
        detailCheck(p);
    }

    @Override
    public void detailCheck(Player p) {
        // [규칙] 정해진 포맷을 엄격히 준수합니다.
        p.sendMessage("§c전투 ● 파이로(팀 포트리스2)");
        p.sendMessage("§f화염방사기를 사용하여 전방을 불바다로 만듭니다.");
        p.sendMessage("§f좌클릭 시 약 1초 동안 가스를 소모하며 20회 연속 화염을 발사합니다.");
        p.sendMessage("§f전방 10칸 부채꼴 범위에 적중 시 3의 데미지를 줍니다.");
        p.sendMessage("§f200 가스를 모두 소모하면 15초 후 가스가 다시 충전됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 15초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 화염 방사기");
        p.sendMessage("§f장비 제거 : 철 칼");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        UUID uuid = p.getUniqueId();
        gasMap.remove(uuid);
        isReloading.remove(uuid);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        // 1. 능력자 체크 및 아이템 확인
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.FLINT_AND_STEEL)
            return;
        if (!item.getItemMeta().getDisplayName().equals("§6화염 방사기"))
            return;

        // 2. 좌클릭 확인
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            e.setCancelled(true); // 기본 동작 취소

            // [규칙] 관전자 처리
            if (p.getGameMode() == GameMode.SPECTATOR)
                return;

            // [규칙] 재장전 체크
            if (isReloading.getOrDefault(uuid, false)) {
                p.sendActionBar(Component.text("§c가스 충전 중입니다..."));
                return;
            }

            // [규칙] Ability 부모 클래스의 checkCooldown 활용
            if (!checkCooldown(p))
                return;

            // 3. 20연사 버스트 로직 시작
            new BukkitRunnable() {
                int count = 0;

                @Override
                public void run() {
                    // 플레이어 상태 확인 (오프라인, 사망, 능력 해제 시 중단)
                    if (!p.isOnline() || p.isDead() || !AbilityManager.getInstance().hasAbility(p, getCode())) {
                        this.cancel();
                        return;
                    }

                    // 재장전 시작 시 중단
                    if (isReloading.getOrDefault(uuid, false)) {
                        this.cancel();
                        return;
                    }

                    int currentGas = gasMap.getOrDefault(uuid, 200);

                    if (currentGas > 0) {
                        // 가스 1 소모
                        currentGas--;
                        gasMap.put(uuid, currentGas);
                        p.sendActionBar(Component.text("§e가스: §f" + currentGas + " / 200"));

                        // 화염 발사!
                        fireFlames(p);

                        count++;

                        // 20회 다 쐈거나 가스가 다 떨어지면 종료
                        if (count >= 20 || currentGas <= 0) {
                            if (currentGas <= 0) {
                                startReloading(p);
                            }
                            this.cancel();
                        }
                    } else {
                        startReloading(p);
                        this.cancel();
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L); // 1틱마다 실행 (총 1초 동안 20회)
        }
    }

    /**
     * 화염방사기 로직: 전방 10칸 부채꼴 범위 공격 및 파티클 출력
     */
    private void fireFlames(Player p) {
        Location eyeLoc = p.getEyeLocation();
        Vector direction = eyeLoc.getDirection().normalize();

        // 소리 재생
        p.getWorld().playSound(eyeLoc, Sound.ITEM_FIRECHARGE_USE, 0.5f, 1.2f);

        // 지현 능력처럼 부채꼴로 점점 커지는 파티클 및 공격 구현
        // 10칸까지 루프를 돌며 처리
        for (int i = 1; i <= 10; i++) {
            final int distance = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    // 플레이어가 이미 제거되었거나 능력이 바뀌었다면 중단
                    if (!p.isOnline() || !AbilityManager.getInstance().hasAbility(p, getCode()))
                        return;

                    Location centerPoint = eyeLoc.clone().add(direction.clone().multiply(distance));

                    // 거리에 따라 비례하여 커지는 부채꼴 반지름 (0.5 ~ 3.5 정도)
                    double radius = 0.5 + (distance * 0.3);

                    // 파티클 출력 (FLAME)
                    // 반지름 내에서 랜덤한 지점들에 파티클 여러 개 소환
                    for (int j = 0; j < 5; j++) {
                        double offsetX = (Math.random() * 2 - 1) * radius;
                        double offsetY = (Math.random() * 2 - 1) * radius;
                        double offsetZ = (Math.random() * 2 - 1) * radius;
                        centerPoint.getWorld().spawnParticle(Particle.FLAME,
                                centerPoint.clone().add(offsetX, offsetY, offsetZ), 1, 0, 0, 0, 0.05);
                    }

                    // 데미지 판정
                    for (Entity entity : centerPoint.getWorld().getNearbyEntities(centerPoint, radius, radius,
                            radius)) {
                        if (entity instanceof LivingEntity target && entity != p) {
                            // [규칙] 관전자 타겟팅 금지
                            if (target instanceof Player pTarget && pTarget.getGameMode() == GameMode.SPECTATOR)
                                continue;

                            // [규칙] 킬 판정 귀속 (MOC_LastKiller)
                            target.setMetadata("MOC_LastKiller",
                                    new FixedMetadataValue(plugin, p.getUniqueId().toString()));
                            target.damage(3, p);

                            // 불 붙이기 (시각적 효과)
                            target.setFireTicks(20);
                        }
                    }
                }
            }.runTaskLater(plugin, (long) (i * 0.5)); // 투사체 이동 느낌을 위해 틱 지연
        }
    }

    /**
     * 가스 재장전(충전) 로직을 시작합니다.
     */
    private void startReloading(Player p) {
        UUID uuid = p.getUniqueId();
        if (isReloading.getOrDefault(uuid, false))
            return;

        isReloading.put(uuid, true);

        // [규칙] 재장전 시 전체 메시지 출력
        Bukkit.broadcast(Component.text("§c파이로: §f무앙무앙"));
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.5f);

        // 15초(300틱) 후 가스 충전
        new BukkitRunnable() {
            int timePassed = 0;

            @Override
            public void run() {
                if (!p.isOnline() || !AbilityManager.getInstance().hasAbility(p, getCode())) {
                    this.cancel();
                    return;
                }

                timePassed++;

                // 액션바에 남은 충전 시간 표시
                int timeLeft = 15 - (timePassed / 20);
                if (timeLeft < 0)
                    timeLeft = 0;
                p.sendActionBar(Component.text("§c가스 충전 중... §f" + timeLeft + "초 남음"));

                if (timePassed >= 300) {
                    gasMap.put(uuid, 200);
                    isReloading.put(uuid, false);
                    p.sendMessage("§a[파이로] §f가스가 완전히 충전되었습니다!");
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
