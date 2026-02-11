package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * [ 041 에렌 예거 ]
 * 거인으로 변신하여 강력한 근접 전투를 수행하는 능력입니다.
 */
public class ErenYeager extends Ability {

    // 거인 상태인지 확인하는 목록
    private final Set<UUID> isTitan = new HashSet<>();
    // 인벤토리 백업 데이터 (내용물)
    private final Map<UUID, ItemStack[]> inventoryBackup = new HashMap<>();
    // 인벤토리 백업 데이터 (갑옷)
    private final Map<UUID, ItemStack[]> armorBackup = new HashMap<>();
    // 거인화 종료 시간 저장 (밀리초)
    private final Map<UUID, Long> titanEndTimes = new HashMap<>();
    // 원래 최대 체력 저장 (복구용)
    private final Map<UUID, Double> originalMaxHealth = new HashMap<>();

    public ErenYeager(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "041";
    }

    @Override
    public String getName() {
        return "에렌 예거";
    }

    @Override
    public List<String> getDescription() {
        List<String> list = new ArrayList<>();
        list.add("§a유틸 ● 에렌 예거(진격의 거인)");
        list.add(" ");
        list.add("§f체력이 소모된 상태에선 거인으로 변신할 수 있습니다.");
        return list;
    }

    @Override
    public void giveItem(Player p) {
        // 추가 장비 없음
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§a유틸 ● 에렌 예거(진격의 거인)");
        p.sendMessage("§f맨손으로 쉬프트를 누른 채 좌클릭 시 거인으로 변신합니다.");
        p.sendMessage("§f거인은 체력이 6줄(120)이 되며 상시 힘 2을 얻습니다.");
        p.sendMessage("§f거인 변신 직후에는 3초 동안 재생 5를 얻습니다.");
        p.sendMessage("§f거인은 체력을 잃은 상태여야 변신이 가능합니다.");
        p.sendMessage("§f변신 전 잃은 체력 반 칸(1)당 3초 동안 지속됩니다.");
        p.sendMessage("§f거인 상태에서는 인벤토리가 비워지며 맨손으로만 싸웁니다.");
        p.sendMessage("§f거인 상태에서 쉬프트 + 좌클릭 시 남은 지속시간을 알 수 있습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 30초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void reset() {
        super.reset();
        // 모든 변신한 플레이어 강제 복구
        for (UUID uuid : new HashSet<>(isTitan)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                revertTitan(p);
            }
        }
        isTitan.clear();
        inventoryBackup.clear();
        armorBackup.clear();
        titanEndTimes.clear();
        originalMaxHealth.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        // [수정] 오프핸드 이벤트 중복 방지
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND)
            return;

        Player p = e.getPlayer();

        // 1. 내 능력인지 확인
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 2. 발동 조건: Shift + 좌클릭 + 맨손 + 메인 핸드

        // 쉬프트 중인가?
        if (!p.isSneaking())
            return;

