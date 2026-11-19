package my.nereus.intro.recovery;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Mencoba beberapa lokasi berurutan sampai ada yang berhasil. Tidak pernah menebak koordinat. */
public final class SafeTeleport {

    private SafeTeleport() {}

    public static Location lastResort() {
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public static void chain(Plugin plugin, Player p, List<Location> candidates, boolean sync, Consumer<Boolean> done) {
        List<Location> list = candidates.stream().filter(Objects::nonNull).toList();
        step(plugin, p, list, 0, sync, done);
    }

    private static void step(Plugin plugin, Player p, List<Location> list, int i, boolean sync, Consumer<Boolean> done) {
        if (i >= list.size()) {
            done.accept(false);
            return;
        }
        Location l = list.get(i);
        if (sync) {
            boolean ok;
            try {
                ok = p.teleport(l, PlayerTeleportEvent.TeleportCause.PLUGIN);
            } catch (Throwable t) {
                ok = false;
            }
            if (ok) done.accept(true);
            else step(plugin, p, list, i + 1, true, done);
            return;
        }
        try {
            p.teleportAsync(l, PlayerTeleportEvent.TeleportCause.PLUGIN).whenComplete((ok, err) -> onMain(plugin, () -> {
                if (err == null && Boolean.TRUE.equals(ok)) done.accept(true);
                else step(plugin, p, list, i + 1, false, done);
            }));
        } catch (Throwable t) {
            step(plugin, p, list, i + 1, false, done);
        }
    }

    private static void onMain(Plugin plugin, Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, r);
        } catch (Throwable ignored) {
            // plugin sedang dimatikan
        }
    }
}
