package me.naook1.nereusbank;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import me.naook1.nereusbank.util.Fmt;
import me.naook1.nereusbank.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * All Nereus Bank screens. Java players get the textured panel (font glyph in the title + custom item icons),
 * Bedrock players get the same layout as a normal chest with the custom icons.
 */
public final class BankGui {

    public enum Mode { DEPOSIT, WITHDRAW, TRANSFER, FIXED, BORROW, REPAY }

    public static final class Holder implements InventoryHolder {
        public Inventory inv;
        public boolean bedrock;
        public final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
        public Mode mode;
        public long amount;
        public UUID target;
        public int period;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    // custom_model_data of the PAPER icons (see resource pack)
    static final int M_ACCOUNT = 8018, M_DEPOSIT = 8010, M_WITHDRAW = 8011, M_TRANSFER = 8012, M_HISTORY = 8013,
            M_SAVINGS = 8014, M_LOAN = 8015, M_CARDS = 8016, M_TOP = 8017, M_CLOSE = 8019, M_BACK = 8020,
            M_ADD = 8030, M_SUB = 8036, M_ALL = 8042, M_HALF = 8043, M_RESET = 8044, M_CONFIRM = 8045,
            M_CANCEL = 8046, M_AMOUNT = 8047, M_TYPE = 8048, M_PREV = 8050, M_NEXT = 8051, M_TX_IN = 8052,
            M_TX_OUT = 8053, M_TX_INTEREST = 8054, M_TX_LOAN = 8055, M_TX_FD = 8056, M_TX_FEE = 8057,
            M_TX_CARD = 8058, M_P7 = 8060, M_P14 = 8061, M_P30 = 8062, M_FD = 8063, M_BORROW = 8070,
            M_REPAY = 8071, M_LOANINFO = 8072, M_REPLACE = 8080;

    private static final long[] STEPS = {100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000};
    private static final String MAIN_BG = "";
    private static final String PLAIN_BG = "";

    private final NereusBank plugin;

    public BankGui(NereusBank plugin) {
        this.plugin = plugin;
    }

    // ---------------- plumbing ----------------

    public boolean isBedrock(Player p) {
        try {
            Class<?> c = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = c.getMethod("getInstance").invoke(null);
            return (boolean) c.getMethod("isFloodgatePlayer", UUID.class).invoke(api, p.getUniqueId());
        } catch (Throwable t) {
            return false;
        }
    }

    private Holder open(Player p, boolean main, String bedrockTitle) {
        Holder h = new Holder();
        h.bedrock = isBedrock(p);
        Component title = h.bedrock ? Text.parse(bedrockTitle)
                : Component.text(main ? MAIN_BG : PLAIN_BG).color(NamedTextColor.WHITE);
        h.inv = Bukkit.createInventory(h, 54, title);
        p.openInventory(h.inv);
        return h;
    }

    private void finish(Holder h) {
        if (!h.bedrock) {
            return;
        }
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta();
        m.displayName(Component.text(" "));
        pane.setItemMeta(m);
        for (int i = 0; i < 54; i++) {
            if (h.inv.getItem(i) == null) {
                h.inv.setItem(i, pane);
            }
        }
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private static ItemStack icon(int model, String name, String... lore) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.item(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) {
                l.add(Text.item(s));
            }
            m.lore(l);
        }
        m.setCustomModelData(model);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(m);
        return it;
    }

    private static void put(Holder h, int slot, ItemStack it, Consumer<ClickType> act, int... extra) {
        h.inv.setItem(slot, it);
        if (act != null) {
            h.actions.put(slot, act);
            for (int e : extra) {
                h.actions.put(e, act);
            }
        }
    }

    private void backAndClose(Player p, Holder h) {
        put(h, 45, icon(M_BACK, "&e&lBack", "&7Kembali ke menu utama."), c -> later(() -> openMain(p)));
        put(h, 49, icon(M_CLOSE, "&c&lClose"), c -> p.closeInventory());
    }

