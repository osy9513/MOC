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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;

public class PolarBearAbility extends Ability {

    // 현재 플레이어의 배고픔 상태 (true: 배부름, false: 배고픔)
    // 배고픔 > 10 : 배부름 (Speed 2, Strength 2)
    // 배고픔 <= 10 : 배고픔 (Strength 4)
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
        p.setMaxHealth(100.0); // 50칸 (5줄)
        p.setHealth(100.0);

        // [장비 지급] 곰 손톱 (염소 뿔)
        ItemStack claw = new ItemStack(Material.GOAT_HORN);
        ItemMeta meta = claw.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f곰 손톱");
            meta.setLore(Arrays.asList("§7북극곰의 날카로운 손톱입니다.", "§c우클릭 사용 불가"));

            // 돌 칼(5) 데미지와 동일하게 설정
            // 기본 1 + 4 = 5
            AttributeModifier damageMod = new AttributeModifier(
                    UUID.randomUUID(),
                    "generic.attackDamage",
                    4.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlot.HAND // 주손에 들었을 때만
            );
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageMod);
            claw.setItemMeta(meta);
        }
        p.getInventory().addItem(claw);

        // 곰 변신
        createVisualBear(p);

        // 상태 초기화 (배부름 상태로 가정)
        isSatiated.put(p.getUniqueId(), true); // 초기엔 보통 배고픔이 꽉 차있으므로

        // 투명화
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false,
                false, false));

    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§b유틸 ● 북극곰(무한도전)");
        p.sendMessage("§f체력 5줄(50칸)의 북극곰으로 변신합니다.");
        p.sendMessage("§f[배부름] 배고픔 5칸 초과 : 신속 2, 힘 2");
        p.sendMessage("§f[배고픔] 배고픔 5칸 이하 : 힘 4 (신속 없음)");
        p.sendMessage("§f생명체를 죽이면 생고기를 2개 얻습니다.");
        p.sendMessage("§c생고기 이외엔 음식을 섭취할 수 없습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 곰 손톱");
        p.sendMessage("§f장비 제거 : 철 칼");
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

    // [로직 1] 배고픔 체크 및 버프 부여 (매 틱)
    // startTickTask 안에서 처리

    // [로직 2] 음식 섭취 제한
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!activeEntities.containsKey(p.getUniqueId()))
            return;

        Material type = e.getItem().getType();
        // 생고기(BEEF)만 가능. (구운 소고기도 안됨? "생고기"라고 했으니 BEEF만)
        // "생고기 이외엔 음식을 섭취할 수 없습니다." -> BEEF only.
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
            // 생고기 2개 드롭 (인벤토리에 줄지 바닥에 떨굴지? 보통 획득이면 인벤토리지만, 편의상 드롭 or 인벤)
            // "생고기를 2개 얻습니다" -> 인벤토리에 넣어주는 게 좋을듯.
            killer.getInventory().addItem(new ItemStack(Material.BEEF, 2));
            killer.sendMessage("§a생고기 2개를 획득했습니다.");
        }
    }

    // [로직 4] 곰 손톱(염소 뿔) 우클릭 방지
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!activeEntities.containsKey(p.getUniqueId()))
            return;

        ItemStack item = e.getItem();
        if (item != null && item.getType() == Material.GOAT_HORN) {
            if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                e.setCancelled(true); // 소리 재생 방지
            }
        }
    }

    // [로직 5] 변신 및 데미지 공유 (리무루 로직 차용)
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

    // 플레이어가 직접 맞았을 때 (투명 상태지만 광역기 등)
    // 리무루는 "슬라임 상태에서는 직접 공격 불가" 로직이 있었으나 북극곰은 언급 없음.
    // 하지만 "리무루처럼... 난 못 때리지만" -> 때릴 수 없다는 의미가 아님.
    // "난 못 때리지만 다른 사람이 북극곰을 때릴 수 있고" -> 내가 나를(곰을) 못 때린다는 뜻인가?
    // 아니면 "나는 (남을) 못 때리지만" 인가? -> 문맥상 "곰은 내가 조종하는 아바타 느낌이라 내가 곰을 때릴 순 없고(맞으면 안되고)"
    // 의미로 해석됨.
    // 혹은 "나는 (직접) 못 때리지만" -> 공격 불가?
    // 리무루 설명: "슬라임으로 변신하며... 몸통 박치기로 공격함" -> 리무루는 직접 공격 불가였음.
    // 북극곰은 "사람을 찢습니다", "곰 손톱" 아이템 지급. -> 직접 공격 가능함.

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

        // 1. Bear (타인용 & 본인용 하나로 통일 가능한가? 리무루는 투명도 차이 때문에 나눴음.)
        // 리무루: 본인은 반투명, 타인은 불투명.
        // 북극곰도 동일하게? "리무루처럼"
        // 본인용 (반투명), 타인용 (실체)

        // 본인용 (Private)
        for (int i = 0; i < 2; i++) { // 농도 조절
            PolarBear privateBear = (PolarBear) p.getWorld().spawnEntity(p.getLocation(), EntityType.POLAR_BEAR);
            privateBear.setAI(false);
            privateBear.setInvulnerable(true);
            privateBear.setCollidable(false);
            privateBear.setSilent(true);
            privateBear.setAdult(); // 성체
            privateBear.addScoreboardTag("POLAR_PRIVATE");

            // 본인에게만 보이도록 (투명화 걸고 타인에게 숨김? 아니, 본인에겐 반투명으로 보여야 하니 투명화 포션 + 타인에게 hide)
            privateBear.addPotionEffect(
                    new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));

            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(p.getUniqueId())) {
                    online.hideEntity(plugin, privateBear);
                }
            }
            entities.add(privateBear);
        }

        // 타인용 (Public)
        PolarBear publicBear = (PolarBear) p.getWorld().spawnEntity(p.getLocation(), EntityType.POLAR_BEAR);
        publicBear.setAI(false);
        publicBear.setInvulnerable(false); // 타격 가능
        publicBear.setCollidable(false);
        publicBear.setSilent(true);
        publicBear.setAdult();
        publicBear.setMaxHealth(100.0);
        publicBear.setHealth(100.0);
        publicBear.addScoreboardTag("POLAR_PUBLIC");

        // 본인에겐 숨김
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
                // ConcurrentModificationException 방지
                for (UUID uuid : new ArrayList<>(activeEntities.keySet())) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline())
                        continue;

                    if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.isDead()) {
                        // 정리 로직은 cleanup에서 처리되거나 여기서
                        continue;
                    }

                    // [버프 로직]
                    int food = p.getFoodLevel();
                    boolean currentSatiated = food > 10; // 5칸(10) 초과

                    Boolean lastState = isSatiated.get(p.getUniqueId());
                    if (lastState == null || lastState != currentSatiated) {
                        // 상태 변경
                        isSatiated.put(p.getUniqueId(), currentSatiated);

                        // 메시지 및 사운드
                        if (currentSatiated) {
                            Bukkit.broadcastMessage("§b북극곰 : §f쿠우어어ㅓㅇ엉(배불러)");
                        } else {
                            Bukkit.broadcastMessage("§b북극곰 : §c쿠우어어ㅓㅇ엉(배고파)");
                        }
                        // 포효 소리
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_POLAR_BEAR_WARNING, 1f, 0.8f);
                    }

                    // 버프 적용 (지속시간 짧게 갱신)
                    if (currentSatiated) {
                        // 배부름: 신속 2, 힘 2
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 1, false, false, false));
                    } else {
                        // 배고픔: 힘 4
                        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 3, false, false, false));
                    }

                    // [변신 로직]
                    List<PolarBear> bears = getVisualBears(p);

                    // 리스폰 체크
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
                        // 기존 제거 후 재생성
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

                    // 텔레포트 및 동기화
                    for (PolarBear bear : bears) {
                        if (bear.isDead())
                            continue;

                        bear.teleport(p.getLocation());

                        // Standing/Attack 모션?
                        // 북극곰은 standUp() 가능. 공격 시 등에 쓰면 좋을듯.
                        // 일단 기본은 네발.

                        // 체력바 동기화
                        double ownerHealth = Math.min(p.getHealth(), 100.0);
                        if (bear.getHealth() != ownerHealth) {
                            try {
                                bear.setHealth(Math.max(0, ownerHealth));
                            } catch (Exception e) {
                            }
                        }

                        // 시야 분리 유지
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
