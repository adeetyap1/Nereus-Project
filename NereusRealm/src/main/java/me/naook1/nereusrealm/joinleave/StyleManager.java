package me.naook1.nereusrealm.joinleave;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.naook1.nereusrealm.NereusRealm;
import me.naook1.nereusrealm.util.SmallCaps;
import me.naook1.nereusrealm.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** Join/leave styles with LuckPerms-friendly access control, plus the player's custom message when active. */
public final class StyleManager {

    public record Style(String id, String name, Material icon, String permission,
                        List<String> ranks, String message, String sound, float pitch) {
    }

    /** What is actually announced for a player. */
    public record Current(String raw, String sound, float pitch) {
    }

    private final NereusRealm plugin;
    private final Map<String, Style> join = new LinkedHashMap<>();
    private final Map<String, Style> leave = new LinkedHashMap<>();
    private String defaultJoin = "default";
    private String defaultLeave = "default";
    private boolean firstJoinEnabled;
    private String firstJoinMessage = "";

    public StyleManager(NereusRealm plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.loadYaml("styles.yml");
        boolean sc = plugin.getConfig().getBoolean("small-caps.styles", true);
        join.clear();
        leave.clear();
        load(c.getConfigurationSection("join-styles"), join, sc);
        load(c.getConfigurationSection("leave-styles"), leave, sc);
        defaultJoin = c.getString("default-join", "default").toLowerCase(Locale.ROOT);
        defaultLeave = c.getString("default-leave", "default").toLowerCase(Locale.ROOT);
        firstJoinEnabled = c.getBoolean("first-join.enabled", true);
        String fm = c.getString("first-join.message", "");
        firstJoinMessage = sc ? SmallCaps.convert(fm) : fm;
    }

    private void load(ConfigurationSection sec, Map<String, Style> out, boolean sc) {
        if (sec == null) {
            return;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            String name = s.getString("name", id);
            String msg = s.getString("message", "");
            if (sc) {
                name = SmallCaps.convert(name);
                msg = SmallCaps.convert(msg);
            }
            Material icon = Material.matchMaterial(s.getString("icon", "PAPER"));
            if (icon == null || !icon.isItem()) {
                icon = Material.PAPER;
            }
            String key = id.toLowerCase(Locale.ROOT);
            out.put(key, new Style(key, name, icon, s.getString("permission", ""),
                    new ArrayList<>(s.getStringList("ranks")), msg,
                    s.getString("sound", ""), (float) s.getDouble("sound-pitch", 1.0)));
        }
    }

    public Map<String, Style> styles(boolean isJoin) {
        return isJoin ? join : leave;
    }

    public Style get(boolean isJoin, String id) {
        return id == null ? null : styles(isJoin).get(id.toLowerCase(Locale.ROOT));
    }

