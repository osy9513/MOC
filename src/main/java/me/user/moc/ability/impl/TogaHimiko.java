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
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
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
        // [추가] 무적 시간 백업
        int maxNoDamageTicks;

        // [버그 수정] 방어 관련 속성 백업 (이름 충돌 방지: attr 접두사 사용)
        double attrArmor;
        double attrArmorToughness;
        double attrKnockbackResistance;
    }

    // 변신 전 원본 상태 저장소
    private final Map<UUID, OriginalState> savedStates = new HashMap<>();
    // 현재 변신 중인 플레이어 목록
    private final Set<UUID> isTransformed = new HashSet<>();

    // [Fix] 변신 도중(능력 교체 중) cleanup에 의해 revertTask가 취소되는 것을 방지하기 위한 플래그
    private final Set<UUID> ignoringCleanup = new HashSet<>();

    public boolean isTransformed(UUID uuid) {
        return isTransformed.contains(uuid);
    }

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
        // 기존 철 칼 제거 (지급받는 단검 사용 유도)
        p.getInventory().remove(Material.IRON_SWORD);

        ItemStack dagger = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = dagger.getItemMeta();
        meta.displayName(Component.text("§c토가의 단검"));
        meta.lore(Arrays.asList(Component.text("§7공격 시 피를 빨아드리는 장치가 장착되어 있다.")));
        meta.setCustomModelData(3); // 리소스팩: togahimiko
        dagger.setItemMeta(meta);

        p.getInventory().addItem(dagger);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§d복합 ● 토가 히미코(나의 히어로 아카데미아)");
        p.sendMessage("§f개성 - 변신을 얻습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f토가의 단검으로 다른 유저를 공격 시");
        p.sendMessage("§f인벤토리에 공격한 유저의 닉네임이 적힌 마실 수 있는 포션이 생긴다.");
        p.sendMessage("§f해당 포션을 우클릭 시 마시고 30초 동안 상대로 변신한다.");
        p.sendMessage("§f상대의 능력을 사용할 수 있고 30초 후 다시 토가 히미코로 돌아온다.");
        p.sendMessage(" ");
        p.sendMessage("§f유저 포션을 마실 때 체력 4칸을 회복한다.");
        // p.sendMessage("§f모든 포션을 80% 더 빨리 마신다."); // 삭제됨
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초.");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 토가의 단검");
        p.sendMessage("§f장비 제거 : 철 칼");
    }

    // [이벤트 1] 공격 시 피(포션) 뽑기
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker) || !(e.getEntity() instanceof Player victim)) {
            return;
        }

        // [Fix] 관전자는 대상에서 제외
        if (victim.getGameMode() == org.bukkit.GameMode.SPECTATOR)
            return;

        // 공격자가 토가 히미코인지 확인 (변신 중에는 피를 뽑을 수 없음 - 원작 고증 혹은 밸런스)
        AbilityManager am = AbilityManager.getInstance((MocPlugin) plugin);
        if (!am.hasAbility(attacker, getCode()) || isTransformed.contains(attacker.getUniqueId())) {
            return;
        }

        // [추가] 토가의 단검으로 공격했는지 확인
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.getType() != Material.IRON_SWORD || weapon.getItemMeta() == null)
            return;

        // 이름 체크 (색상 코드 제외하고 비교하거나 포함 여부 확인)
        // Component 처리 필요하지만 일단 Legacy DisplayName으로 확인
        // Paper 1.21.11에서는 displayName()을 권장하지만, 기존 코드 스타일 유지
        // "§c토가의 단검"
        Component dispName = weapon.getItemMeta().displayName();
        String plainName = (dispName != null) ? ((net.kyori.adventure.text.TextComponent) dispName).content() : "";

        // Legacy or Plain check
        if (!plainName.contains("토가의 단검") &&
                !(weapon.getItemMeta().hasDisplayName() && weapon.getItemMeta().getDisplayName().contains("토가의 단검"))) {
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
            meta.lore(Arrays.asList(Component.text("§7마시면 30초간 해당 플레이어로 변신합니다.")));
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

        // [수정] 인벤토리 저장 시, 현재 마시고 있는 포션(손에 들고 있는 것)은 제외하고 저장
        // 원인: 변신 해제 시 이 저장된 인벤토리가 덮어씌워지는데, 마시기 전 상태(포션 있음)로 저장되어 포션이 복구됨.
        ItemStack[] contents = p.getInventory().getContents();
        ItemStack handItem = p.getInventory().getItemInMainHand();

        // 메인 핸드 아이템이 혈액 포션인지 확인 (이름 등으로 대략 확인)
        if (handItem.getType() == Material.POTION && handItem.getItemMeta() != null) {
            // 간단하게 현재 손으 슬롯을 찾아서 그 부분만 수량을 줄이거나 제거한 배열을 만듦
            // 인벤토리 복사본 생성
            contents = new ItemStack[p.getInventory().getContents().length];
            for (int i = 0; i < p.getInventory().getContents().length; i++) {
                ItemStack it = p.getInventory().getContents()[i];
                if (it != null) {
                    contents[i] = it.clone();
                }
            }

            // 메인 핸드 슬롯 찾기
            int slot = p.getInventory().getHeldItemSlot();
            if (contents[slot] != null && contents[slot].isSimilar(handItem)) {
                if (contents[slot].getAmount() > 1) {
                    contents[slot].setAmount(contents[slot].getAmount() - 1);
                } else {
                    contents[slot] = null;
                }
            }
        }

        state.inventory = contents;
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

        // [버그 수정] 방어력(Armor) 및 방어 강도(Toughness) 백업 추가
        // 1.21.11 대응: Registry 사용
        Attribute armorAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.armor"));
        Attribute toughnessAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.armor_toughness"));
        Attribute knockbackAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.knockback_resistance"));

        state.attrArmor = (armorAttr != null && p.getAttribute(armorAttr) != null)
                ? p.getAttribute(armorAttr).getValue()
                : 0.0;
        state.attrArmorToughness = (toughnessAttr != null && p.getAttribute(toughnessAttr) != null)
                ? p.getAttribute(toughnessAttr).getValue()
                : 0.0;
        state.attrKnockbackResistance = (knockbackAttr != null && p.getAttribute(knockbackAttr) != null)
                ? p.getAttribute(knockbackAttr).getValue()
                : 0.0;

        state.potionEffects = p.getActivePotionEffects(); // 현재 버프 목록 복사
        state.maxNoDamageTicks = p.getMaximumNoDamageTicks(); // 무적 시간 설정 백업

        savedStates.put(p.getUniqueId(), state);
        isTransformed.add(p.getUniqueId());
    }

    private void transformToTarget(Player p, Player target, AbilityManager am) {
        // 1. 프로필(스킨, 닉네임) 복사
        PlayerProfile targetProfile = target.getPlayerProfile();
        p.setPlayerProfile(targetProfile);

        // [버그 수정] 스킨 및 닉네임 변경 사항이 클라이언트에 즉시 반영되도록 리프레시
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.hidePlayer(plugin, p);
            online.showPlayer(plugin, p);
        }
        // [추가] 본인 클라이언트 갱신을 위한 인벤토리 업데이트
        p.updateInventory();

        // 2. 능력 복사 및 교체
        String targetAbCode = am.getPlayerAbilities().get(target.getUniqueId());
        // 만약 타겟이 능력이 없거나 오류 상태면 기본값 혹은 유지? 현재는 복사 시도
        if (targetAbCode != null) {
            // [Fix] 능력 교체 시 cleanup이 호출되는데, 이때 revertTask가 취소되거나 즉시 원복되는 것을 방지
            ignoringCleanup.add(p.getUniqueId());

            // [주입] 토가 히미코 전용 격리 코드 처리
            String actualCodeToGive = targetAbCode;
            if (targetAbCode.equals("018")) { // 리무루
                actualCodeToGive = "TH018";
            } else if (targetAbCode.equals("028")) { // 북극곰
                actualCodeToGive = "TH028";
            } else if (targetAbCode.equals("016")) { // 퉁퉁퉁사후르
                actualCodeToGive = "TH016";
            }

            am.changeAbilityTemporary(p, actualCodeToGive);
            ignoringCleanup.remove(p.getUniqueId());

            // [중요] 능력을 부여받았으므로 초기화 로직(소환, 태스크 시작 등)을 수행해야 합니다.
            // [중요] 능력을 부여받았으므로 초기화 로직(소환, 태스크 시작 등)을 수행해야 합니다.
            Ability newAbility = am.getAbility(actualCodeToGive);
            if (newAbility != null) {
                newAbility.giveItem(p);
                // giveItem이 인벤토리를 건드리지만, 아래에서 타겟 인벤토리로 덮어씌웁니다.
                // 핵심은 giveItem 내부의 사이드 이펙트(소환, 태스크 등)를 실행하는 것입니다.
            }

            // 3. 인벤토리 복사
            p.getInventory().setContents(target.getInventory().getContents());
            p.getInventory().setArmorContents(target.getInventory().getArmorContents());

            // [고도화 1] 변경된 능력 상세 설명 출력
            if (newAbility != null) {
                p.sendMessage(" ");
                p.sendMessage("§a당신이 변신한 §e" + target.getName() + "§a 의 능력은 아래와 같습니다.");
                p.sendMessage(" ");
                newAbility.detailCheck(p);
            }
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
            // [중요 수정] 순서 변경 및 지연 복구
            // 1. 먼저 능력을 교체합니다. (이 과정에서 기존 능력의 cleanup이 호출됨)
            // 이때 리무루/북극곰 cleanup이 호출되어 MaxHealth가 20으로 초기화될 수 있음.
            am.changeAbilityTemporary(p, original.abilityCode);

            // 2. 1틱 후에 원래 상태를 덮어씌웁니다.
            // cleanup의 부작용(체력 리셋 등)을 확실히 이긴 뒤에 적용하기 위함입니다.
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!p.isOnline())
                        return;

                    // 정보 복구
                    p.setPlayerProfile(original.profile);

                    // 스킨 리프레시
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        online.hidePlayer(plugin, p);
                        online.showPlayer(plugin, p);
                    }
                    p.updateInventory();

                    p.getInventory().setContents(original.inventory);
                    p.getInventory().setArmorContents(original.armor);

                    // [핵심] 속성값 완벽 복구
                    // cleanup 후이므로 안전하게 MaxHealth를 원래대로(예: 60) 돌려놓을 수 있음.
                    if (p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                        p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(original.maxHealth);
                    }

                    // 최대 체력 복구 후 현재 체력 설정
                    double restoredMax = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                    p.setHealth(Math.min(original.health, restoredMax));

                    // 나머지 속성 복구
                    if (p.getAttribute(org.bukkit.attribute.Attribute.SCALE) != null)
                        p.getAttribute(org.bukkit.attribute.Attribute.SCALE).setBaseValue(original.scale);
                    if (p.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE) != null)
                        p.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE)
                                .setBaseValue(original.interactionRange);
                    if (p.getAttribute(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE) != null)
                        p.getAttribute(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE)
                                .setBaseValue(original.blockInteractionRange);
                    if (p.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE) != null)
                        p.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE)
                                .setBaseValue(original.attackDamage);
                    if (p.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED) != null)
                        p.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED)
                                .setBaseValue(original.movementSpeed);

                    // 방어 속성 복구
                    Attribute armorAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.armor"));
                    Attribute toughnessAttr = Registry.ATTRIBUTE
                            .get(NamespacedKey.minecraft("generic.armor_toughness"));
                    Attribute knockbackAttr = Registry.ATTRIBUTE
                            .get(NamespacedKey.minecraft("generic.knockback_resistance"));

                    if (armorAttr != null && p.getAttribute(armorAttr) != null)
                        p.getAttribute(armorAttr).setBaseValue(original.attrArmor);
                    if (toughnessAttr != null && p.getAttribute(toughnessAttr) != null)
                        p.getAttribute(toughnessAttr).setBaseValue(original.attrArmorToughness);
                    if (knockbackAttr != null && p.getAttribute(knockbackAttr) != null)
                        p.getAttribute(knockbackAttr).setBaseValue(original.attrKnockbackResistance);

                    // 버프 복구
                    for (org.bukkit.potion.PotionEffect effect : p.getActivePotionEffects()) {
                        p.removePotionEffect(effect.getType());
                    }
                    if (original.potionEffects != null) {
                        p.addPotionEffects(original.potionEffects);
                    }

                    // 무적 시간 복구
                    p.setMaximumNoDamageTicks(original.maxNoDamageTicks);
                    p.setNoDamageTicks(0);

                    p.sendMessage("§d변신이 해제되어 원래 모습으로 돌아왔습니다.");
                    p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 0.5f);

                    // 데이터 정리 (지연 실행 내에서)
                    savedStates.remove(p.getUniqueId());

                    // 내 태스크(타이머) 목록 비우기 (부모 cleanup에서 안지워졌을 경우 대비)
                    if (activeTasks.containsKey(p.getUniqueId())) {
                        activeTasks.get(p.getUniqueId()).clear();
                    }
                }
            }.runTaskLater(plugin, 1L); // 1틱 지연
        } else {
            // original이 없는 경우(거의 없겠지만) 그냥 상태만 지움
            savedStates.remove(p.getUniqueId());
            isTransformed.remove(p.getUniqueId());
        }
    }

    @Override
    public void reset() {
        // [Fix] 게임이 끝날 때(혹은 시작될 때) 변신 중인 모든 플레이어를 원상복구합니다.
        // 변경된 능력을 가진 상태라 cleanup()이 호출되지 않았을 수 있기 때문입니다.
        for (UUID uuid : new HashSet<>(isTransformed)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                revertToOriginal(p);
            }
        }
        isTransformed.clear();
        savedStates.clear();
        ignoringCleanup.clear();

        super.reset();
    }

    @Override
    public void cleanup(Player p) {
        // [Fix] 변신 프로세스 중(능력 교체)에 호출된 cleanup이면 무시
        if (ignoringCleanup.contains(p.getUniqueId())) {
            super.cleanup(p); // 부모 cleanup (태스크 취소 등)은 수행하되
            return; // revertToOriginal은 하지 않음
        }

        // 게임 종료 등으로 강제 정리될 때 원래대로 돌려놓기
        if (isTransformed.contains(p.getUniqueId())) {
            revertToOriginal(p);
        }
        super.cleanup(p);
    }

    // [추가] 사망 시 변신 해제 (원래 모습 복구)
    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (isTransformed.contains(p.getUniqueId())) {
            revertToOriginal(p);
            // 킬 점수는 GameManager에서 처리되므로 여기선 모습 복구만 신경씀.
            // 다만 GameManager.onDeath가 먼저 호출되는지, 이게 먼저인지 순서에 따라
            // 킬러 정보(getKiller)가 달라질 수 있으나, 변신 여부와 상관없이 Player 객체는 동일하므로 OK.
        }
    }

    @Override
    public void onGameEnd(Player p) {
        // [Fix] 라운드 승리 시, 승리 메시지에 변신 이름이 아닌 본캐 닉네임이 뜨도록 강제 복구
        if (isTransformed.contains(p.getUniqueId())) {
            revertToOriginal(p);
        }
    }

    /**
     * 특정 플레이어가 토가 히미코의 '변신 상태'인지 확인하는 헬퍼 메서드
     * (다른 능력 클래스에서 소환수 이름 변경 등에 사용)
     */
    public static boolean isToga(Player p) {
        if (p == null)
            return false;
        AbilityManager am = AbilityManager.getInstance();
        if (am == null)
            return false;
        // 047은 TogaHimiko 코드
        Ability ab = am.getAbility("047");
        if (ab instanceof TogaHimiko toga) {
            return toga.isTransformed(p.getUniqueId());
        }
        return false;
    }
}
