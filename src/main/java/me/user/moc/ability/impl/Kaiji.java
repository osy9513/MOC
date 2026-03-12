package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.*;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Kaiji extends Ability {

    private final String CODE = "077";
    private final String NAME = "카이지(도박묵시록 카이지)";
    private final int COOLDOWN = 10;

    // 가위바위보 상태 관리
    private final Map<UUID, UUID> gambleTargets = new ConcurrentHashMap<>(); // 누가(키) 누구와(값) 도박 중인지
    private final Map<UUID, GambleSession> activeSessions = new ConcurrentHashMap<>();

    // UI 아이템 이름
    private final String TITLE = "§c[가위 바위 보]";
    private final String SCISSORS = "§e가위";
    private final String ROCK = "§e바위";
    private final String PAPER = "§e보";

    public Kaiji(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§2혹합 ● 카이지(도박묵시록 카이지)",
                "§f인생을 건 도박을 합니다.");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§2혹합 ● 카이지(도박묵시록 카이지)");
        p.sendMessage("§f인생을 건 도박을 합니다.");
        p.sendMessage("§f ");
        p.sendMessage("§f다이아몬드로 플레이어를 가격 시 상대와 나는 5초간 가위 바위 보를 합니다.");
        p.sendMessage("§f고르지 못한 경우 패배 처리되며, 둘 다 고르지 못한 경우 카이지의 승리입니다.");
        p.sendMessage("§f가위 바위 보 도중엔 무적 상태이며 이긴 사람은 진 사람의 모든 아이템을 가져갑니다.");
        p.sendMessage("§f무승부일 경우 재경기를 진행합니다.");
        p.sendMessage("§f ");
        p.sendMessage("§f쿨타임 : " + COOLDOWN + "초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 다이아몬드");
        p.sendMessage("§f장비 제거 : 없음");
    }

    @Override
    public void giveItem(Player p) {
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        ItemMeta meta = diamond.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b인생의 도박 (다이아몬드)");
            meta.setLore(Arrays.asList("§f플레이어를 가격 시 가위바위보를 시작합니다."));
            diamond.setItemMeta(meta);
        }
        p.getInventory().addItem(diamond);
        detailCheck(p);
    }

    @Override
    public void reset() {
        super.reset();
        for (GambleSession session : activeSessions.values()) {
            session.cancel();
        }
        activeSessions.clear();
        gambleTargets.clear();
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
        UUID uuid = p.getUniqueId();

        // 내가 카이지이거나 타겟인 세션 찾기
        GambleSession currentSession = null;
        for (GambleSession session : activeSessions.values()) {
            if (session.kaijiUuid.equals(uuid) || session.targetUuid.equals(uuid)) {
                currentSession = session;
                break;
            }
        }

        if (currentSession != null) {
            currentSession.cancel();
            activeSessions.remove(currentSession.kaijiUuid);
            gambleTargets.remove(currentSession.kaijiUuid);
            gambleTargets.remove(currentSession.targetUuid);
        }
    }

    // 도박 강제 종료 무적 방어: 데미지 이벤트에서 막음
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (gambleTargets.containsKey(p.getUniqueId())) {
                e.setCancelled(true); // 도박 중 무적
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player target))
            return;
        if (!(e.getDamager() instanceof Player p))
            return;

        if (isSilenced(p))
            return;
        if (target.getGameMode() == GameMode.SPECTATOR)
            return;
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;

        // 아이템 확인
        ItemStack mainHand = p.getInventory().getItemInMainHand();
        if (mainHand.getType() != Material.DIAMOND)
            return;

        // 진행중 & 쿨타임 확인
        if (gambleTargets.containsKey(p.getUniqueId())) {
            p.sendMessage("§c이미 가위바위보가 진행 중입니다!");
            e.setCancelled(true);
            return;
        }
        if (gambleTargets.containsKey(target.getUniqueId())) {
            p.sendMessage("§c상대방이 이미 도박 중입니다!");
            e.setCancelled(true);
            return;
        }

        if (!checkCooldown(p))
            return;

        // 타격 데미지 무효 (도박 시작 목적이므로)
        e.setCancelled(true);

        // 쿨타임 부여
        if (p.getGameMode() != GameMode.CREATIVE) {
            setCooldown(p, COOLDOWN);
        }

        // 도박 시작!
        startGambleSession(p, target);
    }

    private void startGambleSession(Player kaiji, Player target) {
        UUID kaijiUuid = kaiji.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        // 등록
        gambleTargets.put(kaijiUuid, targetUuid);
        gambleTargets.put(targetUuid, kaijiUuid);

        // 대사 방송
        Bukkit.broadcastMessage("§c카이지 : §f이건 단순한 가위 바위 보가 아니야!! 인생을 건 가위 바위 보다!!!");

        // 소리 & 이펙트
        kaiji.playSound(kaiji.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.0f);
        target.playSound(target.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.0f);

        // 세션 생성
        GambleSession session = new GambleSession(kaiji, target);
        activeSessions.put(kaijiUuid, session);
        session.start();
    }

    // 인벤토리(가위바위보) 클릭 처리 이벤트
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p))
            return;
        UUID uuid = p.getUniqueId();

        if (e.getView().getTitle().equals(TITLE)) {
            e.setCancelled(true); // 템 가져가기 방지

            if (!gambleTargets.containsKey(uuid)) {
                p.closeInventory();
                return;
            }

            ItemStack clickedItem = e.getCurrentItem();
            if (clickedItem == null || !clickedItem.hasItemMeta())
                return;

            String itemName = clickedItem.getItemMeta().getDisplayName();
            String choice = null;

            if (itemName.equals(SCISSORS)) {
                choice = "가위";
            } else if (itemName.equals(ROCK)) {
                choice = "바위";
            } else if (itemName.equals(PAPER)) {
                choice = "보";
            }

            if (choice != null) {
                // 내 세션 찾기
                GambleSession session = null;
                for (GambleSession s : activeSessions.values()) {
                    if (s.kaijiUuid.equals(uuid) || s.targetUuid.equals(uuid)) {
                        session = s;
                        break;
                    }
                }

                if (session != null) {
                    session.setChoice(uuid, choice);

                    // UI 피드백 (대기 상태로 변경 등)
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 2.0f);
                    p.sendMessage("§e[" + choice + "] §f(을)를 선택했습니다. 상대방을 기다리는 중...");

                    // 인벤토리를 닫으면 다시 열렸을 때 취소 이벤트가 꼬이지 않도록,
                    // 선택 완료를 의미하는 배리어 블록 같은 걸로 덮어씌울 수 있지만,
                    // 가장 확실한 건 창을 닫아주고 다시 못 열게 하는 것임
                    p.closeInventory();
                }
            }
        }
    }

    // UI 닫힘 방지 (아직 선택 안했는데 창을 닫으면 강제로 다시 염)
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p))
            return;
        if (e.getView().getTitle().equals(TITLE)) {
            UUID uuid = p.getUniqueId();
            if (gambleTargets.containsKey(uuid)) {
                // 확인
                GambleSession session = null;
                for (GambleSession s : activeSessions.values()) {
                    if (s.kaijiUuid.equals(uuid) || s.targetUuid.equals(uuid)) {
                        session = s;
                        break;
                    }
                }

                if (session != null) {
                    // 세션이 끝난(결과 처리 중인) 상태면 닫아도 상관 없음
                    if (session.isFinished)
                        return;

                    // 만약 이 사람이 아직 선택을 안 했다면 강제로 다시 열기
                    if (uuid.equals(session.kaijiUuid) && session.kaijiChoice == null) {
                        Bukkit.getScheduler().runTaskLater(plugin, session::openUIForKaiji, 1L);
                    } else if (uuid.equals(session.targetUuid) && session.targetChoice == null) {
                        Bukkit.getScheduler().runTaskLater(plugin, session::openUIForTarget, 1L);
                    }
                }
            }
        }
    }

    // ============================================
    // 내부 클래스: 가위바위보 세션 상태 및 로직 관리
    // ============================================
    private class GambleSession {
        UUID kaijiUuid;
        UUID targetUuid;
        String kaijiChoice = null;
        String targetChoice = null;

        BukkitTask timerTask;
        BukkitTask displayTask;

        ItemDisplay kaijiDisplay; // 카이지 머리 위
        ItemDisplay targetDisplay; // 타겟 머리 위

        int timeRemain = 5;
        boolean isFinished = false;

        public GambleSession(Player kaiji, Player target) {
            this.kaijiUuid = kaiji.getUniqueId();
            this.targetUuid = target.getUniqueId();
        }

        public void start() {
            Player kaiji = Bukkit.getPlayer(kaijiUuid);
            Player target = Bukkit.getPlayer(targetUuid);
            if (kaiji == null || target == null) {
                cancel();
                return;
            }

            // 1. 머리 위에 다이아몬드 디스플레이 생성
            spawnDisplays(kaiji, target);

            // 2. UI 열기
            openUIForKaiji();
            openUIForTarget();

            // 3. 타이머 스케줄러 시작 (5초 카운트다운)
            timerTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (isFinished) {
                        this.cancel();
                        return;
                    }

                    if (timeRemain <= 0) {
                        // 시간 종료 -> 결과 판정
                        evaluateResult();
                        this.cancel();
                        return;
                    }

                    // 카운트다운 알림
                    Player k = Bukkit.getPlayer(kaijiUuid);
                    Player t = Bukkit.getPlayer(targetUuid);
                    if (k != null)
                        k.sendTitle("§e" + timeRemain, "§f가위바위보를 선택하세요!", 0, 25, 0);
                    if (t != null)
                        t.sendTitle("§e" + timeRemain, "§f가위바위보를 선택하세요!", 0, 25, 0);

                    timeRemain--;
                }
            }.runTaskTimer(plugin, 0L, 20L); // 1초마다

            registerTask(kaiji, timerTask); // 능력 해제시 안전장치
        }

        private void spawnDisplays(Player kaiji, Player target) {
            ItemStack diamond = new ItemStack(Material.DIAMOND);

            kaijiDisplay = (ItemDisplay) kaiji.getWorld().spawnEntity(kaiji.getLocation().add(0, 3.0, 0),
                    EntityType.ITEM_DISPLAY);
            kaijiDisplay.setItemStack(diamond);
            kaijiDisplay.setBillboard(Billboard.CENTER);
            registerSummon(kaiji, kaijiDisplay);

            targetDisplay = (ItemDisplay) target.getWorld().spawnEntity(target.getLocation().add(0, 3.0, 0),
                    EntityType.ITEM_DISPLAY);
            targetDisplay.setItemStack(diamond);
            targetDisplay.setBillboard(Billboard.CENTER);
            registerSummon(target, targetDisplay);

            // 빙빙 도는 애니메이션 (2초 = 40틱당 1바퀴 = 틱당 9도 회전)
            displayTask = new BukkitRunnable() {
                int tick = 0;

                @Override
                public void run() {
                    if (isFinished) {
                        this.cancel();
                        return;
                    }
                    Player k = Bukkit.getPlayer(kaijiUuid);
                    Player t = Bukkit.getPlayer(targetUuid);

                    if (k != null && kaijiDisplay != null && !kaijiDisplay.isDead()) {
                        Location loc = k.getLocation().add(0, 2.5, 0);
                        kaijiDisplay.teleport(loc);
                        // 세로 회전(X 또는 Z축)
                        float angle = (float) Math.toRadians(tick * 9);
                        Transformation trans = kaijiDisplay.getTransformation();
                        trans.getLeftRotation().set(new AxisAngle4f(angle, new Vector3f(1, 0, 0))); // X축 회전 적용
                        kaijiDisplay.setTransformation(trans);
                    }
                    if (t != null && targetDisplay != null && !targetDisplay.isDead()) {
                        Location loc = t.getLocation().add(0, 2.5, 0);
                        targetDisplay.teleport(loc);
                        float angle = (float) Math.toRadians(tick * 9);
                        Transformation trans = targetDisplay.getTransformation();
                        trans.getLeftRotation().set(new AxisAngle4f(angle, new Vector3f(1, 0, 0))); // X축 회전 적용
                        targetDisplay.setTransformation(trans);
                    }
                    tick++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
            registerTask(kaiji, displayTask);
        }

        public void openUIForKaiji() {
            Player p = Bukkit.getPlayer(kaijiUuid);
            if (p != null)
                p.openInventory(createRPSInventory());
        }

        public void openUIForTarget() {
            Player p = Bukkit.getPlayer(targetUuid);
            if (p != null)
                p.openInventory(createRPSInventory());
        }

        private Inventory createRPSInventory() {
            Inventory inv = Bukkit.createInventory(null, 9, TITLE);

            ItemStack scissors = new ItemStack(Material.SHEARS);
            ItemMeta scissorsMeta = scissors.getItemMeta();
            if (scissorsMeta != null) {
                scissorsMeta.setDisplayName(SCISSORS);
                scissors.setItemMeta(scissorsMeta);
            }
            inv.setItem(2, scissors);

            ItemStack rock = new ItemStack(Material.COBBLESTONE);
            ItemMeta rockMeta = rock.getItemMeta();
            if (rockMeta != null) {
                rockMeta.setDisplayName(ROCK);
                rock.setItemMeta(rockMeta);
            }
            inv.setItem(4, rock);

            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta paperMeta = paper.getItemMeta();
            if (paperMeta != null) {
                paperMeta.setDisplayName(PAPER);
                paper.setItemMeta(paperMeta);
            }
            inv.setItem(6, paper);

            return inv;
        }

        public void setChoice(UUID uuid, String choice) {
            if (uuid.equals(kaijiUuid))
                kaijiChoice = choice;
            else if (uuid.equals(targetUuid))
                targetChoice = choice;

            // 둘 다 골랐으면 타이머 즉시 종료 및 결과로
            if (kaijiChoice != null && targetChoice != null && !isFinished) {
                if (timerTask != null && !timerTask.isCancelled())
                    timerTask.cancel();
                evaluateResult();
            }
        }

        private void evaluateResult() {
            isFinished = true;

            Player kaiji = Bukkit.getPlayer(kaijiUuid);
            Player target = Bukkit.getPlayer(targetUuid);

            if (kaiji != null)
                kaiji.closeInventory();
            if (target != null)
                target.closeInventory();

            // 대사 방송 (가위 바위 보!!!)
            Bukkit.broadcastMessage("§c카이지 : §f안 내면 진 거! 가위 바위 보!!!");

            // 미선택에 대한 페널티 판정 로직
            final int WIN_KAIJI = 1;
            final int WIN_TARGET = 2;
            final int DRAW = 0;

            final int finalResult; // 누가 이겼나?

            if (kaijiChoice == null && targetChoice == null) {
                // 둘 다 안 냄 -> 카이지 승리
                kaijiChoice = "안 냄(패배)";
                targetChoice = "안 냄(패배)";
                finalResult = WIN_KAIJI;
            } else if (kaijiChoice == null) {
                kaijiChoice = "안 냄(패배)";
                finalResult = WIN_TARGET;
            } else if (targetChoice == null) {
                targetChoice = "안 냄(패배)";
                finalResult = WIN_KAIJI;
            } else {
                // 정상적으로 낸 경우
                if (kaijiChoice.equals(targetChoice)) {
                    finalResult = DRAW;
                } else if ((kaijiChoice.equals("가위") && targetChoice.equals("보")) ||
                        (kaijiChoice.equals("바위") && targetChoice.equals("가위")) ||
                        (kaijiChoice.equals("보") && targetChoice.equals("바위"))) {
                    finalResult = WIN_KAIJI;
                } else {
                    finalResult = WIN_TARGET;
                }
            }

            // 1초 뒤에 결과 발표 처리!
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                String targetName = (target != null) ? target.getName() : "상대방";

                Bukkit.broadcastMessage("§c카이지 §f: " + kaijiChoice);
                Bukkit.broadcastMessage("§e" + targetName + " §f: " + targetChoice);

                if (finalResult == DRAW) {
                    if (kaiji != null)
                        kaiji.sendMessage("§e[무승부!] 다시 가위바위보를 진행합니다.");
                    if (target != null)
                        target.sendMessage("§e[무승부!] 다시 가위바위보를 진행합니다.");

                    // 재시작 (무적 상태 유지)
                    isFinished = false;
                    timeRemain = 5;
                    kaijiChoice = null;
                    targetChoice = null;
                    start();
                    return;

                } else if (finalResult == WIN_KAIJI) {
                    processWinner(kaiji, target);
                } else {
                    processWinner(target, kaiji);
                    Bukkit.broadcastMessage("§c카이지 : §f무승부로 하지 않을래…?");
                }

                // 끝났으므로 세션 종료 (디스플레이, 무적 해제)
                cancel();
            }, 20L); // 20틱 (1초 지연)
        }

        // 아이템 이전 로직
        private void processWinner(Player winner, Player loser) {
            if (winner == null || loser == null)
                return;

            winner.sendMessage("§a[도박 승리!] 상대방의 모든 아이템을 획득합니다!");
            loser.sendMessage("§c[도박 패배...] 모든 아이템을 잃었습니다...");

            // 폭죽
            org.bukkit.entity.Firework fw = winner.getWorld().spawn(winner.getLocation(),
                    org.bukkit.entity.Firework.class);
            org.bukkit.inventory.meta.FireworkMeta fwm = fw.getFireworkMeta();
            fwm.addEffect(org.bukkit.FireworkEffect.builder().withColor(Color.YELLOW)
                    .with(org.bukkit.FireworkEffect.Type.STAR).build());
            fwm.setPower(0);
            fw.setFireworkMeta(fwm);

            // 패배자의 인벤토리 백업 (메인 슬롯, 방어구, 보조손 모두 포함)
            List<ItemStack> allItems = new ArrayList<>();
            for (ItemStack item : loser.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    allItems.add(item.clone());
                }
            }
            for (ItemStack item : loser.getInventory().getArmorContents()) {
                if (item != null && item.getType() != Material.AIR && !allItems.contains(item)) {
                    allItems.add(item.clone());
                }
            }
            for (ItemStack item : loser.getInventory().getExtraContents()) {
                if (item != null && item.getType() != Material.AIR && !allItems.contains(item)) {
                    allItems.add(item.clone());
                }
            }

            // 패배자 인벤토리 비우기
            loser.getInventory().clear();
            loser.getInventory().setArmorContents(null);
            loser.getInventory().setItemInOffHand(null);

            // 승자에게 주기
            for (ItemStack item : allItems) {
                // 승자 인벤토리에 넣기 시도
                HashMap<Integer, ItemStack> leftOvers = winner.getInventory().addItem(item);
                // 남는 건 바닥에 드롭
                for (ItemStack drop : leftOvers.values()) {
                    winner.getWorld().dropItemNaturally(winner.getLocation(), drop);
                }
            }
        }

        public void cancel() {
            isFinished = true;
            if (timerTask != null)
                timerTask.cancel();
            if (displayTask != null)
                displayTask.cancel();
            if (kaijiDisplay != null)
                kaijiDisplay.remove();
            if (targetDisplay != null)
                targetDisplay.remove();

            // 상태 맵에서 제거
            activeSessions.remove(kaijiUuid);
            gambleTargets.remove(kaijiUuid);
            gambleTargets.remove(targetUuid);
        }
    }
}
