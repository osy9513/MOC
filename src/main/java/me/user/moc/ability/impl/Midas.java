package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public class Midas extends Ability {

    public Midas(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "003";
    }

    @Override
    public String getName() {
        return "미다스";
    }

    @Override
    public List<String> getDescription() {
        List<String> list = new ArrayList<>();
        list.add("§6유틸 ● 미다스(그리스 로마 신화)");
        list.add("§f손에 닿는 모든 게 §e금이 된다.");
        return list;
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§6유틸 ● 미다스(그리스 로마 신화)");
        p.sendMessage("§f손으로 블럭을 때리면 그 블럭은 §e금 블럭§f이 됩니다.");
        p.sendMessage("§f손으로 상대를 때리면 상대가 입고 있던 방어구는 §e금 방어구§f가 됩니다.");
        p.sendMessage("§f플레이어가 아닌 대상을 손으로 좌클릭 시 §e황금 블럭§f으로 교체시킵니다.");
        p.sendMessage("§e금괴§f를 먹을 수 있습니다. (배고픔 2칸 회복)");
        p.sendMessage("§7쿨타임 : 0초");
        p.sendMessage("---");
        p.sendMessage("§7추가 장비: 금 갑옷, 금 칼, 발광포션, 황금 사과 64개, 금괴 1280개");
        p.sendMessage("§7장비 제거: 철 갑옷, 철 칼, 체력재생포션, 고기 64개");
    }

    @Override
    public void giveItem(Player p) {
        // 1. [기존 장비 제거]
        p.getInventory().remove(Material.IRON_SWORD);
        p.getInventory().remove(Material.COOKED_BEEF);
        p.getInventory().remove(Material.POTION); // 재생 포션
        p.getInventory().remove(Material.IRON_CHESTPLATE);

        // 갑옷 슬롯 비우기 (철 흉갑만)
        if (p.getInventory().getChestplate() != null &&
                p.getInventory().getChestplate().getType() == Material.IRON_CHESTPLATE) {
            p.getInventory().setChestplate(null);
        }

        // 2. [추가 장비 지급]
        // 금 칼
        p.getInventory().addItem(new ItemStack(Material.GOLDEN_SWORD));
        // 금 갑옷 세트 (입혀줌)
        p.getInventory().setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
        p.getInventory().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
        p.getInventory().setLeggings(new ItemStack(Material.GOLDEN_LEGGINGS));
        p.getInventory().setBoots(new ItemStack(Material.GOLDEN_BOOTS));

        // 황금 사과 64개
        p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 64));

        // 발광 포션 (투척)
        ItemStack glowPotion = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) glowPotion.getItemMeta();
        if (meta != null) {
            meta.addCustomEffect(new PotionEffect(PotionEffectType.GLOWING, 600, 0), true);
            meta.setDisplayName("§e미다스의 빛");
            glowPotion.setItemMeta(meta);
        }
        // 금괴 1280개 (64 * 20)
        p.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 64 * 20));
        p.getInventory().addItem(glowPotion);

        p.updateInventory();
    }

    // 1. 블럭 좌클릭 -> 금 블럭 (맨손 아니어도 됨? "손에 닿는 모든 게" -> 일단 맨손 OR 아무거나, 설명엔 "손으로 블럭을
    // 때리면")
    // 상세 설명: "손으로 블럭을 때리면" -> 보통 좌클릭. 도구 제한 언급 없음. 그냥 다 바꿈.
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isMidas(p))
            return;

        // [추가] 맨손일 때만 발동 (사용자 요청)
        if (p.getInventory().getItemInMainHand().getType() != Material.AIR)
            return;

        // 좌클릭 + 블럭
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null) {
            // 이미 금블럭인 것만 빼고 다시 금블럭으로
            if (e.getClickedBlock().getType() != Material.GOLD_BLOCK) {
                e.getClickedBlock().setType(Material.GOLD_BLOCK);
                e.getClickedBlock().getWorld().playSound(e.getClickedBlock().getLocation(),
                        Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
            }
        }
    }

    // 2. 엔티티 좌클릭 -> 금 블럭 (플레이어 제외)
    // PlayerInteractEntityEvent는 우클릭 기준임. 좌클릭(공격)은 EntityDamageByEntityEvent에서 처리해야
    // 함.
    // 하지만 "플레이어가 아닌 대상을 손으로 좌클릭 시" 라고 했으므로 공격 이벤트로 간주.
    @EventHandler
    public void onEntityHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p))
            return;
        if (!isMidas(p))
            return;

        // "손으로" -> 맨손 검사 필요? 설명엔 "손으로 상대를 때리면".
        // 보통 능력이 손을 써야 발동되면 맨손(AIR)을 체크함.
        // 하지만 미다스는 금칼도 줌. "손에 닿는" 컨셉이면 맨손이 어울리긴 함.
        // 상세 설명의 "금괴를 먹을 수 있다" 제외하고는 "손으로" 라고 되어 있음.
        // 일단 "맨손(AIR)"일 때만 발동하도록 설정 (안전장치 및 밸런스)
        // 만약 도구 들어도 되면 너무 사기일 수 있음 (금칼로 때리면서 방어구 다 벗김).
        // 기획의 "손으로"를 "맨손"으로 해석.
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.AIR)
            return;

        Entity target = e.getEntity();

        // 3. 상대 플레이어 타격 -> 방어구 금으로 변환
        if (target instanceof Player victim) {
            // 방어구 변환 로직
            boolean changed = false;
            ItemStack[] armor = victim.getInventory().getArmorContents();

            for (int i = 0; i < armor.length; i++) {
                ItemStack piece = armor[i];
                if (piece != null && piece.getType() != Material.AIR) {
                    Material goldType = getGoldArmorType(piece.getType());
                    if (goldType != null && piece.getType() != goldType) {
                        armor[i].setType(goldType);
                        changed = true;
                    }
                }
            }

            if (changed) {
                victim.getInventory().setArmorContents(armor);
                // 이펙트
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f); // 띠링!
                // 메시지 출력
                Bukkit.broadcastMessage("§e미다스 : §f반짝이는 건 언제나 옳지, 안 그래?");
            }
        }
        // 4. 플레이어가 아닌 대상 -> 황금 블럭으로 교체
        else if (target instanceof LivingEntity) { // 생명체만 (액자 등 제외)
            // 즉사 시키고 그 자리에 금 블럭 설치
            target.remove();
            target.getLocation().getBlock().setType(Material.GOLD_BLOCK);
            // 이펙트
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1f, 1f);
            p.sendMessage("§e[MOC] §f대상을 황금으로 만들었습니다.");
        }
    }

    // 5. 금괴 섭취 (우클릭 -> 먹기)
    // PlayerItemConsumeEvent는 먹는 행위 완료 시점.
    // 금괴는 먹는 아이템이 아니므로 Interact에서 처리해야 함.
    @EventHandler
    public void onConsumeGold(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isMidas(p))
            return;

        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) &&
                e.getItem() != null && e.getItem().getType() == Material.GOLD_INGOT) {

            // 배고픔이 꽉 차지 않았을 때만
            if (p.getFoodLevel() < 20) {
                e.setCancelled(true); // 설치 방지 등

                // 아이템 하나 소비
                e.getItem().setAmount(e.getItem().getAmount() - 1);

                // 배고픔 회복 2칸 (4)
                p.setFoodLevel(Math.min(20, p.getFoodLevel() + 4));
                p.setSaturation(Math.min(20, p.getSaturation() + 2));

                // 먹는 소리
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 2f); // 맑은 소리 추가
            }
        }
    }

    private boolean isMidas(Player p) {
        // [게임 상태 확인] 전투 시작 전에는 카운트 안 함
        if (!MocPlugin.getInstance().getGameManager().isBattleStarted())
            return false;
        if (plugin instanceof MocPlugin moc) {
            return moc.getAbilityManager() != null && moc.getAbilityManager().hasAbility(p, getCode());
        }
        return false;
    }

    private Material getGoldArmorType(Material mat) {
        String name = mat.name();
        if (name.contains("HELMET"))
            return Material.GOLDEN_HELMET;
        if (name.contains("CHESTPLATE"))
            return Material.GOLDEN_CHESTPLATE;
        if (name.contains("LEGGINGS"))
            return Material.GOLDEN_LEGGINGS;
        if (name.contains("BOOTS"))
            return Material.GOLDEN_BOOTS;
        return null;
    }
}
