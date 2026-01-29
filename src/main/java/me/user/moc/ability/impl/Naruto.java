package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method; // [필수] 리플렉션 도구
import java.util.List;

public class Naruto extends Ability {

    public Naruto(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "007";
    }

    @Override
    public String getName() {
        return "나루토";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§e복합 ● 나루토(나루토)",
                "§f금술 두루마리를 우클릭해 §e다중 그림자 분신술§f을 사용합니다.",
                "§f12명의 분신이 주변의 모든 생명체를 공격합니다.");
    }

    @Override
    public void giveItem(Player p) {
        ItemStack item = new ItemStack(Material.ORANGE_BANNER);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6금술 두루마리");
            meta.setLore(List.of("§7우클릭 시 다중 그림자 분신술을 사용합니다."));
            item.setItemMeta(meta);
        }
        p.getInventory().addItem(item);
    }

    @Override
    public void detailCheck(Player p) {
        // [디테일 정보 출력] 사용자 요청 포맷에 맞게 수정됨
        p.sendMessage("§e복합 ㆍ 나루토(나루토)");
        p.sendMessage("금술 두루마리를 우클릭하여 '다중 그림자 분신술'을 사용합니다.");
        p.sendMessage("플레이어와 동일한 모습의 분신 12명을 즉시 소환하여 전장을 장악합니다.");
        p.sendMessage("분신은 적을 추격하여 공격하며, 파괴되기 전까지 계속해서 적을 압박합니다.");
        p.sendMessage(" ");
        p.sendMessage("쿨타임 : 30초");
        p.sendMessage("---");
        p.sendMessage("추가 장비 : 금술 두루마리(오렌지색 현수막)");
        p.sendMessage("장비 제거 : 없음");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!hasAbility(p))
            return;

        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack hand = e.getItem();
            if (hand != null && hand.getType() == Material.ORANGE_BANNER) {
                e.setCancelled(true);

                if (checkCooldown(p)) {
                    spawnClones(p);
                    setCooldown(p, 30);
                }
            }
        }
    }

    private void spawnClones(Player p) {
        p.getServer().broadcastMessage("§e나루토 : 다중 그림자 분신술!");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.5f);

        // 연기 효과
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 10) {
                    this.cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.02);
                count++;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // 12명의 분신 소환
        for (int i = 0; i < 12; i++) {
            double offsetX = (Math.random() - 0.5) * 6;
            double offsetZ = (Math.random() - 0.5) * 6;
            org.bukkit.Location spawnLoc = p.getLocation().add(offsetX, 0, offsetZ);
            spawnLoc.setY(p.getWorld().getHighestBlockYAt(spawnLoc) + 1);

            Mannequin clone = (Mannequin) p.getWorld().spawnEntity(spawnLoc, EntityType.MANNEQUIN);

            // [▼▼▼ 리플렉션 적용: 빨간 줄 완전 제거 ▼▼▼]
            // VS Code가 ResolvableProfile을 못 찾아도, 실행 시점에 강제로 주입합니다.
            try {
                // 1. 플레이어의 프로필 가져오기 (반환 타입이 무엇이든 Object로 받음)
                Object profile = p.getPlayerProfile();

                // 2. 마네킹의 'setProfile' 메서드를 이름으로 찾기
                Method setProfileMethod = null;
                for (Method m : clone.getClass().getMethods()) {
                    // setProfile 혹은 setPlayerProfile 이름이 있으면 채택
                    if (m.getName().equals("setProfile") || m.getName().equals("setPlayerProfile")) {
                        setProfileMethod = m;
                        break;
                    }
                }

                // 3. 메서드 실행 (프로필 주입)
                if (setProfileMethod != null) {
                    setProfileMethod.invoke(clone, profile);
                }
            } catch (Exception ex) {
                // 실패 시 로그 없이 넘어감 (기본 스킨)
                // 필요 시: p.sendMessage("§c스킨 적용 실패");
            }
            // [▲▲▲ 여기까지 변경됨 ▲▲▲]

            clone.setCustomName(p.getName());
            clone.setCustomNameVisible(false);
            clone.setMetadata("NarutoOwner", new FixedMetadataValue(plugin, p.getUniqueId().toString()));

            if (clone.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
                clone.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(4.0);
            }

            // 장비 복제
            clone.getEquipment().setArmorContents(p.getInventory().getArmorContents());
            clone.getEquipment().setItemInMainHand(p.getInventory().getItemInMainHand());
            clone.getEquipment().setItemInOffHand(p.getInventory().getItemInOffHand());

            startCloneAI(p, clone);
            registerSummon(p, clone);
        }
    }

    private void startCloneAI(Player owner, Mannequin clone) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (clone.isDead() || !clone.isValid()) {
                    this.cancel();
                    return;
                }

                // Mannequin -> Mob 캐스팅 (안전하게 처리)
                if (clone instanceof Mob) {
                    Mob aiBody = (Mob) clone;
                    LivingEntity target = aiBody.getTarget();
                    if (target == null || target.isDead() || target.equals(owner)) {
                        LivingEntity nearest = null;
                        double minDistance = 20.0;
                        for (Entity e : clone.getNearbyEntities(minDistance, 5, minDistance)) {
                            if (e instanceof LivingEntity victim && !e.equals(owner) && !e.hasMetadata("NarutoOwner")) {
                                double dist = e.getLocation().distance(clone.getLocation());
                                if (dist < minDistance) {
                                    minDistance = dist;
                                    nearest = victim;
                                }
                            }
                        }
                        if (nearest != null)
                            aiBody.setTarget(nearest);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    @EventHandler
    public void onTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent e) {
        if (e.getEntity().hasMetadata("NarutoOwner") && e.getTarget() instanceof Player p) {
            String ownerUUID = e.getEntity().getMetadata("NarutoOwner").get(0).asString();
            if (p.getUniqueId().toString().equals(ownerUUID))
                e.setCancelled(true);
        }
    }

    private boolean hasAbility(Player p) {
        return me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode());
    }
}