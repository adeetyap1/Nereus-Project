package com.nereus.rpg;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Penyimpanan data pemain: satu file YAML per UUID di playerdata/. Tulis/baca file dilakukan di thread IO
 * tunggal (urutan tulis terjaga); main thread hanya menyusun YAML kecil. Penulisan atomik (tmp lalu move).
 */
public final class PlayerStore {
    private final File dir;
    private final Logger log;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NerusRPG-IO");
        t.setDaemon(true);
        return t;
    });
    /** Data yang sudah dibaca saat pra-login (async), menunggu diambil saat join. */
    private final Map<UUID, Profile> pre = new ConcurrentHashMap<>();

    public PlayerStore(File dir, Logger log) {
        this.dir = dir;
        this.log = log;
        dir.mkdirs();
    }

    public File dir() {
        return dir;
    }

    public File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    public boolean exists(UUID id) {
        return file(id).isFile();
    }

    /** Baca sinkron (panggil dari thread IO/async atau saat darurat). Null jika belum ada file. */
    public Profile read(UUID id) {
        File f = file(id);
        if (!f.isFile()) {
            return null;
        }
        try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            Profile p = new Profile(id);
            p.load(y);
            return p;
        } catch (RuntimeException e) {
            log.severe("Gagal membaca " + f.getName() + ": " + e.getMessage()
                    + " (file TIDAK diubah; cek manual / backups/)");
            return null;
        }
    }

    /** Dipanggil dari AsyncPlayerPreLoginEvent (thread async). */
    public void preload(UUID id) {
        Profile p = read(id);
        if (p != null) {
            pre.put(id, p);
        }
    }

    public Profile takePreloaded(UUID id) {
        return pre.remove(id);
    }

    public void dropPreloaded(UUID id) {
        pre.remove(id);
    }

    /** Baca async lalu panggil callback di main thread (null jika tidak ada file). */
    public void readAsync(org.bukkit.plugin.Plugin plugin, UUID id, Consumer<Profile> done) {
        io.execute(() -> {
            Profile p = read(id);
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> done.accept(p));
        });
    }

    /** Susun YAML di thread pemanggil (cepat), tulis di thread IO. */
    public void saveAsync(Profile p) {
        YamlConfiguration y = new YamlConfiguration();
        p.save(y);
        String text = y.saveToString();
        UUID id = p.id;
        io.execute(() -> write(id, text));
    }

    /** Tulis langsung (dipakai saat shutdown). */
    public void saveNow(Profile p) {
        YamlConfiguration y = new YamlConfiguration();
        p.save(y);
        write(p.id, y.saveToString());
    }

    private void write(UUID id, String text) {
        File f = file(id);
        File tmp = new File(dir, id + ".yml.tmp");
        try {
            Files.writeString(tmp.toPath(), text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomic) {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.severe("Gagal menyimpan data " + id + ": " + e.getMessage());
        }
    }

    /** Jalankan pekerjaan berat di thread IO. */
    public void run(Runnable r) {
        io.execute(r);
    }

    public String[] allIds() {
        String[] names = dir.list((d, n) -> n.endsWith(".yml"));
        return names == null ? new String[0] : names;
    }

    public void shutdown() {
        io.shutdown();
        try {
            if (!io.awaitTermination(15, TimeUnit.SECONDS)) {
                log.warning("Penulisan data belum selesai setelah 15 detik.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
