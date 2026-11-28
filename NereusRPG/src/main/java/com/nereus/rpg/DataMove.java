package com.nereus.rpg;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Salin data dari plugins/NerusRPG (nama lama) ke plugins/NereusRPG (nama baru), sekali saja.
 * Folder lama TIDAK dihapus. Sebelum menyalin dibuat backup zip. File lama quests.yml/classes.yml disimpan sebagai
 * quests-legacy.yml/classes-legacy.yml supaya quest aktif dan class lama tetap bisa dimigrasi.
 */
final class DataMove {
    private DataMove() {
    }

    static void run(NereusRPG pl) {
        File data = pl.getDataFolder();
        File old = new File(data.getParentFile(), "NerusRPG");
        File marker = new File(new File(data, "logs"), ".v3-data-copied");
        try {
            if (marker.exists() || !old.isDirectory() || old.getCanonicalFile().equals(data.getCanonicalFile())) {
                return;
            }
        } catch (IOException e) {
            return;
        }
        pl.getLogger().info("Data NerusRPG lama ditemukan, menyalin ke " + data.getName() + " (folder lama tidak dihapus)...");
        data.mkdirs();
        File bk = new File(data, "backups");
        bk.mkdirs();
        File zip = new File(bk, "pre-v3-NerusRPG-" + System.currentTimeMillis() + ".zip");
        Path oldP = old.toPath();
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip.toPath())); Stream<Path> w = Files.walk(oldP)) {
            for (Path f : (Iterable<Path>) w.filter(Files::isRegularFile)::iterator) {
                z.putNextEntry(new ZipEntry(oldP.relativize(f).toString().replace('\\', '/')));
                Files.copy(f, z);
                z.closeEntry();
            }
        } catch (IOException e) {
            // tanpa backup, jangan menyalin apa pun
            pl.getLogger().severe("Backup data lama gagal (" + e.getMessage() + "). Penyalinan dibatalkan.");
            return;
        }
        // config.yml asing (plugin NereusRPG 1.0 lama) disingkirkan agar tidak dianggap config baru
        File cfg = new File(data, "config.yml");
        if (cfg.exists() && !YamlConfiguration.loadConfiguration(cfg).contains("level")) {
            moveAside(cfg);
        }
        int copied = 0;
        try (Stream<Path> w = Files.walk(oldP)) {
            for (Path f : (Iterable<Path>) w.filter(Files::isRegularFile)::iterator) {
                String rel = oldP.relativize(f).toString();
                if (rel.startsWith("backups")) {
                    continue; // backup lama tidak perlu dilipat dua
                }
                String name = rel;
                if (rel.equals("quests.yml")) {
                    name = "quests-legacy.yml";
                } else if (rel.equals("classes.yml")) {
                    name = "classes-legacy.yml";
                }
                File to = new File(data, name);
                if (to.exists()) {
                    moveAside(to);
                }
                to.getParentFile().mkdirs();
                Files.copy(f, to.toPath(), StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }
            marker.getParentFile().mkdirs();
            Files.writeString(marker.toPath(), "copied " + copied + " files from NerusRPG at " + System.currentTimeMillis());
        } catch (IOException e) {
            pl.getLogger().severe("Penyalinan data lama gagal di tengah jalan: " + e.getMessage()
                    + ". Folder lama masih utuh; restart server untuk mencoba lagi.");
            return;
        }
        pl.getLogger().info("Selesai: " + copied + " file disalin. Backup: " + zip.getName());
    }

    private static void moveAside(File f) {
        File to = new File(f.getParentFile(), f.getName() + ".sebelum-v3-" + System.currentTimeMillis());
        try {
            Files.move(f.toPath(), to.toPath());
        } catch (IOException ignored) {
            // biarkan; akan ditimpa
        }
    }
}
