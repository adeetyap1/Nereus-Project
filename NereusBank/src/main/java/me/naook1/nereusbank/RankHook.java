package me.naook1.nereusbank;

import org.bukkit.entity.Player;

/** Membaca %nereusrpg_rank_plain% lewat PlaceholderAPI (opsional). Mengembalikan "" jika NONE, null jika tidak tersedia. */
final class RankHook {
    private RankHook() {
    }

    static String rankOf(NereusBank plugin, Player p) {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null
                || !plugin.getConfig().getBoolean("guild-perks-enabled", true)) {
            return null;
        }
        try {
            String v = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, "%nereusrpg_rank_plain%");
            if (v == null || v.contains("%")) {
                return null; // NerusRPG belum terpasang
            }
            v = v.trim().toUpperCase();
            return v.equals("NONE") || v.length() != 1 ? "" : v;
        } catch (LinkageError e) {
            return null;
        }
    }
}
