package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import org.bukkit.Color;
import org.bukkit.Particle.DustOptions;

import java.util.Arrays;
import java.util.List;

import org.bukkit.GameMode;

public class MisakaMikoto extends Ability {

    // [NEW] 레일건 사용 횟수 카운트 (악용 방지)
    private final java.util.Map<java.util.UUID, Integer> railgunUseCount = new java.util.concurrent.ConcurrentHashMap<>();

    public MisakaMikoto(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "미사카 미코토";
    }

    @Override
    public String getCode() {
        return "034";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§e전투 ● §f미사카 미코토 (어떤 과학의 초전자포)",
                "§f레일건을 쏩니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 코인 10개 지급
        ItemStack coin = new ItemStack(Material.PRISMARINE_CRYSTALS, 10);
        ItemMeta meta = coin.getItemMeta();
        meta.setDisplayName("§b코인");
        meta.setLore(Arrays.asList("§7우클릭하여 레일건을 발사합니다.", "§8(소모품, 쿨타임 3초)"));
        meta.setCustomModelData(1); // 리소스팩: misakamikoto1
        coin.setItemMeta(meta);
        p.getInventory().addItem(coin);

        // [추가] 카운트 초기화
        railgunUseCount.put(p.getUniqueId(), 0);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 양손 이벤트 중복 방지
        if (e.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND)
            return;

        ItemStack item = e.getItem();
        if (item == null)
            return;

        // 1. 코인 사용 (레일건)
        if (item.getType() == Material.PRISMARINE_CRYSTALS) {
            if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                // 쿨타임 체크
                if (!checkCooldown(p))
                    return;
                setCooldown(p, 3); // 쿨타임 3초

                // 코인 1개 소모
                item.setAmount(item.getAmount() - 1);
                // 레일건 발사
                fireRailgun(p);
            }
        }

