package my.nereus.relics.weapon;

import my.nereus.relics.NereusRelics;
import my.nereus.relics.altar.Altar;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Senjata melayang: ItemDisplay yang berputar dan naik-turun halus (interpolasi klien, murah di server). */
public final class WeaponDisplayManager {

    public static final String ROLE = "weapon";

    private final NereusRelics plugin;
    private final Map<String, Long> phase = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAmbient = new ConcurrentHashMap<>();

    public WeaponDisplayManager(NereusRelics plugin) {
        this.plugin = plugin;
    }

    public ItemDisplay find(Altar a) {
        UUID id = a.weaponEntity();
        if (id == null) return null;
        Entity e = org.bukkit.Bukkit.getEntity(id);
        return e instanceof ItemDisplay d && d.isValid() ? d : null;
    }

    public ItemDisplay spawn(Altar a) {
        Location base = plugin.altars().baseLocation(a);
        ItemStack item = a.weaponItem();
        if (base == null || item == null) return null;
        Location l = base.clone().add(0, plugin.altars().weaponHeight(a), 0);
        float scale = (float) plugin.getConfig().getDouble("weapon.scale", 1.4);
        ItemDisplay d = l.getWorld().spawn(l, ItemDisplay.class, e -> {
            e.setItemStack(item);
            e.setBillboard(Display.Billboard.FIXED);
            try {
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.valueOf(
                        plugin.getConfig().getString("weapon.display-transform", "FIXED").toUpperCase()));
            } catch (IllegalArgumentException ex) {
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            }
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
            e.setGlowing(plugin.getConfig().getBoolean("weapon.glow", true));
            e.setViewRange(1.0f);
            e.setGravity(false);
            e.setInvulnerable(true);
            e.setPersistent(true);
            e.getPersistentDataContainer().set(plugin.keys().entityAltar, PersistentDataType.STRING, a.id());
            e.getPersistentDataContainer().set(plugin.keys().entityRole, PersistentDataType.STRING, ROLE);
        });
        a.weaponEntity(d.getUniqueId());
        return d;
    }

    public void remove(Altar a) {
        UUID id = a.weaponEntity();
        if (id != null) {
            plugin.clearLag().expect(id);
            Entity e = org.bukkit.Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        a.weaponEntity(null);
        phase.remove(a.id());
        lastAmbient.remove(a.id());
    }

    /** Dipanggil berkala (hanya untuk altar aktif): animasi, partikel, dan suara ambient bila ada pemain di dekatnya. */
    public void tick(Altar a) {
        Location base = plugin.altars().baseLocation(a);
        if (base == null || !base.getWorld().isChunkLoaded(a.location().chunkX(), a.location().chunkZ())) return;
        if (base.getWorld().getNearbyPlayers(base, 64).isEmpty()) return;

        ItemDisplay d = find(a);
        int interval = Math.max(5, plugin.getConfig().getInt("weapon.animation-interval-ticks", 20));
        if (d != null) {
            long step = phase.merge(a.id(), 1L, Long::sum);
            double rotateTicks = Math.max(1.0, plugin.getConfig().getDouble("weapon.rotate-seconds", 4.0) * 20.0);
            float angle = (float) ((step * interval / rotateTicks) * 2 * Math.PI);
            double bobHeight = plugin.getConfig().getDouble("weapon.bob-height", 0.12);
            float bob = (float) (Math.sin(step * interval / 60.0 * Math.PI) * bobHeight);
            float scale = (float) plugin.getConfig().getDouble("weapon.scale", 1.4);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(interval);
            d.setTransformation(new Transformation(new Vector3f(0, bob, 0),
                    new Quaternionf().rotateY(angle), new Vector3f(scale, scale, scale), new Quaternionf()));
        }

        Location fx = base.clone().add(0, plugin.altars().weaponHeight(a), 0);
        plugin.effects().particle(fx, plugin.altars().str(a, "ambient-particle", "effects.ambient-particle"),
                plugin.getConfig().getInt("effects.ambient-particle-count", 3), 0.35, 0.35, 0.35, 0.01);
        long now = System.currentTimeMillis();
        long every = plugin.getConfig().getInt("effects.ambient-sound-every-seconds", 6) * 1000L;
        if (now - lastAmbient.getOrDefault(a.id(), 0L) >= every) {
            lastAmbient.put(a.id(), now);
            plugin.effects().sound(fx, plugin.altars().str(a, "ambient-sound", "effects.ambient-sound"), 0.6f, 1.0f);
        }
    }
}
