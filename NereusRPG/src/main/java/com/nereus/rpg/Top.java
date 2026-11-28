package com.nereus.rpg;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitTask;

/**
 * Peringkat Nerus Guild (rank F-S, lalu EXP total) untuk placeholder %nereusrpg_top_N_*%.
 * Dihitung async dari playerdata/ secara berkala; file hanya dibaca ulang bila berubah.
 */
public final class Top {
    public record Entry(UUID id, String name, String rankKey, int rankIndex, int level, long totalXp) {
    }

    private record Seen(long modified, Entry entry) {
    }

    private final Core core;
    private final Map<UUID, Seen> seen = new ConcurrentHashMap<>();
    private volatile List<Entry> list = List.of();
    private BukkitTask task;

    public Top(Core core) {
        this.core = core;
    }

    public void start() {
        long every = Math.max(20L, core.plugin.getConfig().getLong("top.refresh-seconds", 60L)) * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(core.plugin, this::refresh, 100L, every);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    public int size() {
        return Math.max(1, core.plugin.getConfig().getInt("top.size", 100));
    }

    /** Posisi 1-based, atau null bila di luar daftar. */
    public Entry at(int pos) {
        List<Entry> l = list;
        return pos >= 1 && pos <= l.size() && pos <= size() ? l.get(pos - 1) : null;
    }

    public int positionOf(UUID id) {
        List<Entry> l = list;
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).id().equals(id)) {
                return i + 1;
            }
        }
        return 0;
    }

    private void refresh() {
        try {
            List<Entry> out = new ArrayList<>();
            String[] ids = core.store.allIds();
            for (String raw : ids) {
                UUID id;
                try {
                    id = UUID.fromString(raw.replace(".yml", ""));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                Profile live = core.loaded(id);
                Entry e;
                if (live != null) {
                    e = entry(id, live);
                } else {
                    File f = core.store.file(id);
                    long mod = f.lastModified();
                    Seen s = seen.get(id);
                    if (s != null && s.modified() == mod) {
                        e = s.entry();
                    } else {
                        Profile p = core.store.read(id);
                        e = p == null ? null : entry(id, p);
                        seen.put(id, new Seen(mod, e));
                    }
                }
                if (e != null) {
                    out.add(e);
                }
            }
            out.sort(Comparator.comparingInt(Entry::rankIndex).reversed()
                    .thenComparing(Comparator.comparingLong(Entry::totalXp).reversed())
                    .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
            int max = size();
            list = List.copyOf(out.size() > max ? out.subList(0, max) : out);
        } catch (Throwable t) {
            core.plugin.getLogger().warning("Top gagal diperbarui: " + t.getMessage());
        }
    }

    private Entry entry(UUID id, Profile p) {
        if (!p.member()) {
            return null;
        }
        Defs.Rank r = core.rank(p.rank);
        if (r == null) {
            return null;
        }
        String name = p.name;
        if (name == null || name.isBlank()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            name = op.getName() == null ? id.toString().substring(0, 8) : op.getName();
        }
        return new Entry(id, name, r.key(), p.rank, p.level, p.totalXp);
    }
}
