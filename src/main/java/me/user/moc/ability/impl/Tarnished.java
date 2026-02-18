package me.user.moc.ability.impl;

import me.user.moc.ability.AbilityManager;
import me.user.moc.ability.Ability;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Tarnished extends Ability {

    // 플레이어별 상태 관리 (토가 히미코 규칙 준수)
    private final Map<UUID, Long> lastSneakTime = new HashMap<>(); // 더블 쉬프트 감지용
    private final Map<UUID, Long> invincibleUntil = new HashMap<>(); // 무적 시간 관리
    private final Map<UUID, Integer> killCount = new HashMap<>(); // 킬 카운트 (방어구 업그레이드용)

    public Tarnished(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "058";
    }

    @Override
    public String getName() {
        return "빛바랜 자";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c유틸 ● 빛바랜 자(엘든 링)",
                "§f구르기의 시간이다.");
    }

    @Override
    public void giveItem(Player p) {
        // 기존 아이템 제거
        p.getInventory().remove(Material.IRON_SWORD);

        // [New] 빛바랜 자의 대검 (CustomModelData 13)
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        org.bukkit.inventory.meta.ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName("§7빛바랜 자의 대검");
        meta.setCustomModelData(13); // ResourcePack: tarnished_sword
        meta.setLore(Arrays.asList("§7오래된 전설이 깃든 대검입니다."));
        sword.setItemMeta(meta);

        p.getInventory().addItem(sword);

        plugin.getServer().broadcastMessage("§a빛바랜 자 : §f이 앞, 숨겨진 길 있다.");
        // 초기화
        killCount.put(p.getUniqueId(), 0);
        detailCheck(p);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c유틸 ● 빛바랜 자(엘든 링)");
        p.sendMessage("§f쉬프트를 두 번 누르면 배고픔을 1칸 소모하여");
        p.sendMessage("§f0.8초간 전방으로 구릅니다.");
        p.sendMessage("§f구르는 동안은 무적 상태입니다.");
        p.sendMessage("§f생명체를 처치하면 체력 4칸 회복 및 방어구가 강화됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f1킬: 철 레깅스 | 2킬: 철 부츠");
        p.sendMessage("§f3킬: 다이아 흉갑 | 4킬: 다이아 레깅스 & 부츠");
        p.sendMessage("§f5킬: 네더라이트 흉갑 | 6킬: 네더라이트 레깅스 & 부츠");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 빛바랜 자의 대검");
        p.sendMessage("§f장비 제거 : 철 검");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p); // 부모 클래스의 기본적인 cleanup 호출 (소환수, 태스크 등)
        UUID uuid = p.getUniqueId();
        lastSneakTime.remove(uuid);
        invincibleUntil.remove(uuid);
        killCount.remove(uuid);
    }

    @Override
    public void reset() {
        super.reset();
        lastSneakTime.clear();
        invincibleUntil.clear();
        killCount.clear();
    }

    // --- [능력 구현] ---

    // 1. 더블 쉬프트 감지 및 구르기 실행
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        // 능력이 없는 플레이어는 무시
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;
        // 웅크리기 해제(일어날 때)가 아니라 누를 때만 감지하려면 e.isSneaking() 체크
        if (!e.isSneaking())
            return;

        // 쿨타임 체크 (0초지만 형식상)
        if (!checkCooldown(p))
            return;

        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastSneakTime.getOrDefault(uuid, 0L);

        // 0.3초 이내에 다시 눌렀다면 더블 클릭으로 인정
        if (now - last < 300) {
            performRoll(p);
            lastSneakTime.remove(uuid); // 더블 클릭 처리 했으니 리셋
        } else {
            lastSneakTime.put(uuid, now);
        }
    }

    private void performRoll(Player p) {
        // 1. 배고픔 체크
        if (p.getFoodLevel() < 2) { // 1칸 = 2 포인트
            p.sendActionBar(net.kyori.adventure.text.Component.text("§c배고픔이 부족합니다!"));
            return;
        }

        // 2. 소모 및 실행
        p.setFoodLevel(p.getFoodLevel() - 2);

        // 전방으로 구르기 (약 0.8초간 이동할 정도의 힘)
        // 냅다 슬라이딩: Y축 살짝 들어주고 전방 벡터 강화
        p.setVelocity(p.getLocation().getDirection().multiply(1.5).setY(0.2));

        // 무적 설정 (0.8초 = 800ms)
        invincibleUntil.put(p.getUniqueId(), System.currentTimeMillis() + 800);

        // 효과
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation(), 10, 0.5, 0.1, 0.5, Material.DIRT.createBlockData());

        // 먼지 이펙트 (구르는 느낌) - 스케줄러로 0.8초 동안 짧게 따라가며 파티클
        // 하지만 간단하게 한 번 뿌리는 걸로 구현 (요청사항: 먼지 같은 거 날리도록)
        p.getWorld().spawnParticle(Particle.POOF, p.getLocation(), 5, 0.3, 0.1, 0.3, 0.05);
    }

    // 2. 무적 처리
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p))
            return;

        if (invincibleUntil.containsKey(p.getUniqueId())) {
            long until = invincibleUntil.get(p.getUniqueId());
            if (System.currentTimeMillis() < until) {
                e.setCancelled(true);
            }
        }
    }

    // 3. 킬 보상 시스템
    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null)
            return;

        // 킬러가 빛바랜 자인지 확인
        if (!AbilityManager.getInstance().hasAbility(killer, getCode()))
            return;

        // 1. 체력 4 회복
        double maxHealth = killer.getAttribute(Attribute.MAX_HEALTH).getValue();
        killer.setHealth(Math.min(maxHealth, killer.getHealth() + 4));
        killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);

        // 2. 킬 카운트 증가 및 장비 지급
        UUID uuid = killer.getUniqueId();
        int kills = killCount.getOrDefault(uuid, 0) + 1;
        killCount.put(uuid, kills);

        updateArmor(killer, kills);
    }

    private void updateArmor(Player p, int kills) {
        ItemStack[] armor = p.getInventory().getArmorContents();

        // 1킬 - 철 레깅스
        if (kills == 1) {
            armor[1] = new ItemStack(Material.IRON_LEGGINGS);
            p.sendMessage("§a[강화] 철 레깅스 획득!");
        }
        // 2킬 - 철 부츠
        else if (kills == 2) {
            armor[0] = new ItemStack(Material.IRON_BOOTS);
            p.sendMessage("§a[강화] 철 부츠 획득!");
        }
        // 3킬 - 다이아 흉갑
        else if (kills == 3) {
            armor[2] = new ItemStack(Material.DIAMOND_CHESTPLATE);
            p.sendMessage("§b[강화] 다이아몬드 흉갑 획득!");
        }
        // 4킬 - 다이아 레깅스 & 부츠
        else if (kills == 4) {
            armor[1] = new ItemStack(Material.DIAMOND_LEGGINGS);
            armor[0] = new ItemStack(Material.DIAMOND_BOOTS);
            p.sendMessage("§b[강화] 다이아몬드 레깅스 & 부츠 획득!");
        }
        // 5킬 - 네더라이트 흉갑
        else if (kills == 5) {
            armor[2] = new ItemStack(Material.NETHERITE_CHESTPLATE);
            p.sendMessage("§5[강화] 네더라이트 흉갑 획득!");
        }
        // 6킬 - 네더라이트 레깅스 & 부츠
        else if (kills == 6) {
            armor[1] = new ItemStack(Material.NETHERITE_LEGGINGS);
            armor[0] = new ItemStack(Material.NETHERITE_BOOTS);
            p.sendMessage("§5[강화] 네더라이트 레깅스 & 부츠 획득! (최종)");
        }

        p.getInventory().setArmorContents(armor);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1f, 1f);
    }
}
