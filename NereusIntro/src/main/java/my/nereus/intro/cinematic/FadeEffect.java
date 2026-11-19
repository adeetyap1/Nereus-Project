package my.nereus.intro.cinematic;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Fade hitam memakai Blindness (efek ini ada di Java dan Bedrock, tanpa resource pack). */
public final class FadeEffect {

    private FadeEffect() {}

    /** Gelap dan bertahan sampai {@link #fadeIn} atau {@link #clear}. */
    public static void fadeOut(Player p) {
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 60 * 20, 0, false, false, false));
    }

    /** Terang perlahan dalam {@code ticks}; efek habis sendiri. */
    public static void fadeIn(Player p, int ticks) {
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Math.max(20, ticks), 0, false, false, false));
    }

    public static void clear(Player p) {
        p.removePotionEffect(PotionEffectType.BLINDNESS);
    }
}