        // 좌클릭인가? (허공 or 블럭)
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK)
            return;

        // 맨손인가?
        if (!p.getInventory().getItemInMainHand().getType().isAir())
            return;

        // 이미 거인 상태라면 시간 조회 처리
        if (isTitan.contains(p.getUniqueId())) {
            // [복구] 시전 방법과 동일하게 변경 (Shift+좌클릭 시 조회)
            long endTime = titanEndTimes.getOrDefault(p.getUniqueId(), 0L);
            long remaining = endTime - System.currentTimeMillis();
            if (remaining > 0) {
                double seconds = remaining / 1000.0;
                p.sendActionBar(
                        net.kyori.adventure.text.Component.text(String.format("§e[남은 시간] %.1f초", seconds)));
            }
            return;
        }

        // 3. 쿨타임 체크
        if (!checkCooldown(p)) {
            return;
        }

        // 4. 체력이 깎였는지 체크
        double maxHealth = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        double currentHealth = p.getHealth();

        if (currentHealth >= maxHealth) {
            p.sendMessage("§c체력이 소모된 상태에서만 거인으로 변신할 수 있습니다.");
            return;
        }

        // 5. 변신 시작
        transformTitan(p, maxHealth - currentHealth);
    }

    /**
     * 거인 변신 로직
     * 
     * @param p             대상 플레이어
     * @param damagedAmount 잃은 체력 양
     */
    private void transformTitan(Player p, double damagedAmount) {
        isTitan.add(p.getUniqueId());

        // [메시지 및 사운드]
        Bukkit.broadcastMessage("§c예렌 예거 : 구축해주겠어!!!!!");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 0.5f);
        p.getWorld().strikeLightningEffect(p.getLocation()); // 연출용 번개

        // [1. 인벤토리 백업 및 초기화]
        inventoryBackup.put(p.getUniqueId(), p.getInventory().getContents());
        armorBackup.put(p.getUniqueId(), p.getInventory().getArmorContents());
        p.getInventory().clear();

        // [2. 체력 및 버프 부여]
        // 6줄 체력 (120)
        AttributeInstance hpAttr = p.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            // [Fix] 기존 최대 체력 저장
            originalMaxHealth.put(p.getUniqueId(), hpAttr.getValue());
            hpAttr.setBaseValue(120.0);
            p.setHealth(120.0);
        }

        // 버프 부여
        // [너프] 힘 5 -> 힘 2 (Amplifier 1 = Level 2)
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 1, true, true, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 3, 4, true, true, true));

        // [3. 크기 확대 (약 15블록 높이) 및 사거리 증가]
        // 기본 Scale 1.0 -> 8.0 ~ 10.0 정도면 매우 커집니다.
        AttributeInstance scaleAttr = p.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(15.0);
        }
        // 사거리 상향 (주민 등 타격 가능하도록)
        AttributeInstance rangeAttr = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (rangeAttr != null) {
            rangeAttr.setBaseValue(27.0);
        }
        // [추가] 블록 상호작용 사거리 상향 (발 밑 블록 파괴 가능하도록)
        AttributeInstance blockRangeAttr = p.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        if (blockRangeAttr != null) {
            blockRangeAttr.setBaseValue(27.0);
        } // [4. 지속 시간 계산 및 종료/파티클/카운트다운 루프]
          // 반 칸당 3초 (1데미지당 3초 = 60틱)
        long durationTicks = (long) (damagedAmount * 60L);
        long endTime = System.currentTimeMillis() + (durationTicks * 50); // 1틱 = 50ms
        titanEndTimes.put(p.getUniqueId(), endTime);

        // [파티클 및 카운트다운 태스크]
        new BukkitRunnable() {
            int tick = 0;
            boolean countdown3 = false;
            boolean countdown2 = false;
            boolean countdown1 = false;

            @Override
            public void run() {
                if (!p.isOnline() || !isTitan.contains(p.getUniqueId())) {
                    this.cancel();
                    return;
                }

                // 3초(60틱) 동안 파티클 효과
                if (tick < 60) {
                    // 흰색과 노란색 먼지 파티클
                    org.bukkit.Particle.DustOptions whiteDust = new org.bukkit.Particle.DustOptions(
                            org.bukkit.Color.WHITE, 2.0f);
                    org.bukkit.Particle.DustOptions yellowDust = new org.bukkit.Particle.DustOptions(
                            org.bukkit.Color.YELLOW, 2.0f);

                    // [수정] 파티클 생성 위치를 발 밑부터 머리 위(15블록)까지 분포되도록 수정
                    p.getWorld().spawnParticle(org.bukkit.Particle.DUST, p.getLocation().add(0, 7.5, 0), 40, 3.0, 7.5,
                            3.0, whiteDust);
                    p.getWorld().spawnParticle(org.bukkit.Particle.DUST, p.getLocation().add(0, 7.5, 0), 40, 3.0, 7.5,
                            3.0, yellowDust);
                } // 카운트다운 (액션바)
                long remainingMillis = endTime - System.currentTimeMillis();
                if (remainingMillis <= 3000 && remainingMillis > 2000 && !countdown3) {
                    p.sendActionBar(net.kyori.adventure.text.Component.text("§c[거인화 종료 임박] 3"));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
                    countdown3 = true;
                } else if (remainingMillis <= 2000 && remainingMillis > 1000 && !countdown2) {
                    p.sendActionBar(net.kyori.adventure.text.Component.text("§c[거인화 종료 임박] 2"));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
                    countdown2 = true;
                } else if (remainingMillis <= 1000 && remainingMillis > 0 && !countdown1) {
                    p.sendActionBar(net.kyori.adventure.text.Component.text("§c[거인화 종료 임박] 1"));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.8f); // 피치 올림
                    countdown1 = true;
                }

                if (remainingMillis <= 0) {
                    revertTitan(p);
                    // 원래 능력이 등록된 상태라면 쿨타임 시작
                    if (AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
                        setCooldown(p, 30);
                    }
                    this.cancel();
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 원래 상태로 복구
     */
    private void revertTitan(Player p) {
        if (!isTitan.contains(p.getUniqueId()))
            return;

        // [중요] 토가 히미코 등 타 능력이 변신했다가 본래 능력으로 돌아간 경우,
        // 이 revert 로직이 실행되어 엉뚱하게 인벤토리를 덮어쓰거나 초기화하는 것을 방지합니다.
        // 즉, '지금도 에렌 예거 능력자인가?'를 확인합니다.
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
            // 이미 다른 능력(토가 본체)으로 돌아갔다면, 거인 관련 데이터만 조용히 지우고 종료
            isTitan.remove(p.getUniqueId());
            titanEndTimes.remove(p.getUniqueId());
            inventoryBackup.remove(p.getUniqueId());
            armorBackup.remove(p.getUniqueId());
            return;
        }

        isTitan.remove(p.getUniqueId());
        titanEndTimes.remove(p.getUniqueId());

        // 원래 최대 체력 가져오기 (기본값 20.0)
        double originMaxHp = originalMaxHealth.remove(p.getUniqueId());
        if (originMaxHp <= 0)
            originMaxHp = 20.0;

        // [1. 크기 및 사거리 원상복구]
        AttributeInstance scaleAttr = p.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(1.0);
        }
        AttributeInstance rangeAttr = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (rangeAttr != null) {
            rangeAttr.setBaseValue(3.0); // 기본값 3.0
        }
        AttributeInstance blockRangeAttr = p.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        if (blockRangeAttr != null) {
            blockRangeAttr.setBaseValue(4.5); // 기본값 4.5
        } // [2. 체력 복구 (정상 수치 20.0)] - 플러그인 설정에 따라 다를 수 있으나 보통 20
        AttributeInstance hpAttr = p.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(originMaxHp);
            if (p.getHealth() > originMaxHp)
                p.setHealth(originMaxHp);
        }

        // [3. 버프 제거]
        p.removePotionEffect(PotionEffectType.STRENGTH);
        p.removePotionEffect(PotionEffectType.REGENERATION);

        // [4. 인벤토리 복구]
        p.getInventory().clear();
        if (inventoryBackup.containsKey(p.getUniqueId())) {
            p.getInventory().setContents(inventoryBackup.get(p.getUniqueId()));
            inventoryBackup.remove(p.getUniqueId());
        }
        if (armorBackup.containsKey(p.getUniqueId())) {
            p.getInventory().setArmorContents(armorBackup.get(p.getUniqueId()));
            armorBackup.remove(p.getUniqueId());
        }

        p.sendMessage("§e[MOC] 거인화가 해제되었습니다.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

        // [추가] 거인화 해제 시 은은한 흰 연기 파티클 1초 지속
        new BukkitRunnable() {
            int smokeTick = 0;

            @Override
            public void run() {
                if (!p.isOnline() || smokeTick >= 20) {
                    this.cancel();
                    return;
                }
                p.getWorld().spawnParticle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, p.getLocation().add(0, 1, 0), 3,
                        0.5, 1.0, 0.5, 0.02);
                smokeTick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // --- [ 제한 사항 구현 ] ---

    /**
     * 거인 상태일 때 아이템 줍기 방지
     */
    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (isTitan.contains(p.getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    /**
     * 거인 상태일 때 인벤토리 클릭(아이템 이동) 방지
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            if (isTitan.contains(p.getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    /**
     * 거인 상태에서 사망 시 원래대로 복구
     */
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (isTitan.contains(p.getUniqueId())) {
            revertTitan(p);
        }
    }
}
