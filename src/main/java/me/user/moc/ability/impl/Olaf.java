package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;

import java.util.ArrayList;
import java.util.List;

public class Olaf extends Ability {

    public Olaf(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "002";
    }

    @Override
    public String getName() {
        return "올라프";
    }

    @Override
    public List<String> getDescription() {
        List<String> list = new ArrayList<>();
        list.add("§f전투 ● 올라프(리그 오브 레전드)");
        list.add("§f철 도끼가 지급된다. 우 클릭 시 도끼를 던져");
        list.add("§f적에게 강력한 대미지를 입힌다.");
        return list;
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● 올라프(리그 오브 레전드)");
        p.sendMessage("§f우클릭 시 도끼를 상대에게 던져 체력 8칸(Damage 8)의 피해를 줍니다.");
        p.sendMessage("§f맞은 상대는 §e구속 1§f이 1초간 걸립니다.");
        p.sendMessage("§f도끼는 플레이어 혹은 블럭에 접촉하면 땅에 떨어집니다.");
        p.sendMessage("§f(단, §b유리 블럭§f은 부수고 계속 날아갑니다.)");
        p.sendMessage("§f땅에 떨어진 도끼는 §a올라프 본인만§f 주울 수 있으며,");
        p.sendMessage("§f도끼를 주우면 §b쿨타임이 초기화§f됩니다.");
        p.sendMessage("§7쿨타임 : 5초");
        p.sendMessage("§---");
        p.sendMessage("§7추가 장비: 철 도끼 2개");
        p.sendMessage("§7장비 제거: 철 검");
    }

    @Override
    public void giveItem(Player p) {
        // 장비 제거: 철 검
        p.getInventory().remove(Material.IRON_SWORD);
        // 추가 장비: 철 도끼 2개
        p.getInventory().addItem(new ItemStack(Material.IRON_AXE, 2));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 능력자 체크 (AbilityManager를 통해)
        if (plugin instanceof MocPlugin moc) {
            if (moc.getAbilityManager() == null || !moc.getAbilityManager().hasAbility(p, getCode()))
                return;
        }

        // 아이템 체크 (철 도끼) & 액션 체크 (우클릭)
        if (e.getItem() != null && e.getItem().getType() == Material.IRON_AXE &&
                (e.getAction().name().contains("RIGHT"))) {

            // 바닐라 도끼 사용(박피 등) 방지
            e.setCancelled(true);

            // 쿨타임 체크
            if (!checkCooldown(p))
                return;

            // 쿨타임 설정 (5초)
            setCooldown(p, 5);

            // 스킬 발동
            throwAxe(p);
        }
    }

    private void throwAxe(Player p) {
        // 메시지 출력
        p.getServer().broadcastMessage("§c올라프 : 형씨마시아!");
        // 효과음
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);

        Location startLoc = p.getEyeLocation().subtract(0, 0.2, 0); // 눈높이보다 살짝 아래
        Vector dir = startLoc.getDirection().normalize();

