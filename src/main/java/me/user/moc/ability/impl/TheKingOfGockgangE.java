package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import java.util.Arrays;
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
    private final java.util.Map<java.util.UUID, Integer> breakCounts = new java.util.HashMap<>();

    public TheKingOfGockgangE(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "019";
    }

    @Override
    public String getName() {
        return "The King of Gockgang-E";// 일명 킹오브곡갱이
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
            meta.setLore(Arrays.asList("§7블록 파괴 시 주변 적에게 피해를 입힙니다.", "§eShout Out TEAM PICKAXE"));
            meta.setUnbreakable(true); // 편의상 파괴 불가 설정
            // 내구성 인챈트 10 (요청사항: 내구성10)
            meta.addEnchant(Enchantment.UNBREAKING, 10, true);
            meta.setCustomModelData(1); // 리소스팩: thekingofgockgange
            pickaxe.setItemMeta(meta);
        }

        // 3. 아이템 지급
        p.getInventory().addItem(pickaxe);

        // 4. 성급함 V 효과 부여 (99999초)
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, PotionEffect.INFINITE_DURATION, 4, true, true));

        // 5. 능력 발동 메시지
        p.sendMessage("§e[MOC] §fShout Out TEAM PICKAXE");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e복합 ● The King of Gockgang-E(The King of Gockgang-E)");
        p.sendMessage("§f성급한 V를 상시 활성화 합니다.");
        p.sendMessage("§f모든 블럭을 그리고 기반암까지 크리에이트처럼 때려 부수는 `왕 쩌는 곡갱이`를 얻습니다.");
        p.sendMessage("§f왕 쩌는 곡갱이로 블럭을 부수면 주변에 3의 범위 피해를 줍니다.");
        p.sendMessage("§f[제한] 블록을 30개 파괴하면 15초의 재충전 시간이 필요합니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 15초(30개 파괴 시)");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 왕 쩌는 곡갱이(네더라이트곡갱이-내구성10)");
        p.sendMessage("§f장비 제거 : 철 검");
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
        if (!checkCooldown(p)) {
            // 쿨타임 중이면 남은 시간 가이드 (이미 Ability에서 처리하겠지만 게이지를 위해 리턴만 함)
            return;
        }

        // 2. 능력 보유 확인 (코드 기반)
        // 킹오브곡갱이는 다른 사람이 먹어서 사용할 수 있게 구현 ㅋㅋ 그게 더 잼슴.
        /*
         * if (!me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin)
         * plugin).hasAbility(p, getCode())) {
         * return;
         * }
         */

        ItemStack mainHand = p.getInventory().getItemInMainHand();
        if (mainHand.getType() != Material.NETHERITE_PICKAXE)
            return;
        ItemMeta meta = mainHand.getItemMeta();
        if (meta == null || !"§e왕 쩌는 곡갱이".equals(meta.getDisplayName()))
            return;

        // 3. 블록 즉시 파괴 로직
        Block block = e.getBlock();
        World world = block.getWorld();

        world.playSound(block.getLocation(), block.getBlockData().getSoundGroup().getBreakSound(), 1.0f, 1.0f);
        block.setType(Material.AIR);
        spawnDebrisEffect(block);

        for (Entity entity : world.getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 1.5, 1.5, 1.5)) {
            if (entity instanceof LivingEntity && entity != p) {
                if (entity instanceof Player && ((Player) entity).getGameMode() == org.bukkit.GameMode.SPECTATOR)
                    continue;
                ((LivingEntity) entity).damage(3.0, p);
            }
        }

        int count = breakCounts.getOrDefault(p.getUniqueId(), 0) + 1;
        if (count >= 30) {
            breakCounts.put(p.getUniqueId(), 0);
            setCooldown(p, 15);
            p.sendMessage("§f왕 쩌는 곡갱이의 마력이 다했습니다! 15초간 재충전이 필요합니다.");
            p.sendActionBar(net.kyori.adventure.text.Component.text("§c[게이지] ■■■■■ 30/30 (재충전 시작)"));
        } else {
            breakCounts.put(p.getUniqueId(), count);
            StringBuilder gauge = new StringBuilder("§e[게이지] ");
            int filled = (count * 5) / 30;
            for (int i = 0; i < 5; i++) {
                if (i < filled)
                    gauge.append("■");
                else
                    gauge.append("□");
            }
            gauge.append(" ").append(count).append("/30");
            p.sendActionBar(net.kyori.adventure.text.Component.text(gauge.toString()));
        }

        e.setInstaBreak(true);
    }

    @EventHandler
    public void onAttack(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p))
            return;

        // [추가] 전투 시작 전 데미지 증가 금지
        if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted())
            return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() == Material.NETHERITE_PICKAXE && hand.hasItemMeta()) {
            if ("§e왕 쩌는 곡갱이".equals(hand.getItemMeta().getDisplayName())) {
                e.setDamage(6.0);
            }
        }
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
