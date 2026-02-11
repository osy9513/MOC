package me.user.moc.ability.impl;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import me.user.moc.ability.Ability;

public class PaulPhoenix extends Ability {
    private final java.util.Set<java.util.UUID> isDamaging = new java.util.HashSet<>();

    public PaulPhoenix(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "017";
    }

    @Override
    public String getName() {
        return "폴 피닉스";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§c전투 ● 폴 피닉스(철권)",
                "§f맨손으로 §c[Shift] + 좌클릭§f 시 제자리에서 강력한 붕권을 지릅니다.",
                "§f전방에 §c파괴적인 충격파§f를 발생시켜 적을 붕괴시킵니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 폴 피닉스(철권)");
        p.sendMessage("§f맨손 상태에서 웅크리기(Shift) + 좌클릭 시");
        p.sendMessage("§f전방으로 거대한 에너지를 방출하여 공간을 타격합니다.");
        p.sendMessage("§f적중 시 대상은 1초간 그로기 상태가 되며");
        p.sendMessage("§f주변의 적들은 충격파에 밀려나게 됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 8초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void giveItem(Player p) {
        // 폴 피닉스는 맨손 격투가이므로 기본 지급된 철검 제거
        p.getInventory().remove(org.bukkit.Material.IRON_SWORD);
        // 상시 힘 1 버프
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 0, true, true));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 1. 내 능력이 맞는지 확인
        if (!me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode())) {
            return;
        }

        // [추가] 전투 시작 전 사용 금지
        if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        // 2. 발동 조건: 좌클릭 + 쉬프트 + 맨손 + 메인 핸드
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK)
            return;
        if (!p.isSneaking())
            return;
        if (e.getHand() != EquipmentSlot.HAND)
            return;
        if (!p.getInventory().getItemInMainHand().getType().isAir())
            return;

        // 3. 쿨타임 및 게임 상태 체크
        if (!checkCooldown(p))
            return;

        // 4. 스킬 실행
        useSkill(p, null);
        setCooldown(p, 8); // 8초 쿨타임
    }

    @EventHandler
    public void onHit(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p))
            return;

        // [추가] 전투 시작 전 사용 금지
        if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        // [Loop Guard] 스킬로 인한 데미지면 이벤트 처리 건너뛰기
        if (isDamaging.contains(p.getUniqueId()))
            return;

        // 1. 내 능력이 맞는지 확인
        if (!me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode())) {
            return;
        }

        // 2. 발동 조건: 쉬프트 + 맨손
        if (!p.isSneaking())
            return;
        if (!p.getInventory().getItemInMainHand().getType().isAir())
            return;

        // 3. 쿨타임 및 게임 상태 체크
        if (!checkCooldown(p))
            return;

        // 4. 스킬 실행 (평타 데미지 무효화 후 스킬 발동)
        e.setCancelled(true);
        if (e.getEntity() instanceof LivingEntity target) {
            useSkill(p, target);
        } else {
            useSkill(p, null);
        }
        setCooldown(p, 8);
    }

    private void useSkill(Player p, LivingEntity forcedTarget) {
        World w = p.getWorld();
        Location startLoc = p.getEyeLocation();
        Vector direction = startLoc.getDirection().normalize();

        // A. 기합 소리
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.6f);
        Bukkit.broadcastMessage("§c폴 피닉스 : 뚜~아-!");

        // B. 화면 반동 (Recoil) 제거 요청으로 삭제됨.
        // p.setVelocity(...) 삭제

        // C. 시각적 연출 (Shockwave Visuals)
        // C. 시각적 연출 (Shockwave Visuals) - [Safe Mode]
        for (double d = 0.5; d <= 4.5; d += 0.5) {
            Location loc = startLoc.clone().add(direction.clone().multiply(d));

            // 안전한 파티클 사용 (Color 데이터 요구 없음)
            w.spawnParticle(Particle.POOF, loc, 2, 0.2, 0.2, 0.2, 0.05);
            w.spawnParticle(Particle.CRIT, loc, 5, 0.2, 0.2, 0.2, 0.1);

            if (d >= 4.0) {
                // 끝부분 강조
                w.spawnParticle(Particle.POOF, loc, 10, 0.5, 0.5, 0.5, 0.1);
                w.spawnParticle(Particle.FLAME, loc, 5, 0.2, 0.2, 0.2, 0.05);
            }
        }

        Location impactLoc = startLoc.clone().add(direction.clone().multiply(3.0));
        w.spawnParticle(Particle.POOF, impactLoc, 30, 1.5, 0.5, 1.5, 0.05); // Safe Cloud alternative
        w.spawnParticle(Particle.FLAME, impactLoc, 20, 1.0, 1.0, 1.0, 0.2);

        // D. 타겟 선정 (직접 타격 또는 RayTrace)
        LivingEntity target = forcedTarget;

        // 타겟이 없으면 RayTrace로 탐색 (범위 너비 1.0으로 넉넉하게)
        if (target == null) {
            RayTraceResult result = w.rayTraceEntities(startLoc, direction, 4.5, 1.0,
                    entity -> entity instanceof LivingEntity && !entity.getUniqueId().equals(p.getUniqueId())
                            && !(entity instanceof Player
                                    && ((Player) entity).getGameMode() == org.bukkit.GameMode.SPECTATOR));
            if (result != null && result.getHitEntity() instanceof LivingEntity hit) {
                target = hit;
            }
        }

        if (target != null) {
            applyHitEffect(p, target);
        } else {
            // 허공 타격 시 효과음
            w.playSound(impactLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.7f);
            w.playSound(impactLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.5f);
        }
    }

    private void applyHitEffect(Player attacker, LivingEntity target) {
        // 1. 데미지 16.0 (하트 8칸)
        isDamaging.add(attacker.getUniqueId());
        try {
            target.setNoDamageTicks(0);
            target.damage(16.0, attacker);
        } finally {
            isDamaging.remove(attacker.getUniqueId());
        }

        // 2. 그로기 (CC) - 구속, 점프 불가
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 5, true, true));
        if (target instanceof Player p) {
            applyJumpSilence(p, 20);
        }

        // 3. 적중 시 소리 (타격감 극대화)
        World w = target.getWorld();
        Location hitLoc = target.getLocation().add(0, 1, 0);

        w.playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 2.0f, 0.6f);
        w.playSound(hitLoc, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f); // 묵직한 쇠소리
        w.playSound(hitLoc, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.5f, 0.5f); // 우지끈

        // 4. 충격파 (Shockwave AoE)
        // 타겟 주변 3블록 내 적들에게 넉백
        for (Entity e : target.getNearbyEntities(3, 3, 3)) {
            if (e instanceof LivingEntity nearby && !e.equals(attacker) && !e.equals(target)) {
                if (nearby instanceof Player && ((Player) nearby).getGameMode() == org.bukkit.GameMode.SPECTATOR)
                    continue;

                // 중심에서 바깥으로 밀어내기 (넉백 감소)
                Vector knockback = e.getLocation().toVector().subtract(target.getLocation().toVector()).normalize()
                        .multiply(0.5).setY(0.2); // [Fix] 넉백 0.5배, Y축 0.2로 감소
                if (Double.isNaN(knockback.getX()))
                    knockback = new Vector(0, 0.2, 0); // 겹쳐있을 경우 위로

                nearby.setVelocity(knockback);
                // nearby.sendMessage("§c강렬한 충격파에 밀려났습니다!");
            }
        }
    }
}
