package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Jadoo extends Ability {

    // [상태 관리의 격리] 싱글톤 변수 금지, Map 사용
    private final Map<UUID, Integer> sendCountMap = new HashMap<>();

    public Jadoo(MocPlugin plugin) {
        super(plugin);
        // [생성자 로직 금지] super(plugin) 외에는 로직 없음
    }

    @Override
    public String getCode() {
        return "079";
    }

    @Override
    public String getName() {
        return "최자두";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c전투 ● 최자두(안녕 자두야)",
                "§f행운의 편지를 믿고 열심히 돌립니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 최자두(안녕 자두야)");
        p.sendMessage("§f행운의 편지로 상대를 공격 시 상대 인벤토리에 행운의 편지를 전달합니다.");
        p.sendMessage("§f행운에 편지를 가지고 있는 모든 플레이어는 4초마다 행운의 편지 수 만큼 1의 데미지를 받습니다.");
        p.sendMessage("§f행운의 편지를 버릴 경우 77% 확률로 1의 데미지를 받습니다.");
        p.sendMessage("§f전달한 행운의 편지는 7의 내구도를 가집니다.");
        p.sendMessage("§f자두가 가진 행운의 편지는 파괴되지 않고,");
        p.sendMessage("§f자두가 행운의 편지를 7번 돌린 후엔 행운의 편지를 가지고 있어도 데미지를 받지 않습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 4초.");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 자두의 행운의 편지.");
        p.sendMessage("§f장비 제거 : 철 칼.");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        sendCountMap.remove(p.getUniqueId());
        p.removePotionEffect(PotionEffectType.LUCK);
        // 4초 스케줄러는 activeTasks에 등록하여 부모에서 자동 해제되도록 함.
    }

    @Override
    public void giveItem(Player p) {
        detailCheck(p);

        // 철 칼 제거 (인벤토리에서 철 검 삭제)
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == Material.IRON_SWORD) {
                p.getInventory().remove(item);
            }
        }

        // 자두의 행운의 편지 (파괴 불가 금칼) 지급
        p.getInventory().addItem(createJadooLetter());

        // 초기 카운트 설정
        sendCountMap.put(p.getUniqueId(), 0);

        // 4초 주기 스케줄러 시작
        startCurseScheduler(p);
    }

    // 자두의 행운의 편지 (본인 무기 - 파괴 불가, 데미지/공속 철검과 동일)
    private ItemStack createJadooLetter() {
        ItemStack item = new ItemStack(Material.GOLDEN_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e자두의 행운의 편지");
            meta.setCustomModelData(1); // 리소스팩: luck_letter
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            // 데미지 6 (철검과 동일, 바닐라 철검은 기본 1 + 5 = 6)
            AttributeModifier damageModifier = new AttributeModifier(UUID.randomUUID(), "generic.attackDamage", 5.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageModifier);

            // 공속 1.6 (철검과 동일, 바닐라 기준 -2.4)
            AttributeModifier speedModifier = new AttributeModifier(UUID.randomUUID(), "generic.attackSpeed", -2.4,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND);
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, speedModifier);

            item.setItemMeta(meta);
        }
        return item;
    }

    // 타인에게 주는 행운의 편지 (파괴 가능 (내구도 7), 철검 데미지/공속 동일)
    private ItemStack createCurseLetter() {
        ItemStack item = new ItemStack(Material.GOLDEN_SWORD);
        Damageable meta = (Damageable) item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c행운의 편지");
            meta.setCustomModelData(1); // 리소스팩: luck_letter

            // 금검의 최대 내구도는 32. 내구도가 7 남도록 하려면 (32 - 7 = 25)만큼 데미지를 줌.
            int maxDurability = Material.GOLDEN_SWORD.getMaxDurability(); // 32
            meta.setDamage(maxDurability - 7);

            // 데미지 3 (전달 받은 편지는 데미지 너프 1 +2 = 3)
            AttributeModifier damageModifier = new AttributeModifier(UUID.randomUUID(), "generic.attackDamage", 2.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageModifier);

            item.setItemMeta(meta);
        }
        return item;
    }

    // 자두가 상대를 편지로 때리면 상대 인벤에 편지가 들어감
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p))
            return;
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;
        if (isSilenced(p))
            return;

        // 본인이 무기(자두의 행운의 편지)를 들고 있는지 확인
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != Material.GOLDEN_SWORD || !hand.hasItemMeta())
            return;
        if (!hand.getItemMeta().getDisplayName().equals("§e자두의 행운의 편지"))
            return;

        if (e.getEntity() instanceof Player target) {
            if (target.getGameMode() == GameMode.SPECTATOR)
                return; // 관전자 제외

            // 7번 채웠으면 패스
            int count = sendCountMap.getOrDefault(p.getUniqueId(), 0);
            if (count >= 7)
                return;

            // 상대에게 편지 줌
            ItemStack curseLetter = createCurseLetter();
            HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(curseLetter);

            // 인벤토리가 꽉 찼으면 바닥에 드랍
            if (!leftover.isEmpty()) {
                target.getWorld().dropItemNaturally(target.getLocation(), leftover.get(0));
            }

            // 개수 증가 메세지 및 효과
            count++;
            sendCountMap.put(p.getUniqueId(), count);

            if (count == 1) {
                // 첫 타격 시 메시지
                Bukkit.broadcastMessage("§c최자두 : §f이 편지는 영국에서 최초로 시작되어…");
            }

            if (count == 7) {
                // 7번 완성
                Bukkit.broadcastMessage("§c최자두 : §f운이 좋아진 기분이야!");
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f); // 행운이 올라간듯한 효과음
                p.addPotionEffect(
                        new PotionEffect(PotionEffectType.LUCK, PotionEffect.INFINITE_DURATION, 6, false, false)); // 행운
                                                                                                                   // 7
                                                                                                                   // 버프
                                                                                                                   // (0부터
                                                                                                                   // 시작이므로
                                                                                                                   // 6)
            }
        }
    }

    // 행운의 편지를 버릴 때의 데미지 이벤트
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        ItemStack dropped = e.getItemDrop().getItemStack();

        if (dropped == null || dropped.getType() != Material.GOLDEN_SWORD || !dropped.hasItemMeta())
            return;
        if (!"§c행운의 편지".equals(dropped.getItemMeta().getDisplayName()))
            return;

        // 77% 확률로 1 데미지 (내 편지는 자두 본인이 돌린 후면 데미지 무시, 근데 여긴 단순 드랍이라 누구든 확률)
        if (Math.random() <= 0.77) {
            // 자두가 편지를 7개 돌렸으면 데미지 무시
            if (AbilityManager.getInstance().hasAbility(p, getCode())) {
                int count = sendCountMap.getOrDefault(p.getUniqueId(), 0);
                if (count >= 7) {
                    return; // 자두 본인이 7번 돌렸으면 버려도 데미지 안받음
                }
            }

            DamagePlayer(p, 1, null); // 이 단순 데미지는 누가 줬는지 특정불가하므로 null, 혹은 자두를 추적해야한다면 구현 필요 (현재 기획상 단순 데미지)
            p.sendMessage("§f아 편지를 버리면 안 될 거 같다..");
        }
    }

    // 4초 주기 스킬/저주 데미지 스케줄러
    private void startCurseScheduler(Player jadoo) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!jadoo.isOnline() || !AbilityManager.getInstance().hasAbility(jadoo, getCode())) {
                    this.cancel();
                    return;
                }

                int totalLettersSent = sendCountMap.getOrDefault(jadoo.getUniqueId(), 0);
                boolean isJadooSafe = totalLettersSent >= 7;

                int victimCount = 0;
                int damageCount = 0;

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getGameMode() == GameMode.SPECTATOR)
                        continue;

                    // 자두 본인이고 7번 찼으면 자기 인벤 조사 패스
                    if (p.getUniqueId().equals(jadoo.getUniqueId()) && isJadooSafe)
                        continue;

                    // 인벤토리에서 행운의 편지 개수 세기
                    int letterCount = 0;
                    for (ItemStack item : p.getInventory().getContents()) {
                        if (item != null && item.getType() == Material.GOLDEN_SWORD && item.hasItemMeta()) {
                            if ("§c행운의 편지".equals(item.getItemMeta().getDisplayName())) {
                                letterCount += item.getAmount();
                            }
                        }
                    }

                    if (letterCount > 0) {
                        // 편지 개수만큼 고정 1 데미지 주는게 아니라, '편지 개수 * 1' 데미지
                        double finalDamage = letterCount * 1.0;
                        DamagePlayer(p, finalDamage, jadoo);

                        p.sendMessage("§f아 빨리 편지를 써야할 거 같다..");

                        // 자두 본인이 아니면 피해자 통계 누적
                        if (!p.getUniqueId().equals(jadoo.getUniqueId())) {
                            victimCount++;
                            damageCount += letterCount;
                        }
                    }
                }

                // 4초마다 자두 본인에게 알림
                if (victimCount > 0) {
                    jadoo.sendMessage("§c[알림] §f현재 " + victimCount + "명에게 총 " + damageCount + " 데미지의 편지가 발동되었습니다.");
                } else if (totalLettersSent > 0) {
                    jadoo.sendMessage("§c[알림] §f현재 행운의 편지를 보유한 상대가 없습니다. (전달 횟수: " + totalLettersSent + "/7)");
                }
            }
        };
        BukkitTask bTask = task.runTaskTimer(plugin, 0L, 80L); // 0초 지연, 80틱(4초) 반복
        registerTask(jadoo, bTask);
    }

    // 타겟에게 데미지를 주고 자두에게 킬 판정 연동 (MOC_LastKiller)
    private void DamagePlayer(Player target, double amount, Player killer) {
        if (target == null || target.isDead() || target.getGameMode() == GameMode.CREATIVE
                || target.getGameMode() == GameMode.SPECTATOR)
            return;

        // 킬어트리뷰션
        if (killer != null) {
            target.setMetadata("MOC_LastKiller", new FixedMetadataValue(plugin, killer.getUniqueId().toString()));
        }

        double finalHealth = target.getHealth() - amount;
        if (finalHealth <= 0) {
            target.setHealth(0); // 더블 데미지 방지
        } else {
            target.setHealth(finalHealth);
            target.damage(0.0001); // 피격 모션 (타격감)용 미세 데미지
        }
    }
}
