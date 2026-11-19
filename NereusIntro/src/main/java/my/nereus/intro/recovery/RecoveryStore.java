package my.nereus.intro.recovery;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.logging.Logger;

/** Satu file YAML per pemain selama intro berjalan; dihapus saat pemulihan berhasil. */
public final class RecoveryStore {

    public record Record(PlayerSnapshot snapshot, boolean test) {}

    private final File dir;
    private final Logger log;

    public RecoveryStore(File dataFolder, Logger log) {
        this.dir = new File(dataFolder, "recovery");
        this.log = log;
        if (!dir.exists() && !dir.mkdirs()) log.warning("Tidak bisa membuat folder recovery: " + dir);
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    public boolean exists(UUID id) {
        return file(id).isFile();
    }

    public void save(UUID id, PlayerSnapshot snap, boolean test) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("test", test);
        y.set("saved-at", System.currentTimeMillis());
        snap.write(y.createSection("state"));
        File target = file(id);
        File tmp = new File(dir, id + ".tmp");
        try {
            y.save(tmp);
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warning("Gagal menyimpan data recovery " + id + ": " + e.getMessage());
        }
    }

    /** @return null bila tidak ada atau rusak (file rusak dihapus). */
    public Record load(UUID id) {
        File f = file(id);
        if (!f.isFile()) return null;
        try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection s = y.getConfigurationSection("state");
            if (s == null) throw new IllegalStateException("section state hilang");
            return new Record(PlayerSnapshot.read(s), y.getBoolean("test", false));
        } catch (RuntimeException e) {
            log.warning("File recovery rusak untuk " + id + " (" + e.getMessage() + "), dihapus.");
            delete(id);
            return null;
        }
    }

    public void delete(UUID id) {
        try {
            Files.deleteIfExists(file(id).toPath());
        } catch (IOException e) {
            log.warning("Gagal menghapus data recovery " + id + ": " + e.getMessage());
        }
    }
}
