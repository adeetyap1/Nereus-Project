package com.nereus.rpg;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Placeholder PlaceholderAPI: %nereusrpg_rank% dkk (dipakai TAB, scoreboard, chat). */
public final class Expansion extends PlaceholderExpansion {
    private record Cached(Profile profile, long at) {
    }

    private final Core core;
    private Top top;
    /** Cache singkat untuk pemain offline (data dibaca dari playerdata/). */
    private final Map<UUID, Cached> offline = new ConcurrentHashMap<>();

    public Expansion(Core core) {
        this.core = core;
    }

    public void top(Top top) {
        this.top = top;
    }

    /** %nereusrpg_top_N_name|rank|rank_plain|level|exp_total% dan %nereusrpg_top_pos%. */
    private String topRequest(OfflinePlayer player, String key) {
        if (top == null) {
            return "";
        }
        if (key.equals("top_pos")) {
            int pos = player == null ? 0 : top.positionOf(player.getUniqueId());
            return pos == 0 ? "-" : String.valueOf(pos);
        }
        String[] a = key.split("_", 3);
        if (a.length < 3) {
            return null;
        }
        int n;
        try {
            n = Integer.parseInt(a[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        Top.Entry e = top.at(n);
        String field = a[2];
        if (e == null) {
            return switch (field) {
                case "name" -> core.plugin.getConfig().getString("top.empty-name", "-");
                case "rank", "rank_plain" -> "-";
                case "level", "exp_total" -> "0";
                default -> null;
            };
        }
        return switch (field) {
            case "name" -> e.name();
            case "rank" -> {
                Defs.Rank r = core.rank(e.rankIndex());
                yield r == null ? e.rankKey() : color(r.tag());
            }
            case "rank_plain" -> e.rankKey();
            case "level" -> String.valueOf(e.level());
            case "exp_total" -> String.valueOf(e.totalXp());
            default -> null;
        };
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
        return Text.SECTION_HEX.serialize(Text.L.deserialize(s));
    }

    private Profile profile(UUID id) {
        Profile live = core.loaded(id);
        if (live != null) {
            return live;
        }
        long now = System.currentTimeMillis();
        Cached c = offline.get(id);
        if (c != null && now - c.at() < 30_000L) {
            return c.profile();
        }
        if (offline.size() > 200) {
            offline.clear();
        }
        Profile p = core.store.read(id);
        offline.put(id, new Cached(p, now));
        return p;
    }

    private String none() {
        return color(core.plugin.getConfig().getString("ranks-display.none", "&7NONE"));
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String lower = params.toLowerCase();
        if (lower.startsWith("top_")) {
            return topRequest(player, lower);
        }
        if (player == null) {
            return "";
        }
        Profile pr = profile(player.getUniqueId());
        GuildStatus st = pr == null ? GuildStatus.UNREGISTERED : pr.status;
        boolean member = pr != null && pr.member();
        Defs.Rank r = member ? core.rank(pr.rank) : null;
        Defs.Clazz c = member ? core.clazz(pr) : null;
        String key = params.toLowerCase();
        if (key.startsWith("quests_cd_")) {
            // %nereusrpg_quests_cd_mining% -> sisa cooldown jenis quest, atau "Siap"
            if (pr == null || !member) {
                return "Siap";
            }
            String cat = key.substring("quests_cd_".length()).toUpperCase();
            long left = core.cooldownLeft(pr, cat);
            return left <= 0 ? "Siap" : Text.time(left);
        }
        switch (key) {
            case "guild_status" -> {
                return st.name();
            }
            case "registered" -> {
                return String.valueOf(member);
            }
            case "rank" -> {
                return r == null ? none() : color(r.tag());
            }
            case "rank_plain" -> {
                return r == null ? "NONE" : r.key();
            }
            case "rank_name" -> {
                return r == null ? none() : color(r.label());
            }
            case "rank_color" -> {
                return r == null ? "§7" : color(r.colorCode());
            }
            case "rank_legacy" -> {
                return r == null ? "§7NONE" : color(r.legacyTag());
            }
            case "rank_index" -> {
                return r == null ? "0" : String.valueOf(Core.RANK_ORDER.indexOf(r.key().charAt(0)) + 1);
            }
            case "rank_score" -> {
                return r == null ? "0" : String.valueOf((Core.RANK_ORDER.indexOf(r.key().charAt(0)) + 1)
                        * 1_000_000_000L + pr.totalXp);
            }
            case "rank_rarity" -> {
                return r == null ? "-" : r.rarity();
            }
            default -> {
            }
        }
        if (!member) {
            return switch (key) {
                case "level", "exp", "exp_total", "xp", "gp", "mana", "maxmana", "statpoints", "kills", "streak",
                        "quests_active", "quests_done", "quests_completed", "xp_percent", "xp_need" -> "0";
                case "quests_limit" -> "∞";
                case "class", "class_plain", "skill", "story", "next_rank", "next_gp", "exam" -> key.equals("class")
                        || key.equals("class_plain") ? "" : "-";
                case "story_progress" -> "";
                default -> null;
            };
        }
        return switch (key) {
            case "class" -> c == null ? "" : color(c.name());
            case "class_plain" -> c == null ? "" : Text.strip(c.name());
            case "level" -> String.valueOf(pr.level);
            case "exp", "xp" -> String.valueOf(pr.xp);
            case "exp_total" -> String.valueOf(pr.totalXp);
            case "gp" -> String.valueOf(pr.gp);
            case "mana" -> String.valueOf((int) pr.mana);
            case "maxmana" -> String.valueOf((int) core.maxMana(pr));
            case "xp_need" -> String.valueOf(core.xpNeeded(pr.level));
            case "xp_percent" -> String.valueOf(pr.xp * 100 / Math.max(1, core.xpNeeded(pr.level)));
            case "statpoints" -> String.valueOf(pr.statPoints);
            case "kills" -> String.valueOf(pr.totalKills);
            case "streak" -> String.valueOf(pr.streak);
            case "quests_completed" -> String.valueOf(pr.totalQuests);
            case "quests_active" -> String.valueOf(pr.active.size());
            case "quests_done" -> String.valueOf(pr.completedToday);
            case "quests_limit" -> "∞";
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
