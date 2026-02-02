package me.user.moc.ability.impl;

import me.user.moc.ability.Ability;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Jjanggu extends Ability {

    // [대시 상태 관리] 플레이어가 현재 부리부리 대시 중인지 확인하는 목록
    private final List<UUID> dashingPlayers = new ArrayList<>();

    public Jjanggu(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "027";
    }

    @Override
    public String getName() {
        return "짱구";
    }

    @Override
    public List<String> getDescription() {
        List<String> list = new ArrayList<>();
        list.add("§d복합 ● 짱구(짱구는 못말려)");
        list.add(" ");
        list.add("§f부리부리 춤의 달인입니다.");
        return list;
    }

    @Override
    public void giveItem(Player p) {
        // [밸런스 패치] 철 칼 지급 제거 (맨손/다른 무기 사용 유도 or 기본 지급된 철검 제거)
        p.getInventory().remove(org.bukkit.Material.IRON_SWORD);

        // [장비 지급] 초코비(쿠키) 10개
        ItemStack chocobi = new ItemStack(Material.COOKIE, 2);
        ItemMeta meta = chocobi.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6초코비");
            chocobi.setItemMeta(meta);
        }
        p.getInventory().addItem(chocobi);

        // [장비 제거] 구운 소고기 제거
        p.getInventory().remove(Material.COOKED_BEEF);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§d복합 ● 짱구(짱구는 못말려)");
        p.sendMessage("§f배고픔을 가득 채워주는 초코비 2개를 얻습니다.");
        p.sendMessage("§f쉬프트를 눌리면 0.2초간 이동 속도가 엄청 빨라집니다.");
        p.sendMessage("§f이동 속도가 높아진 상태에서 생명체에게 부딪치면 2.5칸의 고정 피해를 줍니다.");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 0초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : 초코비(쿠키) 2개");
        p.sendMessage("§f장비 제거 : 구운 소고기");
    }

    /**
     * [이벤트] 웅크리기(Shift) 감지
     * 쉬프트를 누르면 짧은 순간동안 이동속도가 대폭 상승하며 돌진 모드가 됩니다.
     */
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();

        // 1. 내 능력인지 확인
        if (!me.user.moc.ability.AbilityManager.getInstance((me.user.moc.MocPlugin) plugin).hasAbility(p, getCode())) {
            return;
        }

        // 2. 쿨타임 및 전투 상태 체크 (쿨타임은 0초지만, 기본 검사는 수행)
        if (!checkCooldown(p))
            return;

        // 3. '누르는 순간'에만 발동 (!isSneaking -> 웅크린 상태 아님. 즉 이제 웅크림)
        if (e.isSneaking()) {
            activateDash(p);
        }
    }

    /**
     * [액티브] 짱구의 부리부리 대시 발동
     */
    private void activateDash(Player p) {
        // [중복 방지] 이미 대시 중이면 무시 (0.2초라서 짧지만 안전하게)
        if (dashingPlayers.contains(p.getUniqueId()))
            return;

        dashingPlayers.add(p.getUniqueId());

        // 1. 신속 효과 부여 (0.2초 = 4틱)
        // 신속 30 정도면 매우 빠름. 화면이 확 줌인되는 효과도 있음.
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 4, 30, false, false, false));

        // 2. 부리부리 소리 (돼지 소리?)
        p.playSound(p.getLocation(), Sound.ENTITY_PIG_AMBIENT, 1f, 1.5f);

        // 3. 충돌 감지 태스크 실행 (0.2초 동안)
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                // 게임 종료되거나 플레이어 없으면 취소
                if (!p.isOnline() || p.isDead()) {
                    dashingPlayers.remove(p.getUniqueId());
                    this.cancel();
                    return;
                }

                // 2칸 대미지 충돌 판정
                // 플레이어 주변 2칸 내의 엔티티 검색 (속도가 빨라 범위 상향)
                // [밸런스 패치] 범위 추가 축소 1.0 -> 0.6 (더욱 근접해야 함)
                for (Entity target : p.getNearbyEntities(0.6, 0.6, 0.6)) {
                    if (target instanceof LivingEntity livingTarget && target != p) {
                        if (livingTarget instanceof Player pl && pl.getGameMode() == org.bukkit.GameMode.SPECTATOR)
                            continue;

                        // [대미지 처리]
                        // 고정 피해 5 (하트 2.5칸)
                        livingTarget.damage(5.0, p);

                        // 넉백 살짝 추가
                        Vector dir = p.getLocation().getDirection().setY(0.5).normalize().multiply(1.5);
                        livingTarget.setVelocity(dir);

                        // [밸런스 패치] 실제로 맞췄을 때만 메시지 출력 (도배 방지)
                        Bukkit.broadcastMessage("§c짱구 : §e부리부리~");
                        // 타격음
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1f);
                        // }
                    }
                }

                tick++;
                // 0.2초(4틱) 지나면 종료
                if (tick >= 4) {
                    dashingPlayers.remove(p.getUniqueId());
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
