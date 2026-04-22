package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * [능력 코드: 085]
 * 이름: Eden (에덴)
 * 설명: 좋은 스탯이 뜨길 기도하십시오. 전투 시작 시 무작위 스탯 부여.
 */
public class Eden extends Ability {

    private final Map<UUID, Double> damageMultipliers = new HashMap<>();
    private final Map<UUID, Double> originalMaxHealth = new HashMap<>();
    private final Map<UUID, Double> originalSpeed = new HashMap<>();
    private final Map<UUID, Double> originalJump = new HashMap<>();

    public Eden(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "085";
    }

    @Override
    public String getName() {
        return "에덴";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c유틸 ● 에덴(아이작)",
                "§f좋은 스탯이 뜨길 기도하십시오.");
    }

    @Override
    public void giveItem(Player p) {
        // 에덴의 축복 아이템 지급
        ItemStack edenBlessing = new ItemStack(Material.LILY_OF_THE_VALLEY);
        ItemMeta meta = edenBlessing.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d에덴의 축복");
            meta.setLore(Arrays.asList(
                    "§7이 아이템을 들고 우승 시 다음 라운드 리롤 포인트 +3",
                    "§7우클릭 시 발밑에 꽃을 심습니다."));
            meta.setCustomModelData(1); // 리소스팩: eden
            edenBlessing.setItemMeta(meta);
        }
        p.getInventory().addItem(edenBlessing);

        // 전투 시작 시 스탯 랜덤 부여
        applyRandomStats(p);
    }

    private void applyRandomStats(Player p) {
        UUID uuid = p.getUniqueId();
        Random random = new Random();

        // 범위: -70% ~ +70% (0.3 ~ 1.7 배)
        double healthMod = 0.3 + (random.nextDouble() * 1.4);
        double damageMod = 0.3 + (random.nextDouble() * 1.4);
        double speedMod = 0.3 + (random.nextDouble() * 1.4);
        double jumpMod = 0.3 + (random.nextDouble() * 1.4);

        // 현재 스탯 백업 및 적용 (연속 변신 시 덮어쓰기 방지를 위해 putIfAbsent 사용)
        AttributeInstance maxHealthAttr = p.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            originalMaxHealth.putIfAbsent(uuid, maxHealthAttr.getBaseValue());
            double newHealth = maxHealthAttr.getBaseValue() * healthMod;
            maxHealthAttr.setBaseValue(newHealth);
            p.setHealth(Math.min(p.getHealth(), newHealth));
        }

        AttributeInstance speedAttr = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            originalSpeed.putIfAbsent(uuid, speedAttr.getBaseValue());
            speedAttr.setBaseValue(speedAttr.getBaseValue() * speedMod);
        }

        AttributeInstance jumpAttr = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (jumpAttr != null) {
            originalJump.putIfAbsent(uuid, jumpAttr.getBaseValue());
            jumpAttr.setBaseValue(jumpAttr.getBaseValue() * jumpMod);
        }

        damageMultipliers.put(uuid, damageMod);

        // 출력
        Bukkit.broadcastMessage("§c에덴 : §f지금 내 스탯은!!!!");
        p.sendMessage(" ");
        p.sendMessage("§d[에덴의 무작위 스탯 부여]");
        p.sendMessage("§f체력: " + String.format("%.0f%%", healthMod * 100));
        p.sendMessage("§f공격력: " + String.format("%.0f%%", damageMod * 100));
        p.sendMessage("§f점프력: " + String.format("%.0f%%", jumpMod * 100));
        p.sendMessage("§f이동속도: " + String.format("%.0f%%", speedMod * 100));
        p.sendMessage(" ");

        // 무지개 파티클 이펙트 (3초간)
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 60 || !p.isOnline()) {
                    this.cancel();
                    return;
                }
                Location loc = p.getLocation().add(0, 1, 0);
                for (int i = 0; i < 5; i++) {
                    p.getWorld().spawnParticle(Particle.DUST, loc.clone().add(
                            random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5), 1,
                            new Particle.DustOptions(
                                    Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256)),
                                    1.5f));
                }
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c유틸 ● 에덴(아이작)");
        p.sendMessage("§f전투 시작 시 체력, 공격력, 점프력, 이동속도가 ±70% 무작위로 부여됩니다.");
        p.sendMessage("§f에덴의 축복을 들고 라운드 승리 시 다음 라운드에 리롤 포인트 3점이 추가됩니다.");
        p.sendMessage("§f에덴의 축복 우클릭 시 발밑에 은방울 꽃을 심습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 2초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 에덴의 축복");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        UUID uuid = p.getUniqueId();

        // 스탯 원복
        if (originalMaxHealth.containsKey(uuid)) {
            p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(originalMaxHealth.remove(uuid));
        }
        if (originalSpeed.containsKey(uuid)) {
            p.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(originalSpeed.remove(uuid));
        }
        if (originalJump.containsKey(uuid)) {
            p.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(originalJump.remove(uuid));
        }
        damageMultipliers.remove(uuid);
    }

    @Override
    public void reset() {
        super.reset();
        
        // 플러그인 비활성화나 라운드 강제 종료 시, cleanup이 호출되지 않아 스탯이 영구적으로 변형되는 치명적 버그 방지
        for (Map.Entry<UUID, Double> entry : originalMaxHealth.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline() && p.getAttribute(Attribute.MAX_HEALTH) != null) {
                p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(entry.getValue());
            }
        }
        for (Map.Entry<UUID, Double> entry : originalSpeed.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline() && p.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                p.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(entry.getValue());
            }
        }
        for (Map.Entry<UUID, Double> entry : originalJump.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline() && p.getAttribute(Attribute.JUMP_STRENGTH) != null) {
                p.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(entry.getValue());
            }
        }

        originalMaxHealth.clear();
        originalSpeed.clear();
        originalJump.clear();
        damageMultipliers.clear();
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p))
            return;
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;
        if (isSilenced(p))
            return; // 침묵 상태 검사

        Double mult = damageMultipliers.get(p.getUniqueId());
        if (mult != null) {
            e.setDamage(e.getDamage() * mult);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR)
            return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.LILY_OF_THE_VALLEY)
            return;

        // 에덴의 축복 체크
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != 1)
            return;

        // 누구나 사용 가능하므로 능력자 본인 제한 해제. 침묵 검사 및 쿨타임(2초) 확인
        if (isSilenced(p))
            return;
        if (p.hasCooldown(Material.LILY_OF_THE_VALLEY))
            return;

        e.setCancelled(true);

        // 꽃을 심을 바닥 위치 확인 (공기일 때만 심도록 하여 다른 블록 덮어쓰기 방지)
        org.bukkit.block.Block block = p.getLocation().getBlock();
        if (block.getType().isAir()) {
            // 기반암 등 꽃이 심어질 수 없는 블록 위에서도 아이템화(드랍) 되지 않고 강제로 심어지도록 물리 업데이트(false) 끄기
            block.setType(Material.LILY_OF_THE_VALLEY, false);
            p.playSound(p.getLocation(), Sound.BLOCK_AZALEA_LEAVES_PLACE, 1f, 1f);

            // 바닐라 아이템 쿨타임 2초 (40틱) 적용
            p.setCooldown(Material.LILY_OF_THE_VALLEY, 40);
        }
    }
}
