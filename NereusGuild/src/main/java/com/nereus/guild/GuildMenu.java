package com.nereus.guild;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Menu guild: rank saat ini, syarat rank berikutnya, tombol ujian, dan daftar rank. */
public final class GuildMenu implements InventoryHolder {
    private static final int SLOT_CURRENT = 11;
    private static final int SLOT_NEXT = 13;
    private static final int SLOT_ACTION = 15;
    private static final int SLOT_LADDER = 22;

    private final NereusGuild plugin;
    private final GuildManager mgr;
    private final Player player;
    private Inventory inventory;

    public GuildMenu(NereusGuild plugin, GuildManager mgr, Player player) {
        this.plugin = plugin;
        this.mgr = mgr;
        this.player = player;
    }

    public void open() {
        String title = plugin.getConfig().getString("menu-title", "&8Guild Petualang");
        inventory = Bukkit.createInventory(this, 27, NereusGuild.LEGACY.deserialize(title));
        render();
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void click(int slot) {
        if (slot == SLOT_ACTION) {
            mgr.startExam(player);
            render();
        }
    }

    private void render() {
        PlayerData d = mgr.data(player.getUniqueId());
        RankDef current = mgr.rank(d.rank);
        RankDef next = mgr.rank(d.rank + 1);
        int gp = mgr.gp(player);
        String gpText = mgr.gpEnabled() ? String.valueOf(gp) : "-";
        long now = System.currentTimeMillis();

        inventory.clear();
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(SLOT_CURRENT, item(Material.NETHER_STAR, current.label(),
                "&7Rank petualangmu saat ini",
                "&7GP: &b" + gpText,
                "",
                "&8Quest: rank ini, semua di bawahnya,",
                "&8dan satu tingkat di atasnya."));

        if (next == null) {
            inventory.setItem(SLOT_NEXT, item(Material.BOOK, "&6Rank tertinggi", "&7Kamu sudah di puncak guild."));
            inventory.setItem(SLOT_ACTION, item(Material.DRAGON_EGG, "&6Legenda Guild"));
        } else {
            List<String> lore = new ArrayList<>();
            lore.add("&7Rank: " + next.label());
            if (mgr.gpEnabled()) {
                lore.add("&7GP: " + (gp >= next.gp() ? "&a" : "&c") + gp + "&7/&f" + next.gp());
            }
            lore.add("&7Ujian: &f" + mgr.describe(next.exam()));
            inventory.setItem(SLOT_NEXT, item(Material.BOOK, "&eRank berikutnya", lore.toArray(new String[0])));
            inventory.setItem(SLOT_ACTION, actionItem(d, next, gp, now));
        }

        List<String> ladder = new ArrayList<>();
        for (int i = 0; i < mgr.ranks().size(); i++) {
            RankDef r = mgr.ranks().get(i);
            String mark = i < d.rank ? "&a✔ " : (i == d.rank ? "&e▶ " : "&8• ");
            String req = i == 0 || !mgr.gpEnabled() ? "" : " &8(" + r.gp() + " GP)";
            ladder.add(mark + r.label() + req);
        }
        inventory.setItem(SLOT_LADDER, item(Material.BOOKSHELF, "&bDaftar rank", ladder.toArray(new String[0])));
    }

    private ItemStack actionItem(PlayerData d, RankDef next, int gp, long now) {
        ExamDef ex = next.exam();
        if (d.exam != null) {
            List<String> lore = new ArrayList<>();
            lore.add("&7" + mgr.describe(ex));
            if (ex != null && ex.type() == ExamDef.Type.KILL) {
                lore.add("&7Progres: &f" + d.exam.progress + "&7/&f" + ex.amount());
            }
            if (d.exam.deadline > 0) {
                lore.add("&7Sisa waktu: &c" + mgr.formatTime(d.exam.deadline - now));
            }
            lore.add("");
            lore.add("&8Batalkan dengan /guild abandon");
            return item(Material.CLOCK, "&eUjian berjalan", lore.toArray(new String[0]));
        }
        if (d.cooldownUntil > now) {
            return item(Material.BARRIER, "&cJeda ujian", "&7Bisa mengulang dalam &f" + mgr.formatTime(d.cooldownUntil - now));
        }
        if (mgr.gpEnabled() && gp < next.gp()) {
            return item(Material.RED_CONCRETE, "&cGP belum cukup",
                    "&7Butuh &f" + next.gp() + " &7GP, kamu punya &f" + gp + "&7.",
                    "&8Kerjakan quest harian untuk menambah GP.");
        }
        boolean direct = ex == null || ex.type() == ExamDef.Type.NONE;
        return item(Material.LIME_CONCRETE, direct ? "&aNaik rank" : "&aMulai ujian",
                "&7Menuju " + next.label(),
                "&7" + mgr.describe(ex),
                "",
                "&eKlik untuk " + (direct ? "naik rank" : "memulai"));
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(NereusGuild.LEGACY.deserialize(name).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String l : lore) {
                lines.add(NereusGuild.LEGACY.deserialize(l).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lines);
        }
        it.setItemMeta(meta);
        return it;
    }
}
