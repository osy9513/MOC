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
 * [능력 코드: TH018]
 * 이름: 리무루 템페스트 (토가 히미코 변신 전용)
 * 설명: 토가 히미코가 리무루로 변신했을 때 사용하는 격리된 클래스입니다.
 * 주의: Rimuru.java의 로직 수정 시, 이 파일도 반드시 함께 수정해야 합니다.
 */
public class TH_Rimuru extends Ability {

    private final Set<UUID> waterState = new HashSet<>();
    private final Map<UUID, Integer> damageStacks = new HashMap<>();

    // 몸통 박치기 데미지 판정 중인지 확인 (이벤트 캔슬 방지용)
    private final Set<UUID> isSlamming = new HashSet<>();

    public TH_Rimuru(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "TH018"; // 토가 히미코 전용 코드
    }

    @Override
    public String getName() {
        return "리무루 템페스트"; // 이름은 원본과 동일하게 유지
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
            meta.setLore(List.of("§7물 속에서 빠릅니다.", "§7다른 아이템 획득 시 흡수하여 성장에 사용합니다."));
            bucket.setItemMeta(meta);
        }
        p.getInventory().addItem(bucket);

        // [Fix] Paper 1.21.11 대응: Attribute API 사용 및 안전한 체력 설정
        try {
            if (p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(70.0); // 3.5줄
            }
            p.setHealth(70.0);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[TH_Rimuru] 체력 설정 중 오류 발생: " + e.getMessage());
        }

