package me.naook1.nereusbank.util;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.naook1.nereusbank.NereusBank;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** messages.yml with small caps. Missing keys fall back to the jar defaults. */
public final class Messages {

    private final NereusBank plugin;
    private final Map<String, String> cache = new HashMap<>();
    private FileConfiguration cfg;
    private boolean smallCaps;

    public Messages(NereusBank plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (Exception ignored) {
            // defaults are optional
        }
        smallCaps = plugin.getConfig().getBoolean("small-caps", true);
        cache.clear();
    }

    public boolean has(String key) {
        return cfg != null && cfg.contains(key);
    }

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
        return Text.fill(raw(key).replace("{prefix}", raw("prefix")), kv);
    }

    public void send(CommandSender to, String key, String... kv) {
        String s = format(key, kv);
        if (!s.isBlank()) {
            to.sendMessage(Text.parse(s));
        }
    }

    public void broadcast(String key, String... kv) {
        String s = format(key, kv);
        if (!s.isBlank()) {
            Bukkit.broadcast(Text.parse(s));
        }
    }

    public List<String> list(String key, String... kv) {
        List<String> out = new ArrayList<>();
        for (String line : cfg.getStringList(key)) {
            String s = smallCaps ? SmallCaps.convert(line) : line;
            out.add(Text.fill(s.replace("{prefix}", raw("prefix")), kv));
        }
        return out;
    }

    public void sendList(CommandSender to, String key, String... kv) {
        for (String line : list(key, kv)) {
            to.sendMessage(Text.parse(line));
        }
    }
}
