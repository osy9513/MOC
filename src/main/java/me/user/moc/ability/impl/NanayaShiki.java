package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.List;

public class NanayaShiki extends Ability {

    public NanayaShiki(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "나나야 시키";
    }

    @Override
    public String getCode() {
        return "035";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c전투 ● §f나나야 시키 (월희)",
                "§f극사 나나야를 사용합니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 나나야 시키 전용 단검(철검) 지급
        p.getInventory().remove(Material.IRON_SWORD);
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b나나츠요루");
            meta.setUnbreakable(true);
            sword.setItemMeta(meta);
        }
        p.getInventory().addItem(sword);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 1. 능력 보유 여부 확인
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 2. 액션 확인 (우클릭만 허용)
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // 3. 아이템 확인 (나나츠요루인지)
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.IRON_SWORD || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!"§b나나츠요루".equals(meta.getDisplayName())) {
            return;
        }

        // [수정됨] 전투 시작 여부 체크 (GameManager가 존재할 때만 체크, 테스트 시 무시)
        if (MocPlugin.getInstance().getGameManager() != null
                && !MocPlugin.getInstance().getGameManager().isBattleStarted()) {
            return;
        }

        // 4. 쿨타임 체크 및 발동
        if (checkCooldown(p)) {
            setCooldown(p, 18);
            fireKnife(p);
        }
    }

    private void fireKnife(Player p) {
        // 칼 투척 (Arrow)
        final Arrow arrow = p.launchProjectile(Arrow.class);
        arrow.setVelocity(p.getLocation().getDirection().multiply(4.0)); // 속도 상향 (더욱 빠름)
        arrow.setShooter(p);
        arrow.setMetadata("NanayaKnife", new FixedMetadataValue(plugin, true));
        arrow.setSilent(true); // 소리 제거

        // 시각 효과: 칼(ItemDisplay)이 화살을 타고 날아감
        ItemDisplay visual = (ItemDisplay) p.getWorld().spawnEntity(p.getEyeLocation(), EntityType.ITEM_DISPLAY);
        visual.setItemStack(new ItemStack(Material.IRON_SWORD));

        arrow.addPassenger(visual);
        p.playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1f);

        // [추가] 사거리(15칸) 제한 로직
        final org.bukkit.Location startLoc = p.getLocation();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (arrow.isDead() || !arrow.isValid()) {
                    cancel();
                    return;
                }
                // 발사 지점으로부터 15칸 이상 멀어지면 제거
                if (arrow.getLocation().distance(startLoc) > 15) {
                    if (!arrow.getPassengers().isEmpty()) {
                        arrow.getPassengers().forEach(org.bukkit.entity.Entity::remove);
                    }
                    arrow.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (e.getEntity() instanceof Arrow arrow && arrow.hasMetadata("NanayaKnife")) {

            // 시각 효과(ItemDisplay) 제거
            if (!arrow.getPassengers().isEmpty()) {
                arrow.getPassengers().forEach(Entity::remove);
            }

            if (e.getHitEntity() instanceof LivingEntity target) {
                Player shooter = (Player) arrow.getShooter();
                if (shooter == null)
                    return;

                // 본인이 맞은 경우 무시
                if (target.equals(shooter))
                    return;

                // 나나야 시키 : 극사 나나야!
                Bukkit.broadcastMessage("§b나나야 시키 : §c극사 나나야!");

                // 순간이동 (대상 위 2.5칸) - 머리 위로 이동
                // 시선 처리는 유지 (shooter.getLocation().getYaw/Pitch 사용 가능하지만 일단 그대로 둠)
                shooter.teleport(target.getLocation().add(0, 2.5, 0));

                // 대미지 45 (하트 22.5칸)
                target.damage(45.0, shooter);

                // 효과
                shooter.playSound(shooter.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 20);
            }

            // 화살 제거
            arrow.remove();
        }
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 나나야 시키(월희)");
        p.sendMessage("§f극사 나나야를 사용합니다.");
        p.sendMessage("§f나나츠요루 우클릭 시 칼을 던집니다(사거리 15).");
        p.sendMessage("§f적중 시 대상 위로 순간이동하며 45의 치명적인 대미지를 입힙니다.");
        p.sendMessage("§f[패시브] 나나야 체술: 공격 시 25% 확률로 5의 추가 고정 피해를 입힙니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 18초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 나나츠요루");
        p.sendMessage("§f장비 제거 : 철 칼");
    }

    @EventHandler
    public void onAttack(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker))
            return;
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(attacker, getCode()))
            return;

        // [추가] 전투 시작 전 발동 금지
        if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        if (!(e.getEntity() instanceof LivingEntity target))
            return;

        // 25% 확률로 나나야 체술 발동
        if (Math.random() < 0.25) {
            e.setDamage(e.getDamage() + 5.0); // 5 데미지 추가
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.5f);
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 15, 0.2, 0.2, 0.2, 0.1);
            attacker.sendActionBar(net.kyori.adventure.text.Component.text("§c[나나야 체술] §f치명적인 일격!"));
        }
    }
}