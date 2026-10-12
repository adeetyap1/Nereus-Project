package me.naook1.nereusistent.util;

import java.util.HashMap;
import java.util.Map;
import me.naook1.nereusistent.Nereusistent;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public final class Messages {

    private final Nereusistent plugin;
    private final Map<String, String> cache = new HashMap<>();
    private FileConfiguration cfg;
    private boolean smallCaps;

    public Messages(Nereusistent plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        cfg = plugin.loadYaml("messages.yml");
        smallCaps = plugin.getConfig().getBoolean("small-caps.messages", true);
        cache.clear();
    }

    /** Raw (not yet colored) message, small caps applied if enabled. */
    public String raw(String key) {
        return cache.computeIfAbsent(key, k -> {
            String s = cfg.getString(k);
            if (s == null) {
                return "&cMissing message: " + k;
            }
            return smallCaps ? SmallCaps.convert(s) : s;
        });
    }

    public String format(String key, String... kv) {
        String s = raw(key).replace("{prefix}", raw("prefix"));
        return Text.fill(s, kv);
    }

    public void send(CommandSender to, String key, String... kv) {
        String s = format(key, kv);
        if (s.isBlank()) {
            return;
        }
        if (to instanceof Player p) {
            s = Text.papi(p, s);
        }
        to.sendMessage(Text.parse(s));
    }

    public void sendList(CommandSender to, String key, String... kv) {
        for (String line : cfg.getStringList(key)) {
            String s = smallCaps ? SmallCaps.convert(line) : line;
            s = Text.fill(s.replace("{prefix}", raw("prefix")), kv);
            if (to instanceof Player p) {
                s = Text.papi(p, s);
            }
            to.sendMessage(Text.parse(s));
        }
    }
}
