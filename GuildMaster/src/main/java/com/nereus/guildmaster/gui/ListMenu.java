package com.nereus.guildmaster.gui;

import com.nereus.guildmaster.GuildMaster;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Menu daftar berhalaman. Baris atas untuk entri, baris bawah untuk navigasi (dari yml). */
public abstract class ListMenu<T> extends Menu {
    private final String id;
    private final List<T> shown = new ArrayList<>();
    private int page;

    protected ListMenu(GuildMaster plugin, GuiManager gui, Player player, String id) {
        super(plugin, gui, player);
        this.id = id;
    }

    @Override
    protected ConfigurationSection cfg() {
        return plugin.configs().gui(id);
    }

    protected abstract List<T> entries();

    protected abstract ItemStack entryItem(T entry);

    protected abstract void entryClick(T entry, ClickType type);

    protected int pageSize() {
        return rows() * 9 - 9;
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void render() {
        inv.clear();
        fill();
        List<T> all = entries();
        int size = pageSize();
        int pages = Math.max(1, (all.size() + size - 1) / size);
        page = Math.max(0, Math.min(page, pages - 1));
        shown.clear();
        for (int i = 0; i < size; i++) {
            int idx = page * size + i;
            if (idx >= all.size()) {
                break;
            }
            T e = all.get(idx);
            shown.add(e);
            inv.setItem(i, entryItem(e));
        }
        Map<String, String> vars = new java.util.HashMap<>(gui.vars(player));
        vars.put("page", String.valueOf(page + 1));
        vars.put("pages", String.valueOf(pages));
        vars.put("count", String.valueOf(all.size()));
        nav("prev", page > 0, vars);
        nav("next", page < pages - 1, vars);
        nav("back", true, vars);
        nav("info", true, vars);
        if (all.isEmpty()) {
            ConfigurationSection e = cfg().getConfigurationSection("empty");
            if (e != null) {
                set(e.getInt("slot", 22), GuiItems.build(e, vars, player));
            }
        }
    }

    private void nav(String key, boolean visible, Map<String, String> vars) {
        ConfigurationSection s = cfg().getConfigurationSection("nav." + key);
        if (s != null && visible) {
            set(s.getInt("slot", 45), GuiItems.build(s, vars, player));
        }
    }

    @Override
    protected void click(int slot, ClickType type) {
        if (slot < pageSize()) {
            if (slot < shown.size()) {
                entryClick(shown.get(slot), type);
            }
            return;
        }
        if (slot == cfg().getInt("nav.prev.slot", 45) && page > 0) {
            page--;
            render();
        } else if (slot == cfg().getInt("nav.next.slot", 53)) {
            page++;
            render();
        } else if (slot == cfg().getInt("nav.back.slot", 49)) {
            gui.openMain(player);
        }
    }

    protected void refresh() {
        render();
    }
}
