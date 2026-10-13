package me.naook1.nereusrealm.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import me.naook1.nereusrealm.NereusRealm;
import org.bukkit.entity.Player;

/** Only loaded when PlaceholderAPI is present. */
public final class PapiHook {

    private PapiHook() {
    }

    public static String set(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    public static void registerExpansion(NereusRealm plugin) {
        new NereusExpansion(plugin).register();
    }
}
