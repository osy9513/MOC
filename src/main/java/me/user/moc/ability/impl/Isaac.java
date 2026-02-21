package me.user.moc.ability.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;

public class Isaac extends Ability {

    private final Map<UUID, Long> tearCooldowns = new HashMap<>(); // 눈물 쿨타임 (0.5초)
    private final Map<UUID, Long> diceCooldowns = new HashMap<>(); // 주사위 쿨타임 (7초)

    private final Random random = new Random();
    private final List<Material> ALL_ITEMS = new ArrayList<>();

    public Isaac(MocPlugin plugin) {
        super(plugin);

        // 아이템 목록 세팅 (서버 켜질 때 한 번만)
        for (Material m : Material.values()) {
            if (m.isItem() && !m.isAir()) {
                ALL_ITEMS.add(m);
            }
        }
    }

    @Override
    public String getCode() {
        return "064";
    }

    @Override
    public String getName() {
        return "아이작";
    }

    @Override
    public List<String> getDescription() {
        return java.util.Arrays.asList(
                "§c복합 ● 아이작(아이작의 번제)",
                "§f엉엉 울거나 주사위를 굴립니다.");
    }

    @Override
    public void giveItem(Player p) {

        // 장비 제거: 철 칼
        p.getInventory().remove(Material.IRON_SWORD);

        // 추가 장비: 주사위 (붉은 양털)
        ItemStack dice = new ItemStack(Material.RED_WOOL);
        ItemMeta meta = dice.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c주사위");
            // 주사위 아이템 설명 추가
            meta.setLore(java.util.Arrays.asList(
                    "§f우클릭 시 주변 10x10 반경의",
                    "§f모든 드롭 아이템이 랜덤하게 변경됩니다.",
                    "§f(쿨타임: 7초)"));
            meta.setCustomModelData(1);
            dice.setItemMeta(meta);
        }
        p.getInventory().addItem(dice);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c복합 ● 아이작(아이작의 번제)");
        p.sendMessage("§f맨손으로 좌클릭 시 0.5초마다 눈물(눈덩이)을 발사합니다.");
        p.sendMessage("§f적중 시 2의 데미지를 주며, 눈덩이는 10칸까지 직선으로 날아가다 이후 떨어집니다.");
        p.sendMessage(" ");
        p.sendMessage("§f주사위 우클릭 시 주위 10x10 범위 내 땅에 떨어진 아이템이 랜덤하게 변경됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 7초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 주사위");
        p.sendMessage("§f장비 제거 : 철 칼");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        Action action = e.getAction();
        ItemStack item = p.getInventory().getItemInMainHand();

        // 1. 공통 봉인 확인 (Tear, Dice 모두 적용)
        if (AbilityManager.silencedPlayers.contains(p.getUniqueId())) {
            // checkCooldown 내부에서도 띄워주긴 하나, 눈물은 checkCooldown을 우회하므로 직접 차단
            return;
        }

