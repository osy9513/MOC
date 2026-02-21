package me.user.moc.ability.impl;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;

public class SungJinWoo extends Ability {

    public SungJinWoo(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "063";
    }

    @Override
    public String getName() {
        return "성진우";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§b유틸 ● 성진우(나 혼자만 레벨업)",
                "§fE급 헌터에서 국가권력급 헌터가 됩니다.");
    }

    @Override
    public void giveItem(Player p) {
        // 능력 발동 시 채팅에 출력될 메세지
        p.getServer().broadcastMessage("§f성진우 : 일어나라.");

        // 철칼 제거
        p.getInventory().remove(Material.IRON_SWORD);

        // 경험치 레벨 초기화 (레벨 1부터 시작할지, 0부터 갈지는 기획에 따라) -> E급이므로 0 정도로
        p.setLevel(0);
        p.setExp(0);

        // 무기 지급: 나이트 킬러 (철 검 기반, 파괴 불가)
        ItemStack nightKiller = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = nightKiller.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8나이트 킬러");
            meta.setUnbreakable(true);
            meta.setCustomModelData(14); // 위에서 설정한 14번
            nightKiller.setItemMeta(meta);
        }
        p.getInventory().addItem(nightKiller);

        // 10초마다 레벨업 스케줄러 실행
        startLevelUpTask(p);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§b유틸 ● 성진우(나 혼자만 레벨업)");
        p.sendMessage("§fE급 헌터에서 국가권력급 헌터가 됩니다.");
        p.sendMessage("§f10초마다 레벨이 1씩 오릅니다.");
        p.sendMessage("§f맨손 쉬프트 좌클릭 시 레벨을 소모하여 소환할 수 있는 선택 창이 출력됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 나이트 킬러(파괴 불가)");
        p.sendMessage("§f장비 제거 : 철 칼");
    }

    private void startLevelUpTask(Player p) {
        // 기존 태스크가 있다면 정리 (Ability 부모 클래스가 관리)
        if (activeTasks.containsKey(p.getUniqueId())) {
            List<BukkitTask> tasks = activeTasks.get(p.getUniqueId());
            for (BukkitTask pt : tasks) {
                if (pt != null && !pt.isCancelled())
                    pt.cancel();
            }
            activeTasks.remove(p.getUniqueId());
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    this.cancel();
                    return;
                }

                // MOC 시스템 상 게임 진행 여부 확인
                MocPlugin moc = (MocPlugin) plugin;
                if (moc.getGameManager() == null || !moc.getGameManager().isBattleStarted()) {
                    return; // 전투 시작 전에 렙 오르는 것 방지
                }

                // 관전자일 경우 취소
                if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    this.cancel();
                    return;
                }

                // 레벨업 (10초마다 1)
                p.setLevel(p.getLevel() + 1);
            }
        }.runTaskTimer(plugin, 200L, 200L); // 200틱 = 10초

        // 부모의 태스크 리스트에 등록하여 사망/리셋 시 자동 종료되도록 함
        List<BukkitTask> tList = activeTasks.getOrDefault(p.getUniqueId(), new java.util.ArrayList<>());
        tList.add(task);
        activeTasks.put(p.getUniqueId(), tList);
    }

    @org.bukkit.event.EventHandler
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!me.user.moc.ability.AbilityManager.getInstance((MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        if (!p.isSneaking())
            return;

        org.bukkit.event.block.Action action = e.getAction();
        if (action != org.bukkit.event.block.Action.LEFT_CLICK_AIR
                && action != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK)
            return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != Material.AIR && hand.getAmount() > 0)
            return; // 맨손 검사

        // 봉인 검사 및 게임 시작 여부 검사
        if (me.user.moc.ability.AbilityManager.silencedPlayers.contains(p.getUniqueId()))
            return;
        MocPlugin moc = (MocPlugin) plugin;
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            if (moc.getGameManager() == null || !moc.getGameManager().isBattleStarted())
                return;
        }

        // GUI 오픈
        openSummonGUI(p);
    }

    private void openSummonGUI(Player p) {
        org.bukkit.inventory.Inventory gui = org.bukkit.Bukkit.createInventory(null, 27, "§8그림자 군단 소환");

        // 아이템 종류, 레벨 제한 세팅 (총 12종)
        gui.setItem(0, createGuiItem(Material.ZOMBIE_HEAD, "§7[Lv.1] 일반 좀비 병사", p.getLevel() >= 1));
        gui.setItem(9, createGuiItem(Material.IRON_HELMET, "§f[Lv.2] 정예 좀비 병사", p.getLevel() >= 2));
        gui.setItem(18, createGuiItem(Material.DIAMOND_SWORD, "§b[Lv.7] 기사 좀비", p.getLevel() >= 7));

        gui.setItem(1, createGuiItem(Material.SKELETON_SKULL, "§7[Lv.3] 일반 스켈레톤 병사", p.getLevel() >= 3));
        gui.setItem(10, createGuiItem(Material.BOW, "§f[Lv.4] 정예 스켈레톤 병사", p.getLevel() >= 4));
        gui.setItem(19, createGuiItem(Material.DIAMOND_CHESTPLATE, "§b[Lv.8] 기사 스켈레톤", p.getLevel() >= 8));

        gui.setItem(2, createGuiItem(Material.STONE_BRICKS, "§7[Lv.5] 일반 좀벌레 병사", p.getLevel() >= 5));
        gui.setItem(11, createGuiItem(Material.IRON_BOOTS, "§f[Lv.6] 정예 좀벌레 병사", p.getLevel() >= 6));
        gui.setItem(20, createGuiItem(Material.GOLDEN_APPLE, "§b[Lv.9] 기사 좀벌레", p.getLevel() >= 9));

        gui.setItem(15, createGuiItem(Material.SADDLE, "§4[Lv.10] 장군 파괴수", p.getLevel() >= 10));
        gui.setItem(16, createGuiItem(Material.WITHER_SKELETON_SKULL, "§5[Lv.15] 원수 위더", p.getLevel() >= 15));
        gui.setItem(17, createGuiItem(Material.SCULK_SENSOR, "§c[Lv.20] 총군단장 워든", p.getLevel() >= 20));

        p.openInventory(gui);
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1f);
    }

    private ItemStack createGuiItem(Material mat, String name, boolean canAfford) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (canAfford) {
                meta.setLore(Arrays.asList("§a클릭하여 소환합니다."));
            } else {
                meta.setLore(Arrays.asList("§c레벨이 부족합니다."));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @org.bukkit.event.EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("§8그림자 군단 소환"))
            return;
        e.setCancelled(true); // 아이템 빼기 방지

        if (!(e.getWhoClicked() instanceof Player))
            return;
        Player p = (Player) e.getWhoClicked();

        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR)
            return;
        int slot = e.getRawSlot();
        if (slot >= 27)
            return; // 플레이어 인벤토리 클릭 시 무시

        int reqLevel = getRequiredLevel(slot);
        if (reqLevel == -1 || p.getLevel() < reqLevel) {
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            p.sendMessage("§c레벨이 부족합니다!");
            return;
        }

        // 레벨 차감
        p.setLevel(p.getLevel() - reqLevel);
        p.closeInventory();
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 0.8f);

        // 소환 로직 호출
        spawnShadow(p, slot);
    }

    private int getRequiredLevel(int slot) {
        switch (slot) {
            case 0:
                return 1;
            case 9:
                return 2;
            case 18:
                return 7;
            case 1:
                return 3;
            case 10:
                return 4;
            case 19:
                return 8;
            case 2:
                return 5;
            case 11:
                return 6;
            case 20:
                return 9;
            case 15:
                return 10;
            case 16:
                return 15;
            case 17:
                return 20;
            default:
                return -1;
        }
    }

    private void spawnShadow(Player owner, int slot) {
        org.bukkit.Location loc = owner.getLocation();
        org.bukkit.World w = owner.getWorld();
        org.bukkit.entity.LivingEntity summon = null;

        String name = "";

        switch (slot) {
            case 0:
                name = "일반 좀비 병사";
                summon = spawnZombie(w, loc, false, false);
                break;
            case 9:
                name = "정예 좀비 병사";
                summon = spawnZombie(w, loc, true, false);
                break;
            case 18:
                name = "기사 좀비";
                summon = spawnZombie(w, loc, false, true); // true, true에서 변경 (기사는 투구/갑옷 대신 풀셋)
                // 위 주석처럼 기사 좀비는 isKnight=true 로 들어가게 되어 spawnZombie() 파라미터가 수정 필요할수 있지만
                // 기존 기사 좀비 파라미터가 (w, loc, true, true) 이었고 코드를 그대로 쓰되 호출부만 바꾼다면:
                // 기존 기사 좀비는: spawnZombie(w, loc, true, true) [isElite=true, isKnight=true]
                summon = spawnZombie(w, loc, true, true);
                break;
            case 1:
                name = "일반 스켈레톤 병사";
                summon = spawnSkeleton(w, loc, false, false);
                break;
            case 10:
                name = "정예 스켈레톤 병사";
                summon = spawnSkeleton(w, loc, true, false);
                break;
            case 19:
                name = "기사 스켈레톤";
                summon = spawnSkeleton(w, loc, true, true);
                break;
            case 2:
                name = "일반 좀벌레 병사";
                summon = spawnSilverfish(w, loc, false, false);
                break;
            case 11:
                name = "정예 좀벌레 병사";
                summon = spawnSilverfish(w, loc, true, false);
                break;
            case 20:
                name = "기사 좀벌레";
                summon = spawnSilverfish(w, loc, true, true);
                break;
            case 15:
                name = "장군 파괴수";
                summon = (org.bukkit.entity.LivingEntity) w.spawnEntity(loc, org.bukkit.entity.EntityType.RAVAGER);
                summon.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(150);
                summon.setHealth(150);
                summon.getAttribute(org.bukkit.attribute.Attribute.SCALE).setBaseValue(1.3);
                break;
            case 16:
                name = "원수 위더";
                summon = (org.bukkit.entity.LivingEntity) w.spawnEntity(loc, org.bukkit.entity.EntityType.WITHER);
                summon.getAttribute(org.bukkit.attribute.Attribute.SCALE).setBaseValue(1.3);
                break;
            case 17:
                name = "총군단장 워든";
                summon = (org.bukkit.entity.LivingEntity) w.spawnEntity(loc, org.bukkit.entity.EntityType.WARDEN);
                summon.getAttribute(org.bukkit.attribute.Attribute.SCALE).setBaseValue(1.3);
                break;
        }

        if (summon != null) {
            // 커스텀 이름 및 발광, 파티클 이펙트 세팅
            summon.setCustomName("§8" + name);
            summon.setCustomNameVisible(true);
            summon.setGlowing(true); // 검푸른 발광 (바닐라 Glowing)

            // 1초간의 검푸른 파티클
            w.spawnParticle(org.bukkit.Particle.SOUL, loc.add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.05);

            // 타겟팅 방어 및 삭제 처리를 위한 태그와 Ability 연동
            summon.addScoreboardTag("SungJinWoo_Summon");
            registerSummon(owner, summon);
        }
    }

    private org.bukkit.entity.LivingEntity spawnZombie(org.bukkit.World w, org.bukkit.Location loc, boolean isElite,
            boolean isKnight) {
        org.bukkit.entity.Zombie z = (org.bukkit.entity.Zombie) w.spawnEntity(loc, org.bukkit.entity.EntityType.ZOMBIE);
        z.setBaby(false);
        if (isKnight) {
            z.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
            z.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            z.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        } else if (isElite) {
            z.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
            z.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SHOVEL));
        } else {
            z.getEquipment().setHelmet(new ItemStack(Material.ZOMBIE_HEAD)); // 햇빛 면역용
        }
        return z;
    }

    private org.bukkit.entity.LivingEntity spawnSkeleton(org.bukkit.World w, org.bukkit.Location loc, boolean isElite,
            boolean isKnight) {
        org.bukkit.entity.Skeleton s = (org.bukkit.entity.Skeleton) w.spawnEntity(loc,
                org.bukkit.entity.EntityType.SKELETON);
        ItemStack bow = new ItemStack(Material.BOW);
        if (isKnight) {
            s.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
            s.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.POWER, 2);
            bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PUNCH, 3);
            s.getEquipment().setItemInMainHand(bow);
        } else if (isElite) {
            s.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
            bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.POWER, 2);
            s.getEquipment().setItemInMainHand(bow);
        } else {
            s.getEquipment().setHelmet(new ItemStack(Material.SKELETON_SKULL));
        }
        return s;
    }

    private org.bukkit.entity.LivingEntity spawnSilverfish(org.bukkit.World w, org.bukkit.Location loc, boolean isElite,
            boolean isKnight) {
        org.bukkit.entity.Silverfish sf = (org.bukkit.entity.Silverfish) w.spawnEntity(loc,
                org.bukkit.entity.EntityType.SILVERFISH);
        // 장비장착이 안보이더라도 착용 판정을 위해 세팅
        if (sf.getEquipment() != null)
            sf.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));

        if (isKnight) {
            sf.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(40);
            sf.setHealth(40);
            sf.addPotionEffect(
                    new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
            sf.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION,
                    Integer.MAX_VALUE, 1)); // 재생 2
        } else if (isElite) {
            sf.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(35);
            sf.setHealth(35);
            sf.addPotionEffect(
                    new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        } else {
            sf.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(30);
            sf.setHealth(30);
        }
        return sf;
    }

    @org.bukkit.event.EventHandler
    public void onEntityTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent e) {
        org.bukkit.entity.Entity entity = e.getEntity();
        org.bukkit.entity.LivingEntity target = e.getTarget();

        if (target == null)
            return;

        // 타겟팅 주체가 내 소환수일 경우
        if (entity.getScoreboardTags().contains("SungJinWoo_Summon")) {
            // 다른 성진우의 소환수를 공격하려 하거나 성진우(누구든)를 공격하려 하면 취소
            if (target.getScoreboardTags().contains("SungJinWoo_Summon")
                    || (target instanceof Player && me.user.moc.ability.AbilityManager.getInstance((MocPlugin) plugin)
                            .hasAbility((Player) target, getCode()))) {
                e.setCancelled(true);
            }
            // 관전자 타겟팅 금지 방어
            if (target instanceof Player && ((Player) target).getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                e.setCancelled(true);
            }
        }

        // 타겟팅 당하는 대상이 내 소환수일 경우
        if (target.getScoreboardTags().contains("SungJinWoo_Summon")) {
            // 공격자가 성진우(나) 본인이거나 다른 성진우의 소환수라면 취소 (이중 방패)
            if (entity.getScoreboardTags().contains("SungJinWoo_Summon")
                    || (entity instanceof Player && me.user.moc.ability.AbilityManager.getInstance((MocPlugin) plugin)
                            .hasAbility((Player) entity, getCode()))) {
                e.setCancelled(true);
            }
        }
    }

    @org.bukkit.event.EventHandler
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        // 팀킬 광역기 데미지 방지
        org.bukkit.entity.Entity damager = e.getDamager();
        org.bukkit.entity.Entity victim = e.getEntity();

        // 투사체 처리 (스켈레톤의 화살 등)
        if (damager instanceof org.bukkit.entity.Projectile) {
            org.bukkit.projectiles.ProjectileSource shooter = ((org.bukkit.entity.Projectile) damager).getShooter();
            if (shooter instanceof org.bukkit.entity.Entity) {
                damager = (org.bukkit.entity.Entity) shooter;
            }
        }

        boolean damagerIsSungJinWoo = false;
        if (damager instanceof Player) {
            damagerIsSungJinWoo = me.user.moc.ability.AbilityManager.getInstance((MocPlugin) plugin)
                    .hasAbility((Player) damager, getCode());
        } else if (damager.getScoreboardTags() != null && damager.getScoreboardTags().contains("SungJinWoo_Summon")) {
            damagerIsSungJinWoo = true;
        }

        boolean victimIsSungJinWoo = false;
        if (victim instanceof Player) {
            victimIsSungJinWoo = me.user.moc.ability.AbilityManager.getInstance((MocPlugin) plugin)
                    .hasAbility((Player) victim, getCode());
        } else if (victim.getScoreboardTags() != null && victim.getScoreboardTags().contains("SungJinWoo_Summon")) {
            victimIsSungJinWoo = true;
        }

        if (damagerIsSungJinWoo && victimIsSungJinWoo) {
            e.setCancelled(true); // 서로 공격 불가
        }
    }
}
