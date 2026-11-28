package com.nereus.rpg.guild;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Deteksi pemain Bedrock (Geyser). Floodgate dipakai jika ada, kalau tidak heuristik UUID/prefix. */
public final class Platform {
    private Platform() {
    }

    /** true jika Floodgate aktif. BedrockForms hanya boleh disentuh setelah ini true. */
    public static boolean floodgate() {
        return Bukkit.getPluginManager().isPluginEnabled("floodgate");
    }

    public static boolean isBedrock(Player p) {
        if (floodgate()) {
            try {
                return BedrockForms.isFloodgate(p);
            } catch (LinkageError e) {
                // API Floodgate tidak cocok: pakai heuristik
            }
        }
        return p.getUniqueId().getMostSignificantBits() == 0 || p.getName().startsWith(".");
    }
}
