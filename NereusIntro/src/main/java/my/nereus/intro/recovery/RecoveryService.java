package my.nereus.intro.recovery;

import my.nereus.intro.NereusIntro;
import my.nereus.intro.config.IntroConfig;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Memulihkan pemain yang intro-nya terputus (crash, disconnect, restart, plugin dimatikan). */
public final class RecoveryService {

    private final NereusIntro plugin;
    private final RecoveryStore store;

    public RecoveryService(NereusIntro plugin, RecoveryStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public RecoveryStore store() { return store; }

    /**
     * Dipanggil saat login. {@code then} selalu dipanggil (di main thread) setelah pemulihan selesai atau tidak diperlukan.
     */
    public void recoverIfNeeded(Player p, Runnable then) {
        IntroConfig cfg = plugin.config();
        UUID id = p.getUniqueId();
        if (plugin.sessions().isActive(id) || !cfg.recoveryRestoreOnJoin || !store.exists(id)) {
            then.run();
            return;
        }
        RecoveryStore.Record rec = store.load(id);
        if (rec == null) {
            then.run();
            return;
        }
        List<Location> targets = new ArrayList<>();
        targets.add(rec.snapshot().location());
        targets.add(cfg.resolveSpawn());
        targets.add(SafeTeleport.lastResort());
        SafeTeleport.chain(plugin, p, targets, false, ok -> {
            if (!p.isOnline()) return;
            rec.snapshot().applyState(p);
            if (ok) {
                store.delete(id);
                if (!rec.test()) plugin.progress().addInterrupted(id);
                p.sendMessage(cfg.message("recovered"));
                plugin.getLogger().info("Pemain " + p.getName() + " dipulihkan dari intro yang terputus.");
            } else {
                plugin.getLogger().warning("Pemulihan " + p.getName() + " gagal teleport; file recovery dipertahankan.");
            }
            then.run();
        });
    }
}
