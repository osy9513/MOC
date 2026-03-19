package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.List;

public class Sasuke extends Ability {

    public Sasuke(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "074";
    }

    @Override
    public String getName() {
        return "사스케";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c전투 ● 사스케(나루토)",
                "§f화둔 호화구의 술를 사용합니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 장비 없음
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 사스케(나루토)");
        p.sendMessage("§f맨손 쉬프트 좌클릭 시 전방에 호화구의 술을 3번 시전하여");
        p.sendMessage("§f맞은 대상에게 10의 데미지를 주며 10초간 불에 탑니다.");
        p.sendMessage("§f호화구의 술을 하늘을 향해 시전할 경우, 10% 확률로 비가 옵니다.");
        p.sendMessage("§f비가 올 땐 호화구의 술을 시전할 수 없으며");
        p.sendMessage("§f대신 맨손 쉬프트 좌클릭으로 적을 타격 시");
        p.sendMessage("§f해당 적에게 뇌둔 기린을 시전하여 8데미지를 가진 무적 무시 번개를 5번 떨어트립니다.");
        p.sendMessage("§f뇌둔 기린의 번개가 떨어지면 날씨가 맑아집니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 16초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    // 허공을 클릭하여 화둔을 시전할 때 처리
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        if (isSilenced(p))
            return;
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        // 맨손, 웅크리기, 좌클릭 확인
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK)
            return;
        if (!p.isSneaking())
            return;
        if (p.getInventory().getItemInMainHand().getType() != Material.AIR)
            return;

        // 비 올 때는 허공 클릭(화둔) 발동 불가
        World world = p.getWorld();
        if (world.hasStorm())
            return;

        if (!checkCooldown(p))
            return;

        // 쿨타임 부여 (기린과 화둔 공유)
        setCooldown(p, 16);

        // 화둔 발동
        castFireball(p);
    }

