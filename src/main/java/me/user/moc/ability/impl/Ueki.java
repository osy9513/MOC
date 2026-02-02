// 파일 경로: src/main/java/me/user/moc/ability/impl/Ueki.java
package me.user.moc.ability.impl;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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

import me.user.moc.ability.Ability;

public class Ueki extends Ability {

    private final int COOLDOWN_TIME = 8; // 쿨타임 8초

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
                "§f생명체와 아이템을 나무로 바꿉니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§a유틸 ● 우에키(우에키의 법칙)");
        p.sendMessage("§f묘목을 우클릭하면 주변 20블록 이내의 모든 생명체와 아이템을 나무로 바꿉니다.");
        p.sendMessage("§f나무로 변한 대상(몹/아이템)은 즉시 소멸하며, 플레이어는 나무 속에 갇힙니다.");
        p.sendMessage("§f[제한] 소환된 나무의 개수만큼 인벤토리의 묘목을 소모합니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 8초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 참나무 묘목 64개");
        p.sendMessage("§f장비 제거 : 없음");
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
        if (!me.user.moc.MocPlugin.getInstance().getAbilityManager().hasAbility(p, getCode()))
            return;

        // 2. 묘목을 들고 우클릭했는지 확인
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                && e.getMaterial() == Material.OAK_SAPLING) {

            // 3. 쿨타임 체크 (부모 메서드 사용)
            if (checkCooldown(p)) {
                setCooldown(p, COOLDOWN_TIME); // 쿨타임 설정
                useAbility(p);
            }
        }
    }

    private void useAbility(Player p) {
        Bukkit.broadcastMessage("§a우에키 : 쓰레기를 나무로 바꾸는 힘!");
        p.playSound(p.getLocation(), Sound.BLOCK_CHERRY_SAPLING_PLACE, 1f, 1f);

        int treeCount = 0;

        // 주변 20블럭 이내의 아이템과 생명체 탐색
        for (Entity entity : p.getNearbyEntities(20, 20, 20)) {
            // 아이템이거나, 시전자가 아닌 생명체인 경우
            if (entity instanceof Item || (entity instanceof LivingEntity && !entity.equals(p))) {

                // [추가] 관전 모드 플레이어는 대상에서 제외
                if (entity instanceof Player targetPlayer
                        && targetPlayer.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    continue;
                }

                Location loc = entity.getLocation();

                // 1. 나무가 어떤 블럭 위에서도 잘 자라도록 발바닥 블럭을 흙으로 바꿉니다.
                loc.getBlock().getRelative(0, -1, 0).setType(Material.GRASS_BLOCK);

                // 2. 해당 위치에 진짜 나무(참나무) 한 그루를 생성합니다.
                boolean grown = loc.getWorld().generateTree(loc, org.bukkit.TreeType.TREE);

                // 3. 만약 공간이 좁아 나무가 안 자랐다면? 우리가 직접 "강제로" 만듭니다!
                if (!grown) {
                    for (int i = 0; i < 3; i++) {
                        loc.clone().add(0, i, 0).getBlock().setType(Material.OAK_LOG);
                    }
                    loc.clone().add(0, 3, 0).getBlock().setType(Material.OAK_LEAVES);
                    loc.clone().add(1, 2, 0).getBlock().setType(Material.OAK_LEAVES);
                    loc.clone().add(-1, 2, 0).getBlock().setType(Material.OAK_LEAVES);
                    loc.clone().add(0, 2, 1).getBlock().setType(Material.OAK_LEAVES);
                    loc.clone().add(0, 2, -1).getBlock().setType(Material.OAK_LEAVES);
                }

                // 나무가 생성된 것으로 간주
                treeCount++;

                // 4. 대상이 플레이어가 아니면(아이템이나 몹) 제거합니다.
                if (!(entity instanceof Player)) {
                    entity.remove();
                }
            }
        }

        // 5. 사용된 나무 개수만큼 묘목 소모
        if (treeCount > 0) {
            ItemStack sapling = new ItemStack(Material.OAK_SAPLING, treeCount);
            p.getInventory().removeItem(sapling);
            p.sendMessage("§a[MOC] §f주변 쓰레기를 나무로 바꾸며 묘목 " + treeCount + "개를 소모했습니다.");
        }
    }
}
