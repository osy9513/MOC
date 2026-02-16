package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;

import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class Umamusume extends Ability {

    // 플레이어 UUID -> 소환된 말
    private final Map<UUID, Horse> myHorse = new HashMap<>();

    // 플레이어 UUID -> 누적 이동 거리
    private final Map<UUID, Double> distanceTraveled = new HashMap<>();

    // 플레이어 UUID -> 이전 위치 (거리 계산용)
    private final Map<UUID, Location> lastLocations = new HashMap<>();

    // 플레이어 UUID -> 우승 여부
    private final Map<UUID, Boolean> hasWon = new HashMap<>();

    // 목표 거리 (1000 블록, 기획서에는 3000이나 현실성을 위해 조정 가능하도록 상수 처리)
    private static final double TARGET_DISTANCE = 1.0;

    public Umamusume(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "052";
    }

    @Override
    public String getName() {
        return "우마무스메";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§d유틸 ● 우마무스메(우마무스메 프리티 더비)",
                "§f트레이너로서 우마무스메를 육성하여 레이스에서 승리하세요!");
    }

    @Override
    public void giveItem(Player p) {
        // 초기화
        cleanup(p);
        hasWon.put(p.getUniqueId(), false);
        distanceTraveled.put(p.getUniqueId(), 0.0);

        detailCheck(p);

        // 말 소환
        spawnHorse(p);

        // 거리 측정 태스크 시작
        startTrackingTask(p);
    }

    private void spawnHorse(Player p) {
        Location spawnLoc = p.getLocation();
        Horse horse = p.getWorld().spawn(spawnLoc, Horse.class);

        // 주인 설정
        horse.setTamed(true);
        horse.setOwner(p);
        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));

        // 10% 확률로 골드 쉽 (흰색 말)
        boolean isGoldShip = new Random().nextDouble() < 0.1;

        if (isGoldShip) {
            horse.setColor(Horse.Color.WHITE);
            horse.setStyle(Horse.Style.NONE);
            horse.customName(Component.text("§6고루도싯푸"));
            horse.setCustomNameVisible(true);

            // 2배 스탯
            Objects.requireNonNull(horse.getAttribute(Attribute.MAX_HEALTH))
                    .setBaseValue(horse.getAttribute(Attribute.MAX_HEALTH).getValue() * 2);
            Objects.requireNonNull(horse.getAttribute(Attribute.MOVEMENT_SPEED))
                    .setBaseValue(horse.getAttribute(Attribute.MOVEMENT_SPEED).getValue() * 2);
            Objects.requireNonNull(horse.getAttribute(Attribute.JUMP_STRENGTH))
                    .setBaseValue(horse.getAttribute(Attribute.JUMP_STRENGTH).getValue() * 2);
            // 낙하 데미지 무시 설정은 별도 이벤트 리스너에서 처리하거나 속성으로 불가 (이벤트 처리 필요)
        } else {
            horse.setColor(Horse.Color.CHESTNUT); // 기본 갈색
            horse.setStyle(Horse.Style.NONE);
            horse.customName(Component.text("§d" + p.getName() + "의 우마무스메"));
            horse.setCustomNameVisible(true);
        }

        // 초기 크기 50% (0.5)
        AttributeInstance scaleAttr = horse.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(0.5);
        }

        // 등록
        myHorse.put(p.getUniqueId(), horse);
        registerSummon(p, horse);

        p.sendMessage("§d[우마무스메] §f당신의 담당 우마무스메가 도착했습니다!");
        if (isGoldShip) {
            p.sendMessage("§6[골드 쉽] §f앗! 이 녀석은 전설의 고루도싯푸?!");
        }
    }

    private void startTrackingTask(Player p) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!AbilityManager.getInstance().hasAbility(p, getCode())) {
                    this.cancel();
                    return;
                }

                Horse horse = myHorse.get(p.getUniqueId());

                // 말이 없거나 죽었으면 패스 (우승한 경우 제외)
                if (Boolean.TRUE.equals(hasWon.get(p.getUniqueId())))
                    return;

                if (horse == null || horse.isDead())
                    return;

                // [추가] 게임 시작 전에는 거리 누적 안 됨
                if (!me.user.moc.MocPlugin.getInstance().getGameManager().isBattleStarted()) {
                    lastLocations.put(p.getUniqueId(), p.getLocation()); // 위치는 계속 갱신하여 시작 직후 텔포 판정 방지
                    return;
                }

                // 플레이어가 말을 타고 있는지 확인
                if (horse.getPassengers().contains(p)) {
                    Location currentLoc = p.getLocation();
                    Location lastLoc = lastLocations.get(p.getUniqueId());

                    if (lastLoc != null && currentLoc.getWorld().equals(lastLoc.getWorld())) {
                        double dist = currentLoc.distance(lastLoc);
                        if (dist > 0) {
                            double totalDist = distanceTraveled.getOrDefault(p.getUniqueId(), 0.0) + dist;
                            distanceTraveled.put(p.getUniqueId(), totalDist);

                            // 액션바로 진행도 표시
                            int percent = (int) ((totalDist / TARGET_DISTANCE) * 100);
                            p.sendActionBar(Component.text("§d[URA Finals] 진행도: " + percent + "% ("
                                    + String.format("%.1f", totalDist) + " / " + (int) TARGET_DISTANCE + "m)"));

                            // 우승 체크
                            if (totalDist >= TARGET_DISTANCE) {
                                win(p, horse);
                            }
                        }
                    }
                    lastLocations.put(p.getUniqueId(), currentLoc);
                } else {
                    // 말을 안 타고 있으면 위치만 갱신 (순간이동 등으로 인한 거리 뻥튀기 방지)
                    lastLocations.put(p.getUniqueId(), p.getLocation());
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // 0.5초마다 체크
        registerTask(p, task);
    }

    private void win(Player p, Horse horse) {
        // 중복 우승 방지
        if (Boolean.TRUE.equals(hasWon.get(p.getUniqueId())))
            return;
        hasWon.put(p.getUniqueId(), true);

        // 1. 전체 방송
        Bukkit.broadcast(Component.text("§f "));
        Bukkit.broadcast(Component.text("§d§lURA Finals!! 우승은~! 우마무스메 " + p.getName() + " 팀 입니다~!"));
        Bukkit.broadcast(Component.text("§f "));

        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

        // 2. 말의 스탯 저장
        double maxHealth = Objects.requireNonNull(horse.getAttribute(Attribute.MAX_HEALTH)).getValue();
        double speed = Objects.requireNonNull(horse.getAttribute(Attribute.MOVEMENT_SPEED)).getValue();

        // 3. 말 퇴장 (휴식하러 감)
        // 안장 해제 (인벤토리로 반환하지 않고 그냥 삭제되는게 깔끔할듯, 혹시 모르니 그냥 사라짐)
        horse.remove();
        myHorse.remove(p.getUniqueId()); // 맵에서도 제거

        // 4. 트레이너 버프 지급
        // 최대 체력 설정
        AttributeInstance pHealth = p.getAttribute(Attribute.MAX_HEALTH);
        if (pHealth != null) {
            pHealth.setBaseValue(maxHealth);
        }
        p.setHealth(maxHealth); // [Fix] 퀘스트 완료 시 풀피로 회복

        // 이동 속도 설정 (플레이어 기본 속도는 0.2, 말은 보통 0.2~0.3)
        // 말의 속도를 그대로 플레이어에게 적용하면 너무 빠를 수 있음 (플레이어 speed attribute 기준이 다름)
        // 말의 속도(0.225 average) -> 플레이어 walk speed(0.2 default)
        // 기획 의도: "말과 같은 이동속도". 말의 속도 수치를 그대로 플레이어에게 주입.
        AttributeInstance pSpeed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (pSpeed != null) {
            pSpeed.setBaseValue(speed);
        }

        p.sendMessage("§d[SYSTEM] §f우마무스메가 명예의 전당에 올랐습니다! 우승자 버프를 획득합니다.");
    }

    @EventHandler
    public void onFeed(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Horse horse))
            return;

        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();

        // 내 말인지 확인 (주인만 먹일 수 있는게 아니라 "모든 플레이어"가 먹을 수 있음)
        // 하지만 이 말이 "누군가의 우마무스메"인지는 확인해야 함.
        UUID ownerUUID = null;
        for (Map.Entry<UUID, Horse> entry : myHorse.entrySet()) {
            if (entry.getValue().equals(horse)) {
                ownerUUID = entry.getKey();
                break;
            }
        }

        if (ownerUUID == null)
            return; // 능력으로 소환된 말이 아님

        if (hand.getType() == Material.AIR)
            return;
        if (hand.getType() == Material.SADDLE)
            return; // 안장은 제외

        // 고루도싯푸(흰색 말)이 아니면 먹이 제한 없음? 기획서엔 "아이템 1개당"이라고 되어있음.
        // 모든 아이템 먹기 가능.

        e.setCancelled(true); // 말 UI 열기 방지 및 아이템 소모 직접 처리

        // 아이템 1개 소모
        hand.setAmount(hand.getAmount() - 1);

        // 성장 로직
        // 크기 1% 증가 (0.01)
        AttributeInstance scale = horse.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(scale.getBaseValue() + 0.01);
        }

        // 체력 1칸(2) 증가
        AttributeInstance health = horse.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() + 2.0);
            horse.setHealth(Math.min(horse.getHealth() + 2.0, health.getBaseValue())); // 회복도 시켜줌
        }

        // 속도 1% 증가
        AttributeInstance speed = horse.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            // 기존 값의 1%를 더하는게 아니라, 그냥 0.01을 더하는게 아니라 "1%씩 증가"
            // 보통 스탯의 1% 증가.
            double currentBase = speed.getBaseValue();
            speed.setBaseValue(currentBase * 1.01);
        }

        // 이펙트
        horse.getWorld().spawnParticle(Particle.HEART, horse.getLocation().add(0, 1, 0), 3);
        horse.getWorld().playSound(horse.getLocation(), Sound.ENTITY_HORSE_EAT, 1f, 1f);

        // 밥 준 사람에게 메시지
        p.sendMessage("§a[SYSTEM] §f무럭무럭 자라라~! §7(현재 크기: " + String.format("%.0f", scale.getValue() * 100) + "%, 체력: "
                + String.format("%.1f", horse.getHealth()) + "/" + String.format("%.1f", health.getValue()) + ")");
    }

    @EventHandler
    public void onHorseDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Horse horse))
            return;

        // 내 말인지 확인
        UUID ownerUUID = null;
        for (Map.Entry<UUID, Horse> entry : myHorse.entrySet()) {
            if (entry.getValue().equals(horse)) {
                ownerUUID = entry.getKey();
                break;
            }
        }

        if (ownerUUID == null)
            return;

        // 우승한 상태면 패널티 없음 (이미 말 없음)
        if (Boolean.TRUE.equals(hasWon.get(ownerUUID)))
            return;

        // 트레이너에게 패널티 부여
        Player trainer = Bukkit.getPlayer(ownerUUID);
        if (trainer != null) {
            trainer.sendMessage("§c[!] 담당 우마무스메가 쓰러졌습니다... 당신은 깊은 우울에 빠집니다.");
            trainer.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 300, 2)); // 15초, 레벨 3 (amplifiers 2)
            trainer.playSound(trainer.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1f, 0.5f);
        }

        // 맵에서 제거
        myHorse.remove(ownerUUID);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§d유틸 ● 우마무스메(우마무스메 프리티 더비)");
        p.sendMessage("트레이너로서 우마무스메를 육성하여 레이스에서 승리하세요!");
        p.sendMessage(" ");
        p.sendMessage("§f시작 시 당신의 담당 우마무스메(말)가 지급됩니다.");
        p.sendMessage("§f모든 플레이어는 말에게 아이템을 우클릭하여 먹일 수 있습니다.");
        p.sendMessage("§f아이템 1개당 말의 크기 +1%, 체력 +1칸, 이동속도 +1% 증가합니다.");
        p.sendMessage("§f말을 타고 " + (int) TARGET_DISTANCE + "블럭을 이동하면 우승합니다.");
        p.sendMessage(" ");
        p.sendMessage("§e[우승 보상]");
        p.sendMessage("§f말이 집으로 돌아가고, 트레이너는 말의 최대 체력과 이동속도를 영구히 얻습니다.");
        p.sendMessage(" ");
        p.sendMessage("§c[주의]");
        p.sendMessage("§f말이 사망 시 15초간 구속 III에 걸리며 우승이 불가능해집니다.");
        p.sendMessage("§f10% 확률로 전설의 '고루도싯푸'가 등장합니다. (스탯 2배)");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p); // 기본 태스크/엔티티 정리

        // 1. 말 제거
        Horse horse = myHorse.remove(p.getUniqueId());
        if (horse != null && !horse.isDead()) {
            horse.remove();
        }

        // 2. 데이터 정리
        distanceTraveled.remove(p.getUniqueId());
        lastLocations.remove(p.getUniqueId());
        hasWon.remove(p.getUniqueId());

        // 3. [Fix] 영구 버프(속성 변경) 초기화
        // 우승 후 게임이 끝나거나 능력이 변경될 때 원래대로 되돌려야 함.
        if (p != null) {
            AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(20.0); // 기본값 20
            }
            // 현재 체력이 최대 체력보다 높으면 깎임 (자동)

            AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(0.1); // [Fix] 기본값 0.1 강제 적용
            }
            p.setWalkSpeed(0.2f); // [Fix] 걷기 속도도 기본값(0.2)으로 초기화
        }
    }
}
