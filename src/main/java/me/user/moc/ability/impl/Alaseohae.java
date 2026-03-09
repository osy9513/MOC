package me.user.moc.ability.impl;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;

public class Alaseohae extends Ability {

    public Alaseohae(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "H10";
    }

    @Override
    public String getName() {
        return "알아서해";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList("§e소주 킬러.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§b유틸 ● 알아서해(바집소)");
        p.sendMessage("§f소주병을 우클릭하면 전방에 소주병을 던져 술을 뿌립니다.");
        p.sendMessage("§f맞은 상대는 멀미 100와 채굴 피로 100가 10초간 걸립니다.");
        p.sendMessage("§f알아서해가 맞을 경우 멀미 3와 힘 3가 10초간 걸립니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 30초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 소주병");
        p.sendMessage("§f장비 제거 : 철칼");
    }

    @Override
    public void giveItem(Player p) {
        detailCheck(p);

        // 장비 제거: 철칼
        p.getInventory().remove(Material.IRON_SWORD);

        // 추가 장비: 소주병 (초록색 염료, CustomModelData 1)
        ItemStack soju = new ItemStack(Material.GREEN_DYE);
        ItemMeta meta = soju.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a소주병");
            meta.setCustomModelData(1); // 리소스팩: soju.json
            meta.setLore(Arrays.asList("§f우클릭을 하여", "§f전방에 소주병을 투척합니다."));
            soju.setItemMeta(meta);
        }
        p.getInventory().addItem(soju);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 자신이 알아서해인지, 그리고 능력 봉인 상태인지 침묵 검사
        if (!AbilityManager.getInstance().hasAbility(p, getCode())) {
            return;
        }

        if (isSilenced(p))
            return;

        // 우클릭 검사
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        // 손에 든 아이템 검사 (소주병)
        ItemStack handItem = p.getInventory().getItemInMainHand();
        if (handItem.getType() != Material.GREEN_DYE)
            return;

        ItemMeta meta = handItem.getItemMeta();
        if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != 1)
            return;

        e.setCancelled(true); // 기본 우클릭 작동 방지 (블록 설치 등)

        // 쿨타임 검사 (크리에이티브 통과 등 포함)
        if (!checkCooldown(p))
            return;

        // --------- 능력 발동 --------- //

        // 대사 출력
        Bukkit.broadcastMessage("§b알아서해: 네가 사는 거라면 나도 끼지~");

        // 소주병(투척용 포션 엔티티) 발사
        // ThrownPotion을 플레이어의 눈 위치 및 시선 기준 launchProjectile로 발사
        ThrownPotion thrownSoju = p.launchProjectile(ThrownPotion.class);

        // 날아가는 투사체 외형을 '소주병'으로 덮어씌움
        ItemStack visualItem = new ItemStack(Material.GREEN_DYE);
        ItemMeta visualMeta = visualItem.getItemMeta();
        if (visualMeta != null) {
            visualMeta.setCustomModelData(1);
            visualItem.setItemMeta(visualMeta);
        }
        thrownSoju.setItem(visualItem);

        // 투사체 식별을 위해 메타데이터 삽입
        thrownSoju.setMetadata("MOC_ALASEOHAE", new FixedMetadataValue(plugin, p.getUniqueId().toString()));

        // 투척 사운드
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 1.0f);

        // 쿨타임 30초 부여
        setCooldown(p, 30.0);
    }

    @EventHandler
    public void onPotionSplash(PotionSplashEvent e) {
        // 우리가 던진 알아서해 소주병인지 확인
        if (!e.getEntity().hasMetadata("MOC_ALASEOHAE")) {
            return;
        }

        // 효과 제거 (기본 포션 효과가 적용되는 것을 방지, 비어있겠지만 만약을 위해)
        e.setCancelled(true);

        // 깨지는 효과음
        e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.2f);

        // 물보라에 닿은 엔티티들에게 대상별 버프/디버프 부여
        for (LivingEntity target : e.getAffectedEntities()) {
            // 거리에 따른 영향력 보정(Intensity)을 무시하고 타겟 범위 안이면 무조건 10초 고정 부여
            applySojuEffect(target);
        }

        // 발사체 제거 (cancel 되더라도 깔끔하게 삭제)
        e.getEntity().remove();
    }

    /**
     * 타겟이 알아서해인지 판별하여 버프나 디버프를 부여하는 함수.
     */
    private void applySojuEffect(LivingEntity target) {
        if (!(target instanceof Player p)) {
            // 플레이어가 아닌 엔티티 (일반 몹 등) 에게는 디버프 (기능적 통일성)
            // 멀미2, 채굴피로2 (10초 = 200틱) // 멀미 2 = 레벨 1, 채굴 피로 2 = 레벨 1
            target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 1, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 200, 1, false, true, true));
            return;
        }

        // '알아서해' 능력을 갖고 있는지 검사
        boolean isAlaseohae = AbilityManager.getInstance().hasAbility(p, getCode());

        if (isAlaseohae) {
            // 알아서해 유저 (본인 혹은 다른 알아서해)
            // 멀미 3 (레벨 3), 힘 3 (레벨 3) (10초)
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 2, false, true, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 2, false, true, true));
            p.sendMessage("§a[소주 버프] §f힘 3 및 멀미 3가 적용되었습니다. 캬 취한다~!");
        } else {
            // 다른 능력자 유저 (적)
            // 멀미 100 (레벨 100), 채굴 피로 100 (레벨 100) (10초)
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 99, false, true, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 200, 99, false, true, true));
            p.sendMessage("§c[소주 디버프] §f술폭탄에 맞았습니다! 멀미 100, 채굴피로 100를 받습니다.");
        }
    }
}
