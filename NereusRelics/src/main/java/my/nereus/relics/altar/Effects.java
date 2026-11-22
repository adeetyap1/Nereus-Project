package my.nereus.relics.altar;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.SoundCategory;

/** Partikel dan suara berbasis nama (dari config), aman untuk nama yang salah. */
public final class Effects {

    public void particle(Location l, String name, int count, double dx, double dy, double dz, double speed) {
        if (name == null || name.isBlank() || l.getWorld() == null) return;
        try {
            Particle p = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(name.toLowerCase().replace("minecraft:", "")));
            if (p == null || p.getDataType() != Void.class) return;
            l.getWorld().spawnParticle(p, l, count, dx, dy, dz, speed);
        } catch (Throwable ignored) {
            // kosmetik saja
        }
    }

    public void sound(Location l, String key, float volume, float pitch) {
        if (key == null || key.isBlank() || l.getWorld() == null) return;
        try {
            l.getWorld().playSound(l, key, SoundCategory.MASTER, volume, pitch);
        } catch (Throwable ignored) {
            // kosmetik saja
        }
    }
}