    // 대상을 직접 타격했을 때 처리 (비 올때 기린 기믹, 비 안올때는 화둔 처리)
    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p))
            return;
        if (!(e.getEntity() instanceof LivingEntity target))
            return;

        // 관전자 타겟팅 제외
        if (target instanceof Player tp && tp.getGameMode() == GameMode.SPECTATOR)
            return;

        if (isSilenced(p))
            return;
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        // 맨손, 웅크리기 확인
        if (!p.isSneaking())
            return;
        if (p.getInventory().getItemInMainHand().getType() != Material.AIR)
            return;

        // 비 올 때 -> 뇌둔 기린 발동
        World world = p.getWorld();
        if (world.hasStorm()) {
            if (!checkCooldown(p))
                return;

            setCooldown(p, 16);
            castKirin(p, target);
        } else {
            // 맑을 때 대상을 때려도 화둔이 나감
            if (!checkCooldown(p))
                return;

            setCooldown(p, 16);
            castFireball(p);
        }
    }

    // 투사체(화둔) 적중 처리
    @EventHandler
    public void onProjectileHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof SmallFireball fireball) {
            if (fireball.getShooter() instanceof Player shooter) {
                if (AbilityManager.getInstance().hasAbility(shooter, getCode())) {
                    if (e.getEntity() instanceof LivingEntity target) {
                        // 10 데미지 고정 피해 및 10초 화염
                        target.setMetadata("MOC_LastKiller",
                                new FixedMetadataValue(plugin, shooter.getUniqueId().toString()));
                        e.setDamage(10.0);
                        target.setFireTicks(200); // 10초 * 20틱
                    }
                }
            }
        }
    }

    private void castFireball(Player p) {
        Bukkit.broadcastMessage("§c사스케: §f화둔 호화구의 술!");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1f, 1f);

        // 1초에 걸쳐 총 3개의 화염구 발사
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 3 || !p.isOnline()) {
                    this.cancel();
                    return;
                }

                Location eyeLoc = p.getEyeLocation();
                Vector direction = eyeLoc.getDirection().normalize();

                // SmallFireball 발사
                SmallFireball fireball = p.getWorld().spawn(eyeLoc.add(direction.clone().multiply(1.5)),
                        SmallFireball.class);
                fireball.setShooter(p);
                fireball.setVelocity(direction.clone().multiply(1.5)); // 속도 조절
                fireball.setYield(0); // 폭발 파괴 끔
                fireball.setIsIncendiary(false); // 블럭에 불붙기 끔

                registerSummon(p, fireball);

                // 파티클 (궤적) 생성 및 높이 도달(Y+32) 확인 시스템
                new BukkitRunnable() {
                    int aliveTicks = 0; // 생존 시간 체크

                    @Override
                    public void run() {
                        if (fireball.isDead() || !fireball.isValid()) {
                            this.cancel();
                            return;
                        }

                        // 10초(200틱) 경과 시 자동 소멸 (최적화 위함)
                        if (aliveTicks >= 200) {
                            fireball.remove();
                            this.cancel();
                            return;
                        }

                        Location loc = fireball.getLocation();
                        World w = loc.getWorld();

                        // 화염구 궤적 파티클
                        w.spawnParticle(Particle.FLAME, loc, 5, 0.2, 0.2, 0.2, 0.05);

                        // 중앙 에메랄드 탐색 (매번하면 무거울 수 있지만 y좌표 찾기 위함, ArenaManager를 직접 못쓰니)
                        Location center = w.getWorldBorder().getCenter();
                        int emeraldY = -999;
                        for (int y = w.getMaxHeight(); y > w.getMinHeight(); y--) {
                            if (w.getBlockAt(center.getBlockX(), y, center.getBlockZ())
                                    .getType() == Material.EMERALD_BLOCK) {
                                emeraldY = y;
                                break;
                            }
                        }

                        if (emeraldY != -999) {
                            if (loc.getY() >= emeraldY + 32) {
                                // 도달 완료 - 화염구 소멸 후 확률 체크
                                fireball.remove();

                                // 10% 확률 폭우
                                if (Math.random() < 0.10) {
                                    if (!w.hasStorm()) {
                                        w.setStorm(true);
                                        // 넉넉하게 폭우 지속
                                        w.setWeatherDuration(6000);
                                        Bukkit.broadcastMessage("§b[!] 폭우가 쏟아집니다...");
                                    }
                                }
                                this.cancel();
                            }
                        }

                        aliveTicks++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);

                count++;
            }
        }.runTaskTimer(plugin, 0L, 6L); // 총 3발, 6틱 간격 (약 18틱 = 약 1초)
    }

    private void castKirin(Player p, LivingEntity target) {
        Bukkit.broadcastMessage("§c사스케: 술법의 이름은 기린. 뇌명과 함께 사라져라...!!");

        // 발광 3초 부여
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, true));

        // 3초간 1초마다 무피해 번개 효과
        new BukkitRunnable() {
            int timer = 0;

            @Override
            public void run() {
                if (timer >= 3 || !p.isOnline()) {
                    this.cancel();

                    // 3초 후 기린(타겟 피격) 시전
                    if (target.isValid() && !target.isDead()) {
                        strikeKirin(p, target);
                    }
                    return;
                }

                // 타겟 상공에 무피해 번개 (번개 효과음)
                Location strikeLoc = p.getLocation().clone().add(Math.random() * 4 - 2, 0, Math.random() * 4 - 2);
                p.getWorld().strikeLightningEffect(strikeLoc);

                timer++;
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1초(20틱)마다 실행
    }

    private void strikeKirin(Player p, LivingEntity target) {
        // 1.2초(24틱) 동안 5번 떨어트림 -> 약 5틱 간격
        new BukkitRunnable() {
            int strikes = 0;

            @Override
            public void run() {
                if (strikes >= 5 || !target.isValid() || target.isDead()) {
                    // 번개 종료 후 날씨 맑아짐
                    target.getWorld().setStorm(false);
                    target.getWorld().setThundering(false);
                    this.cancel();
                    return;
                }

                Location loc = target.getLocation();
                target.getWorld().strikeLightningEffect(loc);

                // 8 고정 데미지 + 무적시간 리셋
                target.setNoDamageTicks(0);

                // 킬 메타데이터
                target.setMetadata("MOC_LastKiller", new FixedMetadataValue(plugin, p.getUniqueId().toString()));

                // 즉사 처리 주의 로직 포함하여 데미지 8 부여
                double hitHealth = target.getHealth() - 8.0;
                if (hitHealth <= 0) {
                    target.setHealth(0);
                } else {
                    target.damage(8.0);
                }

                strikes++;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }
}
