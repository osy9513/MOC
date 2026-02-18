package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public class TrafalgarLaw extends Ability {

    private final Map<UUID, Long> roomExpiry = new HashMap<>(); // 룸 만료 시간
    private final Map<UUID, Location> roomCenters = new HashMap<>(); // 룸 중심 좌표
    private final Map<UUID, Long> clickCooldown = new HashMap<>(); // 클릭 디바운스 (버그 수정)

    private static final int ROOM_RADIUS = 15;
    // [버프] 지속시간 5초 -> 13초 (+8초)
    private static final int ROOM_DURATION_SEC = 13;
    private static final int DAMAGE_AMOUNT = 10; // 체력 5칸
    private static final int COOLDOWN_SEC = 30;

    public TrafalgarLaw(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "023";
    }

    @Override
    public String getName() {
        return "트라팔가 로우";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§c전투 ● 트라팔가 로우(원피스)",
                "§fROOM을 전개하여 영역 내 대상과 위치를 바꾸고 공간 절단 피해를 입힙니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 기본 지급 아이템: 철 검 (있다면 지급 안 함 -> 지급하고 Lore 추가)
        p.getInventory().remove(Material.IRON_SWORD);
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b키코쿠"); // Kikoku
            meta.setLore(Arrays.asList("§7웅크리기 + 우클릭 : ROOM 전개", "§7ROOM 안에서 우클릭 : 샴블즈 (위치 교환)"));
            meta.setCustomModelData(11); // 리소스팩: trafalgarlaw
            sword.setItemMeta(meta);
        }
        p.getInventory().addItem(sword);
        p.sendMessage("§e[MOC] §fROOM, 샴블즈(Shambles).");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 트라팔가 로우(원피스)");
        p.sendMessage("§f웅크리기(Shift) + 검 우클릭 시 13초간 §bROOM§f을 전개합니다.");
        p.sendMessage("§fROOM 내부의 대상을 바라보고 우클릭 시 §e위치를 맞바꿉니다(샴블즈)§f.");
        p.sendMessage("§f교환된 대상에게는 10의 피해를 입힙니다.");
        p.sendMessage("§f룸 지속시간 동안 횟수 제한 없이 사용 가능합니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 30초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void reset() {
        super.reset();
        roomExpiry.clear();
        roomCenters.clear();
        clickCooldown.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isLaw(p))
            return;

        // [추가] 전투 시작 전에는 스킬 발동 금지
        if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        // [버그 수정] 왼존/오른손 중복 실행 방지
        if (e.getHand() != EquipmentSlot.HAND)
            return;

        // [버그 수정] 너무 빠른 연속 클릭 방지 (0.2초)
        if (checkClickThrottle(p))
            return;

        // 우클릭 이벤트만 처리 (ROOM or Shambles)
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (!isSword(p.getInventory().getItemInMainHand().getType()))
                return;

            if (p.isSneaking()) {
                // Shift + 우클릭 -> ROOM 전개
                activateRoom(p);
            } else {
                // 그냥 우클릭 -> 샴블즈 (원거리)
                tryShambles(p, null);
            }
        }
    }

    // [버그 수정] 클릭 스로틀링 (디바운싱)
    private boolean checkClickThrottle(Player p) {
        long now = System.currentTimeMillis();
        long last = clickCooldown.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < 200) { // 0.2초 미만 재클릭 무시
            return true;
        }
        clickCooldown.put(p.getUniqueId(), now);
        return false;
    }

    /**
     * 검으로 직접 때렸을 때 (좌클릭) - 샴블즈 발동 안 함 (우클릭으로 변경됨 요청)
     * 하지만 사용자가 "우클릭시 발동되도록 변경"을 요청했으므로,
     * 공격(좌클릭) 이벤트에서의 샴블즈 로직은 제거하거나 비활성화해야 함.
     * 여기서는 제거합니다.
     */
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent e) {
        // 기존 좌클릭 샴블즈 로직 제거 -> 우클릭으로 통합
    }

    private boolean isLaw(Player p) {
        return MocPlugin.getInstance().getAbilityManager().hasAbility(p, getCode());
    }

    private boolean isSword(Material type) {
        return type.name().endsWith("_SWORD");
    }

    private boolean isRoomActive(Player p) {
        return roomExpiry.containsKey(p.getUniqueId()) &&
                System.currentTimeMillis() < roomExpiry.get(p.getUniqueId());
    }

    /**
     * ROOM 활성화 로직
     */
    private void activateRoom(Player p) {
        // 쿨타임 체크 (부모 클래스 메서드 활용)
        if (!checkCooldown(p))
            return;

        // 이미 룸이 켜져있으면 리턴? (혹은 갱신? 여기선 쿨타임 때문에 못 씀)
        // 쿨타임 적용
        setCooldown(p, COOLDOWN_SEC);

        // 1. 상태 설정
        long durationMillis = ROOM_DURATION_SEC * 1000L;
        roomExpiry.put(p.getUniqueId(), System.currentTimeMillis() + durationMillis);
        roomCenters.put(p.getUniqueId(), p.getLocation().clone());

        // 2. 메시지 및 사운드
        p.getServer().broadcastMessage("§b트라팔가 로우 : ROOM.");

        // [사운드] 바닐라 효과음 (기본)
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.5f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);

        // [사운드] 커스텀 음성 (리소스팩 있을 시 재생)
        // 리소스팩이 입혀진 클라이언트는 음성이 겹쳐 들리고, 없으면 위 효과음만 들립니다.
        p.getWorld().playSound(p.getLocation(), "moc.law.room", 1.0f, 1.0f);

        // 3. 파티클 루프 (Sphere)
        startRoomParticleTask(p, p.getLocation(), durationMillis);
    }

    /**
     * 파티클 표시 태스크 (개선된 버전)
     */
    private void startRoomParticleTask(Player p, Location center, long durationMillis) {
        BukkitTask task = new BukkitRunnable() {
            long startTime = System.currentTimeMillis();
            double angle = 0; // 회전 각도

            @Override
            public void run() {
                // 시간 만료 혹은 플레이어 접속 종료 시 중단
                if (System.currentTimeMillis() - startTime > durationMillis || !p.isOnline()) {
                    this.cancel();
                    return;
                }

                // 1. 바닥 테두리 (선명한 원)
                drawCircle(center, ROOM_RADIUS);

                // 2. 구형 파티클 (회전하는 점들)
                drawRotatingSphere(center, ROOM_RADIUS, angle);
                angle += 0.1; // 회전 속도
            }
        }.runTaskTimer(plugin, 0L, 2L); // 0.1초(2틱)마다 갱신 (부드러운 애니메이션)

        registerTask(p, task);
    }

    /**
     * 바닥 원 그리기
     */
    private void drawCircle(Location center, double radius) {
        // [수정] 밀도 증가 (PI/16 -> PI/32)
        for (double a = 0; a < Math.PI * 2; a += Math.PI / 32) {
            double x = Math.cos(a) * radius;
            double z = Math.sin(a) * radius;
            center.add(x, 0, z);
            // [수정] 크기 증가 (1.5f -> 2.0f)
            center.getWorld().spawnParticle(Particle.DUST, center, 1,
                    new Particle.DustOptions(Color.fromRGB(0, 100, 200), 2.0f));
            center.subtract(x, 0, z);
        }
    }

    /**
     * 회전하는 구형 파티클 (Fibonacci Sphere 변형)
     */
    // [수정] org.bukkit.util.Vector 명시적 사용
    private void drawRotatingSphere(Location center, double radius, double angleOffset) {
        // [수정] 점 개수 증가 (50 -> 100)
        int points = 100;
        double phi = Math.PI * (3. - Math.sqrt(5.)); // 황금각

        for (int i = 0; i < points; i++) {
            // y좌표는 -1 ~ 1 사이를 순회 (구 전체)
            // 시간(angleOffset)을 더해 점들이 계속 흐르도록 함
            double y = 1 - (i / (float) (points - 1)) * 2;
            double r = Math.sqrt(1 - y * y) * radius; // 해당 높이에서의 반지름

            double theta = phi * i + angleOffset * 5; // 회전 적용

            double x = Math.cos(theta) * r;
            double z = Math.sin(theta) * r;

            // y값에도 radius 적용
            org.bukkit.util.Vector vec = new org.bukkit.util.Vector(x, y * radius, z);

            center.add(vec);
            // [수정] 크기 증가 (1.0f -> 1.5f)
            center.getWorld().spawnParticle(Particle.DUST, center, 1,
                    new Particle.DustOptions(Color.AQUA, 1.5f));

            // [추가] 가시성을 위해 END_ROD 파티클 섞기 (10개 중 1개 꼴)
            if (i % 10 == 0) {
                center.getWorld().spawnParticle(Particle.END_ROD, center, 1, 0, 0, 0, 0);
            }
            center.subtract(vec);
        }
    }

    /**
     * 샴블즈 시도 (위치 교환)
     * 
     * @param target 직접 타격한 엔티티 (없으면 null)
     * @return 성공 여부
     */
    private boolean tryShambles(Player p, Entity target) {
        // 1. 룸 활성화 확인
        if (!isRoomActive(p))
            return false;

        // 2. 횟수 제한 확인 (제거됨: 무제한)

        // 3. 타겟 확보 (직접 타격이 아니면 레이트레이싱)
        if (target == null) {
            RayTraceResult result = p.getWorld().rayTraceEntities(
                    p.getEyeLocation(),
                    p.getEyeLocation().getDirection(),
                    ROOM_RADIUS, // 룸 반경까지만 탐색
                    entity -> entity != p && entity instanceof LivingEntity && !(entity instanceof Player
                            && ((Player) entity).getGameMode() == org.bukkit.GameMode.SPECTATOR));
            if (result != null) {
                target = result.getHitEntity();
            }
        }

        // 타겟 유효성 검사
        if (target == null || !(target instanceof LivingEntity))
            return false;

        // 4. 거리 검사 (룸 영역 내부인지 확인)
        Location center = roomCenters.get(p.getUniqueId());
        if (center == null)
            return false;

        // 룸 중심으로부터의 거리가 반경보다 크면 실패
        if (center.distance(target.getLocation()) > ROOM_RADIUS) {
            p.sendActionBar(Component.text("§c대상이 ROOM 범위를 벗어났습니다."));
            return false;
        }

        // 5. 실행 (Swap)
        performSwap(p, (LivingEntity) target);

        return true;
    }

    private void performSwap(Player p, LivingEntity target) {
        Location pLoc = p.getLocation();
        Location tLoc = target.getLocation();

        // 위치 교환 (Yaw/Pitch 유지 여부는 기획 의도에 따라 다름. 여기선 그대로 이동)
        p.teleport(tLoc);
        target.teleport(pLoc);

        // 효과음 & 파티클 (원래 위치 + 도착 위치 모두)
        playSwapEffect(pLoc);
        playSwapEffect(tLoc);

        // 메시지
        p.sendMessage("§b샴블즈!");
        // [음성] 커스텀 사운드 키
        p.getWorld().playSound(p.getLocation(), "moc.law.shambles", 1.0f, 1.0f);

        // 피해 및 상태이상
        target.damage(DAMAGE_AMOUNT, p); // 10 데미지 (하트 5칸)
        // target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0)); //
        // 멀미 2초 (40 ticks)

        // 대상에게도 메시지 (플레이어인 경우)
        if (target instanceof Player tPlayer) {
            tPlayer.sendMessage("§c" + p.getName() + "의 ROOM 안에서 공간이 절단되었습니다!");
        }
    }

    private void playSwapEffect(Location loc) {
        // [사운드] 바닐라 효과음 (이동/타격감)
        loc.getWorld().playSound(loc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2.0f);

        loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
    }
}
