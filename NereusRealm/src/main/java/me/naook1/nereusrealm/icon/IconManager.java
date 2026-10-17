package me.naook1.nereusrealm.icon;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import me.naook1.nereusrealm.NereusRealm;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** id -> icon character sequence, plus rank resolution from LuckPerms groups (permission group.NAME). */
public final class IconManager {

    private static final Pattern TOKEN = Pattern.compile(":([a-zA-Z0-9_]+):");

    private final NereusRealm plugin;
    private final Map<String, String> icons = new LinkedHashMap<>();
    private final Map<String, String> groups = new LinkedHashMap<>();
    private final Map<String, String> colors = new LinkedHashMap<>();
    private final Map<String, String> labels = new LinkedHashMap<>();

    public IconManager(NereusRealm plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        icons.clear();
        groups.clear();
        colors.clear();
        labels.clear();
        FileConfiguration cfg = plugin.loadYaml("icons.yml");
        ConfigurationSection is = cfg.getConfigurationSection("icons");
        if (is != null) {
            for (String id : is.getKeys(false)) {
                String v = is.getString(id);
                if (v != null && !v.isEmpty()) {
                    icons.put(id.toLowerCase(Locale.ROOT), v);
                }
            }
        }
        ConfigurationSection cs = cfg.getConfigurationSection("name-colors");
        if (cs != null) {
            for (String g : cs.getKeys(false)) {
                String c = cs.getString(g);
                if (c != null) {
                    colors.put(g.toLowerCase(Locale.ROOT), c);
                }
            }
        }
        ConfigurationSection ls = cfg.getConfigurationSection("labels");
        if (ls != null) {
            for (String g : ls.getKeys(false)) {
                String l = ls.getString(g);
                if (l != null) {
                    labels.put(g.toLowerCase(Locale.ROOT), l);
                }
            }
        }
        ConfigurationSection gs = cfg.getConfigurationSection("groups");
        if (gs != null) {
            for (String g : gs.getKeys(false)) {
                String id = gs.getString(g);
                if (id != null) {
                    groups.put(g.toLowerCase(Locale.ROOT), id.toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    /** group name -> icon characters, in config order (first = highest priority). */
    public Map<String, String> groupIcons() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : groups.entrySet()) {
            String icon = icon(e.getValue());
            if (icon != null) {
                out.put(e.getKey(), icon);
            }
        }
        return out;
    }

    /** Color code (e.g. &6) for the player name of a group, or empty. */
    public String nameColor(String group) {
        return colors.getOrDefault(group.toLowerCase(Locale.ROOT), "");
    }

    /** Rank text shown after the icon (plain text), or empty. */
    public String label(String group) {
        return labels.getOrDefault(group.toLowerCase(Locale.ROOT), "");
    }

    public Collection<String> ids() {
        return icons.keySet();
    }

    public String icon(String id) {
        return id == null ? null : icons.get(id.toLowerCase(Locale.ROOT));
    }

    /** The icon for the first configured group the player has (permission group.NAME). */
    public String rank(Player p) {
        if (p == null) {
            return null;
        }
        for (Map.Entry<String, String> e : groups.entrySet()) {
            if (e.getKey().equals("default")) {
                continue;
            }
            if (p.hasPermission("group." + e.getKey())) {
                return icon(e.getValue());
            }
        }
        return icon(groups.get("default"));
    }

    /** Replaces :id: (and :rank:) with the icon characters. Unknown tokens stay as they are. */
    public String apply(String in, Player p) {
        if (in == null || in.indexOf(':') < 0) {
            return in;
        }
        Matcher m = TOKEN.matcher(in);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String id = m.group(1).toLowerCase(Locale.ROOT);
            String rep = id.equals("rank") ? rank(p) : icon(id);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep != null ? rep : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Same as {@link #apply(String, Player)} but on an Adventure component (for chat plugins that render components). */
    public Component apply(Component in, Player p) {
        return in.replaceText(TextReplacementConfig.builder().match(TOKEN).replacement((MatchResult r, net.kyori.adventure.text.TextComponent.Builder b) -> {
            String id = r.group(1).toLowerCase(Locale.ROOT);
            String rep = id.equals("rank") ? rank(p) : icon(id);
            return rep == null ? b : Component.text(rep);
        }).build());
    }

    /** Breaks :id: tokens (zero-width space) so players without permission cannot type icons themselves. */
    public Component neutralize(Component in) {
        return in.replaceText(TextReplacementConfig.builder().match(TOKEN).replacement((MatchResult r, net.kyori.adventure.text.TextComponent.Builder b) ->
                Component.text(":\u200B" + r.group(1) + ":")).build());
    }
}
