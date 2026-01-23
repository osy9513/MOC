package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Ranga extends Ability {

    // 플레이어의 UUID와 소환된 늑대(란가)를 매핑하여 관리
    private final Map<UUID, Wolf> wolves = new HashMap<>();

    public Ranga(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "006";
    }

    @Override
    public String getName() {
        return "란가";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§c전투 ● 란가(전생슬)",
                "§f라운드 시작 시 든든한 늑대 '란가'를 소환합니다.",
                "§f란가는 강력한 전투력과 번개 능력을 가졌습니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 란가(전생했더니 슬라임이었던 건에 대하여)");
        p.sendMessage("라운드를 시작하면 란가를 옆에 소환합니다.");
        p.sendMessage("란가는 덩치 큰 검은 늑대이며, 체력 3줄(60)에 늑대 갑옷을 입고,");
        p.sendMessage("영구 지속 이속 2 버프를 보유했습니다.");
        p.sendMessage(" ");
        p.sendMessage("란가의 공격력은 체력 5칸이며 공격 시 40% 확률로 번개가 칩니다.");
        p.sendMessage("란가는 주인을 공격하지 않으며, 주인의 적을 함께 공격합니다.");
        p.sendMessage(" ");
        p.sendMessage("란가가 사망 시 주인의 그림자에 들어가 주인에게 영구지속 이속 2 버프를 줍니다.");
        p.sendMessage(" ");
        p.sendMessage("쿨타임 : 0초.");
        p.sendMessage("---");
        p.sendMessage("추가 장비: 없음.");
        p.sendMessage("장비 제거: 없음.");
    }

    @Override
    public void cleanup(Player p) {
        if (wolves.containsKey(p.getUniqueId())) {
            Wolf wolf = wolves.get(p.getUniqueId());
            if (wolf != null && !wolf.isDead()) {
                wolf.remove(); // 늑대 제거
                // 효과음은 선택 (조용히 사라지는게 잔재 처리 느낌)
            }
            wolves.remove(p.getUniqueId());
            p.sendMessage("§8[MOC] §7란가가 그림자 속으로 돌아갔습니다.");
        }
    }

    @Override
    public void giveItem(Player p) {
        // 기존에 소환된 늑대가 있다면 제거 (중복 소환 방지)
        if (wolves.containsKey(p.getUniqueId())) {
            Wolf oldWolf = wolves.get(p.getUniqueId());
            if (oldWolf != null && !oldWolf.isDead()) {
                oldWolf.remove();
            }
        }

        Location loc = p.getLocation();
        Wolf ranga = (Wolf) p.getWorld().spawnEntity(loc, EntityType.WOLF);

        // 늑대 스펙 설정
        ranga.setOwner(p);
        ranga.setTamed(true);
        ranga.setAdult();
        ranga.customName(Component.text("란가").color(NamedTextColor.RED));
        ranga.setVariant(org.bukkit.entity.Wolf.Variant.BLACK);
        ranga.setCustomNameVisible(true);
        // ranga.setInterested(true); // 고개를 갸웃거리는 상태 (선택)

        // 갑옷 착용 (EquipmentSlot.BODY 사용)
        if (ranga.getEquipment() != null) {
            ranga.getEquipment().setItem(org.bukkit.inventory.EquipmentSlot.BODY, new ItemStack(Material.WOLF_ARMOR));
        }

        // 속성 설정: 체력 60 (하트 30칸 = 3줄) - 1.21.2+ Attribute 명칭 변경
        if (ranga.getAttribute(Attribute.MAX_HEALTH) != null) {
            ranga.getAttribute(Attribute.MAX_HEALTH).setBaseValue(60.0);
        }
        ranga.setHealth(60.0);

        // 속성 설정: 공격력 10 (하트 5칸)
        if (ranga.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            ranga.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(10.0);
        }

        // 속성 설정: 크기 2배 (1.20.5+ SCALE)
        if (ranga.getAttribute(Attribute.SCALE) != null) {
            ranga.getAttribute(Attribute.SCALE).setBaseValue(2.0);
        }

        // 버프: 이속 2 (Amplifier 1)
        ranga.addPotionEffect(
                new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false));
        // 버프: 화염 저항
        ranga.addPotionEffect(
                new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 100, false, false));

        // 맵에 등록
        wolves.put(p.getUniqueId(), ranga);

        // 소환 효과: 검은 연기
        p.getWorld().spawnParticle(Particle.SMOKE, loc, 50, 0.5, 1, 0.5, 0.1);
        p.sendMessage("§8[§c!§8] §f그림자 속에서 란가가 나타났습니다!");
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        // 1. 란가가 공격할 때
        if (e.getDamager() instanceof Wolf wolf && wolves.containsValue(wolf)) {
            // 번개 확률 40%
            if (Math.random() < 0.4) {
                Entity target = e.getEntity();
                target.getWorld().strikeLightning(target.getLocation());

                // 주인 찾기 (메시지 전송용)
                Player owner = (Player) wolf.getOwner();
                if (owner != null) {
                    owner.sendMessage("§b데스 스톰!");
                }
            }
        }

        // 2. 주인이 공격하거나 맞을 때 타겟 설정 동기화는 늑대 기본 AI가 해주지만, 확실하게 하기 위해 추가 가능
        // (여기서는 기본 AI를 신뢰하고 생략하되, 란가가 주인을 공격하는 것만 막음)

        // 란가가 주인을 공격하려는 경우 (광역기 오인 등) 방지
        if (e.getDamager() instanceof Wolf wolf && wolves.containsValue(wolf)) {
            if (e.getEntity().equals(wolf.getOwner())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Wolf wolf && wolves.containsValue(wolf)) {
            // 주인을 찾아서 버프 지급
            Player owner = (Player) wolf.getOwner();
            if (owner != null && owner.isOnline()) {
                owner.sendMessage("§8[§c!§8] §f란가가 그림자 속으로 돌아왔습니다.");
                owner.sendMessage("§7(이동 속도 증가 버프 획득)");

                // 영구 지속 이속 2 부여
                owner.addPotionEffect(
                        new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false));

                // 효과음: 바람 소리 (BREEZE_WIND_BURST or ELYTRA_FLY 등으로 대체)
                owner.playSound(owner.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1f);

                // 검은 연기 출력
                wolf.getWorld().spawnParticle(Particle.SMOKE, wolf.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);

                // 맵에서 제거
                wolves.remove(owner.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        // 번개 데미지 무효화 로직
        if (e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.LIGHTNING) {
            // 1. 피해자가 란가인 경우
            if (e.getEntity() instanceof Wolf wolf && wolves.containsValue(wolf)) {
                e.setCancelled(true);
            }
            // 2. 피해자가 란가의 주인인 경우
            if (e.getEntity() instanceof Player p && wolves.containsKey(p.getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }
}
