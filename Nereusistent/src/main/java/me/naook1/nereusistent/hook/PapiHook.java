package me.naook1.nereusistent.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/** Only loaded when PlaceholderAPI is present. */
public final class PapiHook {

    private PapiHook() {
    }

    public static String set(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
