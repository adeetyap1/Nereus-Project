package me.naook1.nereusbank;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import me.naook1.nereusbank.util.Fmt;
import me.naook1.nereusbank.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Physical Nereus cards (Silver / Gold / Platinum). */
public final class CardManager {

    private final NereusBank plugin;
    private final NamespacedKey keyOwner;
    private final NamespacedKey keyTier;
    private final NamespacedKey keyNo;

    public CardManager(NereusBank plugin) {
        this.plugin = plugin;
        keyOwner = new NamespacedKey(plugin, "card-owner");
        keyTier = new NamespacedKey(plugin, "card-tier");
        keyNo = new NamespacedKey(plugin, "card-no");
    }

    public ItemStack create(Account a, Tier t) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item("&b&lNereus " + t.label() + " &b&lCard"));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Text.item("&8Nereus Bank"));
        lore.add(Text.item(""));
        lore.add(Text.item("&7Pemegang: &f" + a.name));
        lore.add(Text.item("&7Nomor: &f" + a.cardNo));
        lore.add(Text.item("&7Tier: " + t.label()));
        lore.add(Text.item(""));
        lore.add(Text.item("&7Bunga: &a" + Fmt.pct(t.interest()) + " &8/ " + plugin.bank().interestDays() + " hari"));
        lore.add(Text.item("&7Biaya transfer: &e" + Fmt.pct(t.transferFee())));
        lore.add(Text.item("&7Batas harian: &e" + (t.dailyLimit() < 0 ? "Tanpa batas" : Fmt.money(t.dailyLimit()))));
        lore.add(Text.item("&7Pinjaman: &e" + Fmt.money(t.loanLimit()) + " &8@ " + Fmt.pct(t.loanRate())));
        lore.add(Text.item(""));
        lore.add(Text.item("&8Klik kanan untuk membuka bank."));
        lore.add(Text.item("&8Kartu ini hanya bisa dipakai pemiliknya."));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (t.model() > 0) {
            meta.setCustomModelData(t.model());
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyOwner, PersistentDataType.STRING, a.id.toString());
        pdc.set(keyTier, PersistentDataType.STRING, t.id());
        pdc.set(keyNo, PersistentDataType.STRING, a.cardNo == null ? "" : a.cardNo);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isCard(ItemStack item) {
        return owner(item) != null;
    }

    public UUID owner(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return null;
        }
        String s = item.getItemMeta().getPersistentDataContainer().get(keyOwner, PersistentDataType.STRING);
        if (s == null) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String tierOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(keyTier, PersistentDataType.STRING);
    }

    /** True if the player carries a card of theirs. */
    public boolean holdsOwnCard(Player p) {
        PlayerInventory inv = p.getInventory();
        UUID a = owner(inv.getItemInMainHand());
        UUID b = owner(inv.getItemInOffHand());
        return p.getUniqueId().equals(a) || p.getUniqueId().equals(b);
    }

    /** Removes the player's old cards from their inventory (used on upgrade / replace). */
    public void removeOwnCards(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (p.getUniqueId().equals(owner(it))) {
                inv.setItem(i, null);
            }
        }
    }

    public void give(Player p, ItemStack card) {
        var left = p.getInventory().addItem(card);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    /** Pays from the bank balance first, then from the wallet. */
    private boolean pay(Player p, Account a, long price) {
        if (price <= 0) {
            return true;
        }
        if (a.balance >= price) {
            a.balance -= price;
            return true;
        }
        if (plugin.economy().ready() && plugin.economy().wallet(p) >= price && plugin.economy().take(p, price)) {
            return true;
        }
        return false;
    }

    /** Buys or upgrades to a tier and hands over the card. */
    public BankManager.Result buy(Player p, Tier t) {
        BankManager bank = plugin.bank();
        Account a = bank.account(p);
        Tier cur = bank.tier(a);
        if (t.order() <= cur.order()) {
            return BankManager.Result.fail("card-already", "tier", cur.label());
        }
        if (a.overdue()) {
            return BankManager.Result.fail("loan-overdue");
        }
        if (a.balance + a.lockedInDeposits() < t.cardMinBalance()) {
            return BankManager.Result.fail("card-min-balance", "min", Fmt.money(t.cardMinBalance()));
        }
        if (!pay(p, a, t.cardPrice())) {
            return BankManager.Result.fail("card-no-funds", "price", Fmt.money(t.cardPrice()));
        }
        bank.setTier(a, t);
        bank.logCard(a, t.cardPrice(), t.name());
        removeOwnCards(p);
        give(p, create(a, t));
        return BankManager.Result.ok();
    }

    /** Reprints a lost card of the current tier (new number). */
    public BankManager.Result replace(Player p) {
        BankManager bank = plugin.bank();
        Account a = bank.account(p);
        long fee = plugin.getConfig().getLong("card.replace-fee", 5000);
        if (!pay(p, a, fee)) {
            return BankManager.Result.fail("card-no-funds", "price", Fmt.money(fee));
        }
        removeOwnCards(p);
        bank.freshCardNumber(a);
        bank.logCard(a, fee, "replace");
        give(p, create(a, bank.tier(a)));
        return BankManager.Result.ok();
    }
}
