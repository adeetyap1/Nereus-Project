package com.nereus.rpg;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Menu buatan sendiri dari folder menus/*.yml (toko, dll). Judul menu boleh memakai huruf
 * resource pack (glyph), item boleh memakai item-model dari resource pack.
 * Uang lewat Vault (EssentialsX dll) dengan refleksi, jadi Vault opsional.
 */
public final class CustomMenus {
    private final Core core;
    private final Map<String, YamlConfiguration> defs = new LinkedHashMap<>();
    private final Set<String> registered = new HashSet<>();

    private boolean ecoTried;
    private Object eco;
    private Method balanceM;
    private Method withdrawM;
    private Method depositM;
    private Method okM;

    public CustomMenus(Core core) {
        this.core = core;
    }

    // ------------------------------------------------------------ muat

    public void load() {
        File dir = new File(core.plugin.getDataFolder(), "menus");
        if (!dir.exists()) {
            dir.mkdirs();
            core.plugin.saveResource("menus/toko.yml", false);
        }
        defs.clear();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            defs.put(id, y);
            List<String> names = new ArrayList<>();
            if (y.getString("open-command") != null) {
                names.add(y.getString("open-command"));
            }
            names.addAll(y.getStringList("aliases"));
            for (String n : names) {
                registerCommand(n.toLowerCase(Locale.ROOT), id);
            }
        }
    }

    private void registerCommand(String name, String id) {
        if (!registered.add(name)) {
            return;
        }
        Command c = new Command(name) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (sender instanceof Player p) {
                    open(p, id);
                } else {
                    sender.sendMessage("Hanya untuk pemain.");
                }
                return true;
            }
        };
        c.setDescription("Menu " + id);
        Bukkit.getCommandMap().register("nereusrpg", c);
    }

    public Set<String> ids() {
        return defs.keySet();
    }

    public boolean open(Player p, String id) {
        YamlConfiguration y = defs.get(id.toLowerCase(Locale.ROOT));
        if (y == null) {
            core.msg(p, "&cMenu tidak ditemukan.");
            return false;
        }
        new CustomMenu(core, p, this, id.toLowerCase(Locale.ROOT), y).open();
        return true;
    }

    // ------------------------------------------------------------ ekonomi (Vault, refleksi)

    private boolean eco() {
        if (eco != null) {
            return true;
        }
        if (ecoTried && Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        ecoTried = true;
        try {
            Class<?> c = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(c);
            if (rsp == null) {
                return false;
            }
            eco = rsp.getProvider();
            balanceM = c.getMethod("getBalance", OfflinePlayer.class);
            withdrawM = c.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            depositM = c.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            okM = withdrawM.getReturnType().getMethod("transactionSuccess");
            return true;
        } catch (Exception e) {
            eco = null;
            return false;
        }
    }

    public double balance(Player p) {
        try {
            return eco() ? (double) balanceM.invoke(eco, p) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean pay(Player p, double amount, boolean withdraw) {
        try {
            if (!eco()) {
                return false;
            }
            Object r = (withdraw ? withdrawM : depositM).invoke(eco, p, amount);
            return (boolean) okM.invoke(r);
        } catch (Exception e) {
            return false;
        }
    }

    static String money(double v) {
        return String.format(Locale.ROOT, "%,.0f", v).replace(',', '.');
    }

    // ------------------------------------------------------------ menu

    public static final class CustomMenu extends Menus.Base {
        private final CustomMenus owner;
        private final String id;
        private final YamlConfiguration cfg;
        private final Map<Integer, String> slotMap = new HashMap<>();

        CustomMenu(Core core, Player p, CustomMenus owner, String id, YamlConfiguration cfg) {
            super(core, p);
            this.owner = owner;
            this.id = id;
            this.cfg = cfg;
        }

        @Override
        protected String title() {
            return cfg.getString("title", "&8Menu");
        }

        @Override
        protected Component titleComponent() {
            return cfg.getBoolean("minimessage-title", false)
                    ? MiniMessage.miniMessage().deserialize(title()) : Text.c(title());
        }

        @Override
        protected int size() {
            return Math.max(1, Math.min(6, cfg.getInt("rows", 3))) * 9;
        }

        @Override
        protected int backSlot() {
            return cfg.getBoolean("back-button", false) ? size() - 1 : -1;
        }

        private String rep(String s, ConfigurationSection it) {
            Profile pr = core.profile(player);
            Defs.Rank r = core.rank(pr.rank);
            return s.replace("%player%", player.getName())
                    .replace("%gold%", money(owner.balance(player)))
                    .replace("%gp%", String.valueOf(pr.gp))
                    .replace("%level%", String.valueOf(pr.level))
                    .replace("%rank%", r == null ? "" : r.tag())
                    .replace("%price%", it == null ? "" : money(it.getDouble("price", 0)));
        }

        private ItemStack build(ConfigurationSection s) {
            Material m = Material.matchMaterial(s.getString("material", "STONE"));
            if (m == null) {
                m = Material.BARRIER;
            }
            ItemStack it = new ItemStack(m, Math.max(1, Math.min(64, s.getInt("amount", 1))));
            ItemMeta meta = it.getItemMeta();
            if (s.contains("name")) {
                meta.displayName(Text.c(rep(s.getString("name", ""), s)));
            }
            List<Component> lore = new ArrayList<>();
            for (String l : s.getStringList("lore")) {
                lore.add(Text.c(rep(l, s)));
            }
            String lock = lockReason(s);
            if (lock != null) {
                lore.add(Text.c("&c" + lock));
            }
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            String model = s.getString("item-model");
            if (model != null) {
                NamespacedKey k = NamespacedKey.fromString(model);
                if (k != null) {
                    meta.setItemModel(k);
                }
            }
            if (s.getBoolean("hide-flags", true)) {
                meta.addItemFlags(ItemFlag.values());
            }
            if (s.getBoolean("glow", false)) {
                meta.setEnchantmentGlintOverride(true);
            }
            it.setItemMeta(meta);
            return it;
        }

        private String lockReason(ConfigurationSection s) {
            Profile pr = core.profile(player);
            String minRank = s.getString("min-rank");
            if (minRank != null && pr.rank < core.rankIndex(minRank)) {
                return "Butuh rank " + minRank.toUpperCase(Locale.ROOT);
            }
            if (pr.level < s.getInt("min-level", 0)) {
                return "Butuh level " + s.getInt("min-level");
            }
            String cls = s.getString("class");
            if (cls != null && !cls.equalsIgnoreCase(pr.cls)) {
                return "Khusus class " + cls;
            }
            return null;
        }

        @Override
        protected void render() {
            inv.clear();
            slotMap.clear();
            String filler = cfg.getString("filler");
            if (filler != null) {
                Material fm = Material.matchMaterial(filler);
                if (fm != null) {
                    ItemStack f = Text.item(fm, " ");
                    for (int i = 0; i < inv.getSize(); i++) {
                        inv.setItem(i, f);
                    }
                }
            }
            ConfigurationSection items = cfg.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    ConfigurationSection s = items.getConfigurationSection(key);
                    if (s == null) {
                        continue;
                    }
                    List<Integer> slots = new ArrayList<>(s.getIntegerList("slots"));
                    if (s.contains("slot")) {
                        slots.add(s.getInt("slot"));
                    }
                    ItemStack it = build(s);
                    for (int slot : slots) {
                        if (slot >= 0 && slot < inv.getSize()) {
                            inv.setItem(slot, it);
                            slotMap.put(slot, key);
                        }
                    }
                }
            }
            if (backSlot() >= 0) {
                addBack();
            }
        }

        @Override
        public void click(int slot, boolean shift) {
            String key = slotMap.get(slot);
            ConfigurationSection s = key == null ? null : cfg.getConfigurationSection("items." + key);
            if (s == null) {
                return;
            }
            String lock = lockReason(s);
            if (lock != null) {
                core.msg(player, "&c" + lock);
                return;
            }
            String action = s.getString("action", "NONE").toUpperCase(Locale.ROOT);
            switch (action) {
                case "BUY" -> buy(s);
                case "SELL" -> sell(s);
                case "COMMAND" -> runCommands(s);
                case "OPEN" -> owner.open(player, s.getString("target", "toko"));
                case "BACK" -> new Menus.HubMenu(core, player).open();
                case "CLOSE" -> player.closeInventory();
                default -> {
                }
            }
            if (s.getBoolean("close", false)) {
                player.closeInventory();
            } else if (action.equals("BUY") || action.equals("SELL")) {
                render();
            }
        }

        private void runCommands(ConfigurationSection s) {
            core.runConsole(player, s.getStringList("commands"));
            for (String c : s.getStringList("player-commands")) {
                player.performCommand(c.replace("%player%", player.getName()).replaceFirst("^/", ""));
            }
        }

        private void buy(ConfigurationSection s) {
            double price = s.getDouble("price", 0);
            if (!owner.eco()) {
                core.msg(player, "&cEkonomi (Vault) tidak tersedia, pembelian dinonaktifkan.");
                return;
            }
            if (owner.balance(player) < price) {
                core.msg(player, "&cUangmu kurang. Butuh &6" + money(price) + "&c.");
                return;
            }
            if (!owner.pay(player, price, true)) {
                core.msg(player, "&cPembayaran gagal.");
                return;
            }
            ConfigurationSection gi = s.getConfigurationSection("give-item");
            if (gi != null) {
                ItemStack give = build(gi);
                for (ItemStack left : player.getInventory().addItem(give).values()) {
                    player.getWorld().dropItem(player.getLocation(), left);
                }
            }
            runCommands(s);
            core.msg(player, "&aDibeli seharga &6" + money(price) + "&a.");
        }

        private void sell(ConfigurationSection s) {
            ConfigurationSection si = s.getConfigurationSection("sell-item");
            if (si == null) {
                return;
            }
            Material m = Material.matchMaterial(si.getString("material", ""));
            int amount = Math.max(1, si.getInt("amount", 1));
            if (m == null || core.tracker.count(player, m) < amount) {
                core.msg(player, "&cKamu tidak punya &f" + amount + " " + Text.pretty(si.getString("material", "?")) + "&c.");
                return;
            }
            double price = s.getDouble("price", 0);
            if (!owner.eco() || !owner.pay(player, price, false)) {
                core.msg(player, "&cEkonomi (Vault) tidak tersedia.");
                return;
            }
            player.getInventory().removeItem(new ItemStack(m, amount));
            core.msg(player, "&aTerjual, kamu dapat &6" + money(price) + "&a.");
        }
    }
}
