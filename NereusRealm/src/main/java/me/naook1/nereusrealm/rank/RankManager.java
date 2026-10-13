package me.naook1.nereusrealm.rank;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.naook1.nereusrealm.NereusRealm;
import me.naook1.nereusrealm.hook.LuckPermsHook;
import me.naook1.nereusrealm.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** Rank list for the shop menu, read from ranks.yml (order in file = order in menu, lowest first). */
public final class RankManager {

    public record Rank(String id, String name, Material icon, String price, String duration,
                       List<String> features, List<String> actions) {
    }

    private final NereusRealm plugin;
    private final Map<String, Rank> ranks = new LinkedHashMap<>();

    public RankManager(NereusRealm plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.loadYaml("ranks.yml");
        boolean sc = plugin.getConfig().getBoolean("small-caps.gui", true);
        ranks.clear();
        String store = c.getString("store-url", "");
        List<String> defaults = withStore(c.getStringList("default-actions"), store);
        ConfigurationSection sec = c.getConfigurationSection("ranks");
        if (sec == null) {
            return;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection r = sec.getConfigurationSection(id);
            if (r == null) {
                continue;
            }
            String key = id.toLowerCase(Locale.ROOT);
            Material icon = Material.matchMaterial(r.getString("icon", "PAPER"));
            if (icon == null || !icon.isItem()) {
                icon = Material.PAPER;
            }
            List<String> features = new ArrayList<>();
            for (String f : r.getStringList("features")) {
                features.add(sc ? SmallCaps.convert(f) : f);
            }
            List<String> actions = r.isList("actions") ? withStore(r.getStringList("actions"), store) : defaults;
            ranks.put(key, new Rank(key, conv(r.getString("name", id), sc), icon,
                    r.getString("price", "-"), conv(r.getString("duration", "-"), sc), features, actions));
        }
    }

    private static String conv(String s, boolean sc) {
        return sc ? SmallCaps.convert(s) : s;
    }

    private static List<String> withStore(List<String> actions, String store) {
        List<String> out = new ArrayList<>();
        for (String a : actions) {
            out.add(a.replace("{store_url}", store));
        }
        return out;
    }

    public Collection<Rank> all() {
        return ranks.values();
    }

    public boolean owns(Player p, Rank r) {
        return p.hasPermission("group." + r.id());
    }

    /** Highest rank from ranks.yml the player has (LuckPerms group), or null. */
    public Rank current(Player p) {
        Rank best = null;
        for (Rank r : ranks.values()) {
            if (owns(p, r)) {
                best = r;
            }
        }
        return best;
    }

    /**
     * Name shown for the player's own rank: the primary LuckPerms group (so staff and custom groups show
     * correctly), styled from ranks.yml when listed there. Falls back to the highest ranks.yml rank the
     * player owns, then to "Member".
     */
    public String currentName(Player p) {
        String member = plugin.messages().raw("ranks.member");
        String id = null;
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            id = LuckPermsHook.primaryGroup(p);
        }
        if (id == null || id.isBlank() || id.equalsIgnoreCase("default")) {
            Rank r = current(p);
            return r == null ? member : r.name();
        }
        Rank listed = ranks.get(id.toLowerCase(Locale.ROOT));
        if (listed != null) {
            return listed.name();
        }
        String shown = LuckPermsHook.display(id);
        if (shown == null || shown.isBlank()) {
            shown = Character.toUpperCase(id.charAt(0)) + id.substring(1);
        }
        return conv(shown, plugin.getConfig().getBoolean("small-caps.gui", true));
    }

    /** Display name from ranks.yml, or the capitalized id if the rank is not listed. */
    public String displayName(String id) {
        Rank r = ranks.get(id.toLowerCase(Locale.ROOT));
        if (r != null) {
            return r.name();
        }
        return id.isEmpty() ? id : Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }
}
