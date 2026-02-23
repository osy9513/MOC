package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class Magnus extends Ability {

    public Magnus(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "004";
    }

    @Override
    public String getName() {
        return "매그너스";
    }

    @Override
    public List<String> getDescription() {
        return List.of(
                "§e복합 ● 매그너스(이터널 리턴)",
                "진정한 사나이의 질주를 시작한다.");
    }

    @Override
    public void giveItem(Player p) {
        ItemStack item = new ItemStack(Material.MINECART);
        var meta = item.getItemMeta();
        meta.setDisplayName("§c오토바이");
        meta.setLore(List.of("§7우클릭 시 탑승하여 전방으로 질주합니다.", "§7하차하거나 충돌 시 폭발합니다.", "§8(쿨타임 20초)"));
        meta.setCustomModelData(1); // 리소스팩: magnus
        item.setItemMeta(meta);
        p.getInventory().addItem(item);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e복합 ● 매그너스(이터널 리턴)");
        p.sendMessage("§f우클릭 시 10초간 오토바이를 탑승하여 질주합니다.");
        p.sendMessage("§f점점 빠른 속도로 이동하며, 마우스로 방향을 조절합니다.");
        p.sendMessage("§f충돌 시 또는 하차 후 1초 뒤 폭발하여 강력한 광역 피해를 줍니다.");
        p.sendMessage("§f본인은 폭발 대미지를 입지 않습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 20초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 오토바이");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!hasAbility(p))
            return;

        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack hand = e.getItem();
            if (hand != null && hand.getType() == Material.MINECART) {
                e.setCancelled(true); // 기본 설치 방지

                if (!checkCooldown(p))
                    return;

                // 오토바이 소환
                useBike(p);
                setCooldown(p, 20);
            }
        }
    }

    private void useBike(Player p) {
        // 메시지 및 사운드
        plugin.getServer().broadcastMessage("§e매그너스 : 진정한 바이커의 질주를 보여주지!");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_MINECART_RIDING, 2f, 0.5f);

        // 오토바이(광산 수레) 소환
        Minecart bike = p.getWorld().spawn(p.getLocation(), Minecart.class);
        bike.setInvulnerable(true); // 무적 설정

        // [긴급 수정] 맨땅 주행 속도 관련 물리 설정 해제
        // 1. [속도 조절 1] 최대 속도 제한 (기본 0.4). 이 값을 낮추면 아무리 빨라도 이 속도를 넘지 못합니다.
        bike.setMaxSpeed(4.0);
        // 2. 탈선(맨땅) 시 감속 계수 조정 (1.0 = 감속 없음/속도 유지, 1.0보다 크면 가속됨)
        // 기존 10.0은 하차 시 급가속 버그 유발 -> 1.0으로 수정하여 마찰만 제거
        bike.setDerailedVelocityMod(new Vector(1.0, 1.0, 1.0));

        bike.addPassenger(p);

        // 엔티티 관리 등록
        registerSummon(p, bike);

        // 오토바이 운전 태스크
        new BukkitRunnable() {
            int tick = 0;
            final int maxTick = 200; // 10초

            @Override
            public void run() {
                // 1. 유효성 검사 (죽거나, 수레가 없거나, 내렸거나)
                if (bike.isDead() || !bike.isValid() || bike.getPassengers().isEmpty()) {
                    this.cancel();
                    // 내린 후 처리는 이벤트나 로직 흐름상 아래에서 처리됨 (여기선 강제 종료 시)
                    if (!bike.isDead())
                        processDismount(p, bike);
                    return;
                }

                // 2. 시간 초과
                if (tick >= maxTick) {
                    bike.removePassenger(p); // 강제 하차 -> ExitEvent 유도
                    this.cancel();
                    return;
                }

                // 3. 이동 로직 [가속 제거 - 상시 최대 속도]
                // [속도 조절 2] 땅 위에서 달리는 실제 속도입니다. (현재 0.1)
                // 너무 빠르면 이 값을 0.05 단위로, 너무 느리면 0.1 단위로 올려보세요.
                double speed = 0.8;

                Location loc = p.getLocation();
                Vector dir = loc.getDirection().multiply(speed);
                dir.setY(bike.getVelocity().getY()); // 중력 유지

                bike.setVelocity(dir);

                // [추가] 배기구 연기 효과
                // 요청: 연기 위치 반대로 (앞에서 나온다고 함 -> 뒤로 가도록 좌표 수정?)
                // 기존(-1)이 앞에서 나온다고 느꼈다면, 반대인 (+1) 쪽이나 아예 뒤로 더 밀어야 함.
                // 일단 사용자가 '반대로'를 원했으니 부호를 바꿈. (혹은 좌표 기준이 달랐을 수 있음)
                // 하지만 상식적으로 '배기구'는 뒤편임. 마인카트 뒤쪽으로 확실히 밀어주기 위해 -1.5 사용.
                // (만약 그래도 앞에서 나오면 마인카트가 뒤로 가는 중인 것)
                Vector backDir = loc.getDirection().normalize().multiply(-1.5); // 거리를 더 벌림
                Location particleLoc = bike.getLocation().add(backDir).add(0, 0.2, 0); // 높이도 낮춤

                // [수정] CAMPFIRE_SIGNAL_SMOKE는 너무 오래 남음 -> LARGE_SMOKE로 변경 (금방 사라짐)
                p.getWorld().spawnParticle(org.bukkit.Particle.LARGE_SMOKE, particleLoc, 3, 0.1, 0.1, 0.1, 0.05);

                // 효과음
                if (tick % 5 == 0) {
                    // 고속 주행음 (높은 피치 유지)
                    p.getWorld().playSound(bike.getLocation(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, 2.0f, 2.0f);
                }

                // 4. 충돌 감지 (벽이나 엔티티)
                // [수정] 동일한 Y축 내에서만 충돌 판정 (공중 소환 시 즉시 폭발 방지)
                Vector checkDir = dir.clone().setY(0).normalize();
                if (!bike.getLocation().add(checkDir).getBlock().isPassable()) {
                    bike.removePassenger(p);
                    explode(p, bike);
                    this.cancel();
                    return;
                }

                // 엔티티 충돌은 Bukkit Event로 잡기 어렵거나 느리므로, 근처 엔티티 직접 검사
                for (org.bukkit.entity.Entity e : bike.getNearbyEntities(0.8, 0.8, 0.8)) {
                    if (e != p && e instanceof org.bukkit.entity.LivingEntity) {
                        if (e instanceof Player && ((Player) e).getGameMode() == org.bukkit.GameMode.SPECTATOR)
                            continue;
                        bike.removePassenger(p);
                        explode(p, bike);
                        this.cancel();
                        return;
                    }
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 하차 이벤트 감지
     */
    @EventHandler
    public void onExit(VehicleExitEvent e) {
        if (e.getExited() instanceof Player p && hasAbility(p)) {
            if (e.getVehicle() instanceof Minecart bike) {
                // 내 능력이 만든 바이크인지 확인 (activeEntities에 있는지)
                if (activeEntities.containsKey(p.getUniqueId()) && activeEntities.get(p.getUniqueId()).contains(bike)) {
                    // 하차 후 로직 진행
                    processDismount(p, bike);
                }
            }
        }
    }

    /**
     * 하차 후 1초 뒤 폭발 로직
     */
    private void processDismount(Player p, Minecart bike) {
        // 이미 터진거면 패스
        if (bike.isDead())
            return;

        // 아직 관성으로 가던 방향
        Vector velocity = bike.getVelocity();

        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (bike.isDead() || count >= 20) { // 1초(20틱)
                    explode(p, bike);
                    this.cancel();
                    return;
                }

                // 1초간 앞으로 전진
                bike.setVelocity(velocity);
                count++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void explode(Player p, Minecart bike) {
        if (bike.isDead())
            return;

        Location loc = bike.getLocation();
        bike.remove(); // 수레 제거

        // 이펙트 및 대미지
        loc.getWorld().createExplosion(loc, 0F, false); // 지형 파괴 없는 이펙트용 폭발
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 3f, 1f);

        // 범위 대미지
        for (org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, 4, 4, 4)) {
            if (e instanceof org.bukkit.entity.LivingEntity living) {
                // 본인은 대미지 안 받음
                if (e.equals(p))
                    continue;

                if (e instanceof Player && ((Player) e).getGameMode() == org.bukkit.GameMode.SPECTATOR)
                    continue;

                living.damage(36.0, p); // 18칸 = 36 대미지
            }
        }
    }

    // 헬퍼: 내 능력인지 확인
    private boolean hasAbility(Player p) {
        // [추가] 능력이 봉인된 상태 (침묵)인지 체크
        if (isSilenced(p))
            return false;
        return me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode());
    }
}
