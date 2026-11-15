package id.nereusmc.totemstack;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Builds and handles the dark/gold admin menus. Every click is validated against the viewer's permissions. */
public final class GUIManager {

    public static final String PERM_GUI = "nereustotem.gui";
    public static final String PERM_ADMIN = "nereustotem.admin";

    public enum MenuType { MAIN, TOTEM, STACK, ACTIVATION, EFFECTS, STATS, CONFIRM_RESET }

    /** Marks an inventory as one of our menus so listeners can recognise it. */
    public static final class Holder implements InventoryHolder {
        private final MenuType type;
        private Inventory inventory;

        private Holder(MenuType type) {
            this.type = type;
        }

        public MenuType type() {
            return type;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final String[] EFFECT_NAMES = {"Regeneration", "Absorption", "Fire Resistance"};
    private static final Material[] EFFECT_ICONS = {Material.GHAST_TEAR, Material.GOLDEN_APPLE, Material.MAGMA_CREAM};
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Main plugin;

    public GUIManager(Main plugin) {
        this.plugin = plugin;
    }

    private ConfigManager cfg() {
        return plugin.config();
    }

    // ---------------------------------------------------------------- open

    public void open(Player player, MenuType type) {
        if (!player.hasPermission(PERM_GUI)) {
            player.sendMessage(cfg().message("no-permission"));
            return;
        }
        if (!cfg().isGuiEnabled()) {
            player.sendMessage(cfg().message("gui-disabled"));
            return;
        }
        Holder holder = new Holder(type);
        Inventory inventory = Bukkit.createInventory(holder, size(type), title(type));
        holder.inventory = inventory;
        populate(inventory, player, type);
        player.openInventory(inventory);
    }

    private int size(MenuType type) {
        return switch (type) {
            case MAIN, STATS -> 45;
            case EFFECTS -> 36;
            default -> 27;
        };
    }

    private Component title(MenuType type) {
        String base = cfg().getGuiTitle();
        String suffix = switch (type) {
            case MAIN -> "";
            case TOTEM -> " &7» &6Totem";
            case STACK -> " &7» &6Stacking";
            case ACTIVATION -> " &7» &6Activation";
            case EFFECTS -> " &7» &6Effects";
            case STATS -> " &7» &6Statistics";
            case CONFIRM_RESET -> " &7» &cReset";
        };
        return cfg().color(base + suffix);
    }

    // ------------------------------------------------------------- render

    private void populate(Inventory inv, Player viewer, MenuType type) {
        inv.clear();
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
        switch (type) {
            case MAIN -> renderMain(inv);
            case TOTEM -> renderTotem(inv);
            case STACK -> renderStack(inv);
            case ACTIVATION -> renderActivation(inv);
            case EFFECTS -> renderEffects(inv);
            case STATS -> renderStats(inv, viewer);
            case CONFIRM_RESET -> renderConfirm(inv);
        }
    }

    private void renderMain(Inventory inv) {
        inv.setItem(10, item(Material.TOTEM_OF_UNDYING, "&6Totem Settings", "&7General plugin switch and reset."));
        inv.setItem(12, item(Material.CHEST, "&6Stack Settings", "&7Custom stacking and stack limit.",
                "&7Current limit: &e" + stackLimitText()));
        inv.setItem(14, item(Material.CLOCK, "&6Activation Settings", "&7Where totems are searched",
                "&7when a fatal hit happens."));
        inv.setItem(16, item(Material.POTION, "&6Effect Settings", "&7Regeneration, absorption and",
                "&7fire resistance after activation."));
        inv.setItem(29, item(Material.BOOK, "&6Statistics", "&7Activations and top players."));
        inv.setItem(31, item(Material.REDSTONE, "&6Reload Configuration", "&7Reload config.yml from disk."));
        inv.setItem(33, item(Material.BARRIER, "&cClose Menu", "&7Close this menu."));
    }

    private void renderTotem(Inventory inv) {
        inv.setItem(11, toggle(cfg().isPluginEnabled(), "Plugin", "&7Master switch for stacking and",
                "&7automatic activation."));
        inv.setItem(13, item(Material.TNT, "&cReset to Defaults", "&7Restore the default config.yml.",
                "&8Asks for confirmation."));
        inv.setItem(22, back());
    }

    private void renderStack(Inventory inv) {
        inv.setItem(10, toggle(cfg().isStackingEnabled(), "Custom Stacking", "&7Let totems stack above 1."));
        inv.setItem(11, item(Material.BOOK, "&6Current Limit", "&7Max stack size: &e" + stackLimitText()));
        for (int i = 0; i < ConfigManager.STACK_CHOICES.length; i++) {
            int size = ConfigManager.STACK_CHOICES[i];
            boolean selected = cfg().getMaxStackSize() == size;
            ItemStack icon = item(selected ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE,
                    (selected ? "&a" : "&f") + size,
                    selected ? "&aSelected" : "&7Click to set max stack to &e" + size);
            icon.setAmount(Math.max(1, Math.min(64, size)));
            inv.setItem(12 + i, icon);
        }
        inv.setItem(22, back());
    }

    private void renderActivation(Inventory inv) {
        inv.setItem(10, toggle(cfg().isInventoryActivation(), "Inventory Activation",
                "&7Search totems outside the hands."));
        inv.setItem(11, toggle(cfg().isHotbarEnabled(), "Use Hotbar", "&7Allow totems from the hotbar."));
        inv.setItem(12, toggle(cfg().isMainInventoryEnabled(), "Use Main Inventory",
                "&7Allow totems from the main inventory."));
        boolean hotbarFirst = cfg().isHotbarFirst();
        inv.setItem(14, item(Material.HOPPER, "&6Search Order",
                "&7Hands first (vanilla), then:",
                hotbarFirst ? "&e1. Hotbar &72. Main inventory" : "&e1. Main inventory &72. Hotbar",
                "&8Click to swap"));
        inv.setItem(22, back());
    }

    private void renderEffects(Inventory inv) {
        for (int row = 0; row < ConfigManager.EFFECTS.size(); row++) {
            String key = ConfigManager.EFFECTS.get(row);
            int base = row * 9;
            boolean enabled = cfg().isEffectEnabled(key);
            int duration = cfg().getEffectDuration(key);
            int amplifier = cfg().getEffectAmplifier(key);

            ItemStack icon = item(EFFECT_ICONS[row], (enabled ? "&a" : "&c") + EFFECT_NAMES[row],
                    "&7Status: " + (enabled ? "&aON" : "&cOFF"), "&8Click to toggle");
            inv.setItem(base + 1, icon);
            inv.setItem(base + 2, item(Material.RED_STAINED_GLASS_PANE, "&c- Duration",
                    "&7Left: -5s  Shift: -30s"));
            inv.setItem(base + 3, item(Material.CLOCK, "&6Duration", "&e" + (duration / 20) + "s &8(" + duration + " ticks)"));
            inv.setItem(base + 4, item(Material.LIME_STAINED_GLASS_PANE, "&a+ Duration",
                    "&7Left: +5s  Shift: +30s"));
            inv.setItem(base + 5, item(Material.RED_STAINED_GLASS_PANE, "&c- Level", "&7Lower the amplifier"));
            inv.setItem(base + 6, item(Material.EXPERIENCE_BOTTLE, "&6Level", "&e" + (amplifier + 1)));
            inv.setItem(base + 7, item(Material.LIME_STAINED_GLASS_PANE, "&a+ Level", "&7Raise the amplifier"));
        }
        inv.setItem(31, back());
    }

    private void renderStats(Inventory inv, Player viewer) {
        StatisticsManager stats = plugin.statistics();
        inv.setItem(10, item(Material.TOTEM_OF_UNDYING, "&6Total Activations", "&e" + stats.totalActivations()));
        inv.setItem(12, item(Material.PAPER, "&6Totems Consumed", "&e" + stats.totalConsumed()));
        StatisticsManager.Snapshot own = stats.get(viewer.getUniqueId());
        if (own == null) {
            inv.setItem(14, item(Material.CLOCK, "&6Your Activations", "&7No activations yet."));
        } else {
            inv.setItem(14, item(Material.CLOCK, "&6Your Activations",
                    "&7Activations: &e" + own.activations(),
                    "&7Consumed: &e" + own.consumed(),
                    "&7Last: &e" + DATE_FORMAT.format(Instant.ofEpochMilli(own.lastActivation()))));
        }
        inv.setItem(16, item(Material.WRITABLE_BOOK, "&6Tracked Players", "&e" + stats.trackedPlayers()));

        List<StatisticsManager.Snapshot> top = stats.top(5);
        for (int i = 0; i < top.size(); i++) {
            StatisticsManager.Snapshot s = top.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            if (meta instanceof SkullMeta skull) {
                skull.setOwningPlayer(Bukkit.getOfflinePlayer(s.uuid()));
            }
            String name = s.name() == null ? s.uuid().toString().substring(0, 8) : s.name();
            meta.displayName(cfg().itemText("&6#" + (i + 1) + " &f" + name));
            List<Component> lore = new ArrayList<>();
            lore.add(cfg().itemText("&7Activations: &e" + s.activations()));
            lore.add(cfg().itemText("&7Consumed: &e" + s.consumed()));
            meta.lore(lore);
            hideFlags(meta);
            head.setItemMeta(meta);
            inv.setItem(28 + i, head);
        }
        if (top.isEmpty()) {
            inv.setItem(30, item(Material.GRAY_DYE, "&7No data yet", "&7Nobody has used a totem."));
        }
        inv.setItem(40, back());
    }

    private void renderConfirm(Inventory inv) {
        inv.setItem(11, item(Material.LIME_CONCRETE, "&aConfirm Reset",
                "&7Overwrite config.yml with defaults.", "&cThis cannot be undone."));
        inv.setItem(15, item(Material.RED_CONCRETE, "&cCancel", "&7Keep the current settings."));
    }

    // -------------------------------------------------------------- items

    private String stackLimitText() {
        return cfg().isStackingEnabled() ? String.valueOf(cfg().getMaxStackSize()) : "1 (stacking off)";
    }

    private ItemStack back() {
        return item(Material.ARROW, "&6Back", "&7Return to the main menu.");
    }

    private ItemStack toggle(boolean on, String label, String... lore) {
        List<String> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(line);
        }
        lines.add("&7Status: " + (on ? "&aON" : "&cOFF"));
        lines.add("&8Click to toggle");
        return item(on ? Material.LIME_DYE : Material.GRAY_DYE, (on ? "&a" : "&c") + label,
                lines.toArray(new String[0]));
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(cfg().itemText(name));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(cfg().itemText(line));
            }
            meta.lore(lines);
        }
        hideFlags(meta);
        stack.setItemMeta(meta);
        return stack;
    }

    private void hideFlags(ItemMeta meta) {
        for (ItemFlag flag : ItemFlag.values()) {
            meta.addItemFlags(flag);
        }
    }

    // -------------------------------------------------------------- clicks

    /** Called by GUIListener for a click inside the menu's top inventory (already cancelled). */
    public void handleClick(Player player, Holder holder, int slot, ClickType click) {
        if (!player.hasPermission(PERM_GUI)) {
            player.sendMessage(cfg().message("no-permission"));
            later(player::closeInventory);
            return;
        }
        switch (holder.type()) {
            case MAIN -> clickMain(player, holder, slot);
            case TOTEM -> clickTotem(player, holder, slot);
            case STACK -> clickStack(player, holder, slot);
            case ACTIVATION -> clickActivation(player, holder, slot);
            case EFFECTS -> clickEffects(player, holder, slot, click);
            case STATS -> {
                if (slot == 40) {
                    openLater(player, MenuType.MAIN);
                }
            }
            case CONFIRM_RESET -> clickConfirm(player, slot);
        }
    }

    private void clickMain(Player player, Holder holder, int slot) {
        switch (slot) {
            case 10 -> openLater(player, MenuType.TOTEM);
            case 12 -> openLater(player, MenuType.STACK);
            case 14 -> openLater(player, MenuType.ACTIVATION);
            case 16 -> openLater(player, MenuType.EFFECTS);
            case 29 -> openLater(player, MenuType.STATS);
            case 31 -> {
                if (requireAdmin(player)) {
                    plugin.reloadAll();
                    player.sendMessage(cfg().message("reloaded"));
                    refresh(player, holder);
                }
            }
            case 33 -> later(player::closeInventory);
            default -> {
            }
        }
    }

    private void clickTotem(Player player, Holder holder, int slot) {
        if (slot == 22) {
            openLater(player, MenuType.MAIN);
        } else if (slot == 11 && requireAdmin(player)) {
            cfg().setPluginEnabled(!cfg().isPluginEnabled());
            changed(player, holder, true);
        } else if (slot == 13 && requireAdmin(player)) {
            openLater(player, MenuType.CONFIRM_RESET);
        }
    }

    private void clickStack(Player player, Holder holder, int slot) {
        if (slot == 22) {
            openLater(player, MenuType.MAIN);
        } else if (slot == 10 && requireAdmin(player)) {
            cfg().setStackingEnabled(!cfg().isStackingEnabled());
            changed(player, holder, true);
        } else if (slot >= 12 && slot < 12 + ConfigManager.STACK_CHOICES.length && requireAdmin(player)) {
            if (cfg().setMaxStackSize(ConfigManager.STACK_CHOICES[slot - 12])) {
                changed(player, holder, true);
            }
        }
    }

    private void clickActivation(Player player, Holder holder, int slot) {
        if (slot == 22) {
            openLater(player, MenuType.MAIN);
            return;
        }
        if (slot != 10 && slot != 11 && slot != 12 && slot != 14) {
            return;
        }
        if (!requireAdmin(player)) {
            return;
        }
        switch (slot) {
            case 10 -> cfg().setInventoryActivation(!cfg().isInventoryActivation());
            case 11 -> cfg().setHotbarEnabled(!cfg().isHotbarEnabled());
            case 12 -> cfg().setMainInventoryEnabled(!cfg().isMainInventoryEnabled());
            default -> cfg().setHotbarFirst(!cfg().isHotbarFirst());
        }
        changed(player, holder, false);
    }

    private void clickEffects(Player player, Holder holder, int slot, ClickType click) {
        if (slot == 31) {
            openLater(player, MenuType.MAIN);
            return;
        }
        int row = slot / 9;
        int col = slot % 9;
        if (row >= ConfigManager.EFFECTS.size() || col < 1 || col > 7) {
            return;
        }
        if (col == 3 || col == 6 || !requireAdmin(player)) {
            return;
        }
        String key = ConfigManager.EFFECTS.get(row);
        int step = click.isShiftClick() ? 600 : 100;
        switch (col) {
            case 1 -> cfg().setEffectEnabled(key, !cfg().isEffectEnabled(key));
            case 2 -> cfg().setEffectDuration(key, cfg().getEffectDuration(key) - step);
            case 4 -> cfg().setEffectDuration(key, cfg().getEffectDuration(key) + step);
            case 5 -> cfg().setEffectAmplifier(key, cfg().getEffectAmplifier(key) - 1);
            case 7 -> cfg().setEffectAmplifier(key, cfg().getEffectAmplifier(key) + 1);
            default -> {
                return;
            }
        }
        changed(player, holder, false);
    }

    private void clickConfirm(Player player, int slot) {
        if (slot == 11 && requireAdmin(player)) {
            cfg().resetToDefaults();
            plugin.stacks().normalizeOnline();
            player.sendMessage(cfg().message("reset-done"));
            openLater(player, MenuType.TOTEM);
        } else if (slot == 15) {
            openLater(player, MenuType.TOTEM);
        }
    }

    // ------------------------------------------------------------- helpers

    private boolean requireAdmin(Player player) {
        if (player.hasPermission(PERM_ADMIN)) {
            return true;
        }
        player.sendMessage(cfg().message("no-permission"));
        return false;
    }

    /** Persists the change, re-applies stacking when needed and redraws the open menu. */
    private void changed(Player player, Holder holder, boolean restack) {
        cfg().save();
        if (restack) {
            plugin.stacks().normalizeOnline();
        }
        refresh(player, holder);
    }

    private void refresh(Player player, Holder holder) {
        if (holder.getInventory() != null) {
            populate(holder.getInventory(), player, holder.type());
        }
    }

    private void openLater(Player player, MenuType type) {
        later(() -> open(player, type));
    }

    private void later(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