        // 2. 전력 사용 (네더의 별)
        if (item.getType() == Material.NETHER_STAR) {
            if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                if (item.getItemMeta() != null && "§e전력".equals(item.getItemMeta().getDisplayName())) {
                    // 쿨타임 체크 (코인 쿨타임과 공유됨)
                    if (!checkCooldown(p))
                        return;
                    setCooldown(p, 15); // 전력 쿨타임 15초

                    // 아이템 소모 코드 삭제됨 (무한 사용)
                    // item.setAmount(item.getAmount() - 1);

                    fireFullPower(p);
                }
            }
        }
    }

    // 일반 레일건
    private void fireRailgun(Player p) {
        Bukkit.broadcastMessage("§b미사카 미코토 : §f있지, 레일건이라는 말 알아?");
        p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f);

        // [▼▼▼ 파티클 완전 교체: 오류 방지 ▼▼▼]
        // CRIT 대신 WAX_ON (반짝이는 별) 사용 - 데이터 불필요, 안전함
        p.getWorld().spawnParticle(Particle.WAX_ON, p.getEyeLocation(), 10, 0.5, 0.5, 0.5, 0.1);

        var result = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 15, 0.5,
                e -> e instanceof LivingEntity && e != p
                        && !(e instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR));

        Vector dir = p.getEyeLocation().getDirection();
        for (int i = 0; i < 15; i++) {
            // END_ROD (흰색 막대) - 안전함
            p.getWorld().spawnParticle(Particle.END_ROD, p.getEyeLocation().add(dir.clone().multiply(i)), 1, 0, 0, 0,
                    0);
        }

        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            target.damage(8.0, p);
            // FLASH는 Color 데이터가 필수입니다.
            target.getWorld().spawnParticle(Particle.FLASH, target.getLocation(), 1, Color.YELLOW);
        }

        // [고도화] 스킬 사용 횟수 카운트 및 전력 지급
        int count = railgunUseCount.getOrDefault(p.getUniqueId(), 0) + 1;
        railgunUseCount.put(p.getUniqueId(), count);

        // 10회 사용 시 전력 지급 (기존 checkCoinsEmpty 대체)
        if (count >= 10) {
            p.sendMessage("§f코인을 모두 사용했습니다(10회). 15초 후 전력을 사용할 수 있습니다.");
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
                        ItemStack fullPower = new ItemStack(Material.NETHER_STAR);
                        ItemMeta meta = fullPower.getItemMeta();
                        meta.setDisplayName("§e전력");
                        meta.setCustomModelData(1); // 리소스팩: misakamikoto2
                        fullPower.setItemMeta(meta);
                        p.getInventory().addItem(fullPower);
                        p.sendMessage("§e[MOC] §f전력(네더의 별)이 활성화되었습니다!");
                        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
                    }
                }
            }.runTaskLater(plugin, 300L); // 15초 대기
        }
    }

    // 전력 발사
    private void fireFullPower(Player p) {
        Bukkit.broadcastMessage("§b미사카 미코토 : §e§l이게 나의 전력이다!!!");

        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 3) {
                    launchFullPowerProjectile(p);
                    cancel();
                    return;
                }

                p.getWorld().strikeLightningEffect(p.getLocation());
                // [안전한 파티클 유지] CRIT
                p.getWorld().spawnParticle(Particle.CRIT, p.getLocation(), 20, 1, 1, 1, 0.1);

                count++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // [▼▼▼ 새로운 메서드: 블레이즈 막대 투사체 발사 ▼▼▼]
    private void launchFullPowerProjectile(Player p) {
        // 1. 투사체(화살) 생성 - 중력 무시, 빠른 속도
        Arrow arrow = p.launchProjectile(Arrow.class);
        arrow.setShooter(p);
        arrow.setVelocity(p.getLocation().getDirection().multiply(8.0)); // 매우 빠름 (4.0 -> 8.0)
        arrow.setMetadata("MisakaFullPower", new FixedMetadataValue(plugin, true));
        arrow.setSilent(true);
        arrow.setGravity(false); // 직선으로 날아감

        // 2. 시각 효과 (블레이즈 막대) - ItemDisplay 사용
        ItemDisplay display = (ItemDisplay) p.getWorld().spawnEntity(p.getEyeLocation(), EntityType.ITEM_DISPLAY);
        display.setItemStack(new ItemStack(Material.BLAZE_ROD));
        // 화살에 탑승시킴 (같이 이동)
        arrow.addPassenger(display);

        // 3. 파티클 트레일 (노란색 전기) - Runnable로 따라가며 생성
        new BukkitRunnable() {
            @Override
            public void run() {
                // 화살이 사라지거나 땅에 박히면 종료 (이벤트에서 처리하지만 안전장치)
                if (arrow.isDead() || !arrow.isValid() || arrow.isOnGround()) {
                    display.remove(); // 디스플레이 제거
                    this.cancel();
                    return;
                }

                // 파티클 생성 (화살 위치)
                DustOptions electricOptions = new DustOptions(Color.YELLOW, 1.5f);
                // DUST (노랑) + WAX_ON (별)
                arrow.getWorld().spawnParticle(Particle.DUST, arrow.getLocation(), 5, 0.2, 0.2, 0.2, 0,
                        electricOptions);
                arrow.getWorld().spawnParticle(Particle.WAX_ON, arrow.getLocation(), 2, 0.1, 0.1, 0.1, 0);

                // [추가] 판정 범위 확대 (반경 3.0) 수동 충돌 체크
                // 화살의 히트박스는 작으므로, 주변 3칸 내에 적이 있으면 맞은 것으로 처리
                // (단, 본인은 제외)
                for (Entity target : arrow.getWorld().getNearbyEntities(arrow.getLocation(), 3.0, 3.0, 3.0)) {
                    if (target instanceof LivingEntity && target != p) {
                        if (target instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR)
                            continue;

                        // 충돌 처리 (폭발)
                        triggerFullPowerExplosion(arrow.getLocation(), p, arrow);

                        // 화살 및 디스플레이 제거 (이후 onProjectileHit은 호출되지 않거나, 호출되어도 유효하지 않음)
                        display.remove();
                        arrow.remove();
                        this.cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // 매 틱마다 실행
    }

    // [▼▼▼ 투사체 적중 이벤트: 번개 및 대미지 처리 ▼▼▼]
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (e.getEntity() instanceof Arrow arrow && arrow.hasMetadata("MisakaFullPower")) {
            // 이미 제거된 화살이면 무시
            if (!arrow.isValid())
                return;

            // 적중 위치 계산
            org.bukkit.Location hitLoc;
            if (e.getHitBlock() != null) {
                hitLoc = e.getHitBlock().getLocation().add(0.5, 1, 0.5);
            } else if (e.getHitEntity() != null) {
                hitLoc = e.getHitEntity().getLocation();
            } else {
                hitLoc = arrow.getLocation();
            }

            // 폭발 로직 실행
            if (arrow.getShooter() instanceof Player shooter) {
                triggerFullPowerExplosion(hitLoc, shooter, arrow);
            }

            // 화살 제거 (메서드 내에서 처리하지만 안전장치)
            if (!arrow.getPassengers().isEmpty()) {
                arrow.getPassengers().forEach(Entity::remove);
            }
            arrow.remove();
        }
    }

    // [분리] 전력 폭발 로직
    private void triggerFullPowerExplosion(org.bukkit.Location loc, Player shooter, Arrow arrow) {
        // 중복 폭발 방지 (이미 터진 화살인지 체크 - 여유가 있다면 메타데이터로 체크하겠지만,
        // remove()되면 isValid()가 false가 되므로 호출 측에서 방어)

        // 1. 번개 내리꽂기 (반드시!)
        loc.getWorld().strikeLightningEffect(loc);
        loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 2f, 1f);

        // 2. 광역 대미지 (기존 반경 2 -> 반경 5로 상향)
        for (Entity nearby : loc.getWorld().getNearbyEntities(loc, 5, 5, 5)) {
            if (nearby instanceof LivingEntity target && target != shooter) {
                if (target instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR)
                    continue;
                // 전력 데미지
                target.damage(28.0, shooter);
            }
        }

        // 3. 시각 효과 제거 (ItemDisplay)
        if (arrow != null && !arrow.getPassengers().isEmpty()) {
            arrow.getPassengers().forEach(Entity::remove);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p
                && AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode())) {
            if (e.getCause() == EntityDamageEvent.DamageCause.LIGHTNING) {
                e.setCancelled(true);
            }
        }
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● 미사카 미코토(어떤 과학의 초전자포)");
        p.sendMessage("§f레일건을 쏩니다.");
        p.sendMessage("§f코인 우클릭 시 1개를 소모하여 전면 직선으로 레일건을 발사합니다(사거리 15, 대미지 8).");
        p.sendMessage("§f레일건을 10회 모두 발사하면 15초 후 전력을 사용할 수 있습니다.");
        p.sendMessage("§f전력 우클릭 시 3초 차징 후 발리하여 강력한 대미지를 줍니다(사거리 50, 대미지 28).");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 3초 / 15초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 코인 10개, 전력(조건부)");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void reset() {
        railgunUseCount.clear();
        super.reset();
    }

    @Override
    public void cleanup(Player p) {
        railgunUseCount.remove(p.getUniqueId());
        super.cleanup(p);
    }
}