    /** LuckPerms exposes every group a player is in (including inherited) as the node group.<name>. */
    public boolean canUse(Player p, Style s) {
        if (p.hasPermission("nereus.bypass")) {
            return true;
        }
        boolean noPerm = s.permission() == null || s.permission().isBlank();
        if (noPerm && s.ranks().isEmpty()) {
            return true;
        }
        if (!noPerm && p.hasPermission(s.permission())) {
            return true;
        }
        for (String rank : s.ranks()) {
            if (p.hasPermission("group." + rank.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public String requirement(Style s) {
        if (!s.ranks().isEmpty()) {
            return plugin.ranks().displayName(s.ranks().get(0)) + "&f ke atas";
        }
        if (s.permission() != null && !s.permission().isBlank()) {
            return s.permission();
        }
        return "-";
    }

    /** The style the player gets (their choice, or default if missing / no longer allowed). */
    public Style resolve(Player p, boolean isJoin) {
        Map<String, Style> map = styles(isJoin);
        String id = plugin.data().getString(p.getUniqueId(), isJoin ? "join-style" : "leave-style", null);
        Style s = id == null ? null : map.get(id);
        if (s != null && canUse(p, s)) {
            return s;
        }
        s = map.get(isJoin ? defaultJoin : defaultLeave);
        if (s != null) {
            return s;
        }
        return map.values().stream().findFirst().orElse(null);
    }

    /** True when the player's own custom message is what gets announced. */
    public boolean customActive(Player p, boolean isJoin) {
        String mode = plugin.data().getString(p.getUniqueId(), isJoin ? "join-mode" : "leave-mode", "style");
        return "custom".equals(mode)
                && plugin.custom().tierOf(p) != null
                && plugin.custom().hasText(p, isJoin);
    }

    /** The selected built-in style, or null while the custom message is active. */
    public Style selected(Player p, boolean isJoin) {
        return customActive(p, isJoin) ? null : resolve(p, isJoin);
    }

    public String displayName(Player p, boolean isJoin) {
        if (customActive(p, isJoin)) {
            return plugin.messages().raw("custom.name");
        }
        Style s = resolve(p, isJoin);
        return s == null ? "-" : s.name();
    }

    public Current current(Player p, boolean isJoin) {
        if (customActive(p, isJoin)) {
            return new Current(plugin.custom().buildRaw(isJoin, plugin.custom().text(p, isJoin)),
                    plugin.custom().sound(isJoin), 1f);
        }
        Style s = resolve(p, isJoin);
        return s == null ? null : new Current(s.message(), s.sound(), s.pitch());
    }

    public boolean select(Player p, boolean isJoin, String id) {
        Style s = get(isJoin, id);
        if (s == null) {
            plugin.messages().send(p, "style.unknown");
            return false;
        }
        if (!canUse(p, s)) {
            plugin.messages().send(p, "style.locked", "requirement", requirement(s));
            return false;
        }
        plugin.data().set(p.getUniqueId(), isJoin ? "join-style" : "leave-style", s.id());
        plugin.data().set(p.getUniqueId(), isJoin ? "join-mode" : "leave-mode", "style");
        plugin.messages().send(p, isJoin ? "style.selected-join" : "style.selected-leave", "style", s.name());
        preview(p, s);
        return true;
    }

    public void preview(Player p, boolean isJoin, String id) {
        Style s = get(isJoin, id);
        if (s != null) {
            preview(p, s);
        }
    }

    private void preview(Player p, Style s) {
        plugin.messages().send(p, "style.preview");
        p.sendMessage(render(p, s.message(), p));
    }

    /** Preview of whatever is currently active (style or custom). */
    public void previewCurrent(Player p, boolean isJoin) {
        Current c = current(p, isJoin);
        if (c == null) {
            return;
        }
        plugin.messages().send(p, "style.preview");
        p.sendMessage(render(p, c.raw(), p));
    }

    public Component render(Player subject, String raw, Player papiContext) {
        return Text.parse(Text.papi(papiContext, Text.fill(raw, "player", subject.getName())));
    }

    public Component render(Player subject, Style s) {
        return render(subject, s.message(), subject);
    }

    public Component renderCustom(Player p, boolean isJoin) {
        return render(p, plugin.custom().buildRaw(isJoin, plugin.custom().text(p, isJoin)), p);
    }

    public void announceJoin(Player p, boolean first) {
        if (first && firstJoinEnabled && !firstJoinMessage.isBlank()) {
            broadcast(p, firstJoinMessage, "", 1f);
            return;
        }
        Current c = current(p, true);
        if (c != null) {
            broadcast(p, c.raw(), c.sound(), c.pitch());
        }
    }

    public void announceLeave(Player p) {
        Current c = current(p, false);
        if (c != null) {
            broadcast(p, c.raw(), c.sound(), c.pitch());
        }
    }

    private void broadcast(Player subject, String raw, String sound, float pitch) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        Component c = render(subject, raw, subject);
        float volume = (float) plugin.getConfig().getDouble("join-leave.sound-volume", 0.7);
        for (Player v : Bukkit.getOnlinePlayers()) {
            if (!plugin.data().getBoolean(v.getUniqueId(), "show-joinleave", true)) {
                continue;
            }
            v.sendMessage(c);
            if (sound != null && !sound.isBlank()) {
                v.playSound(v.getLocation(), sound, volume, pitch);
            }
        }
        Bukkit.getConsoleSender().sendMessage(c);
    }
}
