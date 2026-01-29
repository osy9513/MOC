package me.user.moc.ability.impl;

import java.util.List;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;

public class Zenitsu extends Ability {

    public Zenitsu(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "021";
    }

    @Override
    public String getName() {
        return "아가츠마 젠이츠";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§e전투 ● 아가츠마 젠이츠(귀멸의 칼날)",
                "§e벽력일섬(霹靂一閃)§f을 시전합니다.");
    }

    @Override
    public void giveItem(Player p) {
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● 아가츠마 젠이츠(귀멸의 칼날)");
        p.sendMessage("§f검을 우클릭하면 '벽력일섬'을 시전하여 전방으로 8칸 순간이동합니다.");
        p.sendMessage("§f경로상의 적에게 8의 고정 피해를 입힙니다.");
        p.sendMessage("§f시전 직후 짧은 시간 동안 신속 버프를 얻습니다.");
        p.sendMessage(" ");
        p.sendMessage("§7쿨타임 : 8초");
        p.sendMessage("---");
        p.sendMessage("§7추가 장비 : 없음");
        p.sendMessage("§7장비 제거 : 없음");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 1. 내 능력인지 확인
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 2. 우클릭 & 검 확인
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack hand = e.getItem();
        if (hand == null || !hand.getType().name().endsWith("_SWORD"))
            return;

        // 3. 쿨타임 확인
        if (!checkCooldown(p))
            return;

        // 4. 스킬 시전
        useThunderclapAndFlash(p);
    }

    private void useThunderclapAndFlash(Player p) {
        // 벽력일섬 기술의 재사용 대기시간(쿨타임)을 8초로 설정합니다.
        setCooldown(p, 8);

        // 시전 메시지
        p.getServer().broadcastMessage("§e아가츠마 젠이츠 : 벽력일섬(霹靂一閃).");

        // 사운드
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 1.2f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);

        Location startLoc = p.getLocation();
        Vector dir = startLoc.getDirection().normalize();
        // 이 변수는 젠이츠가 '벽력일섬' 기술을 사용했을 때 한 번에 이동할 수 있는 최대 거리를 결정합니다.
        // 숫자가 클수록 더 먼 거리를 순식간에 이동하게 됩니다. (현재 설정값: 8블록)
        double maxDistance = 8.0;

        // 벽 통과 방지 RayTrace
        RayTraceResult result = p.getWorld().rayTraceBlocks(
                p.getEyeLocation(),
                dir,
                maxDistance,
                FluidCollisionMode.NEVER,
                true);

        Location targetLoc;
        if (result != null && result.getHitBlock() != null) {
            // 벽에 부딪히면 벽 바로 앞 (0.5블록 뒤)
            Vector hitPos = result.getHitPosition();
            // 벽에서 살짝 뒤로
            targetLoc = hitPos.toLocation(p.getWorld()).subtract(dir.clone().multiply(0.5));
            targetLoc.setYaw(startLoc.getYaw());
            targetLoc.setPitch(startLoc.getPitch());
        } else {
            // 벽이 없으면 최대 거리
            targetLoc = startLoc.clone().add(dir.clone().multiply(maxDistance));
        }

        // 텔레포트
        p.teleport(targetLoc);

        // 순간이동 완료 후 원래 위치에 번개 소환
        p.getWorld().strikeLightning(startLoc);

        // 파티클 (경로를 따라 생성)
        double distance = startLoc.distance(targetLoc);
        Vector step = dir.clone().multiply(0.5); // 0.5 간격으로 파티클
        Location current = startLoc.clone().add(0, 1, 0); // 몸통 높이 보정

        for (double d = 0; d < distance; d += 0.5) {
            current.add(step);
            // 노란색 번개 느낌 더스트
            p.getWorld().spawnParticle(Particle.DUST, current, 3, 0.2, 0.2, 0.2,
                    new Particle.DustOptions(Color.YELLOW, 1.5f));
            // 흰색 섞기
            p.getWorld().spawnParticle(Particle.DUST, current, 1, 0.1, 0.1, 0.1,
                    new Particle.DustOptions(Color.WHITE, 1.0f));
        }

        // 피해 입히기 (경로상의 BoundingBox)
        // 너비 3블록, 높이 2블록 정도의 박스를 경로(Line)를 따라 검사하거나,
        // 전체 시작점과 끝점을 포함하는 큰 박스를 만든 뒤 벡터 거리를 계산하는 방식 사용.
        // 여기서는 간단하게 BoundingBox.of(start, end).expand(1.5) 사용 (직사각형 범위)
        BoundingBox box = BoundingBox.of(startLoc, targetLoc).expand(1.5, 2.0, 1.5);

        for (Entity entity : p.getWorld().getNearbyEntities(box)) {
            if (entity == p)
                continue; // 자신 제외
            if (!(entity instanceof LivingEntity))
                continue;

            LivingEntity target = (LivingEntity) entity;
            // 정확한 판정을 위해 벡터 거리 계산 (원통형 범위 체크)
            // 선분(start->end)과 점(entity) 사이의 거리가 2.0 이내인 경우만 피격
            // 하지만 간단하게 박스 범위 내면 맞는 것으로 처리해도 무방 (Minecraft 특성상)
            // 사용자 요청: "이동 경로(너비 3블록...)" -> 박스 판정이 적절함.

            target.damage(8.0, p); // 4칸 = 8 damage
            // 타격감 효과
            p.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10);
        }

        // 특수 효과: 신속 3 (2초) -> Speed 3 = Amplifier 2
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, 0));
    }
}
