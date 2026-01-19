package me.user.moc.game;

import me.user.moc.MocPlugin;
import me.user.moc.ability.AbilityManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

public class GameManager implements Listener {
    private final MocPlugin plugin;
    private final ArenaManager arenaManager;
    private AbilityManager abilityManager; // 나중에 setter로 주입하거나 생성자에서 받음

    private boolean isGameStarted = false;
    private boolean isInvincible = false;
    private final Map<UUID, Integer> scores = new HashMap<>();

    private BukkitTask selectionTask;
    private final Set<UUID> readyPlayers = new HashSet<>();

    public GameManager(MocPlugin plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void setAbilityManager(AbilityManager abilityManager) {
        this.abilityManager = abilityManager;
    }

    public boolean isGameStarted() {
        return isGameStarted;
    }

    public void startNewRound(Location center) {
        this.isGameStarted = false;
        readyPlayers.clear();
        if (abilityManager != null) {
            abilityManager.resetAbilities();
        }

        // 경기장 생성
        arenaManager.setGameCenter(center);
        arenaManager.generateCircleFloor(center, 60, center.getBlockY() - 1);

        // 능력 배정 및 안내
        if (abilityManager != null) {
            abilityManager.distributeAbilities();
        }

        // 45초 타이머
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
        }.runTaskTimer(plugin, 0, 20L);
    }

    public void acceptAbility(Player p) {
        if (readyPlayers.contains(p.getUniqueId())) return;
        readyPlayers.add(p.getUniqueId());
        p.sendMessage("§a[MOC] §f능력을 수락하셨습니다. 준비 완료!");
    }

    private void startGameLogic() {
        isGameStarted = true;
        isInvincible = true;
        Bukkit.broadcastMessage("§6§l[MOC] 전투가 곧 시작됩니다! 무적 시간(3초)");

        Location center = arenaManager.getGameCenter();

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getInventory().clear();
            giveBasicItems(p);

            // 능력 아이템 지급
            if (abilityManager != null) {
                abilityManager.giveAbilityItems(p);
            }

            // 랜덤 텔레포트
            double rx = center.getX() + (new Random().nextDouble() * 80 - 40);
            double rz = center.getZ() + (new Random().nextDouble() * 80 - 40);
            p.teleport(new Location(center.getWorld(), rx, center.getY() + 1, rz));

            // 체력 3줄 (60.0)
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
        }.runTaskLater(plugin, 60L);

        // 5분 뒤 자기장 시작
        new BukkitRunnable() {
            @Override
            public void run() {
                arenaManager.startBorderShrink();
            }
        }.runTaskLater(plugin, 20L * 60 * 5);

        arenaManager.startBorderDamage();
    }

    public void stopGame() {
        arenaManager.stopTasks();
        if (selectionTask != null) selectionTask.cancel();
        
        isGameStarted = false;
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.getInventory().clear();
            p.setGameMode(GameMode.SURVIVAL);
            AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) maxHealth.setBaseValue(20.0);
            p.setHealth(20.0);
        });
    }

    private void giveBasicItems(Player p) {
        p.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
        p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 64));
        p.getInventory().addItem(new ItemStack(Material.WATER_BUCKET));
        p.getInventory().addItem(new ItemStack(Material.GLASS, 10));
        p.getInventory().addItem(new ItemStack(Material.IRON_CHESTPLATE));

        ItemStack regenPotion = new ItemStack(Material.POTION);
        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) regenPotion.getItemMeta();
        if (meta != null) {
            try {
                meta.setBasePotionData(new org.bukkit.potion.PotionData(org.bukkit.potion.PotionType.REGENERATION));
            } catch (NoSuchFieldError e) {
                meta.setBasePotionData(new org.bukkit.potion.PotionData(org.bukkit.potion.PotionType.valueOf("REGEN")));
            }
            regenPotion.setItemMeta(meta);
        }
        p.getInventory().addItem(regenPotion);
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
                    public void run() { startNewRound(arenaManager.getGameCenter()); }
                }.runTaskLater(plugin, 200L);
            }
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

    @EventHandler
    public void onInvincible(EntityDamageEvent e) {
        if (isInvincible && e.getEntity() instanceof Player) e.setCancelled(true);
    }

    @EventHandler
    public void onDura(PlayerItemDamageEvent e) {
        if (e.getItem().getType() == Material.IRON_CHESTPLATE) e.setCancelled(true);
    }
}
