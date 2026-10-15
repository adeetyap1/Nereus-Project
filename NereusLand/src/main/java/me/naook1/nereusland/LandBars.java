package me.naook1.nereusland;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.naook1.nereusland.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Shows / updates / hides one boss bar per player depending on the RedProtect land they stand in. */
public final class LandBars {

    private static final class State {
        final BossBar bar;
        String key;

        State(BossBar bar, String key) {
            this.bar = bar;
            this.key = key;
        }
    }

    private final NereusLand plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private final Set<UUID> disabled = new HashSet<>();
    private final File file;

    private boolean enabled = true;
    private float progress = 1f;
    private BossBar.Overlay overlay = BossBar.Overlay.PROGRESS;
    private BossBar.Color ownColor = BossBar.Color.GREEN;
    private BossBar.Color otherColor = BossBar.Color.YELLOW;
    private boolean wild;
    private BossBar.Color wildColor = BossBar.Color.WHITE;
    private final Set<String> ignoreWorlds = new HashSet<>();

    public LandBars(NereusLand plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void reload() {
        var c = plugin.getConfig();
        enabled = c.getBoolean("enabled", true);
        progress = (float) Math.max(0, Math.min(1, c.getDouble("bossbar.progress", 1.0)));
        overlay = parse(BossBar.Overlay.class, c.getString("bossbar.overlay", "PROGRESS"), BossBar.Overlay.PROGRESS);
        ownColor = parse(BossBar.Color.class, c.getString("bossbar.own-color", "GREEN"), BossBar.Color.GREEN);
        otherColor = parse(BossBar.Color.class, c.getString("bossbar.other-color", "YELLOW"), BossBar.Color.YELLOW);
        wild = c.getBoolean("bossbar.wilderness.enabled", false);
        wildColor = parse(BossBar.Color.class, c.getString("bossbar.wilderness.color", "WHITE"), BossBar.Color.WHITE);
        ignoreWorlds.clear();
        for (String w : c.getStringList("ignore-worlds")) {
            ignoreWorlds.add(w.toLowerCase(Locale.ROOT));
        }
        hideAll();      // rebuilt on the next tick with the new settings
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return fallback;
        }
    }

    public void load() {
        disabled.clear();
        if (!file.exists()) {
            return;
        }
        for (String s : YamlConfiguration.loadConfiguration(file).getStringList("disabled")) {
            try {
                disabled.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // skip broken id
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> ids = new ArrayList<>();
        for (UUID u : disabled) {
            ids.add(u.toString());
        }
        y.set("disabled", ids);
        final String text = y.saveToString();
        Runnable write = () -> {
            try {
                File dir = file.getParentFile();
                if (dir != null && !dir.exists()) {
                    dir.mkdirs();
                }
                Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save data.yml: " + e.getMessage());
            }
        };
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        } else {
            write.run();
        }
    }

    /** Returns true when the bar is now enabled for the player. */
    public boolean toggle(Player p) {
        boolean nowOn = disabled.remove(p.getUniqueId());
        if (!nowOn) {
            disabled.add(p.getUniqueId());
            hide(p);
        }
        save();
        return nowOn;
    }

    public void tick() {
        var hook = plugin.hook();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!enabled || disabled.contains(p.getUniqueId()) || !p.hasPermission("nereusland.use")
                    || ignoreWorlds.contains(p.getWorld().getName().toLowerCase(Locale.ROOT))) {
                hide(p);
                continue;
            }
            RedProtectHook.Land land = hook.landAt(p);
            if (land == null) {
                if (wild) {
                    show(p, "wild", plugin.messages().format("bossbar.wilderness"), wildColor);
                } else {
                    hide(p);
                }
                continue;
            }
            var m = plugin.messages();
            String text;
            BossBar.Color color;
            if (land.role() != null) {
                text = m.format("bossbar.own", "name", land.name(), "owner", land.owner(), "role", m.raw("role." + land.role()));
                color = ownColor;
            } else {
                text = m.format("bossbar.other", "name", land.name(), "owner", land.owner(), "role", "-");
                color = otherColor;
            }
            show(p, land.name() + "|" + land.role() + "|" + land.owner(), text, color);
        }
    }

    private void show(Player p, String key, String text, BossBar.Color color) {
        State st = states.get(p.getUniqueId());
        if (st == null) {
            BossBar bar = BossBar.bossBar(Text.parse(text), progress, color, overlay);
            states.put(p.getUniqueId(), new State(bar, key));
            p.showBossBar(bar);
            return;
        }
        if (!key.equals(st.key)) {
            st.key = key;
            st.bar.name(Text.parse(text));
            st.bar.color(color);
        }
    }

    public void hide(Player p) {
        State st = states.remove(p.getUniqueId());
        if (st != null) {
            p.hideBossBar(st.bar);
        }
    }

    public void hideAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            hide(p);
        }
        states.clear();
    }

    public int active() {
        return states.size();
    }
}
