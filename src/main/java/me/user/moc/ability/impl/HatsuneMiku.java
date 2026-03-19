package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HatsuneMiku extends Ability {

    public HatsuneMiku(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "072";
    }

    @Override
    public String getName() {
        return "하츠네 미쿠";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§b전투 ● 하츠네 미쿠(보컬로이드)",
                "§f미쿠미쿠빔~~~!!!");
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§b전투 ● 하츠네 미쿠(보컬로이드)");
        p.sendMessage("§f마이크 우클릭 시 하츠네 미쿠가 되어");
        p.sendMessage("§f10초에 걸쳐 미쿠미쿠 빔을 준비합니다.");
        p.sendMessage("§f준비가 끝나면 전방 15칸까지 5초간 9 데미지의 미쿠미쿠 빔을 마구 쏩니다.");
        p.sendMessage("§f미쿠미쿠 빔 발동 중엔 공격을 할 수 없습니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 13초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 마이크");
        p.sendMessage("§f장비 제거 : 철칼");
    }

    @Override
    public void giveItem(Player p) {
        detailCheck(p);

        p.getInventory().remove(Material.IRON_SWORD);

        // 추가 장비: 마이크 (CustomModelData: 17)
        ItemStack mic = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = mic.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b마이크");
            meta.setCustomModelData(17); // 리소스팩: mic
            meta.setLore(Arrays.asList("§f우클릭을 하여", "§f미쿠미쿠 빔을 발사합니다."));
            mic.setItemMeta(meta);
        }
        p.getInventory().addItem(mic);
    }

    private boolean hasAbility(Player p) {
        return AbilityManager.getInstance().hasAbility(p, getCode());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 침묵 상태, 능력 소유 여부 검사
        if (isSilenced(p))
            return;
        if (!hasAbility(p))
            return;

        // 관전자 검사
        if (p.getGameMode() == GameMode.SPECTATOR)
            return;

        // 마이크(아이언 소드, CustomModelData 17) 우클릭 검사
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        ItemStack handItem = p.getInventory().getItemInMainHand();
        if (handItem.getType() != Material.IRON_SWORD)
            return;

        ItemMeta meta = handItem.getItemMeta();
        if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != 17)
            return;

        // 이미 빔 준비이거나 발사 중인지 검사 (activeTasks에 남아있는지)
        if (hasActiveTasks(p))
            return;

        // 쿨타임 검사 (크리에이티브 모드는 쿨타임 무시됨)
        if (!checkCooldown(p))
            return;

        // 스케줄러를 등록하여 진행
        startMikuMikuBeam(p);
        // 채굴 피로 10 15초(300틱) 부여
        p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 300, 9, true, true, true));
    }

    // 현재 작동 중인 태스크(스킬 캐스팅/활성화 상태) 체크
    private boolean hasActiveTasks(Player p) {
        List<BukkitTask> tasks = activeTasks.get(p.getUniqueId());
        if (tasks != null) {
            for (BukkitTask task : tasks) {
                if (!task.isCancelled())
                    return true;
            }
        }
        return false;
    }

    private void startMikuMikuBeam(Player p) {
        BukkitRunnable beamTask = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                // 게임 종료, 사망, 연결 끊김 시 안전하게 취소
                if (!p.isOnline() || p.isDead()) {
                    this.cancel();
                    return;
                }

                // 머리에 청록색 양갈래 머리 파티클 출력 (매 틱)
                drawTwintails(p);
                // --- 대사 출력 (10초에 걸쳐) ---
                if (tick == 0)
                    playVoiceLine(p, "§b하츠네 미쿠 : And now,");
                else if (tick == 20)
                    playVoiceLine(p, "§b하츠네 미쿠 : it's time");
                else if (tick == 40)
                    playVoiceLine(p, "§b하츠네 미쿠 : for the moment you've");
                else if (tick == 60)
                    playVoiceLine(p, "§b하츠네 미쿠 : been waiting for!");
                else if (tick == 100)
                    playVoiceLine(p, "§b하츠네 미쿠: 1…!");
                else if (tick == 120)
                    playVoiceLine(p, "§b하츠네 미쿠: 2…!");
                else if (tick == 140)
                    playVoiceLine(p, "§b하츠네 미쿠: 3…!");
                else if (tick == 160)
                    playVoiceLine(p, "§b하츠네 미쿠: Ready?!");
                else if (tick == 180)
                    playVoiceLine(p, "§b하츠네 미쿠: MIKU MIKU BEAM!!!!!!!!!!!!!!!!!!!");

                // --- 미쿠미쿠 빔 발사 (180틱 ~ 280틱, 총 5초) ---
                if (tick >= 180 && tick <= 280) {
                    Location eyeLoc = p.getEyeLocation();

                    // 파티클 발사 효과음
                    if (tick % 5 == 0) {
                        p.getWorld().playSound(eyeLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2f, 1.5f);
                    }

                    Vector dir = eyeLoc.getDirection().normalize();
                    Particle.DustOptions cyanDust = new Particle.DustOptions(Color.AQUA, 2.0F);

                    // 전방 15칸 내부의 원뿔/기둥 모양으로 파티클 흩뿌리기
                    for (int i = 0; i < 20; i++) {
                        // 0~15 랜덤 거리에서 폭발 효과
                        double distance = Math.random() * 15;
                        double spreadX = (Math.random() - 0.5) * 4;
                        double spreadY = (Math.random() - 0.5) * 4;

                        Location particleLoc = eyeLoc.clone()
                                .add(dir.clone().multiply(distance))
                                .add(rotateAroundAxisY(dir, spreadX, spreadY));

                        p.getWorld().spawnParticle(Particle.FIREWORK, particleLoc, 1, 0, 0, 0, 0);
                        p.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, cyanDust);
                    }

                    // 데미지 판정 (15칸 범위 내 적)
                    // BoundingBox로 대략적인 범위를 구한 후 거리와 벡터 내적으로 타겟팅을 정밀하게 검사
                    BoundingBox box = BoundingBox.of(eyeLoc.clone().add(dir.clone().multiply(7.5)), 7.5, 4.0, 7.5);
                    for (Entity ent : p.getWorld().getNearbyEntities(box)) {
                        if (ent instanceof LivingEntity target && target != p) {
                            if (target instanceof Player pt && pt.getGameMode() == GameMode.SPECTATOR)
                                continue;

                            Vector toTarget = target.getLocation().toVector().subtract(eyeLoc.toVector());
                            // 자신과 타겟 사이의 거리 벡터 정규화
                            if (toTarget.lengthSquared() > 0) {
                                Vector targetNorm = toTarget.clone().normalize();
                                // 내적이 0.5 초과 (약 60도의 원뿔 형태) 범위 && 거리 15 이내
                                if (dir.dot(targetNorm) > 0.5 && target.getLocation().distance(eyeLoc) <= 15) {
                                    // 타겟에게 데미지를 지속적으로 입힘
                                    // 바닐라 기본 무적 틱(NoDamageTicks)의 쿨타임을 활용해 데미지가 중복 난사되지 않게 됨
                                    target.setMetadata("MOC_LastKiller",
                                            new FixedMetadataValue(plugin, p.getUniqueId().toString()));
                                    target.damage(9.0, p);
                                }
                            }
                        }
                    }
                }

                // --- 종료 처리 ---
                if (tick > 280) {
                    if (p.getGameMode() != GameMode.CREATIVE) {
                        setCooldown(p, 13);
                    }
                    this.cancel();
                    return;
                }

                tick++;
            }
        };

        activeTasks.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(beamTask.runTaskTimer(plugin, 0L, 1L));
    }

    /**
     * 전자음(보컬로이드 느낌)과 함께 대사를 브로드캐스트합니다. (주변 사람만 소리 재생)
     */
    private void playVoiceLine(Player p, String message) {
        Bukkit.broadcastMessage(message);
        // 플레이어 주변에서만 들리도록 월드의 좌표 기반 사운드 재생 사용
        // 반경 16블록 내외의 사람들에게만 플링 소리가 들립니다.

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 1.5f, 2.0f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.5f, 1.8f);
    }

    /**
     * 시전자 머리에 양갈래 파티클을 그리는 함수 ('ㄱ'자 형태)
     */
    private void drawTwintails(Player p) {
        Location head = p.getEyeLocation().clone().add(0, 0.2, 0); // 머리 정수리 약간 위
        Vector dir = head.getDirection().setY(0).normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Particle.DustOptions cyan = new Particle.DustOptions(Color.AQUA, 1.5F);

        // 시야 확보를 위해 양 옆으로 멀리(약 1.0칸), 뒤로 조금(-0.3칸) 보냄
        Location leftBase = head.clone().add(right.clone().multiply(-1.0)).add(dir.clone().multiply(-0.3));
        Location rightBase = head.clone().add(right.clone().multiply(1.0)).add(dir.clone().multiply(-0.3));

        // 1. 머리에서 양옆(어깨 넓이 이상)으로 뻗어나가는 가로선 그리기
        for (double d = 0; d <= 1.0; d += 0.2) { // 1.0 길이 만큼 뻗음
            Location lTarget = head.clone().add(right.clone().multiply(-d)).add(dir.clone().multiply(-0.3));
            Location rTarget = head.clone().add(right.clone().multiply(d)).add(dir.clone().multiply(-0.3));
            p.getWorld().spawnParticle(Particle.DUST, lTarget, 1, 0.05, 0.05, 0.05, 0, cyan);
            p.getWorld().spawnParticle(Particle.DUST, rTarget, 1, 0.05, 0.05, 0.05, 0, cyan);
        }

        // 2. 뻗어나간 끝점(Base)에서 수직으로 내려가는 세로선 그리기 (양갈래 머리칼)
        // 기존 2.0에서 허리(1.2) 정도로 짧게 수정
        for (double d = 0; d < 1.2; d += 0.2) { // 길이가 1.2로 축소
            Location lTail = leftBase.clone().add(0, -d, 0);
            Location rTail = rightBase.clone().add(0, -d, 0);
            p.getWorld().spawnParticle(Particle.DUST, lTail, 1, 0.05, 0.05, 0.05, 0, cyan);
            p.getWorld().spawnParticle(Particle.DUST, rTail, 1, 0.05, 0.05, 0.05, 0, cyan);
        }
    }

    /**
     * 특정 카메라 시선 기준(dir)으로 상대적 x, y 오프셋을 Vector로 변환하는 유틸
     */
    private Vector rotateAroundAxisY(Vector dir, double rightOffset, double upOffset) {
        Vector up = new Vector(0, 1, 0);
        Vector right = dir.clone().crossProduct(up).normalize();
        if (right.lengthSquared() == 0) { // 완전 위나 아래를 볼 때 방어
            right = new Vector(1, 0, 0);
        }
        Vector realUp = right.clone().crossProduct(dir).normalize();

        return right.multiply(rightOffset).add(realUp.multiply(upOffset));
    }

    @Override
    public void cleanup(Player p) {
        super.cleanup(p);
    }

}
