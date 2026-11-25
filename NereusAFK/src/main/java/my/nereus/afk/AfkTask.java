package my.nereus.afk;

import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tiap detik: pemain di dalam zona AFK mengumpulkan waktu; cukup interval = dapat fragmen (dibatasi per hari). */
final class AfkTask extends BukkitRunnable {
    private final NereusAFK plugin;
    private final Map<UUID, Integer> progress = new ConcurrentHashMap<>();

    AfkTask(NereusAFK plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        var cfg = plugin.getConfig();
        int interval = Math.max(5, cfg.getInt("afk.interval-seconds", 60));
        int per = Math.max(1, cfg.getInt("afk.fragments-per-interval", 1));
        int cap = Math.max(0, cfg.getInt("afk.daily-cap", 180));
        boolean bar = cfg.getBoolean("afk.show-actionbar", true);

        progress.keySet().removeIf(id -> plugin.getServer().getPlayer(id) == null);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (p.getGameMode() == GameMode.SPECTATOR || plugin.zones().zoneAt(p.getLocation()) == null) {
                progress.remove(id);
                continue;
            }
            PlayerData d = plugin.store().get(id);
            String capText = cap == 0 ? "∞" : String.valueOf(cap);
            if (cap > 0 && d.today >= cap) {
                if (bar) {
                    p.sendActionBar(Text.color(fill(cfg.getString("messages.actionbar-capped", ""), d, 0, capText)));
                }
                continue;
            }
            int s = progress.merge(id, 1, Integer::sum);
            if (s >= interval) {
                progress.put(id, 0);
                int give = cap > 0 ? Math.min(per, cap - d.today) : per;
                d.fragments += give;
                d.total += give;
                d.today += give;
                plugin.store().markDirty();
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
                p.sendMessage(Text.color(plugin.m("gained", "amount", String.valueOf(give), "fragments", String.valueOf(d.fragments))));
            } else if (bar) {
                p.sendActionBar(Text.color(fill(cfg.getString("messages.actionbar", ""), d, interval - s, capText)));
            }
        }
    }

    private String fill(String s, PlayerData d, int seconds, String cap) {
        return s.replace("{fragments}", String.valueOf(d.fragments)).replace("{seconds}", String.valueOf(seconds))
                .replace("{today}", String.valueOf(d.today)).replace("{cap}", cap);
    }
}
