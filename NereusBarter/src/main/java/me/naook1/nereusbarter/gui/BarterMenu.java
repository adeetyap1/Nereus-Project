package me.naook1.nereusbarter.gui;

import java.util.ArrayList;
import java.util.List;
import me.naook1.nereusbarter.BarterManager;
import me.naook1.nereusbarter.BarterSession;
import me.naook1.nereusbarter.NereusBarter;
import me.naook1.nereusbarter.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** One player's view of a barter: my offer on the left, theirs on the right (read only). */
public final class BarterMenu implements InventoryHolder {

    public static final int RESET_SLOT = 36;
    public static final int[] STEP_SLOTS = {37, 38, 39};
    public static final int MONEY_MINE = 40;
    public static final int MONEY_THEIRS = 41;
    public static final int INFO_SLOT = 43;
    public static final int CANCEL_SLOT = 45;
    public static final int STATUS_YOU = 47;
    public static final int ACTION_SLOT = 49;
    public static final int STATUS_THEM = 51;
    public static final int BACK_SLOT = 53;

    private final NereusBarter plugin;
    private final BarterSession session;
    private final int side;
    private final Inventory inventory;

    public BarterMenu(NereusBarter plugin, BarterSession session, int side) {
        this.plugin = plugin;
        this.session = session;
        this.side = side;
        String partner = session.names[BarterSession.other(side)];
        this.inventory = Bukkit.createInventory(this, 54, Text.parse(plugin.messages().format("gui.title", "partner", partner)));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public BarterSession session() {
        return session;
    }

    public int side() {
        return side;
    }

    public static int mineSlot(int i) {
        return (i / 4) * 9 + (i % 4);
    }

    public static int theirsSlot(int i) {
        return (i / 4) * 9 + 5 + (i % 4);
    }

    /** Index in the offer (0..15) for a slot of my area, or -1. */
    public static int offerIndex(int slot) {
        if (slot < 0 || slot >= 36) {
            return -1;
        }
        int col = slot % 9;
        return col <= 3 ? (slot / 9) * 4 + col : -1;
    }

    public void render() {
        var m = plugin.messages();
        BarterManager bm = plugin.barter();
        int other = BarterSession.other(side);
        String partner = session.names[other];
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, m.format("gui.filler"));
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }
        ItemStack divider = item(Material.GRAY_STAINED_GLASS_PANE, m.format("gui.divider"));
        for (int r = 0; r < 4; r++) {
            inventory.setItem(r * 9 + 4, divider);
        }
        for (int i = 0; i < BarterSession.OFFER_SLOTS; i++) {
            inventory.setItem(mineSlot(i), session.offer[side][i] == null ? null : session.offer[side][i].clone());
            inventory.setItem(theirsSlot(i), session.offer[other][i] == null ? null : session.offer[other][i].clone());
        }

        boolean stage2 = session.confirmStage;
        if (bm.moneyEnabled()) {
            if (!stage2) {
                inventory.setItem(RESET_SLOT, item(Material.RED_STAINED_GLASS_PANE, m.format("gui.money-reset")));
                List<Long> steps = bm.steps();
                Material[] mats = {Material.GOLD_NUGGET, Material.GOLD_INGOT, Material.GOLD_BLOCK};
                for (int i = 0; i < STEP_SLOTS.length && i < steps.size(); i++) {
                    inventory.setItem(STEP_SLOTS[i], item(mats[i],
                            m.format("gui.money-add", "amount", bm.fmt(steps.get(i)))));
                }
            }
            inventory.setItem(MONEY_MINE, item(Material.EMERALD, m.format("gui.money-mine", "money", bm.fmt(session.money[side]))));
            inventory.setItem(MONEY_THEIRS, item(Material.EMERALD, m.format("gui.money-theirs",
                    "partner", partner, "money", bm.fmt(session.money[other]))));
        }

        List<String> info = m.list(stage2 ? "gui.info-confirm" : "gui.info-edit");
        inventory.setItem(INFO_SLOT, item(Material.BOOK, m.format("gui.info-title"), info));
        inventory.setItem(CANCEL_SLOT, item(Material.RED_CONCRETE, m.format("gui.btn-cancel")));
        inventory.setItem(STATUS_YOU, item(Material.PAPER, m.format("gui.status-you", "state", state(side))));
        inventory.setItem(STATUS_THEM, item(Material.PAPER, m.format("gui.status-them", "partner", partner, "state", state(other))));

        if (!stage2) {
            inventory.setItem(ACTION_SLOT, session.ready[side]
                    ? item(Material.YELLOW_CONCRETE, m.format("gui.btn-unready"))
                    : item(Material.LIME_CONCRETE, m.format("gui.btn-ready")));
        } else {
            long left = Math.max(0, session.unlockAt - System.currentTimeMillis());
            if (session.confirmed[side]) {
                inventory.setItem(ACTION_SLOT, item(Material.CYAN_CONCRETE, m.format("gui.btn-confirmed", "partner", partner)));
            } else if (left > 0) {
                inventory.setItem(ACTION_SLOT, item(Material.GRAY_CONCRETE,
                        m.format("gui.btn-confirm-lock", "time", String.valueOf((left + 999) / 1000))));
            } else {
                inventory.setItem(ACTION_SLOT, item(Material.LIME_CONCRETE, m.format("gui.btn-confirm")));
            }
            inventory.setItem(BACK_SLOT, item(Material.ORANGE_CONCRETE, m.format("gui.btn-back")));
        }
    }

    private String state(int s) {
        var m = plugin.messages();
        if (session.confirmStage) {
            return m.raw(session.confirmed[s] ? "gui.state-confirmed" : "gui.state-wait");
        }
        return m.raw(session.ready[s] ? "gui.state-ready" : "gui.state-wait");
    }

    private static ItemStack item(Material mat, String name) {
        return item(mat, name, List.of());
    }

    private static ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.item(name));
        if (!lore.isEmpty()) {
            List<net.kyori.adventure.text.Component> l = new ArrayList<>();
            for (String s : lore) {
                l.add(Text.item(s));
            }
            meta.lore(l);
        }
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }
}
