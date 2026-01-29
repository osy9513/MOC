package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

// [▼▼▼ 여기서부터 변경됨: 1.21.11에서 사라진 ResolvableProfile을 제거하고 PlayerProfile로 대체 ▼▼▼]
import org.bukkit.profile.PlayerProfile; // 플레이어의 얼굴(스킨) 정보를 담는 부품
import java.lang.reflect.Method; // 컴퓨터가 실행 중에 스스로 부품을 찾게 만드는 도구 (리플렉션)
// [▲▲▲ 여기까지 변경됨 ▲▲▲]

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
        // 나루토 상징인 주황색 현수막(두루마리) 지급
        ItemStack item = new ItemStack(Material.ORANGE_BANNER);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6금술 두루마리");
            meta.setLore(List.of("§7우클릭 시 다중 그림자 분신술을 사용합니다."));
            item.setItemMeta(meta);
        }
        p.getInventory().addItem(item);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e복합 ● 나루토(나루토)");
        p.sendMessage("§f금술 두루마리를 우클릭하여 '다중 그림자 분신술'을 사용합니다.");
        p.sendMessage("§f플레이어와 동일한 모습의 분신 12명을 즉시 소환하여 전장을 장악합니다.");
        p.sendMessage("§f분신은 적을 추격하여 공격하며, 파괴되기 전까지 계속해서 적을 압박합니다.");
        p.sendMessage(" ");
        p.sendMessage("§7쿨타임 : 30초");
        p.sendMessage("---");
        p.sendMessage("§7추가 장비 : 금술 두루마리(오렌지색 현수막)");
        p.sendMessage("§7장비 제거 : 없음");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!hasAbility(p))
            return;

        // 두루마리(오렌지 배너) 우클릭 체크
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack hand = e.getItem();
            if (hand != null && hand.getType() == Material.ORANGE_BANNER) {
                e.setCancelled(true); // 땅에 설치되는 것 방지

                if (checkCooldown(p)) {
                    spawnClones(p);
                    setCooldown(p, 30);
                }
            }
        }
    }

    private void spawnClones(Player p) {
        p.getServer().broadcastMessage("§e나루토 : 다중 그림자 분신술!");
        // 소환 시 효과음 (아이템 줍는 소리를 변조해서 닌자 느낌 연출)
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.5f);

        // 펑! 하는 연기 효과 (10번 반복)
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 10) {
                    this.cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.02);
                count++;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // 12명의 분신 소환 루프
        for (int i = 0; i < 12; i++) {
            double offsetX = (Math.random() - 0.5) * 6; // 반경 3미터 내 무작위 위치
            double offsetZ = (Math.random() - 0.5) * 6;
            org.bukkit.Location spawnLoc = p.getLocation().add(offsetX, 0, offsetZ);
            spawnLoc.setY(p.getWorld().getHighestBlockYAt(spawnLoc) + 1);

            // [▼▼▼ 여기서부터 변경됨: 1.21.11 전용 엔티티 'MANNEQUIN' 소환 ▼▼▼]
            // 마네킹은 플레이어의 스킨을 완벽하게 복제할 수 있는 새로운 엔티티입니다.
            Mannequin clone = (Mannequin) p.getWorld().spawnEntity(spawnLoc, EntityType.MANNEQUIN);

            try {
                // 플레이어의 실제 스킨 정보를 가져옵니다.
                PlayerProfile profile = p.getPlayerProfile();

                // 마네킹에게 주인(디렉터님)의 스킨을 씌웁니다.
                // 1.21.11에서 가장 안정적인 메서드를 찾아서 실행합니다.
                Method setProfileMethod = null;
                for (Method m : clone.getClass().getMethods()) {
                    if (m.getName().equals("setPlayerProfile") || m.getName().equals("setProfile")) {
                        setProfileMethod = m;
                        break;
                    }
                }

                if (setProfileMethod != null) {
                    setProfileMethod.invoke(clone, profile);
                }
            } catch (Exception ex) {
                // 실패 시 기본 스킨으로 생성됨
            }
            // [▲▲▲ 여기까지 변경됨 ▲▲▲]

            clone.setCustomName(p.getName()); // 머리 위에 주인 이름 표시
            clone.setCustomNameVisible(false); // 이름표는 숨김 (더 진짜 같게)

            // 분신에게 "나는 나루토의 분신이다"라는 이름표(메타데이터)를 붙임
            clone.setMetadata("NarutoOwner", new FixedMetadataValue(plugin, p.getUniqueId().toString()));

            // 분신 대미지 설정 (주먹 한 대당 하트 2칸 정도)
            if (clone.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
                clone.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(4.0);
            }

            // [▼▼▼ 여기서부터 변경됨: 장비 복제 시 1.21.11 아이템 대응 ▼▼▼]
            // 플레이어가 입고 있는 갑옷과 들고 있는 무기(예: 네더라이트 창)를 그대로 복사합니다.
            clone.getEquipment().setArmorContents(p.getInventory().getArmorContents());
            clone.getEquipment().setItemInMainHand(p.getInventory().getItemInMainHand());
            clone.getEquipment().setItemInOffHand(p.getInventory().getItemInOffHand());
            // [▲▲▲ 여기까지 변경됨 ▲▲▲]

            startCloneAI(p, clone); // 분신에게 지능 부여
            registerSummon(p, clone); // 게임 종료 시 한꺼번에 사라지게 등록
        }
    }

    private void startCloneAI(Player owner, Mannequin clone) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (clone.isDead() || !clone.isValid()) {
                    this.cancel();
                    return;
                }

                // 1.21.11의 Mannequin은 Mob 인터페이스를 상속받아 AI 사용이 가능합니다.
                if (clone instanceof Mob aiBody) {
                    LivingEntity target = aiBody.getTarget();

                    // 타겟이 없거나, 죽었거나, 주인일 경우 새로운 적 탐색
                    if (target == null || target.isDead() || target.equals(owner)) {
                        LivingEntity nearest = null;
                        double minDistance = 20.0; // 탐지 거리 20미터

                        for (Entity e : clone.getNearbyEntities(minDistance, 5, minDistance)) {
                            // 적군(플레이어 제외 생명체)이고, 다른 나루토 분신이 아닐 때만 공격
                            if (e instanceof LivingEntity victim && !e.equals(owner) && !e.hasMetadata("NarutoOwner")) {
                                double dist = e.getLocation().distance(clone.getLocation());
                                if (dist < minDistance) {
                                    minDistance = dist;
                                    nearest = victim;
                                }
                            }
                        }
                        if (nearest != null)
                            aiBody.setTarget(nearest); // 가장 가까운 적을 향해 돌격!
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // 0.5초마다 적을 찾음
    }

    @EventHandler
    public void onTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent e) {
        // 분신이 주인을 공격하려 할 때 강제로 막음 (팀킬 방지)
        if (e.getEntity().hasMetadata("NarutoOwner") && e.getTarget() instanceof Player p) {
            String ownerUUID = e.getEntity().getMetadata("NarutoOwner").get(0).asString();
            if (p.getUniqueId().toString().equals(ownerUUID))
                e.setCancelled(true);
        }
    }

    private boolean hasAbility(Player p) {
        return me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode());
    }
}