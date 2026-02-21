package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

public class Frieren extends Ability {

    public Frieren(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "061";
    }

    @Override
    public String getName() {
        return "프리렌";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§d전투 ● 프리렌(장송의 프리렌)",
                "§f졸트라크를 사용합니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 프리렌 지팡이
        ItemStack staff = new ItemStack(Material.STICK);
        ItemMeta staffMeta = staff.getItemMeta();
        if (staffMeta != null) {
            staffMeta.setDisplayName("§f프리렌의 지팡이");
            staffMeta.setLore(Arrays.asList(
                    "§7우클릭 시 3초의 캐스팅 후 졸트라크를 발사합니다."));
            // CustomModelData는 추후 리소스팩 적용 시 변경 가능
            staffMeta.setCustomModelData(2);
            // [추가] 오직 들었을 때만 철검과 동일한 성능 발휘 (기본+6 피해망가, 1.6 속도)
            org.bukkit.attribute.AttributeModifier damageMod = new org.bukkit.attribute.AttributeModifier(
                    new org.bukkit.NamespacedKey(plugin, "frieren_dmg"),
                    6.0,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                    org.bukkit.inventory.EquipmentSlotGroup.MAINHAND);
            org.bukkit.attribute.AttributeModifier speedMod = new org.bukkit.attribute.AttributeModifier(
                    new org.bukkit.NamespacedKey(plugin, "frieren_spd"),
                    -2.4,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                    org.bukkit.inventory.EquipmentSlotGroup.MAINHAND);

            staffMeta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_DAMAGE, damageMod);
            staffMeta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_SPEED, speedMod);

