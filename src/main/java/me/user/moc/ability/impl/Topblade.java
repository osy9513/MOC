package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.List;

public class Topblade extends Ability {

    public Topblade(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "055";
    }

    @Override
    public String getName() {
        return "탑블레이드";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§c전투 ● 탑블레이드(탑블레이드)",
                "§f탑블레이드가 됩니다!");
    }

    @Override
    public void giveItem(Player p) {
        // [추가] 기본 지급된 철 검 제거 (중복 방지)
        p.getInventory().remove(Material.IRON_SWORD);

        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName("§c탑블레이드");
        meta.setLore(Arrays.asList(
                "§f우클릭 시 10초동안 빠르게 회전하며 돌진합니다.",
                "§f벽이나 자기장에 부딪히면 튕겨나갑니다.",
                "§f적과 충돌 시 데미지를 입히고 튕겨나갑니다."));
        meta.setCustomModelData(12); // 리소스팩: beyblade
        meta.setUnbreakable(true);
        sword.setItemMeta(meta);

        p.getInventory().addItem(sword);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 탑블레이드(탑블레이드)");
        p.sendMessage("§f칼을 우클릭하면 10초동안 본인이 빙글빙글 돌면서 앞으로 돌진합니다.");
        p.sendMessage("§f바닥을 제외한 블럭 및 자기장에 부딪히면 반대 방향으로 튕겨나갑니다.");
        p.sendMessage("§f생명체와 부딪치면 8데미지를 주며 해당 생명체와 본인이 튕겨나갑니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 10초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        // [추가] 능력이 봉인된 상태 (침묵)인지 체크
        if (isSilenced(p))
            return;
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = e.getItem();
            if (item != null && item.getType() == Material.IRON_SWORD) {
                // [수정] checkCooldown이 true를 반환하면 "사용 가능"이므로, !checkCooldown일 때 리턴해야 함!
                if (!checkCooldown(p))
                    return;

                useSkill(p);
            }
        }
    }

    private void useSkill(Player p) {
        p.sendMessage("§c탑블레이드 : 고고 탑블레이드~");

        // 초기 방향 설정 (현재 바라보는 방향)
        final Vector direction = p.getLocation().getDirection().setY(0).normalize().multiply(0.8); // 0.8 블록/틱 = 16 블록/초
                                                                                                   // (속도 2배 증가)

        org.bukkit.scheduler.BukkitTask task = new BukkitRunnable() {
            int tick = 0;
            final int maxTick = 200; // 10초
            Vector currentVelocity = direction.clone();

            @Override
            public void run() {
                // 종료 조건
                if (!p.isOnline() || p.isDead() || tick >= maxTick) {
                    this.cancel();
                    // 종료 시 쿨타임 부여
                    setCooldown(p, 10);
                    return;
                }

                // 1. 회전 처리 (1틱당 36도)
                Location loc = p.getLocation();
                float newYaw = loc.getYaw() + 36f;

                // 시점 회전만 teleport로 처리하고, 이동은 velocity로 처리하여 부드러움을 유도
                Location rotateLoc = p.getLocation();
                rotateLoc.setYaw(newYaw);
                p.teleport(rotateLoc);

                // 2. 충돌 감지 및 반사 (바닥 제외)
                World world = p.getWorld();
                Location headLoc = p.getEyeLocation();
                Location bodyLoc = p.getLocation().add(0, 0.5, 0); // 몸통 중간

                // 진행 방향 체크 (조금 더 멀리 체크)
                Vector nextStep = currentVelocity.clone().normalize();
                Location checkLoc = bodyLoc.clone().add(nextStep);

                boolean collided = false;

                // 벽 충돌 (몸통 또는 머리 높이)
                if (isSolid(checkLoc.getBlock()) || isSolid(headLoc.clone().add(nextStep).getBlock())) {
                    collided = true;
                }

                // 자기장 충돌
                WorldBorder border = world.getWorldBorder();
                if (!border.isInside(checkLoc)) {
                    collided = true;
                }

                if (collided) {
                    // 반대 방향으로 튕기기 (단순 반전)
                    currentVelocity.multiply(-1);
                    // [수정] 사람과 부딪친 것과 같은 이팩트 출력
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 2f);
                    p.getWorld().spawnParticle(Particle.EXPLOSION, p.getLocation(), 1);
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                }

                // 이동 적용 (충돌 후 방향 반영)
                // [수정] 반드시 앞으로 이동하도록, 플레이어가 멈추지 않게 약간의 중력을 둔 XZ 강제 고정
                Vector applyVel = currentVelocity.clone();
                applyVel.setY(p.getVelocity().getY() - 0.04);
                p.setVelocity(applyVel);

                // 3. 엔티티 충돌 처리
                // 자신 제외, 관전자 제외
                for (Entity entity : p.getNearbyEntities(1.2, 1.2, 1.2)) { // 범위를 약간 늘림 (충돌 원활)
                    if (entity instanceof LivingEntity && entity != p) {
                        LivingEntity target = (LivingEntity) entity;

                        // [수정] 관전자 완벽 제외
                        if (target instanceof Player
                                && ((Player) target).getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                            continue;
                        }

                        // 데미지
                        target.damage(8, p);

                        // [수정] 10칸 이상 날리는 초강력 넉백
                        // damage() 메서드가 넉백 이벤트를 덮어씌우지 않게 1틱 지연 적용
                        final Vector knockbackDir = currentVelocity.clone().normalize().multiply(-1).setY(0); // 현재
                                                                                                              // currentVelocity는
                                                                                                              // 튕겨나가기
                                                                                                              // 전이므로 타겟
                                                                                                              // 쪽 방향
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (target.isValid() && !target.isDead()) {
                                    // 3.5 배율에 Y 1.2 정도면 10칸 이상 저 멀리 날아감
                                    target.setVelocity(knockbackDir.multiply(-4.0).setY(1.3));
                                }
                            }
                        }.runTaskLater(plugin, 1L);

                        // 본인 튕겨나감
                        currentVelocity.multiply(-1);
                        p.setVelocity(currentVelocity.clone().setY(p.getVelocity().getY()));

                        // 효과
                        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 2f);
                        p.getWorld().spawnParticle(Particle.EXPLOSION, p.getLocation(), 1);
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);

                        break; // 한 명만 치고 튕겨나가도록
                    }
                }

                tick++;
            }

            private boolean isSolid(Block b) {
                return b.getType().isSolid();
            }
        }.runTaskTimer(plugin, 0L, 1L);

        registerTask(p, task);
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
    }
}
