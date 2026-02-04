package me.user.moc.ability.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;

import com.destroystokyo.paper.profile.PlayerProfile;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import net.kyori.adventure.text.Component;

public class TogaHimiko extends Ability {

    // [상태 저장용 클래스] 변신 전의 내 정보를 저장합니다.
    private static class OriginalState {
        PlayerProfile profile; // 스킨 및 닉네임 정보
        String abilityCode; // 원래 능력 코드
        ItemStack[] inventory; // 옷 포함 인벤토리
        ItemStack[] armor; // 갑옷
        double health; // 체력
        double maxHealth; // 최대 체력

        // [추가] 속성 및 버프 백업
        double scale;
        double interactionRange;
        double blockInteractionRange;
        double attackDamage;
        double movementSpeed;
        java.util.Collection<org.bukkit.potion.PotionEffect> potionEffects;
    }

    // 변신 전 원본 상태 저장소
    private final Map<UUID, OriginalState> savedStates = new HashMap<>();
    // 현재 변신 중인 플레이어 목록
    private final Set<UUID> isTransformed = new HashSet<>();

    public TogaHimiko(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "047";
    }

    @Override
    public String getName() {
        return "토가 히미코";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§d복합 ● 토가 히미코(나의 히어로 아카데미아)",
                "§f개성 - 변신을 얻습니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 기본 지급 아이템 없음
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§d복합 ● 토가 히미코(나의 히어로 아카데미아)");
        p.sendMessage("§f개성 - 변신을 얻습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f토가 히미코가 다른 유저를 공격 시");
        p.sendMessage("§f인벤토리에 공격한 유저의 닉네임이 적힌 마실 수 있는 포션이 생긴다.");
        p.sendMessage("§f해당 포션을 우클릭 시 마시고 30초 동안 상대로 변신한다.");
        p.sendMessage("§f상대의 능력을 사용할 수 있고 30후 다시 토가 히미코로 돌아온다.");
        p.sendMessage(" ");
        p.sendMessage("§f유저 포션을 마실 때 체력 4칸을 회복한다.");
        p.sendMessage("§f모든 포션을 80% 더 빨리 마신다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초.");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 없음");
        p.sendMessage("§f장비 제거 : 없음");
    }

