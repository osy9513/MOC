package me.user.moc.ability.impl;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;

public class Janggeohan extends Ability {

    public Janggeohan(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "068";
    }

    @Override
    public String getName() {
        return "장거한";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§6전투 ● 장거한(더 킹 오브 파이터즈)",
                "§f철구대회전을 사용합니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 장비 제거: 철 칼
        p.getInventory().remove(Material.IRON_SWORD);

        ItemStack ball = new ItemStack(Material.IRON_BLOCK);
        ItemMeta meta = ball.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f철구");
            meta.setCustomModelData(1); // 리소스팩: jang_ball
            meta.setLore(List.of(
                    "§f우클릭 시 철구대회전 발동",
                    "§8[능력 아이템]"));

            // 공격력 및 공격 속도 철검과 동일하게 설정 (대미지 +6, 공속 -2.4)
            NamespacedKey damageKey = new NamespacedKey(plugin, "janggeohan_damage");
            NamespacedKey speedKey = new NamespacedKey(plugin, "janggeohan_speed");

            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(damageKey, 6.0, AttributeModifier.Operation.ADD_NUMBER,
                            EquipmentSlotGroup.HAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                    new AttributeModifier(speedKey, -2.4, AttributeModifier.Operation.ADD_NUMBER,
                            EquipmentSlotGroup.HAND));

            ball.setItemMeta(meta);
        }
        p.getInventory().addItem(ball);

