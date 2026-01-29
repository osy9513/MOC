package me.user.moc.ability.impl;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import me.user.moc.ability.Ability;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public class Ranga extends Ability {

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
                "§c전투 ● 란가(전생했더니슬라임이었던건에대하여)",
                "§f라운드 시작 시 든든한 늑대 '란가'를 소환합니다.",
                "§f란가는 강력한 전투력과 번개 능력을 가졌습니다.");
    }

    @Override
    public void detailCheck(Player p) {
        // [디테일 정보 출력] 사용자 요청 포맷에 맞게 수정됨
        p.sendMessage("§c전투 ㆍ 란가(전생했더니 슬라임이었던 건에 대하여)");
        p.sendMessage("라운드를 시작하면 옆에 강력한 늑대 '란가'를 소환합니다.");
        p.sendMessage("란가는 영구적인 이동 속도 II 버프를 가진 검은 늑대이며,");
        p.sendMessage("공격 시 40% 확률로 강력한 번개를 떨어뜨려 적을 공격합니다.");
        p.sendMessage("란가가 사망하면 주인의 그림자로 돌아가 주인에게 이동 속도 II를 부여합니다.");
        p.sendMessage(" ");
        p.sendMessage("쿨타임 : 0초");
        p.sendMessage("---");
        p.sendMessage("추가 장비 : 없음");
        p.sendMessage("장비 제거 : 없음");
    }

    @Override
    public void cleanup(Player p) {
        // 부모의 cleanup을 호출하는 것이 좋지만, 현재는 부모 cleanup이 비어있음.
        // 다만 activeEntities를 부모가 관리하므로, 여기서 직접 지우는 대신
        // reset() 개념이 아니라 '이 능력자만 정리'하는 것이므로 직접 지워줌.
        List<Entity> list = activeEntities.get(p.getUniqueId());
        if (list != null && !list.isEmpty()) {
            for (Entity e : list) {
                e.remove();
            }
            list.clear();
            p.sendMessage("§8[MOC] §7란가가 그림자 속으로 돌아갔습니다.");
        }
    }

    @Override
    public void giveItem(Player p) {
        // 기존에 소환된 늑대가 있다면 제거 (중복 소환 방지)
        List<Entity> list = activeEntities.get(p.getUniqueId());
        if (list != null) {
            for (Entity e : list) {
                e.remove();
            }
            list.clear();
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

        // 속성 설정: 체력 60
        if (ranga.getAttribute(Attribute.MAX_HEALTH) != null) {
            ranga.getAttribute(Attribute.MAX_HEALTH).setBaseValue(60.0);
        }
        ranga.setHealth(60.0);

        // 속성 설정: 공격력 10
        if (ranga.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            ranga.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(10.0);
        }

        // 속성 설정: 크기 2배
        if (ranga.getAttribute(Attribute.SCALE) != null) {
            ranga.getAttribute(Attribute.SCALE).setBaseValue(2.0);
        }

        // 버프
        ranga.addPotionEffect(
                new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false));
        ranga.addPotionEffect(
                new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 100, false, false));

        // 맵에 등록 (부모 메서드 사용)
        registerSummon(p, ranga);

        // 소환 효과: 검은 연기
        p.getWorld().spawnParticle(Particle.SMOKE, loc, 50, 0.5, 1, 0.5, 0.1);
        p.sendMessage("§8[§c!§8] §f그림자 속에서 란가가 나타났습니다!");
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        // 1. 란가가 공격할 때
        if (e.getDamager() instanceof Wolf wolf && wolf.getOwner() instanceof Player owner) {
            List<Entity> list = activeEntities.get(owner.getUniqueId());
            if (list != null && list.contains(wolf)) {
                // 번개 확률 40%
                if (Math.random() < 0.4) {
                    Entity target = e.getEntity();
                    target.getWorld().strikeLightning(target.getLocation());
                    // 모든 사람에게 하늘색(AQUA)으로 메시지 전송
                    owner.getServer().broadcast(
                            Component.text("란가 : 데스 스톰!")
                                    .color(net.kyori.adventure.text.format.NamedTextColor.AQUA));
                }
            }
        }

        // 2. 란가가 주인을 공격하려는 경우 방지
        if (e.getDamager() instanceof Wolf wolf && wolf.getOwner() instanceof Player owner) {
            List<Entity> list = activeEntities.get(owner.getUniqueId());
            if (list != null && list.contains(wolf)) {
                if (e.getEntity().equals(owner)) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Wolf wolf && wolf.getOwner() instanceof Player owner) {
            List<Entity> list = activeEntities.get(owner.getUniqueId());
            if (list != null && list.contains(wolf)) {
                if (owner.isOnline()) {
                    owner.sendMessage("§8[§c!§8] §f란가가 그림자 속으로 돌아왔습니다.");
                    owner.sendMessage("§7(이동 속도 증가 버프 획득)");

                    // 영구 지속 이속 2 부여
                    owner.addPotionEffect(
                            new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false));
                    owner.playSound(owner.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1f);
                    wolf.getWorld().spawnParticle(Particle.SMOKE, wolf.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);

                    // 맵에서 제거
                    list.remove(wolf);
                }
            }
        }
    }

    @EventHandler
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        // 번개 데미지 무효화 로직
        if (e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.LIGHTNING) {
            // 1. 피해자가 란가인 경우
            if (e.getEntity() instanceof Wolf wolf && wolf.getOwner() instanceof Player owner) {
                List<Entity> list = activeEntities.get(owner.getUniqueId());
                if (list != null && list.contains(wolf)) {
                    e.setCancelled(true);
                }
            }
            // 2. 피해자가 란가의 주인인 경우
            if (e.getEntity() instanceof Player p && activeEntities.containsKey(p.getUniqueId())) {
                List<Entity> list = activeEntities.get(p.getUniqueId());
                if (list != null && !list.isEmpty()) { // 란가가 존재하면 면역
                    e.setCancelled(true);
                }
            }
        }
    }
}
