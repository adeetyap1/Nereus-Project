package my.nereus.intro.cinematic;

import my.nereus.intro.NereusIntro;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Musik latar per sesi. Suara custom dari resource pack diputar ulang sesuai panjang lagu (sama seperti
 * NereusEvents) dan SELALU dihentikan saat sesi berakhir. Volume tidak bisa di-fade oleh klien Minecraft.
 */
public final class MusicPlayer {

    private final NereusIntro plugin;
    private final Player player;
    private String key;
    private float volume;
    private Sound.Source source = Sound.Source.RECORD;
    private BukkitTask loop;

    public MusicPlayer(NereusIntro plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void start(String key, double lengthSeconds, float volume, Sound.Source source) {
        stop();
        if (key == null || key.isBlank()) return;
        this.key = key.contains(":") ? key : "minecraft:" + key;
        this.volume = volume;
        this.source = source;
        play();
        if (lengthSeconds > 0) {
            long ticks = Math.max(20L, Math.round(lengthSeconds * 20.0));
            loop = Bukkit.getScheduler().runTaskTimer(plugin, this::play, ticks, ticks);
        }
    }

    /** Dipanggil setelah pindah world agar musik tidak hilang. */
    public void restart() {
        if (key == null) return;
        try {
            player.stopSound(key);
        } catch (Throwable ignored) {
            // abaikan
        }
        play();
    }

    public boolean isPlaying() { return key != null; }

    private void play() {
        if (key == null || !player.isOnline()) return;
        try {
            player.playSound(Sound.sound(Key.key(key), source, volume, 1f), Sound.Emitter.self());
        } catch (Throwable ignored) {
            // key tidak valid: hening, bukan error
        }
    }

    public void stop() {
        if (loop != null) {
            loop.cancel();
            loop = null;
        }
        if (key != null) {
            try {
                player.stopSound(key);
            } catch (Throwable ignored) {
                // pemain mungkin sudah offline
            }
            key = null;
        }
    }
}
