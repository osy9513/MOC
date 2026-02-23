package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * [능력 코드: 054]
 * 이름: 요뽀뽀 (갓슈벨!!/금색의 갓슈!!)
 * 설명: 귀여운 요뽀뽀이가 당신을 도와 싸웁니다!
 */
public class Yopopo extends Ability {

    // 플레이어 UUID -> 소환된 요뽀뽀(Zombie) 객체 매핑
    private final Map<UUID, Zombie> yopopoMap = new HashMap<>();
    // 요뽀뽀가 현재 춤추고 있는지 여부를 저장
    private final Set<UUID> dancingYopopos = new HashSet<>();

    public Yopopo(JavaPlugin plugin) {
        super(plugin);
        startTauntTask();
        startPassiveTask();
    }

    @Override
    public String getCode() {
        return "054";
    }

    @Override
    public String getName() {
        return "요뽀뽀";
    }

    @Override
    public List<String> getDescription() {
        List<String> list = new ArrayList<>();
        list.add("§a복합 ● 요뽀뽀(갓슈벨!!/금색의 갓슈!!)");
        list.add(" ");
        list.add("§f귀여운 요뽀뽀이가 당신을 도와 싸웁니다!");
        return list;
    }

    @Override
    public void giveItem(Player p) {
        // 기존 요뽀뽀가 있다면 제거 (중복 소환 방지)
        cleanupYopopo(p.getUniqueId());

        // 1. 요뽀뽀 책 지급
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a요뽀뽀 책");
            meta.setLore(Arrays.asList(
                    "§7[좌클릭] 요뽀뽀 공격 명령",
                    "§7[우클릭] 요뽀뽀 춤 (5초간 주변 어그로, 피격 무시)"));
            meta.setCustomModelData(1); // 요뽀뽀 책 모델
            book.setItemMeta(meta);
        }
        p.getInventory().addItem(book);

        // 2. 요뽀뽀 소환
        Location spawnLoc = p.getLocation().clone().add(1, 0, 1);
        Zombie yopopo = spawnLoc.getWorld().spawn(spawnLoc, Zombie.class);
        yopopo.setBaby(); // 아기 좀비
        yopopo.setCustomName("§a요뽀뽀");
        yopopo.setCustomNameVisible(true);
        yopopo.setRemoveWhenFarAway(false);

