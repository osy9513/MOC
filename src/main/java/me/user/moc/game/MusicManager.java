package me.user.moc.game;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MusicManager {

    private final JavaPlugin plugin;
    private final Map<UUID, BukkitTask> musicTasks = new HashMap<>();

    public MusicManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // 🎵 라운드 시작 시 브금 무한 반복 재생
    public void playBattleBGM(Player p) {
        // 기존 마크 브금 끄기
        p.stopSound(Sound.MUSIC_GAME, SoundCategory.MUSIC);

        if (musicTasks.containsKey(p.getUniqueId())) {
            musicTasks.get(p.getUniqueId()).cancel();
        }

        // 🎯 브라다의 완벽한 계산: 1분 50초 = 110초
        int bgmLengthInSeconds = 110;

        // 110초를 마인크래프트 틱(Tick) 단위로 변환 (110 * 20 = 2200틱)
        int bgmLengthInTicks = bgmLengthInSeconds * 20;

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) {
                    this.cancel();
                    musicTasks.remove(p.getUniqueId());
                    return;
                }

                // ⭐ [핵심 추가] 새 루프가 시작되기 직전에, 혹시 남아있을지 모르는 2초의 꼬리를 강제로 싹둑 잘라버립니다!
                p.stopSound("moc.music.battlegroundbgm", SoundCategory.MUSIC);

                // 그리고 바로 0.001초의 틈도 없이 새 노래를 시작합니다! (자연스러운 무한 반복)
                p.playSound(p.getLocation(), "moc.music.battlegroundbgm", SoundCategory.MUSIC, 1.0f, 1.0f);
            }
        }.runTaskTimer(plugin, 0L, bgmLengthInTicks); // 처음 틀 때 0초 대기, 이후 110초마다 반복

        musicTasks.put(p.getUniqueId(), task);
    }

    // 🔇 라운드 종료 시 브금 완전 정지
    public void stopBattleBGM(Player p) {
        if (musicTasks.containsKey(p.getUniqueId())) {
            musicTasks.get(p.getUniqueId()).cancel();
            musicTasks.remove(p.getUniqueId());
        }
        // 라운드 끝날 때도 당연히 음악을 꺼줍니다.
        p.stopSound("moc.music.battlegroundbgm", SoundCategory.MUSIC);
    }
}