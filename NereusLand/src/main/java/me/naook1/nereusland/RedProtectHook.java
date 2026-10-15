package me.naook1.nereusland;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Reads the region at a location straight from RedProtect's API (RedProtect.get().getAPI().getRegion(Location)).
 * Reflection only, so no RedProtect jar is needed to build and minor API changes do not break the plugin.
 */
public final class RedProtectHook {

    public record Land(String name, String owner, String role) {
    }

    private static final Pattern COLOR = Pattern.compile("(?i)[&§][0-9a-fk-orx]");
    private static final Pattern SPLIT = Pattern.compile("[,;\\s]+");

    private final NereusLand plugin;
    private Object api;
    private Method getRegion;
    private String status = "belum terhubung";
    private String lastError = "-";

    public RedProtectHook(NereusLand plugin) {
        this.plugin = plugin;
    }

    public String status() {
        return status;
    }

    public String lastError() {
        return lastError;
    }

    public boolean ready() {
        if (api != null) {
            return true;
        }
        Plugin rp = Bukkit.getPluginManager().getPlugin("RedProtect");
        if (rp == null || !rp.isEnabled()) {
            status = "RedProtect tidak ditemukan / belum aktif";
            return false;
        }
        try {
            Object candidate = rp.getClass().getMethod("getAPI").invoke(rp);
            if (candidate == null) {
                status = "API RedProtect belum siap";
                return false;
            }
            getRegion = candidate.getClass().getMethod("getRegion", Location.class);
            api = candidate;
            status = "terhubung (RedProtect " + rp.getDescription().getVersion() + ")";
            plugin.getLogger().info("Hooked into RedProtect " + rp.getDescription().getVersion());
            return true;
        } catch (Throwable t) {
            status = "API RedProtect tidak cocok: " + t.getClass().getSimpleName();
            lastError = String.valueOf(t.getMessage());
            return false;
        }
    }

    /** The land at the player's feet, or null when there is none (or RedProtect is not available). */
    public Land landAt(Player p) {
        if (!ready()) {
            return null;
        }
        try {
            Object region = getRegion.invoke(api, p.getLocation());
            if (region == null) {
                return null;
            }
            String name = str(call(region, "getName"));
            if (name.isBlank()) {
                return null;
            }
            String leaders = clean(str(call(region, "getLeadersDesc")));
            String admins = clean(str(call(region, "getAdminDesc")));
            String members = clean(str(call(region, "getMembersDesc")));
            String role = null;
            if (has(leaders, p.getName())) {
                role = "leader";
            } else if (has(admins, p.getName())) {
                role = "admin";
            } else if (has(members, p.getName())) {
                role = "member";
            }
            return new Land(name, leaders.isBlank() ? "-" : leaders, role);
        } catch (Throwable t) {
            lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return null;
        }
    }

    private static Object call(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String clean(String s) {
        return COLOR.matcher(s).replaceAll("").trim();
    }

    private static boolean has(String list, String name) {
        for (String part : SPLIT.split(list)) {
            if (part.equalsIgnoreCase(name) || part.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
