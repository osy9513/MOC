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
import org.bukkit.scheduler.BukkitTask;
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

    // 몸통 박치기 데미지 판정 중인지 확인 (이벤트 캔슬 방지용)
    private final Set<UUID> isSlamming = new HashSet<>();

    public Rimuru(JavaPlugin plugin) {
        super(plugin);
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
        p.getInventory().clear();
        ItemStack bucket = new ItemStack(Material.WATER_BUCKET);
        org.bukkit.inventory.meta.ItemMeta meta = bucket.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of("§7슬라임 상태 유지에 필요한 수분입니다.", "§7다른 아이템 획득 시 흡수하여 성장에 사용합니다."));
            bucket.setItemMeta(meta);
        }
        p.getInventory().addItem(bucket);

        p.setMaxHealth(70.0); // 3.5줄 (70칸)
        p.setHealth(70.0);

        p.addPotionEffect(
                new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, 2, true, true));
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, true, true));
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.SATURATION, PotionEffect.INFINITE_DURATION, 0, true, true));
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 0, true, true));
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, true, true)); // 상시
                                                                                                                    // 화염
                                                                                                                    // 저항
        p.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);

        damageStacks.put(p.getUniqueId(), 0);

        createVisualSlime(p);

        Bukkit.broadcastMessage("§b리무루 템페스트 : §f나쁜 슬라임이 아니야!");
        startTickTask();
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§b전투 ● 리무루 템페스트(전생했더니 슬라임이었던 건에 대하여)");
        p.sendMessage("§f슬라임으로 변신합니다.");
        p.sendMessage("§f상대와 부딪치거나 겹치면 상대에게 8 데미지를 줍니다.");
        p.sendMessage("§f기본 체력이 3.5줄(70칸)이며 배고픔이 달지 않습니다.");
        p.sendMessage("§f넉백 저항 100%가 있으며 점프 시 블럭 3칸을 올라갑니다.");
        p.sendMessage("§f물 양동이를 제외한 땅에 떨어진 아이템을 먹을 때마다 크기가 커집니다(무한).");
        p.sendMessage("§f아이템 섭취 시 체력이 1칸(2) 회복되며, 부딪히는 데미지가 영구적으로 2 증가합니다.");
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
        isSlamming.remove(p.getUniqueId()); // [Cleanup] 상태 제거
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

        // [이벤트 캔슬] 슬라임 자체는 데미지를 입지 않고 본체로 전달
        e.setCancelled(true);

        // [설명] 기존에는 owner.getNoDamageTicks() <= 0 체크가 있어,
        // 무적 시간(i-frame) 도중 더 높은 데미지가 들어와도 씹히는 문제가 있었습니다.
        // 이를 제거하고 owner.damage()를 직접 호출하여 마인크래프트 기본 로직(높은 데미지 갱신 등)을 따르도록 합니다.
        if (owner.isValid() && !owner.isDead()) {
            // 본인이 본인 슬라임을 때리는 경우 방지
            if (e instanceof EntityDamageByEntityEvent edbe && edbe.getDamager().equals(owner)) {
                return;
            }

            // 데미지 전달
            if (e instanceof EntityDamageByEntityEvent edbe) {
                owner.damage(e.getFinalDamage(), edbe.getDamager());
            } else {
                owner.damage(e.getFinalDamage());
            }

            // 피격 가시성 강화 (항상 재생)
            slime.playHurtAnimation(0); // 슬라임 움찔
            owner.playHurtAnimation(0); // 본체 플레이어 움찔

            // 피격 파티클
            slime.getWorld().spawnParticle(Particle.BLOCK, slime.getLocation().add(0, 0.5, 0), 10, 0.3, 0.3, 0.3,
                    Bukkit.createBlockData(Material.REDSTONE_BLOCK));

            // 피격 사운드 (모든 플레이어가 들을 수 있도록 World.playSound 사용)
            owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_SLIME_HURT, 1f, 1f);
            owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.5f, 1f);
        }
    }

    private void grow(Player p) {
        damageStacks.put(p.getUniqueId(), damageStacks.getOrDefault(p.getUniqueId(), 0) + 1);
        int stack = damageStacks.get(p.getUniqueId());

        List<Slime> slimes = getVisualSlimes(p);
        for (Slime s : slimes) {
            if (s != null) {
                // [너프] 성장률 70% 적용 (기본 2 + 스택 * 0.7)
                int newSize = 2 + (int) (stack * 0.7);
                s.setSize(newSize);

                // [추가] Public Slime (타인용)은 30% 더 크게 설정 (히트박스 문제 해결 요청)
                // Private Slime은 기본 크기(Scale 1.0) 유지
                if (s.getScoreboardTags().contains("RIMURU_PUBLIC")) {
                    if (s.getAttribute(org.bukkit.attribute.Attribute.SCALE) != null) {
                        s.getAttribute(org.bukkit.attribute.Attribute.SCALE).setBaseValue(1.3);
                    }
                } else {
                    if (s.getAttribute(org.bukkit.attribute.Attribute.SCALE) != null) {
                        s.getAttribute(org.bukkit.attribute.Attribute.SCALE).setBaseValue(1.0);
                    }
                }

                p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, s.getLocation().add(0, s.getSize() * 0.5, 0), 10,
                        s.getSize() * 0.3, s.getSize() * 0.3, s.getSize() * 0.3);
            }
        }

        double healAmount = 2.0;
        p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + healAmount));

        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);
        Bukkit.broadcastMessage("§f리무루가 히포쿠테 초와 마광석을 섭취하여 성장했습니다! (횟수: "
                + damageStacks.get(p.getUniqueId()) + ")");
    }

    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p))
            return;
        if (!activeEntities.containsKey(p.getUniqueId()))
            return;

        if (isSlamming.contains(p.getUniqueId())) {
            return;
        }

        e.setCancelled(true);
        p.sendMessage("§c슬라임 상태에서는 직접 공격할 수 없습니다! (몸통 박치기 활용)");
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
        team.addEntry(p.getName());

        // 1. Private Slime (본인용 - 나에게만 보여야 함)
        // [복구] 3중 겹치기로 농도 강화
        for (int i = 0; i < 3; i++) {
            Slime privateSlime = (Slime) p.getWorld().spawnEntity(p.getLocation(), EntityType.SLIME);
            privateSlime.setSize(2);
            privateSlime.setAI(false);
            privateSlime.setInvulnerable(true); // 본인용은 무적
            privateSlime.setCollidable(false);
            privateSlime.setSilent(true);
            privateSlime.setMaxHealth(100.0);
            privateSlime.addScoreboardTag("RIMURU_PRIVATE");

            // [수정] 본인 시점에서 반투명하게 보이게 하기 위해 투명화 적용 + 팀 설정
            privateSlime.addPotionEffect(
                    new PotionEffect(PotionEffectType.INVISIBILITY,
                            PotionEffect.INFINITE_DURATION, 0, true, true));

            // [핵심] 다른 플레이어들에게는 Private Slime을 숨김
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(p.getUniqueId())) {
                    online.hideEntity(plugin, privateSlime);
                }
            }
            // [추가] 본인과 같은 팀에 넣어 반투명하게 보이게 함
            team.addEntry(privateSlime.getUniqueId().toString());

            entities.add(privateSlime);
        }

        // 2. Public Slime (타인용 - 남들에게만 보여야 함)
        Slime publicSlime = (Slime) p.getWorld().spawnEntity(p.getLocation(), EntityType.SLIME);
        publicSlime.setSize(2);
        publicSlime.setAI(false);
        publicSlime.setInvulnerable(false); // 타인은 때릴 수 있어야 함
        publicSlime.setCollidable(false);
        publicSlime.setSilent(true);
        publicSlime.setMaxHealth(100.0);
        publicSlime.setHealth(100.0);
        publicSlime.addScoreboardTag("RIMURU_PUBLIC");

        // [요청] 피격 가능한 슬라임을 30% 크게 설정 (Attribute.SCALE 활용)
        if (publicSlime.getAttribute(org.bukkit.attribute.Attribute.SCALE) != null) {
            publicSlime.getAttribute(org.bukkit.attribute.Attribute.SCALE).setBaseValue(1.3);
        }

        // [핵심] 본인에게는 Public Slime을 숨김
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

    private BukkitTask tickTask;

    private void startTickTask() {
        if (tickTask != null && !tickTask.isCancelled())
            return;

        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (activeEntities.isEmpty()) {
                    this.cancel();
                    tickTask = null;
                    return;
                }

                // [Fix] ConcurrentModificationException 방지를 위해 키셋 사본 사용
                for (UUID uuid : new ArrayList<>(activeEntities.keySet())) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline())
                        continue;

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
                        if (activeEntities.containsKey(uuid)) {
                            List<Entity> old = activeEntities.remove(uuid);
                            if (old != null) {
                                for (Entity e : old) {
                                    if (e.isValid())
                                        e.remove();
                                }
                            }
                        }

                        createVisualSlime(p);

                        int stackCount = damageStacks.getOrDefault(uuid, 0);
                        int newSize = 2 + (int) (stackCount * 0.7);

                        slimes = getVisualSlimes(p);
                        for (Slime s : slimes) {
                            s.setSize(newSize);
                            // [추가] 리스폰 시에도 크기 비율 유지
                            if (s.getScoreboardTags().contains("RIMURU_PUBLIC")) {
                                if (s.getAttribute(org.bukkit.attribute.Attribute.SCALE) != null)
                                    loadingScale(s, 1.3);
                            } else {
                                if (s.getAttribute(org.bukkit.attribute.Attribute.SCALE) != null)
                                    loadingScale(s, 1.0);
                            }
                        }
                    }

                    if (slimes.isEmpty())
                        continue;

                    // [추가] Visibility 유지 (새로 들어온 플레이어 등에게 Private 숨김)
                    // 매 틱마다는 부하가 클 수 있으니, 20틱(1초)마다 체크하거나, 그냥 둬도 됨.
                    // 여기서는 확실하게 하기 위해 간단히 Private 슬라임에 대해 루프를 돕니다.

                    for (Slime slime : slimes) {
                        if (slime.isDead())
                            continue;
                        slime.teleport(p.getLocation());

                        // 체력 동기화
                        double ownerHealth = Math.min(p.getHealth(), 100.0);
                        if (slime.getHealth() != ownerHealth) {
                            try {
                                slime.setHealth(Math.max(0, ownerHealth));
                            } catch (Exception e) {
                            }
                        }

                        // [핵심] 시야 분리 유지 Check
                        if (slime.getScoreboardTags().contains("RIMURU_PRIVATE")) {
                            // [수정] 본인에게는 반투명하게 보여야 함 (팀 설정 + 은신)
                            p.showEntity(plugin, slime);

                            // 은신 효과가 풀리지 않도록 유지
                            if (!slime.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                                slime.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                                        PotionEffect.INFINITE_DURATION, 0, true, true));
                            }

                            for (Player online : Bukkit.getOnlinePlayers()) {
                                if (!online.getUniqueId().equals(p.getUniqueId())) {
                                    // 다른 사람에게는 숨김
                                    online.hideEntity(plugin, slime);
                                }
                            }
                        } else if (slime.getScoreboardTags().contains("RIMURU_PUBLIC")) {
                            // 본인에게는 숨김
                            p.hideEntity(plugin, slime); // 항상 숨김 시도 (이미 숨겨져 있으면 무시됨)
                        }
                    }

                    if (p.isInWater()) {
                        p.addPotionEffect(
                                new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 40, 40, true, true, true));
                        if (!waterState.contains(p.getUniqueId())) {
                            p.sendMessage("§b리무루 템페스트 : §f수압 추진!");
                            p.playSound(p.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, 1f, 1f);
                            waterState.add(p.getUniqueId());
                        }
                    } else {
                        waterState.remove(p.getUniqueId());
                    }

                    Slime tempSlime = null;
                    for (Slime s : slimes) {
                        if (!s.isDead()) {
                            tempSlime = s;
                            break;
                        }
                    }
                    if (tempSlime == null)
                        continue;

                    final Slime mainSlime = tempSlime;

                    org.bukkit.util.BoundingBox slimeBox = mainSlime.getBoundingBox();
                    double searchRadius = mainSlime.getSize() * 0.8 + 2.0;

                    int stackCount = damageStacks.getOrDefault(p.getUniqueId(), 0);
                    double damage = 8.0 + (stackCount * 2.0);

                    final List<Slime> finalSlimes = slimes;

                    p.getWorld().getNearbyEntities(p.getLocation(), searchRadius, searchRadius, searchRadius)
                            .forEach(target -> {
                                if (target != p && !finalSlimes.contains(target)
                                        && target instanceof LivingEntity livingTarget) {
                                    if (livingTarget instanceof Player
                                            && ((Player) livingTarget).getGameMode() == org.bukkit.GameMode.SPECTATOR)
                                        return;
                                    if (slimeBox.overlaps(target.getBoundingBox())) {
                                        if (livingTarget.getNoDamageTicks() <= 0) {
                                            isSlamming.add(p.getUniqueId());
                                            try {
                                                livingTarget.damage(damage, p);
                                            } finally {
                                                isSlamming.remove(p.getUniqueId());
                                            }

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

            private void loadingScale(Slime s, double val) {
                try {
                    s.getAttribute(org.bukkit.attribute.Attribute.SCALE).setBaseValue(val);
                } catch (Exception e) {
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
}
