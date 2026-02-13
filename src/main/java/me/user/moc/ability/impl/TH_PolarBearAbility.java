package me.user.moc.ability.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.PolarBear;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import me.user.moc.ability.Ability;

/**
 * [능력 코드: TH028]
 * 이름: 북극곰 (토가 히미코 변신 전용)
 * 설명: 토가 히미코가 북극곰으로 변신했을 때 사용하는 격리된 클래스입니다.
 * 주의: PolarBearAbility.java의 로직 수정 시, 이 파일도 반드시 함께 수정해야 합니다.
 */
public class TH_PolarBearAbility extends Ability {

    // 현재 플레이어의 배고픔 상태 (true: 배부름, false: 배고픔)
    private final Map<UUID, Boolean> isSatiated = new HashMap<>();

    public TH_PolarBearAbility(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "TH028"; // 토가 히미코 전용 코드
    }

    @Override
    public String getName() {
        return "북극곰"; // 이름 유지
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§b유틸 ● 북극곰(무한도전)",
                "§f사람을 찢습니다.");
    }

    @Override
    public void giveItem(Player p) {
        // [초기화]
        p.getInventory().remove(Material.IRON_SWORD);
        p.getInventory().remove(Material.POTION); // 재생 포션

        // [Fix] Paper 1.21.11 대응: Attribute API 사용 및 안전한 체력 설정
        try {
            if (p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(71.0); // 3줄 5반
            }
            p.setHealth(71.0);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[TH_PolarBear] 체력 설정 중 오류 발생: " + e.getMessage());
        }

        // [추가] 구운 소고기 제거 및 생고기 지급
        p.getInventory().remove(Material.COOKED_BEEF);
        p.getInventory().addItem(new ItemStack(Material.BEEF, 32));

        // [장비 지급] 곰 손톱 (염소 뿔 -> 돌 칼로 변경 요청)
        ItemStack claw = new ItemStack(Material.STONE_SWORD);
        ItemMeta meta = claw.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(1);
            meta.setDisplayName("§f곰 손톱");
            meta.setLore(Arrays.asList("§7북극곰의 날카로운 손톱입니다."));
            // 1. 데미지 설정 (기존 유지)
            AttributeModifier damageMod = new AttributeModifier(
                    new org.bukkit.NamespacedKey(plugin, "th_polar_bear_damage"), // 키 변경
                    3.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    org.bukkit.inventory.EquipmentSlotGroup.HAND);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageMod);

            claw.setItemMeta(meta);
        }
        p.getInventory().addItem(claw);

        // 곰 변신
        createVisualBear(p);

        // 상태 초기화
        isSatiated.put(p.getUniqueId(), true);

        // 투명화
        // [수정] 투명화 파티클 제거 (1인칭 시점 방해 방지 및 위치 노출 방지)
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false,
                false, true));

        // [추가] 갑옷 제거
        p.getInventory().setArmorContents(null);
        startTickTask();
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§b유틸 ● 북극곰(무한도전)");
        p.sendMessage("§f체력 3줄 5반(71칸)의 북극곰으로 변신합니다.");
        p.sendMessage("§f[배부름] 배고픔 5칸 초과 : 신속 2, 힘 2");
        p.sendMessage("§f[배고픔] 배고픔 5칸 이하 : 힘 4 (신속 없음)");
        p.sendMessage("§f생명체를 죽이면 생소고기 2개를 얻습니다.");
        p.sendMessage("§c생소고기 이외엔 음식을 섭취할 수 없습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 곰 앞발, 생소고기 32개");
        p.sendMessage("§f장비 제거 : 철 칼, 철 흉갑, 구운 소고기 64개, 재생 포션");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);

        try {
            // [Fix] 원래 체력(20)으로 복구 (Attribute API 사용)
            if (p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20.0);
            }
            if (p.getHealth() > 20.0)
                p.setHealth(20.0);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[TH_PolarBear] Cleanup 체력 복구 중 오류 발생: " + e.getMessage());
        }

        isSatiated.remove(p.getUniqueId());

        // 팀 해제
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam("TH_POLAR_" + p.getUniqueId().toString().substring(0, 8));
        if (team != null) {
            team.unregister();
        }

        // 효과 제거
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        p.removePotionEffect(PotionEffectType.SPEED);
        p.removePotionEffect(PotionEffectType.STRENGTH);

        // [Fix] 플레이어 다시 보이기 (딜레이 추가로 확실하게 적용)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        online.showEntity(plugin, p);
                    }
                }
            }
        }.runTaskLater(plugin, 5L); // [Fix] 2L -> 5L (TogaRevert와 충돌 방지 및 확실한 처리)
    }

    // [로직 2] 음식 섭취 제한 (곰 손톱 예외 처리 추가)
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!activeEntities.containsKey(p.getUniqueId()))
            return;

        Material type = e.getItem().getType();

        if (type != Material.BEEF) {
            e.setCancelled(true);
            p.sendMessage("§c북극곰은 생 소고기만 먹을 수 있습니다!");
        }
    }

    // [로직 3] 킬 시 생고기 획득
    @EventHandler
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();

        if (killer != null && activeEntities.containsKey(killer.getUniqueId())) {
            killer.getInventory().addItem(new ItemStack(Material.BEEF, 2));
            killer.sendMessage("§a생고기 2개를 획득했습니다.");
        }
    }

    // [로직 4] 곰 손톱 우클릭 방지 (보조)
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.STONE_SWORD)
            return;

        if (!activeEntities.containsKey(p.getUniqueId()))
            return;

        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_POLAR_BEAR_AMBIENT, 1f, 1f);
        }
    }

    // [로직 5] 변신 및 데미지 공유
    @EventHandler
    public void onBearDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof PolarBear bear))
            return;

        Player owner = null;
        for (Map.Entry<UUID, List<Entity>> entry : activeEntities.entrySet()) {
            if (entry.getValue().contains(bear)) {
                try {
                    owner = Bukkit.getPlayer(entry.getKey());
                } catch (Exception ex) {
                } // 안전처리
                break;
            }
        }

        if (owner == null)
            return;

        e.setCancelled(true); // 곰은 데미지 X

        if (owner.isValid() && !owner.isDead()) {
            // 자해 방지
            if (e instanceof EntityDamageByEntityEvent edbe && edbe.getDamager().equals(owner)) {
                return;
            }

            // 데미지 전달
            if (e instanceof EntityDamageByEntityEvent edbe) {
                owner.damage(e.getFinalDamage(), edbe.getDamager());
            } else {
                owner.damage(e.getFinalDamage());
            }

            // 피격 리액션
            bear.playHurtAnimation(0);
            owner.playHurtAnimation(0);
        }
    }

    private void createVisualBear(Player p) {
        List<Entity> entities = new ArrayList<>();
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "TH_POLAR_" + p.getUniqueId().toString().substring(0, 8);
        Team team = sb.getTeam(teamName);
        if (team == null)
            team = sb.registerNewTeam(teamName);

        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        team.setCanSeeFriendlyInvisibles(true);
        team.setAllowFriendlyFire(true); // [Fix] 아군 공격 허용 (토가-곰 vs 진짜-곰 데미지 처리를 위해 필수)
        // [Fix] 메인 스코어보드 팀에는 플레이어를 추가하지 않음!
        // 토가는 Private Scoreboard를 사용하므로, 메인 스코어보드 팀에 들어가면 안 됨(진짜 유저 충돌 방지).
        // if (!team.hasEntry(p.getName())) { team.addEntry(p.getName()); }

        // 본인용 (Private)
        for (int i = 0; i < 2; i++) {
            PolarBear privateBear = (PolarBear) p.getWorld().spawnEntity(p.getLocation(), EntityType.POLAR_BEAR);
            privateBear.setAI(false);
            privateBear.setInvulnerable(true);
            privateBear.setCollidable(false); // [추가] 본인이 밀리지 않도록 충돌 제거
            privateBear.setSilent(true);
            privateBear.setAdult();
            privateBear.addScoreboardTag("POLAR_PRIVATE");

            // [수정] 본인에게는 반투명하게 보여야 함 (Team.CanSeeFriendlyInvisibles=true 덕분에 반투명으로 보임)
            // 투명화 포션 다시 복구 (반투명 효과 위해)
            // 파티클은 제거 (hideParticles = true)
            privateBear.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION,
                    0, false, false, true));

            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(p.getUniqueId())) {
                    online.hideEntity(plugin, privateBear);
                    online.hideEntity(plugin, p); // [Fix] 플레이어 본체 숨기기 (떠다니는 칼/파티클 제거)
                }
            }
            // [추가] 본인과 같은 팀에 넣어 반투명하게 보이게 함
            String entry = privateBear.getUniqueId().toString();
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }

            // [Immediate Sync Restore] Private Bear
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
                    if (!userTeam.hasEntry(p.getPlayerProfile().getName())) // [Fix] 프로필 이름 사용 (가시성 동기화)
                        userTeam.addEntry(p.getPlayerProfile().getName());
                }
            } catch (Exception e) {
            }

            entities.add(privateBear);
        }

        // 타인용 (Public)
        PolarBear publicBear = (PolarBear) p.getWorld().spawnEntity(p.getLocation(), EntityType.POLAR_BEAR);
        publicBear.setAI(false);
        publicBear.setInvulnerable(false);
        publicBear.setCollidable(false);
        publicBear.setSilent(true);
        publicBear.setAdult();
        publicBear.setMaxHealth(100.0);
        publicBear.setHealth(100.0);
        publicBear.addScoreboardTag("POLAR_PUBLIC");

        p.hideEntity(plugin, publicBear);

        // [추가] 충돌 방지: Public Bear도 팀에 추가 (COLLISION_RULE.NEVER 적용) - 메인 스코어보드
        String publicEntry = publicBear.getUniqueId().toString();
        if (!team.hasEntry(publicEntry)) {
            team.addEntry(publicEntry);
        }

        // [Immediate Sync Restore] ScoreboardManager 딜레이(1초) 방지를 위한 즉시 동기화
        try {
            Scoreboard userSb = p.getScoreboard();
            if (userSb != null && !userSb.equals(sb)) {
                Team userTeam = userSb.getTeam(teamName);
                if (userTeam == null) {
                    userTeam = userSb.registerNewTeam(teamName);
                }
                userTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
                userTeam.setCanSeeFriendlyInvisibles(true); // [핵심]
                userTeam.setAllowFriendlyFire(true); // [Fix]

                if (!userTeam.hasEntry(p.getPlayerProfile().getName())) // [Fix]
                    userTeam.addEntry(p.getPlayerProfile().getName());

                // Bears added to entities list below, but we can add them to team now if we
                // have ref
                // Private Bear logic handled above? No, wait.
            }
        } catch (Exception e) {
        }

        entities.add(publicBear);

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
                            if (e instanceof PolarBear
                                    && ((PolarBear) e).getScoreboardTags().contains("POLAR_PRIVATE")) {
                                String entry = e.getUniqueId().toString();
                                if (!userTeam.hasEntry(entry))
                                    userTeam.addEntry(entry);
                            }
                            // Public Bear도 충돌 방지 위해 추가
                            if (e instanceof PolarBear
                                    && ((PolarBear) e).getScoreboardTags().contains("POLAR_PUBLIC")) {
                                String entry = e.getUniqueId().toString();
                                if (!userTeam.hasEntry(entry))
                                    userTeam.addEntry(entry);
                            }
                        }
                    }
                } catch (Exception e) {
                }
            }
        }.runTaskLater(plugin, 10L); // 10 ticks = 0.5 sec
    }

    private List<PolarBear> getVisualBears(Player p) {
        List<PolarBear> result = new ArrayList<>();
        List<Entity> list = activeEntities.get(p.getUniqueId());
        if (list == null)
            return result;
        for (Entity e : list) {
            if (e instanceof PolarBear b)
                result.add(b);
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

                for (UUID uuid : new ArrayList<>(activeEntities.keySet())) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline())
                        continue;

                    if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.isDead()) {
                        continue;
                    }

                    // [버프 로직]
                    int food = p.getFoodLevel();
                    boolean currentSatiated = food > 10;

                    Boolean lastState = isSatiated.get(p.getUniqueId());
                    if (lastState == null || lastState != currentSatiated) {
                        isSatiated.put(p.getUniqueId(), currentSatiated);

                        if (currentSatiated) {
                            Bukkit.broadcastMessage("§b북극곰 : §f쿠우어어ㅓㅇ엉(배불러)");
                        } else {
                            Bukkit.broadcastMessage("§b북극곰 : §c쿠우어어ㅓㅇ엉(배고파)");
                        }
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_POLAR_BEAR_WARNING, 1f, 0.8f);
                    }

                    if (currentSatiated) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, true, true, true));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 1, true, true, true));
                    } else {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 3, true, true, true));
                    }

                    // [변신 로직]
                    List<PolarBear> bears = getVisualBears(p);

                    boolean needsRespawn = bears.isEmpty();
                    if (!needsRespawn) {
                        for (PolarBear b : bears) {
                            if (b.isDead() || !b.isValid()) {
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
                        createVisualBear(p);
                        bears = getVisualBears(p);
                    }

                    // [Flickering Fix] Removed brute-force sync every tick.
                    // Instead, we rely on ScoreboardManager and the initial setup in
                    // createVisualBear.

                    if (bears.isEmpty())
                        continue;

                    for (PolarBear bear : bears) {
                        if (bear.isDead())
                            continue;

                        // [수정] 월드 보더 밖으로 나가지 않도록 보정
                        org.bukkit.Location targetLoc = p.getLocation();

                        // [Fix] Private Bear 본인 공격(시야) 방해 방지 -> 뒤로 0.75칸 이동
                        if (bear.getScoreboardTags().contains("POLAR_PRIVATE")) {
                            targetLoc = targetLoc.clone()
                                    .subtract(p.getLocation().getDirection().normalize().multiply(0.75));
                        }

                        bear.teleport(clampLocationToBorder(targetLoc));

                        double ownerHealth = Math.min(p.getHealth(), 100.0);
                        if (bear.getHealth() != ownerHealth) {
                            try {
                                bear.setHealth(Math.max(0, ownerHealth));
                            } catch (Exception e) {
                            }
                        }

                        if (bear.getScoreboardTags().contains("POLAR_PRIVATE")) {
                            // [수정] 본인에게는 보이도록 설정 (반투명 유지를 위해)
                            p.showEntity(plugin, bear);

                            for (Player online : Bukkit.getOnlinePlayers()) {
                                if (!online.getUniqueId().equals(p.getUniqueId())) {
                                    online.hideEntity(plugin, bear);
                                }
                            }
                        } else if (bear.getScoreboardTags().contains("POLAR_PUBLIC")) {
                            p.hideEntity(plugin, bear);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
}