        // 1. 시각적 투사체 (ItemDisplay) 생성
        ItemDisplay display = p.getWorld().spawn(startLoc, ItemDisplay.class);
        display.setItemStack(new ItemStack(Material.IRON_AXE));
        // 초기 변환 설정 (회전을 위해 FIXED 모드 사용 권장되나, 기본 모드도 무방. 여기선 돌려야 하므로 기본값 사용 후 transform
        // 조작)
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED); // 제어하기 편하게 FIXED

        // 크기 및 초기 회전
        Transformation transform = display.getTransformation();
        transform.getScale().set(1.5f, 1.5f, 1.5f); // 약간 큼직하게
        // 아이템이 수직으로 서 있도록 조정 (FIXED 기준)
        transform.getLeftRotation().set(new AxisAngle4f((float) Math.toRadians(90), 0, 1, 0));
        display.setTransformation(transform);

        registerSummon(p, display); // 라운드 종료 시 삭제되도록 등록

        // 2. 투사체 로직 (BukkitRunnable)
        new BukkitRunnable() {
            Location currentLoc = startLoc.clone();
            double speed = 1.2;
            double distance = 0;
            double maxDistance = 30; // 최대 사거리
            float rotationAngle = 0;

            @Override
            public void run() {
                if (!display.isValid() || distance > maxDistance) {
                    dropAxe(currentLoc, p); // 사거리 끝 도달 시 드랍
                    display.remove();
                    this.cancel();
                    return;
                }

                // 이동 전 위치 (필요 시 사용)
                // Location prevLoc = currentLoc.clone();
                // 다음 위치 계산
                Vector move = dir.clone().multiply(speed);
                Location nextLoc = currentLoc.clone().add(move);

                // 2-1. 충돌 체크 (블록) - 촘촘하게 검사
                double stepSize = 0.5; // 0.5칸씩 전진하며 체크
                double dist = currentLoc.distance(nextLoc);
                Vector stepDir = dir.clone().normalize().multiply(stepSize);

                Location checkLoc = currentLoc.clone();
                for (double d = 0; d < dist; d += stepSize) {
                    checkLoc.add(stepDir);
                    Block b = checkLoc.getBlock();

                    if (!b.isPassable()) { // 통과 불가능한 블록 발견
                        if (isGlass(b.getType())) {
                            // 유리: 부수고 계속 진행 (소리 및 파티클)
                            b.getWorld().playSound(b.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1f);
                            b.getWorld().spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 10,
                                    Bukkit.createBlockData(b.getType()));
                            b.setType(Material.AIR);
                        } else {
                            // 일반 블록: 벽에 꽝 -> 멈춤
                            currentLoc.getWorld().spawnParticle(Particle.ITEM, currentLoc, 10, 0.2, 0.2, 0.2, 0.1,
                                    new ItemStack(Material.IRON_AXE));
                            currentLoc.getWorld().playSound(currentLoc, Sound.ENTITY_ITEM_BREAK, 1f, 0.5f);

                            dropAxe(currentLoc, p); // 드랍
                            display.remove();
                            this.cancel();
                            return;
                        }
                    }
                }

                // 2-2. 충돌 체크 (엔티티)
                // 히트박스 1.0 (좀 넉넉하게)
                for (Entity e : currentLoc.getWorld().getNearbyEntities(currentLoc, 1.0, 1.0, 1.0)) {
                    if (e.equals(p))
                        continue; // 본인은 맞지 않음
                    if (e instanceof LivingEntity target) {
                        // 적중!
                        hitTarget(target, p);

                        // 이펙트
                        currentLoc.getWorld().spawnParticle(Particle.ITEM, currentLoc, 10, 0.2, 0.2, 0.2, 0.1,
                                new ItemStack(Material.IRON_AXE));
                        currentLoc.getWorld().playSound(currentLoc, Sound.ENTITY_ITEM_BREAK, 1f, 0.5f);

                        // 드랍
                        dropAxe(currentLoc, p);
                        display.remove();
                        this.cancel();
                        return;
                    }
                }

                // 3. 이동 적용
                currentLoc = nextLoc;
                display.teleport(currentLoc);

                // 4. 회전 애니메이션 (빙글빙글)
                rotationAngle += 45; // 틱당 45도 회전
                Transformation t = display.getTransformation();
                // 축을 기준으로 회전 (Z축 회전으로 스핀 구현)
                // FIXED 모드에서 아이템 방향에 따라 축이 다르므로 테스트 필요.
                // 일단 X축 기준으로 도끼가 날아가므로 Z축 회전이면 휠윈드처럼 돔.
                t.getRightRotation().set(new AxisAngle4f((float) Math.toRadians(rotationAngle), 0, 0, 1));
                display.setTransformation(t);

                distance += speed;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean isGlass(Material mat) {
        return mat.name().contains("GLASS");
    }

    private void hitTarget(LivingEntity target, Player attacker) {
        // 데미지 8 (4칸)
        target.damage(8.0, attacker);
        // 구속 1 (1초 = 20 ticks)
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 0));
    }

    // 도끼 드랍 (아이템 엔티티 생성)
    private void dropAxe(Location loc, Player owner) {
        // 실제 아이템 드랍
        Item item = loc.getWorld().dropItem(loc, new ItemStack(Material.IRON_AXE));
        // 못 줍게 막기 (일단 줍기 딜레이를 무한으로? 아니면 메타데이터)
        item.setPickupDelay(20); // 1초 후 줍기 가능 (바로 주워지는 거 방지)
        item.setOwner(owner.getUniqueId()); // (Paper API) 소유자 설정 시 타인이 줍기 어려움(설정에 따라 다름)
        // 확실한 처리를 위해 메타데이터 사용
        item.setMetadata("olaf_axe_owner", new FixedMetadataValue(plugin, owner.getUniqueId().toString()));
        item.setCustomName("§c올라프의 도끼");
        item.setCustomNameVisible(true);
        // 소환수로 등록 (게임 끝나면 삭제되게)
        registerSummon(owner, item);
    }

    // 아이템 줍기 이벤트 처리
    @EventHandler
    public void onPickup(EntityPickupItemEvent e) { // PlayerPickupItemEvent는 Deprecated (Paper)
        if (e.getEntity() instanceof Player p) {
            Item itemEntity = e.getItem();

            // 올라프 도끼인지 확인
            if (itemEntity.hasMetadata("olaf_axe_owner")) {
                String ownerUUIDStr = itemEntity.getMetadata("olaf_axe_owner").get(0).asString();

                // 1. 주운 사람이 주인인가?
                if (p.getUniqueId().toString().equals(ownerUUIDStr)) {
                    // 주인임 -> 쿨타임 초기화 + 아이템 삭제 (인벤토리에 들어오지 않게?)
                    // "우클릭으로 떨어진 도끼는 올라프만 주울 수 있다."
                    // "올라프가 도끼를 주우면 쿨타임이 초기회 된다."
                    // 보통 이런 스킬은 도끼를 회수하는 개념이므로, 아이템을 먹어서 사라지게 하고 쿨타임을 0으로 만듦.

                    e.setCancelled(true); // 인벤토리에 들어오는 것 취소 (도끼 개수 복사는 막음)
                    itemEntity.remove(); // 땅에 있는 거 삭제

                    // 쿨타임 초기화
                    cooldowns.remove(p.getUniqueId());

                    p.sendMessage("§a[!] 도끼 회수");
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

                } else {
                    // 주인이 아님 -> 줍기 불가
                    e.setCancelled(true);
                    // 메시지는 너무 자주 뜨면 시끄러우니 생략하거나 액션바로
                }
            }
        }
    }
}
