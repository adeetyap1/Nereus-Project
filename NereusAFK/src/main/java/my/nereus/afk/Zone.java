package my.nereus.afk;

import org.bukkit.Location;

/** Zona AFK: silinder dengan pusat (x,z), radius horizontal, dan rentang tinggi dari y-2 sampai y+height. */
record Zone(String id, String world, double x, double y, double z, double radius, double height) {

    boolean contains(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(world)) return false;
        double dx = l.getX() - x, dz = l.getZ() - z;
        return dx * dx + dz * dz <= radius * radius && l.getY() >= y - 2 && l.getY() <= y + height;
    }
}
