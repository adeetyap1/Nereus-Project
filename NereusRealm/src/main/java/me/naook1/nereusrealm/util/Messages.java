package me.naook1.nereusrealm.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.naook1.nereusrealm.NereusRealm;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public final class Messages {

    private final NereusRealm plugin;
    private final Map<String, String> cache = new HashMap<>();
    private FileConfiguration cfg;
    private boolean smallCaps;

    public Messages(NereusRealm plugin) {
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

    public List<String> list(String key) {
        List<String> out = new ArrayList<>();
        for (String line : cfg.getStringList(key)) {
            out.add(smallCaps ? SmallCaps.convert(line) : line);
        }
        return out;
    }

    public void sendList(CommandSender to, String key, String... kv) {
        for (String line : list(key)) {
            String s = Text.fill(line.replace("{prefix}", raw("prefix")), kv);
            if (to instanceof Player p) {
                s = Text.papi(p, s);
            }
            to.sendMessage(Text.parse(s));
        }
    }
}