            staff.setItemMeta(staffMeta);
        }

        // [추가] 기존에 지급받은 철검 제거
        p.getInventory().remove(Material.IRON_SWORD);
        p.getInventory().addItem(staff);

        // 옷만 녹이는 약 (투척용 보라색 물약)
        ItemStack potion = new ItemStack(Material.SPLASH_POTION);
        PotionMeta potionMeta = (PotionMeta) potion.getItemMeta();
        if (potionMeta != null) {
            potionMeta.setDisplayName("§5옷만 녹이는 약");
            potionMeta.setLore(Arrays.asList(
                    "§7주변 개체의 방어구를 완벽히 녹여버립니다. (자신 포함)"));
            // 보라색 색상 지정
            potionMeta.setColor(Color.PURPLE);
            potion.setItemMeta(potionMeta);
        }
        p.getInventory().addItem(potion);

        // 엘프 패시브: 이동 속도 13% 감소
        // 구속 버프(SLOWNESS) 0레벨 = 약 15% 감소 효과
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 0, false, false, true));
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§d전투 ● 프리렌(장송의 프리렌)");
        p.sendMessage("§f지팡이를 우클릭 시 3초의 캐스팅 후 졸트라크를 시전하여");
        p.sendMessage("§f전방 30블럭 직선으로 방어 무시 30데미지를 줍니다.");
        p.sendMessage("§f엘프인 프리렌은 시간개념이 달라 늘 여유롭습니다.");
        p.sendMessage("§f항상 13% 느리게 이동합니다.");
        p.sendMessage("§f지급된 옷만 녹이는 약을 던져 맞은 상대의 방어구를 영구적으로 파괴합니다. (자신도 맞을 수 있습니다)");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 15초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 지팡이, 옷만 녹이는 약");
        p.sendMessage("§f장비 제거 : 철 칼, 재생 포션");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        // 이동 속도 감소 버프 제거
        if (p.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            p.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }

    // 캐스팅 중복 방지 시스템
    private final Set<UUID> castingPlayers = new HashSet<>();

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        ItemStack item = e.getItem();
        if (item == null || !(e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK))
            return;

        // 지팡이 확인
        if (item.getType() == Material.STICK && item.getItemMeta() != null
                && item.getItemMeta().getDisplayName().contains("지팡이")) {

            // 크리에이티브 모드면 쿨타임과 캐스팅 타이머(바로 발사)를 무시합니다.
            if (p.getGameMode() == GameMode.CREATIVE) {
                fireZoltraak(p);
                return;
            }

            if (!checkCooldown(p))
                return;

            // 이미 캐스팅 중인지 확인 (더 이상 혼석된 activeTasks를 쓰지 않습니다)
            if (castingPlayers.contains(p.getUniqueId())) {
                p.sendMessage("§c[!] 이미 스킬을 사용 중입니다.");
                return;
            }

            setCooldown(p, 15);
            startCasting(p);
        }
    }

    private void startCasting(Player p) {
        UUID uuid = p.getUniqueId();
        castingPlayers.add(uuid); // 캐스팅 명단에 추가

        BukkitTask castTask = new BukkitRunnable() {
            int ticks = 0;
            final int castTime = 60; // 3초 = 60틱

            @Override
            public void run() {
                if (!p.isOnline() || !AbilityManager.getInstance().hasAbility(p, getCode()) || p.isDead()) {
                    castingPlayers.remove(uuid); // 비정상 종료 시 명단 제거
                    this.cancel();
                    return;
                }

                // [추가] 캐스팅 도중 침묵 체크
                if (isSilenced(p)) {
                    return;
                }

                if (ticks >= castTime) {
                    fireZoltraak(p);
                    castingPlayers.remove(uuid); // 정상 발사 후 명단 제거
                    this.cancel();
                    return;
                }

                // 매 틱마다 흰색 파티클 회전 이펙트 출력 (총 4개)
                Location loc = p.getLocation().add(0, 1.2, 0); // 눈높이보다 약간 아래
                Vector dir = loc.getDirection().normalize().multiply(1.5); // 플레이어 정면 1.5블록 앞
                Location center = loc.add(dir);

                // 플레이어가 바라보는 방향에 수직인 두 벡터(right, up) 계산
                Vector front = p.getLocation().getDirection().normalize();
                Vector right = front.clone().crossProduct(new Vector(0, 1, 0)).normalize();
                Vector up = right.clone().crossProduct(front).normalize();

                double radius = 1.0;
                double angleOffset = (ticks * 15.0) * Math.PI / 180.0; // 매 틱당 15도씩 회전

                for (int i = 0; i < 4; i++) {
                    double angle = angleOffset + (i * Math.PI / 2.0); // 90도마다 1개씩 총 4개
                    Vector offset = right.clone().multiply(Math.cos(angle) * radius)
                            .add(up.clone().multiply(Math.sin(angle) * radius));
                    p.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(offset), 1, 0, 0, 0, 0);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        registerTask(p, castTask);
    }

    private void fireZoltraak(Player p) {
        Bukkit.broadcastMessage("§f프리렌 : 졸트라크");
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 1.0f);

        Location eyeLoc = p.getEyeLocation();
        Vector direction = eyeLoc.getDirection().normalize();

        // [시각 효과 고도화] 빔 간격을 0.5로 하여 더 촘촘하게 만듭니다.
        double maxRange = 30.0;
        double stepSize = 0.5;
        // 30블록 / 0.5블록 간격 = 총 60개의 파편 생성
        int maxSteps = (int) (maxRange / stepSize);
        Vector stepDir = direction.clone().multiply(stepSize);

        World world = p.getWorld();
        Location currentLoc = eyeLoc.clone();

        List<Entity> hitEnemies = new ArrayList<>();

        // 1. 데미지 계산 및 광선 이펙트 소환 위치 지정
        for (int i = 0; i < maxSteps; i++) {
            currentLoc.add(stepDir);

            // 시각적 빔 처리를 위해 각 블록마다 TextDisplay 생성
            spawnBeamSegment(p, currentLoc);

            if (currentLoc.getBlock().getType().isSolid()) {
                // 블록에 부딪혔으므로 광선 생성 중지 (관통 무기이나 블록 관통은 아님, 문제 시 수정 요망. 여기선 벽 관통 X로 상정)
                break;
            }

            // 주변 엔티티 데미지 처리 (반경도 간격에 맞게 소폭 축소)
            for (Entity e : world.getNearbyEntities(currentLoc, 0.8, 1.0, 0.8)) {
                if (e instanceof LivingEntity target && e != p && !hitEnemies.contains(e)) {
                    // 관전자 판정: 타겟이 플레이어고 관전모드면 무시
                    if (target instanceof Player tp && tp.getGameMode() == GameMode.SPECTATOR) {
                        continue;
                    }

                    // 방어력 무시(고정) 데미지 30
                    target.damage(30.0);
                    hitEnemies.add(target);
                }
            }
        }
    }

    private void spawnBeamSegment(Player p, Location loc) {
        World world = p.getWorld();
        TextDisplay display = (TextDisplay) world.spawnEntity(loc, org.bukkit.entity.EntityType.TEXT_DISPLAY);

        display.setText("§0§l|§f§l|§0§l|"); // 검-흰-검 패턴으로 빔 느낌 표현
        display.setBillboard(Billboard.CENTER);

        Transformation transform = display.getTransformation();
        transform.getScale().set(3.0f, 3.0f, 3.0f); // 빔 크기 확대
        display.setTransformation(transform);

        registerSummon(p, display);

        // 1.3초 후 투명해지며 삭제
        BukkitTask fadeTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!display.isValid()) {
                    this.cancel();
                    return;
                }

                if (ticks >= 26) { // 1.3초 = 26틱
                    display.remove();
                    this.cancel();
                    return;
                }

                // 후반부에는 투명도 같은 효과로 Text 변경을 고려할 수 있으나,
                // 간단히 일정 시간 유지 후 삭제하는 방식으로 처리.
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        registerTask(p, fadeTask);
    }

    @EventHandler
    public void onPotionSplash(PotionSplashEvent e) {
        Entity thrower = e.getPotion().getShooter() instanceof Entity ? (Entity) e.getPotion().getShooter() : null;

        // 능력을 가진 플레이어가 던진 것인지 확인 (투척자 검증)
        // 약은 던진 사람과 맞은 사람이 다르므로, 던진 물약의 속성을 검사해야 합니다.
        if (e.getPotion().getItem().getItemMeta() != null
                && e.getPotion().getItem().getItemMeta().getDisplayName().contains("옷만 녹이는 약")) {

            // 맞은 모든 엔티티의 방어구를 제거합니다.
            for (LivingEntity affected : e.getAffectedEntities()) {
                if (affected instanceof Player targetPlayer) {
                    if (targetPlayer.getGameMode() == GameMode.SPECTATOR)
                        continue;

                    targetPlayer.getInventory().setArmorContents(null);
                    targetPlayer.sendMessage("§5옷만 녹이는 약에 의해 녹았다..!");
                } else if (affected.getEquipment() != null) {
                    // 일반 몬스터의 경우
                    affected.getEquipment().setArmorContents(null);
                }
            }
        }
    }
}
