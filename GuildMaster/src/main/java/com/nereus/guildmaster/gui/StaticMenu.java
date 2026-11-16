package com.nereus.guildmaster.gui;

import com.nereus.guildmaster.GuildMaster;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Menu tetap yang seluruh tampilannya dari gui/&lt;id&gt;.yml (utama, profil, informasi). */
public final class StaticMenu extends Menu {
    private final String id;
    private final Map<Integer, String> actions = new HashMap<>();

    public StaticMenu(GuildMaster plugin, GuiManager gui, Player player, String id) {
        super(plugin, gui, player);
        this.id = id;
    }

    @Override
    protected ConfigurationSection cfg() {
        return plugin.configs().gui(id);
    }

    @Override
    protected void render() {
        inv.clear();
        actions.clear();
        fill();
        ConfigurationSection items = cfg().getConfigurationSection("items");
        if (items == null) {
            return;
        }
        var vars = gui.vars(player);
        Set<String> keys = items.getKeys(false);
        for (String k : keys) {
            ConfigurationSection s = items.getConfigurationSection(k);
            if (s == null) {
                continue;
            }
            int slot = s.getInt("slot", -1);
            set(slot, GuiItems.build(s, vars, player));
            String action = s.getString("action");
            if (action != null && slot >= 0) {
                actions.put(slot, action);
            }
        }
    }

    @Override
    protected void click(int slot, ClickType type) {
        String a = actions.get(slot);
        if (a == null) {
            return;
        }
        if (a.startsWith("open:")) {
            gui.open(player, a.substring(5));
        } else if (a.equals("back")) {
            gui.openMain(player);
        } else if (a.equals("close")) {
            player.closeInventory();
        }
    }
}
