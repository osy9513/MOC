package me.user.moc.ability.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;

/**
 * [능력 코드: 062]
 * 이름: 올드보이 (오대수)
 * 설명: 15년간 만두를 먹습니다.
 */
public class Oldboy extends Ability {

    private final Map<UUID, List<BlockState>> originalBlocks = new HashMap<>();
    private final Set<Location> protectedBlocks = new HashSet<>();
    private final Map<UUID, Integer> consumedCounts = new HashMap<>();
    private final Map<UUID, Boolean> hasCleared = new HashMap<>();

    public Oldboy(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "062";
    }

    @Override
    public String getName() {
        return "오대수";
    }

    @Override
    public List<String> getDescription() {
        return java.util.Arrays.asList(
                "§b유틸 ● 오대수(올드보이)",
                "§f15년간 만두를 먹습니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 장비 제거: 기본 지급되는 철 검과 구운 소고기 제거
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null) {
                if (item.getType() == Material.IRON_SWORD || item.getType() == Material.COOKED_BEEF) {
                    item.setAmount(0);
                }
            }
        }

        // 1. 공중 감옥으로 텔레포트 (현재 위치 Y + 5)
        Location center = p.getLocation().add(0, 5, 0);
        // 플레이어가 중앙에 올 수 있도록 보정
        center.setX(center.getBlockX() + 0.5);
        center.setZ(center.getBlockZ() + 0.5);
        p.teleport(center);

        // 2. 3x3 흙 감옥 생성 및 기존 블록 상태 저장
        List<BlockState> states = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block b = center.getBlock().getRelative(x, y, z);
                    states.add(b.getState()); // 나중에 롤백하기 위해 원본 저장
                    protectedBlocks.add(b.getLocation());

                    // 감옥 안쪽: 머리 위는 공기, 발밑은 밝기를 위해 레드스톤 토치 설치, 나머지는 벽돌
                    if (x == 0 && z == 0 && y == 1) {
                        b.setType(Material.AIR);
                    } else if (x == 0 && z == 0 && y == 0) {
                        b.setType(Material.REDSTONE_TORCH);
                    } else {
                        b.setType(Material.BRICKS); // [변경] 흙 -> 벽돌
                    }
                }
            }
        }
        originalBlocks.put(p.getUniqueId(), states);

        // 3. 배고픔 0으로 초기화
        p.setFoodLevel(0);
        p.setSaturation(0);

        // 4. 만두(말린 켈프) 15개 지급
        ItemStack gunmandu = new ItemStack(Material.DRIED_KELP, 15);
        ItemMeta meta = gunmandu.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l군만두");
            meta.setCustomModelData(1); // 리소스팩 id
            List<String> lore = new ArrayList<>();
            lore.add("§f우클릭 시 군만두를 먹습니다.");
            lore.add("§f15개를 다 먹으면 감옥에서 탈출합니다.");
            meta.setLore(lore);
            gunmandu.setItemMeta(meta);
        }
        p.getInventory().addItem(gunmandu);

        // 5. 진행 상태 초기화
        consumedCounts.put(p.getUniqueId(), 0);
        hasCleared.put(p.getUniqueId(), false);

        detailCheck(p);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§b유틸 ● 오대수(올드보이)");
        p.sendMessage("§f15년간 만두를 먹습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f전투 시작 시 배고픔이 0으로 시작하며");
        p.sendMessage("§f공중에 3x3 벽돌 감옥에 갇힙니다.");
        p.sendMessage("§f만두를 15번 다 먹을 경우 감옥에서 풀려나고");
        p.sendMessage("§f영구적으로 힘 1 버프와 날카로움 1이 붙은 망치를 얻습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 만두 15개");
        p.sendMessage("§f장비 제거 : 철 칼, 구운 소고기 64개");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        clearPrison(p);
        consumedCounts.remove(p.getUniqueId());
        hasCleared.remove(p.getUniqueId());
    }

    /**
     * 감옥 블록을 원래 상태로 되돌리고 무적 보호를 해제하는 메서드.
     */
    private void clearPrison(Player p) {
        List<BlockState> states = originalBlocks.remove(p.getUniqueId());
        if (states != null) {
            for (BlockState state : states) {
                protectedBlocks.remove(state.getLocation());
                state.update(true, false); // 블록 롤백
            }
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (isSilenced(p))
            return; // [신규 규칙] 침묵 상태 검사
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        ItemStack item = e.getItem();
        if (item.getType() == Material.DRIED_KELP && item.hasItemMeta() &&
                item.getItemMeta().hasCustomModelData() && item.getItemMeta().getCustomModelData() == 1) {

            // [변경] 군만두 섭취 시 3초 쿨타임 적용
            if (!checkCooldown(p)) {
                e.setCancelled(true);
                return;
            }
            setCooldown(p, 3);

            int count = consumedCounts.getOrDefault(p.getUniqueId(), 0) + 1;
            consumedCounts.put(p.getUniqueId(), count);

            p.sendMessage("§e" + count + "년 동안 군만두를 먹었습니다...");

            if (count >= 15 && !hasCleared.getOrDefault(p.getUniqueId(), false)) {
                hasCleared.put(p.getUniqueId(), true);

                // 1. 감옥 해제
                clearPrison(p);

                // 2. 전체 채팅
                Bukkit.broadcastMessage("§f오대수: 누구냐, 너.");

                // 3. 혜택: 영구 힘 1
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 0, false,
                        false, true));

                // 4. 혜택: 장도리 (망치) 지급
                ItemStack hammer = new ItemStack(Material.IRON_SWORD);
                ItemMeta hMeta = hammer.getItemMeta();
                if (hMeta != null) {
                    hMeta.setDisplayName("§c§l장도리");
                    hMeta.setCustomModelData(16); // 리소스팩 id
                    hMeta.addEnchant(Enchantment.SHARPNESS, 1, true); // 날카로움 1
                    List<String> lore = new ArrayList<>();
                    lore.add("§f오대수가 쥐어패고 다니는 장도리입니다.");
                    hMeta.setLore(lore);
                    hammer.setItemMeta(hMeta);
                }
                p.getInventory().addItem(hammer);

                // 배고픔 보정 (선택 사항: 감옥에서 버틸만큼만 채워줌)
                p.setFoodLevel(20);
                p.setSaturation(10);

                // 감옥이 지상과 너무 높이 떨어져 있을 경우를 대비하여 낙하 대미지 면제 효과 부여
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, false, false));
            }
        }
    }

    // === 감옥 흙 블록 무적 보호 로직 ===

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (protectedBlocks.contains(e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(block -> protectedBlocks.contains(block.getLocation()));
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(block -> protectedBlocks.contains(block.getLocation()));
    }
}
