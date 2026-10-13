package me.naook1.nereuslottery.gui;

import java.util.ArrayList;
import java.util.List;
import me.naook1.nereuslottery.LotteryManager;
import me.naook1.nereuslottery.NereusLottery;
import me.naook1.nereuslottery.util.TimeUtil;
import me.naook1.nereuslottery.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** 3-row lottery menu: jackpot, buy buttons, own tickets and recent winners. Refreshed every second. */
public final class LotteryMenu implements InventoryHolder {

    public static final int POT_SLOT = 4;
    public static final int MINE_SLOT = 16;
    public static final int HISTORY_SLOT = 22;
    public static final int CLOSE_SLOT = 26;
    private static final int[] BUY_SLOTS = {10, 12, 14};

    private final NereusLottery plugin;
    private final Player player;
    private final Inventory inventory;

    public LotteryMenu(NereusLottery plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 27, Text.parse(plugin.messages().format("menu.title")));
        refresh();
    }

    public void open() {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Amount of tickets the button in this slot buys, or -1 if the slot is not a buy button. */
    public int amountAt(int slot) {
        List<Integer> amounts = plugin.lottery().buyAmounts();
        for (int i = 0; i < BUY_SLOTS.length && i < amounts.size(); i++) {
            if (BUY_SLOTS[i] == slot) {
                return amounts.get(i);
            }
        }
        return -1;
    }

    public void refresh() {
        var m = plugin.messages();
        LotteryManager l = plugin.lottery();
        var id = player.getUniqueId();
        long nextMs = 0;
        String symbol = l.symbol();

        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, 1, m.format("menu.border-name"), List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, border);
        }

        String[] common = {
                "symbol", symbol,
                "pot", l.fmt(l.pot()),
                "price", l.fmt(l.price()),
                "players", String.valueOf(l.participants()),
                "tickets", String.valueOf(l.totalTickets()),
                "winners", String.valueOf(l.winnerCount()),
                "mine", String.valueOf(l.ticketsOf(id)),
                "max", String.valueOf(l.maxTickets()),
                "chance", l.chance(id),
                "next", l.nextClock(),
                "time", l.nextText(),
                "balance", l.fmt((long) plugin.economy().balance(player))
        };

        inventory.setItem(POT_SLOT, item(Material.GOLD_BLOCK, 1,
                m.format("menu.pot.name", common), m.list("menu.pot.lore", common)));

        List<Integer> amounts = l.buyAmounts();
        for (int i = 0; i < BUY_SLOTS.length && i < amounts.size(); i++) {
            int n = amounts.get(i);
            String[] kv = concat(common, "amount", String.valueOf(n), "cost", l.fmt(l.price() * n));
            inventory.setItem(BUY_SLOTS[i], item(Material.PAPER, Math.min(64, n),
                    m.format("menu.buy.name", kv), m.list("menu.buy.lore", kv)));
        }

        inventory.setItem(MINE_SLOT, item(Material.NAME_TAG, 1,
                m.format("menu.mine.name", common), m.list("menu.mine.lore", common)));

        List<String> lore = new ArrayList<>();
        long now = System.currentTimeMillis();
        int shown = 0;
        for (LotteryManager.Win w : l.history()) {
            if (shown++ >= 5) {
                break;
            }
            lore.add(m.format("menu.history.line", "player", w.player(), "prize", l.fmt(w.prize()),
                    "symbol", symbol) + " &8(" + TimeUtil.ago(now - w.time()) + ")");
        }
        if (lore.isEmpty()) {
            lore.add(m.format("menu.history.empty"));
        }
        inventory.setItem(HISTORY_SLOT, item(Material.BOOK, 1, m.format("menu.history.name"), lore));

        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, 1, m.format("menu.close.name"), List.of()));
    }

    private static String[] concat(String[] a, String... b) {
        String[] out = new String[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static ItemStack item(Material material, int amount, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.item(name));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Text.item(line));
        }
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }
}
