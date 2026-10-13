package me.naook1.nereusrealm.joinleave;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.naook1.nereusrealm.NereusRealm;
import me.naook1.nereusrealm.util.SmallCaps;
import me.naook1.nereusrealm.util.Text;
import me.naook1.nereusrealm.util.TimeUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Player-written join/leave messages. Which ranks may write one, how long it can be and which
 * formatting is allowed is configured under custom-message in config.yml (ordered lowest to highest rank).
 */
public final class CustomMessages {

    public record Tier(String id, int maxLength, boolean colors, boolean formats, boolean hex) {
    }

    /** Result of validating a message. If error is not null it is a messages.yml key and arg fills {max}/{time}/{requirement}. */
    public record Check(String text, String error, String arg) {
        public boolean ok() {
            return error == null;
        }
    }

    private static final Pattern AMP = Pattern.compile("&(#[0-9a-fA-F]{6}|[0-9a-fA-FklmnorKLMNOR])|&");
    private static final String TOKEN = "\u0001";

    private final NereusRealm plugin;
    private final Map<String, Tier> tiers = new LinkedHashMap<>();
    private final List<String> blocked = new ArrayList<>();
    private boolean enabled;
    private String joinPrefix = "";
    private String leavePrefix = "";
    private String textColor = "&7";
    private String joinSound = "";
    private String leaveSound = "";
    private boolean convertSmallCaps;
    private long cooldownSeconds;

    public CustomMessages(NereusRealm plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        tiers.clear();
        blocked.clear();
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("custom-message");
        if (c == null) {
            enabled = false;
            return;
        }
        enabled = c.getBoolean("enabled", true);
        ConfigurationSection r = c.getConfigurationSection("ranks");
        if (r != null) {
            for (String id : r.getKeys(false)) {
                ConfigurationSection t = r.getConfigurationSection(id);
                if (t == null) {
                    continue;
                }
                String key = id.toLowerCase(Locale.ROOT);
                tiers.put(key, new Tier(key, Math.max(1, t.getInt("max-length", 40)),
                        t.getBoolean("colors", false), t.getBoolean("formats", false), t.getBoolean("hex", false)));
            }
        }
        joinPrefix = c.getString("join-prefix", "");
        leavePrefix = c.getString("leave-prefix", "");
        textColor = c.getString("text-color", "&7");
        joinSound = c.getString("join-sound", "");
        leaveSound = c.getString("leave-sound", "");
        convertSmallCaps = c.getBoolean("convert-small-caps", false);
        cooldownSeconds = Math.max(0, c.getLong("cooldown-seconds", 30L));
        blocked.addAll(c.getStringList("blocked-words"));
    }

    public boolean isEnabled() {
        return enabled && !tiers.isEmpty();
    }

    /** The highest tier the player qualifies for (group.<rank> or nereus.custom.<rank>), or null. */
    public Tier tierOf(Player p) {
        if (!isEnabled()) {
            return null;
        }
        List<Tier> list = new ArrayList<>(tiers.values());
        if (p.hasPermission("nereus.bypass")) {
            return list.get(list.size() - 1);
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            Tier t = list.get(i);
            if (p.hasPermission("group." + t.id()) || p.hasPermission("nereus.custom." + t.id())) {
                return t;
            }
        }
        return null;
    }

    /** "Duke ke atas" (lowest rank that may write a custom message). */
    public String requirement() {
        if (tiers.isEmpty()) {
            return "-";
        }
        String first = tiers.keySet().iterator().next();
        return plugin.ranks().displayName(first) + "&f ke atas";
    }

    public String describe(Tier t) {
        List<String> parts = new ArrayList<>();
        if (t.colors()) {
            parts.add(plugin.messages().raw("custom.label-colors"));
        }
        if (t.formats()) {
            parts.add(plugin.messages().raw("custom.label-format"));
        }
        if (t.hex()) {
            parts.add(plugin.messages().raw("custom.label-hex"));
        }
        return parts.isEmpty() ? plugin.messages().raw("custom.label-none") : String.join(" + ", parts);
    }

    // ------------------------------------------------------------------ stored text

    public String text(Player p, boolean join) {
        return plugin.data().getString(p.getUniqueId(), join ? "custom-join" : "custom-leave", "");
    }

