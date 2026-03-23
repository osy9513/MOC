package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.List;

public class Doraemon extends Ability {

    public Doraemon(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "082";
    }

    @Override
    public String getName() {
        return "도라에몽";
    }

    @Override
    public void giveItem(Player p) {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f빅 라이트");
            meta.setLore(Arrays.asList(
                    "§f빅 라이트로 상대를 가격 시 대상의 크기를 15% 키웁니다.",
                    "§7누구나 획득 시 사용 가능한 능력 아이템입니다."
            ));
            meta.setCustomModelData(18); // 리소스팩: biglight
            item.setItemMeta(meta);
        }
        p.getInventory().addItem(item);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 도라에몽(도라에몽)");
        p.sendMessage("§f빅 라이트로 상대(동물, 몬스터 포함)를 가격 시 15% 커지게 만듭니다.");
        p.sendMessage("§f빅 라이트는 꼭 도라에몽이 아니어도 모든 플레이어가 사용할 수 있습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 1.5초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 빅 라이트");
        p.sendMessage("§f장비 제거 : 철 검");
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c전투 ● 도라에몽(도라에몽)",
                "§f빅라이트를 사용합니다."
        );
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;

        // 침묵 상태 확인
        if (isSilenced(attacker)) return;

        // 공격자가 들고 있는 아이템이 빅 라이트(철검, CustomModelData 18)인지 검사
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (hand.getType() != Material.IRON_SWORD || !hand.hasItemMeta()) return;
        ItemMeta meta = hand.getItemMeta();
        if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != 18) return;

        // 타겟이 생명체(LivingEntity)여야 크기 증폭이 가능함
        if (!(e.getEntity() instanceof LivingEntity target)) return;
        if (target instanceof Player t && t.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;

        // 쿨타임 1.5초 (크리에이티브는 기반 클래스의 checkCooldown에서 무시 처리됨)
        if (!checkCooldown(attacker)) return;
        setCooldown(attacker, 1.5);

        // 도라에몽 대사 출력
        Bukkit.broadcastMessage("§c도라에몽 : §f빅 라이트~!");

        // 크기 증폭 (15%)
        AttributeInstance scale = null;
        try {
            scale = target.getAttribute(Attribute.valueOf("GENERIC_SCALE"));
        } catch (IllegalArgumentException ex) {
            // 버전 호환성을 위한 예외 처리
        }
        if (scale != null) {
            double currentScale = scale.getBaseValue();
            scale.setBaseValue(currentScale * 1.15);
        }

        // 1.5초간(30틱) 이펙트 및 사운드 효과
        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (target.isDead() || !target.isValid() || ticks >= 30) {
                    this.cancel();
                    return;
                }
                
                // 파티클
                target.getWorld().spawnParticle(
                        Particle.HAPPY_VILLAGER, 
                        target.getLocation().add(0, target.getHeight() / 2, 0),
                        5, 0.5, 0.5, 0.5, 0.05
                );
                
                // 슉슉슉 자라는 듯한 사운드 (10틱 마다 재생)
                if (ticks % 10 == 0) {
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
                }
                ticks += 5;
            }
        };

        BukkitTask bukkitTask = task.runTaskTimer(plugin, 0L, 5L);
        List<org.bukkit.scheduler.BukkitTask> taskList = activeTasks.computeIfAbsent(attacker.getUniqueId(), k -> new java.util.ArrayList<>());
        taskList.add(bukkitTask);
    }
}
