package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Regent extends Ability {

    private final Random random = new Random();

    public Regent(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "084";
    }

    @Override
    public String getName() {
        return "리젠트";
    }

    @Override
    public void giveItem(Player p) {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f군주의 칼날");
            meta.setCustomModelData(20); // 리소스팩: danjo
            
            // 데미지 0 설정 (이 modifier를 넣으면 철검의 기본 데미지가 사라짐)
            AttributeModifier damageModifier = new AttributeModifier(
                    new NamespacedKey(plugin, "regent_damage"), 
                    0.0, 
                    AttributeModifier.Operation.ADD_NUMBER, 
                    EquipmentSlotGroup.HAND
            );
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageModifier);
            
            // 공속 설정 (철검 기본 공속 유지)
            AttributeModifier speedModifier = new AttributeModifier(
                    new NamespacedKey(plugin, "regent_speed"), 
                    -2.4, 
                    AttributeModifier.Operation.ADD_NUMBER, 
                    EquipmentSlotGroup.HAND
            );
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, speedModifier);

            updateLore(meta, 0);

            item.setItemMeta(meta);
        }
        p.getInventory().addItem(item);
    }

    private void updateLore(ItemMeta meta, int currentDamage) {
        meta.setLore(Arrays.asList(
                "§f쉬프트 우클릭 시 단조를 합니다.",
                "§f단조 시 데미지가 무작위로 0~3 강화됩니다.",
                " ",
                "§a현재 추가 데미지 : +" + currentDamage
        ));
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c유틸 ● 리젠트(슬레이 더 스파이어 2)");
        p.sendMessage("§f데미지 0인 군주의 칼날을 받습니다.");
        p.sendMessage("§f군주의 칼날을 쉬프트 우클릭 시 단조를 합니다.");
        p.sendMessage("§f단조 시 0~3 만큼 군주의 칼날의 데미지가 강화 됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 13초.");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 군주의 칼날");
        p.sendMessage("§f장비 제거 : 철 검");
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c유틸 ● 리젠트(슬레이 더 스파이어 2)",
                "§f더  열 심 히  노 력 하 라"
        );
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance().hasAbility(p, getCode())) return;
        if (isSilenced(p)) return;

        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        
        // 웅크리고 있는지 확인 (쉬프트 우클릭)
        if (!p.isSneaking()) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != Material.IRON_SWORD || !hand.hasItemMeta()) return;
        
        ItemMeta meta = hand.getItemMeta();
        if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != 20) return;

        // 쿨타임 13초 체크
        if (!checkCooldown(p)) return;
        setCooldown(p, 13.0);

        // 단조 대사 출력
        Bukkit.broadcastMessage("§c리젠트 : §f위대한 재련!");

        // 기존 데미지 찾기
        int currentDamage = 0;
        if (meta.hasAttributeModifiers() && meta.getAttributeModifiers(Attribute.ATTACK_DAMAGE) != null) {
            for (AttributeModifier mod : meta.getAttributeModifiers(Attribute.ATTACK_DAMAGE)) {
                if (mod.getKey().getKey().equals("regent_damage")) {
                    currentDamage = (int) mod.getAmount();
                    break;
                }
            }
        }

        // 기존 데미지 속성 삭제
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);

        // 무작위 증가량 (0~3) 계산
        int additional = random.nextInt(4);
        int newDamage = currentDamage + additional;

        // 새 데미지 부여
        AttributeModifier newDamageMod = new AttributeModifier(
                new NamespacedKey(plugin, "regent_damage"), 
                newDamage, 
                AttributeModifier.Operation.ADD_NUMBER, 
                EquipmentSlotGroup.HAND
        );
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, newDamageMod);

        // 설명Lore 업데이트
        updateLore(meta, newDamage);
        
        hand.setItemMeta(meta);

        // 개인 메시지로 결과 안내
        if (additional > 0) {
            p.sendMessage("§e단조 성공! §f데미지가 §a+" + additional + "§f 증가하여 총 §b+" + newDamage + "§f 데미지가 되었습니다!");
        } else {
            p.sendMessage("§7단조 실패! §f데미지가 증가하지 않았습니다.");
        }

        // 깡깡깡 소리 3회 재생 (5틱 간격)
        BukkitRunnable forgeTask = new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count >= 3 || !p.isOnline() || p.isDead()) {
                    this.cancel();
                    return;
                }
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                count++;
            }
        };
        BukkitTask bTask = forgeTask.runTaskTimer(plugin, 0L, 5L);
        registerTask(p, bTask);
    }
}