        // 지급 시 상세 설명 출력
        detailCheck(p);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§6전투 ● 장거한(더 킹 오브 파이터즈)");
        p.sendMessage("§f철구를 우클릭 시 8초간 강제로 웅크린 상태가 되고 철구를 휘둘려");
        p.sendMessage("§f주변 12*12 범위에 10의 피해를 주고 0.5칸 넉백을 입힙니다.");
        p.sendMessage("§f능력 발동 시 손에 철구만 들 수 있습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 15초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 철구");
        p.sendMessage("§f장비 제거 : 철 검");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 관전자 처리 및 침묵 처리
        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR)
            return;
        if (isSilenced(p))
            return;

        // 능력이 있는지 확인
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack hand = e.getItem();
            if (hand != null && hand.getType() == Material.IRON_BLOCK && hand.hasItemMeta()
                    && hand.getItemMeta().hasCustomModelData() && hand.getItemMeta().getCustomModelData() == 1) {
                e.setCancelled(true);

                // 쿨타임 체크 (15초) - 적용은 철구대회전이 끝난 후에 합니다.
                if (!checkCooldown(p))
                    return;
                // setCooldown(p, 15); 주석: 쿨타임은 회전이 끝난 후 적용

                // 발동 메시지 (전체)
                plugin.getServer().broadcastMessage("§f장거한: §l후우우우웅~!");

                // 철구 회전 시작
                startTornado(p);
            }
        }
    }

    private void startTornado(Player p) {
        BukkitRunnable task = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                // 발동 중 사망하거나 나간 경우, 혹은 능력이 변경된 경우 취소
                if (tick >= 160 || p.isDead() || !p.isOnline()
                        || !AbilityManager.getInstance().hasAbility(p, getCode())) {

                    // 능력이 끝날 때 쿨타임 적용 (능력 유지시켰다면 15초)
                    if (AbilityManager.getInstance().hasAbility(p, getCode()) && p.isOnline()) {
                        setCooldown(p, 15);
                    }

                    p.setSneaking(false);
                    // BlockDisplay 삭제 (태스크 종료 시)
                    String displayKey = "Janggeohan_Display_" + p.getUniqueId().toString();
                    String chainKey = "Janggeohan_Chain_" + p.getUniqueId().toString();
                    for (org.bukkit.entity.Entity e : p.getWorld()
                            .getEntitiesByClass(org.bukkit.entity.BlockDisplay.class)) {
                        if (e.hasMetadata(displayKey) || e.hasMetadata(chainKey)) {
                            e.remove();
                        }
                    }
                    this.cancel();
                    return;
                }

                // 웅크림 강제 적용 및 슬로우
                p.setSneaking(true);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 5, 2, false, false, false));

                // 철구 아이템 고정 검사
                ItemStack mainHand = p.getInventory().getItemInMainHand();
                if (mainHand.getType() != Material.IRON_BLOCK) {
                    // 철구가 아닌 걸 들면 강제로 찾아준다.
                    int slot = -1;
                    for (int i = 0; i < 9; i++) {
                        ItemStack item = p.getInventory().getItem(i);
                        if (item != null && item.getType() == Material.IRON_BLOCK && item.hasItemMeta()
                                && item.getItemMeta().hasCustomModelData()
                                && item.getItemMeta().getCustomModelData() == 1) {
                            slot = i;
                            break;
                        }
                    }
                    if (slot != -1) {
                        p.getInventory().setHeldItemSlot(slot);
                        p.sendMessage("§c현재 철구대회전 중 입니다.");
                    }
                }

                // 위치 계산 로직
                // [여기가 회전 속도입니다] 초당 1회전 (20틱에 360도 = 틱당 18도)
                // 숫자가 커지면 더 빨리 돌게 됩니다.
                double angle = tick * (Math.PI / 10.0);

                // [여기가 철구의 거리입니다] 숫자가 커질수록 철구가 플레이어에게서 더 멀리 생성됩니다.
                double radius = 6.0;

                // [여기가 철구의 높이입니다] Y + 3은 발바닥에서 3칸 위(머리 위쪽)를 의미합니다.
                // 더 높게 하려면 숫자를 키우고, 낮추려면 숫자를 줄이세요.
                Location center = p.getLocation().add(0, 3, 0);

                double x = radius * Math.cos(angle);
                double z = radius * Math.sin(angle);

                // 최종적으로 철구가 위치할 좌표 (중심점 + 계산된 거리)
                // [여기서 철구 전용 높이를 조절하세요] 쇠사슬은 그대로 두고, 철구만 아래로 약간 내립니다. (예: Y -0.8)
                Location ballLoc = center.clone().add(x, -1, z);

                // [이펙트 쇠사슬 강화] 쇠사슬 블럭(Material.CHAIN) BlockDisplay 생성
                String chainKey = "Janggeohan_Chain_" + p.getUniqueId().toString();

                // radius가 6이므로 대략 1칸 단위로 5개의 체인을 만듦 (0.5 ~ 5.5)
                int chainIdx = 0;
                for (double r = 0.5; r < radius; r += 1.0) {
                    Location chainLoc = center.clone().add(r * Math.cos(angle), 0, r * Math.sin(angle));

                    // 체인이 중심에서 철구를 바라보는 각도를 계산
                    Vector dir = new Vector(Math.cos(angle), 0, Math.sin(angle));
                    chainLoc.setDirection(dir); // Yaw 자동 계산

                    org.bukkit.entity.BlockDisplay chainDisplay = null;
                    String specificChainKey = chainKey + "_" + chainIdx;
                    for (org.bukkit.entity.Entity e : p.getWorld()
                            .getEntitiesByClass(org.bukkit.entity.BlockDisplay.class)) {
                        if (e.hasMetadata(specificChainKey)) {
                            chainDisplay = (org.bukkit.entity.BlockDisplay) e;
                            break;
                        }
                    }

                    if (chainDisplay == null) {
                        chainDisplay = p.getWorld().spawn(chainLoc, org.bukkit.entity.BlockDisplay.class);
                        chainDisplay.setMetadata(specificChainKey, new FixedMetadataValue(plugin, true));
                        chainDisplay.setMetadata(chainKey, new FixedMetadataValue(plugin, true));
                        // 쇠사슬 블럭 (X/Z축(axis) 등에 따라 방향이 달라질 수 있지만, Transformation 사용)
                        Material chainMat = Material.matchMaterial("CHAIN");
                        if (chainMat == null)
                            chainMat = Material.IRON_BARS;
                        chainDisplay.setBlock(chainMat.createBlockData());
                        chainDisplay.setTeleportDuration(1); // 1틱마다 부드럽게 보간

                        // 체인은 기본적으로 Y축 방향입니다.
                        // 진행방향(Z축)으로 눕히기 위해 X축을 90도 회전
                        org.joml.Quaternionf rot = new org.joml.Quaternionf();
                        rot.rotateX((float) Math.toRadians(90));

                        org.bukkit.util.Transformation transform = new org.bukkit.util.Transformation(
                                new org.joml.Vector3f(-0.55f, -0.55f, -0.55f), // 오프셋 중앙 이동
                                rot,
                                new org.joml.Vector3f(1.1f, 1.1f, 1.1f), // 약간 크게
                                new org.joml.Quaternionf());
                        chainDisplay.setTransformation(transform);

                        registerSummon(p, chainDisplay);
                    } else {
                        // 기존 방향 유지하며 텔레포트
                        chainDisplay.teleport(chainLoc);
                    }
                    chainIdx++;
                }

                // [수정] 거대 철블럭을 BlockDisplay 엔티티로 생성하여 부드럽게 공전 및 자전
                float yaw = (tick * 15.0f) % 360.0f;
                float pitch = (tick * 10.0f) % 360.0f;
                ballLoc.setYaw(yaw);
                ballLoc.setPitch(pitch);

                String displayKey = "Janggeohan_Display_" + p.getUniqueId().toString();
                org.bukkit.entity.BlockDisplay blockDisplay = null;
                for (org.bukkit.entity.Entity e : p.getWorld()
                        .getEntitiesByClass(org.bukkit.entity.BlockDisplay.class)) {
                    if (e.hasMetadata(displayKey)) {
                        blockDisplay = (org.bukkit.entity.BlockDisplay) e;
                        break;
                    }
                }

                if (blockDisplay == null) {
                    blockDisplay = p.getWorld().spawn(ballLoc, org.bukkit.entity.BlockDisplay.class);
                    blockDisplay.setMetadata(displayKey, new FixedMetadataValue(plugin, true));
                    blockDisplay.setBlock(Material.IRON_BLOCK.createBlockData());
                    blockDisplay.setTeleportDuration(1); // 1틱마다 부드럽게 보간

                    // Transformation: 크기 1.5배, 중심축 이동 (-0.75 오프셋)
                    org.bukkit.util.Transformation transform = new org.bukkit.util.Transformation(
                            new org.joml.Vector3f(-0.75f, -0.75f, -0.75f),
                            new org.joml.Quaternionf(),
                            new org.joml.Vector3f(1.5f, 1.5f, 1.5f),
                            new org.joml.Quaternionf());
                    blockDisplay.setTransformation(transform);

                    // 게임 종료 시(클린업) 지워지도록 registerSummon
                    registerSummon(p, blockDisplay);
                } else {
                    blockDisplay.teleport(ballLoc);
                }

                // [이펙트 강화] 이동 궤적에 강력한 풍압(SWEEP_ATTACK) 생성
                if (tick % 2 == 0) {
                    p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, ballLoc, 2, 0.8, 0.8, 0.8, 0);
                }

                // [이펙트 강화] 사운드: 쇳소리와 묵직한 타격감 혼합 (5틱마다)
                if (tick % 5 == 0) {
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.8f, 0.6f);
                }

                // 대미지 및 넉백 처리 (반경 6.5 내, 사슬 또는 철구의 현재 각도 근처에 있는 대상)
                for (org.bukkit.entity.Entity e : p.getWorld().getNearbyEntities(center, 6.5, 2.5, 6.5)) {
                    if (!(e instanceof LivingEntity target))
                        continue;
                    if (target.equals(p))
                        continue;
                    if (target instanceof Player tp && tp.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                        continue;
                    }

                    // 각도 검사를 통해 궤적 상에 있는지 확인
                    Vector toEntity = target.getLocation().toVector().subtract(center.toVector());
                    toEntity.setY(0);
                    if (toEntity.lengthSquared() > 0) {
                        double targetAngle = Math.atan2(toEntity.getZ(), toEntity.getX());
                        if (targetAngle < 0)
                            targetAngle += 2 * Math.PI;

                        double currentAngle = angle % (2 * Math.PI);

                        // 각도 차이
                        double diff = Math.abs(currentAngle - targetAngle);
                        if (diff > Math.PI)
                            diff = 2 * Math.PI - diff;

                        // 약 45도(PI/4) 안에 들어오고, 반경 6.5 안에 있으면 타격 (사슬/철구의 타격 판정)
                        if (diff < Math.PI / 4) {
                            // 중복 타격 방지 (0.8초 쿨타임, 코드가 1초에 한바퀴 도니까 한바퀴에 한 번씩 맞게)
                            String hitKey = p.getUniqueId().toString() + "_jang_hit";
                            long lastHit = target.hasMetadata(hitKey) ? target.getMetadata(hitKey).get(0).asLong() : 0;
                            if (System.currentTimeMillis() - lastHit > 800) {
                                target.setMetadata(hitKey, new FixedMetadataValue(plugin, System.currentTimeMillis())); // 현재
                                                                                                                        // 시간
                                                                                                                        // 기록
                                // 킬 판정 연동 (MOC_LastKiller)
                                target.setMetadata("MOC_LastKiller",
                                        new FixedMetadataValue(plugin, p.getUniqueId().toString()));

                                // 대미지 10
                                target.damage(10, p);

                                // 넉백 (0.5칸 단위의 벡터를 더해줌)
                                Vector kb = toEntity.normalize().multiply(0.5).setY(0.2);
                                target.setVelocity(target.getVelocity().add(kb));
                            }
                        }
                    }
                }

                tick++;
            }
        };
        BukkitTask bTask = task.runTaskTimer(plugin, 0L, 1L);
        // 태스크 등록 (게임 끝나면 일괄 취소되도록)
        registerTask(p, bTask);
    }
}
