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
import org.bukkit.util.Vector;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
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
        p.sendMessage("§e복합 ● 나루토(나루토)");
        p.sendMessage("§f금술 두루마리를 우클릭하여 '다중 그림자 분신술'을 사용합니다.");
        p.sendMessage("§f플레이어와 동일한 모습의 분신 12명을 즉시 소환하여 전장을 장악합니다.");
        p.sendMessage("§f분신은 적을 추격하여 공격하며, 파괴되기 전까지 계속해서 적을 압박합니다.");
        p.sendMessage(" ");
        p.sendMessage("§7쿨타임 : 30초");
        p.sendMessage("---");
        p.sendMessage("§7추가 장비 : §6금술 두루마리");
        p.sendMessage("§7장비 제거 : 없음");
    }

    @Override
    public void cleanup(Player p) {
        if (activeEntities.containsKey(p.getUniqueId())) {
            List<Entity> entities = activeEntities.get(p.getUniqueId());
            for (Entity e : entities) {
                if (e != null && !e.isDead()) {
                    e.remove();
                }
            }
            entities.clear();
        }
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

        double playerDamage = 1.0;
        double playerArmor = 0.0;
        double playerToughness = 0.0;

        if (p.getAttribute(Attribute.ATTACK_DAMAGE) != null)
            playerDamage = p.getAttribute(Attribute.ATTACK_DAMAGE).getValue();
        if (p.getAttribute(Attribute.ARMOR) != null)
            playerArmor = p.getAttribute(Attribute.ARMOR).getValue();
        if (p.getAttribute(Attribute.ARMOR_TOUGHNESS) != null)
            playerToughness = p.getAttribute(Attribute.ARMOR_TOUGHNESS).getValue();

        for (int i = 0; i < 12; i++) {
            double offsetX = (Math.random() - 0.5) * 8;
            double offsetZ = (Math.random() - 0.5) * 8;
            org.bukkit.Location spawnLoc = p.getLocation().add(offsetX, 0, offsetZ);
            spawnLoc.setY(p.getWorld().getHighestBlockYAt(spawnLoc) + 1);

            Mannequin clone = (Mannequin) p.getWorld().spawnEntity(spawnLoc, EntityType.MANNEQUIN);

            clone.setProfile(ResolvableProfile.resolvableProfile(p.getPlayerProfile()));
            clone.setDescription(null);
            clone.setCustomName(p.getName());
            clone.setCustomNameVisible(true);
            clone.setImmovable(false);

            if (clone.getAttribute(Attribute.MAX_HEALTH) != null)
                clone.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
            clone.setHealth(20.0);

            if (clone.getAttribute(Attribute.ATTACK_DAMAGE) != null)
                clone.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(playerDamage);
            if (clone.getAttribute(Attribute.ARMOR) != null)
                clone.getAttribute(Attribute.ARMOR).setBaseValue(playerArmor);
            if (clone.getAttribute(Attribute.ARMOR_TOUGHNESS) != null)
                clone.getAttribute(Attribute.ARMOR_TOUGHNESS).setBaseValue(playerToughness);

            clone.setMetadata("NarutoOwner", new FixedMetadataValue(plugin, p.getUniqueId().toString()));
            clone.getEquipment().setArmorContents(p.getInventory().getArmorContents());
            clone.getEquipment().setItemInMainHand(p.getInventory().getItemInMainHand());

            // [추가] 분신 소환 효과: 연기 사운드와 2초(40틱) 동안 지속되는 흰색 연기 파티클
            clone.getWorld().playSound(clone.getLocation(), Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1f, 1.5f);

            new BukkitRunnable() {
                int timer = 0;

                @Override
                public void run() {
                    if (clone.isDead() || !clone.isValid() || timer >= 40) {
                        this.cancel();
                        return;
                    }
                    // 분신 위치에 흰색 연기(CLOUD) 생성 (매 틱마다 조금씩)
                    clone.getWorld().spawnParticle(Particle.CLOUD, clone.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3,
                            0.05);
                    timer++;
                }
            }.runTaskTimer(plugin, 0L, 1L);

            startPhysicalAI(p, clone);
            registerSummon(p, clone);
        }
    }

    private void startPhysicalAI(Player owner, Mannequin clone) {
        new BukkitRunnable() {
            int tickCount = 0;
            long lastAttackTime = 0;

            @Override
            public void run() {
                if (clone.isDead() || !clone.isValid()) {
                    this.cancel();
                    return;
                }
                tickCount++;

                // 도발 로직 (1초마다)
                if (tickCount % 20 == 0) {
                    for (Entity e : clone.getNearbyEntities(10, 5, 10)) {
                        if (e instanceof Mob mob) {
                            if (!e.equals(owner) && !e.hasMetadata("NarutoOwner")) {
                                if (mob.getTarget() == null || mob.getTarget().equals(owner)) {
                                    mob.setTarget(clone);
                                }
                            }
                        }
                    }
                }

                LivingEntity target = null;
                double minDistance = 32.0;

                for (Entity e : clone.getNearbyEntities(minDistance, 10, minDistance)) {
                    if (e instanceof LivingEntity victim && !e.equals(owner) && !e.hasMetadata("NarutoOwner")) {
                        double d = clone.getLocation().distance(e.getLocation());
                        if (d < minDistance) {
                            minDistance = d;
                            target = victim;
                        }
                    }
                }

                if (target != null) {
                    double dist = clone.getLocation().distance(target.getLocation());

                    if (dist > 0.1) {
                        org.bukkit.Location currentLoc = clone.getLocation();
                        org.bukkit.Location targetLoc = target.getLocation();
                        Vector dir = targetLoc.toVector().subtract(currentLoc.toVector()).normalize();

                        currentLoc.setDirection(dir);
                        clone.setRotation(currentLoc.getYaw(), currentLoc.getPitch());

                        if (dist > 2.5) {
                            clone.setVelocity(dir.multiply(0.4).setY(clone.getVelocity().getY()));

                            org.bukkit.block.Block frontBlock = currentLoc.add(dir.clone().multiply(1.0)).getBlock();
                            if (frontBlock.getType().isSolid() && clone.isOnGround()) {
                                clone.setVelocity(clone.getVelocity().setY(0.5));
                            }
                        } else {
                            long now = System.currentTimeMillis();
                            if (now - lastAttackTime >= 1000) {
                                lastAttackTime = now;

                                clone.swingMainHand();

                                // [▼▼▼ 사운드 & 이펙트 변경됨 ▼▼▼]
                                // 기존 SWEEP(칼 소리) 제거 -> KNOCKBACK(달리기 공격 퍽!) + CRIT(점프 공격 툭!) 사운드 추가
                                clone.getWorld().playSound(clone.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK,
                                        1f, 1.0f);
                                clone.getWorld().playSound(clone.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f,
                                        1.0f);

                                // 칼 이펙트(SWEEP) 제거 -> 치명타 입자(CRIT) 추가로 맨손 타격감 강조
                                Vector viewDir = clone.getEyeLocation().getDirection();
                                clone.getWorld().spawnParticle(Particle.CRIT,
                                        clone.getEyeLocation().add(viewDir.multiply(1.5)), 5);
                                // [▲▲▲ 여기까지 변경됨 ▲▲▲]

                                double damage = 1.0;
                                if (clone.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
                                    damage = clone.getAttribute(Attribute.ATTACK_DAMAGE).getValue();
                                }
                                target.damage(damage, owner);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean hasAbility(Player p) {
        return me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode());
    }
}