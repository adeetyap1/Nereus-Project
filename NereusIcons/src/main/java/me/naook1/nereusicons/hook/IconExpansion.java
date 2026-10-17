package me.naook1.nereusicons.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.naook1.nereusicons.NereusIcons;
import org.bukkit.OfflinePlayer;

/** %nereusicons_rank% = icon of the player's group, %nereusicons_owner% = a specific icon. */
public final class IconExpansion extends PlaceholderExpansion {

    private final NereusIcons plugin;

    public IconExpansion(NereusIcons plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "nereusicons";
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
        String p = params.toLowerCase();
        if (p.equals("rank")) {
            String r = player != null && player.isOnline() ? plugin.icons().rank(player.getPlayer()) : null;
            return r == null ? "" : r;
        }
        String icon = plugin.icons().icon(p);
        return icon == null ? "" : icon;
    }
}
