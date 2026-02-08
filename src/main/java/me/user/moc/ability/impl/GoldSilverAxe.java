package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class GoldSilverAxe extends Ability {

    private final Random random = new Random();

    public GoldSilverAxe(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "015";
    }

    @Override
    public String getName() {
        return "금도끼 은도끼";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§a유틸 ● 금도끼 은도끼(이솝 우화/나무꾼과 헤르메스)",
                "§f도끼를 물에 빠트리면 산신령이 나타나 도끼를 건네줍니다.");
    }

    @Override
    public void giveItem(Player p) {
        p.getInventory().remove(Material.IRON_SWORD); // 철 칼 삭제
        ItemStack axe = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = axe.getItemMeta();
        meta.setUnbreakable(true);
        meta.setDisplayName("§e낡은 나무 도끼");
        meta.setLore(Arrays.asList("§7물에 던지면 산신령이 나타납니다.", "§8(확률적으로 좋은 도끼 획득)"));
        axe.setItemMeta(meta);
        p.getInventory().addItem(axe);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§a유틸 ● 금도끼 은도끼(이솝 우화/나무꾼과 헤르메스)");
        p.sendMessage("§f도끼를 물속에 던지면 산신령의 힘으로 새로운 도끼로 교환해줍니다.");
        p.sendMessage("§f아래의 확률로 도끼와 전투 인챈트가 부여됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f나무 도끼 - 20%, 돌 도끼 - 20%, 철 도끼 - 30%");
        p.sendMessage("§f다이아 도끼 - 20%, 네더라이트 도끼 - 10%");
        p.sendMessage("§f해당 도끼에 인챈트 레벨 1 ~ 5 사이의 전투 인챈트 2개 랜덤 부여");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 나무 도끼");
        p.sendMessage("§f장비 제거 : 철 검");
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        // 플레이어 능력 확인
        if (!me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode())) {
            return;
        }

        Item itemDrop = e.getItemDrop();
        ItemStack is = itemDrop.getItemStack();

        // 도끼인지 확인
        if (!isAxe(is.getType())) {
            return;
        }

        // 물에 닿는지 감지하는 태스크 시작
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                // 아이템이 사라지거나(먹혔거나), 너무 오래되거나(10초), 능력자가 죽으면 취소
                if (itemDrop.isDead() || !itemDrop.isValid() || ticks > 200 || p.isDead()) {
                    this.cancel();
                    return;
                }

                // 물에 닿았는지 확인
                if (itemDrop.isInWater()) {
                    // 효과 연출 (흰 연기 + 소리)
                    p.getWorld().spawnParticle(Particle.CLOUD, itemDrop.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);
                    p.getWorld().playSound(itemDrop.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 1f);
                    p.getServer().broadcastMessage("§b금도끼 은도끼 : 이 도끼가 네 도끼냐?");

                    // 기존 아이템 제거
                    itemDrop.remove();
                    this.cancel();

                    // 2초 후 결과 지급
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            spawnRandomAxe(itemDrop.getLocation(), p);
                        }
                    }.runTaskLater(plugin, 40L); // 2초 = 40틱
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean isAxe(Material type) {
        return type == Material.WOODEN_AXE || type == Material.STONE_AXE ||
                type == Material.IRON_AXE || type == Material.GOLDEN_AXE ||
                type == Material.DIAMOND_AXE || type == Material.NETHERITE_AXE;
    }

    private void spawnRandomAxe(org.bukkit.Location loc, Player p) {
        // 1. 도끼 재질 결정
        Material mat = pickRandomAxeMaterial();
        ItemStack newAxe = new ItemStack(mat);
        ItemMeta meta = newAxe.getItemMeta();
        meta.setUnbreakable(true); // 기본적으로 내구도 무한 설정 (선택사항)
        newAxe.setItemMeta(meta);

        // 2. 인챈트 부여
        applyRandomEnchants(newAxe);

        // 3. 아이템 소환
        Item resultItem = loc.getWorld().dropItem(loc, newAxe);

        // 4. 아이템을 물 위로 튀어오르게 함
        resultItem.setVelocity(new Vector(0, 0.8, 0));

        // 5. 효과 연출 (노란 연기 + 소리)
        // DUST 파티클은 Color 옵션이 필요함 (노란색: 255, 255, 0)
        Particle.DustOptions dustOptions = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 255, 0), 1.5f);
        loc.getWorld().spawnParticle(Particle.DUST, loc, 30, 0.5, 0.5, 0.5, dustOptions);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
    }

    private Material pickRandomAxeMaterial() {
        int r = random.nextInt(100); // 0 ~ 99
        // 나무 20%, 돌 20%, 철 30%, 다이아 20%, 네더라이트 10%
        if (r < 20)
            return Material.WOODEN_AXE;
        else if (r < 40)
            return Material.STONE_AXE;
        else if (r < 70)
            return Material.IRON_AXE;
        else if (r < 90)
            return Material.DIAMOND_AXE;
        else
            return Material.NETHERITE_AXE;
    }

    private void applyRandomEnchants(ItemStack item) {
        List<Enchantment> pool = new ArrayList<>();
        pool.add(Enchantment.SHARPNESS);
        pool.add(Enchantment.FIRE_ASPECT);
        pool.add(Enchantment.KNOCKBACK);
        pool.add(Enchantment.LOOTING);
        pool.add(Enchantment.SMITE);
        // "칼에 부여 가능한 인챈트" 기준, 위 5개가 대표적 (내구, 수선 등 제외하고 전투 관련만 예시에 있음)

        Collections.shuffle(pool);

        // 2개 부여
        for (int i = 0; i < 2; i++) {
            Enchantment ench = pool.get(i);
            int level = random.nextInt(5) + 1; // 1 ~ 5
            item.addUnsafeEnchantment(ench, level);
        }
    }
}
