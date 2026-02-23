package me.user.moc.ability.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;

public class Deidara extends Ability {

    // 플레이어별 관리 중인 TNT 목록 (기폭용, C0는 즉발이라 관리 안 함)
    private final java.util.Map<UUID, List<TNTPrimed>> managedTNTs = new java.util.HashMap<>();

    // [추가] 부싯돌 기폭 쿨타임 (3초)
    private final java.util.Map<UUID, Long> detonateCooldowns = new java.util.HashMap<>();

    public Deidara(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "044";
    }

    @Override
    public String getName() {
        return "데이다라";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c전투 ● 데이다라(나루토)",
                "§f폭발은 예술입니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 기존 철 칼 제거
        p.getInventory().remove(Material.IRON_SWORD);

        // 점토
        ItemStack item1 = new ItemStack(Material.CLAY_BALL);
        ItemMeta meta1 = item1.getItemMeta();
        if (meta1 != null) {
            meta1.setDisplayName("§f점토");
            meta1.setLore(Arrays.asList("§7우클릭 시 폭죽 탄약을 생성합니다.", "§f쿨타임: 4초"));
            meta1.setCustomModelData(1); // 리소스팩: deidara0
            item1.setItemMeta(meta1);
        }

        // 부싯돌 (기폭 장치)
        ItemStack item2 = new ItemStack(Material.FLINT);
        ItemMeta meta2 = item2.getItemMeta();
        if (meta2 != null) {
            meta2.setDisplayName("§c기폭 점토");
            meta2.setLore(Arrays.asList(
                    "§7우클릭 시 설치된 모든 TNT를 폭발시킵니다.",
                    "§c[C0 조건] §f폭죽 탄약 20개 이상 보유 시 궁극기 발동"));
            meta2.setCustomModelData(1); // 리소스팩: deidara2
            item2.setItemMeta(meta2);
        }

        p.getInventory().addItem(item1);
        p.getInventory().addItem(item2);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 데이다라(나루토)");
        p.sendMessage("§f[점토] 우클릭 시 폭죽 탄약 1개를 생성합니다.");
        p.sendMessage("§f[폭죽 탄약] 우클릭 시 소모하며 점화된 TNT를 발사합니다. (터지지 않음)");
        p.sendMessage("§f[부싯돌] 우클릭 시 발사했던 모든 TNT를 일제히 폭발시킵니다. (쿨타임: 3초)");
        p.sendMessage(" ");
        p.sendMessage("§c[C0 - 자폭]");
        p.sendMessage("§f인벤토리에 폭죽 탄약이 20개 이상일 때 부싯돌을 우클릭하면 발동.");
        p.sendMessage("§f1초 뒤 자신의 위치에 §cTNT 20개 분량의 대폭발§f을 일으킵니다.");
        p.sendMessage("§f이 폭발은 물 속에서도 피해를 주며, 자신도 휘말릴 수 있습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 4초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 점토, 부싯돌");
        p.sendMessage("§f장비 제거 : 철 칼");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        // [추가] 능력이 봉인된 상태 (침묵)인지 체크
        if (isSilenced(p))
            return;
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack item = e.getItem();
        if (item == null)
            return;

        // 1. 점토 (탄약 생성)
        if (item.getType() == Material.CLAY_BALL) {
            e.setCancelled(true);

            // [수정] 부모 클래스의 공통 쿨타임 체크 메서드 사용
            if (!checkCooldown(p))
                return;

            // 탄약 지급
            ItemStack ammo = new ItemStack(Material.FIREWORK_STAR);
            ItemMeta meta = ammo.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e폭죽 탄약");
                meta.setLore(Arrays.asList("§7우클릭하여 발사"));
                meta.setCustomModelData(1); // 리소스팩: deidara1
                ammo.setItemMeta(meta);
            }
            p.getInventory().addItem(ammo);
            p.playSound(p.getLocation(), Sound.BLOCK_GRAVEL_PLACE, 1f, 1f);

            // [수정] 부모 클래스의 공통 쿨타임 설정 메서드 사용 (자동 알림 포함)
            setCooldown(p, 4);

            return;
        }

        // 2. 폭죽 탄약 (TNT 발사)
        if (item.getType() == Material.FIREWORK_STAR) {
            e.setCancelled(true);
            // 아이템 소모
            item.setAmount(item.getAmount() - 1);

            // TNT 발사
            Location loc = p.getEyeLocation();
            TNTPrimed tnt = (TNTPrimed) p.getWorld().spawnEntity(loc.add(loc.getDirection().multiply(1.5)),
                    EntityType.TNT);
            tnt.setVelocity(loc.getDirection().multiply(1.2)); // 발사 속도
            tnt.setFuseTicks(32767); // 터지지 않게 (약 27분)
            tnt.setSource(p); // 폭발 주인 설정 (Kill Log용)

            // 관리 목록에 추가
            managedTNTs.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(tnt);

            p.playSound(p.getLocation(), Sound.ENTITY_TNT_PRIMED, 1f, 1.5f);
            return;
        }

