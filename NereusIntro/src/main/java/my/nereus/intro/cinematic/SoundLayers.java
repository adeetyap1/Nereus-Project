package my.nereus.intro.cinematic;

import my.nereus.intro.NereusIntro;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/**
 * Efek suara berdurasi (hujan, klakson, dengung ruang putih). Suara vanilla pendek diulang tiap
 * {@code interval} tick selama {@code duration} tick, lalu dihentikan. Semua dihentikan saat sesi berakhir.
 */
public final class SoundLayers {

    private final NereusIntro plugin;
    private final Player player;
    private final Map<String, BukkitTask> active = new HashMap<>();

    public SoundLayers(NereusIntro plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    private static String norm(String key) {
        return key.contains(":") ? key : "minecraft:" + key;
    }

    public void start(String key, float volume, float pitch, int durationTicks, int intervalTicks) {
        if (key == null || key.isBlank()) return;
        final String k = norm(key);
        stop(k);
        SoundPlayer.play(player, k, volume, pitch);
        final int iv = Math.max(5, intervalTicks);
        final int[] elapsed = {0};
        active.put(k, Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            elapsed[0] += iv;
            if (elapsed[0] >= durationTicks || !player.isOnline()) {
                stop(k);
                return;
            }
            SoundPlayer.play(player, k, volume, pitch);
        }, iv, iv));
    }

    public void stop(String key) {
        if (key == null || key.isBlank()) return;
        String k = norm(key);
        BukkitTask t = active.remove(k);
        if (t != null) t.cancel();
        try {
            player.stopSound(k);
        } catch (Throwable ignored) {
            // pemain mungkin sudah offline
        }
    }

    public void stopAll() {
        for (String k : new java.util.ArrayList<>(active.keySet())) stop(k);
    }
}
