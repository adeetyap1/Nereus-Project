package com.nereus.guildmaster.hook;

import com.nereus.guildmaster.GuildMaster;
import com.nereus.guildmaster.adventurer.Adventurer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/** %guildmaster_registered%, %guildmaster_status%, %guildmaster_since%. Hanya dimuat jika PlaceholderAPI ada. */
public final class GuildExpansion extends PlaceholderExpansion {
    private final GuildMaster plugin;

    public GuildExpansion(GuildMaster plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "guildmaster";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Nereus MC";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        Adventurer a = plugin.adventurers().get(player.getUniqueId());
        return switch (params.toLowerCase()) {
            case "registered" -> String.valueOf(a != null && a.active());
            case "status" -> a == null ? "Belum terdaftar" : a.active() ? "Registered" : "Dicabut";
            case "since" -> a == null ? "-" : plugin.dialogues().date(a.registeredAt());
            default -> null;
        };
    }
}
