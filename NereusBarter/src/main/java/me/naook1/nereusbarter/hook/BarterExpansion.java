package me.naook1.nereusbarter.hook;

import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.naook1.nereusbarter.NereusBarter;
import org.bukkit.OfflinePlayer;

/** PlaceholderAPI expansion, identifier "barter": %barter_count% = number of completed barters. */
public final class BarterExpansion extends PlaceholderExpansion {

    private final NereusBarter plugin;

    public BarterExpansion(NereusBarter plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "barter";
    }

    @Override
    public String getAuthor() {
        return "naook1/adityap";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player != null && params.toLowerCase(Locale.ROOT).equals("count")) {
            return String.valueOf(plugin.store().count(player.getUniqueId()));
        }
        return null;
    }
}
