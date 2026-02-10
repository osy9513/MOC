package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class Jihyun extends Ability {

    // 플레이어별 스택(연속 발사 횟수) 저장 (기본 1, 최대 8)
    private final Map<UUID, Integer> stackMap = new HashMap<>();

    public Jihyun(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "H05";
    }

    @Override
    public String getName() {
        return "지현";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§d히든 ● 지현(바집소)",
                "§f연기로 도넛을 만듭니다!");
    }

    @Override
    public void giveItem(Player p) {
        // 기존 철 검 제거 (슬롯 확인 필요 없이 전체 인벤토리에서 제거 시도)
        p.getInventory().remove(Material.IRON_SWORD);

        // 전자담배(흰색 양털) 지급
        ItemStack weapon = new ItemStack(Material.WHITE_WOOL);
        ItemMeta meta = weapon.getItemMeta();
        meta.setDisplayName("§f전자담배");
        weapon.setItemMeta(meta);

        // 첫 번째 슬롯에 지급
        p.getInventory().setItem(0, weapon);

        // 스택 초기화 (혹시 이전 기록이 남아있을 수 있으므로)
        stackMap.put(p.getUniqueId(), 1);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§d히든 ● 지현(바집소)");
        p.sendMessage("§f연기로 도넛을 만듭니다!");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 5초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 전자담배");
        p.sendMessage("§f장비 제거 : 철 검");
        p.sendMessage("§f---");

        // [능력 이펙트 상세 설명]
        p.sendMessage("§e[능력 상세]");
        p.sendMessage("§f도넛 모양 연기를 날립니다.");
        p.sendMessage("§f연기는 3초에 걸려 10블럭 날라가며 맞을 경우 5의 데미지를 줍니다.");
        p.sendMessage("§f맞은 대상은 3초간 시야가 좁아집니다(실명).");
        p.sendMessage(" ");
        p.sendMessage("§f능력을 사용할 수록 날라가는 연기가 증가하며");
        p.sendMessage("§f최대 8번까지 연속해서 연기를 발사합니다.");
        p.sendMessage("§f(연속 발사 간격: 0.2초)");

        // 아이템 지급 (확인용)
        giveItem(p);
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p); // 부모 클래스의 cleanup 호출 (소환수, 태스크 등 정리)
        stackMap.remove(p.getUniqueId()); // 스택 정보 제거
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 1. 능력자 확인 및 아이템 확인
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;
        if (e.getItem() == null || e.getItem().getType() != Material.WHITE_WOOL)
            return;
        if (!e.getItem().getItemMeta().getDisplayName().equals("§f전자담배"))
            return;

        // 2. 우클릭 확인
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true); // 양털 설치 방지

            // 3. 쿨타임 확인 (Ability 부모 클래스 메서드 활용)
            if (!checkCooldown(p))
                return;

            // 4. 발사 로직 시작
            // 현재 스택 가져오기 (없으면 1)
            int currentStack = stackMap.getOrDefault(p.getUniqueId(), 1);

            // 쿨타임 설정 (5초)
            setCooldown(p, 5);

            // 채팅 메시지 출력
            MocPlugin.getInstance().getServer().broadcastMessage("§e" + p.getName() + " §f: 담탐 ㄱ?");

            // 연사 루프 시작 (BukkitRunnable)
            new BukkitRunnable() {
                int count = 0;

                @Override
                public void run() {
                    // 플레이어가 오프라인이거나 죽었으면 중단
                    if (!p.isOnline() || p.isDead()) {
                        this.cancel();
                        return;
                    }

                    // 연기 투사체 발사
                    fireSmokeProjectile(p);
                    count++;

                    // 설정된 횟수만큼 발사하면 종료
                    if (count >= currentStack) {
                        this.cancel();

                        // 다음 사용을 위해 스택 증가 (최대 8)
                        if (currentStack < 8) {
                            stackMap.put(p.getUniqueId(), currentStack + 1);
                        }
                    }
                }
            }.runTaskTimer(plugin, 0L, 4L); // 0.2초 = 4 ticks
        }
    }

    /**
     * 도넛 모양의 연기 투사체를 발사합니다.
     */
    private void fireSmokeProjectile(Player p) {
        Location startLoc = p.getEyeLocation();
        Vector direction = startLoc.getDirection().normalize();

        // 투사체 속도: 10블럭 / 3초 ≈ 3.33 blocks/sec
        // 1틱(0.05초)당 이동 거리 = 3.33 * 0.05 ≈ 0.166 blocks
        double speedPerTick = 10.0 / (3.0 * 20.0);

        // 도넛 모양 생성을 위한 기준 벡터 (진행 방향에 수직인 벡터들)
        // 임의의 벡터(Y축)와 외적하여 수직 벡터 하나를 구함
        Vector arbitrary = new Vector(0, 1, 0);
        if (Math.abs(direction.getY()) > 0.9) { // 만약 진행 방향이 수직에 가까우면 X축을 기준
            arbitrary = new Vector(1, 0, 0);
        }

        // right: 진행 방향의 오른쪽 벡터
        Vector right = direction.getCrossProduct(arbitrary).normalize();
        // up: 진행 방향의 위쪽 벡터 (실제 월드의 위쪽이 아니라, 진행 방향 기준 로컬 좌표계의 위쪽)
        Vector up = direction.getCrossProduct(right).normalize();

        // 소리 재생 (연기 뿜는 소리 - 바람 소리 등 활용)
        p.getWorld().playSound(startLoc, Sound.ENTITY_CAT_HISS, 0.5f, 0.5f);

        // 투사체 태스크
        BukkitRunnable projectileTask = new BukkitRunnable() {
            Location currentLoc = startLoc.clone();
            double distanceTraveled = 0;
            final double removeDistance = 30.0; // 안전장치: 너무 멀리 가면 삭제 (10블럭보다 넉넉하게 잡음)
            final double lifeTimeBlocks = 10.0; // 10블럭 이동 후 소멸

            // 도넛 크기: 멀어질수록 커짐 (0.5에서 시작해 3초 뒤 약 1.5~2.0까지?)
            // 기획: "멀어질 수록 점점 커집니다"
            double radius = 0.3;

            @Override
            public void run() {
                // 1. 이동
                currentLoc.add(direction.clone().multiply(speedPerTick));
                distanceTraveled += speedPerTick;

                // 2. 소멸 조건 (거리 도달)
                if (distanceTraveled >= lifeTimeBlocks) {
                    this.cancel();
                    return;
                }

                // 3. 도넛 파티클 그리기
                // 원형으로 파티클 생성
                int particleCount = 20; // 원 하나당 파티클 수

                // 거리에 비례해서 반지름 증가 (0.3 -> 1.5)
                radius = 0.3 + (distanceTraveled / lifeTimeBlocks) * 1.2;

                for (int i = 0; i < particleCount; i++) {
                    double angle = 2 * Math.PI * i / particleCount;
                    double xOffset = radius * Math.cos(angle);
                    double yOffset = radius * Math.sin(angle);

                    // 로컬 좌표(xOffset, yOffset)를 월드 좌표로 변환
                    // pos = center + right * x + up * y
                    Vector offset = right.clone().multiply(xOffset).add(up.clone().multiply(yOffset));
                    Location particleLoc = currentLoc.clone().add(offset);

                    p.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, particleLoc, 0, 0, 0, 0, 1);
                }

                // 4. 충돌 체크 (범위 내 적 확인)
                // 도넛의 크기만큼 반경 체크
                for (Entity entity : currentLoc.getWorld().getNearbyEntities(currentLoc, radius + 0.5, radius + 0.5,
                        radius + 0.5)) {
                    if (entity instanceof LivingEntity target && entity != p) {
                        // 정확한 판정을 위해 거리 체크 한 번 더 (도넛 평면과의 거리 + 중심축과의 거리)
                        // 여기선 간단하게 구체 판정으로 처리하되, 본인은 제외
                        // 0.2초 간격 발사이므로 무적시간(NoDamageTicks)을 무시하거나 줄여야 연타가 들어감
                        // 하지만 일반적인 데미지 처리를 원할 수 있으므로 기본 로직 사용.

                        // 이미 맞은 대상인지 체크하는 로직이 필요할 수 있으나,
                        // 기획서에 "관통"이라고 되어 있으므로 한 번 훑고 지나감.
                        // 같은 투사체에 여러 번 맞지 않도록 리스트 관리 필요?
                        // -> 투사체가 느려서(3초 10블럭) 계속 겹쳐있으면 매 틱마다 데미지 들어갈 수 있음.
                        // -> 따라서 이 투사체에 의해 데미지를 입었는지 추적해야 함.

                        if (!hitEntities.contains(target.getUniqueId())) {
                            // 데미지 5
                            target.damage(5, p);

                            // 실명 3초 (60틱)
                            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));

                            // "시야에 3초간 연기가 떠다녀" -> 실명으로 표현됨.
                            // 추가 효과를 원하면 여기에 파티클 등 추가 가능.

                            hitEntities.add(target.getUniqueId());
                        }
                    }
                }
            }

            // 이 투사체에 이미 피격된 엔티티 목록 (중복 피격 방지)
            Set<UUID> hitEntities = new HashSet<>();
        };

        BukkitTask task = projectileTask.runTaskTimer(plugin, 0L, 1L); // 1틱마다 업데이트

        // 메모리 관리를 위해 부모 클래스의 태스크 리스트에 등록 (선택사항, 투사체가 짧게 끝나서 필수는 아님)
        // 하지만 라운드 종료 시 즉시 삭제되게 하려면 등록하는 게 좋음.
        registerTask(p, task);
    }
}
