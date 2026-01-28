package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class Naruto extends Ability {

    public Naruto(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "007";
    }

    @Override
    public String getName() {
        return "나루토";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§e복합 ● 나루토(나루토)",
                "§f금술 두루마리를 우클릭해 §e다중 그림자 분신술§f을 사용합니다.",
                "§f12명의 분신이 주변의 모든 생명체를 공격합니다.");
    }

    @Override
    public void giveItem(Player p) {
        ItemStack item = new ItemStack(Material.ORANGE_BANNER);
        var meta = item.getItemMeta();
        meta.setDisplayName("§6금술 두루마리");
        meta.setLore(List.of("§7우클릭 시 다중 그림자 분신술을 사용합니다."));
        item.setItemMeta(meta);
        p.getInventory().addItem(item);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e[나루토] §f다중 그림자 분신술");
        p.sendMessage("§7- 금술 두루마리 우클릭 시 분신 12명을 소환합니다.");
        p.sendMessage("§7- 분신은 좀비 AI를 가지며, 사용자를 제외한 모든 생명체를 공격합니다.");
        p.sendMessage("§7- 분신은 죽기 전까지 사라지지 않습니다.");
        p.sendMessage("§7- 쿨타임: 30초");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!hasAbility(p))
            return;

        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack hand = e.getItem();
            if (hand != null && hand.getType() == Material.ORANGE_BANNER) {
                e.setCancelled(true);

                if (checkCooldown(p)) {
                    spawnClones(p);
                    setCooldown(p, 30);
                }
            }
        }
    }

    private void spawnClones(Player p) {
        p.sendMessage("§e나루토 : 다중 그림자 분신술!");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.5f);

        // 연기 효과 (2초간)
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 40) { // 2초
                    this.cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.05);
                count += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // 분신 12명 소환
        for (int i = 0; i < 12; i++) {
            Zombie clone = (Zombie) p.getWorld().spawnEntity(p.getLocation(), EntityType.ZOMBIE);

            // 기본 설정
            clone.setAdult();
            clone.setCustomName("§e나루토의 분신");
            clone.setCustomNameVisible(false);
            clone.setMetadata("NarutoOwner", new FixedMetadataValue(plugin, p.getUniqueId().toString()));
            clone.setCanPickupItems(false);

            // 능력치 조정
            clone.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(3.0);

            // [수정] 장비 복제 (머리, 갑옷, 양손)
            // 갑옷 복사
            ItemStack[] armorContents = p.getInventory().getArmorContents();
            ItemStack[] cloneArmor = new ItemStack[4];
            for (int j = 0; j < 4; j++) {
                if (armorContents[j] != null)
                    cloneArmor[j] = armorContents[j].clone();
            }
            clone.getEquipment().setArmorContents(cloneArmor);

            // 머리는 플레이어 스킨 강제 적용 (헬멧이 덮어씌워질 수 있으므로 확인 필요하지만, 요청상 스킨 머리가 중요하면 헬멧 자리에 덮어씀)
            // 사용자 요청: "내 캐릭터가 여러명 소환되는 것" -> 갑옷을 입고 있다면 갑옷을 입는게 더 비슷함.
            // 하지만 얼굴 식별을 위해 헬멧 대신 머리를 씌우길 원할 수도 있음.
            // 여기서는 "내 캐릭터(장비 포함)"를 원했으므로 갑옷을 우선하되, 헬멧이 없다면 머리를 씌우는 식으로?
            // 아니면 그냥 플레이어 머리를 씌우고 나머지만 갑옷?
            // 보통 "분신"은 얼굴이 보여야 하므로 헬멧 자리에 머리를 씌우고 나머지 갑옷을 입히는게 가장 분신다움.

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
            skullMeta.setOwningPlayer(p);
            head.setItemMeta(skullMeta);
            clone.getEquipment().setHelmet(head); // 헬멧은 항상 머리로 고정

            // 손 아이템 복사
            if (p.getInventory().getItemInMainHand() != null)
                clone.getEquipment().setItemInMainHand(p.getInventory().getItemInMainHand().clone());
            if (p.getInventory().getItemInOffHand() != null)
                clone.getEquipment().setItemInOffHand(p.getInventory().getItemInOffHand().clone());

            clone.getEquipment().setHelmetDropChance(0f);
            clone.getEquipment().setChestplateDropChance(0f);
            clone.getEquipment().setLeggingsDropChance(0f);
            clone.getEquipment().setBootsDropChance(0f);
            clone.getEquipment().setItemInMainHandDropChance(0f);
            clone.getEquipment().setItemInOffHandDropChance(0f);

            // 타겟팅 AI (모든 생명체 공격)
            startAI(p, clone);

            // 통합 관리 등록
            registerSummon(p, clone);
        }
    }

    /**
     * [중요] 분신이 주인을 공격하지 못하도록 막는 이벤트
     */
    @EventHandler
    public void onTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent e) {
        if (e.getEntity() instanceof Zombie zombie && e.getTarget() instanceof Player targetPlayer) {
            // 이 좀비가 나루토 분신인지 확인
            if (zombie.hasMetadata("NarutoOwner")) {
                String ownerUUID = zombie.getMetadata("NarutoOwner").get(0).asString();
                // 타겟이 주인이라면 공격 취소
                if (targetPlayer.getUniqueId().toString().equals(ownerUUID)) {
                    e.setCancelled(true);
                }
            }
        }
    }

    private void startAI(Player owner, Zombie clone) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (clone.isDead() || !clone.isValid()) {
                    this.cancel();
                    return;
                }

                // 타겟이 없거나 죽었으면 새로운 타겟 탐색
                if (clone.getTarget() == null || clone.getTarget().isDead() || clone.getTarget().equals(owner)) {
                    LivingEntity nearest = null;
                    double minDistance = 20.0; // 감지 범위

                    for (org.bukkit.entity.Entity e : clone.getNearbyEntities(minDistance, 10, minDistance)) {
                        if (e instanceof LivingEntity target && !e.equals(owner)
                                && !(e instanceof Zombie && e.hasMetadata("NarutoOwner"))) {
                            double dist = e.getLocation().distance(clone.getLocation());
                            if (dist < minDistance) {
                                minDistance = dist;
                                nearest = target;
                            }
                        }
                    }

                    if (nearest != null) {
                        clone.setTarget(nearest);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1초마다 타겟 갱신 확인
    }

    private boolean hasAbility(Player p) {
        return me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode());
    }
}
