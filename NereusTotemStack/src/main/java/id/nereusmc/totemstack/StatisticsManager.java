package id.nereusmc.totemstack;

import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-player totem statistics kept in memory and persisted to SQLite (stats.db) with a single
 * debounced asynchronous write; there is no repeating task.
 */
public final class StatisticsManager {

    /** Immutable view of one player's numbers. */
    public record Snapshot(UUID uuid, String name, long activations, long consumed, long lastActivation) {
    }

    private static final class Entry {
        private final UUID uuid;
        private String name;
        private long activations;
        private long consumed;
        private long last;

        private Entry(UUID uuid, String name, long activations, long consumed, long last) {
            this.uuid = uuid;
            this.name = name;
            this.activations = activations;
            this.consumed = consumed;
            this.last = last;
        }

        private synchronized Snapshot snapshot() {
            return new Snapshot(uuid, name, activations, consumed, last);
        }

        private synchronized void hit(String playerName) {
            name = playerName;
            activations++;
            consumed++;
            last = System.currentTimeMillis();
        }
    }

    private final Main plugin;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean flushPending = new AtomicBoolean(false);
    private final Object dbLock = new Object();
    private Connection connection;

    public StatisticsManager(Main plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            File folder = plugin.getDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                throw new SQLException("Could not create " + folder);
            }
            File dbFile = new File(folder, "stats.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS totem_stats ("
                        + "uuid TEXT PRIMARY KEY, name TEXT, "
                        + "activations INTEGER NOT NULL DEFAULT 0, "
                        + "consumed INTEGER NOT NULL DEFAULT 0, "
                        + "last_activation INTEGER NOT NULL DEFAULT 0)");
            }
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT uuid, name, activations, consumed, last_activation FROM totem_stats")) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("uuid"));
                    cache.put(id, new Entry(id, rs.getString("name"), rs.getLong("activations"),
                            rs.getLong("consumed"), rs.getLong("last_activation")));
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Statistics database unavailable, statistics will not persist: " + ex.getMessage());
            closeQuietly();
        }
    }

    /** Records one activation (one totem consumed) and schedules a debounced async save. */
    public void record(Player player) {
        if (!plugin.config().isStatisticsEnabled()) {
            return;
        }
        UUID id = player.getUniqueId();
        cache.computeIfAbsent(id, key -> new Entry(key, player.getName(), 0, 0, 0)).hit(player.getName());
        dirty.add(id);
        scheduleFlush();
    }

    private void scheduleFlush() {
        if (connection == null || !plugin.isEnabled() || !flushPending.compareAndSet(false, true)) {
            return;
        }
        long delay = plugin.config().getSaveIntervalSeconds() * 20L;
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            flushPending.set(false);
            flush();
        }, delay);
    }

    /** Writes all dirty entries. Safe to call from any thread. */
    public void flush() {
        if (connection == null) {
            return;
        }
        List<Entry> batch = new ArrayList<>();
        for (UUID id : new ArrayList<>(dirty)) {
            dirty.remove(id);
            Entry entry = cache.get(id);
            if (entry != null) {
                batch.add(entry);
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        synchronized (dbLock) {
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO totem_stats (uuid, name, activations, consumed, last_activation) VALUES (?, ?, ?, ?, ?) "
                                + "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, activations = excluded.activations, "
                                + "consumed = excluded.consumed, last_activation = excluded.last_activation")) {
                    for (Entry entry : batch) {
                        Snapshot s = entry.snapshot();
                        ps.setString(1, s.uuid().toString());
                        ps.setString(2, s.name());
                        ps.setLong(3, s.activations());
                        ps.setLong(4, s.consumed());
                        ps.setLong(5, s.lastActivation());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
            } catch (SQLException ex) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // nothing more to do
                }
                for (Entry entry : batch) {
                    dirty.add(entry.uuid);
                }
                plugin.getLogger().warning("Failed to save statistics: " + ex.getMessage());
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // connection is likely closed
                }
            }
        }
    }

    /** Called on plugin disable: synchronous final write, then close. */
    public void shutdown() {
        flush();
        closeQuietly();
    }

    private void closeQuietly() {
        synchronized (dbLock) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // already closed
                }
                connection = null;
            }
        }
    }

    // -------------------------------------------------------------- queries

    public long totalActivations() {
        long total = 0;
        for (Entry entry : cache.values()) {
            total += entry.snapshot().activations();
        }
        return total;
    }

    public long totalConsumed() {
        long total = 0;
        for (Entry entry : cache.values()) {
            total += entry.snapshot().consumed();
        }
        return total;
    }

    public int trackedPlayers() {
        return cache.size();
    }

    public List<Snapshot> top(int limit) {
        List<Snapshot> all = new ArrayList<>();
        for (Entry entry : cache.values()) {
            all.add(entry.snapshot());
        }
        all.sort(Comparator.comparingLong(Snapshot::activations).reversed());
        return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
    }

    public Snapshot get(UUID uuid) {
        Entry entry = cache.get(uuid);
        return entry == null ? null : entry.snapshot();
    }
}
