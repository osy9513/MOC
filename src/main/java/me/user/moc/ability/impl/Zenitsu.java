package me.user.moc.ability.impl;

import java.util.List;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;

public class Zenitsu extends Ability {

    public Zenitsu(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "021";
    }

    @Override
    public String getName() {
        return "아가츠마 젠이츠";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§e전투 ● 아가츠마 젠이츠(귀멸의 칼날)",
                "§e우클릭 : 벽력일섬(霹靂一閃)§f을 시전합니다.",
                "§e웅크리기 + 우클릭 : 벽력일섬 육연(霹靂一閃 六連)§f을 시전합니다.");
    }

    @Override
    public void giveItem(Player p) {
        p.getInventory().remove(org.bukkit.Material.IRON_SWORD);
        ItemStack sword = new ItemStack(org.bukkit.Material.IRON_SWORD);
        org.bukkit.inventory.meta.ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e일륜도");
            meta.setLore(List.of("§7우클릭 시 벽력일섬을 사용합니다.", "§7웅크리고 우클릭 시 벽력일섬 육연을 사용합니다."));
            meta.setCustomModelData(9); // 리소스팩: zenitsu
            sword.setItemMeta(meta);
        }
        p.getInventory().addItem(sword);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● 아가츠마 젠이츠(귀멸의 칼날)");
        p.sendMessage("§f검을 우클릭하면 '벽력일섬'을 시전하여 전방으로 8칸 순간이동합니다.");
        p.sendMessage("§f경로상의 적에게 8의 고정 피해를 입힙니다.");
        p.sendMessage("§f웅크리고 우클릭하면 '벽력일섬 육연'을 시전합니다.");
        p.sendMessage("§f전방으로 짧게 6번 연속 돌진하며 광역 피해를 입힙니다.");
        // p.sendMessage("§4※주의※ 바닥도 뚫고 갑니다."); // 버그 수정으로 제거
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 8초 (육연 사용 시 12초)");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 1. 내 능력인지 확인
        if (!AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 2. 우클릭 & 검 확인
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack hand = e.getItem();
        if (hand == null || !hand.getType().name().endsWith("_SWORD"))
            return;

        // 3. 쿨타임 확인
        if (!checkCooldown(p))
            return;

        // 4. 스킬 시전 (Shift 여부에 따라 분기)
        if (p.isSneaking()) {
            useSixfold(p);
        } else {
            useThunderclapAndFlash(p);
        }
    }

    private void useThunderclapAndFlash(Player p) {
        setCooldown(p, 8);
        p.getServer().broadcastMessage("§e아가츠마 젠이츠 : 벽력일섬(霹靂一閃).");

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 1.2f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2.0f);

        Location startLoc = p.getLocation();
        Vector dir = startLoc.getDirection().normalize();
        double maxDistance = 8.0;

        RayTraceResult result = p.getWorld().rayTraceBlocks(
                p.getEyeLocation(),
                dir,
                maxDistance,
                FluidCollisionMode.NEVER,
                true);

        Location targetLoc;
        if (result != null && result.getHitBlock() != null) {
            Vector hitPos = result.getHitPosition();
            targetLoc = hitPos.toLocation(p.getWorld()).subtract(dir.clone().multiply(0.5));
            targetLoc.setYaw(startLoc.getYaw());
            targetLoc.setPitch(startLoc.getPitch());
        } else {
            targetLoc = startLoc.clone().add(dir.clone().multiply(maxDistance));
        }

        // 안전한 위치 보정
        targetLoc = getSafeDestination(targetLoc);

        p.teleport(targetLoc);
        p.getWorld().strikeLightning(startLoc);

        // 지그재그 파티클
        drawLightning(p, startLoc.add(0, 1, 0), targetLoc.add(0, 1, 0));

        // 피해 입히기
        BoundingBox box = BoundingBox.of(startLoc, targetLoc).expand(1.5, 2.0, 1.5);
        for (Entity entity : p.getWorld().getNearbyEntities(box)) {
            if (entity == p)
                continue;
            if (!(entity instanceof LivingEntity))
                continue;

            if (entity instanceof Player && ((Player) entity).getGameMode() == org.bukkit.GameMode.SPECTATOR)
                continue;

            LivingEntity target = (LivingEntity) entity;
            target.damage(8.0, p);
            p.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10);
            // [Fix] Particle.FLASH가 데이터 클래스(Color)를 요구한다는 오류가 있어 EXPLOSION으로 대체
            p.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation().add(0, 1, 0), 1);
        }

        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, 1, true, true, true)); // Speed 2
    }

    private void useSixfold(Player p) {
        setCooldown(p, 12);
        // Stylized RP Message
        p.getServer().broadcastMessage("§e――――――――――――――――――――――――――――――――");
        p.getServer().broadcastMessage("§e⚡ §l아가츠마 젠이츠 §e: §f벽력일섬(霹靂一閃)... §6§l육연(六連)!!!");
        p.getServer().broadcastMessage("§e――――――――――――――――――――――――――――――――");

        // 6번 연속 대시 (Scheduler 사용)
        for (int i = 0; i < 6; i++) {
            final int count = i;
            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                if (!p.isOnline() || p.isDead())
                    return;

                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f,
                        1.5f + (count * 0.1f));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);

                Location start = p.getLocation();
                Vector dashDir = start.getDirection().normalize();

                // 육연은 짧게 이동 (4칸)
                double dashDist = 4.0;
                RayTraceResult res = p.getWorld().rayTraceBlocks(p.getEyeLocation(), dashDir, dashDist,
                        FluidCollisionMode.NEVER, true);

                Location dest;
                if (res != null && res.getHitBlock() != null) {
                    dest = res.getHitPosition().toLocation(p.getWorld()).subtract(dashDir.clone().multiply(0.5));
                    dest.setYaw(start.getYaw());
                    dest.setPitch(start.getPitch());
                } else {
                    dest = start.clone().add(dashDir.clone().multiply(dashDist));
                }

                // 안전한 위치 보정
                dest = getSafeDestination(dest);

                p.teleport(dest);

                // 파티클
                drawLightning(p, start.add(0, 0.5, 0), dest.add(0, 0.5, 0));

                // 데미지 (회당 3 데미지 x 6회 = 18)
                BoundingBox box = BoundingBox.of(start, dest).expand(1.0, 1.5, 1.0);
                for (Entity e : p.getWorld().getNearbyEntities(box)) {
                    if (e == p)
                        continue;
                    if (e instanceof LivingEntity le) {
                        if (le instanceof Player && ((Player) le).getGameMode() == org.bukkit.GameMode.SPECTATOR)
                            continue;
                        le.damage(3.0, p);
                        le.setNoDamageTicks(0); // 연속 타격 가능하게
                    }
                }
            }, i * 10L); // 10틱 간격 (0.5초)
        }

        // 마지막 피니시 효과 (6회 * 10틱 = 60틱 후)
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 1.0f);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

            // 번개 소환 (Visual Only - 데미지 없음)
            p.getWorld().strikeLightningEffect(p.getLocation());

            // 피니시 광역 데미지 (자신 제외)
            for (Entity e : p.getNearbyEntities(4, 4, 4)) {
                if (e instanceof LivingEntity le && e != p) {
                    if (le instanceof Player && ((Player) le).getGameMode() == org.bukkit.GameMode.SPECTATOR)
                        continue;
                    le.damage(5.0, p); // 마무리 5뎀
                    le.getWorld().spawnParticle(Particle.EXPLOSION, le.getLocation().add(0, 1, 0), 1);
                }
            }
            p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation().add(0, 1, 0), 1);
        }, 60L);
    }

    private Location getSafeDestination(Location loc) {
        // 발 위치가 고체 블록이면 위로 올림
        if (loc.getBlock().getType().isSolid()) {
            return loc.add(0, 1, 0);
        }
        return loc;
    }

    private void drawLightning(Player p, Location start, Location end) {
        double distance = start.distance(end);
        Vector dir = end.clone().subtract(start).toVector().normalize();

        // 메인 빛줄기 (노란색)
        for (double d = 0; d < distance; d += 0.2) {
            Location point = start.clone().add(dir.clone().multiply(d));
            // 무작위 오차를 주어 지그재그 효과 연출
            double offset = 0.3;
            point.add((Math.random() - 0.5) * offset, (Math.random() - 0.5) * offset, (Math.random() - 0.5) * offset);

            p.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, new Particle.DustOptions(Color.YELLOW, 1.0f));
        }

        // 잔상 (흰색)
        for (double d = 0; d < distance; d += 0.5) {
            Location point = start.clone().add(dir.clone().multiply(d));
            p.getWorld().spawnParticle(Particle.DUST, point, 1, 0.1, 0.1, 0.1,
                    new Particle.DustOptions(Color.WHITE, 0.5f));
        }
    }
}
