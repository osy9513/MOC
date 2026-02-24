package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class Naofumi extends Ability {

    // [방어 카운터] 플레이어별 막은 횟수 저장
    private final Map<UUID, Integer> blockCounts = new HashMap<>();

    // [아이언 메이든 활성화 여부]
    private final Set<UUID> isIronMaidenReady = new HashSet<>();

    public Naofumi(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "025";
    }

    @Override
    public String getName() {
        return "이와타니 나오후미";
    }

    @Override
    public List<String> getDescription() {
        List<String> list = new ArrayList<>();
        list.add("§c전투 ● 이와타니 나오후미(방패 용사 성공담)");
        list.add(" ");
        list.add("§f분노의 방패를 듭니다. 아이언 메이든를 사용할 수 있습니다.");
        return list;
    }

    @Override
    public void giveItem(Player p) {
        // [장비 지급] 분노의 방패 (실제론 그냥 방패)
        ItemStack shield = new ItemStack(Material.SHIELD);
        ItemMeta meta = shield.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c분노의 방패");
            meta.setLore(Arrays.asList(
                    "§7방어 성공 시 아이언 메이든 게이지가 충전됩니다.",
                    "§720회 방어 달성 후 방패로 타격 시 대상에게 아이언 메이든을 발동합니다.",
                    "§c(패널티) 해당 방패 소지 시 구속 1 디버프를 얻습니다.",
                    "§c(패널티) 발동 후 20초간 방패 사용이 불가능합니다."));
            meta.setUnbreakable(true); // [Fix] 내구도 무한
            shield.setItemMeta(meta);
        }
        p.getInventory().addItem(shield);

        // [Fix] 철칼 제거
        p.getInventory().remove(Material.IRON_SWORD);

        // 초기화
        blockCounts.put(p.getUniqueId(), 0);
        isIronMaidenReady.remove(p.getUniqueId());
        startPassiveLoop();
    }

    // ... (skipped detailCheck and reset) ...

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 이와타니 나오후미(방패 용사 성공담)");
        p.sendMessage("§f분노의 방패를 얻습니다.");
        p.sendMessage("§f방패을 들고 있을 경우 힘1과 허기1 디버프가 상시 적용됩니다.");
        p.sendMessage("§f방패로 공격을 20번 막으면 아이언 메이든이 활성화 됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f아이언 메이든이 활성화된 상태에서 방패를 들고");
        p.sendMessage("§f방패로 타격하면 대상에게 즉시 아이언 메이든이 발동됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f발동 후 다시 쓰려면 다시 20번의 공격을 막아야합니다.");
        p.sendMessage("§f아이언 메이든의 지속시간은 20초 입니다.");
        p.sendMessage("§f[제한] 대상이 플레이어일 경우, 체력이 3칸(6.0) 남으면 능력이 강제 종료됩니다.");
        p.sendMessage("§f[패널티] 인벤토리에 '분노의 방패'가 있다면 상시 구속 1 효과를 겪습니다.");
        p.sendMessage("§f[패널티] 능력 발동 후 20초간 방패를 우클릭 사용할 수 없습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 분노의 방패");
        p.sendMessage("§f장비 제거 : 철 칼");
    }

    @Override
    public void reset() {
        super.reset();
        blockCounts.clear();
        isIronMaidenReady.clear();
    }

    /**
     * [패시브 루프] 1초마다 방패 들고 있는지 확인하여 버프/디버프 부여
     */
    private BukkitTask passiveTask;

    /**
     * [패시브 루프] 1초마다 방패 들고 있는지 확인하여 버프/디버프 부여
     */
    private void startPassiveLoop() {
        if (passiveTask != null && !passiveTask.isCancelled())
            return;

        passiveTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Naofumi 플레이어 존재 여부 확인
                boolean naofumiExists = false;
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode())) {
                        naofumiExists = true;
                        break;
                    }
                }

                if (!naofumiExists) {
                    this.cancel();
                    passiveTask = null;
                    return;
                }

                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode())) {
                        checkShieldPassive(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void checkShieldPassive(Player p) {
        boolean hasShield = false;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == Material.SHIELD) {
                hasShield = true;
                break;
            }
        }

        if (hasShield) {
            // [너프] 방패 소지 시 속도 제한 (구속 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, true, true, true));
        }

        // 들고 있을 때의 버프/디버프는 따로 적용
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        boolean holdingShield = (main != null && main.getType() == Material.SHIELD) ||
                (off != null && off.getType() == Material.SHIELD);

        if (holdingShield) {
            // [효과 부여] 힘 1, 허기 1
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, true, true, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 40, 4, true, true, true));
        }

        // [추가] 아이언 메이든(20스택) 대기 상태라면 붉은 파티클 효과 부여
        if (isIronMaidenReady.contains(p.getUniqueId())) {
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 3, 0.4, 0.5, 0.4,
                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
        }
    }

    /**
     * [이벤트] 방어 성공 감지
     */
    @EventHandler
    public void onBlock(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p))
            return;

        // [추가] 능력이 봉인된 상태 (침묵)인지 체크
        if (isSilenced(p))
            return;
        // 내 능력 확인
        if (!AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        if (p.isBlocking() && e.getFinalDamage() < e.getDamage()) { // 대미지가 방어됨
            int count = blockCounts.getOrDefault(p.getUniqueId(), 0) + 1;
            blockCounts.put(p.getUniqueId(), count);

            p.playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);

            if (count >= 20 && !isIronMaidenReady.contains(p.getUniqueId())) {
                isIronMaidenReady.add(p.getUniqueId());
                p.sendMessage("§c§l[MOC] §4아이언 메이든§c 발동 준비 완료! 방패를 든 채 공격하여 발동하세요.");
                p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 1f, 0.5f);
            } else if (count < 20) {
                p.sendActionBar(net.kyori.adventure.text.Component.text("§7방어 횟수: " + count + "/20"));
            }
        }
    }

    /**
     * [액티브] 아이언 메이든 발동 (생명체 직접 공격)
     */
    @EventHandler
    public void onIronMaidenAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p))
            return;

        // 침묵 확인
        if (isSilenced(p))
            return;

        // 나오후미 여부 확인
        if (!AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        // 아이언 메이든 장전 상태인지 확인
        if (!isIronMaidenReady.contains(p.getUniqueId()))
            return;

        // 방패를 들고 타격했는지 확인
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        if (main.getType() != Material.SHIELD && off.getType() != Material.SHIELD)
            return;

        // 타겟이 살아있는 생명체인지 확인
        if (e.getEntity() instanceof LivingEntity livingTarget) {
            // 대상이 관전자면 제외
            if (livingTarget instanceof Player
                    && ((Player) livingTarget).getGameMode() == org.bukkit.GameMode.SPECTATOR)
                return;

            // 아이언 메이든 대미지 자체를 막기 위해 평타 공격을 취소선 처리할 수 있지만
            // 일반 타격 판정도 넣을거면 그냥 진행. 여기선 아이언 메이든을 발동시킴.
            useIronMaiden(p, livingTarget);
        }
    }

    /**
     * [기존 우클릭] 방어 성공 텍스트나 다른 잔여 효과 방지용으로 남겨둠
     * (아이언 메이든은 이제 직접 타격으로 발동하므로 허공 우클릭 발동은 제거됨)
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        // 기존 코드는 삭제됨 (발동 불가)
    }

    // 대상 얻는 기존 RayTrace 메서드 삭제 또는 보존
    private Entity getTargetEntity(Player p, int range) {
        return p.getTargetEntity(range, false); // RayTrace
    }

    private void useIronMaiden(Player p, LivingEntity target) {
        // 1. 상태 소모
        isIronMaidenReady.remove(p.getUniqueId());
        blockCounts.put(p.getUniqueId(), 0);

        p.sendMessage("§4아이언 메이든!");
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f); // 웅장한 소리

        // [추가] 방패 쿨타임 20초(400틱) 적용 (도끼로 찍힌 효과처럼 방어를 제한함)
        p.setCooldown(Material.SHIELD, 400);
        p.sendMessage("§c[패널티] 아이언 메이든의 반동으로 방패가 20초간 비활성화됩니다.");

        // 2. 가마솥 소환 (BlockDisplay 활용)
        Location targetLoc = target.getLocation();
        // 타겟 위치 기준 10칸 위가 아니라, 끌려갈 도착지점(공중)을 정의
        Location spawnLoc = targetLoc.clone().add(0, 5, 0); // 5블럭 위에서 합체

        // 1.21 Display 엔티티
        BlockDisplay bottom = (BlockDisplay) p.getWorld().spawnEntity(spawnLoc, EntityType.BLOCK_DISPLAY);
        bottom.setBlock(Material.CAULDRON.createBlockData());
        // [Fix] Center Bottom: Scale 2 -> Offset -1 to center X/Z.
        bottom.setTransformation(new Transformation(
                new Vector3f(-1.0f, 0f, -1.0f),
                new AxisAngle4f(),
                new Vector3f(2f, 2f, 2f),
                new AxisAngle4f()));

        BlockDisplay top = (BlockDisplay) p.getWorld().spawnEntity(spawnLoc.clone().add(0, 4, 0), // 4칸 위에서 시작 (닫혀야 함)
                EntityType.BLOCK_DISPLAY);
        top.setBlock(Material.CAULDRON.createBlockData());

        // [Fix] Top Inverted Alignment
        // Rotate 180 X (Flip upside down).
        // Scale 2.
        // X: 0..2 -> Center -1
        // Y: 0..-2 -> To overlap bottom (0..2), we need to shift it up?
        // Actually, let's keep it simple: Top starts high, slams down.
        // We set Top entity position above.
        // Rotation 180X makes (x,y,z) -> (x,-y,-z).
        // Z: 0..-2 -> Center +1.
        AxisAngle4f rotation = new AxisAngle4f((float) Math.toRadians(180), 1f, 0f, 0f);
        top.setTransformation(new Transformation(
                new Vector3f(-1.0f, 2.0f, 1.0f), // Y=2.0 to shift the inverted block up relative to origin?
                rotation,
                new Vector3f(2f, 2f, 2f),
                new AxisAngle4f()));

        registerSummon(p, bottom);
        registerSummon(p, top);

        // [애니메이션 루프]
        new BukkitRunnable() {
            int tick = 0;
            final int MOVE_DURATION = 40; // 2초

            @Override
            public void run() {
                if (!target.isValid()) {
                    bottom.remove();
                    top.remove();
                    this.cancel();
                    return;
                }

                // 2초 동안: 상대방 공중 부양 + 가마솥 닫힘
                if (tick < MOVE_DURATION) {
                    Location current = target.getLocation();
                    // 도착지점 (가마솥 내부, spawnLoc + 1?)
                    Location destination = spawnLoc.clone().add(0, 1, 0); // 가마솥 안

                    // 선형 보간 (Lerp)
                    double progress = (double) tick / MOVE_DURATION;
                    double y = targetLoc.getY() + (destination.getY() - targetLoc.getY()) * progress;

                    Location strictLoc = targetLoc.clone();
                    strictLoc.setY(y);
                    strictLoc.setDirection(current.getDirection());
                    target.teleport(strictLoc);

                    // [Fix] 보라색 이펙트 (엔더 진주 느낌) - 끌려가는 줄
                    // 타겟 중심부 ~ 도착점 사이 선
                    p.getWorld().spawnParticle(Particle.PORTAL, strictLoc.clone().add(0, 1, 0), 5, 0.2, 0.5, 0.2, 0.0);

                    // 가마솥 닫힘 연출 (Top 내려옴)
                    // Top Entity is at spawnLoc + 4.
                    // We want it to end at spawnLoc + 2 (Sitting on top of bottom).
                    double topY = 4.0 - (2.0 * progress);
                    top.teleport(spawnLoc.clone().add(0, topY, 0));
                }

                // 2초 시점: 쾅! 닫힘
                if (tick == MOVE_DURATION) {
                    p.playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
                    target.sendMessage("§4아이언 메이든에 갇혔습니다!");

                    // Top 정확한 위치 고정
                    top.teleport(spawnLoc.clone().add(0, 2, 0));
                }

                // 2초 이후: 20초간 도트 대미지
                if (tick >= MOVE_DURATION) {
                    // 플레이어 고정
                    target.teleport(spawnLoc.clone().add(0, 1, 0));

                    // 대미지 (3틱마다 3)
                    if ((tick - MOVE_DURATION) % 3 == 0) {
                        // [너프] 대상이 플레이어이고 체력이 3칸(6.0) 이하라면 즉시 종료
                        if (target instanceof Player && target.getHealth() <= 6.0) {
                            bottom.remove();
                            top.remove();
                            this.cancel();
                            return;
                        }

                        target.damage(3.0, p);
                        target.setNoDamageTicks(0);
                        p.playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);

                        // [Fix] 대미지 파티클 (레드스톤)
                        p.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5,
                                new Particle.DustOptions(org.bukkit.Color.RED, 2));
                    }
                }

                tick++;

                // 종료 조건 (2초 + 20초 = 22초 = 440틱)
                if (tick > MOVE_DURATION + 400) {
                    bottom.remove();
                    top.remove();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
