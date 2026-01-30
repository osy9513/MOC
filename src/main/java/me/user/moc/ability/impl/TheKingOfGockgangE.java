package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * [능력 코드: 019]
 * 이름: The King of Gockgang-E (왕 쩌는 곡갱이)
 * 설명: 모든 블록(기반암 포함)을 즉시 파괴하며 범위 피해를 입힘.
 */
public class TheKingOfGockgangE extends Ability {

    private final Random random = new Random();

    public TheKingOfGockgangE(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "019";
    }

    @Override
    public String getName() {
        return "The King of Gockgang-E";
    }

    @Override
    public List<String> getDescription() {
        List<String> list = new ArrayList<>();
        list.add("§f복합 ● The King of Gockgang-E(The King of Gockgang-E)");
        list.add("§f왕 쩌는 곡갱이를 얻습니다.");
        return list;
    }

    @Override
    public void giveItem(Player p) {
        // 1. 기존 장비(철 검) 제거
        p.getInventory().remove(Material.IRON_SWORD);

        // 2. 왕 쩌는 곡갱이 생성
        ItemStack pickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = pickaxe.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e왕 쩌는 곡갱이");
            meta.setUnbreakable(true); // 편의상 파괴 불가 설정
            // 내구성 인챈트 10 (요청사항: 내구성10)
            meta.addEnchant(Enchantment.UNBREAKING, 10, true);
            pickaxe.setItemMeta(meta);
        }

        // 3. 아이템 지급
        p.getInventory().addItem(pickaxe);

        // 4. 성급함 V 효과 부여 (99999초)
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 99999 * 20, 4, false, false));

        // 5. 능력 발동 메시지
        p.sendMessage("§e[MOC] §fShout Out TEAM PICKAXE");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e복합 ● The King of Gockgang-E(The King of Gockgang-E)");
        p.sendMessage("성급한 V를 상시 활성화 합니다.");
        p.sendMessage("모든 블럭을 그리고 기반암까지 크리에이트처럼 때려 부수는 `왕 쩌는 곡갱이`를 얻습니다.");
        p.sendMessage("왕 쩌는 곡갱이로 블럭을 부수면 파편이 튀어서 파괴된 블럭의 위 아래 좌 우 대각선 모든 방향에 3데미지를 줍니다.");
        p.sendMessage(" ");
        p.sendMessage("쿨타임 : 0초");
        p.sendMessage("---");
        p.sendMessage("추가 장비 : 왕 쩌는 곡갱이(네더라이트곡갱이-내구성10)");
        p.sendMessage("장비 제거 : 철 검");
    }

    /**
     * 블록을 때릴 때 발생하는 이벤트입니다.
     * 서바이벌 모드에서 좌클릭 시 BlockDamageEvent가 먼저 발생합니다.
     * 이를 이용해 '즉시 파괴'를 구현합니다.
     */
    @EventHandler
    public void onBlockDamage(BlockDamageEvent e) {
        Player p = e.getPlayer();

        // 1. 쿨타임 및 능력 보유 확인
        if (!checkCooldown(p))
            return;

        // 2. AbilityManager를 통해 이 플레이어가 이 능력(019)을 가지고 있는지 확인
        // (부모 클래스 메서드가 있으면 좋겠지만, 현재 구조상 매니저를 호출하거나 직접 확인해야 함)
        // 여기서는 간단히 이름 검사만 하지 않고, AbilityManager와 연동하는 것이 정석이나,
        // 편의상 아이템 이름과 플레이어 상태로직을 믿고 진행합니다.
        // 더 안전하게 하려면 AbilityManager.getInstance(plugin).hasAbility(p, getCode()) 호출 필요.
        // 하지만 성능을 위해 아이템 체크를 먼저 합니다.

        ItemStack mainHand = p.getInventory().getItemInMainHand();
        if (mainHand.getType() != Material.NETHERITE_PICKAXE)
            return;
        ItemMeta meta = mainHand.getItemMeta();
        if (meta == null || !"§e왕 쩌는 곡갱이".equals(meta.getDisplayName()))
            return;

        // 3. 블록 즉시 파괴 로직
        Block block = e.getBlock();
        World world = block.getWorld();

        // 소리 재생 (블록 고유의 파괴음)
        world.playSound(block.getLocation(), block.getBlockData().getSoundGroup().getBreakSound(), 1.0f, 1.0f);

        // 블록을 공기로 바꿉니다 (파괴)
        block.setType(Material.AIR);

        // 4. 시각 효과 (철 조각 파편)
        spawnDebrisEffect(block);

        // 5. 범위 피해 (3.0 대미지)
        // 파괴된 블록 중심 1.5칸 반경 내의 엔티티
        for (Entity entity : world.getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 1.5, 1.5, 1.5)) {
            if (entity instanceof LivingEntity && entity != p) {
                ((LivingEntity) entity).damage(3.0, p);
            }
        }

        // 이벤트 취소? 이미 수동으로 부셨으므로 더 이상의 처리를 막기 위해 취소할 수도 있지만,
        // setType(AIR)를 했으므로 자연스럽게 끝납니다.
        // 다만 Instabreak 효과를 확실히 하기 위해 캔슬하지 않고 둘 수도 있습니다.
        // 하지만 블록이 이미 AIR가 되었으므로 추가 드랍이 없을 것입니다.
        e.setInstaBreak(true); // 혹시 모르니 즉시 파괴 플래그 설정
    }

    /**
     * 블록 파괴 시 철 조각이 튀는 시각 효과를 연출합니다.
     */
    private void spawnDebrisEffect(Block block) {
        World world = block.getWorld();
        // 중앙 위치
        org.bukkit.Location center = block.getLocation().add(0.5, 0.5, 0.5);

        // 4~6개의 철 조각 생성
        int count = 4 + random.nextInt(3); // 4, 5, 6 중 하나

        for (int i = 0; i < count; i++) {
            // 랜덤 벡터 생성 (사방으로 튀도록)
            double x = (random.nextDouble() - 0.5) * 0.5;
            double y = random.nextDouble() * 0.5; // 위로 좀 더 튀게
            double z = (random.nextDouble() - 0.5) * 0.5;

            ItemStack nugget = new ItemStack(Material.IRON_NUGGET);
            Item itemEntity = world.dropItem(center, nugget);
            itemEntity.setVelocity(new Vector(x, y, z));
            itemEntity.setPickupDelay(9999); // 줍기 방지

            // 관리 리스트에 등록 (라운드 종료 시 삭제되도록) - 플레이어 귀속으로 등록하기 애매하면 cleanup 로직 고민 필요
            // 여기서는 플레이어 귀속이 불분명할 수 있지만, 발동 주체가 있으니 주체에게 등록.
            // 하지만 onBlockDamage 내에서 p 변수가 있으므로 사용 가능.
            // 다만, dropItem은 UUID 기반 추적이므로 activeEntities에 넣어야 함.

            // 주의: 이 메서드는 private이라 p를 인자로 받아야 activeEntities에 넣을 수 있음.
            // 여기서는 그냥 로컬 스케줄러로 삭제 처리하는 게 더 깔끔할 수 있음 (짧은 시간 후 삭제되므로).

            // 2초(40틱) 후 삭제
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (itemEntity.isValid()) {
                    itemEntity.remove();
                }
            }, 20L + random.nextInt(10)); // 1 ~ 1.5초 후 사라짐
        }
    }
}
