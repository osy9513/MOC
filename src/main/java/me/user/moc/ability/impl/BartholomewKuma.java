package me.user.moc.ability.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import me.user.moc.ability.Ability;
import me.user.moc.config.ConfigManager;

public class BartholomewKuma extends Ability {

    private final Set<UUID> fallingTargets = new HashSet<>();
    private final ConfigManager config = ConfigManager.getInstance();
    private final Random random = new Random();

    public BartholomewKuma(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "050";
    }

    @Override
    public String getName() {
        return "바솔로뮤 쿠마";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§d복합 ● 바솔로뮤 쿠마(원피스)",
                "§f손바닥으로 상대를 튕겨내 고속으로 날려버립니다.");
    }

    @Override
    public void giveItem(Player p) {
        // [장비 지급] 바솔로뮤 쿠마의 손바닥 (Popped Chorus Fruit)
        ItemStack pad = new ItemStack(Material.POPPED_CHORUS_FRUIT);
        ItemMeta meta = pad.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d바솔로뮤 쿠마의 손바닥"); // 분홍색
            meta.setLore(Arrays.asList("§7상대를 튕겨내 여행을 보냅니다.", "§c좌클릭 시 발동 (쿨타임 15초)"));
            pad.setItemMeta(meta);
        }
        p.getInventory().addItem(pad);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§d복합 ● 바솔로뮤 쿠마(원피스)");
        p.sendMessage("§f바솔로뮤 쿠마의 손바닥으로 상대를 좌클릭하면 능력이 발동됩니다.");
        p.sendMessage("§f적중 시 상대는 맵 내 랜덤한 위치의 상공(Y+32)으로 순간이동됩니다.");
        p.sendMessage("§f이후 낙하 데미지를 입을 때 30% 증가된 피해를 입습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 15초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 바솔로뮤 쿠마의 손바닥");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        fallingTargets.remove(p.getUniqueId()); // 본인이 타겟일 수도 있으니 안전하게 제거? (아니, 타겟 목록은 내가 날린 애들이 아니라 '맞은 애들' 목록인데,
                                                // Ability 인스턴스는 하나라서 공유됨)
        // 아, fallingTargets는 '능력자'가 아니라 '피해자'의 UUID를 저장해야 함.
        // 이 Ability 클래스는 싱글톤처럼 하나만 존재하여 모든 쿠마 유저가 공유함?
        // AbilityManager 구조상 능력 객체는 하나만 생성되어 등록됨. (new BartholomewKuma(plugin))
        // 따라서 fallingTargets는 전역 공유됨.
        // cleanup은 해당 능력을 가진 플레이어가 능력을 잃을 때 호출됨.
        // 쿠마 플레이어가 나가도, 이미 날아간 피해자는 여전히 낙하뎀을 더 받아야 하므로 여기서는 굳이 피해자 목록을 지울 필요는 없음.
        // 다만 메모리 누수 방지를 위해 오랫동안 떠있는 애들을 지워야 할 수도 있지만, 낙하하면 지워지니 괜찮음.
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p))
            return;
        if (!me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode()))
            return; // 쿠마 능력자인지 확인 (Fix: activeEntities -> AbilityManager)

        // 아이템 확인
        ItemStack mainHand = p.getInventory().getItemInMainHand();
        if (mainHand.getType() != Material.POPPED_CHORUS_FRUIT)
            return;

        // 쿨타임 확인 (기본적으로 좌클릭 공격은 쿨타임 없이 나가지만, 능력 발동은 쿨타임 적용)
        if (!checkCooldown(p)) {
            // 능력이 발동 안 되면 일반 공격 데미지는 들어감 (혹은 캔슬?)
            // "손바닥으로 상대를 튕겨내..." -> 능력 발동 시에만 튕겨내고, 쿨타임 중엔 그냥 때리는 건가?
            // 보통 능력 아이템은 쿨타임 중에 메시지를 띄우고 능력 발동을 안함.
            // 여기서는 쿨타임 체크 메시지는 hasCooldown 내부에서 처리한다고 가정하거나 직접 처리.
            // Ability 클래스에 hasCooldown이 있는지 확인 필요. (보통 구현함)
            return;
        }

        if (!(e.getEntity() instanceof LivingEntity target))
            return;

        // 능력 발동
        setCooldown(p, 15); // 15초 쿨타임

        // 1. 텔레포트 좌표 계산
        // 맵 중앙을 기준으로 config.map_size 범위 내 랜덤 좌표
        // ConfigManager에 map_size가 있고, ArenaManager는 보통 (0, 0) 근처를 씀?
        // 게임 중앙 좌표를 가져와야 정확함.
        // 하지만 Ability에서 GameManager나 ArenaManager에 접근하기 까다로울 수 있음.
        // 보통 맵은 (0,0) ~ (size, size) 가 아니라 center 기준 +- size/2 임.
        // ConfigManager.map_size는 지름일 수도 있고 반지름일 수도 있음. (ArenaManager를 보면 size =
        // border.getSize() / 2.0; 로 반지름 계산함)
        // ConfigManager.map_size는 "전장 크기" -> 보통 지름(Diameter)을 의미함.

        Location center = p.getWorld().getWorldBorder().getCenter(); // 현재 월드보더 센터 사용
        double radius = config.map_size / 2.0;

        // 안전한 랜덤 좌표 (반지름보다 약간 작게)
        double safeRadius = Math.max(0, radius - 5);
        double randX = center.getX() + (random.nextDouble() * 2 - 1) * safeRadius;
        double randZ = center.getZ() + (random.nextDouble() * 2 - 1) * safeRadius;
        double targetY = target.getLocation().getY() + 32; // 32 블럭으로 너픔

        // 월드 높이 제한 확인
        if (targetY > 319)
            targetY = 319;

        Location teleportLoc = new Location(center.getWorld(), randX, targetY, randZ);

        // 시선 처리 (아래를 보게 할까? 랜덤?) -> 그냥 유지
        teleportLoc.setYaw(target.getLocation().getYaw());
        teleportLoc.setPitch(target.getLocation().getPitch());

        // 2. 이펙트 및 메시지
        // 살구색 파티클 (DUST, Color.fromRGB(251, 206, 177))
        target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5,
                new Particle.DustOptions(Color.fromRGB(251, 206, 177), 2.0f));

        // 소리 (텔레포트 소리 + 둔탁한 타격음)
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        target.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2f); // 팟! 하는 느낌
        Bukkit.broadcastMessage("§d바솔로뮤 쿠마 : 여행을 한다면, 어디로 가고 싶나?");

        // 3. 텔레포트 실행
        target.teleport(teleportLoc);

        // 4. 낙하 데미지 증가 등록
        fallingTargets.add(target.getUniqueId());
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL)
            return;
        if (fallingTargets.contains(e.getEntity().getUniqueId())) {
            // 데미지 30% 증가
            double original = e.getDamage();
            e.setDamage(original * 1.3);

            // 목록에서 제거 (한 번만 적용)
            fallingTargets.remove(e.getEntity().getUniqueId());

            // 확인용 메시지? (생략)
        }
    }
}