    public boolean hasText(Player p, boolean join) {
        return !text(p, join).isBlank();
    }

    public String sound(boolean join) {
        return join ? joinSound : leaveSound;
    }

    public long cooldownLeft(Player p) {
        long last = plugin.data().getLong(p.getUniqueId(), "custom-last", 0L);
        long left = last + cooldownSeconds * 1000L - System.currentTimeMillis();
        return left > 0 ? (left + 999) / 1000 : 0;
    }

    /** Full message (prefix + text) ready for {@link StyleManager#render}. */
    public String buildRaw(boolean join, String text) {
        String body = convertSmallCaps ? SmallCaps.convert(text) : text;
        if (!body.contains("{player}")) {
            body = "&f{player} " + textColor + body;
        }
        return (join ? joinPrefix : leavePrefix) + textColor + body;
    }

    // ------------------------------------------------------------------ changing

    public Check set(Player p, boolean join, String input) {
        Tier tier = tierOf(p);
        if (tier == null) {
            return new Check(null, "custom.locked", requirement());
        }
        if (!p.hasPermission("nereus.bypass")) {
            long left = cooldownLeft(p);
            if (left > 0) {
                return new Check(null, "custom.cooldown", TimeUtil.format(left));
            }
        }
        Check c = check(tier, input);
        if (!c.ok()) {
            return c;
        }
        UUID id = p.getUniqueId();
        plugin.data().set(id, join ? "custom-join" : "custom-leave", c.text());
        plugin.data().set(id, join ? "join-mode" : "leave-mode", "custom");
        plugin.data().set(id, "custom-last", System.currentTimeMillis());
        return c;
    }

    /** Switch back to a previously written custom message. */
    public boolean use(Player p, boolean join) {
        if (tierOf(p) == null) {
            plugin.messages().send(p, "custom.locked", "requirement", requirement());
            return false;
        }
        if (!hasText(p, join)) {
            plugin.messages().send(p, "custom.none");
            return false;
        }
        plugin.data().set(p.getUniqueId(), join ? "join-mode" : "leave-mode", "custom");
        plugin.messages().send(p, "custom.used");
        return true;
    }

    public void reset(Player p, boolean join) {
        reset(p.getUniqueId(), join);
    }

    public void reset(UUID id, boolean join) {
        plugin.data().set(id, join ? "custom-join" : "custom-leave", null);
        plugin.data().set(id, join ? "join-mode" : "leave-mode", "style");
    }

    public void sendError(Player p, Check c) {
        plugin.messages().send(p, c.error(), "max", c.arg(), "time", c.arg(), "requirement", c.arg());
    }

    // ------------------------------------------------------------------ validation

    public Check check(Tier tier, String input) {
        if (input == null) {
            return new Check(null, "custom.err-empty", "");
        }
        String s = input.replace('§', '&').replaceAll("\\p{Cntrl}", " ").trim();
        s = s.replace("{player}", TOKEN);
        // no placeholders, MiniMessage tags or brackets from players
        s = s.replaceAll("[<>%{}\\[\\]\\\\]", "");

        Matcher m = AMP.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String code = m.group(1);
            String replacement = (code != null && allowed(tier, code))
                    ? "&" + code.toLowerCase(Locale.ROOT)
                    : "";
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        s = out.toString().replaceAll("\\s+", " ").trim();

        String visible = Text.stripCodes(s).replace(TOKEN, "").trim();
        if (visible.isEmpty() && !s.contains(TOKEN)) {
            return new Check(null, "custom.err-empty", "");
        }
        if (visible.length() > tier.maxLength()) {
            return new Check(null, "custom.err-too-long", String.valueOf(tier.maxLength()));
        }
        if (Text.containsBlocked(blocked, visible)) {
            return new Check(null, "custom.err-blocked", "");
        }
        return new Check(s.replace(TOKEN, "{player}"), null, "");
    }

    private static boolean allowed(Tier t, String code) {
        char c = Character.toLowerCase(code.charAt(0));
        if (c == '#') {
            return t.hex();
        }
        if (c == 'k') {
            return false;
        }
        if (c == 'r') {
            return t.colors() || t.formats() || t.hex();
        }
        if (c == 'l' || c == 'o' || c == 'n' || c == 'm') {
            return t.formats();
        }
        return t.colors();
    }
}