        // 능력치 설정 (체력 100)
        if (yopopo.getAttribute(Attribute.MAX_HEALTH) != null) {
            yopopo.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100.0);
        }
        yopopo.setHealth(100.0);

        // 공격력 설정 (나무검 수준 = 보통 좀비 기본 공격력 부근, 약 3~4)
        if (yopopo.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            yopopo.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(4.0);
        }

        // 방어 속성 초기화
        if (yopopo.getAttribute(Attribute.ARMOR) != null) {
            yopopo.getAttribute(Attribute.ARMOR).setBaseValue(0.0);
        }

        // 초록색 가죽 갑옷 세팅
        ItemStack helmet = getColoredArmor(Material.LEATHER_HELMET, Color.GREEN);
        ItemStack chest = getColoredArmor(Material.LEATHER_CHESTPLATE, Color.GREEN);
        ItemStack legs = getColoredArmor(Material.LEATHER_LEGGINGS, Color.GREEN);
        ItemStack boots = getColoredArmor(Material.LEATHER_BOOTS, Color.GREEN);

        yopopo.getEquipment().setHelmet(helmet);
        yopopo.getEquipment().setChestplate(chest);
        yopopo.getEquipment().setLeggings(legs);
        yopopo.getEquipment().setBoots(boots);
        yopopo.getEquipment().setItemInMainHand(null); // 기본적으로 빈손

        // 장비 드롭 확률 0
        yopopo.getEquipment().setHelmetDropChance(0f);
        yopopo.getEquipment().setChestplateDropChance(0f);
        yopopo.getEquipment().setLeggingsDropChance(0f);
        yopopo.getEquipment().setBootsDropChance(0f);
        yopopo.getEquipment().setItemInMainHandDropChance(0f);

        // 타겟팅 무력화 (평소엔 공격 안함)
        yopopo.setTarget(null);

        // 맵에 저장
        yopopoMap.put(p.getUniqueId(), yopopo);

        // [추가] 킬 판정 연동 (GameManager에서 처리)
        yopopo.setMetadata("YopopoOwner",
                new org.bukkit.metadata.FixedMetadataValue(plugin, p.getUniqueId().toString()));

        // 3. 메시지 출력
        Bukkit.broadcastMessage("§a요뽀뽀: 요뽀뽀이~");

        // [디버그] 소환 실패 여부 확인 로그
        if (yopopo == null || !yopopo.isValid()) {
            p.sendMessage("§c[!] 요뽀뽀 소환에 실패했습니다.");
        }
    }

    private ItemStack getColoredArmor(Material mat, Color color) {
        ItemStack item = new ItemStack(mat);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§a복합 ● 요뽀뽀(갓슈벨!!/금색의 갓슈!!)");
        p.sendMessage("§f귀여운 요뽀뽀이가 당신을 도와 싸웁니다!");
        p.sendMessage("§f(요뽀뽀 체력 100)");
        p.sendMessage("§f[좌클릭] 요뽀뽀 책을 생명체를 바라보며 클릭 시 요뽀뽀가 라이터를 들고 돌진합니다.");
        p.sendMessage("§f[우클릭] 요뽀뽀가 5초간 춤을 춥니다. 춤을 출 동안엔 주변 적들이 요뽀뽀를 공격하게 유도하며,");
        p.sendMessage("§f춤추는 동안 요뽀뽀는 어떠한 데미지도 받지 않고 체력이 채워집니다.");
        p.sendMessage("§f");
        p.sendMessage("§f쿨타임 : 10초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 요뽀뽀 책");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        cleanupYopopo(p.getUniqueId());
    }

    private void cleanupYopopo(UUID uuid) {
        if (yopopoMap.containsKey(uuid)) {
            Zombie yopopo = yopopoMap.get(uuid);
            if (yopopo != null && yopopo.isValid()) {
                yopopo.remove();
            }
            yopopoMap.remove(uuid);
            dancingYopopos.remove(uuid);
        }
    }

    /**
     * 책 좌/우클릭 이벤트 처리
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // [추가] 능력이 봉인된 상태 (침묵)인지 체크
        if (isSilenced(p))
            return;
        if (!me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode())) {
            return;
        }

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.BOOK)
            return;
        if (!item.hasItemMeta() || !item.getItemMeta().getDisplayName().contains("요뽀뽀 책"))
            return;

        Zombie yopopo = yopopoMap.get(p.getUniqueId());
        if (yopopo == null || !yopopo.isValid() || yopopo.isDead()) {
            // 요뽀뽀가 죽으면 책이 없어져야 하지만, 오류로 남아있을 경우 수동 제거
            p.sendMessage("§c[!] 요뽀뽀가 곁에 없습니다.");
            return;
        }

        Action action = e.getAction();

        // [좌클릭] 공격 명령
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            e.setCancelled(true);

            // 춤 추는 중이라면 공격 명령 무시
            if (dancingYopopos.contains(yopopo.getUniqueId())) {
                p.sendMessage("§c요뽀뽀는 현재 춤을 추느라 바쁩니다!");
                return;
            }

            // 시야 내 엔티티 추적 (거리 제한 없음 -> 최대한 10000블럭)
            Entity targetUser = null;
            var trace = p.getWorld().rayTraceEntities(
                    p.getEyeLocation(),
                    p.getEyeLocation().getDirection(),
                    10000.0,
                    0.5,
                    ent -> ent != p && ent != yopopo && ent instanceof LivingEntity &&
                            (!(ent instanceof Player)
                                    || ((Player) ent).getGameMode() != org.bukkit.GameMode.SPECTATOR));

            if (trace != null && trace.getHitEntity() != null) {
                targetUser = trace.getHitEntity();
            }

            if (targetUser instanceof LivingEntity livingTarget) {
                // 공격 명령!
                yopopo.setTarget(livingTarget);
                yopopo.getEquipment().setItemInMainHand(new ItemStack(Material.FLINT_AND_STEEL));
                Bukkit.broadcastMessage("§a요뽀뽀: 요뽀뽀이!");

                // 명령 후 사운드 재생
                p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1f, 1.5f);
            } else {
                p.sendMessage("§c[!] 지시할 유효한 생명체가 없습니다.");
                // 명령 취소 및 무기 원상복구
                yopopo.setTarget(null);
                yopopo.getEquipment().setItemInMainHand(null);
            }
        }
        // [우클릭] 춤 명령
        else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);

            if (!checkCooldown(p))
                return;

            // 춤 발동
            startYopopoDance(yopopo);
            setCooldown(p, 10);
            Bukkit.broadcastMessage("§a요뽀뽀: 요뽀뽀이 또뽀뽀이 슈뽀뽀뽀이~!");
        }
    }

    /**
     * 요뽀뽀 춤 시전 (5초)
     */
    private void startYopopoDance(Zombie yopopo) {
        dancingYopopos.add(yopopo.getUniqueId());

        // 춤추는 동안 공격 중지 및 무기 해제
        yopopo.setTarget(null);
        yopopo.getEquipment().setItemInMainHand(null);

        // 저항 5 부여 (피격 면역)
        yopopo.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 5, 4, false, false));

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!yopopo.isValid() || yopopo.isDead() || ticks >= 100) { // 5초(100틱)
                    dancingYopopos.remove(yopopo.getUniqueId());
                    this.cancel();
                    return;
                }

                // 이동 금지를 위해 제자리 맴돌기 구현
                // 1초에 1바퀴 (20틱에 360도 = 1틱당 18도)
                Location loc = yopopo.getLocation();
                loc.setYaw(loc.getYaw() + 18f); // 빙글빙글
                yopopo.teleport(loc);

                // 파티클 (음표)
                yopopo.getWorld().spawnParticle(Particle.NOTE, loc.clone().add(0, 1, 0), 1, 0.5, 0.5, 0.5, 1.0);

                // 1초(20틱)마다 점프 및 체력 회복 (초당 15 회복)
                if (ticks % 20 == 0) {
                    yopopo.setVelocity(new Vector(0, 0.4, 0)); // 점프

                    // 체력 회복 로직
                    if (yopopo.getAttribute(Attribute.MAX_HEALTH) != null) {
                        double maxHealth = yopopo.getAttribute(Attribute.MAX_HEALTH).getValue();
                        yopopo.setHealth(Math.min(maxHealth, yopopo.getHealth() + 15.0));

                        // 회복 파티클 (하트)
                        yopopo.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, 1.5, 0), 3, 0.5, 0.5, 0.5,
                                0);
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 춤추는 동안 주변 엔티티를 도발하는 태스크 (주기적으로 실행)
     */
    private void startTauntTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!me.user.moc.MocPlugin.getInstance().isEnabled()) {
                    this.cancel();
                    return;
                }

                for (UUID ypUuid : dancingYopopos) {
                    Entity ypEntity = Bukkit.getEntity(ypUuid);
                    if (ypEntity instanceof Zombie yopopo && yopopo.isValid()) {
                        // 반경 150블럭 내 몬스터/동물/플레이어 시선 뺏기
                        for (Entity ent : yopopo.getNearbyEntities(150, 150, 150)) {
                            if (ent instanceof org.bukkit.entity.Mob mob && ent != yopopo) {
                                // 요뽀뽀(자신) 제외 몹 타겟팅
                                mob.setTarget(yopopo);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // 0.5초마다 타겟 강제 고정
    }

    /**
     * 요뽀뽀가 때렸을 때 불붙임 효과 제어
     */
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        // 요뽀뽀가 때린 경우
        if (e.getDamager() instanceof Zombie z) {
            // 주인이 지정된 요뽀뽀인지 확인
            if (isYopopo(z)) {
                // 손에 라이터를 들고 있다면 화염 효과 부여
                ItemStack hand = z.getEquipment().getItemInMainHand();
                if (hand != null && hand.getType() == Material.FLINT_AND_STEEL) {
                    e.getEntity().setFireTicks(20 * 5); // 5초 지속
                } else {
                    // 라이터를 안들고 있다면 공격 취소 (기본 공격 안함)
                    e.setCancelled(true);
                }
            }
        }

        // 어떤 대상이 요뽀뽀를 때린 경우 반격(Targeting) 금지
        if (e.getEntity() instanceof Zombie z && isYopopo(z)) {
            // 만약 춤추고 있다면 데미지 무시 (저항 5)
            if (dancingYopopos.contains(z.getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    /**
     * 요뽀뽀가 주인을 타겟팅하는 것을 원천 차단
     */
    @EventHandler
    public void onEntityTarget(org.bukkit.event.entity.EntityTargetEvent e) {
        if (e.getEntity() instanceof Zombie z && isYopopo(z)) {
            Player owner = getOwner(z);
            if (owner != null && e.getTarget() != null && e.getTarget().equals(owner)) {
                e.setCancelled(true);
            }
        }
    }

    /**
     * 무조건 요뽀뽀를 얌전하게 만드는 패시브 태스크. (때려도 반격 안함)
     */
    private void startPassiveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!me.user.moc.MocPlugin.getInstance().isEnabled()) {
                    this.cancel();
                    return;
                }
                for (Zombie z : yopopoMap.values()) {
                    if (z == null || !z.isValid() || z.isDead())
                        continue;

                    ItemStack hand = z.getEquipment().getItemInMainHand();
                    // 공격명령(라이터)도 없고, 춤도 안추면 평상시. 타겟 삭제
                    if ((hand == null || hand.getType() != Material.FLINT_AND_STEEL)
                            && !dancingYopopos.contains(z.getUniqueId())) {
                        if (z.getTarget() != null) {
                            z.setTarget(null);
                        }
                    } else if (hand != null && hand.getType() == Material.FLINT_AND_STEEL) {
                        // 공격대상을 못 찾았거나 대상이 없고, 춤을 안 추고 있다면 아무도 공격 안하게 변경
                        if (z.getTarget() == null || z.getTarget().isDead() || z.getTarget().equals(getOwner(z))) {
                            z.setTarget(null);
                            z.getEquipment().setItemInMainHand(null);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L); // 0.25초마다 타겟 강제 해제
    }

    /**
     * 요뽀뽀 사망 시 처리
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Zombie z && isYopopo(z)) {
            Player owner = getOwner(z);
            if (owner != null) {
                // 인벤토리에서 책 삭제
                for (int i = 0; i < owner.getInventory().getSize(); i++) {
                    ItemStack item = owner.getInventory().getItem(i);
                    if (item != null && item.getType() == Material.BOOK && item.hasItemMeta() &&
                            item.getItemMeta().getDisplayName() != null &&
                            item.getItemMeta().getDisplayName().contains("요뽀뽀 책")) {
                        owner.getInventory().removeItem(item);
                    }
                }
                Bukkit.broadcastMessage("§a요뽀뽀: *" + owner.getName() + " ...");

                // 맵에서 제거
                yopopoMap.remove(owner.getUniqueId());
                dancingYopopos.remove(z.getUniqueId());
            }
            e.getDrops().clear(); // 유품 방지
            e.setDroppedExp(0);
        }
    }

    /**
     * 특정 아기 좀비가 요뽀뽀인지 확인
     */
    private boolean isYopopo(Zombie z) {
        return yopopoMap.containsValue(z);
    }

    /**
     * 요뽀뽀의 주인을 찾습니다.
     */
    private Player getOwner(Zombie z) {
        for (Map.Entry<UUID, Zombie> entry : yopopoMap.entrySet()) {
            if (entry.getValue().equals(z)) {
                return Bukkit.getPlayer(entry.getKey());
            }
        }
        return null;
    }
}
