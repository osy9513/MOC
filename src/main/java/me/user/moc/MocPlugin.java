package me.user.moc;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

public final class MocPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, String> playerAbilities = new HashMap<>();
    private final Map<UUID, Integer> rerollCounts = new HashMap<>();
    private final Set<UUID> readyPlayers = new HashSet<>();
    private final Map<UUID, Integer> scores = new HashMap<>();
    private final Map<UUID, Horse> activeBikes = new HashMap<>();

    private BukkitTask selectionTask;
    private BukkitTask borderTask;
    private BukkitTask damageTask;
    private boolean isInvincible = false;
    private boolean isGameStarted = false;
    private Location gameCenter;
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("moc").setExecutor(this);
        getLogger().info("MOC 시즌 2 시스템 로딩 완료!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;

        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "start":
                    if (player.isOp()) startNewRound(player.getLocation());
                    break;
                case "stop":
                    if (player.isOp()) stopGame();
                    break;
                case "yes":
                    acceptAbility(player);
                    break;
                case "re":
                    rerollAbility(player);
                    break;
            }
            return true;
        }
        return false;
    }




    private void startNewRound(Location center) {
        this.gameCenter = center;
        this.isGameStarted = false;
        playerAbilities.clear();
        rerollCounts.clear();
        readyPlayers.clear();

        // 경기장 바닥 생성
        generateCircleFloor(center, 60, center.getBlockY() - 1, center);

        List<String> abilities = new ArrayList<>(Arrays.asList("우에키", "올라프", "미다스", "매그너스"));
        Collections.shuffle(abilities);

        int i = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String initialAbility = abilities.get(i % abilities.size());
            playerAbilities.put(p.getUniqueId(), initialAbility);
            rerollCounts.put(p.getUniqueId(), 2);
            showAbilityInfo(p, initialAbility);
            i++;
        }

        // 45초 타이머 시작
        if (selectionTask != null) selectionTask.cancel();
        selectionTask = new BukkitRunnable() {
            int timer = 45;
            @Override
            public void run() {
                if (readyPlayers.size() == Bukkit.getOnlinePlayers().size() || timer <= 0) {
                    this.cancel();
                    startGameLogic();
                    return;
                }
                if (timer <= 5) {
                    Bukkit.broadcastMessage("§e[MOC] §f능력 자동 수락까지 §c" + timer + "초 §f남았습니다.");
                }
                timer--;
            }
        }.runTaskTimer(this, 0, 20L);
    }

    private void showAbilityInfo(Player p, String ability) {
        p.sendMessage("§f ");
        p.sendMessage("§e=== §l능력 정보 §e===");
        switch (ability) {
            case "우에키":
                p.sendMessage("§b유틸 ● 우에키(우에키의 법칙/배틀짱)");
                p.sendMessage("§f묘목이 지급된다. 묘목을 우 클릭 시,");
                p.sendMessage("§f주변 나무와 쓰레기들을 변환한다.");
                p.sendMessage("§a- 쓰레기를 나무로 바꾸는 힘!");
                break;
            case "올라프":
                p.sendMessage("§c공격 ● 올라프(리그 오브 레전드)");
                p.sendMessage("§f철 도끼가 지급된다. 우 클릭 시 도끼를 던져");
                p.sendMessage("§f적에게 강력한 대미지를 입힌다.");
                break;
            case "미다스":
                p.sendMessage("§6특수 ● 미다스(그리스 신화)");
                p.sendMessage("§f금괴가 지급된다. 좌 클릭한 블록을");
                p.sendMessage("§f순금 블록으로 바꾸어버린다.");
                break;
            case "매그너스":
                p.sendMessage("§8이동 ● 매그너스(이터널 리턴)");
                p.sendMessage("§f염료가 지급된다. 사용 시 오토바이를 소환해");
                p.sendMessage("§f폭발적인 속도로 돌진 후 자폭한다.");
                break;
        }
        p.sendMessage("§f ");
        p.sendMessage("§e능력 수락 : §a/moc yes");
        p.sendMessage("§e리롤(2회) : §c/moc re §7(소고기 15개 소모)");
        p.sendMessage("§e==================");
    }

    private void acceptAbility(Player p) {
        if (readyPlayers.contains(p.getUniqueId())) return;
        readyPlayers.add(p.getUniqueId());
        p.sendMessage("§a[MOC] §f능력을 수락하셨습니다. 준비 완료!");
    }

    private void rerollAbility(Player p) {
        int left = rerollCounts.getOrDefault(p.getUniqueId(), 0);
        if (left <= 0) {
            p.sendMessage("§c[MOC] 리롤 횟수를 모두 사용했습니다.");
            return;
        }

        // 소고기 15개 체크 및 소모
        if (!p.getInventory().contains(Material.COOKED_BEEF, 15)) {
            p.sendMessage("§c[MOC] 소고기가 부족하여 리롤할 수 없습니다.");
            return;
        }

        p.getInventory().removeItem(new ItemStack(Material.COOKED_BEEF, 15));
        List<String> pool = new ArrayList<>(Arrays.asList("우에키", "올라프", "미다스", "매그너스"));
        String newAbility = pool.get(new Random().nextInt(pool.size()));

        playerAbilities.put(p.getUniqueId(), newAbility);
        rerollCounts.put(p.getUniqueId(), left - 1);
        p.sendMessage("§e[MOC] §f능력이 교체되었습니다! 남은 리롤: §c" + (left - 1));
        showAbilityInfo(p, newAbility);
    }

    private void startGameLogic() {
        isGameStarted = true;
        isInvincible = true;
        Bukkit.broadcastMessage("§6§l[MOC] 전투가 곧 시작됩니다! 무적 시간(3초)");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getInventory().clear();

            // 1. 칼
            p.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
            // 2. 고기
            p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 64));
            // 3. 물양동이
            p.getInventory().addItem(new ItemStack(Material.WATER_BUCKET));
            // 4. 유리
            p.getInventory().addItem(new ItemStack(Material.GLASS, 10));
            // 5. 갑옷
            p.getInventory().addItem(new ItemStack(Material.IRON_CHESTPLATE));

            // [수정된 부분] 1레벨 체력 재생 포션 1개 지급
            ItemStack regenPotion = new ItemStack(Material.POTION);
            org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) regenPotion.getItemMeta();
            if (meta != null) {
                // REGEN이 아니라 REGENERATION을 사용해야 합니다.
                try {
                    meta.setBasePotionData(new org.bukkit.potion.PotionData(org.bukkit.potion.PotionType.REGENERATION));
                } catch (NoSuchFieldError e) {
                    // 아주 최신 버전(1.20.5+)에서 위 코드가 안될 경우를 대비한 대체 코드
                    meta.setBasePotionData(new org.bukkit.potion.PotionData(org.bukkit.potion.PotionType.valueOf("REGEN")));
                }
                regenPotion.setItemMeta(meta);
            }
            p.getInventory().addItem(regenPotion);

            // 6. 고유 능력 아이템 (지급 순서 마지막)
            giveAbilityItem(p, playerAbilities.get(p.getUniqueId()));

            // 랜덤 텔레포트
            double rx = gameCenter.getX() + (new Random().nextDouble() * 80 - 40);
            double rz = gameCenter.getZ() + (new Random().nextDouble() * 80 - 40);
            p.teleport(new Location(gameCenter.getWorld(), rx, gameCenter.getY() + 1, rz));

            // 체력 3줄 설정 (60.0)
            AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(60.0);
                p.setHealth(60.0);
            }
        }

        // 3초 후 무적 해제
        new BukkitRunnable() {
            @Override
            public void run() {
                isInvincible = false;
                Bukkit.broadcastMessage("§c§l[MOC] 무적 시간이 종료되었습니다! 전투 시작!");
            }
        }.runTaskLater(this, 60L);

        // 5분 뒤 자기장 시작
        if (borderTask != null) borderTask.cancel();
        borderTask = new BukkitRunnable() {
            @Override
            public void run() {
                startCustomBorderShrink(gameCenter.getWorld());
            }
        }.runTaskLater(this, 20L * 60 * 5);

        startBorderDamageTask(gameCenter.getWorld());
    }

    private void giveAbilityItem(Player p, String ability) {
        switch (ability) {
            case "우에키": p.getInventory().addItem(new ItemStack(Material.OAK_SAPLING, 16)); break;
            case "올라프": p.getInventory().addItem(new ItemStack(Material.IRON_AXE, 9)); break;
            case "미다스": p.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 64)); break;
            case "매그너스": p.getInventory().addItem(new ItemStack(Material.GRAY_DYE)); break;
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!isGameStarted) return;
        Player victim = e.getEntity();
        Player killer = victim.getKiller();

        if (killer != null) {
            int currentScore = scores.getOrDefault(killer.getUniqueId(), 0);
            scores.put(killer.getUniqueId(), currentScore + 1);
            killer.sendMessage("§e[MOC] §f적을 처치하여 점수를 획득했습니다!");
        }

        checkWinner();
    }

    private void checkWinner() {
        List<Player> alive = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL)
                .collect(Collectors.toList());

        if (alive.size() <= 1) {
            Player winner = alive.get(0);
            scores.put(winner.getUniqueId(), scores.getOrDefault(winner.getUniqueId(), 0) + 3);

            Bukkit.broadcastMessage("§6§l==========================");
            Bukkit.broadcastMessage("§e최후의 승리자: §f" + winner.getName());

            // 내림차순 점수 출력
            scores.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                    .forEach(entry -> {
                        String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                        Bukkit.broadcastMessage("§f- " + name + ": §e" + entry.getValue() + "점");
                    });

            if (scores.get(winner.getUniqueId()) >= 20) {
                Bukkit.broadcastMessage("§b§l[!] " + winner.getName() + "님이 최종 우승(20점)하셨습니다!");
                stopGame();
            } else {
                Bukkit.broadcastMessage("§7잠시 후 다음 라운드가 시작됩니다...");
                new BukkitRunnable() {
                    @Override
                    public void run() { startNewRound(gameCenter); }
                }.runTaskLater(this, 200L);
            }
        }
    }

    private void stopGame() {
        if (borderTask != null) borderTask.cancel();
        if (damageTask != null) damageTask.cancel();
        isGameStarted = false;
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.getInventory().clear();
            p.setGameMode(GameMode.SURVIVAL);
            AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) maxHealth.setBaseValue(20.0);
            p.setHealth(20.0);
        });
        gameCenter.getWorld().getWorldBorder().setSize(30000000);
    }







    private void giveItems(Player p, String ability) {
        p.getInventory().clear();
        p.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
        p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 64));
        p.getInventory().addItem(new ItemStack(Material.WATER_BUCKET));
        p.getInventory().addItem(new ItemStack(Material.GLASS, 10));
        p.getInventory().addItem(new ItemStack(Material.IRON_CHESTPLATE));

        if (ability.equals("우에키")) p.getInventory().addItem(new ItemStack(Material.OAK_SAPLING, 16));
        else if (ability.equals("올라프")) p.getInventory().addItem(new ItemStack(Material.IRON_AXE, 9));
        else if (ability.equals("미다스")) p.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 64));
        else if (ability.equals("매그너스")) {
            ItemStack bike = new ItemStack(Material.GRAY_DYE);
            p.getInventory().addItem(bike);
        }
    }

    private void generateCircleFloor(Location center, int radius, int targetY, Location teleportDest) {
        World world = center.getWorld();
        int cx = center.getBlockX(), cz = center.getBlockZ();
        long radiusSq = (long) radius * radius;
        int emX = teleportDest.getBlockX(), emZ = teleportDest.getBlockZ();

        new BukkitRunnable() {
            int x = cx - radius;
            @Override
            public void run() {
                for (int i = 0; i < 20; i++) {
                    if (x > cx + radius) {
                        world.getEntitiesByClass(Item.class).forEach(Entity::remove);
                        Bukkit.getOnlinePlayers().forEach(p -> p.teleport(teleportDest.clone().add(0.5, 1.0, 0.5)));
                        this.cancel();
                        return;
                    }
                    long dx = (long) (x - cx) * (x - cx);
                    for (int z = cz - radius; z <= cz + radius; z++) {
                        if (dx + (long) (z - cz) * (z - cz) <= radiusSq) {
                            for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                                Block b = world.getBlockAt(x, y, z);
                                if (y == targetY) { if (x != emX || z != emZ) b.setType(Material.BEDROCK, false); }
                                else if (b.getType() != Material.AIR) b.setType(Material.AIR, false);
                            }
                        }
                    }
                    x++;
                }
            }
        }.runTaskTimer(this, 0, 1);
    }






