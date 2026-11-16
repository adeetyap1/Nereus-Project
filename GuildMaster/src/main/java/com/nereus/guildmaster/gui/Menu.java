package com.nereus.guildmaster.gui;

import com.nereus.guildmaster.GuildMaster;
import com.nereus.guildmaster.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Dasar semua menu guild. Semua klik dibatalkan; subclass menangani slot sendiri. */
public abstract class Menu implements InventoryHolder {
    protected final GuildMaster plugin;
    protected final GuiManager gui;
    protected final Player player;
    protected Inventory inv;

    protected Menu(GuildMaster plugin, GuiManager gui, Player player) {
        this.plugin = plugin;
        this.gui = gui;
        this.player = player;
    }

    protected abstract ConfigurationSection cfg();

    protected abstract void render();

    /** slot di inventori atas. */
    protected abstract void click(int slot, ClickType type);

    @Override
    public Inventory getInventory() {
        return inv;
    }

    protected int rows() {
        return Math.max(1, Math.min(6, cfg().getInt("rows", 3)));
    }

    protected String title() {
        return cfg().getString("title", "&8Adventurer Guild");
    }

    public void open() {
        inv = Bukkit.createInventory(this, rows() * 9, Text.c(title()));
        render();
        player.openInventory(inv);
    }

    protected void fill() {
        ItemStack f = GuiItems.filler(cfg());
        if (f == null) {
            return;
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, f);
        }
    }

    protected void set(int slot, ItemStack item) {
        if (slot >= 0 && slot < inv.getSize() && item != null) {
            inv.setItem(slot, item);
        }
    }
}
