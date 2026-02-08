package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Ddangkong extends Ability {

    private final Random random = new Random();

    public Ddangkong(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "H04";
    }

    @Override
    public String getName() {
        return "땅콩";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§d히든 ● 땅콩(바집소)",
                "§f자랑스러운 육군 중사 땅콩이가 됩니다.");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void giveItem(Player p) {
        // 총알 (폭죽 탄약)
        ItemStack bullet = new ItemStack(Material.FIREWORK_STAR);
        ItemMeta bulletMeta = bullet.getItemMeta();
        bulletMeta.displayName(Component.text("§c총알"));
        bulletMeta.setLore(Arrays.asList("§7우클릭하여 발사합니다.", "§8(데미지 10)"));
        bulletMeta.setCustomModelData(4001); // 식별용 데이터
        bullet.setItemMeta(bulletMeta);

        // 훈련용 수류탄 (TNT)
        ItemStack grenade = new ItemStack(Material.TNT);
        ItemMeta grenadeMeta = grenade.getItemMeta();
        grenadeMeta.displayName(Component.text("§c훈련용 수류탄"));
        grenadeMeta.setLore(Arrays.asList("§7우클릭하여 투척합니다.", "§8(3초 후 폭발)"));
        grenadeMeta.setCustomModelData(4002);
        grenade.setItemMeta(grenadeMeta);

        p.getInventory().addItem(bullet);
        p.getInventory().addItem(grenade);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§d히든 ● 땅콩(바집소)");
        p.sendMessage("§f중사가 될 경우 총알을 총처럼 던질 수 있습니다.");
        p.sendMessage("§f총알을 우클릭 하여 발사합니다. (데미지10)");
        p.sendMessage("§f훈련용 수류탄을 우클릭 하여 3초 뒤 폭발하는 TNT를 던집니다.");
        p.sendMessage("§f총알과 수류탄 능력 발동 중 40% 확률로 선임에게 전화가 옵니다.");
        p.sendMessage("§f전화가 올 경우 시전한 능력이 불발되고, 5초간 구속 5, 저항 2가 걸립니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 총알 쿨타임 5초, 수류탄 쿨타임 10초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 총알, 수류탄");
        p.sendMessage("§f장비 제거 : 없음");

        // 마지막으로 아이템 지급 (테스트 용도 등)
        giveItem(p);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        ItemStack item = e.getItem();
        if (item == null)
            return;

        Action action = e.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {

            // 1. 총알 (폭죽 탄약)
            if (item.getType() == Material.FIREWORK_STAR) {
                e.setCancelled(true); // 폭죽 탄약 설치/사용 방지

                // 쿨타임 체크 (별도 맵 사용 필요 - 다중 쿨타임)
                // Ability.java의 기본 checkCooldown은 단일 쿨타임만 지원하므로,
                // 여기서는 Custom Cooldown Map을 사용할 수도 있지만,
                // 간단히 '총알'을 메인 쿨타임으로 쓰고 '수류탄'을 별도 관리하거나,
                // Ability 클래스의 cooldowns 맵을 활용하되 키를 다르게 쓸 순 없습니다 (UUID 키).
                // 따라서, 편의상 '총알'은 메인 쿨타임(cooldowns), '수류탄'은 별도 로직으로 구현합니다.
                // 하지만 부모 클래스 구조상 다중 쿨타임 지원이 안되므로,
                // 여기서는 **맵에 UUID + 구분자**를 넣을 수 없으니(UUID 키 고정),
                // 별도의 쿨타임 맵을 선언해야 합니다.

                // (아래에 별도 맵 선언 및 로직 추가 구현)
                if (isCooldown(p, "BULLET")) {
                    long left = getCooldownTime(p, "BULLET");
                    p.sendActionBar(Component.text("§c총알 쿨타임: " + String.format("%.1f", (left / 1000.0)) + "초"));
                    return;
                }

                // 전화 찬스 (40%)
                if (tryPhoneCall(p)) {
                    // 전화 걸리면 쿨타임 적용 여부? -> "시전한 능력이 불발되고"라고 했으니 쿨타임은 안 도는 게 맞거나,
                    // 혹은 패널티로 쿨타임을 돌릴 수도 있습니다.
                    // 보통 '불발'은 소모값만 날리는 경우가 많습니다.
                    // 여기서는 쿨타임은 돌리지 않고 전화 패널티만 수행합니다.
                    return;
                }

                // 총알 발사 로직
                setCustomCooldown(p, "BULLET", 5); // 5초 쿨타임

                // 눈덩이를 발사체로 사용 (모양은 변경 불가하지만 가장 안정적)
                // 혹은 ItemProjectile (1.20.4+ API 필요, 없으면 Snowball에 ItemMeta 설정 불가)
                // 여기서는 Snowball을 쓰고, 발사체 히트 이벤트에서 처리합니다.
                // 1.21.11에서는 Snowball에 setItem()이 가능합니다.
                Snowball projectile = p.launchProjectile(Snowball.class);
                projectile.setItem(new ItemStack(Material.FIREWORK_STAR)); // 탄약 모양
                projectile.setGravity(false); // 중력 무시 (직선)
                projectile.setVelocity(p.getLocation().getDirection().multiply(3.0)); // 화살 속도 (약 3.0)
                projectile.setShooter(p);

                // 메타데이터로 총알 식별
                projectile.addScoreboardTag("DdangkongBullet");

                // 소리: 짧고 경쾌한 탕!
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 2.0f);

                // [Effect] 총알 트레일 (연기)
                BukkitTask trailTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (projectile.isValid() && !projectile.isOnGround()) {
                            projectile.getWorld().spawnParticle(org.bukkit.Particle.CLOUD,
                                    projectile.getLocation(), 1, 0, 0, 0, 0);
                            projectile.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                                    projectile.getLocation(), 1, 0, 0, 0, 0);
                        } else {
                            this.cancel();
                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L);
                registerTask(p, trailTask); // Cleanup에 등록

                // 등록 (엔티티 관리)
                registerSummon(p, projectile);
            }

            // 2. 수류탄 (TNT)
            else if (item.getType() == Material.TNT) {
                e.setCancelled(true); // 설치 방지

                if (isCooldown(p, "GRENADE")) {
                    long left = getCooldownTime(p, "GRENADE");
                    p.sendActionBar(Component.text("§c수류탄 쿨타임: " + String.format("%.1f", (left / 1000.0)) + "초"));
                    return;
                }

                if (tryPhoneCall(p)) {
                    return;
                }

                setCustomCooldown(p, "GRENADE", 10); // 10초 쿨타임

                // TNT 아이템 던지기
                Item thrownTnt = p.getWorld().dropItem(p.getEyeLocation(), new ItemStack(Material.TNT));
                thrownTnt.setPickupDelay(Integer.MAX_VALUE); // 줍기 방지
                thrownTnt.setVelocity(p.getLocation().getDirection().multiply(1.5)); // 적절한 투척 속도
                thrownTnt.setOwner(p.getUniqueId()); // 소유자 설정 (플러그인 로직용)

                // 엔티티 등록
                registerSummon(p, thrownTnt);

                // [Effect] 수류탄 심지 타는 효과 (3초간)
                BukkitTask fuseTask = new BukkitRunnable() {
                    int ticks = 0;

                    @Override
                    public void run() {
                        if (!thrownTnt.isValid()) {
                            this.cancel();
                            return;
                        }

                        // 심지 타는 연기 (SMOKE) + 소리 (FUSE)
                        thrownTnt.getWorld().spawnParticle(org.bukkit.Particle.SMOKE,
                                thrownTnt.getLocation().add(0, 0.5, 0), 2, 0.05, 0.05, 0.05, 0.02);

                        if (ticks % 5 == 0) { // 0.25초마다 치익 소리
                            thrownTnt.getWorld().playSound(thrownTnt.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f,
                                    1.5f);
                        }

                        ticks++;
                        if (ticks >= 60) { // 3초 도달
                            // 폭발 생성 (위력 4F = TNT 기본 위력)
                            thrownTnt.getWorld().createExplosion(thrownTnt.getLocation(), 4.0F, false, false, p);

                            // [Effect] 추가 폭발 이펙트 (폭발은 이미 기본 이펙트가 있지만 더 화려하게)
                            thrownTnt.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION,
                                    thrownTnt.getLocation(), 1);
                            thrownTnt.getWorld().spawnParticle(org.bukkit.Particle.LAVA,
                                    thrownTnt.getLocation(), 10, 1.0, 1.0, 1.0);

                            thrownTnt.remove();
                            this.cancel();
                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L);

                registerTask(p, fuseTask);
            }
        }
    }

    /**
     * 전화 찬스 로직 (40% 확률)
     * 
     * @return true면 전화 걸림(능력 불발), false면 정상 진행
     */
    private boolean tryPhoneCall(Player p) {
        if (random.nextInt(100) < 40) { // 40%
            // 메시지 출력
            MocPlugin.getInstance().getServer().broadcast(Component.text("§e땅콩 : 잠깐 전화좀..."));

            // [Effect] 전화 벨소리 (따르릉~ 따르릉~)
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 2.0f, 2.0f);

            // [Effect] 진동 느낌 파티클 (머리 위)
            // 반복해서 울리는 효과
            BukkitTask ringTask = new BukkitRunnable() {
                int count = 0;

                @Override
                public void run() {
                    if (count >= 5) { // 5번 반복 (2.5초 정도)
                        this.cancel();
                        return;
                    }
                    p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 2.0f, 2.0f);
                    p.getWorld().spawnParticle(org.bukkit.Particle.NOTE,
                            p.getLocation().add(0, 2.2, 0), 3, 0.3, 0.1, 0.3);
                    count++;
                }
            }.runTaskTimer(plugin, 0L, 10L); // 0.5초 간격

            registerTask(p, ringTask);

            // 구속 5, 저항 2 (5초)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 4));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1));

            // 5초 후 메시지 출력 태스크
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    MocPlugin.getInstance().getServer().broadcast(Component.text("§e땅콩 : 선임분께 다른 일 있다고 말해놨어..."));
                }
            }.runTaskLater(plugin, 100L); // 5초

            registerTask(p, task);

            return true;
        }
        return false;
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Snowball s) {
            if (s.getScoreboardTags().contains("DdangkongBullet")) {
                // 총알 데미지 10 고정
                e.setDamage(10.0);
            }
        }
    }

    // [다중 쿨타임 관리 시스템]
    // Ability 클래스의 cooldowns는 UUID만 키로 쓰므로,
    // 여기서는 별도의 맵으로 "UUID:TYPE" 형태의 키를 관리하거나
    // 내부 클래스로 관리해야 합니다.
    private final java.util.Map<String, Long> customCooldowns = new java.util.concurrent.ConcurrentHashMap<>();

    private void setCustomCooldown(Player p, String type, double seconds) {
        String key = p.getUniqueId().toString() + ":" + type;
        customCooldowns.put(key, System.currentTimeMillis() + (long) (seconds * 1000));
    }

    private boolean isCooldown(Player p, String type) {
        // 크리에이티브면 무시
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE)
            return false;

        String key = p.getUniqueId().toString() + ":" + type;
        if (!customCooldowns.containsKey(key))
            return false;

        return System.currentTimeMillis() < customCooldowns.get(key);
    }

    private long getCooldownTime(Player p, String type) {
        String key = p.getUniqueId().toString() + ":" + type;
        return Math.max(0, customCooldowns.get(key) - System.currentTimeMillis());
    }

    @Override
    public void reset() {
        super.reset();
        customCooldowns.clear();
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        // 플레이어별 쿨타임 키 제거
        if (p != null) {
            String uuidStr = p.getUniqueId().toString();
            customCooldowns.keySet().removeIf(k -> k.startsWith(uuidStr));
        }
    }
}
