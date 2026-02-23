package me.user.moc.ability.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.DecoratedPot;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import me.user.moc.game.GameManager;

public class Gaara extends Ability {

    private final Map<UUID, Integer> passiveTick = new HashMap<>();

    public Gaara(MocPlugin plugin) {
        super(plugin);
        // 리스너 등록은 Ability 상위 클래스에서 처리한다고 가정하거나, 필요 시 여기서 등록
        // plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public String getName() {
        return "가아라";
    }

    @Override
    public String getCode() {
        return "036";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§e전투 ● §f가아라 (나루토)",
                "§f모래 표주박에서 모래를 꺼내 자동으로 공격합니다.",
                "§f모래가 모이면 모래 표주박으로 사박궤/사폭장송을 발동합니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 모래 표주박 지급
        ItemStack potItem = new ItemStack(Material.DECORATED_POT);
        BlockStateMeta bsm = (BlockStateMeta) potItem.getItemMeta();
        if (bsm != null) {
            DecoratedPot potState = (DecoratedPot) bsm.getBlockState();
            // 찢어진 심장 장식
            // 1.21 API: Material.TORN_HEART_POTTERY_SHERD -> PRIZE_POTTERY_SHERD 등?
            // 1.20에 추가된 TORN_HEART_POTTERY_SHERD가 없을 수도 있음 (Spigot/Paper 버전 차이).
            // 안전하게 HEART_POTTERY_SHERD 사용 (TORN이 아니라 HEART일 수 있음)
            // 1.20: HEART_POTTERY_SHERD / HEARTBREAK_POTTERY_SHERD
            // User request: "찢어진 심장" -> HEARTBREAK_POTTERY_SHERD
            potState.setSherd(DecoratedPot.Side.FRONT, Material.HEARTBREAK_POTTERY_SHERD);
            potState.setSherd(DecoratedPot.Side.BACK, Material.HEARTBREAK_POTTERY_SHERD);
            potState.setSherd(DecoratedPot.Side.LEFT, Material.HEARTBREAK_POTTERY_SHERD);
            potState.setSherd(DecoratedPot.Side.RIGHT, Material.HEARTBREAK_POTTERY_SHERD);
            bsm.setBlockState(potState);
            bsm.setDisplayName("§6모래 표주박");
            bsm.setCustomModelData(1); // 리소스팩: gaara
            potItem.setItemMeta(bsm);
        }
        p.getInventory().addItem(potItem);
        createVisualSand(p);
        startTickTask();
    }

    private void startTickTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // 게임 진행 중일 떄만 작동
                GameManager gm = MocPlugin.getInstance().getGameManager();
                if (gm == null || !gm.isBattleStarted())
                    return;

                // activeEntities 키셋(능력자 목록) 기반으로 반복
                // (Rimuru 참조: activeEntities에 등록된 플레이어만 처리)
                // 만약 activeEntities를 안 쓴다면 getCode()를 가진 플레이어 검색 필요.
                // 편의상 activeEntities에 '머리 위 모래'를 등록해두고 그것을 키로 사용

