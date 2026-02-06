package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public class Spiderman extends Ability {

    // 플레이어가 설치한 거미줄 위치를 저장 (재사용/삭제용)
    private final Map<UUID, Location> lastWebLocation = new HashMap<>();

    // 거미줄 설치 후 자동 삭제를 위한 태스크 맵 (UUID -> Task)
    private final Map<UUID, BukkitTask> webCleanupTasks = new HashMap<>();

    // 현재 웹 스윙(이동) 중인지 확인
    private final Set<UUID> isZipping = new HashSet<>();

    // 웹 스윙 태스크 관리
    private final Map<UUID, BukkitTask> zipTasks = new HashMap<>();

    public Spiderman(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "030";
    }

    @Override
    public String getName() {
        return "스파이더맨";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c유틸 ● 스파이더맨(마블 코믹스)",
                "§f친절한 이웃 스파이더맨이 됩니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c유틸 ● 스파이더맨(마블 코믹스)");
        p.sendMessage("라운드 시작 시 힘2, 점프강화 2 버프를 얻고 낙하 데미지를 무시합니다.");
        p.sendMessage("스파이더맨은 거미줄에서 느려지지 않으며, 벽을 보고 웅크리면 벽을 탈 수 있습니다.");
        p.sendMessage(" ");
        p.sendMessage("스파이더맨 슈트와 웹 슈터를 획득합니다.");
        p.sendMessage(" ");
        p.sendMessage("웹 슈터 [좌클릭]:");
        p.sendMessage("직선으로 거미줄을 발사합니다. (최대 80블럭)");
        p.sendMessage("- 블럭에 닿으면 거미줄이 설치됩니다. (8초 후 사라짐)");
        p.sendMessage("- 적에게 맞추면 거미줄에 묶어 느려지게 만듭니다.");
        p.sendMessage(" ");
        p.sendMessage("웹 슈터 [우클릭]:");
        p.sendMessage("설치된 거미줄을 향해 빠르게 이동합니다.");
        p.sendMessage("이동 중 다시 우클릭하면 줄을 끊고 이동을 멈춥니다.");
        p.sendMessage(" ");
        p.sendMessage("피자 64개를 획득합니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 15초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 스파이더맨 슈트, 웹 슈터, 피자 64개");
        p.sendMessage("§f장비 제거 : 철 칼, 철 흉갑, 소고기 64개");
    }

    @Override
    public void giveItem(Player p) {
        // 기존 아이템 제거 (철 칼, 철 흉갑, 소고기)
        p.getInventory().remove(Material.IRON_SWORD);
        p.getInventory().remove(Material.IRON_CHESTPLATE);
        p.getInventory().remove(Material.COOKED_BEEF);

        // 스파이더맨 슈트 (가죽 풀세트, 빨간색)
        ItemStack helmet = createLeatherArmor(Material.LEATHER_HELMET, Color.RED);
        ItemStack chest = createLeatherArmor(Material.LEATHER_CHESTPLATE, Color.RED);
        ItemStack leggings = createLeatherArmor(Material.LEATHER_LEGGINGS, Color.RED);
        ItemStack boots = createLeatherArmor(Material.LEATHER_BOOTS, Color.RED);

        p.getInventory().setHelmet(helmet);
        p.getInventory().setChestplate(chest);
        p.getInventory().setLeggings(leggings);
        p.getInventory().setBoots(boots);

        // 피자 (호박 파이)
        ItemStack pizza = new ItemStack(Material.PUMPKIN_PIE, 64);
        ItemMeta pizzaMeta = pizza.getItemMeta();
        pizzaMeta.displayName(Component.text("§6피자"));
        pizza.setItemMeta(pizzaMeta);
        p.getInventory().addItem(pizza);

        // 웹 슈터
        ItemStack webShooter = new ItemStack(Material.COBWEB);
        ItemMeta shooterMeta = webShooter.getItemMeta();
        shooterMeta.displayName(Component.text("§f웹 슈터"));
        webShooter.setItemMeta(shooterMeta);
        p.getInventory().addItem(webShooter);

        // 패시브 효과 적용
        applyPassive(p);
    }

    private ItemStack createLeatherArmor(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(color);
        meta.displayName(Component.text("§c스파이더맨 슈트"));
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    private void applyPassive(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 1, false, false));
        p.addPotionEffect(
                new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, 1, false, false));
    }

    // --- 이벤트 리스너 ---

    // 낙하 데미지 무시 <- 낙뎀 받도록 수정 ㅋㅋ 물 낙법 잘해야함.
    /*
     * @EventHandler
     * public void onEntityDamage(EntityDamageEvent e) {
     * if (e.getEntity() instanceof Player p) {
     * if (AbilityManager.getInstance().hasAbility(p, getCode())) {
     * if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
     * e.setCancelled(true);
     * }
     * }
     * }
     * }
     */

    // 거미줄 감속 무시 로직 & 벽 타기 로직
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        // 1. 거미줄 감속 무시
        if (p.getLocation().getBlock().getType() == Material.COBWEB ||
                p.getEyeLocation().getBlock().getType() == Material.COBWEB) {

            if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ()) {
                Vector direction = e.getTo().toVector().subtract(e.getFrom().toVector());
                direction.setY(0);

                if (direction.lengthSquared() > 0) {
                    double speed = p.isSprinting() ? 0.35 : 0.25;
                    direction.normalize().multiply(speed);

                    Vector currentVel = p.getVelocity();
                    direction.setY(currentVel.getY());
                    p.setVelocity(direction);
                }
            }
        }

        // 2. 벽 타기 (Wall Climb)
        // 웅크리고 있고, 벽에 붙어있다면 위로 상승
        if (p.isSneaking()) {
            Block targetBlock = p.getTargetBlockExact(1); // 1칸 앞 블럭 확인
            // 만약 타겟 블럭이 없으면 바로 앞 블럭 체크 (몸통 기준)
            if (targetBlock == null) {
                Location frontLoc = p.getLocation().add(p.getLocation().getDirection().multiply(0.6));
                if (frontLoc.getBlock().getType().isSolid()) {
                    targetBlock = frontLoc.getBlock();
                }
            }

            if (targetBlock != null && targetBlock.getType().isSolid()) {
                // 위로 올라가는 힘 적용 (너무 빠르지 않게)
                if (p.getLocation().getY() < 320) { // 높이 제한 안전장치
                    Vector up = new Vector(0, 0.35, 0);
                    p.setVelocity(up);
                    // 시각 효과: 먼지 파티클
                    p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation(), 3, 0.2, 0.1, 0.2,
                            targetBlock.getBlockData());
                }
            }
        }
    }

    // 웹 슈터 설치 방지
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (AbilityManager.getInstance().hasAbility(p, getCode())) {
            ItemStack item = e.getItemInHand();
            if (item.getType() == Material.COBWEB &&
                    item.getItemMeta() != null &&
                    "§f웹 슈터".equals(item.getItemMeta().getDisplayName())) {
                e.setCancelled(true);
                p.sendMessage("§c웹 슈터는 설치할 수 없습니다.");
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.COBWEB)
            return;
        if (item.getItemMeta() == null || !"§f웹 슈터".equals(item.getItemMeta().getDisplayName()))
            return;

        e.setCancelled(true);

        Action action = e.getAction();

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            handleLeftClick(p);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            handleRightClick(p);
        }
    }

    private void handleLeftClick(Player p) {
        if (!checkCooldown(p))
            return;

        p.sendMessage("§a거미줄 발사!");
        p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_STEP, 1f, 2f);
        p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 1f);

        setCooldown(p, 15);

        World world = p.getWorld();
        Location startLoc = p.getEyeLocation();
        Vector direction = startLoc.getDirection();

        RayTraceResult result = world.rayTrace(startLoc, direction, 80,
                org.bukkit.FluidCollisionMode.NEVER, true, 1.0,
                entity -> entity != p && entity instanceof LivingEntity);

        Location targetLoc = null;
        boolean hitEntity = false;

        // 시각 효과: 거미줄 그리기용 끝점
        Location endPointForParticle;

        if (result != null) {
            if (result.getHitEntity() != null) {
                // 엔티티 적중
                targetLoc = result.getHitEntity().getLocation();
                endPointForParticle = result.getHitEntity().getLocation().add(0, 1, 0); // 몸통 쪽으로

                LivingEntity victim = (LivingEntity) result.getHitEntity();
                // 거미줄 함정 효과 적용
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3)); // 3초간 구속 IV
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0)); // 3초간 나약함 I
                p.sendMessage("§a" + victim.getName() + "을(를) 거미줄로 묶었습니다!");
                victim.sendMessage("§c스파이더맨의 거미줄에 묶였습니다!");

                // 적중 시 위치에 거미줄 설치 (선택 사항 - 기획엔 없지만 묶인 느낌을 위해 발 밑에 설치 추천)
                // 여기서는 기획대로 "해당 위치에 설치"를 따르되, 엔티티 위치에 설치
                hitEntity = true;

            } else if (result.getHitBlock() != null) {
                // 블럭 적중
                if (result.getHitBlockFace() != null) {
                    targetLoc = result.getHitBlock().getRelative(result.getHitBlockFace()).getLocation();
                } else {
                    targetLoc = result.getHitBlock().getLocation();
                }
                endPointForParticle = result.getHitPosition().toLocation(world);
            } else {
                targetLoc = startLoc.clone().add(direction.multiply(80));
                endPointForParticle = targetLoc;
            }
        } else {
            targetLoc = startLoc.clone().add(direction.multiply(80));
            endPointForParticle = targetLoc;
        }

        // 파티클 라인 그리기
        drawWebLine(startLoc.add(0, -0.2, 0), endPointForParticle); // 눈 위치보다 살짝 아래에서 시작

        placeWeb(p, targetLoc);
    }

    private void drawWebLine(Location start, Location end) {
        double distance = start.distance(end);

        // 거리가 0이면 그릴 필요 없음
        if (distance <= 0)
            return;

        Vector vec = end.toVector().subtract(start.toVector()).normalize();

        for (double i = 0; i < distance; i += 0.5) {
            Location point = start.clone().add(vec.clone().multiply(i));
            // Particle.DUST with White color
            point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0,
                    new Particle.DustOptions(Color.WHITE, 0.8f));
        }
    }

    private void placeWeb(Player p, Location loc) {
        Block block = loc.getBlock();

        if (!block.getType().isSolid() || block.getType() == Material.COBWEB) {
            removeWeb(p); // 기존 거미줄 제거

            block.setType(Material.COBWEB);
            lastWebLocation.put(p.getUniqueId(), loc);

            p.playSound(p.getLocation(), Sound.BLOCK_WOOL_PLACE, 1f, 1f);

            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (block.getType() == Material.COBWEB) {
                        block.setType(Material.AIR);
                        block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 10, 0.3,
                                0.3, 0.3, Material.COBWEB.createBlockData());
                    }
                    webCleanupTasks.remove(p.getUniqueId());
                    lastWebLocation.remove(p.getUniqueId());
                }
            }.runTaskLater(plugin, 8L * 20L); // 8초

            registerTask(p, task);
            webCleanupTasks.put(p.getUniqueId(), task);

        } else {
            p.sendMessage("§c거미줄을 설치할 공간이 부족합니다.");
        }
    }

    private void removeWeb(Player p) {
        if (webCleanupTasks.containsKey(p.getUniqueId())) {
            webCleanupTasks.get(p.getUniqueId()).cancel();
            webCleanupTasks.remove(p.getUniqueId());
        }

        if (lastWebLocation.containsKey(p.getUniqueId())) {
            Location loc = lastWebLocation.get(p.getUniqueId());
            if (loc.getBlock().getType() == Material.COBWEB) {
                loc.getBlock().setType(Material.AIR);
            }
            lastWebLocation.remove(p.getUniqueId());
        }
    }

    private void handleRightClick(Player p) {
        UUID uid = p.getUniqueId();

        if (isZipping.contains(uid)) {
            stopZip(p);
            return;
        }

        if (lastWebLocation.containsKey(uid)) {
            Location target = lastWebLocation.get(uid);
            if (target.getBlock().getType() == Material.COBWEB) {
                startZip(p, target);
            } else {
                p.sendMessage("§c거미줄이 사라졌습니다.");
                lastWebLocation.remove(uid);
            }
        } else {
            p.sendMessage("§c설치된 거미줄이 없습니다. 먼저 발사하세요.");
        }
    }

    private void startZip(Player p, Location target) {
        UUID uid = p.getUniqueId();
        isZipping.add(uid);

        p.playSound(p.getLocation(), Sound.ENTITY_HORSE_JUMP, 1f, 1.5f);
        p.sendMessage("§e슈욱!");

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || !isZipping.contains(uid)) {
                    this.cancel();
                    return;
                }

                Location current = p.getLocation();
                Vector dir = target.toVector().subtract(current.toVector());
                double distance = dir.length();

                if (distance < 2.0) {
                    stopZip(p);
                    this.cancel();
                    return;
                }

                // 시각 효과: 이동 중 파티클
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 2, 0.2, 0.2, 0.2, 0.05);

                dir.normalize().multiply(1.5);
                if (current.getY() < target.getY()) {
                    dir.setY(dir.getY() + 0.2);
                }

                p.setVelocity(dir);
            }
        }.runTaskTimer(plugin, 0L, 2L);

        zipTasks.put(uid, task);
        registerTask(p, task);
    }

    private void stopZip(Player p) {
        UUID uid = p.getUniqueId();
        if (zipTasks.containsKey(uid)) {
            zipTasks.get(uid).cancel();
            zipTasks.remove(uid);
        }
        isZipping.remove(uid);

        removeWeb(p);

        p.setVelocity(new Vector(0, 0, 0));
        p.sendMessage("§7웹스윙 중단!");
    }

    @Override
    public void cleanup(Player p) {
        removeWeb(p);
        stopZip(p);
    }
}
