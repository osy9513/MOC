package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class Inuyasha extends Ability {

    public Inuyasha(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "026";
    }

    @Override
    public String getName() {
        return "이누야샤";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§e전투 ● 이누야샤(이누야샤)",
                "§f철쇄아를 우클릭해 §e바람의 상처§f를 전방으로 날립니다.",
                "§f다섯 갈래의 검기가 지면을 따라 적을 공격합니다.");
    }

    @Override
    public void giveItem(Player p) {
        // [추가] 기존 철 검 제거
        p.getInventory().remove(Material.IRON_SWORD);

        ItemStack item = new ItemStack(Material.IRON_SWORD);
        var meta = item.getItemMeta();
        meta.setDisplayName("§f철쇄아");
        meta.setLore(List.of("§7우클릭 시 바람의 상처를 발동합니다."));
        meta.setCustomModelData(1); // 리소스팩: inuyasha
        item.setItemMeta(meta);
        p.getInventory().addItem(item);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● 이누야샤(이누야샤)");
        p.sendMessage("§f철쇄아 우클릭 시 전방 5방향으로 검기를 발사합니다.");
        p.sendMessage("§f검기는 3초간 유지되며, 무적 시간을 무시하고 데미지를 줍니다.");
        p.sendMessage("§f벽을 뚫을 수 없고, 하나의 검기에 최대 20의 데미지를 줍니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 11초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 철쇄아(철 검)");
        p.sendMessage("§f장비 제거 : 철 검");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!hasAbility(p))
            return;

        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack hand = e.getItem();
            if (hand != null && hand.getType() == Material.IRON_SWORD) {
                if (checkCooldown(p)) {
                    launchWindScar(p);
                    setCooldown(p, 11);
                }
            }
        }
    }

    private void launchWindScar(Player p) {
        // [수정] 전체 메시지로 변경
        plugin.getServer().broadcastMessage("§c이누야샤 : 바람의 상처!!");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.5f);

        Location startLoc = p.getEyeLocation().subtract(0, 0.2, 0);
        Vector direction = p.getLocation().getDirection().normalize();

        // 5갈래 각도 조정 (부채꼴)
        double[] angles = { -30, -15, 0, 15, 30 };

        for (double angle : angles) {
            Vector v = rotateVector(direction.clone(), angle);
            createWindScarField(p, startLoc.clone(), v);
        }
    }

    private void createWindScarField(Player p, Location start, Vector dir) {
        // 1. 경로 미리 계산 (최대 17블록)
        List<Location> path = new java.util.ArrayList<>();
        // [추가] 비주얼용 아머스탠드 관리 리스트
        List<ArmorStand> visuals = new java.util.ArrayList<>();

        Location current = start.clone();

        for (int i = 0; i < 17; i++) { // 17번 반복 (약 17블록)
            Location next = current.clone().add(dir.clone().multiply(1.0)); // 1블록 전진 시도

            if (!next.getBlock().isPassable()) {
                // 막힘 (블록 내부) // 딜이 넘 강해지는 버그가 있어서 주석 처리
                /*
                 * if (next.getBlock().getType() == Material.BEDROCK) {
                 * // [수정] 기반암이면 뚫지 않고 '벽면을 타듯' 위로 이동 시도
                 * Location up = current.clone().add(0, 1, 0);
                 * if (up.getBlock().isPassable()) {
                 * current = up;
                 * path.add(current.clone());
                 * spawnVisual(current, visuals);
                 * continue;
                 * }
                 * }
                 */
                // 기반암이 아니거나, 위쪽도 막혀있으면 경로 중단
                break;
            }
            // 뚫려있으면 전진
            current = next;
            path.add(current.clone());
            /* spawnVisual(current, visuals); */
        }

        // 2. 장판 유지 (3초간 파티클 & 대미지)
        new BukkitRunnable() {
            int tick = 0;
            final int maxTick = 20; // 2초

            @Override
            public void run() {
                if (tick >= maxTick) {
                    // [추가] 종료 시 비주얼 제거
                    for (ArmorStand as : visuals) {
                        as.remove();
                    }
                    this.cancel();
                    return;
                }

                // 저장된 경로에 파티클 소환 및 대미지 판정
                if (tick % 6 == 0) {
                    // 대미지 판정
                    for (Location loc : path) {
                        for (Entity e : loc.getWorld().getNearbyEntities(loc, 1.0, 1.0, 1.0)) {
                            if (e instanceof LivingEntity target && !e.equals(p)) {
                                if (target instanceof Player
                                        && ((Player) target).getGameMode() == org.bukkit.GameMode.SPECTATOR)
                                    continue;
                                target.setNoDamageTicks(0);
                                target.damage(1, p); // 20회 / 20대미지? -> 3초면 20번(6틱간격). 20 * 1 = 20
                                target.setNoDamageTicks(0);
                            }
                        }
                    }
                }

                // 파티클은 매 틱 그려줌 (장판 느낌)
                Particle.DustOptions yellowDust = new Particle.DustOptions(Color.YELLOW, 2.0f);
                for (Location loc : path) {
                    loc.getWorld().spawnParticle(Particle.DUST, loc, 1, 0.2, 0.2, 0.2, yellowDust);
                }

                if (tick % 6 == 0 && !path.isEmpty()) {
                    // 소리는 가끔 재생 (시작점에)
                    path.get(0).getWorld().playSound(path.get(0), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.3f, 1.5f);
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // 안 예뻐서 뺌;
    private void spawnVisual(Location loc, List<ArmorStand> list) {
        // [추가] 네더 석영을 머리에 쓴 아머스탠드 소환
        ArmorStand as = loc.getWorld().spawn(loc.clone().subtract(0, 1.0, 0), ArmorStand.class, entity -> {
            entity.setVisible(false);
            entity.setGravity(false);
            entity.setMarker(true);
            entity.setSmall(true); // 작게 해야 검기 느낌
            // 요청: 네더 석영 (QUARTZ)
            entity.getEquipment().setHelmet(new ItemStack(Material.QUARTZ));

            // 머리 각도 조절 (45도, 좀 더 날카롭게)
            entity.setHeadPose(new org.bukkit.util.EulerAngle(Math.toRadians(45), 0, 0));
        });
        list.add(as);
    }

    private Vector rotateVector(Vector v, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = v.getX() * cos - v.getZ() * sin;
        double z = v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }

    private boolean hasAbility(Player p) {
        // [추가] 능력이 봉인된 상태 (침묵)인지 체크
        if (isSilenced(p))
            return false;
        return me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode());
    }
}
