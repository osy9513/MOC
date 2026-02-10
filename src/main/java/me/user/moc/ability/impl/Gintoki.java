package me.user.moc.ability.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.user.moc.ability.Ability;
import net.kyori.adventure.text.Component;

/**
 * 긴토키 (은혼) 능력 클래스입니다.
 * 눈 블럭을 사용하여 네오 암스트롱 사이클론 제트 암스트롱 포를 만들 수 있습니다.
 */
public class Gintoki extends Ability {

    private final String ITEM_NAME = "§f네오 암스트롱 사이클론 제트 암스트롱 포을 만들자";

    public Gintoki(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "043";
    }

    @Override
    public String getName() {
        return "긴토키";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§9복합 ● 긴토키(은혼)",
                "§f눈 블럭으로 네오 암스트롱 사이클론 제트 암스트롱 포을 만들 수 있습니다.");
    }

    @Override
    public void giveItem(Player p) {
        ItemStack snowBlock = new ItemStack(Material.SNOW_BLOCK, 1);
        ItemMeta meta = snowBlock.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(ITEM_NAME));
            meta.setLore(Arrays.asList("§7우클릭하여 눈 블럭을 설치합니다.", "§7특정 패턴으로 쌓아 완성합니다.", "§8(쿨타임 1.5초)"));
            snowBlock.setItemMeta(meta);
        }
        p.getInventory().addItem(snowBlock);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§9복합 ● 긴토키(은혼)");
        p.sendMessage("§f눈 블럭으로 네오 암스트롱 사이클론 제트 암스트롱 포을 만들 수 있습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f눈 블럭을 우클릭 시 바라본 위치에 눈 블럭 하나를 설치합니다.");
        p.sendMessage("§f아래와 같이 눈 블럭 5개를 배치하면 ");
        p.sendMessage("§f네오 암스트롱 사이클론 제트 암스트롱 포가 완성됩니다.");
        p.sendMessage("§f완성 3초 뒤 폭발하며 사방으로 눈송이 50개를 발사합니다.");
        p.sendMessage(" ");
        p.sendMessage("§f■ ← 눈.\n" + //
                "\n" + //
                "□ ← 빈공간\n" + //
                "\n" + //
                " " + //
                "\n" + //
                "□□■□□\n" + //
                "□□■□□\n" + //
                "□■■■□");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 1.5초.");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 눈블럭.");
        p.sendMessage("§f장비 제거 : 없음.");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!hasAbility(p))
            return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.SNOW_BLOCK)
            return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()
                || !ITEM_NAME.equals(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().serialize(meta.displayName())))
            return;

        // 눈 블럭은 우클릭 시 설치할 수 없고 사라지지 않음 (기본 설치 방지)
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.RIGHT_CLICK_AIR) {
            e.setCancelled(true);

            // 쿨타임 체크 (설치 행위에 쿨타무 부여)
            if (!checkCooldown(p))
                return;
            setCooldown(p, 1.5);

            // 바라보는 블록 확인
            Block targetBlock = p.getTargetBlockExact(5);
            if (targetBlock == null)
                return;

            // 클릭한 면 옆에 설치하거나, 에어면 거기 설치
            Block placeBlock = e.getClickedBlock() != null ? e.getClickedBlock().getRelative(e.getBlockFace())
                    : targetBlock;

            if (placeBlock.getType() != Material.AIR && placeBlock.getType() != Material.SNOW)
                return;

            placeBlock.setType(Material.SNOW_BLOCK);
            p.playSound(p.getLocation(), Sound.BLOCK_SNOW_PLACE, 1f, 1f);

            // 패턴 체크
            checkPattern(p, placeBlock);
        }
    }

    /**
     * 네오 암스트롱 사이클론 제트 암스트롱 포 패턴을 확인합니다.
     * 패턴:
     * B
     * B
     * BBB
     */
    private void checkPattern(Player p, Block lastPlaced) {
        // 동서남북 방향으로 체크 (수직 패턴)
        Block[][] patterns = {
                // 중심 기준으로 체크하기보다, 모든 가능한 5개 블록 조합 중 하나가 성립하는지 체크
                // 여기서는 lastPlaced가 패턴의 어느 위치든 될 수 있으므로 주변을 탐색
        };

        // 패턴의 모든 5개 블록 위치를 정의 (상대 좌표)
        // 0,0,0 (바닥 중앙), 1,0,0 (바닥 우), -1,0,0 (바닥 좌), 0,1,0 (기둥1), 0,2,0 (기둥2)
        int[][] relativeCoords = {
                { 0, 0, 0 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, 2, 0 }, // X축 나열
                { 0, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 }, { 0, 2, 0 } // Z축 나열
        };

        // lastPlaced가 위 5개 위치 중 하나라고 가정하고 전체 패턴이 완성되었는지 확인
        for (int axis = 0; axis < 2; axis++) { // X축 패턴 또는 Z축 패턴
            for (int i = 0; i < 5; i++) { // lastPlaced가 패턴의 i번째 블록이라고 가정
                int baseIdx = axis * 5;
                Location baseLoc = lastPlaced.getLocation().clone().subtract(
                        relativeCoords[baseIdx + i][0],
                        relativeCoords[baseIdx + i][1],
                        relativeCoords[baseIdx + i][2]);

                if (isFullPattern(baseLoc, axis == 0)) {
                    triggerCannon(p, baseLoc, axis == 0);
                    return;
                }
            }
        }
    }

    private boolean isFullPattern(Location base, boolean isXAxis) {
        // base는 BBB의 중앙 블록 (0,0,0) 위치여야 함
        int[][] coords = isXAxis ? new int[][] { { 0, 0, 0 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, 2, 0 } }
                : new int[][] { { 0, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 }, { 0, 2, 0 } };

        for (int[] offset : coords) {
            if (base.clone().add(offset[0], offset[1], offset[2]).getBlock().getType() != Material.SNOW_BLOCK) {
                return false;
            }
        }
        return true;
    }

    private void triggerCannon(Player p, Location base, boolean isXAxis) {
        // 완성 메시지 출력 (긴토키 제외 가장 가까운 플레이어 기준)
        Player closest = null;
        double minSharedDist = Double.MAX_VALUE;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(p))
                continue;
            double dist = online.getLocation().distance(base);
            if (dist < minSharedDist) {
                minSharedDist = dist;
                closest = online;
            }
        }

        String name = (closest != null) ? closest.getName() : p.getName();
        Bukkit.broadcast(Component.text("§6" + name + " : 이건 네오 암스트롱 사이클론 제트 암스트롱 포잖아? 완성도 높은데 어이!"));

        // 3초 뒤 폭발
        new BukkitRunnable() {
            @Override
            public void run() {
                // 패턴 블록 위치들
                int[][] coords = isXAxis
                        ? new int[][] { { 0, 0, 0 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, 2, 0 } }
                        : new int[][] { { 0, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 }, { 0, 2, 0 } };

                List<Location> blockLocs = new ArrayList<>();
                for (int[] offset : coords) {
                    Location loc = base.clone().add(offset[0], offset[1], offset[2]);
                    if (loc.getBlock().getType() == Material.SNOW_BLOCK) {
                        blockLocs.add(loc.add(0.5, 0.5, 0.5));
                        loc.getBlock().setType(Material.AIR); // 블록 제거
                    }
                }

                if (blockLocs.isEmpty())
                    return;

                // 파티클 효과
                base.getWorld().spawnParticle(Particle.SNOWFLAKE, base, 100, 2, 2, 2, 0.1);
                base.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, base, 3, 1, 1, 1); // [추가] 시각적 폭발 효과
                base.getWorld().playSound(base, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);

                // 눈송이 발사 (각 블록당 10개, 총 50개)
                for (Location loc : blockLocs) {
                    for (int i = 0; i < 10; i++) {
                        Snowball ball = loc.getWorld().spawn(loc, Snowball.class);
                        // [수정] setShooter를 쓰면 자신에게 맞지 않으므로 메타데이터로 시전자 저장
                        // ball.setShooter(p);
                        ball.setMetadata("Shooter", new FixedMetadataValue(plugin, p.getUniqueId()));

                        // [수정] 바닥으로 떨어지는 눈덩이를 줄이기 위해 Y축 상향 보정 (0.1 ~ 0.8) 및 속도 상향
                        Vector velocity = new Vector(Math.random() - 0.5, Math.random() * 0.7 + 0.1,
                                Math.random() - 0.5)
                                .normalize().multiply(1.8);
                        ball.setVelocity(velocity);

                        // 메타데이터나 커스텀 이름으로 긴토키의 눈송이임을 표시 (데미지 계산용)
                        ball.setCustomName("GintokiSnowball");
                    }
                }
            }
        }.runTaskLater(plugin, 60L); // 3초 (20틱 * 3)
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (e.getEntity() instanceof Snowball ball && "GintokiSnowball".equals(ball.getCustomName())) {
            Location loc = ball.getLocation();
            loc.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 10, 0.2, 0.2, 0.2, 0.05);
            loc.getWorld().playSound(loc, Sound.BLOCK_SNOW_BREAK, 1f, 1.5f);

            // [수정] 메타데이터에서 시전자 정보 가져오기
            if (e.getHitEntity() instanceof LivingEntity le) {
                if (le instanceof Player && ((Player) le).getGameMode() == org.bukkit.GameMode.SPECTATOR)
                    return;

                Player shooter = null;
                if (ball.hasMetadata("Shooter")) {
                    java.util.UUID shooterUuid = (java.util.UUID) ball.getMetadata("Shooter").get(0).value();
                    shooter = Bukkit.getPlayer(shooterUuid);
                }

                // 기획: 자신은 1, 타인은 25 데미지
                double damage = (le.equals(shooter)) ? 1.0 : 25.0;
                le.damage(damage, shooter);
            }
        }
    }

    /**
     * 긴토키 전용 눈 블럭이 바닐라 시스템에 의해 설치되는 것을 원천 차단합니다.
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() &&
                ITEM_NAME.equals(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .serialize(meta.displayName()))) {
            e.setCancelled(true);
        }
    }

    // 눈송이 데미지 조절 (기존 눈송이는 데미지가 없으므로 위 ProjectileHitEvent에서 직접 처리함)
    // 하지만 안전하게 EntityDamageByEntityEvent도 처리 가능
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Snowball ball && "GintokiSnowball".equals(ball.getCustomName())) {
            // 이미 ProjectileHitEvent에서 처리했으므로 여기서는 추가 작업 불필요할 수 있음
            // 만약 중복 데미지가 발생한다면 한 곳에서만 처리하도록 수정
        }
    }

    private boolean hasAbility(Player p) {
        return me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode());
    }
}
