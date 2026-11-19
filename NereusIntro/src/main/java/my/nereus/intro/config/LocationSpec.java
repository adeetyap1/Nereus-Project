package my.nereus.intro.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/** Lokasi dari config. Tidak pernah menghasilkan lokasi tebakan: world kosong/tidak ada = null. */
public record LocationSpec(String world, double x, double y, double z, float yaw, float pitch) {

    public static final LocationSpec EMPTY = new LocationSpec("", 0, 0, 0, 0, 0);

    public static LocationSpec read(ConfigurationSection s) {
        if (s == null) return EMPTY;
        return new LocationSpec(
                s.getString("world", ""),
                s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    public boolean isConfigured() {
        return world != null && !world.isBlank()
                && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    /** @return lokasi nyata, atau null bila belum diatur / world belum dimuat. */
    public Location resolve() {
        if (!isConfigured()) return null;
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public String describe() {
        return world + " " + String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", x, y, z);
    }
}
