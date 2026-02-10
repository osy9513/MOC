package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class GojoSatoru extends Ability {

    // 능력 봉인 대상자를 관리하기 위한 전역 집합 (다른 클래스에서 접근 가능하도록 public static)
    public static final Set<UUID> silencedPlayers = new HashSet<>();

    public GojoSatoru(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "059";
    }

    @Override
    public String getName() {
        return "고죠 사토루";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§b유틸 ● 고죠 사토루(주술회전)",
                "§f영역전개 후 무량공처를 사용합니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 지급 아이템 없음
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§b유틸 ● 고죠 사토루(주술회전)");
        p.sendMessage("§f맨손 쉬프트 좌클릭을 할 경우, 영역전개를 시전합니다.");
        p.sendMessage("§f자신을 기준 16x16x16까지 2초에 걸쳐 점점 커지는");
        p.sendMessage("§f영역에 닿은 상대는 무량공처에 맞습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f무량공처에 맞은 상대는 7초간 행동할 수 없으며,");
        p.sendMessage("§f지속 시간 동안 능력이 봉인됩니다.");
        p.sendMessage("§f이후 6초간 능력이 추가로 봉인됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 19초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
    }

    @Override
    public void reset() {
        silencedPlayers.clear();
        super.reset();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 1. 봉인된 플레이어인지 확인 (전역 봉인 체크)
        if (silencedPlayers.contains(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage("§c능력이 봉인되어 사용할 수 없습니다.");
            return;
        }

        // 2. 능력 보유 확인
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 3. 발동 조건: 맨손 + 쉬프트 + 좌클릭
        if (!p.isSneaking())
            return;
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK)
            return;
        if (p.getInventory().getItemInMainHand().getType() != Material.AIR)
            return;

        // 4. 쿨타임 체크
        if (!checkCooldown(p))
            return;

        // 5. 발동
        startDomainExpansion(p);
    }

    private void startDomainExpansion(Player p) {
        setCooldown(p, 19);

        // 1. 캐스팅 중 움직임 봉인 (2초)
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 5, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, 250, false, false));

        // 대사 출력
        p.getServer().broadcastMessage("§b§l고죠 사토루: 영역전개");
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.5f);
        // [추가] 지옥 포탈 소리 대신 즉발적인 웅장한 소리 (엔드 포탈 생성 소리 + 저음)
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 0.5f);

        // [수정] 내 영역을 나도 볼 수 있게 시야 앞 2칸 위치에서 시작
        Location center = p.getEyeLocation().add(p.getLocation().getDirection().multiply(2.0));

        // ItemDisplay 소환 (검은색 양털 - gojo)
        ItemDisplay domain = p.getWorld().spawn(center, ItemDisplay.class, entity -> {
            ItemStack wool = new ItemStack(Material.BLACK_WOOL);
            ItemMeta meta = wool.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(1); // resource pack: gojo
                wool.setItemMeta(meta);
            }
            entity.setItemStack(wool);
            // 아주 작은 크기에서 시작 (0.1배)
            entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(0.1f, 0.1f, 0.1f),
                    new AxisAngle4f(0, 0, 0, 1)));
            entity.setBillboard(Display.Billboard.FIXED); // 고정
        });

        // 목표 크기 설정
        final float targetScale = 16.0f; // 최종 크기 (지름)
        final int DURATION = 40; // 2초 (40틱)

        // [삭제됨] 원래 여기 있던 1틱 뒤에 한 번에 커지는 코드는 지웠어!

        // 파티클, 피격 판정, 그리고 ★크기 변화★ 태스크
        new BukkitRunnable() {
            int tick = 0;
            final Set<UUID> hitTargets = new HashSet<>();

            @Override
            public void run() {
                // 엔티티가 사라졌거나 시간이 다 되면 종료
                if (!domain.isValid() || tick >= DURATION) {
                    this.cancel();
                    if (domain.isValid()) {
                        domain.remove(); // 영역 제거
                    }
                    // 무량공처 완료 이펙트
                    triggerInfiniteVoidEffect(p, center);
                    return;
                }

                // ★ [여기가 핵심] ★
                // 현재 틱에 맞는 크기를 계산해서 매번 업데이트해줘!
                // tick이 0에서 40으로 갈수록, 크기는 0.1에서 16.0으로 부드럽게 변해.
                float currentScale = (targetScale / (float) DURATION) * tick;
                if (currentScale < 0.1f)
                    currentScale = 0.1f; // 최소 크기 방어

                // 매 틱마다 크기를 재설정 (InterpolationDuration을 1로 주면 틱 사이도 부드럽게 이어짐)
                domain.setInterpolationDuration(1);
                domain.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(currentScale, currentScale, currentScale), // 점점 커지는 크기 적용
                        new AxisAngle4f(0, 0, 0, 1)));

                // --- 아래는 기존 피격 판정 로직 (그대로 유지) ---

                // 현재 반경 계산 (0.1 ~ 8.0) - 시각적 크기(Scale)의 절반이 반지름
                double currentRadius = currentScale / 2.0;

                // 실시간 피격 판정
                for (Entity e : center.getWorld().getNearbyEntities(center, currentRadius, currentRadius,
                        currentRadius)) {
                    if (e instanceof LivingEntity target && e != p && !e.isDead()) {
                        if (target instanceof Player
                                && ((Player) target).getGameMode() == org.bukkit.GameMode.SPECTATOR)
                            continue;
                        if (!hitTargets.contains(target.getUniqueId())) {
                            applyStun(target);
                            hitTargets.add(target.getUniqueId());
                            p.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
                            p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, target.getLocation(), 1);
                        }
                    }
                }

                // 룬 문자 파티클 (반경에 맞춰 생성)
                for (int i = 0; i < 20; i++) {
                    double u = Math.random();
                    double v = Math.random();
                    double theta = 2 * Math.PI * u;
                    double phi = Math.acos(2 * v - 1);
                    double x = currentRadius * Math.sin(phi) * Math.cos(theta);
                    double y = currentRadius * Math.sin(phi) * Math.sin(theta);
                    double z = currentRadius * Math.cos(phi);
                    p.getWorld().spawnParticle(Particle.ENCHANT, center.clone().add(x, y, z), 0, 0, 0, 0, 1);
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void triggerInfiniteVoidEffect(Player caster, Location center) {
        caster.getServer().broadcastMessage("§b§l고죠 사토루: 무량공처");
        // [수정] 중앙 폭발 제거 (타겟 개별 폭발로 변경됨)
    }

    private void applyStun(LivingEntity target) {
        // 7초간 행동 불가
        // 구속 250 (움직임 불가), 점프 부스트 250 (점프 불가), 나약함 (공격 데미지 감소), 채굴 피로 (공격 속도/채굴 감소)
        int duration = 140; // 7초 * 20
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 5, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 255, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, 250, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration, 250, false, false));

        // [추가] 스턴 기간 동안에도 능력 봉인 적용
        if (target instanceof Player p) {
            silencedPlayers.add(p.getUniqueId());
        }

        // 시각 효과: 룬 문자 파티클 흐름 & 채팅 도배
        BukkitTask effectTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (target.isDead() || ticks >= 140) {
                    this.cancel();

                    // 스턴 종료 후 6초간 봉인
                    if (target instanceof Player p) {
                        applySilence(p);
                    } else {
                        // [추가] AI 복구
                        target.setAI(true);
                    }
                    return;
                }

                // 파티클: 머리 위에서 아래로
                Location loc = target.getLocation().add(0, 2.5, 0);
                target.getWorld().spawnParticle(Particle.ENCHANT, loc, 10, 0.5, 0.5, 0.5, 1);

                // 채팅 도배 (0.2초마다 = 4틱마다)
                if (ticks % 4 == 0 && target instanceof Player p) {
                    p.sendMessage("§k" + generateRandomString());
                }

                ticks += 2;
            }

            private String generateRandomString() {
                // 적당히 긴 난수 문자열
                return "무량공처무량공처무량공처무량공처";
            }
        }.runTaskTimer(plugin, 0L, 2L);

        if (target instanceof

        Player p) {
            silencedPlayers.add(p.getUniqueId());
            registerTask(p, effectTask);
        } else {
            // [추가] 플레이어가 아닌 엔티티(주민, 몹 등)는 AI를 비활성화하여 완벽하게 정지시킴
            target.setAI(false);

            // AI 복구 태스크 (effectTask 내에서 처리하지 않고 별도로 스케줄링하거나 effectTask가 끝날 때 처리)
            // 위 effectTask는 Player 대상에게만 registerTask로 등록되었으므로,
            // 비-플레이어 엔티티를 위한 별도의 cleanup 로직이 필요함.
            // 하지만 effectTask 자체는 runTaskTimer로 돌아가므로, 내부에서 AI 복구하면 됨.
        }
    }

    private String Code() {
        return getCode();
    }

    private void applySilence(Player p) {
        silencedPlayers.add(p.getUniqueId());
        p.sendMessage("§c능력이 봉인되었습니다.");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    silencedPlayers.remove(p.getUniqueId());
                    p.sendMessage("§a능력 봉인이 해제되었습니다.");
                } else {
                    silencedPlayers.remove(p.getUniqueId());
                }
            }
        }.runTaskLater(plugin, 120L); // 6초
    }
}
