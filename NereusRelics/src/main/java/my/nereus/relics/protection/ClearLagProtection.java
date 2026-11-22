package my.nereus.relics.protection;

import my.nereus.relics.NereusRelics;
import my.nereus.relics.altar.Altar;
import my.nereus.relics.storage.AltarRepository.DeliverySpec;
import my.nereus.relics.util.ItemCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pemulihan entity tanpa polling berat: plugin lain (ClearLag, dsb.) yang menghapus display altar terdeteksi lewat
 * EntityRemoveEvent, lalu entity dibuat ulang (debounce + batas laju). Item persembahan yang dihapus plugin lain
 * dikembalikan ke pelemparnya lewat tabel deliveries.
 */
public final class ClearLagProtection implements Listener {

    private final NereusRelics plugin;
    /** Penghapusan yang disengaja plugin ini (UUID -> waktu). */
    private final Map<UUID, Long> expected = new ConcurrentHashMap<>();
    private final Set<String> queued = new HashSet<>();
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public ClearLagProtection(NereusRelics plugin) {
        this.plugin = plugin;
    }

    public void expect(UUID id) {
        expected.put(id, System.currentTimeMillis());
    }

    public void purgeExpected() {
        long now = System.currentTimeMillis();
        expected.values().removeIf(t -> now - t > 60_000L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent e) {
        Entity ent = e.getEntity();
        String cause = e.getCause().name();
        if (cause.equals("UNLOAD") || cause.equals("PLAYER_QUIT")) return;
        if (expected.remove(ent.getUniqueId()) != null) return;

        String altarId = ent.getPersistentDataContainer().get(plugin.keys().entityAltar, PersistentDataType.STRING);
        if (altarId != null) {
            Altar a = plugin.altars().get(altarId);
            if (a != null && a.isRunning()) scheduleRespawn(a, 2L);
            return;
        }
        if (ent instanceof Item item && cause.equals("PLUGIN") && plugin.offerings().isMarked(item)) {
            refundRemovedItem(item);
        }
    }

    private void scheduleRespawn(Altar a, long delay) {
        synchronized (queued) {
            if (!queued.add(a.id())) return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int max = Math.max(1, plugin.getConfig().getInt("entity-recovery.max-respawns-per-10s", 5));
            long now = System.currentTimeMillis();
            Deque<Long> dq = attempts.computeIfAbsent(a.id(), k -> new ArrayDeque<>());
            while (!dq.isEmpty() && now - dq.peekFirst() > 10_000L) dq.pollFirst();
            synchronized (queued) {
                queued.remove(a.id());
            }
            if (dq.size() >= max) {
                plugin.getLogger().warning("Entity altar '" + a.id() + "' terus dihapus plugin lain; pemulihan ditunda. "
                        + "Pertimbangkan mengecualikan entity display di ClearLag.");
                scheduleRespawn(a, 100L);
                return;
            }
            dq.addLast(now);
            if (a.isRunning()) plugin.altars().ensureEntities(a, false);
        }, delay);
    }

    private void refundRemovedItem(Item item) {
        UUID thrower = plugin.offerings().markerOf(item);
        Altar near = plugin.altars().runningAltar();
        if (thrower == null || near == null) return;
        var stack = item.getItemStack();
        List<DeliverySpec> specs = List.of(new DeliverySpec(thrower, ItemCodec.encode(stack), stack.getAmount(), "REFUND", near.id()));
        try {
            List<Long> ids = plugin.repo().commit(near, false, false, specs);
            Bukkit.getScheduler().runTask(plugin, () -> plugin.deliveries().enqueue(specs, ids));
        } catch (SQLException ex) {
            plugin.getLogger().severe("Gagal mencatat pengembalian item yang dihapus plugin lain: " + ex);
        }
    }
}
