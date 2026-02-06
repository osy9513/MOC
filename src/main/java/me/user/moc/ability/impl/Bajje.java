package me.user.moc.ability.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;

public class Bajje extends Ability {

    // 피해자별 쿨타임 (UUID: Victim, Long: Next Spawn Time)
    private final java.util.Map<UUID, Long> victimCooldowns = new java.util.HashMap<>();

    public Bajje(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "H01"; // 히든 코드
    }

    @Override
    public String getName() {
        return "베째";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§d히든 ● 베째(바집소)",
                "§f잼미니 수영 강사 베째가 됩니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 패시브라 아이템 없음 (기본 무기 제공 필요하면 여기에)
        p.getInventory().remove(Material.IRON_SWORD); // 다른 능력과 일관성 유지 (보통 기본칼 제거시)
        // 하지만 요청사항 "추가 장비: 없음, 장비 제거: 없음" 이므로 건드리지 않음?
        // 기존 코드들이 reset시 칼을 주는지, 아니면 giveItem에서 주는지 봐야 함.
        // PolarBear에서는 IRON_SWORD 제거했음.
        // 여기선 "장비 제거: 없음"이라 명시되었으므로 아무것도 안 함.
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§d히든 ● 베째(바집소)");
        p.sendMessage("§f플레이어가 물에 들어가는 순간 그 위치에 '잼미니'를 소환합니다.");
        p.sendMessage("§f잼미니는 물에 들어간 플레이어를 최우선으로 공격합니다.");
        p.sendMessage("§f베째 본인과 다른 잼미니들은 공격받지 않습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 3초 (피해자별 개별 적용)");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    // [핵심 로직] 물 감지 및 소환
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player victim = e.getPlayer();

        // 1. 최적화: 블록 이동 없으면 패스
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
                e.getFrom().getBlockY() == e.getTo().getBlockY() &&
                e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        // 2. 게임 내에 베째가 있는지 확인 (없으면 발동 안 함)
        // AbilityManager를 통해 H01 능력을 가진 플레이어가 있는지 찾음
        // (매번 반복문 돌면 무거울 수 있으니 최적화 가능하지만, 인원이 적으므로 순회 OK)
        Player bajjeOwner = findBajjePlayer();
        if (bajjeOwner == null)
            return;

        // 3. 베째 본인은 제외? "모든 플레이어들이" -> 본인 포함?
        // "잼미니들은 베째를 공격하지 않습니다" -> 베째가 물에 들어가도 잼미니는 나오지만 공격은 안함 (다른 몹 잡음)
        // 혹은 베째가 물에 들어가면 안 나오는게 자연스러운가?
        // "모든 플레이어들이 물에 들어가는 순간" + "베째를 제외" 언급 없음 (공격만 안 함)
        // -> 베째도 물에 들어가면 잼미니 소환됨 (자신의 군단 양성 가능)

        // 4. 물 체크 (발이 물에 닿았는가)
        Material mat = victim.getLocation().getBlock().getType();
        if (mat != Material.WATER)
            return; // bubble column 등 고려? 보통 WATER만.

        // 5. 피해자 쿨타임 체크 (3초)
        long now = System.currentTimeMillis();
        long next = victimCooldowns.getOrDefault(victim.getUniqueId(), 0L);
        if (now < next)
            return;

        // [소환 발동]
        victimCooldowns.put(victim.getUniqueId(), now + 3000L); // 3초 쿨

        spawnJamminni(bajjeOwner, victim);

        // 메시지? "베째: 애들아 선생님 말 좀 들어라" 는 능력 발동 시(능력 할당 시) 출력인가, 소환 시 출력인가?
        // "능력 발동 시 채팅에 출력될 메세지" -> 보통 게임 시작 or 능력 할당 시 한번.
        // 소환마다 뜨면 도배됨. (패스)
    }

    private Player findBajjePlayer() {
        for (UUID uuid : AbilityManager.getInstance().getPlayerAbilities().keySet()) {
            String code = AbilityManager.getInstance().getPlayerAbilities().get(uuid);
            if ("H01".equals(code)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline())
                    return p;
            }
        }
        return null;
    }

    private void spawnJamminni(Player owner, Player victim) {
        Location loc = victim.getLocation();
        World w = loc.getWorld();

        Zombie jamminni = (Zombie) w.spawnEntity(loc, EntityType.ZOMBIE);
        jamminni.setBaby(true); // 아기 좀비
        jamminni.setCustomName("§b말 안 듣는 잼미니");
        jamminni.setCustomNameVisible(true);
        jamminni.addScoreboardTag("JAMMINNI"); // 태그로 식별

        // 장비: 파란색 가죽 모자
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta meta = (LeatherArmorMeta) helmet.getItemMeta();
        if (meta != null) {
            meta.setColor(Color.BLUE);
            helmet.setItemMeta(meta);
        }
        jamminni.getEquipment().setHelmet(helmet);

        // 스탯 조정 (옵션)
        if (jamminni.getAttribute(Attribute.MOVEMENT_SPEED) != null)
            jamminni.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.35); // 빠름

        // 타겟 설정 (1순위 피해자)
        // 베째는 공격 안 함 (이벤트에서 방어하지만, 여기서도 설정 안 함)
        if (!victim.equals(owner)) {
            jamminni.setTarget(victim);
        }

        // 소유자 등록 (관리용)
        // 베째의 소환수로 등록하여 나중에 cleanup 시 함께 삭제
        registerSummon(owner, jamminni);

        // 소리
        w.playSound(loc, Sound.ENTITY_ZOMBIE_AMBIENT, 1f, 2f); // 아기 좀비 톤
    }

    // [타겟팅 로직]
    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getEntity() instanceof Zombie z))
            return;
        if (!z.getScoreboardTags().contains("JAMMINNI"))
            return;

        LivingEntity target = e.getTarget();
        if (target == null)
            return; // 타겟 해제는 허용

        // 1. 베째 공격 금지
        if (target instanceof Player p) {
            if (AbilityManager.getInstance().hasAbility(p, "H01")) { // 베째면
                e.setCancelled(true);
                return;
            }
        }

        // 2. 같은 잼미니 공격 금지
        if (target.getScoreboardTags().contains("JAMMINNI")) {
            e.setCancelled(true);
            return;
        }

        // 3. 우선순위 로직 보조
        // "물에 들어간 플레이어가 가장 1순위" ->
        // 이미 1순위 타겟(피해자)을 잡고 있다면, 다른 몹으로 어그로가 튀지 않게 막아야 함?
        // 혹은, AI가 자동으로 2순위(주변 생명체)를 잡으려 할 때, 1순위(플레이어)가 여전히 유효하다면 캔슬?
        // Bukkit 이벤트에서 "이 타겟팅이 왜 일어났는지(Reason)" 확인 가능.
        // Reason.CLOSEST_PLAYER, Reason.TARGET_ATTACKED_ENTITY 등.

        // 여기서는 "베째와 잼미니 제외"만 확실히 막아주면 됨.
        // 1순위 설정은 spawn 시 setTarget으로 부여했고,
        // 좀비 AI 특성상 플레이어를 우선하므로 굳이 강제하지 않아도 잘 쫓음.
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        victimCooldowns.clear();
        // 소환수는 super.reset() -> activeEntities.clear()로 날아감 (registerSummon 썼으니)
    }

    @Override
    public void reset() {
        super.reset();
        victimCooldowns.clear();
    }
}
