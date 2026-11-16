package com.nereus.guildmaster.gui;

import com.nereus.guildmaster.GuildMaster;
import com.nereus.guildmaster.adventurer.Adventurer;
import com.nereus.guildmaster.rpg.RpgAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Membuka menu dan menjaga keamanan klik (semua dibatalkan, validasi registrasi, jeda klik). */
public final class GuiManager implements Listener {
    private final GuildMaster plugin;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public GuiManager(GuildMaster plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player p) {
        open(p, "main");
    }

    /** Buka menu berdasarkan id. Hanya pemain terdaftar. */
    public void open(Player p, String id) {
        if (!plugin.adventurers().isRegistered(p.getUniqueId())) {
            plugin.configs().send(p, "not-registered");
            p.closeInventory();
            return;
        }
        Menu m = switch (id) {
            case "quest-board" -> new QuestBoardMenu(plugin, this, p);
            case "active", "active-quest" -> new ActiveQuestMenu(plugin, this, p);
            case "history", "quest-history" -> new HistoryMenu(plugin, this, p);
            case "main", "profile", "info" -> new StaticMenu(plugin, this, p, id);
            default -> null;
        };
        if (m != null) {
            m.open();
        }
    }

    public void forget(UUID id) {
        lastClick.remove(id);
    }

    /** Placeholder untuk teks menu, semuanya dari Nereus RPG dan catatan registrasi. */
    public Map<String, String> vars(Player p) {
        Map<String, String> v = new HashMap<>();
        v.put("player", p.getName());
        Adventurer a = plugin.adventurers().get(p.getUniqueId());
        v.put("status", a == null ? "-" : a.active() ? "Registered" : "Dicabut");
        v.put("registered", a == null ? "-" : plugin.dialogues().date(a.registeredAt()));
        RpgAdapter.RpgProfile r = plugin.rpg().available() ? plugin.rpg().profile(p) : null;
        v.put("rank", r == null ? "-" : r.rankLabel());
        v.put("level", r == null ? "-" : String.valueOf(r.level()));
        v.put("xp", r == null ? "-" : String.valueOf(r.xp()));
        v.put("xp_needed", r == null ? "-" : String.valueOf(r.xpNeeded()));
        v.put("class", r == null || r.className() == null ? "-" : r.className());
        v.put("quests", r == null ? "0" : String.valueOf(r.totalQuests()));
        v.put("kills", r == null ? "0" : String.valueOf(r.totalKills()));
        v.put("streak", r == null ? "0" : String.valueOf(r.streak()));
        v.put("gp", r == null ? "0" : String.valueOf(r.gp()));
        v.put("today", r == null ? "0" : String.valueOf(r.completedToday()));
        v.put("daily_limit", r == null ? "0" : String.valueOf(r.dailyLimit()));
        v.put("active", r == null ? "0" : String.valueOf(r.activeCount()));
        v.put("active_max", r == null ? "0" : String.valueOf(r.activeMax()));
        v.put("story", r == null || r.storyTitle() == null ? "-" : r.storyTitle());
        return v;
    }

    // ------------------------------------------------------------------ keamanan klik

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()
                || e.getClickedInventory().getType() == InventoryType.PLAYER) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastClick.get(p.getUniqueId());
        if (last != null && now - last < plugin.getConfig().getLong("gui.click-cooldown-ms", 200)) {
            return;
        }
        lastClick.put(p.getUniqueId(), now);
        if (!plugin.adventurers().isRegistered(p.getUniqueId())) {
            p.closeInventory();
            plugin.configs().send(p, "not-registered");
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.getOpenInventory().getTopInventory().getHolder() == menu) {
                menu.click(e.getSlot(), e.getClick());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) {
            e.setCancelled(true);
        }
    }
}
