package me.user.moc.ability.impl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import me.user.moc.ability.Ability;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public class Singed extends Ability {

    // [맹독의 자취] 가스 위치 정보를 저장하는 클래스
    private static class GasCloud {
        Location location;
        long timestamp; // 생성된 시간

        public GasCloud(Location location) {
            this.location = location;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // 플레이어별 가스 자취 리스트 (신지드 UUID -> 가스 리스트)
    private final LinkedList<GasCloud> poisonTrail = new LinkedList<>();

    // 신지드 로직 태스크
    private BukkitTask singedTask;

    // 패시브(신속6) 활성화 남은 시간 (틱 단위)
    private int speedBoostTicks = 0;

    public Singed(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "033";
    }

    @Override
    public String getName() {
        return "신지드";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c전투 ● 신지드(리그 오브 레전드)",
                "§f맹독의 자취를 활성화합니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 1. 철 검 제거
        p.getInventory().remove(Material.IRON_SWORD);

        // 2. 방독면 지급 (네더 벽돌 울타리)
        ItemStack gasMask = new ItemStack(Material.NETHER_BRICK_FENCE);
        ItemMeta meta = gasMask.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5방독면");
            meta.setLore(Arrays.asList("§7우클릭하여 착용 가능", "§7독 면역 제공", "§9방어력 +3"));
            meta.setCustomModelData(1); // 리소스팩: singed

            // 방어력 3 설정
            NamespacedKey armorKey = new NamespacedKey(plugin, "singed_mask_armor");
            AttributeModifier armorMod = new AttributeModifier(
                    armorKey,
                    3.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.HEAD);

            meta.addAttributeModifier(Attribute.ARMOR, armorMod);
            gasMask.setItemMeta(meta);
        }
        // [수정] 방독면을 투구 슬롯에 장착
        p.getInventory().setHelmet(gasMask);

        // 3. 채팅 메시지
        p.getServer().broadcast(Component.text("섞을까 말까. 그것이 문제로다.")
                .color(TextColor.color(0x00FF00))); // 밝은 초록색

        // 4. 메인 로직 시작
        startSingedLogic(p);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 신지드");
        p.sendMessage("§f항상 신속 2 상태를 유지합니다.");
        p.sendMessage("§f이동하는 모든 경로에 3초간 지속되는 맹독 가스를 남깁니다.");
        p.sendMessage("§f가스에 닿은 적(및 방독면 없는 자신)은 2초간 독 5에 걸립니다.");
        p.sendMessage("§f다른 생명체와 부딪히면 3초간 신속 6을 얻습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 방독면");
        p.sendMessage("§f장비 제거 : 철 검");
    }

    @Override
    public void cleanup(Player p) {
        if (singedTask != null && !singedTask.isCancelled()) {
            singedTask.cancel();
        }
        poisonTrail.clear();
        speedBoostTicks = 0;

        // 효과 제거
        p.removePotionEffect(PotionEffectType.SPEED);

        super.cleanup(p);
        // [Fix] 독 구름 데이터 초기화 (라운드 종료 시 잔존 방지)
        poisonTrail.clear();
    }

    private void startSingedLogic(Player p) {
        singedTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 1. 플레이어 상태 체크
                if (!p.isOnline() || p.isDead()) {
                    this.cancel();
                    return;
                }

                // 전투 시작 전에는 작동 X
                if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted()) {
                    return;
                }

                // 무적 상태 체크 (무적이고 크리에이티브가 아니면 작동 X)
                if (p.isInvulnerable() && p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    return;
                }

                long currentTime = System.currentTimeMillis();

                // 2. [패시브] 신속 관리
                if (speedBoostTicks > 0) {
                    speedBoostTicks--;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, 5, true, true, true));

                    // [이펙트] 몸에 파랑 파티클 (오라)
                    p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3,
                            new Particle.DustOptions(Color.BLUE, 1.0f));
                } else {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, true, true, true));
                }

                // 3. [맹독의 자취] 가스 생성
                poisonTrail.add(new GasCloud(p.getLocation()));

                // 4. [맹독의 자취] 가스 관리
                Iterator<GasCloud> iterator = poisonTrail.iterator();
                Set<UUID> poisonedEntities = new HashSet<>();

                while (iterator.hasNext()) {
                    GasCloud cloud = iterator.next();

                    // 3초 지났으면 제거
                    if (currentTime - cloud.timestamp > 3000) {
                        iterator.remove();
                        continue;
                    }

                    // [▼▼▼ 파티클 수정됨 ▼▼▼]
                    // 1. 전체 양을 70%로 줄임 (Math.random() < 0.7)
                    // 2. 30% 확률로 보라색, 70% 확률로 초록색 파티클 출력
                    if (Math.random() < 0.7) {
                        if (Math.random() < 0.3) {
                            // 보라색 독 가스
                            cloud.location.getWorld().spawnParticle(Particle.DUST,
                                    cloud.location.clone().add(0, 0.2, 0),
                                    1, 0.3, 0.3, 0.3,
                                    new Particle.DustOptions(Color.PURPLE, 2.0f));
                        } else {
                            // 초록색(라임) 독 가스
                            cloud.location.getWorld().spawnParticle(Particle.DUST,
                                    cloud.location.clone().add(0, 0.2, 0),
                                    1, 0.3, 0.3, 0.3,
                                    new Particle.DustOptions(Color.LIME, 2.0f));
                        }
                    }
                    // [▲▲▲ 여기까지 수정됨 ▲▲▲]

                    // 주변 적 감지 (범위 1.5)
                    for (Entity entity : cloud.location.getWorld().getNearbyEntities(cloud.location, 1.5, 1.5, 1.5)) {
                        if (!(entity instanceof LivingEntity victim))
                            continue;

                        // [Fix] 관전자는 대상에서 제외
                        if (victim instanceof Player pVictim && pVictim.getGameMode() == org.bukkit.GameMode.SPECTATOR)
                            continue;

                        if (poisonedEntities.contains(victim.getUniqueId()))
                            continue;

                        // 방독면 체크
                        ItemStack helmet = victim.getEquipment().getHelmet();
                        if (helmet != null && helmet.getType() == Material.NETHER_BRICK_FENCE) {
                            continue; // 면역
                        }

                        // 독 딜 씹힘 방지
                        PotionEffect currentPoison = victim.getPotionEffect(PotionEffectType.POISON);
                        if (currentPoison != null && currentPoison.getDuration() > 10) {
                            poisonedEntities.add(victim.getUniqueId());
                            continue;
                        }

                        // 독 부여
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 4));
                        poisonedEntities.add(victim.getUniqueId());
                    }
                }

                // 5. [충돌 패시브]
                boolean collided = false;
                for (Entity entity : p.getNearbyEntities(1.2, 1.2, 1.2)) {
                    if (entity instanceof LivingEntity && !entity.equals(p)) {
                        // [Fix] 관전자는 충돌 대상에서 제외
                        if (entity instanceof Player pEntity && pEntity.getGameMode() == org.bukkit.GameMode.SPECTATOR)
                            continue;

                        collided = true;
                        break;
                    }
                }

                if (collided) {
                    if (speedBoostTicks <= 0) {
                        p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(0, 1, 0), 15, 0.3, 0.5,
                                0.3, 0.05);
                        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5);
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 2.0f);
                    }
                    speedBoostTicks = 60;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();

        // 게임중이고, 해당 아이템 이름이 방독면일 때만 장착되게 조건 추가
        if (item == null || item.getType() != Material.NETHER_BRICK_FENCE)
            return;

        if (!item.hasItemMeta() || item.getItemMeta().getCustomModelData() != 1
                || !"§5방독면".equals(item.getItemMeta().getDisplayName()))
            return;

        if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        // 착용 로직
        e.setCancelled(true);

        ItemStack currentHelmet = p.getInventory().getHelmet();
        ItemStack maskToWear = item.clone();
        maskToWear.setAmount(1);

        p.getInventory().setHelmet(maskToWear);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1f, 1f);

        item.setAmount(item.getAmount() - 1);

        if (currentHelmet != null && currentHelmet.getType() != Material.AIR) {
            p.getInventory().addItem(currentHelmet);
        }
    }
}