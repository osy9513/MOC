package me.user.moc.ability.impl;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fox;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import me.user.moc.ability.Ability;
import net.kyori.adventure.text.Component;

public class FoxDevil extends Ability {

    public FoxDevil(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "042";
    }

    @Override
    public String getName() {
        return "여우의 악마";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c전투 ● 여우의 악마(체인소맨)",
                "§f여우의 악마와 계약해 콩을 사용합니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 기본 지급 아이템 없음
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 여우의 악마(체인소맨)");
        p.sendMessage("§f여우의 악마와 계약해 콩을 사용합니다.");
        p.sendMessage(" ");
        p.sendMessage("§f블럭을 바라보며 맨손으로 좌클릭 + 쉬프트를 동시에 눌러 체력을 3칸을 소모하여");
        p.sendMessage("§f해당 블럭에(100블럭 제한) 여우의 악마를 소환해 5*5*5 범위 내 생명체에게 13 데미지를 줌.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 8초.");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 0. 왼손(OffHand) 이벤트 무시
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND)
            return;

        // 1. 능력자 확인
        if (!hasAbility(p))
            return;

        // [변경] 발동 조건: 맨손 + 쉬프트 + 좌클릭

        // 2. 액션 및 상태 확인
        // - 쉬프트 중인가?
        // - 좌클릭인가? (허공 or 블럭)
        // - 맨손인가?
        if (!p.isSneaking())
            return;

        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK)
            return;

        if (!p.getInventory().getItemInMainHand().getType().isAir())
            return;

        // 3. 타겟팅 (사거리 100)
        // FluidCollisionMode.NEVER: 물/용암 무시하고 고체 블록만 찾음
        Block b = p.getTargetBlockExact(100, org.bukkit.FluidCollisionMode.NEVER);

        // 블록을 찾지 못했거나(너무 멀거나 허공), 공기라면 발동 실패
        if (b == null || b.getType() == Material.AIR) {
            p.sendMessage("§c거리가 너무 멀거나 바라보는 곳에 블록이 없습니다. (최대 100칸)");
            return;
        }

        // 4. 쿨타임 확인
        if (!checkCooldown(p))
            return;

        // 5. 발동 (쿨타임, 체력 소모, 메시지)
        setCooldown(p, 8);

        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            // [안전장치] 체력이 6 이하면 죽을 수도 있음. (3칸 = 6)
            if (p.getHealth() > 6.0) {
                p.setHealth(p.getHealth() - 6.0);
            } else {
                p.setHealth(0); // 사망 처리
            }
        }

        Bukkit.broadcast(Component.text("여우의 악마 계약자 : 콩!"));

        // 6. 여우 소환 로직 (나머지는 기존과 동일)
        Location targetLoc = b.getLocation();
        Location summonLoc = targetLoc.clone().add(0.5, -2.0, 0.5);

        Fox fox = (Fox) p.getWorld().spawnEntity(summonLoc, EntityType.FOX);
        fox.setAI(false);
        fox.setInvulnerable(true);
        fox.setSilent(true);
        fox.setAgeLock(true);
        fox.setAdult();
        fox.setCollidable(false);
        fox.setSitting(true);

        var scaleAttr = fox.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(4.0);
        }

        summonLoc.setYaw(p.getLocation().getYaw());
        summonLoc.setPitch(-90f);
        fox.teleport(summonLoc);

        activeEntities.computeIfAbsent(p.getUniqueId(), k -> new java.util.ArrayList<>()).add(fox);

        p.getWorld().playSound(summonLoc, Sound.ENTITY_POLAR_BEAR_DEATH, 1f, 1f);
        p.getWorld().spawnParticle(Particle.FLAME, summonLoc.clone().add(0, 2, 0), 15, 1, 1, 1, 0.05);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!fox.isValid()) {
                    this.cancel();
                    return;
                }

                Location upLoc = targetLoc.clone().add(0.5, -0.5, 0.5);
                upLoc.setYaw(summonLoc.getYaw());
                upLoc.setPitch(-90f);
                fox.teleport(upLoc);

                p.getWorld().spawnParticle(Particle.FLAME, upLoc, 30, 2, 2, 2, 0.1);

                for (Entity ent : p.getWorld().getNearbyEntities(targetLoc, 2.5, 2.5, 2.5)) {
                    if (ent instanceof LivingEntity le) {
                        if (le == fox)
                            continue;
                        try {
                            le.damage(13.0, p);
                        } catch (Exception e) {
                            le.damage(13.0);
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 6L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (fox.isValid()) {
                    p.getWorld().spawnParticle(Particle.FLAME, fox.getLocation().add(0, 1, 0), 50, 0.5, 1.5, 0.5, 0.05);
                    fox.remove();

                    List<Entity> myEntities = activeEntities.get(p.getUniqueId());
                    if (myEntities != null) {
                        myEntities.remove(fox);
                    }
                }
            }
        }.runTaskLater(plugin, 42L);
    }

    // [Fix] 여우 무적 보장 (질식사 방지)
    @EventHandler
    public void onFoxDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e.getEntity() instanceof Fox fox) {
            // 이 플러그인이 소환한 여우인지 확인하기 위해 activeEntities 뒤져봄
            // 성능 최적화를 위해 태그 사용 권장되나 현 구조상 loop
            for (List<Entity> list : activeEntities.values()) {
                if (list.contains(fox)) {
                    e.setCancelled(true); // 절대 무적
                    return;
                }
            }
        }
    }

    private boolean hasAbility(Player p) {
        return me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode());
    }
}
