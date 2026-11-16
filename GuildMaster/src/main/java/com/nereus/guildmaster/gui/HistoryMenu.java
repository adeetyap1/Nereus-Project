package com.nereus.guildmaster.gui;

import com.nereus.guildmaster.GuildMaster;
import com.nereus.guildmaster.Text;
import com.nereus.guildmaster.rpg.RpgAdapter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Riwayat quest dan misi yang pernah diselesaikan (dicatat oleh Nereus RPG). */
public final class HistoryMenu extends ListMenu<RpgAdapter.HistoryEntry> {
    public HistoryMenu(GuildMaster plugin, GuiManager gui, Player player) {
        super(plugin, gui, player, "quest-history");
    }

    @Override
    protected List<RpgAdapter.HistoryEntry> entries() {
        return plugin.rpg().history(player);
    }

    @Override
    protected ItemStack entryItem(RpgAdapter.HistoryEntry h) {
        ConfigurationSection kind = cfg().getConfigurationSection("kinds." + h.kind());
        Material mat = GuiItems.mat(kind == null ? null : kind.getString("material"), Material.PAPER);
        Map<String, String> v = new HashMap<>(gui.vars(player));
        v.put("name", h.name());
        v.put("date", plugin.dialogues().date(h.time()));
        v.put("kind", kind == null ? h.kind() : kind.getString("label", h.kind()));
        ConfigurationSection e = cfg().getConfigurationSection("entry");
        return GuiItems.make(mat, Text.fill(e == null ? "&f%name%" : e.getString("name", "&f%name%"), v),
                GuiItems.lore(e == null ? List.of("%date%") : e.getStringList("lore"), v, Map.of()), false, 1);
    }

    @Override
    protected void entryClick(RpgAdapter.HistoryEntry entry, ClickType type) {
        // hanya tampilan
    }
}