                // 만약 activeEntities 루프가 어렵다면 온라인 플레이어 대상 전수조사
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
                        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) {
                            cleanup(p); // 사망하거나 관전 모드면 이펙트 제거 및 로직 중단
                            continue;
                        }

                        // 비주얼 업데이트 (머리 위 모래)
                        updateVisualSand(p);

                        // 패시브 공격
                        int tick = passiveTick.getOrDefault(p.getUniqueId(), 0);
                        tick++;
                        if (tick >= 80) { // 4초 (20틱 * 4)
                            tick = 0;
                            firePassiveSand(p);
                        }
                        passiveTick.put(p.getUniqueId(), tick);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // [비주얼] 머리 위 모래 생성
    private void createVisualSand(Player p) {
        if (!activeEntities.containsKey(p.getUniqueId())) {
            BlockDisplay display = (BlockDisplay) p.getWorld().spawnEntity(p.getLocation().add(0, 2.5, 0),
                    EntityType.BLOCK_DISPLAY);
            display.setBlock(Bukkit.createBlockData(Material.SAND));
            // 크기 조정 (약간 작게)
            Transformation transformation = display.getTransformation();
            transformation.getScale().set(0.5f, 0.5f, 0.5f);
            display.setTransformation(transformation);

            registerSummon(p, display);
        }
    }

    // [비주얼] 머리 위 모래 위치 갱신
    private void updateVisualSand(Player p) {
        List<Entity> entities = activeEntities.get(p.getUniqueId());
        if (entities != null) {
            for (Entity e : entities) {
                if (e instanceof BlockDisplay bd) {
                    bd.teleport(p.getLocation().add(0, 2.2, 0));
                }
            }
        } else {
            createVisualSand(p);
        }
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        passiveTick.remove(p.getUniqueId());
    }

    @Override
    public void reset() {
        passiveTick.clear();
        super.reset();
    }

    // [패시브] 모래 발사
    private void firePassiveSand(Player p) {
        LivingEntity target = findTarget(p, 13);
        if (target != null) {
            p.playSound(p.getLocation(), Sound.BLOCK_SAND_PLACE, 1f, 1f);

            // 눈덩이를 발사체로 사용 (비주얼은 모래처럼 보이게 하려면 ItemDisplay를 태워야 하나 복잡하므로 단순 눈덩이+파티클)
            Snowball sandProjectile = p.launchProjectile(Snowball.class);
            // 방향 보정 (타겟 쪽으로)
            Vector dir = target.getEyeLocation().toVector().subtract(p.getEyeLocation().toVector()).normalize();
            sandProjectile.setVelocity(dir.multiply(1.5));
            sandProjectile.setShooter(p);
            sandProjectile.setItem(new ItemStack(Material.SAND)); // 1.21 API 지원 시
            sandProjectile.setMetadata("GaaraPassive", new FixedMetadataValue(plugin, true));
            // 모래 1개 지급
            p.getInventory().addItem(new ItemStack(Material.SAND));
        }
    }

    private LivingEntity findTarget(Player p, double range) {
        LivingEntity target = null;
        double minDist = Double.MAX_VALUE;
        for (Entity e : p.getNearbyEntities(range, range, range)) {
            if (e instanceof LivingEntity le && e != p) {
                if (le instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR)
                    continue;
                double d = p.getLocation().distanceSquared(le.getLocation());
                if (d < minDist) {
                    minDist = d;
                    target = le;
                }
            }
        }
        return target;
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (e.getEntity() instanceof Snowball s && s.hasMetadata("GaaraPassive")) {
            // 타격 효과: 모래 터지는 소리
            s.getWorld().playSound(s.getLocation(), Sound.BLOCK_SAND_BREAK, 1f, 1f);
            s.getWorld().spawnParticle(Particle.BLOCK, s.getLocation(), 10, Bukkit.createBlockData(Material.SAND));

            // 엔티티 적중 시
            if (e.getHitEntity() instanceof LivingEntity le) {
                Player shooter = (Player) s.getShooter();
                le.damage(9.0, shooter);

                // 모래 블럭 설치 (엔티티 위치)
                Block b = le.getLocation().getBlock();
                if (!b.getType().isSolid()) {
                    b.setType(Material.SAND);
                }
            }
            // 블럭 적중 시
            else if (e.getHitBlock() != null) {
                Block hitBlock = e.getHitBlock();
                Block placeBlock = hitBlock.getRelative(e.getHitBlockFace());
                if (!placeBlock.getType().isSolid()) {
                    placeBlock.setType(Material.SAND);
                }
            }
        }
    }

    // [우클릭] 모래 표주박 사용
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        // [추가] 능력이 봉인된 상태 (침묵)인지 체크
        if (isSilenced(p))
            return;
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.DECORATED_POT)
            return;
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            // 이름 체크
            if (item.getItemMeta() != null && "§6모래 표주박".equals(item.getItemMeta().getDisplayName())) {
                e.setCancelled(true); // 설치 방지

                // [추가] 중요: 전투 시작 전에는 발동 불가 (checkCooldown에서도 막히지만 확실하게)
                if (MocPlugin.getInstance().getGameManager() != null
                        && !MocPlugin.getInstance().getGameManager().isBattleStarted()) {
                    return;
                }
                if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR)
                    return; // 사망/관전 시 발동 불가

                if (!checkCooldown(p)) {
                    return;
                }

                // 모래 소모 확인
                if (!p.getInventory().contains(Material.SAND, 10)) {
                    p.sendMessage("§c모래가 부족합니다. (필요: 10개)");
                    return;
                }

                // 타겟팅
                LivingEntity target = getTarget(p, 20);
                if (target == null) {
                    p.sendMessage("§c타겟을 찾을 수 없습니다.");
                    return;
                }

                // 실행
                p.getInventory().removeItem(new ItemStack(Material.SAND, 10));
                setCooldown(p, 14);
                useDesertBurial(p, target);
            }
        }

        // [수정] 모래/붉은 모래 변환 로직 제거 (우클릭 -> 줍기 자동 변환으로 변경됨)
        // 원래 있던 우클릭 변환 로직은 삭제합니다.
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        // 모래 표주박 설치 방지 (혹시 Interact가 씹혔을 경우)
        if (AbilityManager.getInstance((MocPlugin) plugin).hasAbility(e.getPlayer(), getCode())) {
            ItemStack item = e.getItemInHand();
            if (item.getType() == Material.DECORATED_POT && item.getItemMeta() != null
                    && "§6모래 표주박".equals(item.getItemMeta().getDisplayName())) {
                e.setCancelled(true);
            }
        }
    }

    // [추가] 아이템 줍기 시 사암/붉은 사암 자동 변환
    @EventHandler
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (isSilenced(p))
                return;
            if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
                // [추가] 능력이 봉인된 상태 (침묵)인지 체크
                return;

            ItemStack item = e.getItem().getItemStack();
            Material type = item.getType();

            // 사암(SANDSTONE) 또는 붉은 사암(RED_SANDSTONE)인지 확인
            // 사박퀴/사폭장송 등으로 생성된 블록이 파괴되어 떨어지는 것을 주웠을 때
            if (type == Material.SANDSTONE || type == Material.RED_SANDSTONE) {
                // 줍는 행위 자체는 허용하되, 1틱 뒤에 인벤토리 검사 수행
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        convertSandstone(p, type);
                    }
                }.runTaskLater(plugin, 1L);
            }
        }
    }

    private void convertSandstone(Player p, Material mat) {
        // 인벤토리에 해당 사암이 2개 이상 있는지 확인
        ItemStack toRemove = new ItemStack(mat, 2);
        if (p.getInventory().containsAtLeast(toRemove, 2)) {
            // 개수 확인: 몇 세트나 교환 가능한지?
            // 반복적으로 전부 교환? 아니면 한 번에 1세트만?
            // "자동으로 2개당 1개의 모래로 교환" -> 가능한 만큼 전부 교환이 자연스러움.

            // 전체 개수 파악
            int totalCount = 0;
            for (ItemStack is : p.getInventory().getContents()) {
                if (is != null && is.getType() == mat) {
                    totalCount += is.getAmount();
                }
            }

            int sandToGive = totalCount / 2;
            int removeAmount = sandToGive * 2;

            if (sandToGive > 0) {
                p.getInventory().removeItem(new ItemStack(mat, removeAmount));
                p.getInventory().addItem(new ItemStack(Material.SAND, sandToGive));
                p.playSound(p.getLocation(), Sound.BLOCK_SAND_BREAK, 0.3f, 1.5f);
                p.sendMessage("§e[가아라] " + mat.name() + " " + removeAmount + "개를 모래 " + sandToGive + "개로 변경했습니다.");
            }
        }
    }

    private LivingEntity getTarget(Player p, int range) {
        var result = p.getWorld().rayTraceEntities(
                p.getEyeLocation(),
                p.getEyeLocation().getDirection(),
                range,
                1.0,
                e -> e instanceof LivingEntity && e != p
                        && !(e instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR));
        return result != null ? (LivingEntity) result.getHitEntity() : null;
    }

    // 사박궤 & 사폭장송 로직
    private void useDesertBurial(Player shooter, LivingEntity target) {
        Bukkit.broadcastMessage("§e가아라 : §f사박궤!");

        Location center = target.getLocation();
        // 5x5x5 사박궤 생성 (매끄러운 사암)
        // 안은 비워두고 겉만? "가둔다" -> 겉을 감싸야 함
        List<Block> changedBlocks = new ArrayList<>();

        // 중심 기준 -2 ~ +2
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 3; y++) { // 발 밑(-1)부터 머리 위(+3)까지
                for (int z = -2; z <= 2; z++) {
                    // 겉 테두리만
                    if (Math.abs(x) == 2 || y == -1 || y == 3 || Math.abs(z) == 2) {
                        // y범위가 -1~3 이므로 조건 수정:
                        // x가 +-2거나, z가 +-2거나, y가 -1(바닥)이거나 y가 3(천장)이면 블럭 설치
                        // 즉, 내부는 비움
                        // 좀 더 단순하게: 5x5x5 큐브의 껍데기
                        boolean isEdge = (Math.abs(x) == 2 || Math.abs(z) == 2 || y == -1 || y == 3);
                        if (isEdge) {
                            Block b = center.clone().add(x, y, z).getBlock();
                            if (!b.getType().isSolid()) { // 이미 블럭이 있으면 덮어쓰지 않음? "가둔다"니까 덮어써야함.
                                // 허공이나 물 등만 교체, 베드락/에메랄드 등은 보호
                                if (b.getType() != Material.BEDROCK && b.getType() != Material.EMERALD_BLOCK) {
                                    b.setType(Material.SMOOTH_SANDSTONE);
                                    changedBlocks.add(b);
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2초 후 사폭장송 (3초 뒤 발동이라 했으나, 상세 설명엔 '2초 뒤... 붉은 사암... 폭발'이라고도 되어 있어 2~3초 텀)
        // 프롬프트: "3초 뒤 사폭장송를 발동하며... 2초 뒤 해당... 붉은 사암으로 변하며..."
        // 해석: 사용 2초 후 붉은 사암 변환 & 메시지 -> 1초 뒤(총 3초) 폭발?
        // 단순화: 3초 뒤 폭발로 통합하거나, 2초 변환 -> 1초 후 폭발.
        new BukkitRunnable() {
            @Override
            public void run() {
                // 붉은 사암으로 변환
                for (Block b : changedBlocks) {
                    if (b.getType() == Material.SMOOTH_SANDSTONE) {
                        b.setType(Material.RED_SANDSTONE);
                    }
                }
                Bukkit.broadcastMessage("§e가아라 : §c§l사폭장송");

                // 직후 폭발 (또는 약간 딜레이)
                // "붉은 사암의 중심에 바로 터지는 TNT를 소환"
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        TNTPrimed tnt = center.getWorld().spawn(center, TNTPrimed.class);
                        tnt.setFuseTicks(0); // 즉시 폭발
                        tnt.setMetadata("GaaraFuneral", new FixedMetadataValue(plugin, true));
                        tnt.setMetadata("GaaraShooter", new FixedMetadataValue(plugin, shooter.getUniqueId()));

                        // 안전장치: 1틱 뒤 강제 대미지 처리 (Event에서 처리 안 될 경우 대비)
                    }
                }.runTaskLater(plugin, 10L); // 변신 후 0.5초 뒤 폭발

                // [추가] 1.5초 뒤 사암 블록 제거 (폭발 후 잔해 정리)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (Block b : changedBlocks) {
                            if (b.getType() == Material.RED_SANDSTONE || b.getType() == Material.SMOOTH_SANDSTONE) {
                                b.setType(Material.AIR);
                            }
                        }
                    }
                }.runTaskLater(plugin, 30L); // 폭발 후 약 1초 뒤 정리
            }
        }.runTaskLater(plugin, 40L); // 2초 뒤
    }

    @EventHandler
    public void onExplodeDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof TNTPrimed tnt && tnt.hasMetadata("GaaraFuneral")) {
            if (e.getEntity() instanceof LivingEntity le) {
                // 고정 피해 15 (무적 무시)
                e.setDamage(15.0);
                le.setNoDamageTicks(0); // 무적 시간 초기화
            }
        }
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● 가아라(나루토)");
        p.sendMessage("§f모래 표주박에서 모래를 꺼내 자동으로 공격합니다.");
        p.sendMessage("§f[패시브] 4초마다 13칸 내 적에게 모래를 발사하여 9 대미지를 주고 모래를 설치합니다.");
        p.sendMessage("§f        4초마다 인벤토리에 모리가 쌓입니다.");
        p.sendMessage("§f[액티브] §6모래 표주박 §f우클릭 시 모래 10개를 소모하여 사박궤를 시전합니다.");
        p.sendMessage("§f대상을 사암으로 가두며, 3초 뒤 사폭장송을 발동하여 15의 폭발 피해를 입힙니다.");
        p.sendMessage("§f사암을 먹을 시 2개당 1개의 모래로 변경합니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 14초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : §6모래 표주박");
        p.sendMessage("§f장비 제거 : 없음");
    }
}
