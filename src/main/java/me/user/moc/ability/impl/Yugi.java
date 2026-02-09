package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import java.util.Arrays;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public class Yugi extends Ability {

    private final Random random = new Random();

    // [엑조디아 스택] (플레이어 UUID -> 스택 수)
    private final java.util.Map<UUID, Integer> exodiaCounts = new java.util.HashMap<>();

    public Yugi(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "029";
    }

    @Override
    public String getName() {
        return "유희";
    }

    @Override
    public List<String> getDescription() {
        List<String> list = new ArrayList<>();
        list.add("§d복합 ● 유희(유희왕)");
        list.add(" ");
        list.add("§f듀얼을 합니다.");
        return list;
    }

    @Override
    public void giveItem(Player p) {
        // [장비 지급] 카드 덱 (사육사 갑옷 장식 - 1.21 아이템)
        // 사육사 갑옷 장식은 WOLF_ARMOR? ARADILLO_SCUTE?
        // 그냥 1.21의 WOLF_ARMOR(늑대 갑옷)를 사용하거나 일반 아이템에 CMD를 씌우는 게 안전.
        // 기획서: "사육사 갑옷 장식"
        // Material.WOLF_ARMOR 가 있으나 1.21.11 기준 확인 필요.
        // 일단 안전하게 LEATHER_HORSE_ARMOR 사용하거나 기획자가 원하는 아이템으로.
        // "사육사 갑옷 장식"은 1.20+ Armor Trim Smithing Template(Dune, etc)?
        // 여기선 기획서가 명확하지 않으므로, 설명에 충실하게 '카드 덱'이라는 이름의 아이템 지급.
        // 마인크래프트 1.21에 'WOLF_ARMOR'가 가장 적절해 보임 (겹쳐질 수 있음).
        // [UPDATE] 유저 요청으로 네더라이트 파편으로 변경.

        ItemStack deck = new ItemStack(Material.NETHERITE_SCRAP);
        ItemMeta meta = deck.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6카드 덱");
            meta.setLore(Arrays.asList("§7우클릭하여 카드를 뽑습니다.", "§7뽑은 카드를 우클릭하여 능력을 사용합니다."));
            meta.setCustomModelData(1); // 리소스팩: yugi0
            deck.setItemMeta(meta);
        }
        p.getInventory().addItem(deck);

        // 초기화
        exodiaCounts.put(p.getUniqueId(), 0);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§d복합 ● 유희(유희왕)");
        p.sendMessage("§f카드 덱을 우클릭 시 아래의 확률에 따라 랜덤한 카드를 뽑습니다.");
        p.sendMessage("§f이미 카드가 있는 경우, 가지고 있는 카드는 없어지고 카드를 뽑습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f20% 빛의 봉인 검 - 전방에 구속 3이 3초간 걸리는 화살 3개를 3갈래로 발사");
        p.sendMessage("§f20% 번개 - 조준선이 가르키는 블럭에 번개을 침");
        p.sendMessage("§f20% 빅 실드 가드너 - 빅 실드 가드너 소환 (아군을 지켜줌)");
        p.sendMessage("§f20% 크리보 - 크리보 소환 (크리보가 살아있으면 유희에게 데미지가 들어오지 않음)");
        p.sendMessage("§f15% 죽은 자의 소생 - 불사의 토템 획득");
        p.sendMessage("§f5% 엑조디아 - 엑조디아를 5 번 뽑으면 엑조드 파이어를 발사합니다");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 7초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 카드 덱(네더라이트 파편)");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void reset() {
        super.reset();
        exodiaCounts.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.NETHERITE_SCRAP)
            return; // 카드 덱 확인

        if (e.getAction().toString().contains("RIGHT")) {
            // 쿨타임 체크 (7초)
            if (!checkCooldown(p))
                return;

            // 드로우 실행
            drawCard(p);

            setCooldown(p, 7);
        }
    }

    private void drawCard(Player p) {
        // "유희 : 나의 턴, 드로우!" 전체 메시지
        plugin.getServer().sendMessage(net.kyori.adventure.text.Component.text("§e유희 : 나의 턴, 드로우!"));
        p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1f, 1f); // 발사기 소리

        // 종이 아이템 지급 (이전 카드는 삭제하라고 했으나, 구체적으로 인벤토리의 종이를 찾아 지우는 로직 필요)
        // 여기선 "새로운 카드가 들어오면 사실상 덮어쓰기" 개념으로 메시지 띄우고 즉시 효과 발동이 더 자연스러울 수 있음.
        // 하지만 기획서에 "우클릭 시 해당 능력 발동"이라고 되어 있는데,
        // 위 `onInteract`는 '카드 덱' 우클릭임.
        // "카드 덱을 우클릭 시... 랜덤한 카드를 뽑습니다. ... 종이에 뽑힌 카드명 출력, 우클릭 시 해당 능력 발동."
        // 아하, 2단계 구조임.
        // 1. 덱(늑대갑옷) 우클릭 -> 종이 카드 지급.
        // 2. 종이 카드 우클릭 -> 실제 스킬 발동.

        // 기존 종이 제거
        for (ItemStack invItem : p.getInventory().getContents()) {
            if (invItem != null && invItem.getType() == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE) {
                p.getInventory().remove(invItem);
            }
        }

        // 확률 계산
        int r = random.nextInt(100);
        String cardName = "";

        if (r < 20) { // 0-19: 빛의 봉인 검
            cardName = "빛의 봉인 검";
        } else if (r < 40) { // 20-39: 번개
            cardName = "번개";
        } else if (r < 60) { // 40-59: 빅 실드 가드너
            cardName = "빅 실드 가드너";
        } else if (r < 80) { // 60-79: 크리보
            cardName = "크리보";
        } else if (r < 95) { // 80-94: 죽은 자의 소생
            cardName = "죽은 자의 소생";
        } else { // 95-99: 엑조디아
            cardName = "엑조디아";
        }

        // 카드 지급 (네더라이트 강화 - 1.20+)
        ItemStack card = new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        ItemMeta meta = card.getItemMeta();
        meta.setDisplayName("§b" + cardName);
        meta.setLore(Arrays.asList("§7우클릭하여 능력을 발동합니다."));

        // [추가] 리소스팩 데이터 설정
        int cmd = 0;
        switch (cardName) {
            case "빛의 봉인 검":
                cmd = 1;
                break; // yugi1
            case "번개":
                cmd = 2;
                break; // yugi2
            case "죽은 자의 소생":
                cmd = 3;
                break; // yugi3
            case "크리보":
                cmd = 4;
                break; // yugi4
            case "빅 실드 가드너":
                cmd = 5;
                break; // yugi5
            case "엑조디아":
                cmd = 6;
                break; // yugi6
        }
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }

        card.setItemMeta(meta);
        p.getInventory().addItem(card);

        p.sendMessage("§a카드를 뽑았습니다: " + cardName);
    }

    // [카드 사용 이벤트]
    @EventHandler
    public void onCardUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode()))
            return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE)
            return;
        if (!e.getAction().toString().contains("RIGHT"))
            return;

        String name = item.getItemMeta().getDisplayName();
        if (name == null)
            return;

        // 카드 이름 파싱 (색상 코드 제거)
        String rawName = ChatColor.stripColor(name);

        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f); // 종이 넘기는 소리

        boolean success = true;

        switch (rawName) {
            case "빛의 봉인 검":
                useSwords(p);
                break;
            case "번개":
                success = useLightning(p);
                break;
            case "빅 실드 가드너":
                useGardna(p);
                break;
            case "크리보":
                useKuriboh(p);
                break;
            case "죽은 자의 소생":
                useReborn(p);
                break;
            case "엑조디아":
                useExodia(p);
                // 엑조디아는 스택만 쌓고 아이템은 사라짐 (또는 5번째에 발사)
                // "5번 뽑으면 발사" -> 뽑기만 해도 스택? 아니면 써야 스택?
                // "엑조디아 사용 시 1/5 카운팅" -> 써야 함.
                break;
        }

        // 카드 소모 (성공했을 때만)
        if (success) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    // --- 카드 구현 ---

    private void useSwords(Player p) {
        // 전방에 구속 3 화살 3개 3갈래
        Vector dir = p.getLocation().getDirection();
        Vector left = dir.clone().rotateAroundY(Math.toRadians(15));
        Vector right = dir.clone().rotateAroundY(Math.toRadians(-15));

        spawnArrow(p, dir);
        spawnArrow(p, left);
        spawnArrow(p, right);
        p.sendMessage("§e빛의 봉인 검!");
    }

    private void spawnArrow(Player p, Vector dir) {
        Arrow arrow = p.launchProjectile(Arrow.class, dir.multiply(1.5));
        arrow.setGlowing(true);
        // 구속 3 (Slowness 2 = -45% speed? Slowness 3 approx -60%)
        // PotionEffectType.SLOWNESS, Amplifier 2 (Level 3)
        arrow.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2), true);
    }

    private boolean useLightning(Player p) {
        Block target = p.getTargetBlockExact(50);
        if (target != null && target.getType() != Material.AIR) {
            p.getWorld().strikeLightning(target.getLocation());
            p.sendMessage("§e번개!");
            return true;
        } else {
            p.sendMessage("§c50블록 이내의 블록을 바라보세요.");
            return false;
        }
    }

    private Location getSafeSummonLocation(Player p) {
        Location loc = p.getLocation();
        Vector dir = loc.getDirection();
        dir.setY(0);

        if (dir.lengthSquared() > 0) {
            dir.normalize();
        } else {
            // 수직으로 보고 있어서 X,Z가 0인 경우 시선 방향(Pitch포함) 사용
            dir = loc.getDirection().setY(0); // 여전히 0이면?
            if (dir.lengthSquared() == 0)
                dir = new Vector(1, 0, 0); // fallback
        }

        // 전방 1.5칸 + 위로 0.5칸
        return loc.add(dir.multiply(1.5)).add(0, 0.5, 0);
    }

    private void useGardna(Player p) {
        // 철골렘 소환
        // [Fix] 땅 파묻힘 방지: 시선 방향 수평으로 1.5칸 앞 + 0.5칸 위
        Location spawnLoc = getSafeSummonLocation(p);

        IronGolem golem = (IronGolem) p.getWorld().spawnEntity(spawnLoc, EntityType.IRON_GOLEM);
        golem.setCustomName("§b빅 실드 가드너");
        golem.setCustomNameVisible(true);
        golem.setPlayerCreated(true);
        registerSummon(p, golem);
        p.sendMessage("§e빅 실드 가드너 소환!");

        // AI 로직(아군을 때리는 적 공격)은 EntityDamageByEntityEvent에서 처리
    }

    private void useKuriboh(Player p) {
        // 거대 갈색 양
        // [Fix] 땅 파묻힘 방지
        Location spawnLoc = getSafeSummonLocation(p);

        Sheep sheep = (Sheep) p.getWorld().spawnEntity(spawnLoc, EntityType.SHEEP);
        sheep.setCustomName("§6크리보");
        sheep.setCustomNameVisible(true);
        sheep.setColor(DyeColor.BROWN);

        // [Fix] Scale attribute might not be available in this build environment
        // if (sheep.getAttribute(Attribute.GENERIC_SCALE) != null) {
        // sheep.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(3.0);
        // }

        // [Fix] Use MAX_HEALTH as GENERIC_MAX_HEALTH caused compilation error (likely
        // older API version)
        if (sheep.getAttribute(Attribute.MAX_HEALTH) != null) {
            sheep.getAttribute(Attribute.MAX_HEALTH).setBaseValue(6.0); // 체력 3칸 (6)
        }
        sheep.setHealth(6.0);

        registerSummon(p, sheep);

        // 저항 5 부여 (상시 유지는 아님, '살아있는 동안')
        // 매 초마다 양이 살아있는지 확인 버프 리필
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!sheep.isValid() || sheep.isDead()) {
                    this.cancel();
                    return;
                }
                // 저항 5 (Damage reduction 100% in vanilla usually capped, but let's give Res 4)
                // Res 4 = 80%, Res 5 = 100% God mode.
                // 기획: "데미지가 들어오지 않음" -> Res 5 (Resistance 255 ok)
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 4, false, false, true));
            }
        }.runTaskTimer(plugin, 0L, 20L);

        p.sendMessage("§e크리보 소환!");
    }

    private void useReborn(Player p) {
        ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);
        p.getInventory().addItem(totem);
        p.sendMessage("§e죽은 자의 소생: 불사의 토템 획득!");
    }

    private void useExodia(Player p) {
        int count = exodiaCounts.getOrDefault(p.getUniqueId(), 0) + 1;
        exodiaCounts.put(p.getUniqueId(), count);

        p.sendMessage("§e엑조디아 파츠 수집: " + count + "/5");

        if (count >= 5) {
            // 엑조드 파이어 발동 프로세스 시작
            startExodiaSequence(p);
            exodiaCounts.put(p.getUniqueId(), 0); // 초기화
        }
    }

    private void startExodiaSequence(Player p) {
        // 시퀀스 실행
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                // p.isOnline() 체크 필요

                if (step == 0) {
                    plugin.getServer()
                            .sendMessage(net.kyori.adventure.text.Component.text("§e유희 : 내가 뽑은 카드는 §6§l봉인된 엑조디아!"));
                    // 노란 오라 파티클
                    p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 50, 0.5, 1, 0.5,
                            new Particle.DustOptions(Color.YELLOW, 2));
                } else if (step == 1) {
                    plugin.getServer()
                            .sendMessage(net.kyori.adventure.text.Component.text("§e유희 : 지금 다섯 장의 카드가 모두 모였어!"));
                } else if (step == 2) {
                    plugin.getServer()
                            .sendMessage(net.kyori.adventure.text.Component.text("§e유희 : §c§l분노의 불꽃, 엑조드 파이어!!!!"));
                    fireExodFire(p);
                } else {
                    this.cancel();
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 40L); // 2초마다 (40틱)
    }

    private void fireExodFire(Player p) {
        // 10초간 발사
        final Vector dir = p.getLocation().getDirection().normalize();
        final Location startLoc = p.getEyeLocation();

        new BukkitRunnable() {
            int duration = 0; // 10초 = 200틱

            @Override
            public void run() {
                if (duration >= 200 || !p.isOnline() || p.isDead()) {
                    this.cancel();
                    return;
                }

                // [Fix] 매 틱마다 플레이어의 시선 방향으로 업데이트
                Location currentLoc = p.getEyeLocation();
                Vector currentDir = currentLoc.getDirection().normalize();

                // 빔 발사 (매 틱)
                // 길이 60, 두께 5x5
                // 최적화를 위해 1블럭 간격으로 60번 루프
                for (double d = 0; d < 60; d += 1.0) {
                    Location point = currentLoc.clone().add(currentDir.clone().multiply(d));

                    // 벽 관통 불가: 블록 만나면 멈춤 (하지만 파괴한다고 했음)
                    // "블럭을 관통하지 못하여... 맞은 해당 블럭은 0.5초 뒤 부셔짐"
                    if (point.getBlock().getType().isSolid()) {
                        // 블록 파괴 예약
                        breakBlockLater(point.getBlock());
                        break; // 빔 끊김
                    }

                    // 파티클 (노랑 메인, 주황 서브)
                    // 5x5 범위에 랜덤 확산
                    p.getWorld().spawnParticle(Particle.DUST, point, 5, 2.5, 2.5, 2.5,
                            new Particle.DustOptions(Color.YELLOW, 2));
                    p.getWorld().spawnParticle(Particle.DUST, point, 2, 2.0, 2.0, 2.0,
                            new Particle.DustOptions(Color.ORANGE, 1));

                    // 대미지 판정 (매 3틱마다)
                    if (duration % 3 == 0) {
                        for (Entity e : p.getWorld().getNearbyEntities(point, 2.5, 2.5, 2.5)) {
                            if (e instanceof LivingEntity le && e != p && !isAlly(p, e)) {
                                le.damage(6.0, p); // 3칸 = 6 대미지
                                le.setNoDamageTicks(0);
                            }
                        }
                    }
                }

                duration++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void breakBlockLater(Block b) {
        // 0.5초 뒤 파괴 배드락도 부숨.
        if (/* b.getType() == Material.BEDROCK || */ b.getType() == Material.BARRIER)
            return;

        new BukkitRunnable() {
            @Override
            public void run() {
                b.setType(Material.AIR);
                b.getWorld().spawnParticle(Particle.BLOCK, b.getLocation(), 10, b.getBlockData());
                b.getWorld().playSound(b.getLocation(), Sound.BLOCK_STONE_BREAK, 1f, 1f);
            }
        }.runTaskLater(plugin, 10L);
    }

    private boolean isAlly(Player p, Entity e) {
        // 소환수(빅실드, 크리보)인지 확인
        // 또는 같은 팀 로직 (현재 팀 로직은 없음, 소환수만 보호)
        if (e.getCustomName() != null && (e.getCustomName().contains("빅 실드") || e.getCustomName().contains("크리보"))) {
            return true;
        }
        return false;
    }

    // [이벤트] 빅 실드 가드너 / 크리보 보호 로직
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        // 1. 아군 공격 시 빅 실드 가드너 반격
        if (e.getEntity() instanceof Player victim) {
            // 내 소환수가 있는지 확인
            // (복잡해서 생략 가능하나, 기획: "아군을 때리는 상대를 때림")
            // 가드너가 주변에 있으면 타겟 변경
            for (Entity nearby : victim.getNearbyEntities(10, 10, 10)) {
                if (nearby instanceof IronGolem golem && golem.isValid() && golem.getCustomName() != null
                        && golem.getCustomName().contains("빅 실드 가드너")) {
                    // 주인이 맞았거나, 크리보가 맞았거나, 가드너가 맞았거나..
                    // 여기선 간단히 '범인이 유희가 아니면' 공격
                    if (e.getDamager() instanceof LivingEntity attacker && attacker != victim) {
                        golem.setTarget(attacker);
                    }
                }
            }
        }
    }
}
