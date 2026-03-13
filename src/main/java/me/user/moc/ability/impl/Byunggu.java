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
                "§c전투 ● 이병구(지구를 지켜라!)",
                "§f외계인에게 꿀을 던집니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§c전투 ● 이병구(지구를 지켜라!)");
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
        detailCheck(p);

        // 꿀 병 (벌집조각, 커스텀 모델 데이터 1)
        ItemStack ggul = new ItemStack(Material.HONEYCOMB);
        ItemMeta gMeta = ggul.getItemMeta();
        if (gMeta != null) {
            gMeta.setDisplayName("§e꿀 병");
            gMeta.setCustomModelData(1); // 리소스팩: ggul
            ggul.setItemMeta(gMeta);
        }
        p.getInventory().addItem(ggul);

        // 양봉 모자 (쇠사슬 투구)
        ItemStack hat = new ItemStack(Material.CHAINMAIL_HELMET);
        ItemMeta hMeta = hat.getItemMeta();
        if (hMeta != null) {
            hMeta.setDisplayName("§e양봉 모자");
            hat.setItemMeta(hMeta);
        }
        p.getInventory().addItem(hat);
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
            Bukkit.broadcastMessage("§c이병구 : §f당신 외계인이지!!");
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

    // 이병구의 벌이 상대를 공격할 때, 킬 스코어가 이병구에게 귀속되도록 셋팅
    @EventHandler
    public void onBeeDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Bee bee) {
            if (bee.hasMetadata("ByungguBee")) {
                if (e.getEntity() instanceof LivingEntity target) {
                    String ownerUuid = bee.getMetadata("ByungguBee").get(0).asString();
                    target.setMetadata("MOC_LastKiller", new FixedMetadataValue(plugin, ownerUuid));
                }
            }
        }
    }
}
