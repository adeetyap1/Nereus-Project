package me.naook1.nereusfarmprice;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Reads bonuses from NereusEvents (soft dependency, via reflection). All values are neutral without it. */
public final class EventsHook {

    private EventsHook() {
    }

    private static double call(String method, String key, double fallback) {
        try {
            Plugin pl = Bukkit.getPluginManager().getPlugin("NereusEvents");
            if (pl == null || !pl.isEnabled()) {
                return fallback;
            }
            Method m = pl.getClass().getMethod(method, String.class);
            Object r = m.invoke(pl, key);
            return r instanceof Number n ? n.doubleValue() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Product of active event values (1.0 when none). */
    public static double multiplier(String key) {
        return call("getMultiplier", key, 1.0);
    }

    /** Sum of active event values (0.0 when none). */
    public static double bonus(String key) {
        return call("getBonus", key, 0.0);
    }
}
