package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.*;
import org.bukkit.entity.*;
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
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class GojoSatoru extends Ability {

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
        p.sendMessage("§f무량공처에 맞은 상대는 3초간 행동할 수 없으며,");
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
        // AbilityManager에서 전역적으로 처리하므로 여기서 별도 clear 불필요 (부모 reset 호출로 충분)
        super.reset();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 1. 봉인된 플레이어인지 확인 (전역 봉인 체크 - Ability.checkCooldown에서 수행하므로 여기선 중복 체크 제거 가능하지만
        // 명시적으로 남김)
        if (AbilityManager.silencedPlayers.contains(p.getUniqueId())) {
            // checkCooldown에서 메시지를 띄워주겠지만, 상호작용 이벤트 자체를 여기서 먼저 취소
            e.setCancelled(true);
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

        // 4. 쿨타임 체크 (이제 여기서 봉인 체크도 함께 수행됨)
        if (!checkCooldown(p))
            return;

        // 5. 발동
        startDomainExpansion(p);
    }

    private void startDomainExpansion(Player p) {
        setCooldown(p, 19);

        // 1. 캐스팅 중 움직임 봉인 (2초)
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 5, true, true, true));
        applyJumpSilence(p, 40);

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

        // 파티클, 피격 판정, 그리고 ★크기 변화★ 태스크
        new BukkitRunnable() {
            int tick = 0;
            final Set<UUID> hitTargets = new HashSet<>();
            final float targetScale = 16.0f; // 최종 크기 (지름)
            final int DURATION = 40; // 2초 (40틱)

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

                float currentScale = (targetScale / (float) DURATION) * tick;
                if (currentScale < 0.1f)
                    currentScale = 0.1f; // 최소 크기 방어

                domain.setInterpolationDuration(1);
                domain.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(currentScale, currentScale, currentScale), // 점점 커지는 크기 적용
                        new AxisAngle4f(0, 0, 0, 1)));

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
                            p.getWorld().playSound(target.getLocation(), Sound.BLOCK_STONE_STEP, 1.0f,
                                    0.5f);
                            p.getWorld().spawnParticle(Particle.ENCHANTED_HIT, target.getLocation(), 10, 0.2, 0.2, 0.2,
                                    0.1);
                        }
                    }
                }

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
    }

    private void applyStun(LivingEntity target) {
        int duration = 60; // 3초 * 20
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 5, true, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, 250, true, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration, 250, true, true, true));

        // [수정] 스턴 기간 동안 전역 봉인 및 점프 봉인 적용
        if (target instanceof Player p) {
            AbilityManager.silencedPlayers.add(p.getUniqueId());
            applyJumpSilence(p, duration);
        }

        BukkitTask effectTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (target.isDead() || ticks >= 60) {
                    this.cancel();

                    // 스턴 종료 후 6초간 봉인 유지
                    if (target instanceof Player p) {
                        applySilence(p);
                    } else {
                        target.setAI(true);
                    }
                    return;
                }

                Location loc = target.getLocation().add(0, 2.5, 0);
                target.getWorld().spawnParticle(Particle.ENCHANT, loc, 10, 0.5, 0.5, 0.5, 1);

                if (ticks % 4 == 0 && target instanceof Player p) {
                    p.sendMessage("§k" + generateRandomString());
                }

                ticks += 2;
            }

            private String generateRandomString() {
                return "무량공처무량공처무량공처무량공처";
            }
        }.runTaskTimer(plugin, 0L, 2L);

        if (target instanceof Player p) {
            registerTask(p, effectTask);
        } else {
            target.setAI(false);
        }
    }

    private void applySilence(Player p) {
        // [수정] AbilityManager의 전역 봉인 목록 사용
        AbilityManager.silencedPlayers.add(p.getUniqueId());
        p.sendMessage("§c능력이 봉인되었습니다.");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    AbilityManager.silencedPlayers.remove(p.getUniqueId());
                    p.sendMessage("§a능력 봉인이 해제되었습니다.");
                } else {
                    AbilityManager.silencedPlayers.remove(p.getUniqueId());
                }
            }
        }.runTaskLater(plugin, 120L); // 6초
    }
}
