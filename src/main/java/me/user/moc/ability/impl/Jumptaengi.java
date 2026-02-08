package me.user.moc.ability.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
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

    // 능력을 맞은 피해자들 목록 (게임 종료 시 원상복구를 위해 저장할 수도 있음)
    // 하지만 "이번 라운드 동안"이라고 했으므로, 게임 종료 시 GameManager가 초기화해주기를 기대하거나
    // AbilityManager.reset()에서 cleanup을 통해 복구해주는 것이 좋습니다.
    private final Map<UUID, Double> originalMaxHealth = new HashMap<>();

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
        p.sendMessage("§f맞은 상대는 이번 라운드 동안 고양이가 됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f고양이가 될 경우 체력이 1.5줄이 되며 인벤토리가 초기화됩니다.");
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
        // 눈덩이를 발사하되, 아이템 모양을 핑크 염료로 설정 (Paper API 1.21)
        Snowball projectile = p.launchProjectile(Snowball.class);
        projectile.setItem(new ItemStack(Material.PINK_DYE)); // 눈덩이 모양을 핑크 염료로 변경
        projectile.setShooter(p);

        // 메타데이터로 "점탱이의 요술볼"임을 표시
        projectile.setMetadata("JumptaengiBall", new org.bukkit.metadata.FixedMetadataValue(plugin, true));

        // 사운드
        p.playSound(p.getLocation(), Sound.ENTITY_WITCH_THROW, 1f, 1.5f);
        p.sendMessage("§d점탱이 : 털박이가 되어라 호잇");

        // [Effect] 발사체 트레일 (하트 파티클)
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
        // 배사체 체크
        if (!(e.getEntity() instanceof Snowball projectile))
            return;
        if (!projectile.hasMetadata("JumptaengiBall"))
            return;

        // [Effect] 적중 시 화려한 폭발 이펙트
        projectile.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, projectile.getLocation(), 1);
        projectile.getWorld().spawnParticle(org.bukkit.Particle.HEART, projectile.getLocation(), 10, 0.5, 0.5, 0.5);
        projectile.getWorld().playSound(projectile.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2.0f);

        // 타겟 체크 (플레이어만)
        if (!(e.getHitEntity() instanceof Player victim))
            return;

        // 시전자는 맞지 않음 (혹시나)
        if (projectile.getShooter() instanceof Player shooter && shooter.equals(victim))
            return;

        // 변신 로직 실행
        transformToCat(victim);
    }

    private void transformToCat(Player victim) {
        // 이미 변신 당했거나 스탯이 변경된 상태라면 중복 적용 방지?
        // -> "이번 라운드 동안"이므로 덮어쓰기 합니다.

        // 1. 체력 30 (1.5줄) 설정
        // 기존 최대 체력 저장 (복구용)
        if (!originalMaxHealth.containsKey(victim.getUniqueId())) {
            double oldMax = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
            originalMaxHealth.put(victim.getUniqueId(), oldMax);
        }
        victim.getAttribute(Attribute.MAX_HEALTH).setBaseValue(30.0);
        victim.setHealth(30.0); // 체력 회복

        // 2. 시각 효과 (거대한 연기 폭발 + 하트 + 고양이 소리)
        victim.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER,
                victim.getLocation().add(0, 1, 0), 3, 0.5, 0.5, 0.5);
        victim.getWorld().spawnParticle(org.bukkit.Particle.HEART,
                victim.getLocation().add(0, 2, 0), 20, 0.5, 0.5, 0.5, 0.1);

        // 주변에 아이템 퍼지는 효과 (시각적 연출, 실제 아이템 아님)
        victim.getWorld().spawnParticle(org.bukkit.Particle.ITEM,
                victim.getLocation().add(0, 1.5, 0),
                30, 0.5, 0.5, 0.5, 0.1,
                new ItemStack(Material.PINK_DYE));

        victim.playSound(victim.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 1f);
        victim.playSound(victim.getLocation(), Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1f, 1f); // 치익- 변신 소리

        victim.sendMessage("§d당신은 고양이가 되었습니다! (체력 증가, 인벤토리 초기화)");

        // 3. 인벤토리 초기화 (1초 뒤)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (victim.isOnline()) {
                    victim.getInventory().clear();
                    victim.sendMessage("§c인벤토리가 초기화되었습니다.");
                    victim.playSound(victim.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.5f);
                    // 추가 효과
                    victim.getWorld().spawnParticle(org.bukkit.Particle.LARGE_SMOKE, victim.getLocation(), 10, 0.2, 0.5,
                            0.2, 0.05);
                }
            }
        }.runTaskLater(plugin, 20L); // 1초 (20 ticks)
    }

    @Override
    public void cleanup(Player p) {
        // 내 능력의 cleanup (쿨타임 등)
        super.cleanup(p);
    }

    @Override
    public void reset() {
        super.reset();

        // 라운드 종료 시 변신시켰던 피해자들 복구
        // (게임 매니저가 플레이어 리셋을 해주지만, MaxHealth는 영구적일 수 있으므로 여기서 복구)
        for (Map.Entry<UUID, Double> entry : originalMaxHealth.entrySet()) {
            Player victim = plugin.getServer().getPlayer(entry.getKey());
            if (victim != null && victim.isOnline()) {
                // 원래 체력으로 복구 (단, 라운드 종료 후 초기화 로직과 겹칠 수 있으니 주의)
                // MocPlugin의 리셋 로직을 믿되, 혹시 모르니 복구
                if (victim.getAttribute(Attribute.MAX_HEALTH) != null) {
                    victim.getAttribute(Attribute.MAX_HEALTH).setBaseValue(entry.getValue());
                }
            }
        }
        originalMaxHealth.clear();
    }
}