        p.addPotionEffect(
                new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, 2, true, true));
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.SATURATION, PotionEffect.INFINITE_DURATION, 0, true, true));
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 0, true, true));
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, true, true)); // 상시
                                                                                                                    // 화염
                                                                                                                    // 저항
        if (p.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE) != null)
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
        p.sendMessage("§f아이템 섭취 시 체력이 1칸(2) 회복됩니다.");
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

        try {
            // [Fix] 원래 체력(20)으로 복구 (Attribute API 사용)
            if (p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20.0);
            }
            // 체력이 20을 넘지 않도록 조정
            if (p.getHealth() > 20.0)
                p.setHealth(20.0);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[TH_Rimuru] Cleanup 체력 복구 중 오류 발생: " + e.getMessage());
        }

        p.removePotionEffect(PotionEffectType.JUMP_BOOST);
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        p.removePotionEffect(PotionEffectType.SATURATION);
        p.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        p.removePotionEffect(PotionEffectType.STRENGTH);
        p.removePotionEffect(PotionEffectType.REGENERATION);
        p.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);

        if (p.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE) != null)
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

    private void grow(Player p) {
        damageStacks.put(p.getUniqueId(), damageStacks.getOrDefault(p.getUniqueId(), 0) + 1);
        int stack = damageStacks.get(p.getUniqueId());

        int newSize = 2 + (int) (stack * 0.7);
        List<Slime> slimes = getVisualSlimes(p);
        for (Slime s : slimes) {
            if (s != null) {
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
        // [Fix] 최대 체력 확인 시 Attribute API 사용
        double maxHp = 20.0;
        if (p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
            maxHp = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        }
        p.setHealth(Math.min(maxHp, p.getHealth() + healAmount));

        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);
        Bukkit.broadcastMessage("§f[리무루] 포식 횟수: "
                + stack + "회 / 현재 크기: " + newSize + "");
    }

    // [추가] 플레이어 사망 시 슬라임 제거 (동기화)
    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (activeEntities.containsKey(p.getUniqueId())) {
            cleanup(p);
        }
    }

    // [추가] 슬라임 사망 시 플레이어도 사망 (동기화)
    @EventHandler
    public void onSlimeDeath(org.bukkit.event.entity.EntityDeathEvent e) {
        if (e.getEntity() instanceof Slime s) {
            // 주인을 찾아서 죽임
            for (UUID uuid : activeEntities.keySet()) {
                List<Entity> list = activeEntities.get(uuid);
                if (list != null && list.contains(s)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && !p.isDead()) {
                        p.setHealth(0); // 플레이어 사망 처리
                        // cleanup은 PlayerDeathEvent에서 호출됨
                    }
                    break;
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSlimeDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Slime s && s.isValid()) {
            // 내 슬라임인지 확인
            Player owner = null;
            for (UUID uuid : activeEntities.keySet()) {
                List<Entity> list = activeEntities.get(uuid);
                if (list != null && list.contains(s)) {
                    owner = Bukkit.getPlayer(uuid);
                    break;
                }
            }

            if (owner != null) {
                // [수정] Public Slime (남들에게 보이는 것)만 데미지 처리
                if (s.getScoreboardTags().contains("RIMURU_PUBLIC")) {
                    double damage = e.getDamage();
                    // 슬라임은 데미지를 입지 않음 (0으로 설정)
                    e.setDamage(0);
                    // 이벤트는 캔슬하여 넉백 등기타 효과 막음 (필요 시 넉백은 허용하고 데미지만 0으로 할 수도 있음)
                    // 하지만 "플레이어가 대신 맞는다"는 개념이므로, 슬라임은 멀쩡해야 함.
                    e.setCancelled(true);

                    // [추가] 피 튀기는 효과 (블럭 파티클: 레드스톤 블럭)
                    s.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, s.getLocation().add(0, s.getSize() * 0.5, 0),
                            20, s.getSize() * 0.3, s.getSize() * 0.3, s.getSize() * 0.3,
                            org.bukkit.Material.REDSTONE_BLOCK.createBlockData());
                    s.getWorld().playSound(s.getLocation(), org.bukkit.Sound.ENTITY_SLIME_HURT, 1f, 1f);

                    // 플레이어에게 데미지 전가
                    if (owner.getNoDamageTicks() <= 0) {
                        // 원인(Attacker)이 있으면 EntityDamageByEntityEvent 처리 필요하나,
                        // 간단히 damage() 메서드로 처리 (이 경우 원인은 불명확해질 수 있음)

                        // 만약 공격자가 있는 경우 공격자를 명시
                        if (e instanceof EntityDamageByEntityEvent debe) {
                            owner.damage(damage, debe.getDamager());
                        } else {
                            owner.damage(damage);
                        }
                    }
                } else {
                    // Private Slime (나에게만 보이는 것)은 무적이어야 함
                    e.setCancelled(true);
                }
            }
        }
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
        team.setAllowFriendlyFire(true); // [Fix] 아군 공격 허용
        // [Fix] 메인 스코어보드 팀에는 플레이어를 추가하지 않음! (충돌 방지)
        // if (!team.hasEntry(p.getName())) { team.addEntry(p.getName()); }

        // 1. Private Slime (본인용 - 나에게만 보여야 함)
        // [복구] 3중 겹치기로 농도 강화
        for (int i = 0; i < 3; i++) {
            Slime privateSlime = (Slime) p.getWorld().spawnEntity(p.getLocation(), EntityType.SLIME);
            privateSlime.setSize(2);
            privateSlime.setAI(false);
            privateSlime.setInvulnerable(true); // 본인용은 무적
            privateSlime.setCollidable(false); // [추가/확인] 본인이 밀리지 않도록 충돌 제거
            privateSlime.setSilent(true);
            privateSlime.setMaxHealth(100.0);
            privateSlime.addScoreboardTag("RIMURU_PRIVATE");

            // [수정] 본인 시점에서 반투명하게 보이게 하기 위해 투명화 적용 (Team.CanSeeFriendlyInvisibles=true 덕분에
            // 반투명으로 보임)
            privateSlime.addPotionEffect(
                    new PotionEffect(PotionEffectType.INVISIBILITY,
                            PotionEffect.INFINITE_DURATION, 0, false, false));
            // [추가] 화염 저항 적용 (사용자 요청)
            // privateSlime.addPotionEffect(
            // new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
            // PotionEffect.INFINITE_DURATION, 0, true, true));

            // [핵심] 다른 플레이어들에게는 Private Slime을 숨김
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(p.getUniqueId())) {
                    online.hideEntity(plugin, privateSlime);
                }
            }
            // [추가] 본인과 같은 팀에 넣어 반투명하게 보이게 함
            String entry = privateSlime.getUniqueId().toString();
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }

            entities.add(privateSlime);

            // [Immediate Sync Restore] Private Slime
            try {
                Scoreboard userSb = p.getScoreboard();
                if (userSb != null && !userSb.equals(sb)) {
                    Team userTeam = userSb.getTeam(teamName);
                    if (userTeam == null)
                        userTeam = userSb.registerNewTeam(teamName);

                    userTeam.setCanSeeFriendlyInvisibles(true); // [핵심]
                    userTeam.setAllowFriendlyFire(true); // [Fix]
                    userTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

                    if (!userTeam.hasEntry(entry))
                        userTeam.addEntry(entry);
                    if (!userTeam.hasEntry(p.getPlayerProfile().getName())) // [Fix] 프로필 이름 사용
                        userTeam.addEntry(p.getPlayerProfile().getName());
                }
            } catch (Exception e) {
            }
        }

        // 2. Public Slime (타인용 - 남들에게만 보여야 함)
        Slime publicSlime = (Slime) p.getWorld().spawnEntity(p.getLocation(), EntityType.SLIME);
        publicSlime.setSize(2);
        publicSlime.setAI(false);
        publicSlime.setInvulnerable(false); // 타인은 때릴 수 있어야 함
        publicSlime.setCollidable(false); // [추가/확인] 본인이 밀리지 않도록 충돌 제거
        publicSlime.setSilent(true);
        publicSlime.setMaxHealth(100.0);
        publicSlime.setHealth(100.0);
        publicSlime.addScoreboardTag("RIMURU_PUBLIC");

        // [추가] 화염 저항 적용 (사용자 요청)
        publicSlime.addPotionEffect(
                new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                        PotionEffect.INFINITE_DURATION, 0, true, true));

        // [요청] 피격 가능한 슬라임을 30% 크게 설정 (Attribute.SCALE 활용)
        if (publicSlime.getAttribute(org.bukkit.attribute.Attribute.SCALE) != null) {
            publicSlime.getAttribute(org.bukkit.attribute.Attribute.SCALE).setBaseValue(1.3);
        }

        // [핵심] 본인에게는 Public Slime을 숨김
        p.hideEntity(plugin, publicSlime);

        // [추가] 충돌 방지: Public Slime도 팀에 추가 (COLLISION_RULE.NEVER 적용)
        String publicEntry = publicSlime.getUniqueId().toString();
        if (!team.hasEntry(publicEntry)) {
            team.addEntry(publicEntry);
        }

        // [Immediate Sync Restore] ScoreboardManager 딜레이 방지
        try {
            Scoreboard userSb = p.getScoreboard();
            if (userSb != null && !userSb.equals(sb)) {
                Team userTeam = userSb.getTeam(teamName);
                if (userTeam == null) {
                    userTeam = userSb.registerNewTeam(teamName);
                }
                userTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
                userTeam.setCanSeeFriendlyInvisibles(true);
                userTeam.setAllowFriendlyFire(true); // [Fix]

                if (!userTeam.hasEntry(p.getPlayerProfile().getName())) // [Fix]
                    userTeam.addEntry(p.getPlayerProfile().getName());
            }
        } catch (Exception e) {
        }

        entities.add(publicSlime);

        if (activeEntities.containsKey(p.getUniqueId())) {
            activeEntities.get(p.getUniqueId()).addAll(entities);
        } else {
            activeEntities.put(p.getUniqueId(), entities);
        }

        // [추가] 0.5초 뒤에 한 번 더 개인 스코어보드 동기화 (초기화 타이밍 이슈 방지)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || !activeEntities.containsKey(p.getUniqueId()))
                    return;
                try {
                    Scoreboard userSb = p.getScoreboard();
                    // 메인보드와 같지 않을 때만 (개인 보드 사용 시)
                    if (userSb != null && !userSb.equals(sb)) {
                        Team userTeam = userSb.getTeam(teamName);
                        if (userTeam == null) {
                            userTeam = userSb.registerNewTeam(teamName);
                        }
                        userTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
                        userTeam.setCanSeeFriendlyInvisibles(true);
                        userTeam.setAllowFriendlyFire(true); // [Fix]

                        if (!userTeam.hasEntry(p.getPlayerProfile().getName())) // [Fix]
                            userTeam.addEntry(p.getPlayerProfile().getName());
                        for (Entity e : entities) {
                            if (e instanceof Slime
                                    && ((Slime) e).getScoreboardTags().contains("RIMURU_PRIVATE")) {
                                String bent = e.getUniqueId().toString();
                                if (!userTeam.hasEntry(bent))
                                    userTeam.addEntry(bent);
                            }
                        }
                    }
                } catch (Exception e) {
                }
            }
        }.runTaskLater(plugin, 10L);
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

                        // [추가] 안전장치: 플레이어가 공허(Y < -64)에 있거나 월드 보더 밖에 있으면 슬라임 생성 스킵
                        // (무의미한 엔티티 생성 및 서버 부하 방지)
                        if (p.getLocation().getY() < -64 || !p.getWorld().getWorldBorder().isInside(p.getLocation())) {
                            continue;
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

                        // [수정] 텔레포트 전 월드 보더 체크 및 보정
                        slime.teleport(clampLocationToBorder(p.getLocation()));

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

                            // 은신 효과가 풀리지 않도록 유지 (반투명 유지를 위해 필수)
                            if (!slime.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                                slime.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                                        PotionEffect.INFINITE_DURATION, 0, false, false));
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

                    // [너프] 스택 당 데미지 증가 제거 (기본 8.0 고정)
                    double damage = 8.0;

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
