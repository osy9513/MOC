// 파일 경로: src/main/java/me/user/moc/ability/impl/Ueki.java
package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class Ueki extends Ability {

    // 플레이어별 쿨타임을 저장하는 장부입니다. (단위: 밀리초)
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final int COOLDOWN_TIME = 15; // 쿨타임 15초

    public Ueki(JavaPlugin plugin) {
        super(plugin);
    }
    // [수정 2] 우에키에게 "001"이라는 번호표를 붙여줍니다.
    @Override
    public String getCode() {
        return "001";
    }
    @Override
    public String getName() {
        return "우에키";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§a유틸 ● 우에키(우에키의 법칙)",
                "§f묘목을 우클릭하면 주변 20블럭 이내의",
                "§f생명체와 아이템을 나무로 바꿉니다."
        );
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§a유틸 ● 우에키(우에키의 법칙)");
        p.sendMessage("묘목을 우클릭 하면 주변 20블럭 이내 \n" +
                "\n" +
                "모든 생명체와 바닥에 떨어진 아이템들이\n" +
                "\n" +
                "나무로 변합니다.\n" +
                "\n" +
                "쿨타임 : 8초.\n" +
                "\n" +
                "---\n" +
                "\n" +
                "추가 장비: 묘목 64개.\n" +
                "\n" +
                "장비 제거: 없음.");
    }

    @Override
    public void giveItem(Player p) {
        // 능력 전용 아이템: 묘목 64개
        p.getInventory().addItem(new ItemStack(Material.OAK_SAPLING, 64));
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 1. 이 사람이 우에키 능력을 가졌는지 확인 (람머스와 동일한 방식)
        if (!me.user.moc.MocPlugin.getInstance().getAbilityManager().hasAbility(p, getCode())) return;

        // 2. 묘목을 들고 우클릭했는지 확인
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                && e.getMaterial() == Material.OAK_SAPLING) {

            // 3. 쿨타임 체크
            if (checkCooldown(p)) {
                useAbility(p);
            }
        }
    }

    private void useAbility(Player p) {
        p.sendMessage("§a[!] 쓰레기를 나무로 바꾸는 힘!");
        p.playSound(p.getLocation(), Sound.BLOCK_CHERRY_SAPLING_PLACE, 1f, 1f);

        // 주변 20블럭 이내의 아이템과 생명체 탐색
        for (Entity entity : p.getNearbyEntities(20, 20, 20)) {
            if (entity instanceof Item || (entity instanceof LivingEntity && !entity.equals(p))) {
                // 해당 위치에 나무(참나무 로그) 설치
                entity.getLocation().getBlock().setType(Material.OAK_LOG);
                if (!(entity instanceof Player)) entity.remove(); // 플레이어가 아니면 제거
            }
        }
    }

    private boolean checkCooldown(Player p) {
        long now = System.currentTimeMillis();
        long lastUse = cooldowns.getOrDefault(p.getUniqueId(), 0L);
        long remain = (lastUse + (COOLDOWN_TIME * 1000L)) - now;

        if (remain > 0) {
            // [옵션 3] 액션바에 붉은색 쿨타임 표시
            double seconds = remain / 1000.0;
            p.sendActionBar(Component.text("남은 시간: " + String.format("%.1f", seconds) + "초")
                    .color(NamedTextColor.RED));
            return false;
        }
        cooldowns.put(p.getUniqueId(), now);
        return true;
    }
}