package my.nereus.duel;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Titik di dunia yang dipetakan lazy (dunia Multiverse bisa dimuat setelah plugin ini). */
record Spot(String world, double x, double y, double z, float yaw, float pitch) {

    static Spot of(Location l) {
        return new Spot(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }
}
