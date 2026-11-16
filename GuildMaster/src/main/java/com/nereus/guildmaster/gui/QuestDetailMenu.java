package com.nereus.guildmaster.gui;

import com.nereus.guildmaster.GuildMaster;
import com.nereus.guildmaster.Text;
import com.nereus.guildmaster.rpg.RpgAdapter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.Map;

/** Detail satu quest: syarat, hadiah, tombol ambil. Pengambilan sepenuhnya dilakukan Nereus RPG. */
public final class QuestDetailMenu extends Menu {
    private final String questId;

    public QuestDetailMenu(GuildMaster plugin, GuiManager gui, Player player, String questId) {
        super(plugin, gui, player);
        this.questId = questId;
    }

    @Override
    protected ConfigurationSection cfg() {
        return plugin.configs().gui("quest-board").getConfigurationSection("detail");
    }

    private RpgAdapter.QuestInfo quest() {
        for (RpgAdapter.QuestInfo q : plugin.rpg().quests(player)) {
            if (q.id().equals(questId)) {
                return q;
            }
        }
        return null;
    }

    @Override
    protected void render() {
        inv.clear();
        fill();
        RpgAdapter.QuestInfo q = quest();
        if (q == null) {
            gui.open(player, "quest-board");
            return;
        }
        Map<String, String> v = QuestBoardMenu.vars(plugin, gui, player, q);
        ConfigurationSection info = cfg().getConfigurationSection("info");
        if (info != null) {
            set(info.getInt("slot", 13), GuiItems.make(q.icon(), Text.fill(info.getString("name", "%name%"), v),
                    GuiItems.lore(info.getStringList("lore"), v, Map.of("rewards", q.rewards())), false, 1));
        }
        ConfigurationSection accept = cfg().getConfigurationSection(q.state() == RpgAdapter.State.AVAILABLE ? "accept" : "unavailable");
        if (accept != null) {
            set(accept.getInt("slot", 11), GuiItems.build(accept, v, player));
        }
        ConfigurationSection back = cfg().getConfigurationSection("back");
        if (back != null) {
            set(back.getInt("slot", 15), GuiItems.build(back, v, player));
        }
    }

    @Override
    protected void click(int slot, ClickType type) {
        RpgAdapter.QuestInfo q = quest();
        if (q == null) {
            return;
        }
        if (slot == cfg().getInt("back.slot", 15)) {
            gui.open(player, "quest-board");
        } else if (slot == cfg().getInt("accept.slot", 11) && q.state() == RpgAdapter.State.AVAILABLE) {
            plugin.rpg().accept(player, q.id());
            render();
        }
    }

    @SuppressWarnings("unused")
    private static Material unused() {
        return Material.AIR;
    }
}
