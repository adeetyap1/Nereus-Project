package my.nereus.relics.ritual;

import my.nereus.relics.NereusRelics;
import my.nereus.relics.altar.Altar;
import my.nereus.relics.storage.AltarRepository.DeliverySpec;
import my.nereus.relics.util.ItemCodec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Ritual + klaim. Satu-satunya jalur ke state CLAIMED; semuanya main thread dan atomik lewat Altar.tryFinishClaim. */
public final class RitualManager {

    private final NereusRelics plugin;
    private final Map<String, BukkitTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, Integer> remaining = new ConcurrentHashMap<>();

    public RitualManager(NereusRelics plugin) {
        this.plugin = plugin;
    }

    public int remainingSeconds(Altar a) {
        return remaining.getOrDefault(a.id(), a.ritualSeconds());
    }

    public boolean begin(Altar a, UUID claimant) {
        if (!a.tryBeginRitual(claimant)) return false;
        try {
            plugin.repo().saveSync(a);
        } catch (SQLException e) {
            plugin.getLogger().severe("Gagal menyimpan state RITUAL, dibatalkan: " + e);
            a.revertRitual();
            return false;
        }
        plugin.altars().announce("ritual", a, plugin.nameOf(claimant));
        plugin.offerings().releaseAround(a);
        start(a);
        return true;
    }

    /** Setelah restart server saat RITUAL: hitung mundur ulang dari awal. */
    public void resume(Altar a) {
        if (a.state() == Altar.State.RITUAL && !tasks.containsKey(a.id())) start(a);
    }

    private void start(Altar a) {
        final int total = a.ritualSeconds() * 20;
        remaining.put(a.id(), a.ritualSeconds());
        plugin.holograms().update(a);
        BukkitTask t = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (a.state() != Altar.State.RITUAL) {
                    stop(a);
                    return;
                }
                tick += 2;
                Location base = plugin.altars().baseLocation(a);
                if (base != null && base.getWorld().isChunkLoaded(a.location().chunkX(), a.location().chunkZ())) {
                    Location c = base.clone().add(0, plugin.altars().weaponHeight(a), 0);
                    double ang = tick * 0.35;
                    double rad = 0.9 + Math.min(1.0, tick / (double) total);
                    String pn = plugin.altars().str(a, "ritual-particle", "effects.ritual-particle");
                    plugin.effects().particle(c.clone().add(Math.cos(ang) * rad, 0.2, Math.sin(ang) * rad), pn, 2, 0.02, 0.02, 0.02, 0.0);
                    plugin.effects().particle(c.clone().add(-Math.cos(ang) * rad, -0.2, -Math.sin(ang) * rad), pn, 2, 0.02, 0.02, 0.02, 0.0);
                    if (tick % 20 == 0) {
                        plugin.effects().sound(c, plugin.altars().str(a, "ritual-sound", "effects.ritual-sound"), 1.5f, 0.6f + 0.8f * tick / total);
                    }
                }
                if (tick % 20 == 0) {
                    remaining.put(a.id(), Math.max(0, (total - tick) / 20));
                    plugin.holograms().update(a);
                }
                if (tick >= total) {
                    stop(a);
                    finish(a);
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
        BukkitTask old = tasks.put(a.id(), t);
        if (old != null) old.cancel();
    }

    private void stop(Altar a) {
        BukkitTask t = tasks.remove(a.id());
        if (t != null) t.cancel();
        remaining.remove(a.id());
    }

    public void shutdown() {
        for (BukkitTask t : tasks.values()) t.cancel();
        tasks.clear();
    }

    // ------------------------------------------------------------------ klaim

    private void finish(Altar a) {
        UUID winner = a.claimant();
        if (winner == null) return;
        String name = plugin.nameOf(winner);
        if (!a.tryFinishClaim(winner, name)) return; // sudah diklaim / bukan pemegang kunci

        List<DeliverySpec> specs = new ArrayList<>();
        ItemStack weapon = a.buildWeapon(plugin.keys());
        specs.add(new DeliverySpec(winner, ItemCodec.encode(weapon), 1, "WEAPON", a.id()));
        if (plugin.getConfig().getBoolean("offering.refund-on-claim", true)) {
            specs.addAll(plugin.altars().refundSpecs(a, winner));
        }
        commit(a, winner, name, specs, 0);
    }

    private void commit(Altar a, UUID winner, String name, List<DeliverySpec> specs, int attempt) {
        List<Long> ids;
        try {
            ids = plugin.repo().commit(a, true, false, specs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Gagal menyimpan klaim (percobaan " + (attempt + 1) + "): " + e);
            if (attempt < 20) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> commit(a, winner, name, specs, attempt + 1), 40L);
            } else {
                plugin.getLogger().severe("KLAIM TIDAK TERSIMPAN untuk altar " + a.id() + ", periksa database!");
            }
            return;
        }
        a.progress().clear();
        plugin.altars().save(a);
        plugin.cinematic().play(a, winner, name, () -> {
            plugin.weapons().remove(a);
            plugin.holograms().remove(a);
            plugin.altars().save(a);
            plugin.deliveries().enqueue(specs, ids);
            plugin.altars().announce("claimed", a, name);
        });
    }
}
