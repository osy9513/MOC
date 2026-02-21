package me.user.moc.ability.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import me.user.moc.MocPlugin;

public class GonFreecss extends Ability {

    private final String GUI_TITLE = "가위.. 바위..";

    // GUI 선택 상태 관리를 위한 Map
    private final Map<UUID, BukkitTask> selectTasks = new HashMap<>();

    // 가위(Scissors) 지속 시간 관리를 위한 상태
    private final Map<UUID, BukkitTask> scissorsTask = new HashMap<>();

    // 바위(Rock) 지속 시간 관리를 위한 상태
    private final Map<UUID, BukkitTask> rockTask = new HashMap<>();

    public GonFreecss(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "060";
    }

    @Override
    public String getName() {
        return "곤 프릭스";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c전투 ● 곤 프릭스(헌터×헌터)",
                "§f가위바위권을 사용합니다.");
    }

    @Override
    public void giveItem(Player p) {
        p.getInventory().remove(Material.IRON_SWORD);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 곤 프릭스(헌터×헌터)");
        p.sendMessage("§f가위바위권을 사용합니다.");
        p.sendMessage("§f맨손 쉬프트 좌 클릭 시 가위, 바위, 보를 선택할 창이 뜹니다.");
        p.sendMessage("§f가위 - 5초 동안 사용 가능한 가위를 얻습니다. 가위는 사거리가 두 배이며 돌 칼과 동급의 DPS를 가집니다.");
        p.sendMessage("§f바위 - 10초 동안 한 번, 바위로 공격 시 20 데미지를 무적 무시로 줍니다.");
        p.sendMessage("§f보 - 전방에 에너지를 발사하여 8의 데미지를 줍니다.");
        p.sendMessage("§f3초 안에 고르지 못하면 자동으로 바위가 선택됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 12초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 철 칼");
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        cancelTask(selectTasks, p.getUniqueId());
        cancelTask(scissorsTask, p.getUniqueId());
        cancelTask(rockTask, p.getUniqueId());

        AttributeInstance rangeAttr = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (rangeAttr != null) {
            rangeAttr.setBaseValue(3.0); // 사거리 원상 복구
        }

        // 아이템 정리
        p.getInventory().remove(Material.SHEARS);
        p.getInventory().remove(Material.STONE);
    }

    private void cancelTask(Map<UUID, BukkitTask> map, UUID uuid) {
        if (map.containsKey(uuid)) {
            BukkitTask task = map.remove(uuid);
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 곤 프릭스 능력이 없는데 우클릭/좌클릭 등 상호작용 하려고 할 때 곤 아이템 검사 후 삭제
        ItemStack handItem = p.getInventory().getItemInMainHand();
        if (handItem != null && handItem.hasItemMeta() && ("§e가위".equals(handItem.getItemMeta().getDisplayName())
                || "§7바위".equals(handItem.getItemMeta().getDisplayName()))) {
            if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
                handItem.setAmount(0);
                return;
            }
        }

        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 1. 가위바위권 발동: 맨손 + 쉬프트 + 좌클릭
        if (p.isSneaking() && (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK)) {
            if (p.getInventory().getItemInMainHand().getType() == Material.AIR) {
                // [추가] 침묵 체크
                if (isSilenced(p))
                    return;

                if (!checkCooldown(p))
                    return;

                startJajanken(p);
                return;
            }
        }

        // 2. 가위(Scissors) 공격 로직: 허공 좌클릭
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            ItemStack item = p.getInventory().getItemInMainHand();
            if (item != null && item.getType() == Material.SHEARS && item.hasItemMeta()
                    && "§e가위".equals(item.getItemMeta().getDisplayName())) {

                // 곤 능력이 아닌데 들고 좌클릭하면 즉시 증발
                if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
                    item.setAmount(0);
                    return;
                }

                // [버그 픽스] 타이머가 끝났는데 편법으로 들고 있는 경우 (창고 등)
                if (!scissorsTask.containsKey(p.getUniqueId())) {
                    item.setAmount(0);
                    p.sendMessage("§c능력 사용 시간이 지났습니다.");
                    return;
                }

                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f);
            }
        }
    }

    // 바위(Rock) 공격 로직: 직접 평타 타격
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p))
            return;

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null)
            return;

        boolean isGonItem = false;
        if (item.hasItemMeta() && ("§e가위".equals(item.getItemMeta().getDisplayName())
                || "§7바위".equals(item.getItemMeta().getDisplayName()))) {
            isGonItem = true;
        }

        // 곤 프릭스 능력자가 아닌데 곤 프릭스 아이템을 들고 타격하려는 경우 아이템 강제 압수 및 리턴
        if (isGonItem && !AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
            item.setAmount(0); // 타인 사용 시 즉시 증발
            return;
        }

        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 바위 타격
        if (item.getType() == Material.STONE && item.hasItemMeta()
                && "§7바위".equals(item.getItemMeta().getDisplayName())) {
            e.setCancelled(true); // 기존 물리 데미지는 무시

            if (!(e.getEntity() instanceof LivingEntity target))
                return;
            if (target instanceof Player pTarget && pTarget.getGameMode() == org.bukkit.GameMode.SPECTATOR)
                return;

            // 1회 필살 타격 적용
            useRock(p, target);
        }

        // 가위 직접 타격 (raytrace 말고 겹쳤을때 때리는 경우 방지 또는 데미지 동기화)
        if (item.getType() == Material.SHEARS && item.hasItemMeta()
                && "§e가위".equals(item.getItemMeta().getDisplayName())) {

            if (!(e.getEntity() instanceof LivingEntity target))
                return;
            if (target instanceof Player pTarget && pTarget.getGameMode() == org.bukkit.GameMode.SPECTATOR)
                return;

            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f);
            if (target.getNoDamageTicks() <= 0) {
                e.setDamage(5.0); // 바닐라 타격을 무력화(Cancel)하지 않고 데미지만 5.0으로 덮어씀
                target.setNoDamageTicks(12); // 연속 클릭 뎀감 방어
            } else {
                e.setCancelled(true); // 타격 무적 프레임일 땐 타격 통과(취소)
            }
        }
    }

    // 다른 유저가 줍거나 보관하지 못하도록 방지 - 아이템 버림
    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        Item itemEntity = e.getItemDrop();
        ItemStack item = itemEntity.getItemStack();
        if (item.hasItemMeta() && "§e가위".equals(item.getItemMeta().getDisplayName())) {
            itemEntity.remove(); // 버리는 즉시 증발
            cancelTask(scissorsTask, p.getUniqueId());
            AttributeInstance rangeAttr = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
            if (rangeAttr != null) {
                rangeAttr.setBaseValue(3.0);
            }
        } else if (item.hasItemMeta() && "§7바위".equals(item.getItemMeta().getDisplayName())) {
            itemEntity.remove(); // 버리는 즉시 증발
            cancelTask(rockTask, p.getUniqueId());
        }
    }

    // 다른 유저가 줍거나 보관하지 못하도록 방지 - 사망 시 드롭
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        boolean hasGonItems = e.getDrops()
                .removeIf(item -> item.hasItemMeta() && ("§e가위".equals(item.getItemMeta().getDisplayName())
                        || "§7바위".equals(item.getItemMeta().getDisplayName())));
        if (hasGonItems) {
            cancelTask(scissorsTask, p.getUniqueId());
            cancelTask(rockTask, p.getUniqueId());
            AttributeInstance rangeAttr = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
            if (rangeAttr != null) {
                rangeAttr.setBaseValue(3.0);
            }
        }
    }

    private void startJajanken(Player p) {
        setCooldown(p, 12);

        // 1. 전체 메시지 출력
        Bukkit.broadcastMessage("§e곤 프릭스 : 처음은 주먹! 가위.. 바위..");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);

        // 2. GUI 오픈
        Inventory gui = Bukkit.createInventory(null, 9, GUI_TITLE);

        ItemStack scissors = new ItemStack(Material.SHEARS);
        ItemMeta scMeta = scissors.getItemMeta();
        scMeta.setDisplayName("§e가위");
        scMeta.setLore(Arrays.asList("§75초간 사거리 2배(6칸)의 돌검급 평타 구사"));
        scissors.setItemMeta(scMeta);
        gui.setItem(0, scissors);

        ItemStack rock = new ItemStack(Material.STONE);
        ItemMeta rockMeta = rock.getItemMeta();
        rockMeta.setDisplayName("§7바위");
        rockMeta.setLore(Arrays.asList("§710초 안에 1회 타격 시 20 고정피해"));
        rock.setItemMeta(rockMeta);
        gui.setItem(4, rock);

        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta paperMeta = paper.getItemMeta();
        paperMeta.setDisplayName("§f보");
        paperMeta.setLore(Arrays.asList("§7전방에 8데미지 에너지볼 즉시 발사"));
        paper.setItemMeta(paperMeta);
        gui.setItem(8, paper);

        p.openInventory(gui);

        // 3. 3초 타이머 시작 (타임아웃 시 바위 강제 선택)
        cancelTask(selectTasks, p.getUniqueId());
        BukkitTask timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (p.getOpenInventory().getTitle().equals(GUI_TITLE)) {
                    p.closeInventory();
                    executeSelection(p, "바위");
                }
            }
        }.runTaskLater(plugin, 60L); // 3초 (60틱)

        selectTasks.put(p.getUniqueId(), timeoutTask);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p))
            return;
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        if (GUI_TITLE.equals(e.getView().getTitle())) {
            e.setCancelled(true);

            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR)
                return;

            String name = clicked.getItemMeta().getDisplayName();
            String selection = "바위";
            if (name.contains("가위"))
                selection = "가위";
            else if (name.contains("바위"))
                selection = "바위";
            else if (name.contains("보"))
                selection = "보";

            cancelTask(selectTasks, p.getUniqueId());
            p.closeInventory();
            executeSelection(p, selection);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p))
            return;
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // GUI를 강제로 닫았는데 타이머가 돌아가고 있다면 (ESC 등을 눌렀을 때) -> 바위 강제 선택
        if (GUI_TITLE.equals(e.getView().getTitle())) {
            if (selectTasks.containsKey(p.getUniqueId())) {
                cancelTask(selectTasks, p.getUniqueId());
                executeSelection(p, "바위");
            }
        }
    }

    private void executeSelection(Player p, String selection) {
        // 공통 효과
        Bukkit.broadcastMessage("§e곤 프릭스 : " + selection + "!");

        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4,
                new Particle.DustOptions(Color.ORANGE, 1.5f));
        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4,
                new Particle.DustOptions(Color.YELLOW, 1.5f));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);

        // 선택지 분기
        switch (selection) {
            case "가위":
                grantScissors(p);
                break;
            case "바위":
                grantRock(p);
                break;
            case "보":
                shootPaper(p);
                break;
        }
    }

    private void grantScissors(Player p) {
        ItemStack scissors = new ItemStack(Material.SHEARS);
        ItemMeta meta = scissors.getItemMeta();
        meta.setDisplayName("§e가위");
        scissors.setItemMeta(meta);

        p.getInventory().setItemInMainHand(scissors);

        // [수정] 에렌 예거와 동일하게 바닐라 평타 사거리를 직접 6.0으로 늘려줌
        AttributeInstance rangeAttr = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (rangeAttr != null) {
            rangeAttr.setBaseValue(6.0); // 6칸 사거리
        }

        // 5초 후 삭제 (연속 타격 가능)
        cancelTask(scissorsTask, p.getUniqueId());
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    // 사거리 복구
                    AttributeInstance attr = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
                    if (attr != null) {
                        attr.setBaseValue(3.0); // 기본 사거리인 3.0으로 복구
                    }

                    // 메인 핸드에 가위가 있으면 날린다
                    for (ItemStack item : p.getInventory().getContents()) {
                        if (item != null && item.getType() == Material.SHEARS && item.hasItemMeta()
                                && "§e가위".equals(item.getItemMeta().getDisplayName())) {
                            item.setAmount(0);
                        }
                    }
                    p.sendMessage("§e[가위] 지속 시간이 끝났습니다.");
                    scissorsTask.remove(p.getUniqueId());
                }
            }
        }.runTaskLater(plugin, 100L); // 5초
        scissorsTask.put(p.getUniqueId(), task);
    }

    private void grantRock(Player p) {
        ItemStack rock = new ItemStack(Material.STONE);
        ItemMeta meta = rock.getItemMeta();
        meta.setDisplayName("§7바위");
        rock.setItemMeta(meta);

        p.getInventory().setItemInMainHand(rock);

        // 5초 후 삭제 (1회 한정)
        cancelTask(rockTask, p.getUniqueId());
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    for (ItemStack item : p.getInventory().getContents()) {
                        if (item != null && item.getType() == Material.STONE && item.hasItemMeta()
                                && "§7바위".equals(item.getItemMeta().getDisplayName())) {
                            item.setAmount(0);
                        }
                    }
                    p.sendMessage("§7[바위] 보유 시간이 끝났습니다.");
                    rockTask.remove(p.getUniqueId());
                }
            }
        }.runTaskLater(plugin, 200L); // 10초(200틱)
        rockTask.put(p.getUniqueId(), task);
    }

    private void useRock(Player p, LivingEntity target) {
        // 바위 1회 타격 처리
        cancelTask(rockTask, p.getUniqueId());
        // 바위 삭제
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == Material.STONE && item.hasItemMeta()
                    && "§7바위".equals(item.getItemMeta().getDisplayName())) {
                item.setAmount(0);
            }
        }

        // 무적 뚫기 (고정 20 데미지)
        target.setNoDamageTicks(0);
        target.damage(20.0); // 어그로, 로그 기록용 데미지

        // 확실한 트루데미지를 위해 체력 직접 삭감
        double realHealth = target.getHealth() - 20.0;
        if (realHealth < 0)
            realHealth = 0;
        try {
            target.setHealth(realHealth);
        } catch (Exception ignored) {
        }

        // 타격감 (이펙트)
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_STONE_BREAK, 2.0f, 0.5f);
        target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5,
                Material.STONE.createBlockData());
    }

    private void shootPaper(Player p) {
        // 즉발 투사체 (에너지 볼)
        Location startLoc = p.getEyeLocation();
        Vector dir = p.getLocation().getDirection().normalize().multiply(1.5);

        p.getWorld().playSound(startLoc, Sound.ENTITY_GHAST_SHOOT, 0.8f, 1.2f);

        new BukkitRunnable() {
            int ticks = 0;
            Location currentLoc = startLoc.clone();

            @Override
            public void run() {
                if (ticks++ > 30) { // 약 1.5초(30틱 * 1.5블럭 = 45블럭) 후 소멸
                    this.cancel();
                    return;
                }

                currentLoc.add(dir);

                // 오렌지+노랑 구형 파티클 모방
                currentLoc.getWorld().spawnParticle(Particle.DUST, currentLoc, 10, 0.3, 0.3, 0.3,
                        new Particle.DustOptions(Color.ORANGE, 2.0f));
                currentLoc.getWorld().spawnParticle(Particle.DUST, currentLoc, 5, 0.2, 0.2, 0.2,
                        new Particle.DustOptions(Color.YELLOW, 1.5f));

                // 벽(블럭) 충돌
                if (currentLoc.getBlock().getType().isSolid()) {
                    this.cancel();
                    explodePaper(currentLoc, p);
                    return;
                }

                // 엔티티 충돌
                for (Entity entity : currentLoc.getWorld().getNearbyEntities(currentLoc, 1.0, 1.0, 1.0)) {
                    if (entity != p && entity instanceof LivingEntity target) {
                        if (target instanceof Player pTarget && pTarget.getGameMode() == org.bukkit.GameMode.SPECTATOR)
                            continue;

                        this.cancel();
                        explodePaper(currentLoc, p);
                        target.damage(8.0, p);
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void explodePaper(Location loc, Player p) {
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
    }

}
