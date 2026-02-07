package me.user.moc.ability.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;

public class Mothership extends Ability {

    // 현재 모선의 중앙 위치 (이동 로직용)
    private final java.util.Map<UUID, Location> shipLocations = new java.util.HashMap<>();

    // 쿨타임은 '모선이 사라진 후'부터 적용되므로, 실제 쿨타임 로직을 별도로 관리해야 할 수도 있으나,
    // Ability 부모 클래스의 setCooldown을 '모선 사라질 때' 호출하면 됨.

    public Mothership(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "032";
    }

    @Override
    public String getName() {
        return "모선";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c전투 ● 모선(스타크래프트2)",
                "§f프로토스의 모선을 소환합니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 모선 호출기 (신호기)
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b모선 호출기");
            meta.setLore(Arrays.asList(
                    "§7우클릭 시 모선을 소환합니다.",
                    "§f지속시간: 12초",
                    "§c쿨타임은 모선 소멸 후 적용됩니다."));
            item.setItemMeta(meta);
        }
        p.getInventory().addItem(item);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 모선(스타크래프트2)");
        p.sendMessage("§f모선 호출기를 우클릭하면 5초 뒤 전장 중앙 상공에 모선이 소환됩니다.");
        p.sendMessage("§f소환된 모선은 가장 가까운 적을 추적합니다.");
        p.sendMessage("§f모선 아래 3x3 범위에 있는 적에게 §c초당 10(5하트)§f의 피해를 입힙니다.");
        p.sendMessage("§f호출기를 가진 플레이어는 공격받지 않습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 20초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 모선 호출기");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        // 아이템 체크
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.BEACON)
            return;

        // 액션 체크 (우클릭)
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        e.setCancelled(true); // 설치 방지

        // [추가] 리액션 결과도 DENY로 설정하여 확실히 방지
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);

        // 쿨타임 체크 (모선이 이미 소환 중이면 사용 불가 + 시스템 쿨타임 체크)
        if (checkCooldown(p)) {
            // [Fix] 이미 소환 중인지 체크 (태스크가 존재하면 진행 중임)
            if (activeTasks.containsKey(p.getUniqueId())) {
                p.sendMessage("§c[!] 이미 모선이 활동 중이거나 소환 준비 중입니다.");
                return;
            }

            p.sendMessage("§e[!] §b5초 뒤 전장 가운데 모선이 나타납니다.");
            Bukkit.broadcastMessage("§c모선 : 심판의 시간이 다가왔다.");

            // 5초 카운트다운 (registerTask를 먼저 해서 중복 방지)
            java.util.UUID uuid = p.getUniqueId();
            activeTasks.computeIfAbsent(uuid, k -> new java.util.ArrayList<>());

            BukkitTask countdown = new BukkitRunnable() {
                int count = 5;

                @Override
                public void run() {
                    // 플레이어 유효성 체크
                    if (!p.isOnline() || !AbilityManager.getInstance().hasAbility(p, getCode())) {
                        this.cancel();
                        activeTasks.remove(uuid);
                        return;
                    }

                    if (count > 0) {
                        p.sendMessage("§e모선 소환까지... " + count + "초");
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 2f);
                        count--;
                    } else {
                        // 소환 시작
                        spawnMothership(p);
                        this.cancel();
                    }
                }
            }.runTaskTimer(plugin, 0L, 20L);

            registerTask(p, countdown);
        }
    }

    // [추가] 신호기 설치를 원천 봉쇄합니다.
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        if (e.getBlockPlaced().getType() == Material.BEACON) {
            e.setCancelled(true);
        }
    }

    private void spawnMothership(Player p) {
        // 전장 중앙 위치 가져오기 (ArenaManager는 static 접근이 안되므로 GameManger -> ArenaManager 식이나,
        // 여기서는 편의상 GameManger가 들고있는 center 로직을 추론하거나, 플레이어 위치 기반이 아닌 맵 중앙 필요.
        // ArenaManager 인스턴스를 가져올 방법이 필요함.
        // 보통 MocPlugin -> getInstance -> getArenaManager 하면 되는데 구조상...
        // ArenaManager에 public static Location getGameCenter() 같은게 있으면 좋겠지만 인스턴스 변수임.
        // MocPlugin.getInstance().getGameManager()... (GameManager가 없으면?)
        // 일단 편의상 0, 100, 0 이나 플레이어 근처가 아닌 '전장 중앙'임.
        // 임시로 플레이어 위치의 Y+30 혹은, 월드 스폰 포인트 활용.
        // 여기서는 안전하게 플레이어의 월드의 0,0 을 기준으로 잡거나,
        // 만약 아레나 매니저가 전역적으로 접근 힘들다면,
        // *** 플레이어 위치 위로 소환하는게 아니라 '전장 중앙'임 ***
        // -> GameManger나 ArenaManager를 통해 가져오는게 맞음.
        // 일단 MocPlugin 메인을 통해 접근 시도.

        // 실제로는 ArenaManager의 gameCenter를 가져와야 함.
        // MocPlugin에 getArenaManager()가 있다고 가정/확인됨? NO.
        // ArenaManager는 로컬 변수로 쓰이는 경우가 많음.
        // 하지만! ArenaManager.prepareArena() 호출 시 gameCenter가 설정됨.
        // -> p.getWorld().getWorldBorder().getCenter() 를 쓰면 정확함! (ArenaManager가 월드보더
        // 센터를 설정하니까)

        // [Fix] 전장 중앙 상공 64칸
        // ArenaManager의 중앙 = WorldBorder의 중앙
        // ArenaManager의 중앙 = WorldBorder의 중앙
        Location targetCenter = p.getWorld().getWorldBorder().getCenter();
        World w = targetCenter.getWorld();

        // [Fix] 전장 중앙 에메랄드 블록 찾기 (위에서 아래로 스캔)
        int emeraldY = -999;
        for (int y = w.getMaxHeight(); y > w.getMinHeight(); y--) {
            if (w.getBlockAt(targetCenter.getBlockX(), y, targetCenter.getBlockZ())
                    .getType() == Material.EMERALD_BLOCK) {
                emeraldY = y;
                break;
            }
        }

        // 에메랄드 못 찾으면 차선책 (지표면)
        if (emeraldY != -999) {
            targetCenter.setY(emeraldY + 32);
        } else {
            double highestY = w.getHighestBlockYAt(targetCenter);
            if (highestY < 64)
                highestY = 64;
            targetCenter.setY(highestY + 32);
        }

        // Y좌표 안전장치 (310 초과 시 고정)
        if (targetCenter.getY() > 310)
            targetCenter.setY(310);

        // 파티클 연출 (은은하게)
        // ... (생략 가능, 요청사항엔 '은은하게 출력' 후 생성)

        // 모선 구조물 생성 (BlockDisplay)
        List<BlockDisplay> shipParts = new ArrayList<>();
        World world = targetCenter.getWorld();

        /*
         * 구조:
         * [다이아]
         * [금][유리][금]
         * [다이아]
         * (매우 간소화된 형태, 좀 더 멋지게 십자 형태 + 대각선)
         * 
         * Layer 0 (Core): Blue Stained Glass
         * Layer 0 (Ring): Gold Blocks around
         * Layer 1 (Details): Diamond Blocks on edges
         */

        // 중앙: 푸른 유리
        shipParts.add(spawnBlockDisplay(world, targetCenter, Material.BLUE_STAINED_GLASS, 0, 0, 0));

        // 십자 금 블록
        shipParts.add(spawnBlockDisplay(world, targetCenter, Material.GOLD_BLOCK, 1, 0, 0));
        shipParts.add(spawnBlockDisplay(world, targetCenter, Material.GOLD_BLOCK, -1, 0, 0));
        shipParts.add(spawnBlockDisplay(world, targetCenter, Material.GOLD_BLOCK, 0, 0, 1));
        shipParts.add(spawnBlockDisplay(world, targetCenter, Material.GOLD_BLOCK, 0, 0, -1));

        // 대각선 다이아 블록
        shipParts.add(spawnBlockDisplay(world, targetCenter, Material.DIAMOND_BLOCK, 1, 0, 1));
        shipParts.add(spawnBlockDisplay(world, targetCenter, Material.DIAMOND_BLOCK, -1, 0, 1));
        shipParts.add(spawnBlockDisplay(world, targetCenter, Material.DIAMOND_BLOCK, 1, 0, -1));
        shipParts.add(spawnBlockDisplay(world, targetCenter, Material.DIAMOND_BLOCK, -1, 0, -1));

        // [수정] registerSummon을 사용하여 관리
        for (BlockDisplay bd : shipParts) {
            registerSummon(p, bd);
        }

        shipLocations.put(p.getUniqueId(), targetCenter.clone());

        p.sendMessage("§b모선이 소환되었습니다!");

        // AI 시작
        startMothershipAI(p, 12); // 12초 지속
    }

    private BlockDisplay spawnBlockDisplay(World world, Location center, Material mat, int dx, int dy, int dz) {
        Location loc = center.clone().add(dx, dy, dz);
        BlockDisplay bd = (BlockDisplay) world.spawnEntity(loc, EntityType.BLOCK_DISPLAY);
        bd.setBlock(mat.createBlockData());
        bd.setBillboard(Billboard.FIXED); // 회전 안함

        // 크기 및 변형 설정 (3배 확대)
        Transformation transform = bd.getTransformation();
        transform.getScale().set(3.0f); // 3배 크기
        bd.setTransformation(transform);

        // 타인에게만 보이게 하거나 그런 설정 없음 (모두에게 보임)
        return bd;
    }

    private void startMothershipAI(Player p, int durationSeconds) {
        UUID pid = p.getUniqueId();

        // 관리 리스트에 추가 (cleanup 용)
        if (!activeTasks.containsKey(pid))
            activeTasks.put(pid, new ArrayList<>());

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = durationSeconds * 20;
            Location currentShipLoc = shipLocations.get(pid);

            @Override
            public void run() {
                // 종료 조건
                if (!p.isOnline() || currentShipLoc == null || ticks >= maxTicks) {
                    removeMothership(p);
                    // 쿨타임 시작 (모선 사라진 후)
                    setCooldown(p, 20);
                    this.cancel();
                    return;
                }

                // 1. 타겟 탐색
                LivingEntity target = findNearestTarget(p, currentShipLoc);

                // 2. 이동 (Target이 있으면 그쪽으로, 없으면 제자리 혹은 랜덤 배회?)
                if (target != null) {
                    Location targetLoc = target.getLocation().clone();
                    targetLoc.setY(targetLoc.getY() + 15); // 타겟 위 15칸 상공 유지 (너무 높으면 안보임, 15칸 적당)

                    // 이동 벡터 계산
                    Vector dir = targetLoc.toVector().subtract(currentShipLoc.toVector());
                    double dist = dir.length();

                    if (dist > 0.5) {
                        dir.normalize().multiply(0.35); // 틱당 0.35칸 (0.5 -> 0.35 감속, 약 30% 너프)
                        currentShipLoc.add(dir);
                    }
                }

                // 3. 비주얼 업데이트 (BlockDisplay 이동)
                updateShipVisuals(pid, currentShipLoc);

                // 4. 공격 (빔)
                // 1초(20틱)마다 데미지
                if (ticks % 20 == 0) {
                    fireBeam(p, currentShipLoc);
                }

                // 빔 효과 (파티클) - 매 틱 혹은 2틱마다
                if (ticks % 2 == 0) {
                    // 중앙에서 바닥으로 빔
                    spawnBeamParticles(currentShipLoc);
                }

                ticks++;
            }
        };

        activeTasks.get(pid).add(task.runTaskTimer(plugin, 0L, 1L));
    }

    @Override
    public void reset() {
        super.reset(); // activeEntities, activeTasks 정리됨
        shipLocations.clear();
    }

    private LivingEntity findNearestTarget(Player owner, Location shipLoc) {
        LivingEntity nearest = null;
        double minDst = Double.MAX_VALUE;

        // 월드의 모든 엔티티 스캔 (범위 제한 100칸)
        for (Entity e : shipLoc.getWorld().getLivingEntities()) {
            if (!(e instanceof LivingEntity le))
                continue;
            /*
             * if (e.equals(owner)) // 주인도 호출기 없으면 공격 맞음.
             * continue; // 주인 제외
             */
            // 호출기(신호기) 들고 있는 사람 제외
            if (le instanceof Player player) {
                if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR)
                    continue;
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand != null && hand.getType() == Material.BEACON)
                    continue;
                // 팀원 체크? (MOC은 개인전 위주지만 팀 기능 있으면 추가)
            }
            if (le instanceof org.bukkit.entity.ArmorStand)
                continue; // 아머스탠드 제외

            double dst = e.getLocation().distanceSquared(shipLoc);
            if (dst < 100 * 100 && dst < minDst) { // 100칸 이내
                minDst = dst;
                nearest = le;
            }
        }
        return nearest;
    }

    private void updateShipVisuals(UUID pid, Location center) {
        java.util.List<org.bukkit.entity.Entity> parts = activeEntities.get(pid);
        if (parts == null)
            return;

        // 각 파츠별 상대 좌표를 유지하며 이동
        int index = 0;
        for (org.bukkit.entity.Entity e : parts) {
            if (!(e instanceof BlockDisplay))
                continue;
            BlockDisplay bd = (BlockDisplay) e;

            Location target = center.clone();
            // spawnBlockDisplay 순서 재구성:
            // 0: Center, 1-4: Sides, 5-8: Corners
            // Note: The scale is 3.0f, so the offsets should be multiplied by 3.
            switch (index) {
                case 0:
                    target.add(0, 0, 0);
                    break; // Center
                case 1:
                    target.add(3, 0, 0);
                    break; // Gold
                case 2:
                    target.add(-3, 0, 0);
                    break; // Gold
                case 3:
                    target.add(0, 0, 3);
                    break; // Gold
                case 4:
                    target.add(0, 0, -3);
                    break; // Gold
                case 5:
                    target.add(3, 0, 3);
                    break; // Diamond
                case 6:
                    target.add(-3, 0, 3);
                    break; // Diamond
                case 7:
                    target.add(3, 0, -3);
                    break; // Diamond
                case 8:
                    target.add(-3, 0, -3);
                    break; // Diamond
            }
            bd.teleport(target);
            index++;
        }
    }

    private void fireBeam(Player owner, Location shipLoc) {
        // 소리
        shipLoc.getWorld().playSound(shipLoc, Sound.BLOCK_BEACON_AMBIENT, 5.0f, 1.0f);
        shipLoc.getWorld().playSound(shipLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 2.0f);

        // 히트박스: 모선 바로 아래 지상
        // 단순히 높이 무시하고 X, Z 좌표 차이가 1.5 이내인 적
        for (Entity e : shipLoc.getWorld().getLivingEntities()) {
            if (e.equals(owner))
                continue;
            if (e instanceof Player p && p.getGameMode() == org.bukkit.GameMode.SPECTATOR)
                continue;
            // 호출기 든 사람 제외
            if (e instanceof Player p) {
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand != null && hand.getType() == Material.BEACON)
                    continue;
            }

            Location targetLoc = e.getLocation();
            if (Math.abs(targetLoc.getX() - shipLoc.getX()) < 1.5 &&
                    Math.abs(targetLoc.getZ() - shipLoc.getZ()) < 1.5) {

                // 높이는? 모선보다 아래에 있어야 함.
                if (targetLoc.getY() < shipLoc.getY()) {
                    ((LivingEntity) e).damage(10.0, owner); // 10 데미지

                    // [추가] 넉백 적용 (연속 피격 방지용)
                    // 빔 중심(shipLoc)에서 피해자(targetLoc) 방향으로 밀어냄
                    Location beamCenter = shipLoc.clone();
                    beamCenter.setY(targetLoc.getY()); // 높이 보정 (수평 밀치기)

                    Vector knockback = targetLoc.toVector().subtract(beamCenter.toVector());

                    // 만약 정확히 중앙이라서 벡터가 0이면 임의 방향(X축)으로 설정
                    if (knockback.lengthSquared() < 0.0001) {
                        knockback = new Vector(1, 0, 0);
                    }

                    knockback.normalize().multiply(1.2).setY(0.3); // 수평 1.2강도, 위로 0.3 (살짝 뜸)
                    e.setVelocity(knockback);
                }
            }
        }
    }

    private void spawnBeamParticles(Location shipLoc) {
        // [변경] 모선에서 바닥까지 빔 (신호기 빔 느낌)
        World w = shipLoc.getWorld();
        Location start = shipLoc.clone();

        // 빔 길이 (최대 100칸으로 늘림 - 높이가 64+라 50은 짧을 수 있음)
        for (int i = 0; i < 100; i++) {
            start.subtract(0, 1, 0);
            if (start.getBlock().getType().isSolid()) {
                // 바닥 닿음 - 충격파 효과
                w.spawnParticle(org.bukkit.Particle.EXPLOSION, start, 3, 1, 0.5, 1, 0.1);
                break;
            }

            // 빔 줄기 (신호기 느낌: END_ROD + DUST)
            // 밀도를 높여서 하나의 기둥처럼 보이게
            w.spawnParticle(org.bukkit.Particle.END_ROD, start, 1, 0, 0, 0, 0);
            w.spawnParticle(org.bukkit.Particle.DUST, start, 5, 0.2, 0.5, 0.2,
                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.AQUA, 1.5f));
        }
    }

    private void removeMothership(Player p) {
        UUID pid = p.getUniqueId();

        // Display 제거
        if (activeEntities.containsKey(pid)) {
            java.util.List<org.bukkit.entity.Entity> list = activeEntities.remove(pid);
            if (list != null) {
                for (org.bukkit.entity.Entity e : list) {
                    if (e != null && e.isValid())
                        e.remove();
                }
            }
        }
        // Task 취소 및 리스트 제거
        if (activeTasks.containsKey(pid)) {
            List<BukkitTask> tasks = activeTasks.remove(pid);
            if (tasks != null) {
                tasks.forEach(t -> {
                    if (!t.isCancelled())
                        t.cancel();
                });
            }
        }
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        removeMothership(p);
    }
}
