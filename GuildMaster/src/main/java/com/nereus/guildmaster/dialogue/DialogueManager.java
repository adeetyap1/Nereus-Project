package com.nereus.guildmaster.dialogue;

import com.nereus.guildmaster.GuildMaster;
import com.nereus.guildmaster.Text;
import com.nereus.guildmaster.adventurer.Adventurer;
import com.nereus.guildmaster.adventurer.AdventurerManager;
import com.nereus.guildmaster.hook.BedrockForms;
import com.nereus.guildmaster.hook.Platform;
import com.nereus.guildmaster.rpg.RpgAdapter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mesin dialog NPC bertahap (node di dialogue.yml). Tampilan: popup native Paper untuk Java,
 * form Floodgate untuk Bedrock, dan teks bernomor sebagai cadangan.
 */
public final class DialogueManager {
    private static final class Session {
        String node;
        final Deque<String> back = new ArrayDeque<>();
        List<Screen.Button> buttons = List.of();
        long token;
        long lastChoose;
    }

    private final GuildMaster plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private long tokenCounter = 1;

    public DialogueManager(GuildMaster plugin) {
        this.plugin = plugin;
    }

    public void end(UUID id) {
        sessions.remove(id);
    }

    public void clear() {
        sessions.clear();
    }

    // ------------------------------------------------------------------ mulai

    /** Buka dialog NPC sesuai status pemain (belum daftar, terdaftar, dicabut). */
    public void open(Player p) {
        plugin.adventurers().ready(p, () -> {
            YamlConfiguration y = plugin.configs().dialogue();
            Adventurer a = plugin.adventurers().get(p.getUniqueId());
            String key = a == null ? "unregistered" : a.active() ? "registered" : "revoked";
            String node = y.getString("entry." + key, "welcome");
            Session s = new Session();
            sessions.put(p.getUniqueId(), s);
            show(p, s, node);
        });
    }

    private void show(Player p, Session s, String nodeId) {
        YamlConfiguration y = plugin.configs().dialogue();
        ConfigurationSection n = y.getConfigurationSection("nodes." + nodeId);
        if (n == null) {
            plugin.getLogger().warning("dialogue.yml: node '" + nodeId + "' tidak ada.");
            sessions.remove(p.getUniqueId());
            return;
        }
        Map<String, String> vars = vars(p);
        List<String> lines = new ArrayList<>();
        for (String l : n.getStringList("lines")) {
            lines.add(Text.fill(l, vars));
        }
        List<Screen.Button> buttons = new ArrayList<>();
        for (Map<?, ?> b : n.getMapList("buttons")) {
            String label = String.valueOf(b.containsKey("label") ? b.get("label") : "?");
            String tip = b.containsKey("tooltip") ? String.valueOf(b.get("tooltip")) : "";
            String action = String.valueOf(b.containsKey("action") ? b.get("action") : "close");
            buttons.add(new Screen.Button(Text.fill(label, vars), Text.fill(tip, vars), action));
        }
        String title = Text.fill(n.getString("title", y.getString("npc-name", "&6GUILD MASTER")), vars);
        s.node = nodeId;
        s.buttons = buttons;
        s.token = tokenCounter++;
        Screen screen = new Screen(title, lines, buttons);
        long token = s.token;
        UUID id = p.getUniqueId();
        java.util.function.IntConsumer handler = index -> Bukkit.getScheduler().runTask(plugin, () -> {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null) {
                choose(pl, token, index);
            }
        });
        if (Platform.isBedrock(p) && Platform.floodgate()) {
            try {
                if (BedrockForms.send(p, screen, handler)) {
                    return;
                }
            } catch (LinkageError e) {
                plugin.getLogger().warning("Floodgate API tidak cocok: " + e.getMessage());
            }
        }
        if (Platform.isBedrock(p)) {
            TextView.show(plugin, p, screen);
            return;
        }
        try {
            JavaDialogView.show(plugin, p, screen, handler);
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().warning("Dialog native gagal, pakai teks: " + e.getMessage());
            TextView.show(plugin, p, screen);
        }
    }

    // ------------------------------------------------------------------ pilihan

    /** Dipanggil dari tombol dialog (token memastikan tombol dari layar lama diabaikan). */
    public void choose(Player p, long token, int index) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.token != token || index < 0 || index >= s.buttons.size()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - s.lastChoose < 150) {
            return;
        }
        s.lastChoose = now;
        run(p, s, s.buttons.get(index).action());
    }

    /** Untuk /guildmaster choose &lt;n&gt; (cadangan tanpa popup). */
    public boolean chooseNumber(Player p, int number) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) {
            return false;
        }
        choose(p, s.token, number - 1);
        return true;
    }

    private void run(Player p, Session s, String action) {
        if (action.startsWith("goto:")) {
            s.back.push(s.node);
            show(p, s, action.substring(5));
        } else if (action.equals("back")) {
            String prev = s.back.isEmpty() ? null : s.back.pop();
            if (prev == null) {
                sessions.remove(p.getUniqueId());
                p.closeDialog();
            } else {
                show(p, s, prev);
            }
        } else if (action.equals("register")) {
            plugin.adventurers().register(p, result -> {
                if (!p.isOnline()) {
                    return;
                }
                String key = switch (result) {
                    case OK -> "registered-ok";
                    case ALREADY -> "already";
                    case REVOKED -> "revoked";
                    case RANK_TOO_LOW -> "rank-too-low";
                    case RPG_UNAVAILABLE -> "rpg-unavailable";
                    case ERROR -> "error";
                };
                if (result == AdventurerManager.Result.OK) {
                    s.back.clear();
                }
                show(p, s, plugin.configs().dialogue().getString("results." + key, key));
            });
        } else if (action.equals("menu")) {
            sessions.remove(p.getUniqueId());
            p.closeDialog();
            plugin.gui().openMain(p);
        } else {
            sessions.remove(p.getUniqueId());
            p.closeDialog();
        }
    }

    // ------------------------------------------------------------------ variabel

    private Map<String, String> vars(Player p) {
        Map<String, String> v = new HashMap<>();
        v.put("player", p.getName());
        v.put("npc", Text.strip(plugin.configs().dialogue().getString("npc-name", "Guild Master")));
        String minKey = plugin.getConfig().getString("registration.min-rank", "F");
        v.put("rank_min", minKey);
        RpgAdapter rpg = plugin.rpg();
        RpgAdapter.RpgProfile prof = rpg.available() ? rpg.profile(p) : null;
        v.put("rank", prof == null ? "-" : prof.rankLabel());
        v.put("level", prof == null ? "-" : String.valueOf(prof.level()));
        v.put("quests", prof == null ? "0" : String.valueOf(prof.totalQuests()));
        Adventurer a = plugin.adventurers().get(p.getUniqueId());
        v.put("registered_date", a == null ? "-" : date(a.registeredAt()));
        v.put("status", a == null ? "Belum terdaftar" : a.active() ? "Registered" : "Dicabut");
        return v;
    }

    public String date(long millis) {
        String fmt = plugin.getConfig().getString("date-format", "dd MMM yyyy");
        try {
            return new SimpleDateFormat(fmt).format(new Date(millis));
        } catch (IllegalArgumentException e) {
            return new SimpleDateFormat("dd MMM yyyy").format(new Date(millis));
        }
    }
}