        me.user.moc.game.GameManager gm = ((MocPlugin) plugin).getGameManager();
        // 크리에이티브 모드일 경우 전투 시작 전에도 테스트를 위해 허용
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            if (gm == null || !gm.isBattleStarted()) {
                return; // 전투 시작 전 차단
            }
        }

        // [주사위(RED_WOOL) 사용 로직]
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (item.getType() == Material.RED_WOOL && item.hasItemMeta()
                    && "§c주사위".equals(item.getItemMeta().getDisplayName())) {
                e.setCancelled(true); // 블럭 설치 방지

                // OSY SIF 표준 쿨타임 체크 (가장 상위의 checkCooldown 사용)
                if (!checkCooldown(p))
                    return;

                setCooldown(p, 7); // 7초 쿨타임 부여
                useDice(p);
                return;
            }
        }

        // [맨손 좌클릭 - 눈물 발사 로직]
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            // 맨손(혹은 아이템 없이) 검사
            if (item.getType() == Material.AIR || item.getAmount() == 0) {
                long now = System.currentTimeMillis();
                long lastUsed = tearCooldowns.getOrDefault(p.getUniqueId(), 0L);
                if (now - lastUsed < 500) { // 0.5초 자체 쿨타임
                    return;
                }
                tearCooldowns.put(p.getUniqueId(), now);

                shootTear(p);
            }
        }
    }

    // 주사위의 블럭 설치 원천 차단
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        ItemStack item = e.getItemInHand();
        if (item.getType() == Material.RED_WOOL && item.hasItemMeta()
                && "§c주사위".equals(item.getItemMeta().getDisplayName())) {
            e.setCancelled(true);
        }
    }

    // 눈물(눈덩이) 투척 로직
    private void shootTear(Player p) {
        Location eyeLoc = p.getEyeLocation();
        Snowball tear = p.getWorld().spawn(eyeLoc, Snowball.class);
        tear.setShooter(p);
        tear.setVelocity(eyeLoc.getDirection().normalize().multiply(1.5));
        tear.setGravity(false); // 처음에는 중력 무시

        // 메타데이터로 데미지 2 마킹
        tear.setMetadata("isaac_tear", new FixedMetadataValue(plugin, true));

        // 타격음 (아이작 쏘는 듯한 소리)
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_EGG_THROW, 0.5f, 0.8f);

        // 10칸 이후 중력 부여 태스크
        new BukkitRunnable() {
            final Location startLoc = tear.getLocation().clone();

            @Override
            public void run() {
                if (!tear.isValid() || tear.isDead() || tear.isOnGround()) {
                    this.cancel();
                    return;
                }

                double dist = tear.getLocation().distance(startLoc);
                if (dist >= 10.0) {
                    tear.setGravity(true); // 10칸 이상 날아가면 뚝 떨어짐
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // 눈덩이 적중 시 처리
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball tear))
            return;
        if (!tear.hasMetadata("isaac_tear"))
            return;

        // 엔티티를 맞춘 경우
        if (e.getHitEntity() instanceof LivingEntity target) {
            if (tear.getShooter() instanceof Player shooter) {
                if (AbilityManager.getInstance((MocPlugin) plugin).hasAbility(shooter, getCode())) {
                    target.damage(2.0, shooter); // 2 데미지
                }
            }
        }
    }

    // 주사위 로직 (D6)
    private void useDice(Player p) {
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.2f);

        // 반경 10 (주위 10*10) 내 드롭된 아이템 탐색
        // 보통 반경 5면 10x10이 되지만 넉넉하게 반경 10을 부여함
        int changedCount = 0;
        for (Entity entity : p.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof Item itemEntity) {
                // 아이템 스택 랜덤 뽑기
                Material randomMat = ALL_ITEMS.get(random.nextInt(ALL_ITEMS.size()));
                ItemStack currentStack = itemEntity.getItemStack();
                currentStack.setType(randomMat);
                itemEntity.setItemStack(currentStack);

                // 파티클 (변경 이펙트)
                entity.getWorld().spawnParticle(Particle.ENCHANT, entity.getLocation(), 10, 0.3, 0.3, 0.3);
                changedCount++;
            }
        }

        // 능력 발동 시 메시지 출력
        p.getServer().broadcastMessage("§f아이작: 뿌!");
        p.sendMessage("§e[주사위] 주변 " + changedCount + "개의 아이템이 랜덤하게 변경되었습니다!");

        // 사용자 몸에 무지개 이펙트 (1초간)
        new BukkitRunnable() {
            int ticks = 0;
            // 무지개 색상 배열
            final Color[] colors = { Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME, Color.AQUA, Color.BLUE,
                    Color.PURPLE };

            @Override
            public void run() {
                if (ticks >= 20 || !p.isOnline() || p.isDead()) {
                    this.cancel();
                    return;
                }

                Color color = colors[(ticks / 3) % colors.length];
                p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5,
                        new Particle.DustOptions(color, 1.5f));

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
