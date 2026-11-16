package com.nereus.guildmaster.gui;

import com.nereus.guildmaster.GuildMaster;
import com.nereus.guildmaster.Text;
import com.nereus.guildmaster.rpg.RpgAdapter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Quest yang sedang berjalan, dengan progres. Klik kiri serahkan (quest item), shift+kanan batalkan. */
public final class ActiveQuestMenu extends ListMenu<RpgAdapter.ActiveQuest> {
    public ActiveQuestMenu(GuildMaster plugin, GuiManager gui, Player player) {
        super(plugin, gui, player, "active-quest");
    }

    @Override
    protected List<RpgAdapter.ActiveQuest> entries() {
        return plugin.rpg().activeQuests(player);
    }

    private String bar(int progress, int required) {
        int len = Math.max(5, cfg().getInt("bar.length", 10));
        int filled = required <= 0 ? len : (int) Math.round((double) progress / required * len);
        filled = Math.max(0, Math.min(len, filled));
        return cfg().getString("bar.full-color", "&a") + cfg().getString("bar.char", "|").repeat(filled)
                + cfg().getString("bar.empty-color", "&7") + cfg().getString("bar.char", "|").repeat(len - filled);
    }

    @Override
    protected ItemStack entryItem(RpgAdapter.ActiveQuest q) {
        ConfigurationSection e = cfg().getConfigurationSection("entry");
        Map<String, String> v = new HashMap<>(gui.vars(player));
        v.put("name", q.name());
        v.put("objective", q.objective());
        v.put("progress", String.valueOf(q.progress()));
        v.put("required", String.valueOf(q.required()));
        v.put("bar", bar(q.progress(), q.required()));
        String statusKey = q.ready() ? "ready" : q.turnIn() ? "collecting" : "ongoing";
        v.put("status", cfg().getString("status." + statusKey, statusKey));
        List<String> lore = e == null ? List.of("%objective%") : e.getStringList("lore");
        List<String> hint = q.turnIn() ? cfg().getStringList("hint.turn-in") : cfg().getStringList("hint.auto");
        Map<String, List<String>> multi = Map.of("hint", Text.fillAll(hint, v));
        return GuiItems.make(q.icon(), Text.fill(e == null ? "&f%name%" : e.getString("name", "&f%name%"), v),
                GuiItems.lore(lore, v, multi), q.ready(), 1);
    }

    @Override
    protected void entryClick(RpgAdapter.ActiveQuest q, ClickType type) {
        if (type == ClickType.SHIFT_RIGHT) {
            plugin.rpg().abandon(player, q.id());
            refresh();
        } else if (type.isLeftClick() && q.turnIn()) {
            plugin.rpg().turnIn(player, q.id());
            refresh();
        }
    }
}
