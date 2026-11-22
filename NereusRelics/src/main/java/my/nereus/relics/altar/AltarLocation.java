package my.nereus.relics.altar;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Lokasi altar (pusat blok). */
public record AltarLocation(String world, double x, double y, double z) {

    public static AltarLocation of(Location l) {
        return new AltarLocation(l.getWorld().getName(), l.getBlockX() + 0.5, l.getBlockY(), l.getBlockZ() + 0.5);
    }

    /** @return null bila world belum dimuat. */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z);
    }

    public int chunkX() { return (int) Math.floor(x) >> 4; }
    public int chunkZ() { return (int) Math.floor(z) >> 4; }

    public String describe() {
        return world + " " + (int) Math.floor(x) + ", " + (int) Math.floor(y) + ", " + (int) Math.floor(z);
    }
}