//------ ↓ 능력자들      ↑ 게임 세팅용





    @EventHandler
    public void onMagnusAbility(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (!"매그너스".equals(playerAbilities.get(p.getUniqueId()))) return;
        if (event.getItem() != null && event.getItem().getType() == Material.GRAY_DYE && event.getAction().name().contains("RIGHT")) {
            if (activeBikes.containsKey(p.getUniqueId())) return;

            Horse bike = (Horse) p.getWorld().spawnEntity(p.getLocation(), EntityType.HORSE);
            bike.setTamed(true);
            bike.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            Optional.ofNullable(bike.getAttribute(Attribute.MOVEMENT_SPEED)).ifPresent(a -> a.setBaseValue(0.5));
            bike.addPassenger(p);
            activeBikes.put(p.getUniqueId(), bike);

            new BukkitRunnable() {
                int t = 0;
                @Override
                public void run() {
                    t++;
                    if (t > 200 || bike.getPassengers().isEmpty() || bike.isDead()) {
                        explodeBike(bike, p);
                        activeBikes.remove(p.getUniqueId());
                        this.cancel();
                        return;
                    }
                    if (bike.getLocation().add(bike.getLocation().getDirection().multiply(1.2)).getBlock().getType().isSolid()) {
                        explodeBike(bike, p);
                        activeBikes.remove(p.getUniqueId());
                        this.cancel();
                    }
                }
            }.runTaskTimer(this, 0, 1);
        }
    }

    private void explodeBike(Horse bike, Player owner) {
        Location loc = bike.getLocation();
        new BukkitRunnable() {
            int m = 0;
            @Override
            public void run() {
                m++;
                loc.add(loc.getDirection().multiply(1.0));
                if (m >= 10 || loc.getBlock().getType().isSolid()) {
                    loc.getWorld().createExplosion(loc, 4.0f, false, false);
                    owner.setNoDamageTicks(20);
                    bike.remove();
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0, 1);
    }

    private void startCustomBorderShrink(World world) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (playerAbilities.isEmpty()) { this.cancel(); return; }
                double size = world.getWorldBorder().getSize();
                if (size <= 1) { this.cancel(); return; }
                world.getWorldBorder().setSize(size - 2, 1);
            }
        }.runTaskTimer(this, 0, 60L);
    }

    private void startBorderDamageTask(World world) {
        damageTask = new BukkitRunnable() {
            @Override
            public void run() {
                WorldBorder b = world.getWorldBorder();
                double s = b.getSize() / 2.0; // 반지름으로 계산해야 정확함
                Location c = b.getCenter();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Location l = p.getLocation();
                    if (Math.abs(l.getX() - c.getX()) > s || Math.abs(l.getZ() - c.getZ()) > s) {
                        p.damage(6.0);
                        p.sendMessage("§c§l[자기장 밖 대미지]");
                    }
                }
            }
        }.runTaskTimer(this, 0, 20L);
    }

    @EventHandler
    public void onUeki(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!"우에키".equals(playerAbilities.get(p.getUniqueId()))) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getItem() != null && e.getItem().getType() == Material.OAK_SAPLING) {
            e.getClickedBlock().getRelative(0, 1, 0).setType(Material.OAK_LOG);
            e.getClickedBlock().getRelative(0, 2, 0).setType(Material.OAK_LOG);
            e.getClickedBlock().getRelative(0, 3, 0).setType(Material.AZALEA_LEAVES);
        }
    }



    @EventHandler
    public void onOlaf(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!"올라프".equals(playerAbilities.get(p.getUniqueId()))) return;
        if (e.getItem() != null && e.getItem().getType() == Material.IRON_AXE && e.getAction().name().contains("RIGHT")) {
            Snowball axe = p.launchProjectile(Snowball.class);
            axe.setItem(new ItemStack(Material.IRON_AXE));
            axe.setCustomName("olaf_axe");
        }
    }

    @EventHandler
    public void onAxeHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Snowball s && "olaf_axe".equals(s.getCustomName())) {
            e.setDamage(10.0); // 하트 5칸 대미지
        }
    }


    @EventHandler
    public void onMidas(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!"미다스".equals(playerAbilities.get(p.getUniqueId()))) return;
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getItem() != null && e.getItem().getType() == Material.GOLD_INGOT) {
            e.getClickedBlock().setType(Material.GOLD_BLOCK);
        }
    }




    @EventHandler
    public void onInvincible(EntityDamageEvent e) {
        if (isInvincible && e.getEntity() instanceof Player) e.setCancelled(true);
    }

    @EventHandler
    public void onDura(PlayerItemDamageEvent e) {
        // 갑옷 내구도 무한 적용
        if (e.getItem().getType() == Material.IRON_CHESTPLATE) e.setCancelled(true);
    }

}