        // 3. 부싯돌 (기폭 / C0)
        if (item.getType() == Material.FLINT) {
            e.setCancelled(true);

            // 탄약 개수 체크 (C0 발동 조건)
            int ammoCount = 0;
            for (ItemStack content : p.getInventory().getContents()) {
                if (content != null && content.getType() == Material.FIREWORK_STAR) {
                    ammoCount += content.getAmount();
                }
            }

            if (ammoCount >= 20) {
                activateC0(p);
            } else {
                detonateAll(p);
            }
        }
    }

    private void detonateAll(Player p) {
        // [추가] 기폭 쿨타임 3초 체크
        long now = System.currentTimeMillis();
        long lastUse = detonateCooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (now - lastUse < 3000) {
            long remaining = 3000 - (now - lastUse);
            p.sendMessage("§c[!] 부싯돌 기폭 쿨타임 중입니다. (" + String.format("%.1f", remaining / 1000.0) + "초)");
            return;
        }

        List<TNTPrimed> tnts = managedTNTs.get(p.getUniqueId());
        if (tnts == null || tnts.isEmpty()) {
            p.sendMessage("§c설치된 예술품이 없습니다.");
            return;
        }

        // 메시지
        Bukkit.broadcastMessage("§c데이다라 : 폭발은 예술이다!");

        int count = 0;
        for (TNTPrimed tnt : tnts) {
            if (tnt.isValid()) {
                tnt.setFuseTicks(0); // 즉시 폭발
                count++;
            }
        }
        tnts.clear(); // 목록 비우기
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
        if (count > 0) {
            p.sendMessage("§e" + count + "개의 예술을 완성했습니다.!");
            // [추가] 성공적으로 기폭을 한 경우에만 쿨타임을 걸어줍니다.
            detonateCooldowns.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }

    private void activateC0(Player p) {
        // 탄약 20개 제거
        int toRemove = 20;
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack slot = p.getInventory().getItem(i);
            if (slot == null || slot.getType() != Material.FIREWORK_STAR)
                continue;

            int amt = slot.getAmount();
            if (amt <= toRemove) {
                toRemove -= amt;
                p.getInventory().setItem(i, null);
            } else {
                slot.setAmount(amt - toRemove);
                toRemove = 0;
            }
            if (toRemove <= 0)
                break;
        }

        // 메시지 및 연출 (시전 시작)
        Bukkit.broadcastMessage("§c데이다라 : 예술은... 폭발이다!!!!!");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_TNT_PRIMED, 2f, 0.5f);

        // 크리퍼 모양 폭죽 발사
        spawnCreeperFirework(p.getLocation());

        // 1초 뒤 폭발
        new BukkitRunnable() {
            @Override
            public void run() {
                // [추가] 능력이 봉인된 상태 (침묵)인지 체크
                if (isSilenced(p))
                    return;
                if (!p.isOnline() || !AbilityManager.getInstance().hasAbility(p, getCode()))
                    return;

                spawnMassiveTNT(p);

            }
        }.runTask(plugin); // 즉시 실행
    }

    // C0 구현부 재정의: Interact에서 호출 시 즉시 실행
    private void spawnMassiveTNT(Player p) {
        Location center = p.getLocation();

        // 20개 TNT 소환 (약간 퍼트려서)
        for (int i = 0; i < 20; i++) {
            Location loc = center.clone().add(
                    (Math.random() - 0.5) * 4,
                    Math.random() * 2,
                    (Math.random() - 0.5) * 4);
            TNTPrimed tnt = (TNTPrimed) p.getWorld().spawnEntity(loc, EntityType.TNT);
            tnt.setFuseTicks(20); // 1초 뒤 폭발
            tnt.setSource(p);
            tnt.setYield(4.0f); // 기본 위력
        }

        // 본인도 휩쓸린다는 경고
        Bukkit.broadcastMessage("§c데이다라 : 갈!!!!");
    }

    private void spawnCreeperFirework(Location loc) {
        Location fireworkLoc = loc.clone().add(0, 2, 0);
        Firework fw = (Firework) loc.getWorld().spawnEntity(fireworkLoc, EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = fw.getFireworkMeta();

        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.CREEPER)
                .withColor(Color.YELLOW)
                .withFade(Color.RED)
                .withFlicker()
                .build());
        meta.setPower(1); // 1초 정도 체공
        fw.setFireworkMeta(meta);
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);

        // 관리 중인 미폭발 TNT 제거
        List<TNTPrimed> tnts = managedTNTs.remove(p.getUniqueId());
        if (tnts != null) {
            for (TNTPrimed tnt : tnts) {
                if (tnt.isValid())
                    tnt.remove();
            }
        }
    }

    @Override
    public void reset() {
        super.reset();
        managedTNTs.clear();
        detonateCooldowns.clear();
    }
}
