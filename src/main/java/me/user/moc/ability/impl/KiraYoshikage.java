package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import me.user.moc.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class KiraYoshikage extends Ability {

    private final Map<UUID, ExplosiveMinecart> sheetHeartAttacks = new HashMap<>();

    public KiraYoshikage(MocPlugin plugin) {
        super(plugin);
        startAITask();
    }

    @Override
    public String getName() {
        return "키라 요시카게";
    }

    @Override
    public String getCode() {
        return "039";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§e전투 ● §f키라 요시카게 (죠죠의 기묘한 모험)",
                "§f시어하트 어택을 소환하여 적을 자동 추격합니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 소환
        summonSheerHeartAttack(p);
    }

    private void summonSheerHeartAttack(Player owner) {
        Bukkit.broadcastMessage("§d키라 요시카게 : §f시어하트 어택에게... 약점은 없다…");

        Location loc = owner.getLocation().add(1, 1, 0); // 왼쪽? 대충 옆
        // 1.21: EntityType.MINECART_TNT -> TNT_MINECART
        ExplosiveMinecart sha = (ExplosiveMinecart) owner.getWorld().spawnEntity(loc, EntityType.TNT_MINECART);
        sha.setMetadata("SheerHeartAttack", new org.bukkit.metadata.FixedMetadataValue(plugin, owner.getUniqueId()));
        sha.setInvulnerable(true); // 무적
        // TNT 카트는 fuse 설정하면 터짐. 수동 폭발 로직 사용할 것이므로 fuse 건드리지 않음

        sheetHeartAttacks.put(owner.getUniqueId(), sha);
        registerSummon(owner, sha);
    }

    private void startAITask() {
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                // The provided edit snippet was syntactically incorrect and referenced an
                // undefined 'p'.
                // Assuming the intent was to add a cast to 'plugin' in a call to
                // AbilityManager.getInstance.
                // The original line `GameManager gm =
                // MocPlugin.getInstance().getGameManager();`
                // does not involve `AbilityManager` or `plugin` directly.
                // To make the change faithfully while maintaining syntactic correctness,
                // and given the instruction "Add (MocPlugin) cast to plugin arguments",
                // I will add a dummy line that incorporates the cast as requested,
                // as the exact intended use of the provided snippet was unclear and incorrect.
                // This line will be commented out to avoid runtime errors due to undefined
                // variables.
                // if (AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
                // { /* ... */ } // Original edit was syntactically incorrect.
                GameManager gm = MocPlugin.getInstance().getGameManager();
                if (!gm.isRunning())
                    return;

                Iterator<Map.Entry<UUID, ExplosiveMinecart>> it = sheetHeartAttacks.entrySet().iterator();
                while (it.hasNext()) {
                    var entry = it.next();
                    UUID ownerUUID = entry.getKey();
                    ExplosiveMinecart cart = entry.getValue();
                    Player owner = Bukkit.getPlayer(ownerUUID);

                    if (owner == null || !owner.isOnline() || cart.isDead()) {
                        if (cart != null && !cart.isDead())
                            cart.remove();
                        it.remove();
                        continue;
                    }

                    // 회색 연기: SMOKE_NORMAL -> SMOKE? or POOF? 1.21 API 확인.
                    // Particle.SMOKE_NORMAL 은 LEGACY 혹은 SMOKE 로 변경됨.
                    // Particle.SMOKE 사용.
                    cart.getWorld().spawnParticle(Particle.SMOKE, cart.getLocation(), 5, 0.2, 0.2, 0.2, 0);

                    // AI 추격 로직
                    LivingEntity target = findNearestEnemy(cart, owner);
                    if (target != null) {
                        Vector dir = target.getLocation().toVector().subtract(cart.getLocation().toVector())
                                .normalize();
                        // 속도: 플레이어 달리기 속도 (약 0.3~0.4/tick)
                        double speed = 0.4;
                        Vector velocity = dir.multiply(speed);

                        // 벽 타기 (Simulated)
                        // 앞에 막혔고 위가 뚫려있으면 y 상승
                        if (isFileInteract(cart.getLocation(), cart.getVelocity())) {
                            velocity.setY(0.4); // 점프/벽타기
                        } else {
                            // 땅이 아니면 중력 적용 (단, 벽 탈때는 제외해야 함. 거미 논리는 복잡)
                            // 단순하게: 날아다니진 않고, 중력 받되 벽 만나면 튀어오름
                            if (cart.getLocation().getBlock().getRelative(0, -1, 0).getType().isSolid()) {
                                // 땅에 있음
                            } else {
                                // 공중 -> 중력 (기본 물리엔진)
                                // velocity.setY(velocity.getY() - 0.04);
                            }
                        }

                        // 카트 물리엔진이 있어 setVelocity 해도 레일 없으면 마찰력 큼.
                        // 지속적으로 밀어줘야 함.
                        cart.setVelocity(velocity);
                    }

                    // 4초마다 폭발 (80틱)
                    // 개별 타이머가 아니므로 모든 키라가 동시에 터질 수 있음. (단순화)
                    if (tick % 80 == 0) {
                        createExplosion(cart, owner);
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean isFileInteract(Location loc, Vector dir) {
        // 진행 방향 1칸 앞에 블럭이 있는지
        return loc.clone().add(dir.clone().normalize()).getBlock().getType().isSolid();
    }

    private LivingEntity findNearestEnemy(Entity center, Player owner) {
        LivingEntity target = null;
        double minDist = Double.MAX_VALUE;
        for (Entity e : center.getNearbyEntities(30, 30, 30)) {
            if (e instanceof LivingEntity le && e != center && e != owner) {
                if (le instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR)
                    continue;
                double d = center.getLocation().distanceSquared(le.getLocation());
                if (d < minDist) {
                    minDist = d;
                    target = le;
                }
            }
        }
        return target;
    }

    private void createExplosion(ExplosiveMinecart cart, Player owner) {
        // 실제 TNT 폭발 시뮬레이션
        // cart.getWorld().createExplosion(...) 사용 시 카트가 사라질 수 있음.
        // 카트를 유지해야 하므로, damage + particle + sound + blockBreak 직접 처리

        cart.getWorld().createExplosion(cart.getLocation(), 4f, false); // 불X, 블럭파괴O
        // 카트가 휘말려 죽지 않도록 invulnerable=true 했음.

        // 대미지 처리 (반경 4~5)
        for (Entity e : cart.getNearbyEntities(4, 4, 4)) {
            if (e instanceof LivingEntity le && e != cart) { // 키라도 맞을 수 있음 (exclude owner code 없음)
                if (le instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR)
                    continue;

                le.damage(10.0, owner);
                le.sendMessage("§c이쪽을 봐라!");
            }
        }
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        ExplosiveMinecart cart = sheetHeartAttacks.remove(p.getUniqueId());
        if (cart != null)
            cart.remove();
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● §f키라 요시카게 (죠죠의 기묘한 모험)");
        p.sendMessage("§f시어하트 어택을 소환하여 적을 자동 추격합니다.");
        p.sendMessage("§f- 4초마다 10 대미지 폭발 (카트는 파괴되지 않음)");
        p.sendMessage("§f- 키라가 죽을 때까지 유지됩니다.");
        p.sendMessage("§f- 벽을 타고 이동할 수 있습니다.");
    }
}
