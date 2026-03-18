package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.util.Vector;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.NamespacedKey;
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

            // 아이템 설명 (Lore) 추가
            // 자두 본인의 편지는 파괴되지 않으며, 7번 돌리면 데미지를 받지 않음을 안내
            meta.setLore(Arrays.asList(
                    "§7상대를 때리면 행운의 편지를 전달합니다.",
                    " ",
                    "§c⚠ §f편지를 가진 모든 이는 4초마다",
                    "§f   §c편지 수만큼 §f데미지를 받습니다.",
                    "§c⚠ §f편지를 버리면 §c77% 확률§f로 데미지!",
                    " ",
                    "§e★ §f편지를 7번 전달하면 저주에서 면역됩니다."));

            // 데미지 4
            AttributeModifier damageModifier = new AttributeModifier(new NamespacedKey(plugin, "jadoo_damage"), 3.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageModifier);

            // 공속 1.6 (철검과 동일, 바닐라 기준 -2.4)
            AttributeModifier speedModifier = new AttributeModifier(new NamespacedKey(plugin, "jadoo_speed"), -2.4,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND);
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

            // 아이템 설명 (Lore) 추가
            // 수신자가 편지의 저주 효과를 바로 인지할 수 있도록 경고 표시
            meta.setLore(Arrays.asList(
                    "§f이 편지는 영국에서 최초로 시작되어…",
                    " ",
                    "§c⚠ §f가지고 있으면 4초마다 §c데미지§f를 받습니다!",
                    "§c⚠ §f버리면 §c77% 확률§f로 데미지를 받습니다!",
                    " ",
                    "§7내구도가 모두 닳으면 사라집니다."));

            // 금검의 최대 내구도는 32. 내구도가 7 남도록 하려면 (32 - 7 = 25)만큼 데미지를 줌.
            int maxDurability = Material.GOLDEN_SWORD.getMaxDurability(); // 32
            meta.setDamage(maxDurability - 7);

            // 데미지 3 (전달 받은 편지는 데미지 너프 1 +2 = 3)
            AttributeModifier damageModifier = new AttributeModifier(new NamespacedKey(plugin, "curse_damage"), 2.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND);
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
        // 침묵 시 편지 전달 불가
        if (isSilenced(p))
            return;
        // MOC 배틀 시작 전에는 편지 전달 불가
        if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted())
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

            // [수정] 7번 제한 제거 - 항상 편지 전달 가능
            // 단, 카운트는 7번까지만 증가 (7번 달성 후 행운 버프 보장)
            int count = sendCountMap.getOrDefault(p.getUniqueId(), 0);

            // 상대에게 편지 줌
            ItemStack curseLetter = createCurseLetter();
            HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(curseLetter);

            // 인벤토리가 꽉 찼으면 바닥에 드랍
            if (!leftover.isEmpty()) {
                target.getWorld().dropItemNaturally(target.getLocation(), leftover.get(0));
            }

            Bukkit.broadcastMessage("§c최자두 : §f이 편지는 영국에서 최초로 시작되어…");

            // 카운트 증가 (7번 달성 전까지만 증가)
            if (count < 7) {
                count++;
                sendCountMap.put(p.getUniqueId(), count);

                if (count == 7) {
                    // 7번 완성
                    Bukkit.broadcastMessage("§c최자두 : §f운이 좋아진 기분이야!");
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    // 행운 7 버프 (Amplifier 0부터 시작이므로 6 = 레벨 7)
                    p.addPotionEffect(
                            new PotionEffect(PotionEffectType.LUCK, PotionEffect.INFINITE_DURATION, 6, false, false));
                }
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

            DamagePlayer(p, 1, null);
            p.sendMessage("§7아 편지를 버리면 안 될 거 같다..");
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

                // [수정] MOC 배틀 시작 전에는 저주 데미지 불사용
                if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted())
                    return;

                int totalLettersSent = sendCountMap.getOrDefault(jadoo.getUniqueId(), 0);
                // 7번 돌린 후 자두 본인 제외 여부
                boolean isJadooSafe = totalLettersSent >= 7;

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getGameMode() == GameMode.SPECTATOR)
                        continue;

                    // 자두 본인이고 7번 찼으면 자기 인벤 조사 패스
                    if (p.getUniqueId().equals(jadoo.getUniqueId()) && isJadooSafe)
                        continue;

                    // [버그 수정] 인벤토리에서 행운의 편지 개수 세기
                    // 자두 본인 아이템("§e자두의 행운의 편지")과 타인 전달 아이템("§c행운의 편지") 모두 인식
                    int letterCount = 0;
                    for (ItemStack item : p.getInventory().getContents()) {
                        if (item == null || item.getType() != Material.GOLDEN_SWORD || !item.hasItemMeta())
                            continue;
                        String name = item.getItemMeta().getDisplayName();
                        if ("§c행운의 편지".equals(name) || "§e자두의 행운의 편지".equals(name)) {
                            letterCount += item.getAmount();
                        }
                    }

                    if (letterCount > 0) {
                        // 편지 개수 * 1 데미지 적용
                        double finalDamage = letterCount * 1.0;
                        DamagePlayer(p, finalDamage, jadoo);
                        p.sendMessage("§7아 빨리 편지를 써야할 거 같다..");
                    }
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

        // 킬어트리부션
        if (killer != null) {
            target.setMetadata("MOC_LastKiller", new FixedMetadataValue(plugin, killer.getUniqueId().toString()));
        }

        // [방어력 무시 + 이중 데미지 방지] setHealth 방식으로 처리
        // ─ damage(amount)는 EntityDamageEvent를 발동시켜 방어력 계산이 들어가고,
        // 스케줄러가 또 감지해서 데미지를 추가 적용할 수 있음.
        // ─ setHealth(0)은 Bukkit이 PlayerDeathEvent를 1번 정상 발생시켜
        // 킬로그 2번 문제 없고 방어력 무시도 보장됨.
        double finalHealth = target.getHealth() - amount;
        if (finalHealth <= 0) {
            // 사망 직전 사망자의 현재 위치를 저장해둠
            // (setHealth(0) 호출 이후에는 위치 정보가 바뀔 수 있으므로 미리 저장)
            Location deathLocation = target.getLocation().clone();

            // 사망 처리: PlayerDeathEvent 1회 발생 (킬로그 1번)
            target.setHealth(0);

            // [신규 기능] 편지 스택 데미지로 사망 시 분수 연출
            // 사망자의 몸에서 행운의 편지 12개가 4초 동안 분수처럼 쏟아짐
            spawnLetterFountain(deathLocation);
        } else {
            // 생존: 체력 직접 차감 (방어력 무시)
            target.setHealth(finalHealth);
            // [피격 모션] EntityEffect.HURT는 1.20.1부터 deprecated 예정이므로
            // 피격 소리 + DAMAGE_INDICATOR 파티클로 피격감 구현
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
            // 피격 파티클 (빨간 하트 깨지는 이펙트)
            target.getWorld().spawnParticle(
                    org.bukkit.Particle.DAMAGE_INDICATOR,
                    target.getLocation().add(0, 1, 0),
                    3, 0.2, 0.2, 0.2, 0.1);
        }
    }

    /**
     * [편지 분수 연출]
     * 사망자의 위치에서 행운의 편지 12개를 4초 동안 분수처럼 쏘아올립니다.
     * 편지는 약 0.33초(6.67틱) 간격으로 1개씩 총 12회 발사됩니다.
     * 4초 = 80틱 / 12개 ≒ 6.67틱 간격 → 반올림하여 7틱(0.35초) 간격으로 처리
     * (7틱 × 12 = 84틱 ≈ 4.2초로 4초에 근접)
     *
     * @param location 사망자가 죽은 위치
     */
    private void spawnLetterFountain(Location location) {
        // 총 발사할 편지 개수
        final int totalLetters = 12;
        // 각 편지 사이의 간격 (틱 단위, 7틱 = 0.35초)
        final long intervalTicks = 7L;

        // [분수 발사 스케줄러]
        // 7틱마다 실행되어 1개씩 편지를 쏘고, 12번 발사 후 스스로 종료
        new BukkitRunnable() {
            // 지금까지 발사한 편지 개수를 추적하는 카운터
            int count = 0;

            @Override
            public void run() {
                // 12개를 모두 발사했으면 스케줄러 종료
                if (count >= totalLetters) {
                    this.cancel();
                    return;
                }

                // ── 방향 계산 ──
                // 편지가 사방으로 골고루 퍼져나가도록 각 편지마다 다른 각도를 계산
                // 360도를 12등분하여 (30도씩) 각기 다른 방향으로 발사
                double angle = (2 * Math.PI / totalLetters) * count;

                // 수평 방향 속도 (cos, sin으로 XZ 평면 방향 결정)
                double vx = Math.cos(angle) * 0.35;
                double vz = Math.sin(angle) * 0.35;
                // 위로 솟구치는 속도 (분수처럼 올라가게)
                double vy = 0.7;

                // ── 발사 위치 설정 ──
                // 사망자 발 위치보다 살짝 위(0.1 블록)에서 발사
                Location spawnLoc = location.clone().add(0, 0.1, 0);

                // ── 아이템 에그 드랍 (분수 발사) ──
                // dropItem으로 아이템 엔티티를 생성한 뒤 속도를 직접 설정하여 분수 효과 구현
                org.bukkit.entity.Item thrownItem = location.getWorld().dropItem(spawnLoc, createCurseLetter());

                // 픽업 딜레이 설정 (40틱 = 2초 동안 주울 수 없음)
                // 분수 연출이 끝나기 전에 누가 줍는 것을 방지
                thrownItem.setPickupDelay(40);

                // 아이템에 분수 방향 속도 적용
                thrownItem.setVelocity(new Vector(vx, vy, vz));

                // ── 사운드 효과 ──
                // 편지가 쏠 때마다 살짝 음 높이가 달라지는 경쾌한 소리
                float pitch = 0.8f + (count * 0.025f); // 0.8 ~ 1.075 범위로 점진적 상승
                location.getWorld().playSound(spawnLoc, Sound.ENTITY_ITEM_PICKUP, 0.5f, pitch);

                count++;
            }
        }.runTaskTimer(plugin, 0L, intervalTicks);
    }
}
