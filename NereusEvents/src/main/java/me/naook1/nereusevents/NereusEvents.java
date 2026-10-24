package me.naook1.nereusevents;

import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NereusEvents - global timed events ("admin abuse") with boss bar, title, sound and bonuses
 * read by NereusFishing, NereusFarmPrice and NereusLottery. Developer: naook1/adityap
 */
public final class NereusEvents extends JavaPlugin implements Listener {

    private EventManager events;
    private EventGui gui;
    private Map<String, String> msgs = new java.util.HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        events = new EventManager(this);
        gui = new EventGui(this);
        loadMessages();
        events.reload();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(gui, this);
        EventCommand cmd = new EventCommand(this);
        getCommand("event").setExecutor(cmd);
        getCommand("event").setTabCompleter(cmd);
        getServer().getScheduler().runTaskTimer(this, events::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, events::playTracks, 20L, 2L);
        long iv = Math.max(4L, getConfig().getLong("lights.interval-ticks", 10L));
        getServer().getScheduler().runTaskTimer(this, events::disco, 20L, iv);
        getLogger().info("NereusEvents v" + getDescription().getVersion() + " enabled | developer: naook1/adityap");
    }

    @Override
    public void onDisable() {
        if (events != null) {
            events.stopAll();
            for (Player p : Bukkit.getOnlinePlayers()) {
                events.clearLuck(p);
            }
        }
    }

    private void loadMessages() {
        msgs.clear();
        var s = getConfig().getConfigurationSection("messages");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                msgs.put(k, s.getString(k, ""));
            }
        }
    }

    public void reloadAll() {
        reloadConfig();
        loadMessages();
        events.reload();
    }

    /** message from config.yml (raw, unparsed). */
    public Messages messages() {
        return new Messages(msgs);
    }

    public record Messages(Map<String, String> map) {
        public String get(String key) {
            return map.getOrDefault(key, key);
        }
    }

    public EventManager events() {
        return events;
    }

    public EventGui gui() {
        return gui;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        events.joined(p);
        Bukkit.getScheduler().runTaskLater(this, () -> events.showBars(p), 40L);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        events.moved(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTaskLater(this, () -> events.moved(e.getPlayer()), 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        events.left(e.getPlayer());
    }

    @EventHandler
    public void onPack(PlayerResourcePackStatusEvent e) {
        if (e.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            events.packLoaded(e.getPlayer());
        }
    }

    // ---------------------------------------------------------------- API (used by other plugins via reflection)

    /** Product of all active event values for the key, 1.0 if none. */
    public double getMultiplier(String key) {
        return events.multiplier(key);
    }

    /** Sum of all active event values for the key, 0.0 if none. */
    public double getBonus(String key) {
        return events.bonus(key);
    }

    public boolean isEventActive(String id) {
        return events.isActive(id);
    }
}
