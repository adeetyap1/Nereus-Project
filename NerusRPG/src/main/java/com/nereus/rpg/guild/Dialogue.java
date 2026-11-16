package com.nereus.rpg.guild;

import com.nereus.rpg.Core;
import com.nereus.rpg.Defs;
import com.nereus.rpg.GuildStatus;
import com.nereus.rpg.Menus;
import com.nereus.rpg.NerusRPG;
import com.nereus.rpg.Profile;
import com.nereus.rpg.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dialog Caylin (Guild Master Nerus Guild), node-node di guild-dialogue.yml.
 * Tampilan: inventori (Java, bawaan) atau popup Paper (opsional), form Floodgate untuk Bedrock,
 * dan teks bernomor sebagai cadangan.
 */
public final class Dialogue implements Listener {
    private static final class Session {
        String node;
        final Deque<String> back = new ArrayDeque<>();
        List<Screen.Button> buttons = List.of();
        long token;
        long lastChoose;
    }

    private final NerusRPG plugin;
    private final Core core;
    private final Guild guild;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private long tokenCounter = 1;
    private YamlConfiguration yml = new YamlConfiguration();

    public Dialogue(NerusRPG plugin, Core core, Guild guild) {
        this.plugin = plugin;
        this.core = core;
        this.guild = guild;
    }

    public void load() {
        File f = new File(plugin.getDataFolder(), "guild-dialogue.yml");
        if (!f.exists()) {
            plugin.saveResource("guild-dialogue.yml", false);
        }
        yml = YamlConfiguration.loadConfiguration(f);
        try (Reader r = new InputStreamReader(plugin.getResource("guild-dialogue.yml"), StandardCharsets.UTF_8)) {
            yml.setDefaults(YamlConfiguration.loadConfiguration(r));
        } catch (Exception ignored) {
            // tanpa bawaan jar
        }
    }

    public String npcName() {
        return Text.strip(yml.getString("npc-name", "Caylin"));
    }

    public void end(UUID id) {
        sessions.remove(id);
    }

    public void clear() {
        sessions.clear();
    }

    // ------------------------------------------------------------------ mulai

    /** Buka dialog sesuai status pemain. */
    public void open(Player p) {
        Profile pr = core.profile(p);
        String key;
        if (pr.status == GuildStatus.REGISTERED) {
            if (pr.restoredNotice) {
                pr.restoredNotice = false;
                core.save(pr);
                key = "restored";
            } else {
                key = "registered";
            }
        } else if (pr.status == GuildStatus.REVOKED) {
            key = "revoked";
        } else {
            key = "unregistered";
        }
        String node = yml.getString("entry." + key, "welcome");
        Session s = new Session();
        sessions.put(p.getUniqueId(), s);
        show(p, s, node);
    }

    private void show(Player p, Session s, String nodeId) {
        ConfigurationSection n = yml.getConfigurationSection("nodes." + nodeId);
        if (n == null) {
            plugin.getLogger().warning("guild-dialogue.yml: node '" + nodeId + "' tidak ada.");
            sessions.remove(p.getUniqueId());
            return;
        }
        Map<String, String> vars = vars(p);
        List<String> lines = new ArrayList<>();
        for (String l : n.getStringList("lines")) {
            if (l.trim().equals("%rank_list%")) {
                lines.addAll(guild.rankLines());
            } else {
                lines.add(Text.fill(l, vars));
            }
        }
        List<Screen.Button> buttons = new ArrayList<>();
        for (Map<?, ?> b : n.getMapList("buttons")) {
            String label = String.valueOf(b.containsKey("label") ? b.get("label") : "?");
            String tip = b.containsKey("tooltip") ? String.valueOf(b.get("tooltip")) : "";
            String action = String.valueOf(b.containsKey("action") ? b.get("action") : "close");
            buttons.add(new Screen.Button(Text.fill(label, vars), Text.fill(tip, vars), action));
        }
        String title = Text.fill(n.getString("title", yml.getString("title", "&6Caylin &8- &7Guild Master")), vars);
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
            TextView.show(p, screen);
            return;
        }
        String mode = plugin.getConfig().getString("dialogue.java-view", "GUI").toUpperCase();
        if (mode.equals("CHAT")) {
            TextView.show(p, screen);
        } else if (mode.equals("DIALOG")) {
            try {
                JavaDialogView.show(plugin, p, screen, handler);
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().warning("Popup Paper gagal, memakai GUI: " + e.getMessage());
                GuiView.show(p, token, screen);
            }
        } else {
            GuiView.show(p, token, screen);
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

    /** Untuk /gm choose N (cadangan teks). */
    public boolean chooseNumber(Player p, int number) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) {
            return false;
        }
        choose(p, s.token, number - 1);
        return true;
    }

    private void closeUi(Player p) {
        sessions.remove(p.getUniqueId());
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof GuiView.Holder) {
            p.closeInventory();
        } else if (plugin.getConfig().getString("dialogue.java-view", "GUI").equalsIgnoreCase("DIALOG")
                && !Platform.isBedrock(p)) {
            p.closeDialog();
        }
    }

    private void run(Player p, Session s, String action) {
        if (action.startsWith("goto:")) {
            s.back.push(s.node);
            show(p, s, action.substring(5));
        } else if (action.equals("back")) {
            String prev = s.back.isEmpty() ? null : s.back.pop();
            if (prev == null) {
                closeUi(p);
            } else {
                show(p, s, prev);
            }
        } else if (action.equals("register")) {
            String key;
            try {
                key = switch (guild.register(p)) {
                    case OK -> "ok";
                    case ALREADY -> "already";
                    case REVOKED -> "revoked";
                    default -> "error";
                };
            } catch (RuntimeException e) {
                plugin.getLogger().severe("Pendaftaran gagal untuk " + p.getName() + ": " + e);
                key = "error";
            }
            if (key.equals("ok")) {
                s.back.clear();
            }
            show(p, s, yml.getString("results." + key, key));
        } else if (action.equals("menu")) {
            closeUi(p);
            Bukkit.getScheduler().runTask(plugin, () -> new Menus.HubMenu(core, p).open());
        } else {
            closeUi(p);
        }
    }

    // ------------------------------------------------------------------ variabel

    private Map<String, String> vars(Player p) {
        Profile pr = core.profile(p);
        Map<String, String> v = new HashMap<>();
        Defs.Rank r = core.activeRank(pr);
        v.put("player", p.getName());
        v.put("npc", npcName());
        v.put("rank", core.label(r, p));
        v.put("level", String.valueOf(pr.member() ? pr.level : 0));
        v.put("quests", String.valueOf(pr.totalQuests));
        v.put("status", guild.statusLabel(pr.status));
        v.put("registered_date", pr.status == GuildStatus.UNREGISTERED ? "-" : guild.date(pr.registeredAt));
        Defs.Rank next = pr.member() ? core.rank(pr.rank + 1) : null;
        v.put("rank_next", next == null ? "-" : core.label(next, p));
        return v;
    }

    // ------------------------------------------------------------------ listener inventori

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof GuiView.Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory() || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        Integer idx = h.slots.get(e.getSlot());
        if (idx != null) {
            choose(p, h.token, idx);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof GuiView.Holder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof GuiView.Holder h && e.getPlayer() instanceof Player p) {
            Session s = sessions.get(p.getUniqueId());
            if (s != null && s.token == h.token) {
                sessions.remove(p.getUniqueId());
            }
        }
    }
}
