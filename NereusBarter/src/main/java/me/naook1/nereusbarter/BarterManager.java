package me.naook1.nereusbarter;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.naook1.nereusbarter.gui.BarterMenu;
import me.naook1.nereusbarter.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Requests, sessions and the final atomic exchange. Everything runs on the main thread.
 * Offered items stay in the owner's inventory until the exchange, and are verified again right before it,
 * so a crash or a cancelled barter can never lose or duplicate an item.
 */
public final class BarterManager {

    private record Request(UUID from, String fromName, long expires) {
    }

    private static final DecimalFormat NUM = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("id-ID")));

    private final NereusBarter plugin;
    private final Map<UUID, BarterSession> sessions = new HashMap<>();
    private final Map<UUID, Request> requests = new HashMap<>();     // key = target
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private boolean enabled = true;
    private long expireMs = 30_000;
    private long cooldownMs = 10_000;
    private int minPlaytime = 20;
    private int maxDistance = 30;
    private long lockMs = 3_000;
    private boolean moneyEnabled = true;
    private final List<Long> steps = new ArrayList<>();
    private long maxMoney = 100_000_000L;
    private String symbol = "$";
    private ZoneId zone = ZoneId.systemDefault();
    private final Set<Material> blocked = EnumSet.noneOf(Material.class);

    public BarterManager(NereusBarter plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        var c = plugin.getConfig();
        enabled = c.getBoolean("enabled", true);
        expireMs = Math.max(5, c.getLong("request.expire-seconds", 30)) * 1000L;
        cooldownMs = Math.max(0, c.getLong("request.cooldown-seconds", 10)) * 1000L;
        minPlaytime = Math.max(0, c.getInt("rules.min-playtime-minutes", 20));
        maxDistance = Math.max(0, c.getInt("rules.max-distance", 30));
        lockMs = Math.max(0, c.getLong("rules.confirm-lock-seconds", 3)) * 1000L;
        moneyEnabled = c.getBoolean("money.enabled", true);
        maxMoney = Math.max(1, c.getLong("money.max", 100_000_000L));
        symbol = c.getString("currency-symbol", "$");
        steps.clear();
        for (Long l : c.getLongList("money.steps")) {
            if (l != null && l > 0 && steps.size() < 3) {
                steps.add(l);
            }
        }
        long[] defaults = {1000, 10000, 100000};
        while (steps.size() < 3) {
            steps.add(defaults[steps.size()]);
        }
        try {
            zone = ZoneId.of(c.getString("history.timezone", "Asia/Makassar"));
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
        }
        blocked.clear();
        for (String s : c.getStringList("blocked-items")) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                blocked.add(m);
            } else {
                plugin.getLogger().warning("blocked-items: unknown material '" + s + "'");
            }
        }
    }

    public boolean moneyEnabled() {
        return moneyEnabled && plugin.economy().ready();
    }

    public List<Long> steps() {
        return steps;
    }

    public String fmt(long amount) {
        return symbol + NUM.format(amount);
    }

    public BarterSession sessionOf(Player p) {
        return sessions.get(p.getUniqueId());
    }

    // ------------------------------------------------------------------ requests

    public void request(Player from, Player target) {
        var m = plugin.messages();
        if (!enabled) {
            m.send(from, "disabled");
            return;
        }
        if (from.getUniqueId().equals(target.getUniqueId())) {
            m.send(from, "self");
            return;
        }
        if (sessions.containsKey(from.getUniqueId())) {
            m.send(from, "busy-you");
            return;
        }
        if (sessions.containsKey(target.getUniqueId())) {
            m.send(from, "busy-them", "target", target.getName());
            return;
        }
        if (plugin.store().isOff(target.getUniqueId())) {
            m.send(from, "declined-all", "target", target.getName());
            return;
        }
        long now = System.currentTimeMillis();
        Long until = cooldowns.get(from.getUniqueId());
        if (until != null && until > now) {
            m.send(from, "cooldown", "time", String.valueOf((until - now + 999) / 1000));
            return;
        }
        if (!validatePair(from, target, from)) {
            return;
        }
        // one outgoing request per sender
        requests.values().removeIf(r -> r.from().equals(from.getUniqueId()));
        requests.put(target.getUniqueId(), new Request(from.getUniqueId(), from.getName(), now + expireMs));
        cooldowns.put(from.getUniqueId(), now + cooldownMs);

        String secs = String.valueOf(expireMs / 1000);
        m.send(from, "request-sent", "target", target.getName(), "seconds", secs);
        Component msg = Text.parse(m.format("request-received", "player", from.getName(), "seconds", secs))
                .appendNewline()
                .append(Text.parse(m.raw("btn-accept")).clickEvent(ClickEvent.runCommand("/barter accept")))
                .append(Component.text("   "))
                .append(Text.parse(m.raw("btn-deny")).clickEvent(ClickEvent.runCommand("/barter deny")));
        target.sendMessage(msg);
        sound(target, "sounds.request");
    }

    public void accept(Player target) {
        var m = plugin.messages();
        Request r = requests.remove(target.getUniqueId());
        if (r == null || r.expires() < System.currentTimeMillis()) {
            m.send(target, "no-request");
            return;
        }
        Player from = Bukkit.getPlayer(r.from());
        if (from == null || !from.isOnline()) {
            m.send(target, "not-found");
            return;
        }
        if (!enabled) {
            m.send(target, "disabled");
            return;
        }
        if (sessions.containsKey(target.getUniqueId()) || sessions.containsKey(from.getUniqueId())) {
            m.send(target, "busy-them", "target", from.getName());
            return;
        }
        if (!validatePair(from, target, target)) {
            return;
        }
        BarterSession s = new BarterSession(from.getUniqueId(), from.getName(), target.getUniqueId(), target.getName());
        s.menus[0] = new BarterMenu(plugin, s, 0);
        s.menus[1] = new BarterMenu(plugin, s, 1);
        sessions.put(from.getUniqueId(), s);
        sessions.put(target.getUniqueId(), s);
        s.menus[0].render();
        s.menus[1].render();
        s.menus[0].open(from);
        s.menus[1].open(target);
        sound(from, "sounds.request");
        sound(target, "sounds.request");
    }

    public void deny(Player target) {
        var m = plugin.messages();
        Request r = requests.remove(target.getUniqueId());
        if (r == null) {
            m.send(target, "no-request");
            return;
        }
        m.send(target, "denied-you", "player", r.fromName());
        Player from = Bukkit.getPlayer(r.from());
        if (from != null) {
            m.send(from, "denied-them", "player", target.getName());
        }
    }

    /** Checks playtime, world and distance for both players. Reports problems to {@code reportTo}. */
    private boolean validatePair(Player a, Player b, Player reportTo) {
        var m = plugin.messages();
        for (Player p : new Player[]{a, b}) {
            if (minPlaytime > 0 && !p.hasPermission("barter.bypass")) {
                long minutes = p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 1200L;   // value is in ticks
                if (minutes < minPlaytime) {
                    if (p.equals(reportTo)) {
                        m.send(reportTo, "playtime-you", "minutes", String.valueOf(minPlaytime - minutes));
                    } else {
                        m.send(reportTo, "playtime-them", "target", p.getName());
                    }
                    return false;
                }
            }
        }
        String problem = distanceProblem(a, b);
        if (problem != null) {
            if (problem.equals("world")) {
                m.send(reportTo, "world");
            } else {
                m.send(reportTo, "too-far", "distance", String.valueOf(maxDistance));
            }
            return false;
        }
        return true;
    }

    /** null when fine, otherwise "world" or "far". */
    private String distanceProblem(Player a, Player b) {
        if (maxDistance <= 0 || a.hasPermission("barter.bypass") || b.hasPermission("barter.bypass")) {
            return null;
        }
        if (!a.getWorld().equals(b.getWorld())) {
            return "world";
        }
        return a.getLocation().distanceSquared(b.getLocation()) > (double) maxDistance * maxDistance ? "far" : null;
    }

    public boolean toggle(Player p) {
        boolean on = plugin.store().toggle(p.getUniqueId());
        plugin.messages().send(p, on ? "toggle-on" : "toggle-off");
        return on;
    }

    public void history(Player p) {
        var m = plugin.messages();
        List<BarterStore.Entry> list = plugin.store().recent(p.getUniqueId(), 5);
        if (list.isEmpty()) {
            m.send(p, "history-empty");
            return;
        }
        m.send(p, "history-header", "count", String.valueOf(list.size()));
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(zone);
        for (BarterStore.Entry e : list) {
            boolean iAmA = e.a().equals(p.getUniqueId());
            String give = describe(iAmA ? e.aGave() : e.bGave(), iAmA ? e.aMoney() : e.bMoney());
            String get = describe(iAmA ? e.bGave() : e.aGave(), iAmA ? e.bMoney() : e.aMoney());
            m.send(p, "history-line", "time", f.format(Instant.ofEpochMilli(e.time())),
                    "partner", iAmA ? e.bName() : e.aName(), "give", give, "get", get);
        }
    }

    private String describe(String items, long money) {
        String out = items == null ? "" : items;
        if (money > 0) {
            out = out.isEmpty() ? fmt(money) : out + ", " + fmt(money);
        }
        return out.isEmpty() ? plugin.messages().raw("nothing") : out;
    }

    // ------------------------------------------------------------------ in-GUI actions

    /** Click on an item in the player's own inventory: put it (or one piece of it) into the offer. */
    public void offerAdd(Player p, ItemStack clicked, boolean single) {
        BarterSession s = sessionOf(p);
        if (s == null || s.ended || s.confirmStage || clicked == null || clicked.getType().isAir()) {
            return;
        }
        var m = plugin.messages();
        if (blocked.contains(clicked.getType())) {
            m.send(p, "blocked-item");
            return;
        }
        int side = s.sideOf(p.getUniqueId());
        int have = count(p, clicked);
        int offered = 0;
        for (ItemStack o : s.offer[side]) {
            if (o != null && o.isSimilar(clicked)) {
                offered += o.getAmount();
            }
        }
        int amount = Math.min(single ? 1 : clicked.getAmount(), have - offered);
        if (amount <= 0) {
            return;
        }
        int max = clicked.getMaxStackSize();
        for (int i = 0; i < BarterSession.OFFER_SLOTS && amount > 0; i++) {
            ItemStack o = s.offer[side][i];
            if (o != null && o.isSimilar(clicked) && o.getAmount() < max) {
                int put = Math.min(amount, max - o.getAmount());
                o.setAmount(o.getAmount() + put);
                amount -= put;
            }
        }
        for (int i = 0; i < BarterSession.OFFER_SLOTS && amount > 0; i++) {
            if (s.offer[side][i] == null) {
                ItemStack n = clicked.clone();
                int put = Math.min(amount, max);
                n.setAmount(put);
                s.offer[side][i] = n;
                amount -= put;
            }
        }
        if (amount > 0) {
            m.send(p, "offer-full");
        }
        changed(s, side);
    }

    public void offerReturn(Player p, int index) {
        BarterSession s = sessionOf(p);
        if (s == null || s.ended || s.confirmStage || index < 0 || index >= BarterSession.OFFER_SLOTS) {
            return;
        }
        int side = s.sideOf(p.getUniqueId());
        if (s.offer[side][index] != null) {
            s.offer[side][index] = null;
            changed(s, side);
        }
    }

    public void addMoney(Player p, int stepIndex) {
        BarterSession s = sessionOf(p);
        if (s == null || s.ended || s.confirmStage || !moneyEnabled() || stepIndex < 0 || stepIndex >= steps.size()) {
            return;
        }
        var m = plugin.messages();
        int side = s.sideOf(p.getUniqueId());
        long target = s.money[side] + steps.get(stepIndex);
        if (target > maxMoney) {
            m.send(p, "money-max", "max", fmt(maxMoney));
            return;
        }
        if (!plugin.economy().has(p, target)) {
            m.send(p, "not-enough-money");
            return;
        }
        s.money[side] = target;
        changed(s, side);
    }

    public void resetMoney(Player p) {
        BarterSession s = sessionOf(p);
        if (s == null || s.ended || s.confirmStage) {
            return;
        }
        int side = s.sideOf(p.getUniqueId());
        if (s.money[side] != 0) {
            s.money[side] = 0;
            changed(s, side);
        }
    }

    /** The offer of {@code side} changed: everybody has to get ready again. */
    private void changed(BarterSession s, int side) {
        boolean someoneWasReady = s.ready[0] || s.ready[1];
        s.ready[0] = s.ready[1] = false;
        s.confirmed[0] = s.confirmed[1] = false;
        s.confirmStage = false;
        if (someoneWasReady) {
            Player other = Bukkit.getPlayer(s.ids[BarterSession.other(side)]);
            if (other != null) {
                plugin.messages().send(other, "changed", "player", s.names[side]);
            }
        }
        renderBoth(s);
    }

    public void toggleReady(Player p) {
        BarterSession s = sessionOf(p);
        if (s == null || s.ended || s.confirmStage) {
            return;
        }
        int side = s.sideOf(p.getUniqueId());
        if (!s.ready[side] && !s.hasAnything()) {
            plugin.messages().send(p, "empty-offer");
            return;
        }
        s.ready[side] = !s.ready[side];
        if (s.ready[0] && s.ready[1]) {
            s.confirmStage = true;
            s.unlockAt = System.currentTimeMillis() + lockMs;
            s.confirmed[0] = s.confirmed[1] = false;
        }
        renderBoth(s);
    }

    public void confirm(Player p) {
        BarterSession s = sessionOf(p);
        if (s == null || s.ended || !s.confirmStage) {
            return;
        }
        int side = s.sideOf(p.getUniqueId());
        long left = s.unlockAt - System.currentTimeMillis();
        if (left > 0) {
            plugin.messages().send(p, "locked", "time", String.valueOf((left + 999) / 1000));
            return;
        }
        s.confirmed[side] = true;
        if (s.confirmed[0] && s.confirmed[1]) {
            execute(s);
        } else {
            renderBoth(s);
        }
    }

    public void back(Player p) {
        BarterSession s = sessionOf(p);
        if (s == null || s.ended || !s.confirmStage) {
            return;
        }
        s.confirmStage = false;
        s.ready[0] = s.ready[1] = false;
        s.confirmed[0] = s.confirmed[1] = false;
        renderBoth(s);
    }

    private void renderBoth(BarterSession s) {
        s.menus[0].render();
        s.menus[1].render();
    }

    // ------------------------------------------------------------------ the exchange

    private void execute(BarterSession s) {
        Player a = Bukkit.getPlayer(s.ids[0]);
        Player b = Bukkit.getPlayer(s.ids[1]);
        if (a == null || b == null) {
            cancel(s, "reason-quit");
            return;
        }
        String problem = distanceProblem(a, b);
        if (problem != null) {
            cancel(s, problem.equals("world") ? "reason-world" : "reason-far");
            return;
        }
        Player[] pl = {a, b};
        List<ItemStack> out0 = collapse(s.offer[0]);
        List<ItemStack> out1 = collapse(s.offer[1]);
        List<List<ItemStack>> outgoing = List.of(out0, out1);

        // 1) everything still there?
        for (int i = 0; i < 2; i++) {
            for (ItemStack it : outgoing.get(i)) {
                if (blocked.contains(it.getType()) || count(pl[i], it) < it.getAmount()) {
                    cancel(s, "reason-incomplete");
                    return;
                }
            }
            if (s.money[i] > 0 && (!moneyEnabled() || !plugin.economy().has(pl[i], s.money[i]))) {
                cancel(s, "reason-incomplete");
                return;
            }
        }
        // 2) room for what they receive (simulated on a copy of the inventory)
        for (int i = 0; i < 2; i++) {
            if (!fits(pl[i], outgoing.get(i), outgoing.get(1 - i))) {
                cancel(s, "reason-full");
                return;
            }
        }
        // 3) money out (reversible)
        boolean took0 = false;
        if (s.money[0] > 0) {
            if (!plugin.economy().withdraw(a, s.money[0])) {
                cancel(s, "reason-incomplete");
                return;
            }
            took0 = true;
        }
        if (s.money[1] > 0 && !plugin.economy().withdraw(b, s.money[1])) {
            if (took0) {
                plugin.economy().deposit(a, s.money[0]);
            }
            cancel(s, "reason-incomplete");
            return;
        }
        // 4) items
        try {
            for (int i = 0; i < 2; i++) {
                var left = pl[i].getInventory().removeItem(copies(outgoing.get(i)));
                if (!left.isEmpty()) {
                    plugin.getLogger().severe("Barter: could not remove all items from " + pl[i].getName() + " (verified earlier!)");
                }
            }
            for (int i = 0; i < 2; i++) {
                var left = pl[i].getInventory().addItem(copies(outgoing.get(1 - i)));
                for (ItemStack rest : left.values()) {
                    pl[i].getWorld().dropItem(pl[i].getLocation(), rest);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Barter exchange error: " + e);
            if (s.money[0] > 0) {
                plugin.economy().deposit(a, s.money[0]);
            }
            if (s.money[1] > 0) {
                plugin.economy().deposit(b, s.money[1]);
            }
            cancel(s, "reason-error");
            return;
        }
        // 5) money in
        if (s.money[0] > 0 && !plugin.economy().deposit(b, s.money[0])) {
            plugin.getLogger().severe("Barter: could not pay " + s.money[0] + " to " + b.getName());
        }
        if (s.money[1] > 0 && !plugin.economy().deposit(a, s.money[1])) {
            plugin.getLogger().severe("Barter: could not pay " + s.money[1] + " to " + a.getName());
        }

        // done
        String gave0 = summary(out0);
        String gave1 = summary(out1);
        plugin.store().add(new BarterStore.Entry(System.currentTimeMillis(), a.getUniqueId(), a.getName(), gave0,
                s.money[0], b.getUniqueId(), b.getName(), gave1, s.money[1]));
        var m = plugin.messages();
        for (int i = 0; i < 2; i++) {
            m.send(pl[i], "success", "player", s.names[1 - i]);
            m.sendList(pl[i], "success-detail",
                    "give", describe(i == 0 ? gave0 : gave1, s.money[i]),
                    "get", describe(i == 0 ? gave1 : gave0, s.money[1 - i]));
            sound(pl[i], "sounds.success");
        }
        end(s);
    }

    private boolean fits(Player p, List<ItemStack> out, List<ItemStack> in) {
        Inventory sim = Bukkit.createInventory(null, 36);
        ItemStack[] contents = p.getInventory().getStorageContents();
        ItemStack[] copy = new ItemStack[36];
        for (int i = 0; i < copy.length && i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        sim.setContents(copy);
        if (!sim.removeItem(copies(out)).isEmpty()) {
            return false;
        }
        return sim.addItem(copies(in)).isEmpty();
    }

    private static ItemStack[] copies(List<ItemStack> list) {
        ItemStack[] arr = new ItemStack[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i).clone();
        }
        return arr;
    }

    /** Merges the offer slots into one stack per distinct item (amount may exceed the max stack size). */
    private static List<ItemStack> collapse(ItemStack[] offer) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack it : offer) {
            if (it == null || it.getType().isAir()) {
                continue;
            }
            boolean merged = false;
            for (ItemStack o : out) {
                if (o.isSimilar(it)) {
                    o.setAmount(o.getAmount() + it.getAmount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                out.add(it.clone());
            }
        }
        return out;
    }

    private static int count(Player p, ItemStack like) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s != null && s.isSimilar(like)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    private static String summary(List<ItemStack> items) {
        Map<String, Integer> byName = new LinkedHashMap<>();
        for (ItemStack it : items) {
            byName.merge(titleCase(it.getType()), it.getAmount(), Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : byName.entrySet()) {
            parts.add(e.getValue() + "x " + e.getKey());
        }
        return String.join(", ", parts);
    }

    private static String titleCase(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String part : m.name().toLowerCase(Locale.ROOT).split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------ ending

    /** Ends the session, tells both players why, and closes their windows. */
    public void cancel(BarterSession s, String reasonKey) {
        if (s.ended) {
            return;
        }
        var m = plugin.messages();
        String reason = m.raw(reasonKey);
        for (UUID id : s.ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                m.send(p, "cancelled", "reason", reason);
                sound(p, "sounds.cancel");
            }
        }
        end(s);
    }

    /** Marks the session as over and closes both windows on the next tick. */
    private void end(BarterSession s) {
        s.ended = true;
        sessions.remove(s.ids[0]);
        sessions.remove(s.ids[1]);
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (UUID id : s.ids) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof BarterMenu) {
                    p.closeInventory();
                }
            }
        });
    }

    public void cancelFor(Player p, String reasonKey) {
        BarterSession s = sessionOf(p);
        if (s != null) {
            cancel(s, reasonKey);
        }
        requests.remove(p.getUniqueId());
        requests.values().removeIf(r -> r.from().equals(p.getUniqueId()));
    }

    public void cancelAll(String reasonKey) {
        for (BarterSession s : new ArrayList<>(new java.util.HashSet<>(sessions.values()))) {
            cancel(s, reasonKey);
        }
        requests.clear();
    }

    public void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Request>> it = requests.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            if (en.getValue().expires() < now) {
                Player from = Bukkit.getPlayer(en.getValue().from());
                Player to = Bukkit.getPlayer(en.getKey());
                if (from != null) {
                    plugin.messages().send(from, "request-expired", "target", to == null ? "?" : to.getName());
                }
                it.remove();
            }
        }
        for (BarterSession s : new ArrayList<>(new java.util.HashSet<>(sessions.values()))) {
            Player a = Bukkit.getPlayer(s.ids[0]);
            Player b = Bukkit.getPlayer(s.ids[1]);
            if (a == null || b == null) {
                cancel(s, "reason-quit");
                continue;
            }
            String problem = distanceProblem(a, b);
            if (problem != null) {
                cancel(s, problem.equals("world") ? "reason-world" : "reason-far");
                continue;
            }
            if (s.confirmStage) {
                renderBoth(s);          // countdown on the confirm button
            }
        }
    }

    private void sound(Player p, String path) {
        String key = plugin.getConfig().getString(path, "");
        if (key != null && !key.isBlank()) {
            p.playSound(p.getLocation(), key, 1f, 1f);
        }
    }
}
