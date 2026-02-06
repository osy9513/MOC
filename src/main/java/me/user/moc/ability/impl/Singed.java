package me.user.moc.ability.impl;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

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

            // 방어력 3 설정 (AttributeModifier)
            NamespacedKey armorKey = new NamespacedKey(plugin, "singed_mask_armor");
            AttributeModifier armorMod = new AttributeModifier(
                    armorKey,
                    3.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.HEAD // 머리에 꼈을 때만 적용
            );

            // [▼▼▼ 여기서부터 변경됨 ▼▼▼]
            // 설명: 보내주신 Attribute 클래스에 GENERIC_ARMOR 대신 ARMOR로 정의되어 있어 수정했습니다.
            meta.addAttributeModifier(Attribute.ARMOR, armorMod);
            // [▲▲▲ 여기까지 변경됨 ▲▲▲]

            gasMask.setItemMeta(meta);
        }
        p.getInventory().addItem(gasMask);

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
        p.sendMessage("§f가스에 닿은 적(및 방독면 없는 자신)은 2초간 독 3에 걸립니다.");
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
    }

    /**
     * 신지드 메인 로직 (매 틱 실행)
     */
    private void startSingedLogic(Player p) {
        singedTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    this.cancel();
                    return;
                }

                long currentTime = System.currentTimeMillis();

                // 1. [기본 패시브] 신속 2 상시 유지 (신속 6 발동 중이 아닐 때만)
                // 신속 6(Amplifier 5)이 적용 중이면 덮어씌우지 않음.
                if (speedBoostTicks <= 0) {
                    // PotionEffectType.SPEED, duration 20, amplifier 1 (신속2)
                    // 지속시간을 짧게 계속 갱신
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false, false));
                } else {
                    // 패시브(충돌) 효과: 신속 6 (Amplifier 5)
                    speedBoostTicks--;
                    // 효과 부여 (이미 있으면 갱신됨)
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10, 5, false, false, true));

                    // [이펙트] 몸에 파랑 파티클 (오라)
                    p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3,
                            new Particle.DustOptions(Color.BLUE, 1.0f));
                }

                // 2. [맹독의 자취] 가스 생성
                // 현재 위치 저장
                poisonTrail.add(new GasCloud(p.getLocation()));

                // 3. [맹독의 자취] 가스 관리 (오래된 가스 제거 및 효과 부여)
                Iterator<GasCloud> iterator = poisonTrail.iterator();
                while (iterator.hasNext()) {
                    GasCloud cloud = iterator.next();

                    // 3초(3000ms) 지났으면 제거
                    if (currentTime - cloud.timestamp > 3000) {
                        iterator.remove();
                        continue;
                    }

                    // 가스 시각 효과 (초록색 가스)
                    // 성능을 위해 매 틱마다 모든 좌표에 뿌리면 많을 수 있으니, 확률적으로 뿌리거나 듬성듬성 뿌림
                    // 여기서는 간단히 매번 뿌리되 개수를 적게
                    cloud.location.getWorld().spawnParticle(Particle.ENTITY_EFFECT, cloud.location.add(0, 0.5, 0),
                            0, 0.2, 0.8, 0.2, Color.GREEN);
                    // ENTITY_EFFECT + Color 사용 시 count 0, offset을 색상(RGB)처럼 사용하지 않고 data로 Color 넘김
                    // (1.13+)

                    // 주변 적 감지 (범위 1.0)
                    for (Entity entity : cloud.location.getWorld().getNearbyEntities(cloud.location, 1.0, 1.0, 1.0)) {
                        if (!(entity instanceof LivingEntity victim))
                            continue;

                        // 본인 체크
                        if (victim.equals(p)) {
                            // 방독면 착용 여부 확인
                            ItemStack helmet = p.getInventory().getHelmet();
                            boolean hasMask = helmet != null && helmet.getType() == Material.NETHER_BRICK_FENCE;

                            if (!hasMask) {
                                victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 2)); // 독 3, 2초
                            }
                        } else {
                            // 적군
                            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 2)); // 독 3, 2초
                        }
                    }
                }

                // 4. [충돌 패시브] 다른 생명체와 부딪침 감지
                // getNearbyEntities로 본인 주변 0.8칸 탐색
                boolean collided = false;
                for (Entity entity : p.getNearbyEntities(0.8, 0.8, 0.8)) {
                    if (entity instanceof LivingEntity && !entity.equals(p)) {
                        collided = true;
                        break;
                    }
                }

                if (collided) {
                    // 부딪힘 발생!
                    if (speedBoostTicks <= 0) { // 이미 부스팅 중이 아닐 때 '팡' 효과
                        p.getWorld().spawnParticle(Particle.FLASH, p.getLocation().add(0, 1, 0), 1); // 번쩍
                        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5,
                                new Particle.DustOptions(Color.BLUE, 2.0f)); // 파란 팡
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.5f);
                    }

                    // 3초(60틱)간 가속
                    speedBoostTicks = 60;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 방독면(네더 벽돌 울타리) 우클릭 착용 리스너
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();

        // 1. 아이템 체크
        if (item == null || item.getType() != Material.NETHER_BRICK_FENCE)
            return;

        // 2. 액션 체크 (우클릭)
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        // 3. 능력자 체크
        if (!me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 4. 착용 로직
        e.setCancelled(true); // 설치 방지

        ItemStack currentHelmet = p.getInventory().getHelmet();

        // 방독면을 머리에 씌움 (개수 1개 차감)
        ItemStack maskToWear = item.clone();
        maskToWear.setAmount(1);

        p.getInventory().setHelmet(maskToWear);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1f, 1f);

        // 손에 있는 아이템 차감
        item.setAmount(item.getAmount() - 1);

        // 기존에 투구가 있었다면 인벤토리로 되돌려줌 (혹은 바닥에 드롭)
        if (currentHelmet != null && currentHelmet.getType() != Material.AIR) {
            p.getInventory().addItem(currentHelmet);
        }
    }
}