package my.nereus.intro.cinematic;

import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/** Memutar sound lewat key string, sehingga key yang tidak dikenal tidak membuat error (hanya hening). */
public final class SoundPlayer {

    private SoundPlayer() {}

    public static void play(Player p, String key, float volume, float pitch) {
        if (key == null || key.isBlank()) return;
        String k = key.contains(":") ? key : "minecraft:" + key;
        try {
            p.playSound(p.getLocation(), k, SoundCategory.MASTER, volume, pitch);
        } catch (Throwable ignored) {
            // sound opsional; jangan pernah menghentikan intro
        }
    }
}
