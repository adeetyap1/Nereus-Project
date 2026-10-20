package me.naook1.nereusbank;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import me.naook1.nereusbank.util.Fmt;
import me.naook1.nereusbank.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Accounts, money movement, interest, fixed deposits and loans. */
public final class BankManager {

    /** Outcome of an operation: key null = success, otherwise a messages.yml key. */
    public record Result(String key, String[] kv) {
        public static Result ok() {
            return new Result(null, new String[0]);
        }

        public static Result fail(String key, String... kv) {
            return new Result(key, kv);
        }

        public boolean success() {
            return key == null;
        }
    }

    private static final long DAY = 86_400_000L;

    private final NereusBank plugin;
    private final File file;
    private final Map<UUID, Account> accounts = new LinkedHashMap<>();
    private final Map<String, Tier> tiers = new LinkedHashMap<>();
    private final TreeMap<Integer, Double> periods = new TreeMap<>();
    private boolean dirty;

    public BankManager(NereusBank plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "accounts.yml");
    }

    // ---------------- config ----------------

    public void reloadConfig() {
        tiers.clear();
        periods.clear();
        var cfg = plugin.getConfig();
        ConfigurationSection ts = cfg.getConfigurationSection("tiers");
        int order = 0;
        if (ts != null) {
            for (String id : ts.getKeys(false)) {
                ConfigurationSection s = ts.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                String up = id.toUpperCase(Locale.ROOT);
                tiers.put(up, new Tier(up, order++, s.getString("name", id), s.getString("color", "&f"),
                        s.getDouble("interest", 0), s.getLong("interest-cap", -1), s.getDouble("transfer-fee", 0),
                        s.getLong("daily-limit", -1), s.getLong("max-balance", -1), s.getLong("loan-limit", 0),
                        s.getDouble("loan-rate", 0), s.getDouble("deposit-bonus", 0), s.getLong("card-price", 0),
                        s.getLong("card-min-balance", 0), s.getInt("model", 0)));
            }
        }
        if (tiers.isEmpty()) {
            tiers.put("STANDARD", new Tier("STANDARD", 0, "Standard", "&7", 0.5, 50000, 5, 100000, 1000000,
                    20000, 10, 0, 0, 0, 0));
        }
        ConfigurationSection ps = cfg.getConfigurationSection("fixed-deposit.periods");
        if (ps != null) {
            for (String k : ps.getKeys(false)) {
                try {
                    periods.put(Integer.parseInt(k), ps.getDouble(k));
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        }
    }

    public Map<String, Tier> tiers() {
        return tiers;
    }

    public Tier tier(String id) {
        Tier t = id == null ? null : tiers.get(id.toUpperCase(Locale.ROOT));
        return t != null ? t : tiers.values().iterator().next();
    }

    public Tier tier(Account a) {
        return tier(a.tier);
    }

    public Tier baseTier() {
        return tiers.values().iterator().next();
    }

    public TreeMap<Integer, Double> periods() {
        return periods;
    }

    public int interestDays() {
        return Math.max(1, plugin.getConfig().getInt("interest-days", 30));
    }

    // ---------------- accounts ----------------

    public Account get(UUID id) {
        return accounts.get(id);
    }

    public Account account(OfflinePlayer p) {
        Account a = accounts.get(p.getUniqueId());
        if (a == null) {
            a = new Account(p.getUniqueId(), p.getName() == null ? "?" : p.getName());
            a.tier = baseTier().id();
            a.cardNo = newCardNumber();
            accounts.put(a.id, a);
            dirty = true;
        } else if (p.getName() != null && !p.getName().equals(a.name)) {
            a.name = p.getName();
            dirty = true;
        }
        return a;
    }

    public Account findByName(String name) {
        for (Account a : accounts.values()) {
            if (a.name.equalsIgnoreCase(name)) {
                return a;
            }
        }
        return null;
    }

    public List<Account> all() {
        return new ArrayList<>(accounts.values());
    }

    public List<Account> top(int n) {
        List<Account> list = new ArrayList<>(accounts.values());
        list.sort(Comparator.comparingLong((Account a) -> a.balance + a.lockedInDeposits()).reversed());
        return list.size() > n ? list.subList(0, n) : list;
    }

    private String newCardNumber() {
        java.util.Random r = new java.util.Random();
        String s;
        do {
            s = String.format("NRB-%04d-%04d", r.nextInt(10000), r.nextInt(10000));
        } while (cardTaken(s));
        return s;
    }

    private boolean cardTaken(String s) {
        for (Account a : accounts.values()) {
            if (s.equals(a.cardNo)) {
                return true;
            }
        }
        return false;
    }

    public String freshCardNumber(Account a) {
        a.cardNo = newCardNumber();
        dirty = true;
        return a.cardNo;
    }

    public void markDirty() {
        dirty = true;
    }

    // ---------------- helpers ----------------

    private void log(Account a, String type, long amount, String note) {
        a.txs.add(0, new Account.Tx(System.currentTimeMillis(), type, amount, note == null ? "" : note, a.balance));
        while (a.txs.size() > 60) {
            a.txs.remove(a.txs.size() - 1);
        }
        dirty = true;
    }

    /** Tells the player now if online, otherwise stores the message for their next login. */
    public void notify(Account a, String key, String... kv) {
        String msg = plugin.messages().format(key, kv);
        Player p = Bukkit.getPlayer(a.id);
        if (p != null) {
            p.sendMessage(Text.parse(msg));
            return;
        }
        a.notices.add(msg);
        while (a.notices.size() > 12) {
            a.notices.remove(0);
        }
        dirty = true;
    }

    public void deliverNotices(Player p) {
        Account a = accounts.get(p.getUniqueId());
        if (a == null || a.notices.isEmpty()) {
            return;
        }
        for (String s : a.notices) {
            p.sendMessage(Text.parse(s));
        }
        a.notices.clear();
        dirty = true;
    }

    public long dailyRemaining(Account a) {
        Tier t = tier(a);
        return t.dailyLimit() < 0 ? Long.MAX_VALUE : Math.max(0, t.dailyLimit() - a.dailyUsed());
    }

    public long feeFor(Account a, long amount) {
        Tier t = tier(a);
        return Math.round(amount * t.transferFee() / 100.0);
    }

    public long depositRoom(Account a) {
        Tier t = tier(a);
        if (t.maxBalance() < 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, t.maxBalance() - a.balance - a.lockedInDeposits());
    }

    public long nextInterestAt(Account a) {
        return a.lastInterest + interestDays() * DAY;
    }

    public long interestPreview(Account a) {
        Tier t = tier(a);
        long raw = Math.round(a.balance * t.interest() / 100.0);
        return t.interestCap() < 0 ? raw : Math.min(raw, t.interestCap());
    }

    public double depositRate(Account a, int days) {
        Double base = periods.get(days);
        return base == null ? 0 : base + tier(a).depositBonus();
    }

    // ---------------- operations ----------------

    public Result deposit(Player p, long amount) {
        Account a = account(p);
        if (amount <= 0) {
            return Result.fail("invalid-amount");
        }
        if (!plugin.economy().ready()) {
            return Result.fail("no-economy");
        }
        if (plugin.economy().wallet(p) < amount) {
            return Result.fail("not-enough-wallet");
        }
        if (amount > depositRoom(a)) {
            return Result.fail("balance-limit", "limit", Fmt.money(tier(a).maxBalance()));
        }
        if (!plugin.economy().take(p, amount)) {
            return Result.fail("not-enough-wallet");
        }
        a.balance += amount;
        log(a, "DEPOSIT", amount, "");
        return Result.ok();
    }

    public Result withdraw(Player p, long amount) {
        Account a = account(p);
        if (amount <= 0) {
            return Result.fail("invalid-amount");
        }
        if (a.balance < amount) {
            return Result.fail("not-enough-bank");
        }
        if (!plugin.economy().ready() || !plugin.economy().give(p, amount)) {
            return Result.fail("no-economy");
        }
        a.balance -= amount;
        log(a, "WITHDRAW", amount, "");
        return Result.ok();
    }

    public Result transfer(Player from, Account to, long amount) {
        Account a = account(from);
        long min = plugin.getConfig().getLong("transfer.min-amount", 100);
        if (to.id.equals(a.id)) {
            return Result.fail("transfer-self");
        }
        if (amount < min) {
            return Result.fail("transfer-min", "min", Fmt.money(min));
        }
        long fee = feeFor(a, amount);
        if (a.balance < amount + fee) {
            return Result.fail("not-enough-bank");
        }
        if (amount > dailyRemaining(a)) {
            return Result.fail("daily-limit", "left", Fmt.money(dailyRemaining(a)));
        }
        Tier rt = tier(to);
        if (rt.maxBalance() >= 0 && to.balance + to.lockedInDeposits() + amount > rt.maxBalance()) {
            return Result.fail("receiver-full", "player", to.name);
        }
        a.balance -= amount + fee;
        a.addDaily(amount);
        to.balance += amount;
        log(a, "TRANSFER_OUT", amount, to.name);
        if (fee > 0) {
            log(a, "FEE", fee, "transfer");
        }
        log(to, "TRANSFER_IN", amount, a.name);
        notify(to, "transfer-received", "player", a.name, "amount", Fmt.money(amount));
        return Result.ok();
    }

    public Result openDeposit(Account a, long amount, int days) {
        long min = plugin.getConfig().getLong("fixed-deposit.min-amount", 1000);
        if (!periods.containsKey(days)) {
            return Result.fail("invalid-amount");
        }
        if (amount < min) {
            return Result.fail("fd-min", "min", Fmt.money(min));
        }
        if (a.balance < amount) {
            return Result.fail("not-enough-bank");
        }
        double rate = depositRate(a, days);
        long now = System.currentTimeMillis();
        a.balance -= amount;
        a.deposits.add(new Account.Deposit(amount, now, now + days * DAY, rate));
        log(a, "FD_OPEN", amount, days + "d");
        return Result.ok();
    }

    public Result breakDeposit(Account a, int index) {
        if (index < 0 || index >= a.deposits.size()) {
            return Result.fail("invalid-amount");
        }
        Account.Deposit d = a.deposits.remove(index);
        long fee = Math.round(d.amount() * plugin.getConfig().getDouble("fixed-deposit.early-break-penalty", 1) / 100.0);
        a.balance += d.amount() - fee;
        log(a, "FD_BREAK", d.amount() - fee, "-" + Fmt.money(fee));
        return Result.ok();
    }

    public Result borrow(Account a, long amount) {
        Tier t = tier(a);
        long min = plugin.getConfig().getLong("loan.min-amount", 1000);
        if (a.overdue()) {
            return Result.fail("loan-overdue");
        }
        if (amount < min) {
            return Result.fail("loan-min", "min", Fmt.money(min));
        }
        long room = t.loanLimit() - a.loan;
        if (amount > room) {
            return Result.fail("loan-limit", "limit", Fmt.money(Math.max(0, room)));
        }
        if (amount > depositRoom(a)) {
            return Result.fail("balance-limit", "limit", Fmt.money(t.maxBalance()));
        }
        long interest = Math.round(amount * t.loanRate() / 100.0);
        boolean first = a.loan <= 0;
        a.loan += amount + interest;
        if (first) {
            a.loanDue = System.currentTimeMillis() + plugin.getConfig().getInt("loan.due-days", 30) * DAY;
            a.loanPenaltyAt = 0;
        }
        a.balance += amount;
        log(a, "LOAN", amount, "+" + Fmt.money(interest));
        return Result.ok();
    }

    public Result repay(Account a, long amount) {
        if (a.loan <= 0) {
            return Result.fail("no-loan");
        }
        if (amount <= 0) {
            return Result.fail("invalid-amount");
        }
        long pay = Math.min(amount, a.loan);
        if (a.balance < pay) {
            return Result.fail("not-enough-bank");
        }
        a.balance -= pay;
        a.loan -= pay;
        if (a.loan <= 0) {
            a.loan = 0;
            a.loanDue = 0;
            a.loanPenaltyAt = 0;
        }
        log(a, "REPAY", pay, "");
        return Result.ok();
    }

    /** Admin: change balance directly. */
    public void adminAdjust(Account a, long delta, String note) {
        a.balance = Math.max(0, a.balance + delta);
        log(a, "ADMIN", delta, note);
    }

    public void adminSet(Account a, long value) {
        long delta = value - a.balance;
        a.balance = Math.max(0, value);
        log(a, "ADMIN", delta, "set");
    }

    public void setTier(Account a, Tier t) {
        a.tier = t.id();
        dirty = true;
    }

    public void logCard(Account a, long price, String note) {
        log(a, "CARD", price, note);
    }

    public void adminLog(Account a, String type, long amount, String note) {
        log(a, type, amount, note);
    }

    // ---------------- ticking ----------------

    /** Interest, matured deposits, loan collection. Called every few minutes. */
    public void tick() {
        long now = System.currentTimeMillis();
        long interestMs = interestDays() * DAY;
        double penalty = plugin.getConfig().getDouble("loan.overdue-daily-penalty", 1);
        for (Account a : accounts.values()) {
            // matured fixed deposits
            for (int i = a.deposits.size() - 1; i >= 0; i--) {
                Account.Deposit d = a.deposits.get(i);
                if (now >= d.end()) {
                    long earned = Math.round(d.amount() * d.rate() / 100.0);
                    a.deposits.remove(i);
                    a.balance += d.amount() + earned;
                    a.totalInterest += earned;
                    log(a, "FD_PAYOUT", d.amount() + earned, "+" + Fmt.money(earned));
                    notify(a, "fd-matured", "amount", Fmt.money(d.amount() + earned), "interest", Fmt.money(earned));
                }
            }
            // periodic interest
            if (now - a.lastInterest >= interestMs) {
                int periodsDue = (int) Math.min(12, (now - a.lastInterest) / interestMs);
                long total = 0;
                for (int i = 0; i < periodsDue; i++) {
                    total += interestPreview(a);
                }
                a.lastInterest += periodsDue * interestMs;
                if (total > 0) {
                    a.balance += total;
                    a.totalInterest += total;
                    log(a, "INTEREST", total, tier(a).name());
                    notify(a, "interest-paid", "amount", Fmt.money(total), "tier", tier(a).label());
                }
                dirty = true;
            }
            // loans
            if (a.loan > 0 && a.loanDue > 0 && now >= a.loanDue) {
                long take = Math.min(a.balance, a.loan);
                if (take > 0) {
                    a.balance -= take;
                    a.loan -= take;
                    log(a, "REPAY", take, "auto");
                    notify(a, "loan-autocollect", "amount", Fmt.money(take));
                }
                if (a.loan <= 0) {
                    a.loan = 0;
                    a.loanDue = 0;
                    a.loanPenaltyAt = 0;
                } else {
                    long ref = Math.max(a.loanPenaltyAt, a.loanDue);
                    if (now - ref >= DAY) {
                        long days = (now - ref) / DAY;
                        long pen = Math.max(1, Math.round(a.loan * penalty / 100.0)) * days;
                        a.loan += pen;
                        a.loanPenaltyAt = ref + days * DAY;
                        log(a, "FEE", pen, "loan penalty");
                        notify(a, "loan-penalty", "amount", Fmt.money(pen), "owed", Fmt.money(a.loan));
                    }
                }
                dirty = true;
            }
        }
    }

    // ---------------- storage ----------------

    public void load() {
        accounts.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("accounts");
        if (s == null) {
            return;
        }
        for (String key : s.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection c = s.getConfigurationSection(key);
                Account a = new Account(id, c.getString("name", "?"));
                a.balance = c.getLong("balance");
                a.tier = c.getString("tier", "STANDARD");
                a.cardNo = c.getString("card", null);
                a.created = c.getLong("created", a.created);
                a.lastInterest = c.getLong("last-interest", a.created);
                a.totalInterest = c.getLong("total-interest");
                a.loan = c.getLong("loan");
                a.loanDue = c.getLong("loan-due");
                a.loanPenaltyAt = c.getLong("loan-penalty-at");
                a.dayStamp = c.getLong("day-stamp");
                a.dailyOut = c.getLong("daily-out");
                for (Map<?, ?> m : c.getMapList("deposits")) {
                    a.deposits.add(new Account.Deposit(num(m.get("amount")), num(m.get("start")), num(m.get("end")),
                            ((Number) m.get("rate")).doubleValue()));
                }
                for (Map<?, ?> m : c.getMapList("txs")) {
                    a.txs.add(new Account.Tx(num(m.get("time")), String.valueOf(m.get("type")),
                            num(m.get("amount")), String.valueOf(m.get("note")), num(m.get("after"))));
                }
                a.notices.addAll(c.getStringList("notices"));
                if (a.cardNo == null) {
                    a.cardNo = newCardNumber();
                }
                accounts.put(id, a);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping bad account " + key + ": " + ex.getMessage());
            }
        }
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }

    public synchronized void save() {
        if (!dirty) {
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        for (Account a : accounts.values()) {
            String p = "accounts." + a.id;
            y.set(p + ".name", a.name);
            y.set(p + ".balance", a.balance);
            y.set(p + ".tier", a.tier);
            y.set(p + ".card", a.cardNo);
            y.set(p + ".created", a.created);
            y.set(p + ".last-interest", a.lastInterest);
            y.set(p + ".total-interest", a.totalInterest);
            y.set(p + ".loan", a.loan);
            y.set(p + ".loan-due", a.loanDue);
            y.set(p + ".loan-penalty-at", a.loanPenaltyAt);
            y.set(p + ".day-stamp", a.dayStamp);
            y.set(p + ".daily-out", a.dailyOut);
            List<Map<String, Object>> ds = new ArrayList<>();
            for (Account.Deposit d : a.deposits) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("amount", d.amount());
                m.put("start", d.start());
                m.put("end", d.end());
                m.put("rate", d.rate());
                ds.add(m);
            }
            y.set(p + ".deposits", ds);
            List<Map<String, Object>> ts = new ArrayList<>();
            for (Account.Tx t : a.txs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("time", t.time());
                m.put("type", t.type());
                m.put("amount", t.amount());
                m.put("note", t.note());
                m.put("after", t.after());
                ts.add(m);
            }
            y.set(p + ".txs", ts);
            y.set(p + ".notices", a.notices);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
            dirty = false;
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not save accounts.yml: " + ex.getMessage());
        }
    }
}
