package my.nereus.intro.manager;

import my.nereus.intro.NereusIntro;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Menyembunyikan pemain yang sedang intro dari pemain lain (dua arah) agar ruang putih tidak terganggu. */
public final class IsolationService {

    private final NereusIntro plugin;
    private final Set<UUID> isolated = ConcurrentHashMap.newKeySet();

    public IsolationService(NereusIntro plugin) {
        this.plugin = plugin;
    }

    public void apply(Player p) {
        isolated.add(p.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(p)) hidePair(p, other);
        }
    }

    public void release(Player p) {
        if (!isolated.remove(p.getUniqueId())) return;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            try {
                p.showPlayer(plugin, other);
                other.showPlayer(plugin, p);
            } catch (Throwable ignored) {
                // pemain sedang keluar
            }
        }
    }

    /** Pemain baru login saat ada sesi aktif: sembunyikan dari pemain yang sedang intro. */
    public void onJoin(Player joined) {
        for (UUID id : isolated) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !p.equals(joined)) hidePair(p, joined);
        }
    }

    private void hidePair(Player a, Player b) {
        try {
            a.hidePlayer(plugin, b);
            b.hidePlayer(plugin, a);
        } catch (Throwable ignored) {
            // abaikan
        }
    }
}
