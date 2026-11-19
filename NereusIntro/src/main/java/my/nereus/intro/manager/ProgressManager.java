package my.nereus.intro.manager;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Logger;

/** Progres intro (selesai / terputus). Test mode tidak pernah menulis ke sini. */
public final class ProgressManager {

    private final File file;
    private final Logger log;
    private final YamlConfiguration data;

    public ProgressManager(File dataFolder, Logger log) {
        this.file = new File(dataFolder, "progress.yml");
        this.log = log;
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized boolean isCompleted(UUID id) {
        return data.contains("completed." + id);
    }

    public synchronized void markCompleted(UUID id) {
        data.set("completed." + id, System.currentTimeMillis());
        data.set("interrupted." + id, null);
        save();
    }

    public synchronized int interrupted(UUID id) {
        return data.getInt("interrupted." + id, 0);
    }

    public synchronized void addInterrupted(UUID id) {
        data.set("interrupted." + id, interrupted(id) + 1);
        save();
    }

    public synchronized void reset(UUID id) {
        data.set("completed." + id, null);
        data.set("interrupted." + id, null);
        save();
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            log.warning("Gagal menyimpan progress.yml: " + e.getMessage());
        }
    }
}
