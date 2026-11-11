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
            case "xp_need" -> String.valueOf(core.xpNeeded(pr.level));
            case "xp_percent" -> String.valueOf(pr.xp * 100 / Math.max(1, core.xpNeeded(pr.level)));
            case "statpoints" -> String.valueOf(pr.statPoints);
            case "kills" -> String.valueOf(pr.totalKills);
            case "streak" -> String.valueOf(pr.streak);
            case "quests_active" -> String.valueOf(pr.active.size());
            case "quests_done" -> String.valueOf(pr.completedToday);
            case "quests_limit" -> String.valueOf(core.plugin.getConfig().getInt("quests.per-day-limit", 6));
            case "story" -> {
                Defs.Mission m = core.tracker.mission(pr);
                yield m == null ? "Tamat" : Text.strip(m.title());
            }
            case "story_progress" -> {
                Defs.Mission m = core.tracker.mission(pr);
                yield m == null ? "" : pr.storyProgress + "/" + m.obj().amount();
            }
            case "next_rank" -> {
                Defs.Rank n = core.rank(pr.rank + 1);
                yield n == null ? "MAX" : color(n.tag());
            }
            case "next_gp" -> {
                Defs.Rank n = core.rank(pr.rank + 1);
                yield n == null ? "-" : String.valueOf(n.gp());
            }
            case "skill" -> {
                if (c == null || c.skills().isEmpty()) {
                    yield "-";
                }
                yield Text.strip(c.skills().get(Math.min(pr.skillSel, c.skills().size() - 1)).name());
            }
            case "exam" -> {
                Defs.Rank t = core.rank(pr.examTarget);
                yield t == null ? "-" : t.key() + " " + pr.examProgress + "/" + (t.exam() == null ? 0 : t.exam().amount());
            }
            default -> null;
        };
    }
}
