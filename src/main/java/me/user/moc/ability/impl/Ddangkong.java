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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Ddangkong extends Ability {

    private final Random random = new Random();
    // 전화 통화 중인 플레이어 목록 (Thread-safe Set)
    private final Set<UUID> isCalling = ConcurrentHashMap.newKeySet();

    public Ddangkong(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "H04";
    }

    @Override
    public String getName() {
        return "땅콩";// 연준이
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
        bulletMeta.setLore(Arrays.asList("§7우클릭하여 발사합니다.", "§8(원거리 데미지 10)", "§8(근접 데미지 3)"));
        bulletMeta.setCustomModelData(2); // 리소스팩: ddangkong1
        bullet.setItemMeta(bulletMeta);

        // 훈련용 수류탄 (TNT)
        ItemStack grenade = new ItemStack(Material.TNT);
        ItemMeta grenadeMeta = grenade.getItemMeta();
        grenadeMeta.displayName(Component.text("§c훈련용 수류탄"));
        grenadeMeta.setLore(Arrays.asList("§7우클릭하여 투척합니다.", "§8(3초 후 폭발)"));
        grenadeMeta.setCustomModelData(1); // 리소스팩: ddangkong2
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
        p.sendMessage(" ");
        p.sendMessage("§f총알과 수류탄 능력 발동 중 40% 확률로 선임에게 전화가 옵니다.");
        p.sendMessage("§f전화가 올 경우 시전한 능력이 불발되고, 10초간 구속 5, 저항 2가 걸립니다.");
        p.sendMessage("§f또한 통화 중에는 능력을 사용할 수 없습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 총알 쿨타임 5초, 수류탄 쿨타임 10초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 총알, 수류탄");
        p.sendMessage("§f장비 제거 : 없음");
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

            // 전화 통화 중 체크
            if (isCalling.contains(p.getUniqueId())) {
                p.sendActionBar(Component.text("§c[!] 선임과 통화 중이라 능력을 사용할 수 없습니다!"));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }

            // 1. 총알 (폭죽 탄약)
            if (item.getType() == Material.FIREWORK_STAR) {
                e.setCancelled(true); // 폭죽 탄약 설치/사용 방지

                if (isCooldown(p, "BULLET")) {
                    long left = getCooldownTime(p, "BULLET");
                    p.sendActionBar(Component.text("§c총알 쿨타임: " + String.format("%.1f", (left / 1000.0)) + "초"));
                    return;
                }

                // 전화 찬스 (40%)
                if (tryPhoneCall(p)) {
                    return; // 전화 걸리면 능력 불발
                }

                // 총알 발사 로직
                setCustomCooldown(p, "BULLET", 5); // 5초 쿨타임

                // 발사체 생성 (Snowball)
                Snowball projectile = p.launchProjectile(Snowball.class);
                projectile.setItem(new ItemStack(Material.FIREWORK_STAR)); // 탄약 모양
                projectile.setGravity(false); // 중력 무시 (직선)
                projectile.setVelocity(p.getLocation().getDirection().multiply(3.0)); // 화살 속도 (약 3.0)
                projectile.setShooter(p);

                // 메타데이터로 총알 식별
                projectile.addScoreboardTag("DdangkongBullet");

                // [Effect] 발사 소리 및 이펙트 (강렬하게)
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 3.0f, 1.5f);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2.0f);
                // [Fix] FLASH -> EXPLOSION (FLASH requires data in some versions)
                p.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, p.getEyeLocation(), 1);

                // [Effect] 총알 트레일 (회오리 느낌이나 잔상)
                BukkitTask trailTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (projectile.isValid() && !projectile.isOnGround()) {
                            // 연기와 크리티컬 매직 입자
                            projectile.getWorld().spawnParticle(org.bukkit.Particle.CLOUD,
                                    projectile.getLocation(), 2, 0.05, 0.05, 0.05, 0.01);
                            projectile.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                                    projectile.getLocation(), 3, 0.1, 0.1, 0.1, 0.1);
                            projectile.getWorld().spawnParticle(org.bukkit.Particle.SMOKE,
                                    projectile.getLocation(), 1, 0, 0, 0, 0);
                        } else {
                            this.cancel();
                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L);
                registerTask(p, trailTask); // Cleanup에 등록
                registerSummon(p, projectile); // 엔티티 등록
            }

            // 2. 수류탄 (TNT)
            else if (item.getType() == Material.TNT) {
                e.setCancelled(true); // 설치 방지

                if (isCooldown(p, "GRENADE")) {
                    long left = getCooldownTime(p, "GRENADE");
                    p.sendActionBar(Component.text("§c수류탄 쿨타임: " + String.format("%.1f", (left / 1000.0)) + "초"));
                    return;
                }

                // 전화 찬스 (40%)
                if (tryPhoneCall(p)) {
                    return;
                }

                setCustomCooldown(p, "GRENADE", 10); // 10초 쿨타임

                // TNT 아이템 던지기 (리소스팩 적용)
                ItemStack tntItem = new ItemStack(Material.TNT);
                ItemMeta tntMeta = tntItem.getItemMeta();
                tntMeta.setCustomModelData(1); // 리소스팩: ddangkong2
                tntItem.setItemMeta(tntMeta);

                Item thrownTnt = p.getWorld().dropItem(p.getEyeLocation(), tntItem);
                thrownTnt.setPickupDelay(Integer.MAX_VALUE); // 줍기 방지
                thrownTnt.setVelocity(p.getLocation().getDirection().multiply(1.5)); // 적절한 투척 속도
                thrownTnt.setOwner(p.getUniqueId()); // 소유자 설정

                // [Effect] 던지는 소리
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_EGG_THROW, 1.0f, 0.5f);

                registerSummon(p, thrownTnt);

                // [Effect] 수류탄 효과 (3초간)
                BukkitTask fuseTask = new BukkitRunnable() {
                    int ticks = 0;

                    @Override
                    public void run() {
                        if (!thrownTnt.isValid()) {
                            this.cancel();
                            return;
                        }

                        // 심지 타는 연기 (SMOKE_LARGE) + 불꽃 (FLAME)
                        thrownTnt.getWorld().spawnParticle(org.bukkit.Particle.SMOKE,
                                thrownTnt.getLocation().add(0, 0.5, 0), 3, 0.1, 0.1, 0.1, 0.05);
                        thrownTnt.getWorld().spawnParticle(org.bukkit.Particle.FLAME,
                                thrownTnt.getLocation().add(0, 0.5, 0), 1, 0.05, 0.05, 0.05, 0.02);

                        // 경고음 (점점 빨라지게)
                        if (ticks % (Math.max(2, 10 - (ticks / 5))) == 0) {
                            thrownTnt.getWorld().playSound(thrownTnt.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.0f,
                                    1.0f + (ticks / 30.0f)); // 피치 상승
                        }

                        ticks++;
                        if (ticks >= 60) { // 3초 도달
                            // 폭발 생성
                            thrownTnt.getWorld().createExplosion(thrownTnt.getLocation(), 4.0F, false, false, p);

                            // [Effect] 대형 폭발 이펙트 (화려하게)
                            thrownTnt.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER,
                                    thrownTnt.getLocation(), 3); // 거대 폭발
                            thrownTnt.getWorld().spawnParticle(org.bukkit.Particle.LAVA,
                                    thrownTnt.getLocation(), 20, 1.5, 1.5, 1.5);
                            thrownTnt.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION,
                                    thrownTnt.getLocation(), 1);

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
            // 전화 상태 설정
            UUID uuid = p.getUniqueId();
            isCalling.add(uuid);

            // 메시지 출력
            MocPlugin.getInstance().getServer().broadcast(Component.text("§e땅콩 : 잠깐 전화좀... (선임 호출)"));
            p.sendTitle("§c[ CALLING ]", "§7선임에게 전화가 왔습니다...", 10, 60, 20);

            // [Effect] 전화 벨소리 (강렬하게) + 토스트 알림 소리
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 2.0f, 2.0f);
            p.getWorld().playSound(p.getLocation(), Sound.UI_TOAST_IN, 1.0f, 1.0f);

            // [Effect] 머리 위 스트레스 표시 (Villager Angry)
            BukkitTask stressTask = new BukkitRunnable() {
                int count = 0;

                @Override
                public void run() {
                    // count는 0.5초마다 증가 (10L = 0.5s)
                    // 10초 = 20회 반복이지만, 넉넉하게 체크하고 isCalling 여부로 종료
                    if (!isCalling.contains(uuid) || !p.isOnline()) {
                        this.cancel();
                        return;
                    }

                    p.getWorld().spawnParticle(org.bukkit.Particle.ANGRY_VILLAGER,
                            p.getEyeLocation().add(0, 0.5, 0), 3, 0.3, 0.1, 0.3);
                    // 주기적으로 벨소리
                    if (count % 2 == 0) {
                        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 2.0f, 2.0f);
                    }
                    count++;
                }
            }.runTaskTimer(plugin, 0L, 10L); // 0.5초 간격
            registerTask(p, stressTask);

            // 구속 5, 저항 2 (10초 = 200 ticks)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 4));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1));
            // 추가: 시야 흔들림 (멀미) 짧게 줘서 당황 효과
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 1));

            // 10초 후 전화 종료 및 상태 해제
            BukkitTask endTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (isCalling.contains(uuid)) {
                        isCalling.remove(uuid);
                        if (p.isOnline()) {
                            MocPlugin.getInstance().getServer().broadcast(Component.text("§e땅콩 : 휴... 끊었다..."));
                            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f); // 안도감
                        }
                    }
                }
            }.runTaskLater(plugin, 200L); // 10초

            registerTask(p, endTask);

            return true;
        }
        return false;
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        // 1. 원거리 공격 (눈덩이)
        if (e.getDamager() instanceof Snowball s) {
            if (s.getScoreboardTags().contains("DdangkongBullet")) {
                // 총알 데미지 10 고정
                e.setDamage(10.0);

                // [Effect] 히트 시 추가 효과
                e.getEntity().getWorld().spawnParticle(org.bukkit.Particle.BLOCK,
                        e.getEntity().getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2,
                        Material.REDSTONE_BLOCK.createBlockData());
                e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
            }
        }
        // 2. 근접 공격 (좌클릭)
        else if (e.getDamager() instanceof Player p) {
            if (AbilityManager.getInstance().hasAbility(p, getCode())) {
                ItemStack item = p.getInventory().getItemInMainHand();
                if (item.getType() == Material.FIREWORK_STAR && item.getItemMeta() != null
                        && item.getItemMeta().getDisplayName().equals("§c총알")) {
                    // 기본 데미지(주먹 1) + 추가 데미지 3 = 4 (하트 2칸)
                    // 기획: "데미지 3 추가" -> 3으로 고정할지, 추가할지? 보통 "추가"면 +3.
                    // 기존 코드 스타일 보면 setDamage를 주로 씀.
                    // 주먹 데미지가 1이므로, 3을 주고 싶으면 setDamage(3) 혹은 addModifier.
                    // "좌클릭 데미지 3 추가" -> 기본 1 + 3 = 4? 아니면 그냥 3?
                    // 보통 "데미지 3"이라고 하면 setDamage(3). "추가"면 +3.
                    // 요청사항: "땅콩 총알 좌클릭 데미지 3 추가" -> Add 3 damage.
                    // 명확하지 않지만, 보통 무기 데미지로 3을 원함. (목검 4, 돌검 5...)
                    // 총알이니까 약하게 3(하트 1.5칸) 정도로 설정.
                    e.setDamage(3.0);
                }
            }
        }
    }

    // [다중 쿨타임 관리 시스템]
    private final java.util.Map<String, Long> customCooldowns = new java.util.concurrent.ConcurrentHashMap<>();

    private void setCustomCooldown(Player p, String type, double seconds) {
        String key = p.getUniqueId().toString() + ":" + type;
        customCooldowns.put(key, System.currentTimeMillis() + (long) (seconds * 1000));
    }

    private boolean isCooldown(Player p, String type) {
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
        isCalling.clear(); // 전화 상태 초기화
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        if (p != null) {
            String uuidStr = p.getUniqueId().toString();
            customCooldowns.keySet().removeIf(k -> k.startsWith(uuidStr));
            isCalling.remove(p.getUniqueId()); // 플레이어 퇴장/능력 해제 시 전화 상태 제거
        }
    }
}
