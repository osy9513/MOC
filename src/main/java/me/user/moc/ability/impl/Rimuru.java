package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * [능력 코드: 018]
 * 이름: 리무루 템페스트
 * 설명: 슬라임으로 변신하며, 아이템을 먹어 성장하고 몸통 박치기로 공격함.
 */
public class Rimuru extends Ability {

    private final Set<UUID> waterState = new HashSet<>();
    private final Map<UUID, Integer> damageStacks = new HashMap<>();

    public Rimuru(JavaPlugin plugin) {
        super(plugin);
        startTickTask();
    }

    @Override
    public String getCode() {
        return "018";
    }

    @Override
    public String getName() {
        return "리무루 템페스트";
    }

    @Override
    public List<String> getDescription() {
        List<String> list = new ArrayList<>();
        list.add("§b전투 ● 리무루 템페스트(전생했더니 슬라임이었던 건에 대하여)");
        list.add("§f슬라임으로 변신합니다.");
        return list;
    }

    @Override
    public void giveItem(Player p) {
        p.getInventory().clear();
        p.getInventory().addItem(new ItemStack(Material.WATER_BUCKET));

        p.setMaxHealth(70.0); // 3.5줄 (70칸)
        p.setHealth(70.0);

        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 99999 * 20, 2, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 99999 * 20, 0, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 99999 * 20, 0, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 99999 * 20, 0, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999 * 20, 0, false, false)); // 상시 화염 저항
        p.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);

        damageStacks.put(p.getUniqueId(), 0);

        createVisualSlime(p);

        Bukkit.broadcastMessage("§b리무루 템페스트 : §f나쁜 슬라임이 아니야!");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§b전투 ● 리무루 템페스트(전생했더니 슬라임이었던 건에 대하여)");
        p.sendMessage("§f슬라임으로 변신합니다.");
        p.sendMessage("§f상대와 부딪치거나 겹치면 상대에게 8 데미지를 줍니다.");
        p.sendMessage("§f기본 체력이 3.5줄(70칸)이며 배고픔이 달지 않습니다.");
        p.sendMessage("§f넉백 저항 100%가 있으며 점프 시 블럭 3칸을 올라갑니다.");
        p.sendMessage("§f물 양동이를 제외한 땅에 떨어진 아이템을 먹을 때마다 크기가 커집니다(무한).");
        p.sendMessage("§f아이템 섭취 시 체력이 10칸(20) 회복되며, 부딪히는 데미지가 영구적으로 5 증가합니다.");
        p.sendMessage("§f물 양동이 이외 모든 아이템은 자동으로 사라집니다.");
        p.sendMessage("§f수압 추진을 배우고 상시 화염 저항이 있어 물과 불에 강합니다.");
        p.sendMessage("§f상시 재생 1 버프를 가지고 있습니다.");
        p.sendMessage("§f일심동체: 본체와 슬라임의 체력이 공유됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음(물 양동이 지급)");
        p.sendMessage("§f장비 제거 : 물 양동이 외 전부");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);

        p.setMaxHealth(20.0);
        p.removePotionEffect(PotionEffectType.JUMP_BOOST);
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        p.removePotionEffect(PotionEffectType.SATURATION);
        p.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        p.removePotionEffect(PotionEffectType.STRENGTH);
        p.removePotionEffect(PotionEffectType.REGENERATION);
        p.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        p.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE).setBaseValue(0.0);

        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam("RIMURU_" + p.getUniqueId().toString().substring(0, 8));
        if (team != null) {
            team.unregister();
        }

        waterState.remove(p.getUniqueId());
        damageStacks.remove(p.getUniqueId());
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p))
            return;
        if (!activeEntities.containsKey(p.getUniqueId()))
            return;

        Item itemEntity = e.getItem();
        ItemStack stack = itemEntity.getItemStack();

        if (stack.getType() == Material.WATER_BUCKET) {
            return;
        }

        e.setCancelled(true);
        itemEntity.remove();

        grow(p);
    }

    @EventHandler
    public void onSlimeDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Slime slime))
            return;

        Player owner = null;
        for (Map.Entry<UUID, List<Entity>> entry : activeEntities.entrySet()) {
            if (entry.getValue().contains(slime)) {
                owner = Bukkit.getPlayer(entry.getKey());
                break;
            }
        }

        if (owner == null)
            return;

        e.setCancelled(true);

        if (owner.isValid() && !owner.isDead() && owner.getNoDamageTicks() <= 0) {
            if (e instanceof EntityDamageByEntityEvent edbe) {
                if (edbe.getDamager().equals(owner))
                    return;
                owner.damage(e.getFinalDamage(), edbe.getDamager());
            } else {
                owner.damage(e.getFinalDamage());
            }

            // 피격 가시성 강화
            slime.playEffect(org.bukkit.EntityEffect.HURT); // 슬라임 움찔
            owner.playHurtAnimation(0); // 본체 플레이어 움찔 (가시성 증가)

            // 피격 파티클 (슬라임 위치에 붉은 입자)
            slime.getWorld().spawnParticle(Particle.BLOCK, slime.getLocation().add(0, 0.5, 0), 10, 0.3, 0.3, 0.3,
                    Bukkit.createBlockData(Material.REDSTONE_BLOCK));

            // 피격 사운드 (슬라임 소리 + 본체 타격음)
            owner.playSound(owner.getLocation(), Sound.ENTITY_SLIME_HURT, 1f, 1f);
            owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.5f, 1f);
        }
    }

    private void grow(Player p) {
        List<Slime> slimes = getVisualSlimes(p);
        for (Slime s : slimes) {
            if (s != null) {
                s.setSize(s.getSize() + 1);
                // [수정] 이펙트를 슬라임 위치에서 재생
                p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, s.getLocation().add(0, s.getSize() * 0.5, 0), 10,
                        s.getSize() * 0.3, s.getSize() * 0.3, s.getSize() * 0.3);
            }
        }

        double healAmount = 10.0;
        p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + healAmount));

        damageStacks.put(p.getUniqueId(), damageStacks.getOrDefault(p.getUniqueId(), 0) + 1);

        // p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1,
        // 0), 10, 0.5, 0.5, 0.5);
        // -> 슬라임 위치로 이동됨
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);
        Bukkit.broadcastMessage("§f리무루가 히포쿠테 초와 마광석을 섭취하여 성장했습니다! (횟수: "
                + damageStacks.get(p.getUniqueId()) + ")");
    }

    private void createVisualSlime(Player p) {
        List<Entity> entities = new ArrayList<>();
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "RIMURU_" + p.getUniqueId().toString().substring(0, 8);
        Team team = sb.getTeam(teamName);
        if (team == null)
            team = sb.registerNewTeam(teamName);

        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        team.setCanSeeFriendlyInvisibles(true);

        // 1. Private Slimes (본인용, 3중 겹치기로 농도 강화)
        for (int i = 0; i < 3; i++) {
            Slime privateSlime = (Slime) p.getWorld().spawnEntity(p.getLocation(), EntityType.SLIME);
            privateSlime.setSize(2);
            privateSlime.setAI(false);
            privateSlime.setInvulnerable(true);
            privateSlime.setCollidable(false);
            privateSlime.setSilent(true);
            privateSlime.setMaxHealth(100.0);
            privateSlime.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 999999, 0, false, false));

            team.addEntry(privateSlime.getUniqueId().toString());
            entities.add(privateSlime);
        }

        // 플레이어 팀 등록
        team.addEntry(p.getName());

        // 2. Public Slime (타인용, 초록색)
        Slime publicSlime = (Slime) p.getWorld().spawnEntity(p.getLocation(), EntityType.SLIME);
        publicSlime.setSize(2);
        publicSlime.setAI(false);
        publicSlime.setInvulnerable(false);
        publicSlime.setCollidable(false);
        publicSlime.setSilent(true);
        publicSlime.setMaxHealth(100.0);
        publicSlime.setHealth(100.0);

        p.hideEntity(plugin, publicSlime);
        entities.add(publicSlime);

        if (activeEntities.containsKey(p.getUniqueId())) {
            activeEntities.get(p.getUniqueId()).addAll(entities);
        } else {
            activeEntities.put(p.getUniqueId(), entities);
        }
    }

    private List<Slime> getVisualSlimes(Player p) {
        List<Slime> result = new ArrayList<>();
        List<Entity> list = activeEntities.get(p.getUniqueId());
        if (list == null)
            return result;
        for (Entity e : list) {
            if (e instanceof Slime s)
                result.add(s);
        }
        return result;
    }

    private void startTickTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // [Fix] ConcurrentModificationException 방지를 위해 키셋 사본 사용
                for (UUID uuid : new ArrayList<>(activeEntities.keySet())) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline())
                        continue;

                    // [추가] 사망 또는 관전 모드 시 슬라임 제거
                    if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.isDead()) {
                        if (activeEntities.containsKey(uuid)) {
                            List<Entity> old = activeEntities.remove(uuid);
                            if (old != null) {
                                for (Entity e : old) {
                                    if (e.isValid())
                                        e.remove();
                                }
                            }
                        }
                        continue;
                    }

                    List<Slime> slimes = getVisualSlimes(p);

                    // 1. 슬라임 생존 확인 및 리스폰 로직 (자기장 밖 소멸 대응)
                    boolean needsRespawn = slimes.isEmpty();
                    if (!needsRespawn) {
                        for (Slime s : slimes) {
                            if (s.isDead() || !s.isValid()) {
                                needsRespawn = true;
                                break;
                            }
                        }
                    }

                    if (needsRespawn) {
                        // 기존 엔티티 정리
                        if (activeEntities.containsKey(uuid)) {
                            List<Entity> old = activeEntities.remove(uuid);
                            if (old != null) {
                                for (Entity e : old) {
                                    if (e.isValid())
                                        e.remove();
                                }
                            }
                        }

                        // 재소환
                        createVisualSlime(p);

                        // 크기 복구 (기본 2 + 스택)
                        int stack = damageStacks.getOrDefault(p.getUniqueId(), 0);
                        int newSize = 2 + stack;

                        slimes = getVisualSlimes(p); // 리스트 갱신
                        for (Slime s : slimes) {
                            s.setSize(newSize);
                        }
                    }

                    if (slimes.isEmpty())
                        continue;

                    for (Slime slime : slimes) {
                        if (slime.isDead())
                            continue;
                        slime.teleport(p.getLocation());
                        double ownerHealth = Math.min(p.getHealth(), 100.0);
                        if (slime.getHealth() != ownerHealth) {
                            try {
                                slime.setHealth(Math.max(0, ownerHealth));
                            } catch (Exception e) {
                            }
                        }
                    }

                    if (p.isInWater()) {
                        p.addPotionEffect(
                                new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 40, 40, false, false, false));
                        if (!waterState.contains(p.getUniqueId())) {
                            p.sendMessage("§b리무루 템페스트 : §f수압 추진!");
                            p.playSound(p.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, 1f, 1f);
                            waterState.add(p.getUniqueId());
                        }
                    } else {
                        waterState.remove(p.getUniqueId());
                    }

                    // 히트박스 판정용 메인 슬라임 (아무거나)
                    Slime mainSlime = slimes.get(0);
                    if (mainSlime.isDead())
                        continue;

                    org.bukkit.util.BoundingBox slimeBox = mainSlime.getBoundingBox();
                    double searchRadius = mainSlime.getSize() * 0.8 + 2.0;

                    int stack = damageStacks.getOrDefault(p.getUniqueId(), 0);
                    double damage = 8.0 + (stack * 5.0);

                    // Lambda에서 사용하기 위해 final 변수로 참조
                    final List<Slime> finalSlimes = slimes;

                    p.getWorld().getNearbyEntities(p.getLocation(), searchRadius, searchRadius, searchRadius)
                            .forEach(target -> {
                                if (target != p && !finalSlimes.contains(target)
                                        && target instanceof LivingEntity livingTarget) {
                                    if (slimeBox.overlaps(target.getBoundingBox())) {
                                        if (livingTarget.getNoDamageTicks() <= 0) {
                                            livingTarget.damage(damage, p);
                                            Vector dir = target.getLocation().toVector()
                                                    .subtract(p.getLocation().toVector())
                                                    .normalize();
                                            double knockback = 0.5 + (mainSlime.getSize() * 0.1);
                                            livingTarget.setVelocity(dir.multiply(knockback).setY(0.2));
                                        }
                                    }
                                }
                            });
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
}
