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
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent; // [중요] 1.21 데이터 컴포넌트
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import me.user.moc.ability.Ability;

public class PolarBearAbility extends Ability {

    // 현재 플레이어의 배고픔 상태 (true: 배부름, false: 배고픔)
    private final Map<UUID, Boolean> isSatiated = new HashMap<>();

    public PolarBearAbility(JavaPlugin plugin) {
        super(plugin);
        startTickTask();
    }

    @Override
    public String getCode() {
        return "028";
    }

    @Override
    public String getName() {
        return "북극곰";
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
        // [너프] 3줄 5반 (71칸)
        p.setMaxHealth(71.0);
        p.setHealth(71.0);

        // [추가] 구운 소고기 제거 및 생고기 지급
        p.getInventory().remove(Material.COOKED_BEEF);
        p.getInventory().addItem(new ItemStack(Material.BEEF, 32));

        // [장비 지급] 곰 손톱 (염소 뿔 -> 데이터 조작으로 소리 제거)
        ItemStack claw = new ItemStack(Material.GOAT_HORN);
        ItemMeta meta = claw.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f곰 손톱");
            meta.setLore(Arrays.asList("§7북극곰의 날카로운 손톱입니다."));

            // 1. 데미지 설정 (4.0 추가 = 총 5.0)
            AttributeModifier damageMod = new AttributeModifier(
                    UUID.randomUUID(),
                    "generic.attackDamage",
                    3.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlot.HAND // 주손에 들었을 때만
            );
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageMod);

            // [▼▼▼ 핵심: Data Component로 소리 죽이기 ▼▼▼]
            // 염소 뿔을 '음식'으로 설정합니다.
            // 우클릭 시 '나팔 불기' 대신 '먹기' 액션이 나가게 되어 소리가 안 납니다.
            FoodComponent food = meta.getFood();
            food.setNutrition(0);
            food.setSaturation(0);
            food.setCanAlwaysEat(true); // 배불러도 먹는 시늉 가능
            // food.setEatSeconds(1000000); // [Fix] API 호환성 문제로 삭제 (기본 속도로 섭취 모션 재생 후 취소됨)

            meta.setFood(food); // 설정 적용
            // [▲▲▲ 여기까지 추가됨 ▲▲▲]

            claw.setItemMeta(meta);
        }
        p.getInventory().addItem(claw);

        // 곰 변신
        createVisualBear(p);

        // 상태 초기화
        isSatiated.put(p.getUniqueId(), true);

        // 투명화
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false,
                false, false));

        // [추가] 갑옷 제거
        p.getInventory().setArmorContents(null);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§b유틸 ● 북극곰(무한도전)");
        p.sendMessage("§f체력 3줄 5반(71칸)의 북극곰으로 변신합니다.");
        p.sendMessage("§f[배부름] 배고픔 5칸 초과 : 신속 2, 힘 2");
        p.sendMessage("§f[배고픔] 배고픔 5칸 이하 : 힘 4 (신속 없음)");
        p.sendMessage("§f생명체를 죽이면 생소고기를 2개 얻습니다.");
        p.sendMessage("§c생소고기 이외엔 음식을 섭취할 수 없습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 곰 손톱, 생소고기 32개");
        p.sendMessage("§f장비 제거 : 철 칼, 철 흉갑, 구운 소고기 64개");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        p.setMaxHealth(20.0);
        isSatiated.remove(p.getUniqueId());

        // 팀 해제
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam("POLAR_" + p.getUniqueId().toString().substring(0, 8));
        if (team != null) {
            team.unregister();
        }

        // 효과 제거
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        p.removePotionEffect(PotionEffectType.SPEED);
        p.removePotionEffect(PotionEffectType.STRENGTH);
    }

    // [로직 2] 음식 섭취 제한 (곰 손톱 예외 처리 추가)
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!activeEntities.containsKey(p.getUniqueId()))
            return;

        Material type = e.getItem().getType();

        // [추가] 곰 손톱(염소 뿔)은 절대 먹으면 안 됨 (100만초라 불가능하지만 혹시 몰라 차단)
        if (type == Material.GOAT_HORN) {
            e.setCancelled(true);
            return;
        }

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
        if (item == null || item.getType() != Material.GOAT_HORN)
            return;

        if (!activeEntities.containsKey(p.getUniqueId()))
            return;

        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // 이미 Food Component로 소리는 막혔지만, 먹는 동작(손 흔들기)도 취소하고 싶다면 유지
            e.setCancelled(false); // [수정] 먹는 모션(손 흔들기)을 보여주기 위해 캔슬 해제 (FoodComponent 덕분에 소모되진 않음)

            // [추가] 곰 울음 소리 재생
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
                owner = Bukkit.getPlayer(entry.getKey());
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
        String teamName = "POLAR_" + p.getUniqueId().toString().substring(0, 8);
        Team team = sb.getTeam(teamName);
        if (team == null)
            team = sb.registerNewTeam(teamName);

        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        team.setCanSeeFriendlyInvisibles(true);
        team.addEntry(p.getName());

        // 본인용 (Private)
        for (int i = 0; i < 2; i++) {
            PolarBear privateBear = (PolarBear) p.getWorld().spawnEntity(p.getLocation(), EntityType.POLAR_BEAR);
            privateBear.setAI(false);
            privateBear.setInvulnerable(true);
            privateBear.setCollidable(false);
            privateBear.setSilent(true);
            privateBear.setAdult();
            privateBear.addScoreboardTag("POLAR_PRIVATE");

            privateBear.addPotionEffect(
                    new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));

            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(p.getUniqueId())) {
                    online.hideEntity(plugin, privateBear);
                }
            }
            team.addEntry(privateBear.getUniqueId().toString());
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
        entities.add(publicBear);

        if (activeEntities.containsKey(p.getUniqueId())) {
            activeEntities.get(p.getUniqueId()).addAll(entities);
        } else {
            activeEntities.put(p.getUniqueId(), entities);
        }
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

    private void startTickTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
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
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 1, false, false, false));
                    } else {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 3, false, false, false));
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

                    if (bears.isEmpty())
                        continue;

                    for (PolarBear bear : bears) {
                        if (bear.isDead())
                            continue;

                        bear.teleport(p.getLocation());

                        double ownerHealth = Math.min(p.getHealth(), 100.0);
                        if (bear.getHealth() != ownerHealth) {
                            try {
                                bear.setHealth(Math.max(0, ownerHealth));
                            } catch (Exception e) {
                            }
                        }

                        if (bear.getScoreboardTags().contains("POLAR_PRIVATE")) {
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