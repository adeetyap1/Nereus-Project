package my.nereus.intro.manager;

import my.nereus.intro.NereusIntro;
import my.nereus.intro.config.IntroConfig;
import my.nereus.intro.session.IntroSession;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mengirim pack musik intro langsung dari plugin (pemain Java) dan menunggu sampai termuat sebelum intro mulai,
 * sehingga musik pasti terdengar tanpa mengubah server.properties. Gagal/ditolak/timeout = intro tetap jalan tanpa musik.
 */
public final class ResourcePackService implements Listener {

    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("nereusintro-music".getBytes(StandardCharsets.UTF_8));

    private final NereusIntro plugin;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Runnable> waiting = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> timeouts = new ConcurrentHashMap<>();

    public ResourcePackService(NereusIntro plugin) {
        this.plugin = plugin;
    }

    /** {@code ready} dipanggil tepat sekali di main thread. */
    public void ensure(IntroSession s, Runnable ready) {
        IntroConfig cfg = s.config();
        Player p = s.player();
        UUID id = p.getUniqueId();
        if (!cfg.packEnabled || cfg.packUrl.isBlank() || s.isBedrock() || loaded.contains(id)) {
            ready.run();
            return;
        }
        byte[] hash = parseHash(cfg.packSha1);
        try {
            String prompt = PlainTextComponentSerializer.plainText()
                    .serialize(MiniMessage.miniMessage().deserialize(cfg.packPrompt));
            waiting.put(id, ready);
            p.addResourcePack(PACK_ID, cfg.packUrl, hash, prompt, cfg.packForce);
        } catch (Throwable t) {
            plugin.getLogger().warning("Gagal mengirim resource pack intro: " + t);
            waiting.remove(id);
            ready.run();
            return;
        }
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getLogger().info("Resource pack intro belum termuat untuk " + p.getName() + " (timeout); intro dilanjutkan tanpa menunggu.");
            complete(id);
        }, Math.max(20L, cfg.packWaitSeconds * 20L));
        timeouts.put(id, timeout);
        s.track(timeout);
    }

    private void complete(UUID id) {
        BukkitTask t = timeouts.remove(id);
        if (t != null) t.cancel();
        Runnable r = waiting.remove(id);
        if (r != null) r.run();
    }

    /** Sesi berakhir saat menunggu: batalkan tanpa menjalankan callback. */
    public void cancel(UUID id) {
        BukkitTask t = timeouts.remove(id);
        if (t != null) t.cancel();
        waiting.remove(id);
    }

    public void forget(UUID id) {
        cancel(id);
        loaded.remove(id);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        UUID pid = e.getID();
        if (pid != null && !PACK_ID.equals(pid)) return;
        UUID id = e.getPlayer().getUniqueId();
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                loaded.add(id);
                complete(id);
            }
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                plugin.getLogger().info("Resource pack intro untuk " + e.getPlayer().getName() + ": " + e.getStatus()
                        + "; intro dilanjutkan tanpa musik.");
                complete(id);
            }
            default -> { /* ACCEPTED / DOWNLOADED: tunggu hasil akhir */ }
        }
    }

    private static byte[] parseHash(String hex) {
        if (hex == null) return null;
        String h = hex.trim();
        if (h.length() != 40) return null;
        byte[] out = new byte[20];
        try {
            for (int i = 0; i < 20; i++) out[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }
}
