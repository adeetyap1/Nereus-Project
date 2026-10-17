package me.naook1.nereusrealm.icon;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    public IconManager(NereusRealm plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        icons.clear();
        groups.clear();
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
}
