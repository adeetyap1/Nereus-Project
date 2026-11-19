package my.nereus.intro.npc;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import my.nereus.intro.NereusIntro;
import my.nereus.intro.config.IntroConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Mengelola siklus hidup NPC: maksimal satu NPC per pemain, selalu dibersihkan. */
public final class NpcService {

    private final NereusIntro plugin;
    private final SkinResolver skins;
    private final Map<UUID, NerithNpc> active = new ConcurrentHashMap<>();
    private final Map<UUID, Player> owners = new ConcurrentHashMap<>();

    public NpcService(NereusIntro plugin) {
        this.plugin = plugin;
        this.skins = new SkinResolver(plugin);
    }

    public SkinResolver skins() { return skins; }

    public boolean isAvailable() {
        try {
            return Bukkit.getPluginManager().isPluginEnabled("FancyNpcs") && FancyNpcsPlugin.get() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Posisi Nerith: di depan pemain (relative) atau lokasi tetap (absolute), menghadap pemain. */
    public Location placement(Player p, IntroConfig cfg) {
        Location base = p.getLocation();
        if (!cfg.npcRelative) {
            Location abs = cfg.nerithAbsolute.resolve();
            if (abs != null && abs.getWorld().equals(base.getWorld())) {
                faceTowards(abs, base);
                return abs;
            }
        }
        double yaw = Math.toRadians(base.getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        Location loc = base.clone();
        loc.add(fx * cfg.npcForward + rx * cfg.npcSide, cfg.npcYOffset, fz * cfg.npcForward + rz * cfg.npcSide);
        faceTowards(loc, base);
        return loc;
    }

    private static void faceTowards(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (Math.abs(dx) < 1.0E-6 && Math.abs(dz) < 1.0E-6) {
            from.setYaw(to.getYaw() + 180f);
        } else {
            from.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        }
        from.setPitch(0f);
    }

    /** Membuat NPC untuk pemain; NPC lama pemain yang sama dihapus dulu (anti duplikat). */
    public NerithNpc spawn(Player p, IntroConfig cfg) {
        remove(p.getUniqueId());
        NerithNpc npc = NerithNpc.spawn(p, placement(p, cfg), cfg, skins.get());
        active.put(p.getUniqueId(), npc);
        owners.put(p.getUniqueId(), p);
        return npc;
    }

    public NerithNpc get(UUID id) { return active.get(id); }

    public void remove(UUID id) {
        NerithNpc npc = active.remove(id);
        Player p = owners.remove(id);
        if (npc != null && p != null) npc.remove(p);
    }

    public void removeAll() {
        for (UUID id : new ArrayList<>(active.keySet())) remove(id);
    }

    public int activeCount() { return active.size(); }
}
