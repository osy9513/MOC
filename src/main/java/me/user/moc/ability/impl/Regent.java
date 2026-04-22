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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
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
        p.getInventory().remove(Material.IRON_SWORD); // 철 칼 삭제
        // [롤백] 나무검에서 다시 철검을 베이스로 '군주의 칼날'을 생성합니다.
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§9군주의 칼날");
            meta.setCustomModelData(20); // 리소스팩: danjo

            // 데미지 0 설정
            AttributeModifier damageModifier = new AttributeModifier(
                    new NamespacedKey(plugin, "regent_damage"),
                    0.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.HAND);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageModifier);

            // 공속 설정
            AttributeModifier speedModifier = new AttributeModifier(
                    new NamespacedKey(plugin, "regent_speed"),
                    -2.4,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.HAND);
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, speedModifier);

            // [추가] 내구도 무한 설정 및 내구도 바 숨김
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            updateLore(meta, 0);

            item.setItemMeta(meta);
        }
        // [수정] 인벤토리의 1번 칸(배열 인덱스 0번)에 아이템을 지급합니다.
        p.getInventory().setItem(0, item);
    }

    private void updateLore(ItemMeta meta, int currentDamage) {
        meta.setLore(Arrays.asList(
                "§f쉬프트 우클릭 시 단조를 합니다.",
                "§f단조 시 데미지가 무작위로 1~3 강화됩니다.",
                " ",
                "§a현재 추가 데미지 : +" + currentDamage));
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§6유틸 ● 리젠트(슬레이 더 스파이어 2)");
        p.sendMessage("§f데미지 0인 군주의 칼날을 받습니다.");
        p.sendMessage("§f군주의 칼날을 쉬프트 우클릭 시 단조를 합니다.");
        p.sendMessage("§f단조 시 1~3 만큼 군주의 칼날의 데미지가 강화 됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 11초.");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 군주의 칼날");
        p.sendMessage("§f장비 제거 : 철 검"); // [롤백] 철검으로 복구됨
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§6유틸 ● 리젠트(슬레이 더 스파이어 2)",
                "§f더  열 심 히  노 력 하 라");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;
        if (isSilenced(p))
            return;

        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)
            return;

        // [버그 수정] 마인크래프트는 양손(주 손, 보조 손) 각각 이벤트를 발생시킴.
        // 주 손(HAND)으로 상호작용했을 때만 능력이 발동되도록 하여 소리가 중복으로 들리는 현상을 해결함.
        if (e.getHand() != EquipmentSlot.HAND)
            return;

        // 웅크리고 있는지 확인 (쉬프트 우클릭)
        if (!p.isSneaking())
            return;

        // 플레이어가 주 손에 들고 있는 아이템을 가져옵니다.
        ItemStack hand = p.getInventory().getItemInMainHand();
        // [롤백] 베이스 아이템을 나무검에서 다시 철검으로 되돌림
        if (hand.getType() != Material.IRON_SWORD || !hand.hasItemMeta())
            return;

        ItemMeta meta = hand.getItemMeta();
        // 아이템 메타가 없거나, 리젠트의 전용 무기인 '군주의 칼날'(CustomModelData 20)이 아니면 무시합니다.
        // 또한 아이템 이름에 '군주의 칼날'이 포함되어 있는지 한 번 더 확인하여 정확성을 높입니다.
        if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != 20 ||
                !meta.hasDisplayName() || !meta.getDisplayName().contains("군주의 칼날"))
            return;

        // 쿨타임 11초 체크
        if (!checkCooldown(p))
            return;
        setCooldown(p, 11.0);

        // 단조 대사 출력
        Bukkit.broadcastMessage("§6리젠트 : §f위대한 재련!");

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

        // [버프] 무작위 증가량 (1~3) 계산 - random.nextInt(3)은 0,1,2 중 하나를 뽑고 +1을 하여 최소 1을 보장함
        int additional = random.nextInt(3) + 1;
        int newDamage = currentDamage + additional;

        // 새 데미지 부여
        AttributeModifier newDamageMod = new AttributeModifier(
                new NamespacedKey(plugin, "regent_damage"),
                newDamage,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.HAND);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, newDamageMod);

        // 설명Lore 업데이트
        updateLore(meta, newDamage);

        hand.setItemMeta(meta);

        // 개인 메시지로 결과 안내
        p.sendMessage("§e단조! §f데미지가 §a+" + additional + "§f 증가하여 총 §b+" + newDamage + "§f 데미지가 되었습니다!");

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