    private void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
    }

    private static String clock(long t) {
        return new SimpleDateFormat("dd MMM HH:mm").format(new Date(t));
    }

    // ---------------- main ----------------

    public void openMain(Player p) {
        Account a = plugin.bank().account(p);
        Tier t = plugin.bank().tier(a);
        Holder h = open(p, true, "&b&lNereus Bank");
        long wallet = plugin.economy().wallet(p);
        List<String> lore = new ArrayList<>();
        lore.add("&7Pemegang: &f" + a.name);
        lore.add("&7Nomor: &f" + a.cardNo);
        lore.add("&7Tier: " + t.label());
        lore.add("");
        lore.add("&7Saldo bank: &a" + Fmt.money(a.balance));
        lore.add("&7Dompet: &e" + Fmt.money(wallet));
        if (a.lockedInDeposits() > 0) {
            lore.add("&7Deposito: &b" + Fmt.money(a.lockedInDeposits()));
        }
        if (a.loan > 0) {
            lore.add("&7Pinjaman: &c" + Fmt.money(a.loan) + (a.overdue() ? " &4(TERLAMBAT)" : ""));
        }
        lore.add("");
        long left = plugin.bank().nextInterestAt(a) - System.currentTimeMillis();
        lore.add("&7Bunga berikutnya: &a+" + Fmt.money(plugin.bank().interestPreview(a)) + " &8(" + Fmt.duration(left) + ")");
        put(h, 4, icon(t.model() > 0 ? t.model() : M_ACCOUNT, "&b&lNereus Bank &8| " + t.label(),
                lore.toArray(new String[0])), null);

        put(h, 10, icon(M_DEPOSIT, "&a&lDeposit", "&7Simpan uang dompet ke bank.", "",
                "&7Dompet: &e" + Fmt.money(wallet)), c -> later(() -> openPicker(p, Mode.DEPOSIT, null, 0, 0)), 19);
        put(h, 12, icon(M_WITHDRAW, "&e&lWithdraw", "&7Tarik uang bank ke dompet.", "",
                "&7Saldo: &a" + Fmt.money(a.balance)), c -> later(() -> openPicker(p, Mode.WITHDRAW, null, 0, 0)), 21);
        put(h, 14, icon(M_TRANSFER, "&b&lTransfer", "&7Kirim uang ke pemain lain.", "",
                "&7Biaya: &e" + Fmt.pct(t.transferFee()),
                "&7Sisa batas hari ini: &e" + (t.dailyLimit() < 0 ? "Tanpa batas" : Fmt.money(plugin.bank().dailyRemaining(a)))),
                c -> later(() -> openTargets(p, 0)), 23);
        put(h, 16, icon(M_HISTORY, "&f&lStatement", "&7Riwayat transaksi terakhir."),
                c -> later(() -> openHistory(p, 0)), 25);
        put(h, 28, icon(M_SAVINGS, "&d&lSavings", "&7Deposito berjangka, bunga lebih tinggi.", "",
                "&7Terkunci: &b" + Fmt.money(a.lockedInDeposits())), c -> later(() -> openSavings(p)), 37);
        put(h, 30, icon(M_LOAN, "&c&lLoan", "&7Pinjam uang, bayar dalam " + plugin.getConfig().getInt("loan.due-days", 30) + " hari.", "",
                "&7Batas: &e" + Fmt.money(t.loanLimit()), "&7Bunga: &e" + Fmt.pct(t.loanRate())),
                c -> later(() -> openLoan(p)), 39);
        put(h, 32, icon(M_CARDS, "&6&lCards", "&7Silver, Gold dan Platinum.", "",
                "&7Tier kamu: " + t.label()), c -> later(() -> openCards(p)), 41);
        put(h, 34, icon(M_TOP, "&e&lRich List", "&7Pemain terkaya di server."),
                c -> later(() -> openTop(p)), 43);
        put(h, 49, icon(M_CLOSE, "&c&lClose"), c -> p.closeInventory());
        finish(h);
    }

    // ---------------- amount picker ----------------

    public void openPicker(Player p, Mode mode, UUID target, int period, long preset) {
        Holder h = open(p, false, "&b&lNereus Bank");
        h.mode = mode;
        h.target = target;
        h.period = period;
        h.amount = preset;
        renderPicker(p, h);
    }

    private long maxFor(Player p, Account a, Holder h) {
        Tier t = plugin.bank().tier(a);
        switch (h.mode) {
            case DEPOSIT:
                return Math.max(0, Math.min(plugin.economy().wallet(p), plugin.bank().depositRoom(a)));
            case WITHDRAW:
                return a.balance;
            case TRANSFER: {
                double f = 1 + t.transferFee() / 100.0;
                long byBalance = (long) Math.floor(a.balance / f);
                return Math.max(0, Math.min(byBalance, plugin.bank().dailyRemaining(a)));
            }
            case FIXED:
                return a.balance;
            case BORROW:
                return Math.max(0, Math.min(t.loanLimit() - a.loan, plugin.bank().depositRoom(a)));
            case REPAY:
                return Math.min(a.loan, a.balance);
            default:
                return 0;
        }
    }

    private List<String> pickerInfo(Player p, Account a, Holder h) {
        Tier t = plugin.bank().tier(a);
        List<String> l = new ArrayList<>();
        long amt = h.amount;
        switch (h.mode) {
            case DEPOSIT:
                l.add("&7Dompet: &e" + Fmt.money(plugin.economy().wallet(p)));
                l.add("&7Saldo bank: &a" + Fmt.money(a.balance) + " &8-> &a" + Fmt.money(a.balance + amt));
                break;
            case WITHDRAW:
                l.add("&7Saldo bank: &a" + Fmt.money(a.balance) + " &8-> &a" + Fmt.money(a.balance - amt));
                break;
            case TRANSFER: {
                Account to = plugin.bank().get(h.target);
                long fee = plugin.bank().feeFor(a, amt);
                l.add("&7Penerima: &f" + (to == null ? "?" : to.name));
                l.add("&7Biaya (" + Fmt.pct(t.transferFee()) + "): &e" + Fmt.money(fee));
                l.add("&7Total dipotong: &c" + Fmt.money(amt + fee));
                l.add("&7Sisa batas hari ini: &e" + (t.dailyLimit() < 0 ? "Tanpa batas" : Fmt.money(plugin.bank().dailyRemaining(a))));
                break;
            }
            case FIXED: {
                double rate = plugin.bank().depositRate(a, h.period);
                long earn = Math.round(amt * rate / 100.0);
                l.add("&7Jangka: &f" + h.period + " hari");
                l.add("&7Bunga: &a" + Fmt.pct(rate) + " &8(tier " + t.label() + "&8)");
                l.add("&7Cair: &a" + Fmt.money(amt + earn) + " &8(+" + Fmt.money(earn) + ")");
                break;
            }
            case BORROW: {
                long interest = Math.round(amt * t.loanRate() / 100.0);
                l.add("&7Bunga: &e" + Fmt.pct(t.loanRate()) + " &8= " + Fmt.money(interest));
                l.add("&7Total tagihan: &c" + Fmt.money(amt + interest));
                l.add("&7Jatuh tempo: &f" + plugin.getConfig().getInt("loan.due-days", 30) + " hari");
                break;
            }
            case REPAY:
                l.add("&7Tagihan: &c" + Fmt.money(a.loan) + " &8-> &a" + Fmt.money(a.loan - amt));
                break;
            default:
                break;
        }
        return l;
    }

    private String modeTitle(Mode m) {
        return switch (m) {
            case DEPOSIT -> "&a&lDeposit";
            case WITHDRAW -> "&e&lWithdraw";
            case TRANSFER -> "&b&lTransfer";
            case FIXED -> "&d&lSavings";
            case BORROW -> "&c&lBorrow";
            case REPAY -> "&a&lRepay";
        };
    }

    private int modeIcon(Mode m) {
        return switch (m) {
            case DEPOSIT -> M_DEPOSIT;
            case WITHDRAW -> M_WITHDRAW;
            case TRANSFER -> M_TRANSFER;
            case FIXED -> M_SAVINGS;
            case BORROW, REPAY -> M_LOAN;
        };
    }

    private void renderPicker(Player p, Holder h) {
        Account a = plugin.bank().account(p);
        h.inv.clear();
        h.actions.clear();
        long max = maxFor(p, a, h);
        h.amount = Math.max(0, Math.min(h.amount, max));

        int[] addSlots = {10, 11, 12, 14, 15, 16};
        int[] subSlots = {28, 29, 30, 32, 33, 34};
        for (int i = 0; i < STEPS.length; i++) {
            final long step = STEPS[i];
            put(h, addSlots[i], icon(M_ADD + i, "&a&l+" + Fmt.compact(step), "&7Tambah " + Fmt.money(step)), c -> {
                h.amount = Math.min(max, h.amount + step);
                click(p);
                renderPicker(p, h);
            });
            put(h, subSlots[i], icon(M_SUB + i, "&c&l-" + Fmt.compact(step), "&7Kurangi " + Fmt.money(step)), c -> {
                h.amount = Math.max(0, h.amount - step);
                click(p);
                renderPicker(p, h);
            });
        }
        List<String> info = new ArrayList<>(pickerInfo(p, a, h));
        info.add("");
        info.add("&7Maksimal: &f" + Fmt.money(max));
        put(h, 4, icon(modeIcon(h.mode), modeTitle(h.mode), info.toArray(new String[0])), null);
        put(h, 22, icon(M_AMOUNT, "&e&l" + Fmt.money(h.amount), "&7Klik untuk mengetik jumlah di chat.",
                "&8Contoh: 2500, 15k, 1.5m"), c -> {
            p.closeInventory();
            plugin.ask(p, "prompt-amount", input -> {
                long v = Fmt.parse(input);
                if (v < 0) {
                    plugin.messages().send(p, "invalid-amount");
                    openPicker(p, h.mode, h.target, h.period, h.amount);
                    return;
                }
                openPicker(p, h.mode, h.target, h.period, v);
            });
        });
        put(h, 19, icon(M_HALF, "&b&lHalf", "&7Setengah dari maksimal."), c -> {
            h.amount = max / 2;
            click(p);
            renderPicker(p, h);
        });
        put(h, 20, icon(M_ALL, "&b&lAll", "&7Jumlah maksimal."), c -> {
            h.amount = max;
            click(p);
            renderPicker(p, h);
        });
        put(h, 24, icon(M_RESET, "&7&lReset", "&7Kembali ke 0."), c -> {
            h.amount = 0;
            click(p);
            renderPicker(p, h);
        });
        put(h, 25, icon(M_TYPE, "&f&lType", "&7Ketik jumlah di chat."), c -> h.actions.get(22).accept(c));
        put(h, 41, icon(M_CONFIRM, "&a&lConfirm", "&7Jalankan: &f" + Fmt.money(h.amount)), c -> confirmPicker(p, h));
        put(h, 39, icon(M_CANCEL, "&c&lCancel", "&7Batal dan kembali."), c -> later(() -> openMain(p)));
        finish(h);
    }

    private void confirmPicker(Player p, Holder h) {
        Account a = plugin.bank().account(p);
        BankManager bank = plugin.bank();
        BankManager.Result r;
        long amt = h.amount;
        var m = plugin.messages();
        Runnable next = () -> openMain(p);
        switch (h.mode) {
            case DEPOSIT:
                r = bank.deposit(p, amt);
                if (r.success()) {
                    m.send(p, "deposit-done", "amount", Fmt.money(amt), "balance", Fmt.money(a.balance));
                }
                break;
            case WITHDRAW:
                r = bank.withdraw(p, amt);
                if (r.success()) {
                    m.send(p, "withdraw-done", "amount", Fmt.money(amt), "balance", Fmt.money(a.balance));
                }
                break;
            case TRANSFER: {
                Account to = bank.get(h.target);
                if (to == null) {
                    r = BankManager.Result.fail("no-account");
                    break;
                }
                long fee = bank.feeFor(a, amt);
                r = bank.transfer(p, to, amt);
                if (r.success()) {
                    m.send(p, "transfer-done", "amount", Fmt.money(amt), "player", to.name, "fee", Fmt.money(fee));
                }
                break;
            }
            case FIXED:
                r = bank.openDeposit(a, amt, h.period);
                if (r.success()) {
                    m.send(p, "fd-opened", "amount", Fmt.money(amt), "days", String.valueOf(h.period),
                            "rate", Fmt.pct(bank.depositRate(a, h.period)));
                    next = () -> openSavings(p);
                }
                break;
            case BORROW:
                r = bank.borrow(a, amt);
                if (r.success()) {
                    m.send(p, "loan-borrowed", "amount", Fmt.money(amt), "owed", Fmt.money(a.loan));
                    next = () -> openLoan(p);
                }
                break;
            case REPAY: {
                long owed = a.loan;
                r = bank.repay(a, amt);
                if (r.success()) {
                    m.send(p, "loan-repaid", "amount", Fmt.money(Math.min(amt, owed)), "left", Fmt.money(a.loan));
                    next = () -> openLoan(p);
                }
                break;
            }
            default:
                r = BankManager.Result.fail("invalid-amount");
        }
        if (!r.success()) {
            m.send(p, r.key(), r.kv());
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
        later(next);
    }

    // ---------------- transfer targets ----------------

    public void openTargets(Player p, int page) {
        Holder h = open(p, false, "&b&lNereus Bank");
        List<Player> list = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.getUniqueId().equals(p.getUniqueId())) {
                list.add(o);
            }
        }
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int pages = Math.max(1, (list.size() + slots.length - 1) / slots.length);
        int pg = Math.max(0, Math.min(page, pages - 1));
        put(h, 4, icon(M_TYPE, "&b&lKetik nama", "&7Transfer ke pemain offline.", "&7(harus sudah punya akun bank)"), c -> {
            p.closeInventory();
            plugin.ask(p, "prompt-player", input -> {
                Player online = Bukkit.getPlayerExact(input.trim());
                Account to = online != null ? plugin.bank().account(online) : plugin.bank().findByName(input.trim());
                if (to == null) {
                    plugin.messages().send(p, "no-account", "player", input.trim());
                    return;
                }
                openPicker(p, Mode.TRANSFER, to.id, 0, 0);
            });
        });
        for (int i = 0; i < slots.length; i++) {
            int idx = pg * slots.length + i;
            if (idx >= list.size()) {
                break;
            }
            Player o = list.get(idx);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(o);
            sm.displayName(Text.item("&f&l" + o.getName()));
            sm.lore(List.of(Text.item("&7Klik untuk transfer.")));
            head.setItemMeta(sm);
            put(h, slots[i], head, c -> later(() -> {
                Account to = plugin.bank().account(o);
                openPicker(p, Mode.TRANSFER, to.id, 0, 0);
            }));
        }
        if (pg > 0) {
            put(h, 48, icon(M_PREV, "&e&lPrev"), c -> later(() -> openTargets(p, pg - 1)));
        }
        if (pg < pages - 1) {
            put(h, 50, icon(M_NEXT, "&e&lNext"), c -> later(() -> openTargets(p, pg + 1)));
        }
        backAndClose(p, h);
        finish(h);
    }

    // ---------------- history ----------------

    public void openHistory(Player p, int page) {
        Account a = plugin.bank().account(p);
        Holder h = open(p, false, "&b&lNereus Bank");
        int per = 36;
        int pages = Math.max(1, (a.txs.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < per; i++) {
            int idx = pg * per + i;
            if (idx >= a.txs.size()) {
                break;
            }
            Account.Tx t = a.txs.get(idx);
            int model;
            String color;
            String label;
            switch (t.type()) {
                case "DEPOSIT" -> { model = M_TX_IN; color = "&a"; label = "Deposit"; }
                case "WITHDRAW" -> { model = M_TX_OUT; color = "&e"; label = "Withdraw"; }
                case "TRANSFER_IN" -> { model = M_TX_IN; color = "&a"; label = "Transfer masuk"; }
                case "TRANSFER_OUT" -> { model = M_TX_OUT; color = "&c"; label = "Transfer keluar"; }
                case "INTEREST" -> { model = M_TX_INTEREST; color = "&b"; label = "Bunga bank"; }
                case "LOAN" -> { model = M_TX_LOAN; color = "&c"; label = "Pinjaman"; }
                case "REPAY" -> { model = M_TX_LOAN; color = "&a"; label = "Bayar pinjaman"; }
                case "FD_OPEN" -> { model = M_TX_FD; color = "&d"; label = "Buka deposito"; }
                case "FD_PAYOUT" -> { model = M_TX_FD; color = "&a"; label = "Deposito cair"; }
                case "FD_BREAK" -> { model = M_TX_FD; color = "&e"; label = "Deposito dicairkan awal"; }
                case "CARD" -> { model = M_TX_CARD; color = "&6"; label = "Kartu"; }
                default -> { model = M_TX_FEE; color = "&7"; label = t.type().equals("FEE") ? "Biaya" : "Admin"; }
            }
            List<String> lore = new ArrayList<>();
            lore.add("&7Jumlah: " + color + Fmt.money(t.amount()));
            if (!t.note().isBlank()) {
                lore.add("&7Catatan: &f" + t.note());
            }
            lore.add("&7Saldo setelahnya: &f" + Fmt.money(t.after()));
            lore.add("&8" + clock(t.time()));
            put(h, 9 + i, icon(model, color + "&l" + label, lore.toArray(new String[0])), null);
        }
        put(h, 4, icon(M_HISTORY, "&f&lStatement", "&7Halaman " + (pg + 1) + "/" + pages,
                "&7Total transaksi: &f" + a.txs.size()), null);
        if (pg > 0) {
            put(h, 48, icon(M_PREV, "&e&lPrev"), c -> later(() -> openHistory(p, pg - 1)));
        }
        if (pg < pages - 1) {
            put(h, 50, icon(M_NEXT, "&e&lNext"), c -> later(() -> openHistory(p, pg + 1)));
        }
        backAndClose(p, h);
        finish(h);
    }

    // ---------------- savings ----------------

    public void openSavings(Player p) {
        Account a = plugin.bank().account(p);
        Holder h = open(p, false, "&b&lNereus Bank");
        put(h, 4, icon(M_SAVINGS, "&d&lSavings", "&7Kunci uang, dapat bunga lebih besar.", "",
                "&7Terkunci: &b" + Fmt.money(a.lockedInDeposits()),
                "&7Bonus tier: &a+" + Fmt.pct(plugin.bank().tier(a).depositBonus()),
                "&7Min. setoran: &e" + Fmt.money(plugin.getConfig().getLong("fixed-deposit.min-amount", 1000))), null);
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < Math.min(slots.length, a.deposits.size()); i++) {
            Account.Deposit d = a.deposits.get(i);
            long earn = Math.round(d.amount() * d.rate() / 100.0);
            final int index = i;
            put(h, slots[i], icon(M_FD, "&d&lDeposito " + Fmt.compact(d.amount()),
                    "&7Pokok: &f" + Fmt.money(d.amount()),
                    "&7Bunga: &a" + Fmt.pct(d.rate()) + " &8(+" + Fmt.money(earn) + ")",
                    "&7Cair dalam: &f" + Fmt.duration(d.end() - System.currentTimeMillis()),
                    "",
                    "&cKlik untuk mencairkan lebih awal",
                    "&c(bunga hangus + denda " + Fmt.pct(plugin.getConfig().getDouble("fixed-deposit.early-break-penalty", 1)) + ")"),
                    c -> later(() -> confirm(p, icon(M_FD, "&c&lCairkan lebih awal?",
                            "&7Pokok: &f" + Fmt.money(d.amount()), "&7Bunga hangus."), () -> {
                                BankManager.Result r = plugin.bank().breakDeposit(a, index);
                                if (r.success()) {
                                    plugin.messages().send(p, "fd-broken");
                                } else {
                                    plugin.messages().send(p, r.key(), r.kv());
                                }
                                openSavings(p);
                            }, () -> openSavings(p))));
        }
        int[] pslots = {29, 31, 33};
        int pi = 0;
        for (var e : plugin.bank().periods().entrySet()) {
            if (pi >= pslots.length) {
                break;
            }
            int days = e.getKey();
            double rate = plugin.bank().depositRate(a, days);
            int model = days == 7 ? M_P7 : days == 14 ? M_P14 : days == 30 ? M_P30 : M_FD;
            put(h, pslots[pi++], icon(model, "&d&l" + days + " hari",
                    "&7Bunga: &a" + Fmt.pct(rate), "",
                    "&eKlik untuk membuka deposito."),
                    c -> later(() -> openPicker(p, Mode.FIXED, null, days, 0)));
        }
        backAndClose(p, h);
        finish(h);
    }

    // ---------------- loan ----------------

    public void openLoan(Player p) {
        Account a = plugin.bank().account(p);
        Tier t = plugin.bank().tier(a);
        Holder h = open(p, false, "&b&lNereus Bank");
        List<String> l = new ArrayList<>();
        l.add("&7Batas pinjaman: &e" + Fmt.money(t.loanLimit()));
        l.add("&7Bunga: &e" + Fmt.pct(t.loanRate()));
        l.add("&7Tagihan sekarang: &c" + Fmt.money(a.loan));
        if (a.loan > 0) {
            if (a.overdue()) {
                l.add("&4TERLAMBAT! Denda harian berjalan.");
            } else {
                l.add("&7Jatuh tempo: &f" + Fmt.duration(a.loanDue - System.currentTimeMillis()));
            }
        }
        l.add("&7Sisa bisa dipinjam: &a" + Fmt.money(Math.max(0, t.loanLimit() - a.loan)));
        put(h, 13, icon(M_LOANINFO, "&c&lLoan", l.toArray(new String[0])), null);
        put(h, 29, icon(M_BORROW, "&a&lBorrow", "&7Pinjam uang ke saldo bank.",
                "&7Bunga &e" + Fmt.pct(t.loanRate()) + " &7langsung ditambahkan,",
                "&7tapi &adibebaskan &7jika lunas dalam &f" + plugin.getConfig().getInt("loan.grace-minutes", 60) + " menit&7."),
                c -> later(() -> openPicker(p, Mode.BORROW, null, 0, 0)));
        if (a.loan > 0) {
            put(h, 33, icon(M_REPAY, "&e&lRepay", "&7Bayar tagihan dari saldo bank."),
                    c -> later(() -> openPicker(p, Mode.REPAY, null, 0, 0)));
        } else {
            put(h, 33, icon(M_REPAY, "&8&lRepay", "&7Kamu tidak punya pinjaman."), null);
        }
        backAndClose(p, h);
        finish(h);
    }

    // ---------------- cards ----------------

    public void openCards(Player p) {
        BankManager bank = plugin.bank();
        Account a = bank.account(p);
        Tier cur = bank.tier(a);
        Holder h = open(p, false, "&b&lNereus Bank");
        put(h, 4, icon(cur.model() > 0 ? cur.model() : M_ACCOUNT, "&6&lCards &8| " + cur.label(),
                "&7Nomor: &f" + a.cardNo, "&7Beli kartu untuk naik tier."), null);
        int[] slots = {11, 13, 15, 29, 31, 33};
        int i = 0;
        for (Tier t : bank.tiers().values()) {
            if (t.order() == 0 || i >= slots.length) {
                continue;
            }
            List<String> l = new ArrayList<>();
            l.add("&7Bunga: &a" + Fmt.pct(t.interest()) + " &8/ " + bank.interestDays() + " hari &8(maks " + Fmt.money(t.interestCap()) + ")");
            l.add("&7Biaya transfer: &e" + Fmt.pct(t.transferFee()));
            l.add("&7Batas transfer harian: &e" + (t.dailyLimit() < 0 ? "Tanpa batas" : Fmt.money(t.dailyLimit())));
            l.add("&7Saldo maksimal: &e" + (t.maxBalance() < 0 ? "Tanpa batas" : Fmt.money(t.maxBalance())));
            l.add("&7Pinjaman: &e" + Fmt.money(t.loanLimit()) + " &8@ " + Fmt.pct(t.loanRate()));
            l.add("&7Bonus deposito: &a+" + Fmt.pct(t.depositBonus()));
            l.add("");
            l.add("&7Harga: &e" + Fmt.money(t.cardPrice()));
            if (t.cardMinBalance() > 0) {
                l.add("&7Syarat saldo: &e" + Fmt.money(t.cardMinBalance()));
            }
            l.add("");
            if (t.order() == cur.order()) {
                l.add("&a&lKartu aktif");
            } else if (t.order() < cur.order()) {
                l.add("&8Kamu sudah punya tier lebih tinggi.");
            } else {
                l.add("&eKlik untuk membeli.");
            }
            final Tier target = t;
            put(h, slots[i++], icon(t.model() > 0 ? t.model() : M_ACCOUNT, "&b&lNereus " + t.label() + " &b&lCard",
                    l.toArray(new String[0])), c -> {
                if (target.order() <= cur.order()) {
                    return;
                }
                later(() -> confirm(p, icon(target.model() > 0 ? target.model() : M_ACCOUNT, "&6&lBeli " + target.label() + "?",
                        "&7Harga: &e" + Fmt.money(target.cardPrice()),
                        "&7Dibayar dari saldo bank, lalu dompet."), () -> {
                            BankManager.Result r = plugin.cards().buy(p, target);
                            if (r.success()) {
                                plugin.messages().send(p, "card-bought", "tier", target.label());
                                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                                openMain(p);
                            } else {
                                plugin.messages().send(p, r.key(), r.kv());
                                openCards(p);
                            }
                        }, () -> openCards(p)));
            });
        }
        long fee = plugin.getConfig().getLong("card.replace-fee", 5000);
        put(h, 38, icon(M_REPLACE, "&e&lReplace Card", "&7Cetak ulang kartu yang hilang.", "&7Biaya: &e" + Fmt.money(fee)),
                c -> later(() -> confirm(p, icon(M_REPLACE, "&e&lCetak ulang kartu?", "&7Biaya: &e" + Fmt.money(fee)), () -> {
                    BankManager.Result r = plugin.cards().replace(p);
                    if (r.success()) {
                        plugin.messages().send(p, "card-replaced");
                    } else {
                        plugin.messages().send(p, r.key(), r.kv());
                    }
                    openCards(p);
                }, () -> openCards(p))));
        backAndClose(p, h);
        finish(h);
    }

    // ---------------- rich list ----------------

    public void openTop(Player p) {
        Holder h = open(p, false, "&b&lNereus Bank");
        put(h, 4, icon(M_TOP, "&e&lRich List", "&7Total saldo + deposito."), null);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
        List<Account> top = plugin.bank().top(slots.length);
        for (int i = 0; i < top.size(); i++) {
            Account a = top.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            OfflinePlayer op = Bukkit.getOfflinePlayer(a.id);
            sm.setOwningPlayer(op);
            sm.displayName(Text.item("&e&l#" + (i + 1) + " &f" + a.name));
            sm.lore(List.of(Text.item("&7Kekayaan: &a" + Fmt.money(a.balance + a.lockedInDeposits())),
                    Text.item("&7Tier: " + plugin.bank().tier(a).label())));
            head.setItemMeta(sm);
            put(h, slots[i], head, null);
        }
        backAndClose(p, h);
        finish(h);
    }

    // ---------------- confirm ----------------

    public void confirm(Player p, ItemStack info, Runnable yes, Runnable no) {
        Holder h = open(p, false, "&b&lNereus Bank");
        put(h, 13, info, null);
        put(h, 29, icon(M_CONFIRM, "&a&lYes", "&7Lanjutkan."), c -> later(yes));
        put(h, 33, icon(M_CANCEL, "&c&lNo", "&7Batal."), c -> later(no));
        finish(h);
    }
}
