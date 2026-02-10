package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class AmanoHina extends Ability {

    private final Random random = new Random();

    private final org.bukkit.NamespacedKey tearKey;

    public AmanoHina(JavaPlugin plugin) {
        super(plugin);
        this.tearKey = new org.bukkit.NamespacedKey(plugin, "amano_tear");
    }

    @Override
    public String getCode() {
        return "056";
    }

    @Override
    public String getName() {
        return "아마노 히나";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c전투 ● 아마노 히나(날씨의 아이)",
                "§f비가 내리면 맑게 해줍니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 지급 아이템 없음
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 아마노 히나(날씨의 아이)");
        p.sendMessage("맨손 쉬프트 좌클릭 시");
        p.sendMessage("비가 오면서 본인을 제외한 모든 플레이어 머리에 비를 떨어트립니다.");
        p.sendMessage("8초 뒤 날씨가 맑아집니다.");
        p.sendMessage("날씨가 맑아진 뒤 1초간 몸이 투명해집니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 20초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");

        giveItem(p);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 1. 능력자 체크
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        // 2. 액션 체크 (맨손 + 쉬프트 + 좌클릭)
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK)
            return;
        if (!p.isSneaking())
            return;
        if (p.getInventory().getItemInMainHand().getType() != Material.AIR)
            return;

        // 3. 쿨타임 체크
        if (!checkCooldown(p))
            return;

        // 4. 능력 발동
        startRainAndTears(p);
    }

    private void startRainAndTears(Player p) {
        World world = p.getWorld();

        // 날씨를 비로 변경
        world.setStorm(true);
        world.setWeatherDuration(200); // 넉넉하게 설정 (어차피 8초 뒤에 맑게 함)

        p.sendMessage("§b아마노 히나 : 이제부터 맑아질 거야."); // 발동 대사? 기획안엔 "8초 뒤 날씨가 맑아질 때 ... 출력"이라고 되어있지만,
                                                  // 보통 스킬 쓸 때 대사를 치는 게 자연스러움.
                                                  // 하지만 기획안 준수: "8초 뒤 날씨가 맑아질 때 ... 출력" 이라 명시됨.
                                                  // 따라서 여기선 생략하거나 짧은 효과음만.
        p.playSound(p.getLocation(), Sound.WEATHER_RAIN_ABOVE, 1f, 1f);
        p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1f); // 천둥 소리 추가

        // 구름 고리 이펙트
        Location center = p.getLocation().add(0, 1, 0);
        for (int i = 0; i < 360; i += 10) {
            double angle = Math.toRadians(i);
            double x = Math.cos(angle) * 3;
            double z = Math.sin(angle) * 3;
            world.spawnParticle(Particle.CLOUD, center.clone().add(x, 0, z), 1, 0, 0, 0, 0);
        }

        // 눈물 떨구기 태스크 (8초간, 초당 10회 = 2틱마다)
        BukkitTask task = new BukkitRunnable() {
            int tickCount = 0;
            final int maxTicks = 160; // 8초 * 20틱

            @Override
            public void run() {
                // 시간 경과 체크
                if (tickCount >= maxTicks) {
                    finishAbility(p);
                    this.cancel();
                    return;
                }

                // 매번 실행 (2틱마다)
                spawnTears(p, world);

                tickCount += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L); // 2틱마다 실행

        registerTask(p, task);
    }

    private void spawnTears(Player shooter, World world) {
        // 본인 제외 모든 생명체 대상
        for (LivingEntity target : world.getLivingEntities()) {
            if (target.equals(shooter))
                continue;
            if (target.isDead())
                continue;

            // 아머스탠드 등은 제외할 수도 있음 (게임 룰에 따라)
            if (target instanceof ArmorStand)
                continue;

            // 30블럭 위에서 3*3 범위 랜덤
            Location targetLoc = target.getLocation();
            double offsetX = (random.nextDouble() * 3) - 1.5; // -1.5 ~ 1.5
            double offsetZ = (random.nextDouble() * 3) - 1.5; // -1.5 ~ 1.5

            Location spawnLoc = targetLoc.add(offsetX, 30, offsetZ);

            // 가스트 눈물(눈덩이로 위장) 소환
            Snowball tear = world.spawn(spawnLoc, Snowball.class);
            tear.setItem(new ItemStack(Material.GHAST_TEAR));
            tear.setShooter(shooter);
            tear.getPersistentDataContainer().set(tearKey, org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
            // 아래로 가속
            tear.setVelocity(new Vector(0, -1.5, 0));

            // 소환된 엔티티 관리 (Projectiles usually clean themselves up, but good to track)
            registerSummon(shooter, tear);

            // [추가] 궤적 파티클
            BukkitTask trailTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (tear.isDead() || !tear.isValid() || tear.isOnGround()) {
                        this.cancel();
                        return;
                    }
                    world.spawnParticle(Particle.FALLING_WATER, tear.getLocation(), 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.END_ROD, tear.getLocation(), 1, 0, 0, 0, 0.01);
                }
            }.runTaskTimer(plugin, 0L, 1L);
            registerTask(shooter, trailTask); // 태스크 관리로 등록하여 안전하게 취소되도록 함
        }
    }

    private void finishAbility(Player p) {
        World world = p.getWorld();

        // 날씨 맑음
        world.setStorm(false);
        world.setThundering(false);

        // 전체 채팅 출력
        Bukkit.broadcast(net.kyori.adventure.text.Component.text("§b아마노 히나 : 이제부터 맑아질 거야."));

        // 효과음
        world.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        world.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f); // 웅장한 소리
        world.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);

        // 햇살 이펙트
        playSunshineEffect(p.getLocation());

        // 투명화 버프 (1초 = 20틱)
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY, 20, 0,
                false, false));

        // 쿨타임 적용 (8초 지속 끝난 여기 시점부터 20초)
        setCooldown(p, 20);

        // 작업 목록에서 제거는 자동으로 됨 (task cancel)
        // clean up activeTasks map entry potentially if strictly managed, but cleanup()
        // handles bulk removal.
        // Single task removal isn't strictly necessary for logic correctness as long as
        // it cancels itself.
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Snowball snowball) {
            if (snowball.getPersistentDataContainer().has(tearKey, org.bukkit.persistence.PersistentDataType.BOOLEAN)) {
                // 대미지 6으로 설정
                e.setDamage(6.0);
            }
        }
    }

    private void playSunshineEffect(Location location) {
        World world = location.getWorld();
        // 플레이어 위쪽에서 햇살이 내리쬐는 연출
        Location center = location.clone().add(0, 5, 0); // 5블럭 위

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (step >= 20) { // 1초 동안 연출
                    this.cancel();
                    return;
                }

                // 1. 위에서 빛이 퍼지는 효과 (Particle.FLAME, Particle.WAX_ON)
                for (int i = 0; i < 5; i++) {
                    double angle = random.nextDouble() * 360;
                    double distance = random.nextDouble() * 5;
                    double x = Math.cos(Math.toRadians(angle)) * distance;
                    double z = Math.sin(Math.toRadians(angle)) * distance;

                    Location particleLoc = center.clone().add(x, 0, z);
                    // 아래로 떨어지는 빛
                    world.spawnParticle(Particle.FLAME, particleLoc, 0, 0, -1, 0, 0.5); // 속도감 있게 아래로
                    world.spawnParticle(Particle.WAX_ON, particleLoc, 0, 0, -0.5, 0, 0.2);
                }

                // 2. 바닥에서 빛이 올라오는/퍼지는 효과 (Particle.FLASH - 가끔)
                if (step % 5 == 0) {
                    // FLASH 파티클이 서버에서 크래시를 유발하므로 END_ROD로 대체
                    world.spawnParticle(Particle.END_ROD, location.clone().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
                }

                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
