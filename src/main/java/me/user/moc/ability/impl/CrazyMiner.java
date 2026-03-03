package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CrazyMiner extends Ability {

    private final Map<UUID, List<Location>> playerMines = new HashMap<>();

    public CrazyMiner(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "069";
    }

    @Override
    public String getName() {
        return "크레이지마이너";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§6전투 ● 크레이지마이너(로스트사가)",
                "§f지뢰를 설치합니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§6전투 ● 크레이지마이너(로스트사가)");
        p.sendMessage("§f무기를 들고 블럭을 우클릭 시 해당 블럭에 지뢰를 설치합니다.");
        p.sendMessage("§f본인을 제외한 생명체가 지뢰 범위를 밟거나");
        p.sendMessage("§f지뢰가 설치된 블럭이 파괴되면 지뢰가 즉시 폭발하여");
        p.sendMessage("§f3x3 범위에 피격 무적 무시 8의 데미지를 주고 위로 띄웁니다.");
        p.sendMessage("§f이미 지뢰가 있는 블럭에 다시 설치하면 즉시 폭발합니다.");
        p.sendMessage("§f지뢰는 최대 6개까지 설치 가능하며 본인에게만 보입니다.");
        p.sendMessage("§f크레이지마이너 의상을 입으면 폭발 피해에 면역이 됩니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 8초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 크레이지마이너 무기, 크레이지마이너 의상");
        p.sendMessage("§f장비 제거 : 철 칼, 철 흉갑");
    }

    @Override
    public void giveItem(Player p) {
        detailCheck(p);
        super.cleanup(p);

        // 기본 장비 제거
        p.getInventory().remove(Material.IRON_SWORD);
        p.getInventory().setChestplate(new ItemStack(Material.AIR));

        // 크레이지마이너 무기 (철 삽)
        ItemStack weapon = new ItemStack(Material.IRON_SHOVEL);
        ItemMeta weaponMeta = weapon.getItemMeta();
        if (weaponMeta != null) {
            weaponMeta.setDisplayName("§6크레이지마이너 무기");
            weaponMeta.setUnbreakable(true);
            weaponMeta.setCustomModelData(2); // mine.png 지정 (CustomModelData 2)
            weapon.setItemMeta(weaponMeta);
        }
        p.getInventory().addItem(weapon);

        // 크레이지마이너 의상 (가죽 갑옷)
        ItemStack armor = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemMeta armorMeta = armor.getItemMeta();
        if (armorMeta != null) {
            armorMeta.setDisplayName("§6크레이지마이너 의상");
            armorMeta.setUnbreakable(true);
            armorMeta.setLore(Arrays.asList("§7지뢰저항 - 폭발에 면역"));
            armor.setItemMeta(armorMeta);
        }
        p.getInventory().setChestplate(armor);

        // 기존 지뢰 초기화
        playerMines.put(p.getUniqueId(), new ArrayList<>());

        // 지뢰 감지 및 파티클 태스크 시작
        startMineTask(p);
    }

    private void startMineTask(Player p) {
        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (p == null || !p.isOnline() || p.isDead() || !hasAbility(p)) {
                    this.cancel();
                    playerMines.remove(p.getUniqueId());
                    return;
                }

                List<Location> mines = playerMines.getOrDefault(p.getUniqueId(), new ArrayList<>());
                if (mines.isEmpty())
                    return;

                // 1. 파티클 표시 (4틱마다, 본인에게만)
                if (ticks % 4 == 0) {
                    for (Location loc : mines) {
                        showMineParticles(p, loc);
                    }
                }
                ticks++;

                // 2. 밟음 판정
                List<Location> toRemove = new ArrayList<>();
                for (Location loc : mines) {
                    // 블럭의 중심(0.5, 0.5, 0.5)을 기준으로 대상 엔티티 검색
                    // 블럭 주변 0.6 반경 내에 들어오면 밟은 것으로 간주 (블럭 충돌 박스 안쪽)
                    Location center = loc.clone().add(0.5, 0.5, 0.5);
                    for (Entity entity : loc.getWorld().getNearbyEntities(center, 0.6, 0.6, 0.6)) {
                        if (entity instanceof LivingEntity le && !entity.equals(p)) {
                            if (le instanceof Player targetP && targetP.getGameMode() == GameMode.SPECTATOR) {
                                continue;
                            }
                            // 밟았음! 폭발
                            explodeMine(p, loc);
                            toRemove.add(loc);
                            break; // 한 지뢰는 한 번만 터짐
                        }
                    }
                }
                mines.removeAll(toRemove);
            }
        };
        activeTasks.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(task.runTaskTimer(plugin, 1L, 1L));
    }

    private void explodeMine(Player owner, Location loc) {
        World world = loc.getWorld();
        Location center = loc.clone().add(0.5, 0.5, 0.5);

        // 폭발 사운드
        world.playSound(loc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        // 3x3 데미지 주는 범위 만큼 전부 폭발 이펙트 주기
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    world.spawnParticle(Particle.EXPLOSION, center.clone().add(x, y, z), 2, 0.2, 0.2, 0.2, 0.0);
                }
            }
        }

        // 3x3 범위 데미지 판정 (1.5 반경)
        for (Entity entity : world.getNearbyEntities(center, 1.5, 1.5, 1.5)) {
            if (entity instanceof LivingEntity le) {
                if (le instanceof Player targetP && targetP.getGameMode() == GameMode.SPECTATOR) {
                    continue; // 관전자 무시
                }

                // 크레이지마이너 의상 착용자 면역 (지뢰저항)
                if (le instanceof Player targetP) {
                    ItemStack chest = targetP.getInventory().getChestplate();
                    if (chest != null && chest.getType() == Material.LEATHER_CHESTPLATE && chest.hasItemMeta()
                            && chest.getItemMeta().hasLore()) {
                        if (chest.getItemMeta().getLore().contains("§7지뢰저항 - 폭발에 면역")) {
                            continue; // 데미지 무시
                        }
                    }
                }

                // 피격 무적 무시 8 폭발 데미지
                le.setNoDamageTicks(0);
                if (le instanceof Player targetP && !targetP.equals(owner)) {
                    // 킬 연동 추적기
                    targetP.setMetadata("MOC_LastKiller",
                            new org.bukkit.metadata.FixedMetadataValue(plugin, owner.getUniqueId().toString()));
                }
                // 데미지 소스를 넣으면 킬 로그도 원활하게 처리됨
                le.damage(8.0, owner);

                // 맞은 대상 위로 4칸 정도 띄우기 (Y축 속도 1.0 부여)
                org.bukkit.util.Vector velocity = le.getVelocity();
                velocity.setY(1.0);
                le.setVelocity(velocity);
            }
        }
    }

    private void showMineParticles(Player p, Location blockLoc) {
        double cx = blockLoc.getX() + 0.5;
        double cy = blockLoc.getY() + 0.5;
        double cz = blockLoc.getZ() + 0.5;
        Particle.DustOptions dust = new Particle.DustOptions(Color.RED, 1.0f);

        // 6 방향 표면 중앙에 레드스톤 파티클 (0.51 오프셋으로 살짝 튀어나오게)
        p.spawnParticle(Particle.DUST, cx, cy + 0.51, cz, 1, 0, 0, 0, 0, dust); // Top
        p.spawnParticle(Particle.DUST, cx, cy - 0.51, cz, 1, 0, 0, 0, 0, dust); // Bottom
        p.spawnParticle(Particle.DUST, cx, cy, cz - 0.51, 1, 0, 0, 0, 0, dust); // North
        p.spawnParticle(Particle.DUST, cx, cy, cz + 0.51, 1, 0, 0, 0, 0, dust); // South
        p.spawnParticle(Particle.DUST, cx + 0.51, cy, cz, 1, 0, 0, 0, 0, dust); // East
        p.spawnParticle(Particle.DUST, cx - 0.51, cy, cz, 1, 0, 0, 0, 0, dust); // West
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND)
            return; // 주사용 손만 인식
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (!hasAbility(p))
            return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.IRON_SHOVEL)
            return;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()
                || !item.getItemMeta().getDisplayName().equals("§6크레이지마이너 무기"))
            return;

        // 게임 진행 중 발동
        boolean isGameStarted = me.user.moc.game.GameManager.getInstance((MocPlugin) plugin).isBattleStarted();
        if (!isGameStarted && p.getGameMode() != GameMode.CREATIVE) {
            return;
        }

        e.setCancelled(true);

        // 쿨타임 체크 (설치 쿨타임 8초)
        if (!checkCooldown(p))
            return;
        setCooldown(p, 8);

        // 채팅 출력
        plugin.getServer().broadcastMessage("§6크레이지마이너: 스페셜지뢰!");

        Location clickedBlockLoc = e.getClickedBlock().getLocation();
        List<Location> mines = playerMines.getOrDefault(p.getUniqueId(), new ArrayList<>());

        // 기존 설치된 지뢰인지 확인
        if (mines.contains(clickedBlockLoc)) {
            explodeMine(p, clickedBlockLoc);
            mines.remove(clickedBlockLoc);
            return;
        }

        // 지뢰 추가 (최대 6개 선입선출 제한)
        if (mines.size() >= 6) {
            mines.remove(0); // 가장 오래된 지뢰 삭제
        }
        mines.add(clickedBlockLoc);
        playerMines.put(p.getUniqueId(), mines);
    }

    private boolean hasAbility(Player p) {
        return AbilityManager.getInstance().hasAbility(p, getCode());
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        if (p != null) {
            playerMines.remove(p.getUniqueId());
        }
    }

    @Override
    public void reset() {
        super.reset();
        playerMines.clear();
    }

    @EventHandler
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p))
            return;

        // 피해 원인이 폭발 관련인지 확인
        org.bukkit.event.entity.EntityDamageEvent.DamageCause cause = e.getCause();
        if (cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.CUSTOM) {

            // 크레이지마이너 의상 착용 여부 검사 (지뢰저항)
            ItemStack chest = p.getInventory().getChestplate();
            if (chest != null && chest.getType() == Material.LEATHER_CHESTPLATE && chest.hasItemMeta()
                    && chest.getItemMeta().hasLore()) {
                if (chest.getItemMeta().getLore().contains("§7지뢰저항 - 폭발에 면역")) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent e) {
        Location loc = e.getBlock().getLocation();

        // 지뢰가 설치된 블럭인지 확인
        for (Map.Entry<UUID, List<Location>> entry : playerMines.entrySet()) {
            if (entry.getValue().contains(loc)) {
                Player owner = plugin.getServer().getPlayer(entry.getKey());
                if (owner != null && owner.isOnline()) {
                    explodeMine(owner, loc);
                }
                entry.getValue().remove(loc);
                break;
            }
        }
    }
}
