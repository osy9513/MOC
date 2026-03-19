package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Bee;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class Byunggu extends Ability {

    public Byunggu(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "081";
    }

    @Override
    public String getName() {
        return "이병구";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§e전투 ● 이병구(지구를 지켜라!)",
                "§f외계인에게 꿀을 던집니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e전투 ● 이병구(지구를 지켜라!)");
        p.sendMessage("§f꿀 병을 던집니다.");
        p.sendMessage("§f해당 꿀 병에 맞으면 7마리의 벌들이 공격 합니다.");
        p.sendMessage("§f양봉 모자를 쓰면 벌들에게 공격 당하지 않습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 7초.");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 꿀 병, 양봉 모자.");
        p.sendMessage("§f장비 제거 : 없음.");
    }

    @Override
    public void giveItem(Player p) {
        // 꿀 병 (벌집조각, 커스텀 모델 데이터 1)
        ItemStack ggul = new ItemStack(Material.HONEYCOMB);
        ItemMeta gMeta = ggul.getItemMeta();
        if (gMeta != null) {
            gMeta.setDisplayName("§e꿀 병");
            gMeta.setCustomModelData(1); // 리소스팩: ggul
            // 아이템 설명 (Lore) 추가
            gMeta.setLore(Arrays.asList(
                    "§7[ 우클릭 ] 꿀 병을 던집니다.",
                    "§7꿀 병에 맞은 대상에게 7마리의 벌을 소환합니다."));
            ggul.setItemMeta(gMeta);
        }
        p.getInventory().addItem(ggul);

        // 양봉 모자 (쇠사슬 투구) - 자동으로 투구 슬롯에 장착
        ItemStack hat = new ItemStack(Material.CHAINMAIL_HELMET);
        ItemMeta hMeta = hat.getItemMeta();
        if (hMeta != null) {
            hMeta.setDisplayName("§e양봉 모자");
            // 아이템 설명 (Lore) 추가
            hMeta.setLore(Arrays.asList(
                    "§7착용 시 벌에게 공격당하지 않습니다."));
            hat.setItemMeta(hMeta);
        }
        // [고도화] 인벤토리에 넣는 대신 투구 슬롯에 바로 장착
        p.getInventory().setHelmet(hat);
    }

    // 꿀 병 투척 (우클릭)
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.HONEYCOMB || !item.hasItemMeta())
            return;

        if ("§e꿀 병".equals(item.getItemMeta().getDisplayName())) {
            e.setCancelled(true); // 블록에 상호작용하는 것 방지

            if (isSilenced(p))
                return;

            if (!checkCooldown(p))
                return;

            setCooldown(p, 7.0);

            // 대사 출력
            Bukkit.broadcastMessage("§e이병구 : §f당신 외계인이지!!");
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SPLASH_POTION_THROW, 1f, 1f);

            // 노란색 스플래시 포션 던지기
            ThrownPotion potion = p.launchProjectile(ThrownPotion.class);
            ItemStack potionItem = new ItemStack(Material.SPLASH_POTION);
            PotionMeta pMeta = (PotionMeta) potionItem.getItemMeta();
            if (pMeta != null) {
                pMeta.setColor(Color.YELLOW);
                potionItem.setItemMeta(pMeta);
            }
            potion.setItem(potionItem);

            // 병구의 포션임을 알 수 있게 메타데이터 주입
            potion.setMetadata("ByungguPotion", new FixedMetadataValue(plugin, p.getUniqueId().toString()));
        }
    }

    // 포션이 터질 때 (범위 내의 모든 대상 검사 후 벌 소환)
    @EventHandler
    public void onPotionSplash(PotionSplashEvent e) {
        if (!e.getEntity().hasMetadata("ByungguPotion"))
            return;

        String shooterUuidStr = e.getEntity().getMetadata("ByungguPotion").get(0).asString();
        Player shooter = Bukkit.getPlayer(UUID.fromString(shooterUuidStr));

        // 포션 범위에 맞은 모든 엔티티들을 순회
        for (LivingEntity affected : e.getAffectedEntities()) {
            // // 본인 제외 // ㄴㄴ 본인도 맞을 수 있음 근데 양봉 모자가 있으니 공격을 안 당하는 거지 ㅋㅋ
            // if (shooter != null && affected.getUniqueId().equals(shooter.getUniqueId()))
            // continue;

            // [버그 수정] 벌(Bee) 자체는 대상에서 제외합니다!
            // 포션의 범위(getAffectedEntities)에는 주변의 모든 LivingEntity가 포함되기 때문에
            // 이전에 소환된 이병구의 벌들도 포함됩니다.
            // 이를 필터링하지 않으면 새 포션을 던질 때:
            // 1. 기존 벌들이 affected(타겟)로 잡힘
            // 2. 새 벌 7마리가 기존 벌들을 공격하도록 setTarget 설정
            // 3. 벌끼리 싸워서 기존 벌들이 죽어버림 (3마리 정도만 생존)
            if (affected instanceof Bee) {
                continue; // 벌은 포션 대상에서 완전히 제외
            }

            // 관전자 제외
            if (affected instanceof Player targetPlayer) {
                if (targetPlayer.getGameMode() == GameMode.SPECTATOR)
                    continue;
            }

            // 죽은 엔티티 제외
            if (affected.isDead())
                continue;

            // 피격된 대상 주변(살짝 위)에 7마리 벌 소환
            for (int i = 0; i < 7; i++) {
                Bee bee = affected.getWorld().spawn(affected.getLocation().add(0, 1.5, 0), Bee.class);
                bee.setAnger(Integer.MAX_VALUE); // INFINITE 분노 (약 3.4년 지속)
                bee.setTarget(affected); // 즉시 꿀 병 맞은 타겟을 공격하도록 설정
                bee.setMetadata("ByungguBee", new FixedMetadataValue(plugin, shooterUuidStr));

                // [고도화] setPersistent(true)를 설정하면 서버가 "몬스터가 너무 많다"는 이유로
                // 이 벌을 자동으로 삭제하지 않습니다. 다른 소환수(란가 늑대 등)와 동일한 방식.
                bee.setPersistent(true);

                // 만약 이병구 본인이 접속중이라면 라운드 종료시 제거하기 위해 태스크/엔티티 목록에 등록
                if (shooter != null) {
                    registerSummon(shooter, bee);
                }
            }
        }
    }

    // 양봉 모자를 쓴 유저는 벌의 대상에서 제외
    @EventHandler
    public void onBeeTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getEntity() instanceof Bee bee))
            return;

        if (e.getTarget() instanceof Player targetPlayer) {
            ItemStack helmet = targetPlayer.getInventory().getHelmet();
            if (helmet != null && helmet.getType() == Material.CHAINMAIL_HELMET && helmet.hasItemMeta()) {
                if ("§e양봉 모자".equals(helmet.getItemMeta().getDisplayName())) {
                    e.setCancelled(true); // 양봉 모자를 썼으면 대상에서 즉각 제외!
                    bee.setTarget(null);
                }
            }
        }
    }

    // 이병구의 벌이 상대를 공격할 때 처리
    @EventHandler
    public void onBeeDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Bee bee))
            return;
        if (!bee.hasMetadata("ByungguBee"))
            return;

        // [고도화] 양봉 모자를 쓴 플레이어가 피해를 받으려 할 때 강제로 차단합니다.
        // EntityTargetLivingEntityEvent 만으로는 이미 타겟이 설정된 상태에서 벌이
        // 달려와 공격하는 경우를 막을 수 없기 때문에 여기서도 반드시 체크합니다.
        if (e.getEntity() instanceof Player targetPlayer) {
            ItemStack helmet = targetPlayer.getInventory().getHelmet();
            if (helmet != null && helmet.getType() == Material.CHAINMAIL_HELMET && helmet.hasItemMeta()) {
                if ("§e양봉 모자".equals(helmet.getItemMeta().getDisplayName())) {
                    // 양봉 모자를 쓴 플레이어는 피해 완전 차단
                    e.setCancelled(true);
                    // 다음에 타겟을 잡으려 할 때도 막히도록 타겟도 초기화
                    bee.setTarget(null);
                    return;
                }
            }
        }

        // 위 모자 체크를 통과한 경우 킬 스코어를 이병구에게 귀속시킵니다.
        if (e.getEntity() instanceof LivingEntity target) {
            String ownerUuid = bee.getMetadata("ByungguBee").get(0).asString();
            target.setMetadata("MOC_LastKiller", new FixedMetadataValue(plugin, ownerUuid));
        }
    }
}