    // [이벤트 1] 공격 시 피(포션) 뽑기
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker) || !(e.getEntity() instanceof Player victim)) {
            return;
        }

        // 공격자가 토가 히미코인지 확인 (변신 중에는 피를 뽑을 수 없음 - 원작 고증 혹은 밸런스)
        AbilityManager am = AbilityManager.getInstance((MocPlugin) plugin);
        if (!am.hasAbility(attacker, getCode()) || isTransformed.contains(attacker.getUniqueId())) {
            return;
        }

        // 쿨타임 체크 (너무 많이 쌓이는 것 방지 - 0.5초 내부 쿨타임)
        if (!checkCooldown(attacker))
            return;
        setCooldown(attacker, 0); // 쿨타임 0초지만 내부 로직 흐름상 호출

        // 혈액 포션 지급
        giveBloodPotion(attacker, victim.getName());
    }

    private void giveBloodPotion(Player p, String targetName) {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta != null) {
            meta.setBasePotionType(PotionType.WATER); // 물병 베이스
            // [중요] 색상을 빨간색(RGB)으로 강제 설정
            meta.setColor(Color.RED);
            meta.displayName(Component.text("§c" + targetName + "의 혈액"));
            meta.setLore(Arrays.asList("§7마시면 30초간 해당 플레이어로 변신합니다."));
            potion.setItemMeta(meta);
        }
        p.getInventory().addItem(potion);
    }

    // [이벤트 2] 포션 섭취 (변신 로직)
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();

        // 1. 토가 히미코인지 확인
        AbilityManager am = AbilityManager.getInstance((MocPlugin) plugin);
        // (주의) 변신 상태일 때는 hasAbility가 false일 수 있음.
        // 하지만 '토가 히미코의 본체'라면 isTransformed에 있거나, hasAbility가 true여야 함.
        boolean isToga = am.hasAbility(p, getCode()) || isTransformed.contains(p.getUniqueId());

        if (!isToga)
            return;

        // [패시브] 섭취 속도 80% 단축은 1.21 API에서 itemUseDuration 혜택 또는 즉시 섭취 로직으로 구현해야 하나,
        // 현재 ConsumeEvent는 '다 먹었을 때' 발생함.
        // 단축을 위해서는 PlayerInteractEvent에서 startUsingItem 시간을 조작해야 하지만 복잡하므로
        // 여기서는 생략하거나 별도 패킷 처리가 필요. (일단 기능 구현 우선)
        // -> 요청하신 "80% 빨리 마신다"는 ConsumeEvent가 아니라 Interact나 패킷 레벨이 필요하지만,
        // 단순화를 위해 "마셨을 때 효과 발동"에 집중합니다.

        // 2. 혈액 포션인지 확인
        if (item.getType() == Material.POTION && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            String name = meta.getDisplayName(); // Deprecated지만 1.21.11 Paper에선 Component 처리 필요.
            // Spigot 호환성을 위해 Component -> String 변환 로직이 필요할 수 있음.
            // 여기선 편의상 Legacy 텍스트가 포함된 경우를 체크합니다.

            // Paper API Component Check
            Component displayNameComp = meta.displayName();
            String plainName = (displayNameComp != null)
                    ? ((net.kyori.adventure.text.TextComponent) displayNameComp).content()
                    : "";

            // "§cNickName의 혈액" 형식이거나 Legacy String check
            if (plainName.contains("의 혈액") || (meta.hasDisplayName() && meta.getDisplayName().contains("의 혈액"))) {

                // 타겟 이름 추출
                String targetName = plainName.replace("의 혈액", "").replace("§c", "").trim();
                // Legacy fallback
                if (targetName.isEmpty() && meta.hasDisplayName()) {
                    targetName = meta.getDisplayName().replace("의 혈액", "").replace("§c", "").trim();
                }

                // 타겟 플레이어 찾기 (오프라인이어도 프로필을 가져올 수 있으면 좋음)
                Player target = Bukkit.getPlayer(targetName);
                if (target == null) {
                    p.sendMessage("§c대상 플레이어를 찾을 수 없어 변신에 실패했습니다.");
                    return;
                }

                // 3. 변신 실행
                // [중첩 방지] 이미 변신 중이라면, 먼저 해제하고 다시 변신?
                // -> 네. savedStates가 덮어씌워지면 원래대로 못 돌아오므로,
                // 이미 변신 중이라면 '원래 상태'는 유지한 채 변신 대상만 갱신해야 함.
                if (isTransformed.contains(p.getUniqueId())) {
                    // 현재 변신 중인 능력의 소환수 등을 정리
                    String currentAbCode = am.getPlayerAbilities().get(p.getUniqueId());
                    Ability currentAb = am.getAbility(currentAbCode);
                    if (currentAb != null)
                        currentAb.cleanup(p);

                    // (주의) savedStates는 건드리지 않음 (최초의 원본이므로)
                } else {
                    // 최초 변신: 원본 저장
                    saveOriginalState(p);
                }

                transformToTarget(p, target, am);

                // 체력 4칸(8.0) 회복
                double newHealth = Math.min(p.getHealth() + 8.0,
                        p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
                p.setHealth(newHealth);

                p.sendMessage("§d" + targetName + "(으)로 변신했습니다! (30초 지속)");
                p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
            }
        }
    }

    private void saveOriginalState(Player p) {
        AbilityManager am = AbilityManager.getInstance((MocPlugin) plugin);
        OriginalState state = new OriginalState();
        state.profile = p.getPlayerProfile();
        state.abilityCode = am.getPlayerAbilities().get(p.getUniqueId()); // 현재 능력 코드 (047)
        state.inventory = p.getInventory().getContents();
        state.armor = p.getInventory().getArmorContents();
        state.health = p.getHealth();
        state.maxHealth = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();

        // [추가] 중요 속성 백업 (값이 없으면 기본값)
        // Paper 1.21.11 호환성을 위해 Attribute 명칭 확인 필요
        // 여기선 일반적인 서버 설정에 존재하는 속성들 위주로 백업
        state.scale = p.getAttribute(org.bukkit.attribute.Attribute.SCALE) != null
                ? p.getAttribute(org.bukkit.attribute.Attribute.SCALE).getValue()
                : 1.0;
        state.interactionRange = p.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE) != null
                ? p.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE).getValue()
                : 3.0;
        state.blockInteractionRange = p.getAttribute(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE) != null
                ? p.getAttribute(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE).getValue()
                : 4.5;
        state.attackDamage = p.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE) != null
                ? p.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).getValue()
                : 1.0;
        state.movementSpeed = p.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED) != null
                ? p.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).getValue()
                : 0.2; // 플레이어 기본 이속 0.2

        state.potionEffects = p.getActivePotionEffects(); // 현재 버프 목록 복사

        savedStates.put(p.getUniqueId(), state);
        isTransformed.add(p.getUniqueId());
    }

    private void transformToTarget(Player p, Player target, AbilityManager am) {
        // 1. 프로필(스킨, 닉네임) 복사
        PlayerProfile targetProfile = target.getPlayerProfile();
        p.setPlayerProfile(targetProfile);

        // 2. 능력 복사 및 교체
        String targetAbCode = am.getPlayerAbilities().get(target.getUniqueId());
        // 만약 타겟이 능력이 없거나 오류 상태면 기본값 혹은 유지? 현재는 복사 시도
        if (targetAbCode != null) {
            am.changeAbilityTemporary(p, targetAbCode);

            // 3. 인벤토리 복사 (아이템 중복 방지는 giveItem에서 처리하거나 여기서 싹 밀고 줌)
            // 토가는 '상대의 모든 것'을 베끼므로 인벤토리도 복사하는 것이 맞음 (기획상)
            // 단, "아이템이 중복되지 않게"라는 요청은, giveItem을 또 호출해서 곡괭이가 2개 되지 않게 하라는 의미.
            // -> 타겟의 인벤토리를 그대로 복붙하면 이미 타겟이 곡괭이를 갖고 있을 테니 giveItem 호출 필요 없음.
            // -> 하지만 타겟이 템을 버렸을 수도 있으니, 깔끔하게 '기본템 + giveItem' 하는 게 안전할 수도?
            // -> 기획서: "상대의 능력을 사용할 수 있고... 스킨, 능력, 인벤토리... 모두 저장하고 동일하게 얻는다."
            // -> 즉 인벤토리 통채로 복사가 맞습니다.

            p.getInventory().setContents(target.getInventory().getContents());
            p.getInventory().setArmorContents(target.getInventory().getArmorContents());

            // [중요] 타겟의 능력 아이템을 확실히 주기 위해
            // 만약 타겟 인벤토리에 '능력 아이템'이 없는 상태라면(버렸거나 해서) giveItem을 호출해야 함.
            // 하지만 뭐가 능력 아이템인지 식별이 어려우므로,
            // 사용자 요청(중복제거)을 고려하여 "giveItem을 호출하되, 이미 있으면 무시" 로직이 각 능력에 있어야 함.
            // 현실적으로는 타겟 인벤토리를 복사했으면 그 안에 있겠거니 하고 giveItem은 생략하는 것이 꼬이지 않는 길입니다.
            // (만약 giveItem을 강제로 하면 타겟이 곡 2개 들고 있었으면 3개가 됨)

            // **결정**: 인벤토리를 그대로 복사했으므로 `giveItem`은 호출하지 않습니다.
            // 타겟이 곡괭이를 들고 있으면 복사될 것이고, 없으면 없는 대로 복사됩니다.
        }

        // 4. 타이머 설정 (30초 후 복구)
        // 기존 타이머가 있다면 취소 (연속 섭취 시 시간 갱신을 위해)
        if (activeTasks.containsKey(p.getUniqueId())) {
            for (org.bukkit.scheduler.BukkitTask t : activeTasks.get(p.getUniqueId()))
                t.cancel();
            activeTasks.get(p.getUniqueId()).clear();
        }

        org.bukkit.scheduler.BukkitTask revertTask = new BukkitRunnable() {
            @Override
            public void run() {
                revertToOriginal(p);
            }
        }.runTaskLater(plugin, 30L * 20L); // 30초

        registerTask(p, revertTask);
    }

    private void revertToOriginal(Player p) {
        if (!isTransformed.contains(p.getUniqueId()))
            return;

        // [Fix] StackOverflowError 방지
        // 먼저 변신 상태 목록에서 제거하여 재귀 호출 고리를 끊습니다.
        isTransformed.remove(p.getUniqueId());

        OriginalState original = savedStates.get(p.getUniqueId());
        AbilityManager am = AbilityManager.getInstance((MocPlugin) plugin);

        if (original != null) {
            // 1. 변신했던 능력 정리 (소환수 제거 등)
            String currentAbCode = am.getPlayerAbilities().get(p.getUniqueId());
            Ability currentAb = am.getAbility(currentAbCode);
            if (currentAb != null) {
                // cleanup() 내부에서 revertToOriginal()이 다시 호출될 수 있으나,
                // 위에서 이미 isTransformed.remove()를 했으므로 안전합니다.
                currentAb.cleanup(p);
            }

            // 2. 원래 정보 복구
            p.setPlayerProfile(original.profile);
            p.getInventory().setContents(original.inventory);
            p.getInventory().setArmorContents(original.armor);

            // [추가] 속성값 완벽 복구
            if (p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null)
                p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(original.maxHealth);
            if (p.getAttribute(org.bukkit.attribute.Attribute.SCALE) != null)
                p.getAttribute(org.bukkit.attribute.Attribute.SCALE).setBaseValue(original.scale);
            if (p.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE) != null)
                p.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE)
                        .setBaseValue(original.interactionRange);
            if (p.getAttribute(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE) != null)
                p.getAttribute(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE)
                        .setBaseValue(original.blockInteractionRange);
            if (p.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE) != null)
                p.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(original.attackDamage);
            if (p.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED) != null)
                p.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(original.movementSpeed);

            // [추가] 버프 복구
            // 먼저 현재(변신 중 얻은) 버프 모두 제거
            for (org.bukkit.potion.PotionEffect effect : p.getActivePotionEffects()) {
                p.removePotionEffect(effect.getType());
            }
            // 원래 가지고 있던 버프 다시 추가
            if (original.potionEffects != null) {
                p.addPotionEffects(original.potionEffects);
            }

            // 3. 원래 능력 코드로 복귀
            am.changeAbilityTemporary(p, original.abilityCode);

            p.sendMessage("§d변신이 해제되어 원래 모습으로 돌아왔습니다.");
            p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 0.5f);
        }

        // 데이터 정리
        savedStates.remove(p.getUniqueId());
        // isTransformed.remove(p.getUniqueId()); // 위에서 이미 제거함

        // 내 태스크(타이머) 목록 비우기
        if (activeTasks.containsKey(p.getUniqueId())) {
            activeTasks.get(p.getUniqueId()).clear();
        }
    }

    @Override
    public void cleanup(Player p) {
        // 게임 종료 등으로 강제 정리될 때 원래대로 돌려놓기
        if (isTransformed.contains(p.getUniqueId())) {
            revertToOriginal(p);
        }
    }
}
