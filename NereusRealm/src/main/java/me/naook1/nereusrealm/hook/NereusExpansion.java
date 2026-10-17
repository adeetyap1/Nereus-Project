package me.naook1.nereusrealm.hook;

import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.naook1.nereusrealm.NereusRealm;
import me.naook1.nereusrealm.util.TimeUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI expansion, identifier "nereus".
 * %nereus_afk% (AFK or empty), %nereus_afk_bool%, %nereus_afk_time%,
 * %nereus_daily_streak%, %nereus_rating_avg%, %nereus_rating_count%,
 * %nereus_icon_rank% (icon of the player's group), %nereus_icon_owner% (a specific icon)
 */
public final class NereusExpansion extends PlaceholderExpansion {

    private final NereusRealm plugin;

    public NereusExpansion(NereusRealm plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "nereus";
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
    public String onRequest(OfflinePlayer offline, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        switch (key) {
            case "rating_avg":
                return String.format(Locale.ROOT, "%.1f", plugin.feedback().average());
            case "rating_count":
                return String.valueOf(plugin.feedback().count());
            default:
                break;
        }
        if (key.startsWith("icon_") && !key.equals("icon_rank")) {
            String icon = plugin.icons().icon(key.substring(5));
            return icon == null ? "" : icon;
        }
        Player p = offline == null ? null : offline.getPlayer();
        if (p == null) {
            return "";
        }
        switch (key) {
            case "afk":
                return plugin.afk().isAfk(p) ? "AFK" : "";
            case "afk_bool":
                return String.valueOf(plugin.afk().isAfk(p));
            case "afk_time":
                return TimeUtil.clock(plugin.afk().info(p).seconds());
            case "icon_rank": {
                String r = plugin.icons().rank(p);
                return r == null ? "" : r;
            }
            case "daily_streak":
                return String.valueOf(plugin.daily().currentStreak(p));
            default:
                return null;
        }
    }
}
