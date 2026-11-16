package com.nereus.guildmaster.gui;

import com.nereus.guildmaster.GuildMaster;
import com.nereus.guildmaster.rpg.RpgAdapter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Papan quest: daftar quest dari Nereus RPG. Klik membuka detail. */
public final class QuestBoardMenu extends ListMenu<RpgAdapter.QuestInfo> {
    public QuestBoardMenu(GuildMaster plugin, GuiManager gui, Player player) {
        super(plugin, gui, player, "quest-board");
    }

    @Override
    protected List<RpgAdapter.QuestInfo> entries() {
        return plugin.rpg().quests(player);
    }

    static String stateText(GuildMaster plugin, RpgAdapter.State s) {
        String key = switch (s) {
            case AVAILABLE -> "available";
            case ACTIVE -> "active";
            case DONE_TODAY -> "done";
        };
        return plugin.configs().gui("quest-board").getString("states." + key, key);
    }

    static Map<String, String> vars(GuildMaster plugin, GuiManager gui, Player p, RpgAdapter.QuestInfo q) {
        Map<String, String> v = new HashMap<>(gui.vars(p));
        v.put("name", q.name());
        v.put("rank", q.rankLabel());
        v.put("objective", q.objective());
        v.put("state", stateText(plugin, q.state()));
        v.put("type", q.challenge() ? plugin.configs().gui("quest-board").getString("challenge-label", "&c(Tantangan)") : "");
        return v;
    }

    @Override
    protected ItemStack entryItem(RpgAdapter.QuestInfo q) {
        ConfigurationSection e = cfg().getConfigurationSection("entry");
        Map<String, String> v = vars(plugin, gui, player, q);
        ItemStack item = GuiItems.make(q.icon(),
                com.nereus.guildmaster.Text.fill(e == null ? "%rank% &f%name%" : e.getString("name", "%rank% &f%name%"), v),
                GuiItems.lore(e == null ? List.of("%objective%", "%rewards%") : e.getStringList("lore"), v,
                        Map.of("rewards", q.rewards())),
                q.state() == RpgAdapter.State.ACTIVE, 1);
        return item;
    }

    @Override
    protected void entryClick(RpgAdapter.QuestInfo q, ClickType type) {
        new QuestDetailMenu(plugin, gui, player, q.id()).open();
    }
}
