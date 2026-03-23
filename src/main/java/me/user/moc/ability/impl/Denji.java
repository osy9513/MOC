package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.List;

public class Denji extends Ability {

    public Denji(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "083";
    }

    @Override
    public String getName() {
        return "덴지";
    }

    @Override
    public void giveItem(Player p) {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f포치타");
            meta.setLore(Arrays.asList(
                    "§f포치타로 상대를 공격 시 1초에 걸쳐 1 데미지씩 5번 무적을 무시하고 타격합니다.",
                    "§f사망 시 인벤토리에 기본 재생 포션이 있다면",
                    "§f체력 30으로 즉시 부활합니다."
            ));
            meta.setCustomModelData(19); // 리소스팩: pochita
            item.setItemMeta(meta);
        }
        p.getInventory().addItem(item);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c복합 ● 덴지(체인소 맨)");
        p.sendMessage("§f포치타로 공격 시 1초에 걸쳐 1 데미지 씩 5번, 피격 무적을 무시한 채 가격합니다.");
        p.sendMessage("§f사망 시 인벤토리에 기본으로 지급하는 재생 포션이 있다면");
        p.sendMessage("§f체력 30(1줄 반)으로 부활합니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초.");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 포치타");
        p.sendMessage("§f장비 제거 : 철 검");
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c복합 ● 덴지(체인소 맨)",
                "§f포치타와 계약합니다."
        );
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        // 부활로 늘어난 최대 체력 원상 복구
        AttributeInstance maxHp = null;
        try {
            maxHp = p.getAttribute(Attribute.valueOf("GENERIC_MAX_HEALTH"));
        } catch (IllegalArgumentException ex) {}
        if (maxHp != null) {
            maxHp.setBaseValue(20.0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!AbilityManager.getInstance().hasAbility(p, getCode())) return;
        if (isSilenced(p)) return;

        // 재귀 타격 루프 방지
        if (e.getEntity().hasMetadata("pochita_saw")) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != Material.IRON_SWORD || !hand.hasItemMeta()) return;
        ItemMeta meta = hand.getItemMeta();
        if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != 19) return;

        if (!(e.getEntity() instanceof LivingEntity target)) return;
        if (target instanceof Player t && t.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;

        // 쿨타임 없음. 1초에 걸쳐 5연격 추가 (0.2초 = 4틱 간격)
        BukkitRunnable task = new BukkitRunnable() {
            int hits = 0;
            @Override
            public void run() {
                if (target.isDead() || !target.isValid() || !p.isOnline() || p.isDead() || hits >= 5) {
                    this.cancel();
                    return;
                }

                // 타수 늘리기: 무적 무시 1데미지 고정
                target.setNoDamageTicks(0);
                target.setMetadata("pochita_saw", new FixedMetadataValue(plugin, true));

                double health = target.getHealth();
                double newHealth = health - 1.0;
                
                if (newHealth <= 0) {
                    // 데미지로 인한 킬 어트리뷰션 처리
                    target.setMetadata("MOC_LastKiller", new FixedMetadataValue(plugin, p.getUniqueId().toString()));
                    target.setHealth(0); // 자연스런 PlayerDeathEvent 유발
                } else {
                    target.setHealth(newHealth);
                    // 피격 연출
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
                    target.getWorld().spawnParticle(
                            org.bukkit.Particle.DAMAGE_INDICATOR,
                            target.getLocation().add(0, 1, 0),
                            3, 0.2, 0.2, 0.2, 0.1
                    );
                }

                target.removeMetadata("pochita_saw", plugin);
                
                // 엔진 갈리는 소리 추가
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.8f, 1.5f);

                hits++;
            }
        };

        BukkitTask bukkitTask = task.runTaskTimer(plugin, 2L, 4L); // 2틱 뒤부터 4틱 간격으로 실행
        List<BukkitTask> taskList = activeTasks.computeIfAbsent(p.getUniqueId(), k -> new java.util.ArrayList<>());
        taskList.add(bukkitTask);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent e) {
        if (e.isCancelled()) return;
        if (!(e.getEntity() instanceof Player p)) return;
        if (!AbilityManager.getInstance().hasAbility(p, getCode())) return;
        
        // 데미지를 받아 체력이 0 이하가 될 때 (즉, 사망할 때)
        if (p.getHealth() - e.getFinalDamage() <= 0) {
            // 인벤토리에 기본 재생 포션(CustomModelData 2)이 있는지 검사
            boolean hasPotion = false;
            ItemStack[] contents = p.getInventory().getContents();
            for (ItemStack item : contents) {
                if (item != null && item.getType() == Material.POTION) {
                    // MOC 기본 지급 재생 포션 검사
                    if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData() && item.getItemMeta().getCustomModelData() == 2) {
                        item.setAmount(item.getAmount() - 1);
                        hasPotion = true;
                        break;
                    }
                }
            }

            if (hasPotion) {
                // 사망 무효화 (부활)
                e.setCancelled(true);
                p.setFireTicks(0);
                
                // 최대 체력 30(1줄 반)으로 확장 및 즉시 회복
                AttributeInstance maxHp = null;
                try {
                    maxHp = p.getAttribute(Attribute.valueOf("GENERIC_MAX_HEALTH"));
                } catch (IllegalArgumentException ex) {}
                if (maxHp != null) {
                    if (maxHp.getBaseValue() < 30.0) {
                        maxHp.setBaseValue(30.0);
                    }
                }
                p.setHealth(30.0);

                // 부활 대사
                Bukkit.broadcastMessage("§c덴지 : §f네가 나한테 베여서 피를 흘리고! 내가 네 피를 마시고 회복!! 영구기관이 완성되고 말았어!!! 이걸로 노벨상은 내 거라고!!!!");

                // 전기톱 소리 및 피 튀는 이펙트 스케줄러 (2초 = 40틱)
                BukkitRunnable reviveTask = new BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        if (p.isDead() || !p.isValid() || ticks >= 40) {
                            this.cancel();
                            return;
                        }

                        // 레드스톤 블록 부드러운 분쇄 파티클 (피 튀는 연출)
                        p.getWorld().spawnParticle(
                                org.bukkit.Particle.BLOCK,
                                p.getLocation().add(0, 1, 0),
                                15, 0.4, 0.5, 0.4, 0.1,
                                Bukkit.createBlockData(Material.REDSTONE_BLOCK)
                        );

                        // 부앙부앙 엔진 소리 (10틱에 1번씩)
                        if (ticks % 10 == 0) {
                            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_MINECART_RIDING, 1.0f, 0.8f);
                        }

                        ticks += 5;
                    }
                };
                
                BukkitTask bTask = reviveTask.runTaskTimer(plugin, 0L, 5L);
                List<BukkitTask> taskList = activeTasks.computeIfAbsent(p.getUniqueId(), k -> new java.util.ArrayList<>());
                taskList.add(bTask);
            }
        }
    }
}
