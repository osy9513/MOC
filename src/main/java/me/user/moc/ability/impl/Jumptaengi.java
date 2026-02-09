package me.user.moc.ability.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Cat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import net.kyori.adventure.text.Component;

public class Jumptaengi extends Ability {

    // [Stat] 변신 전 최대 체력 저장
    private final Map<UUID, Double> originalMaxHealth = new HashMap<>();

    // [New] Inventory & Armor 저장 (복구를 위해)
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmors = new HashMap<>();

    // [Visual] 고양이 엔티티와 텔레포트 태스크
    private final Map<UUID, Cat> disguisedCats = new HashMap<>();
    private final Map<UUID, BukkitTask> disguiseTasks = new HashMap<>();

    // [Revert] 15초 뒤 복귀 타이머
    private final Map<UUID, BukkitTask> revertTasks = new HashMap<>();

    public Jumptaengi(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "H03";
    }

    @Override
    public String getName() {
        return "점탱이";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§d히든 ● 점탱이(VRC)",
                "§f미스 퍼리퀸 대 점 탱");
    }

    @Override
    public void giveItem(Player p) {
        ItemStack item = new ItemStack(Material.PINK_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§d고양이 요술장갑"));
            meta.setLore(Arrays.asList(
                    "§f우클릭 시 요술 볼을 날립니다.",
                    "§f맞은 상대는 고양이가 됩니다."));
            item.setItemMeta(meta);
        }
        p.getInventory().addItem(item);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§d히든 ● 점탱이(VRC)");
        p.sendMessage("§f미스 퍼리퀸 대 점 탱");
        p.sendMessage(" ");
        p.sendMessage("§f고양이 요술장갑을 우클릭 시 요술 볼을 날립니다.");
        p.sendMessage("§f맞은 상대는 15초 동안 고양이가 됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f고양이가 될 경우 체력이 1.5줄이 되며 인벤토리가 초기화됩니다.");
        p.sendMessage("§f(15초 후 아이템과 체력이 원래대로 돌아옵니다)");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 10초.");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 고양이 요술장갑.");
        p.sendMessage("§f장비 제거 : 철칼.");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 1. 능력자 체크
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 2. 아이템 체크 (고양이 요술장갑 = Pink Dye)
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.PINK_DYE)
            return;

        // 3. 액션 체크 (우클릭)
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true); // 염료 사용 방지

            // 4. 쿨타임 체크
            if (!checkCooldown(p))
                return;

            // 5. 발사 로직
            launchProjectile(p);
            setCooldown(p, 10);
        }
    }

    private void launchProjectile(Player p) {
        Snowball projectile = p.launchProjectile(Snowball.class);
        projectile.setItem(new ItemStack(Material.PINK_DYE)); // 눈덩이 모양을 핑크 염료로 변경
        projectile.setShooter(p);
        projectile.setMetadata("JumptaengiBall", new org.bukkit.metadata.FixedMetadataValue(plugin, true));

        p.playSound(p.getLocation(), Sound.ENTITY_WITCH_THROW, 1f, 1.5f);
        p.sendMessage("§d점탱이 : 털박이가 되어라 호잇");

        // [Effect] 발사체 트레일
        BukkitTask trailTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (projectile.isValid() && !projectile.isOnGround()) {
                    projectile.getWorld().spawnParticle(org.bukkit.Particle.HEART,
                            projectile.getLocation(), 1, 0, 0, 0, 0);
                    projectile.getWorld().spawnParticle(org.bukkit.Particle.DUST,
                            projectile.getLocation(), 1, 0, 0, 0, 0,
                            new org.bukkit.Particle.DustOptions(org.bukkit.Color.FUCHSIA, 1.0f));
                } else {
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        registerTask(p, trailTask);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball projectile))
            return;
        if (!projectile.hasMetadata("JumptaengiBall"))
            return;

        // [Effect] 적중 시 이펙트
        projectile.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, projectile.getLocation(), 1);
        projectile.getWorld().spawnParticle(org.bukkit.Particle.HEART, projectile.getLocation(), 10, 0.5, 0.5, 0.5);
        projectile.getWorld().playSound(projectile.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2.0f);

        if (!(e.getHitEntity() instanceof Player victim))
            return;
        if (projectile.getShooter() instanceof Player shooter && shooter.equals(victim))
            return;

        // 변신 로직 실행
        transformToCat(victim);
    }

    private void transformToCat(Player victim) {
        UUID uuid = victim.getUniqueId();

        // 중복 변신 방지 -> 기존 변신을 완전히 취소하고 새로 적용
        // (즉, 타이머 초기화됨)
        revertTransformation(uuid, false); // false = don't restore items immediately if we are re-transforming?
        // -> 사실 덮어쓰기면 이미 저장된 인벤토리를 유지해야 할 수도 있지만,
        // 여기선 깔끔하게 '해제 후 재변신'으로 처리하되,
        // 만약 해제 시 아이템이 복구되면 -> 다시 저장 -> OK. (잠깐 복구되었다가 다시 사라짐)

        // 1. 체력 저장 & 변경
        if (!originalMaxHealth.containsKey(uuid)) {
            double oldMax = (victim.getAttribute(Attribute.MAX_HEALTH) != null)
                    ? victim.getAttribute(Attribute.MAX_HEALTH).getValue()
                    : 20.0;
            originalMaxHealth.put(uuid, oldMax);
        }
        if (victim.getAttribute(Attribute.MAX_HEALTH) != null) {
            victim.getAttribute(Attribute.MAX_HEALTH).setBaseValue(30.0);
        }
        victim.setHealth(30.0);

        // 2. 시각적 변신 (투명화 + 고양이)
        victim.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY,
                org.bukkit.potion.PotionEffect.INFINITE_DURATION, 0, false, false));

        Cat cat = (Cat) victim.getWorld().spawnEntity(victim.getLocation(), EntityType.CAT);
        cat.setInvulnerable(true);
        cat.setSilent(true);
        cat.setCollidable(false);
        cat.setAI(false);
        cat.setAgeLock(true);
        cat.setAdult();

        disguisedCats.put(uuid, cat);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!victim.isOnline() || victim.isDead() || !cat.isValid()) {
                    // 비정상 상황 시 정리
                    revertTransformation(uuid, true);
                    this.cancel();
                    return;
                }
                cat.teleport(victim.getLocation());
            }
        }.runTaskTimer(plugin, 0L, 1L);
        disguiseTasks.put(uuid, task);

        // 3. 이펙트 및 메시지
        victim.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, victim.getLocation().add(0, 1, 0), 3,
                0.5, 0.5, 0.5);
        victim.getWorld().spawnParticle(org.bukkit.Particle.HEART, victim.getLocation().add(0, 2, 0), 20, 0.5, 0.5, 0.5,
                0.1);
        victim.getWorld().spawnParticle(org.bukkit.Particle.ITEM, victim.getLocation().add(0, 1.5, 0), 30, 0.5, 0.5,
                0.5, 0.1, new ItemStack(Material.PINK_DYE));
        victim.playSound(victim.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 1f);
        victim.playSound(victim.getLocation(), Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1f, 1f);

        victim.sendMessage("§d당신은 고양이가 되었습니다! (15초간 지속)");

        // 4. 인벤토리 저장 및 초기화 (1초 딜레이)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (victim.isOnline() && disguisedCats.containsKey(uuid)) {
                    // 저장 (이미 저장된 게 있다면 덮어쓰지 않음 - 최초의 원본 보존?
                    // -> 아니요, 재변신 시에는 '잠깐 복구된' 상태를 다시 저장해야 함.
                    // 하지만 위에서 revertTransformation을 호출했으므로 맵은 비워져 있음.
                    savedInventories.put(uuid, victim.getInventory().getContents());
                    savedArmors.put(uuid, victim.getInventory().getArmorContents());

                    victim.getInventory().clear();

                    victim.sendMessage("§c인벤토리가 초기화되었습니다.");
                    victim.playSound(victim.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.5f);
                    victim.getWorld().spawnParticle(org.bukkit.Particle.LARGE_SMOKE, victim.getLocation(), 10, 0.2, 0.5,
                            0.2, 0.05);
                }
            }
        }.runTaskLater(plugin, 20L);

        // 5. 15초 후 복구 타이머
        BukkitTask revertTask = new BukkitRunnable() {
            @Override
            public void run() {
                revertTransformation(uuid, true); // true = restore items
            }
        }.runTaskLater(plugin, 300L); // 15초

        revertTasks.put(uuid, revertTask);
    }

    private void revertTransformation(UUID uuid, boolean restoreItems) {
        // 타이머 취소
        BukkitTask timer = revertTasks.remove(uuid);
        if (timer != null && !timer.isCancelled()) {
            timer.cancel();
        }

        // 고양이 & 텔레포트 태스크 제거
        BukkitTask disTask = disguiseTasks.remove(uuid);
        if (disTask != null && !disTask.isCancelled())
            disTask.cancel();

        Cat cat = disguisedCats.remove(uuid);
        if (cat != null && cat.isValid())
            cat.remove();

        Player p = plugin.getServer().getPlayer(uuid);
        if (p != null) {
            // 투명화 해제
            p.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);

            // 체력 복구
            if (originalMaxHealth.containsKey(uuid)) {
                double oldMax = originalMaxHealth.remove(uuid);
                if (p.getAttribute(Attribute.MAX_HEALTH) != null) {
                    p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(oldMax);
                }
            }

            // 아이템 복구
            if (restoreItems) {
                if (savedInventories.containsKey(uuid)) {
                    p.getInventory().setContents(savedInventories.remove(uuid));
                }
                if (savedArmors.containsKey(uuid)) {
                    p.getInventory().setArmorContents(savedArmors.remove(uuid));
                }
                // 복구 메시지
                if (p.isOnline()) {
                    p.sendMessage("§a변신이 해제되어 원래 모습으로 돌아왔습니다.");
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                }
            } else {
                // 아이템 복구 안함 (재변신 등을 위해) -> 맵에서만 제거
                savedInventories.remove(uuid);
                savedArmors.remove(uuid);
            }
        } else {
            // 플레이어가 없다면 맵 정리만
            originalMaxHealth.remove(uuid);
            savedInventories.remove(uuid);
            savedArmors.remove(uuid);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        // 죽으면 변신 해제 (아이템 복구는? DeathEvent에서 드랍되는 아이템이 문제.
        // 클리어된 상태에서 죽으면 드랍이 없음.
        // 원본 아이템을 드랍해줘야 하나? -> 보통은 불공평할 수 있으니 그냥 증발 or 드랍?
        // 유저 요청 사항이 없으므로, 일단 '변신 해제'만 수행.
        // 단, 죽어서 리스폰되면 아이템이 없으므로... 복구해주는 게 맞을 수도 있지만
        // "인벤토리 초기화"가 페널티라면 죽었을 때 드랍 안 되는 것도 페널티일 수 있음.
        // 여기서는 안전하게 revertTransformation(restoreItems=false) 호출하여 맵 정리만 하고,
        // 아이템은 날리거나 유지하도록 결정.
        // -> 보통 변신 풀리면서 아이템 들어오면 그게 바닥에 뿌려질 수 있음 (KeepInventory false면).
        // 일단 맵 정리는 필수.
        if (disguisedCats.containsKey(e.getEntity().getUniqueId())) {
            // 죽었으니 아이템 복구해주지 않음 (이미 죽은 자리에 드랍되어야 하는데, 인벤이 비었음)
            // 만약 드랍되길 원하면 e.getDrops().addAll(...) 해야 함.
            // 복잡도를 피하기 위해 맵만 정리.
            revertTransformation(e.getEntity().getUniqueId(), false);
        }
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
    }

    @Override
    public void reset() {
        super.reset();

        // 모든 변신 해제 및 아이템 복구 (라운드 종료 시점이므로 복구해주는 게 맞음)
        // 키셋 복사본으로 순회
        for (UUID uuid : new HashSet<>(originalMaxHealth.keySet())) {
            revertTransformation(uuid, true);
        }
        // 혹시 남은 잔여물 정리
        for (UUID uuid : new HashSet<>(disguisedCats.keySet())) {
            revertTransformation(uuid, true);
        }

        originalMaxHealth.clear();
        savedInventories.clear();
        savedArmors.clear();
        disguisedCats.clear();
        disguiseTasks.clear();
        revertTasks.clear();
    }
}
