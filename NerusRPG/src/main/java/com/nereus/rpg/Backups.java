package com.nereus.rpg;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Backup zip playerdata/ ke backups/ (di thread IO) dan pembersihan backup lama. */
public final class Backups {
    private final NerusRPG plugin;

    public Backups(NerusRPG plugin) {
        this.plugin = plugin;
    }

    public File dir() {
        File d = new File(plugin.getDataFolder(), "backups");
        d.mkdirs();
        return d;
    }

    /** Zip folder apa pun ke backups/<prefix>-waktu.zip. Mengembalikan file zip, atau null jika gagal. */
    public File zip(File source, String prefix) {
        if (!source.exists()) {
            return null;
        }
        File out = new File(dir(), prefix + "-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".zip");
        try (OutputStream os = Files.newOutputStream(out.toPath()); ZipOutputStream zip = new ZipOutputStream(os)) {
            Path base = source.toPath();
            if (source.isFile()) {
                add(zip, source.toPath(), source.getName());
            } else {
                try (var walk = Files.walk(base)) {
                    for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                        add(zip, p, base.relativize(p).toString().replace('\\', '/'));
                    }
                }
            }
            return out;
        } catch (IOException e) {
            plugin.getLogger().severe("Backup gagal: " + e.getMessage());
            return null;
        }
    }

    private static void add(ZipOutputStream zip, Path p, String name) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        Files.copy(p, zip);
        zip.closeEntry();
    }

    /** Backup playerdata di thread IO; callback (nama file atau null) dipanggil di main thread. */
    public void backupPlayerdata(java.util.function.Consumer<File> done) {
        plugin.core().store.run(() -> {
            File f = zip(plugin.core().store.dir(), "playerdata");
            prune("playerdata-", plugin.getConfig().getInt("backup.keep", 14));
            if (done != null) {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> done.accept(f));
            }
        });
    }

    private void prune(String prefix, int keep) {
        File[] files = dir().listFiles((d, n) -> n.startsWith(prefix) && n.endsWith(".zip"));
        if (files == null || files.length <= keep) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - keep; i++) {
            if (!files[i].delete()) {
                plugin.getLogger().warning("Tidak bisa menghapus backup lama " + files[i].getName());
            }
        }
    }
}
