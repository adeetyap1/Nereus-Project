package my.nereus.duel;

import org.bukkit.Location;

final class Arena {
    final String id;
    Spot s1;
    Spot s2;
    volatile Match busy;

    Arena(String id) {
        this.id = id;
    }

    boolean ready() {
        return s1 != null && s2 != null && s1.world().equals(s2.world()) && s1.toLocation() != null && s2.toLocation() != null;
    }

    /** Titik tengah (x,z) di antara dua spawn. */
    double[] center() {
        return new double[]{(s1.x() + s2.x()) / 2.0, (s1.z() + s2.z()) / 2.0};
    }

    boolean inside(Location l, double radius, double voidDepth) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(s1.world())) return false;
        double[] c = center();
        double dx = l.getX() - c[0], dz = l.getZ() - c[1];
        if (dx * dx + dz * dz > radius * radius) return false;
        return l.getY() >= Math.min(s1.y(), s2.y()) - voidDepth;
    }
}
