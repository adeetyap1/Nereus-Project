package com.nereus.rpg;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;

/** Placeholder PlaceholderAPI: %nereusrpg_rank% dkk, dipakai TAB. */
public final class Expansion extends PlaceholderExpansion {
    private final Core core;

    public Expansion(Core core) {
        this.core = core;
    }

    @Override
    public String getIdentifier() {
        return "nereusrpg";
    }

    @Override
    public String getAuthor() {
        return "Nereus MC";
    }

    @Override
    public String getVersion() {
        return core.plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    private static String color(String s) {
        return LegacyComponentSerializer.legacySection().serialize(Text.L.deserialize(s));
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        Profile pr = core.loaded(player.getUniqueId());
        if (pr == null) {
            return "";
        }
        Defs.Rank r = core.rank(pr.rank);
        Defs.Clazz c = core.clazz(pr);
        return switch (params.toLowerCase()) {
            case "rank" -> r == null ? "" : color(r.tag());
            case "rank_plain" -> r == null ? "" : r.key();
            case "rank_name" -> r == null ? "" : color(r.label());
            case "class" -> c == null ? "" : color(c.name());
            case "class_plain" -> c == null ? "" : Text.strip(c.name());
            case "level" -> String.valueOf(pr.level);
            case "gp" -> String.valueOf(pr.gp);
            case "mana" -> String.valueOf((int) pr.mana);
            case "maxmana" -> String.valueOf((int) core.maxMana(pr));
            case "xp" -> String.valueOf(pr.xp);
            default -> null;
        };
    }
}
