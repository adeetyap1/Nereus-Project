package com.nereus.rpg;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Semua menu GUI (aman untuk Bedrock lewat Geyser). Klik selalu dibatalkan oleh MenuListener. */
public final class Menus {
    private Menus() {
    }

    public abstract static class Base implements InventoryHolder {
        protected final Core core;
        protected final Player player;
        protected Inventory inv;

        protected Base(Core core, Player player) {
            this.core = core;
            this.player = player;
        }

        protected abstract String title();

        protected abstract int size();

        protected abstract void render();

        public abstract void click(int slot, boolean shift);

        /** Dipanggil listener: tombol Kembali membuka menu utama, selain itu diteruskan ke click. */
        public final void handle(int slot, boolean shift) {
            if (!(this instanceof HubMenu) && slot == backSlot()) {
                new HubMenu(core, player).open();
                return;
            }
            click(slot, shift);
        }

        protected int backSlot() {
            return size() - 1;
        }

        protected void addBack() {
            inv.setItem(backSlot(), Text.item(Material.SPECTRAL_ARROW, "&cKembali", "&7Ke menu utama"));
        }

        public void open() {
            inv = Bukkit.createInventory(this, size(), Text.c(title()));
            render();
            player.openInventory(inv);
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }

        protected void fill() {
            ItemStack f = Text.item(Material.GRAY_STAINED_GLASS_PANE, " ");
            for (int i = 0; i < inv.getSize(); i++) {
                inv.setItem(i, f);
            }
        }

        protected String t(String key, String def) {
            return core.plugin.getConfig().getString("menu-titles." + key, def);
        }
    }

    // ------------------------------------------------------------ hub

    public static final class HubMenu extends Base {
        public HubMenu(Core core, Player p) {
            super(core, p);
        }

        @Override
        protected String title() {
            return t("hub", "&8Petualang");
        }

        @Override
        protected int size() {
            return 27;
        }

        @Override
        protected void render() {
            fill();
            Profile pr = core.profile(player);
            Defs.Clazz c = core.clazz(pr);
            Defs.Rank r = core.rank(pr.rank);
            long need = core.xpNeeded(pr.level);
            inv.setItem(10, Text.item(Material.NETHER_STAR, "&6" + player.getName(),
                    "&7Class: " + (c == null ? "&cbelum dipilih" : c.name()),
                    "&7Level: &f" + pr.level + " &8(" + pr.xp + "/" + need + " XP)",
                    "&7Rank guild: " + (r == null ? "-" : r.label()),
                    "&7GP: &b" + pr.gp,
                    "&7Mana: &9" + (int) pr.mana + "&7/&9" + (int) core.maxMana(pr),
                    "&7Login beruntun: &f" + pr.streak + " hari"));
            inv.setItem(11, Text.item(c == null ? Material.BARRIER : c.icon(), "&eClass",
                    c == null ? "&7Klik untuk memilih class" : "&7Klik untuk melihat / ganti class"));
            inv.setItem(12, Text.item(Material.EXPERIENCE_BOTTLE, "&aStat Karakter",
                    "&7Poin tersedia: &f" + pr.statPoints, "&7Klik untuk membagi poin"));
            inv.setItem(13, Text.item(Material.WRITABLE_BOOK, "&bPapan Quest",
                    "&7Aktif: &f" + pr.active.size() + "&7/&f" + core.plugin.getConfig().getInt("quests.max-active", 3),
                    "&7Selesai hari ini: &f" + pr.completedToday));
            Defs.Mission m = core.tracker.mission(pr);
            inv.setItem(14, Text.item(Material.ENCHANTED_BOOK, "&dMisi Cerita",
                    m == null ? "&7Kisah utama tamat" : "&7Sekarang: " + m.title(), "&7Klik untuk melihat (/story)"));
            inv.setItem(15, Text.item(Material.GOLDEN_HELMET, "&6Guild Petualang",
                    "&7Rank, GP, dan ujian naik rank"));
            List<String> sk = new ArrayList<>();
            if (c != null) {
                for (int i = 0; i < c.skills().size(); i++) {
                    Defs.Skill s = c.skills().get(i);
                    boolean open = pr.level >= s.level();
                    sk.add((i == pr.skillSel ? "&e▶ " : "&7• ") + (open ? "&f" : "&8") + s.name()
                            + " &8(" + s.mana() + " mana" + (open ? "" : ", Lv " + s.level()) + ")");
                }
                sk.add("");
                sk.add("&8Tombol F = pakai skill, Shift+F = ganti skill");
                sk.add("&8atau /skill <nomor>. Klik untuk ganti skill.");
            }
            inv.setItem(16, Text.item(Material.BLAZE_POWDER, "&cSkill", sk));
        }

        @Override
        public void click(int slot, boolean shift) {
            switch (slot) {
                case 11 -> new ClassMenu(core, player).open();
                case 12 -> new StatsMenu(core, player).open();
                case 13 -> new QuestMenu(core, player).open();
                case 14 -> {
                    player.closeInventory();
                    Commands.showStory(core, player);
                }
                case 15 -> new RankMenu(core, player).open();
                case 16 -> {
                    core.plugin.skills().cycle(player);
                    render();
                }
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------ class

    public static final class ClassMenu extends Base {
        private final List<Defs.Clazz> list = new ArrayList<>();
        private String confirm = "";
        private int start;

        public ClassMenu(Core core, Player p) {
            super(core, p);
            list.addAll(core.classes.values());
            start = 9 + Math.max(0, (9 - list.size()) / 2);
        }

        @Override
        protected String title() {
            return t("class", "&8Pilih Class");
        }

        @Override
        protected int size() {
            return 27;
        }

        @Override
        protected void render() {
            fill();
            Profile pr = core.profile(player);
            for (int i = 0; i < list.size() && start + i < 18; i++) {
                Defs.Clazz c = list.get(i);
                List<String> lore = new ArrayList<>(c.desc());
                lore.add("");
                lore.add("&6Buff permanen:");
                lore.addAll(c.buffLore());
                lore.add("");
                lore.add("&6Skill:");
                for (Defs.Skill s : c.skills()) {
                    lore.add("&7• &f" + s.name() + " &8(Lv " + s.level() + ", " + s.mana() + " mana) &7" + s.desc());
                }
                List<String> w = core.weaponList(c.key());
                lore.add("");
                if (w.isEmpty()) {
                    lore.add("&6Senjata: &7semua item");
                } else {
                    lore.add("&6Senjata:");
                    for (String e : w) {
                        String[] parts = e.split("\\|", 2);
                        lore.add("&7• &f" + Text.pretty(parts[0]) + (parts.length > 1 && !parts[1].isEmpty() ? " &8\"" + parts[1] + "\"" : ""));
                    }
                }
                lore.add("");
                boolean cur = c.key().equals(pr.cls);
                lore.add(cur ? "&aClass kamu sekarang" : (pr.cls == null ? "&eKlik untuk memilih" : "&eKlik untuk ganti class"));
                ItemStack it = Text.item(c.icon(), c.name(), lore);
                inv.setItem(start + i, cur ? Text.glow(it) : it);
            }
            addBack();
        }

        @Override
        public void click(int slot, boolean shift) {
            int i = slot - start;
            if (i < 0 || i >= list.size()) {
                return;
            }
            Defs.Clazz c = list.get(i);
            Profile pr = core.profile(player);
            if (c.key().equals(pr.cls)) {
                return;
            }
            boolean change = pr.cls != null;
            if (change) {
                if (!core.plugin.getConfig().getBoolean("class-change.allowed", true)) {
                    core.msg(player, "&cGanti class dinonaktifkan.");
                    return;
                }
                long cd = core.plugin.getConfig().getLong("class-change.cooldown-hours", 24) * 3_600_000L;
                long left = pr.classChangedAt + cd - System.currentTimeMillis();
                if (left > 0) {
                    core.msg(player, "&cGanti class bisa lagi dalam &f" + Text.time(left) + "&c.");
                    return;
                }
                if (!c.key().equals(confirm)) {
                    confirm = c.key();
                    core.msg(player, "&eKlik sekali lagi untuk ganti ke " + c.name() + "&e.");
                    return;
                }
            }
            core.setClass(player, c.key());
            if (change) {
                core.runConsole(player, core.plugin.getConfig().getStringList("class-change.commands"));
            }
            core.msg(player, "&aKamu sekarang " + c.name() + "&a! Atur senjata classmu dan pakai skill dengan tombol F.");
            player.closeInventory();
        }
    }

    // ------------------------------------------------------------ quest

    public static final class QuestMenu extends Base {
        private final List<Defs.Quest> shown = new ArrayList<>();
        private int page;

        public QuestMenu(Core core, Player p) {
            super(core, p);
        }

        @Override
        protected String title() {
            return t("quests", "&8Papan Quest");
        }

        @Override
        protected int size() {
            return 54;
        }

        @Override
        protected int backSlot() {
            return 48;
        }

        @Override
        protected void render() {
            fill();
            Profile pr = core.profile(player);
            core.checkDay(pr);
            List<Defs.Quest> list = core.tracker.eligible(pr);
            int pages = Math.max(1, (list.size() + 44) / 45);
            page = Math.max(0, Math.min(page, pages - 1));
            shown.clear();
            for (int i = 0; i < 45; i++) {
                int idx = page * 45 + i;
                if (idx >= list.size()) {
                    inv.setItem(i, null);
                    continue;
                }
                Defs.Quest q = list.get(idx);
                shown.add(q);
                inv.setItem(i, questItem(pr, q));
            }
            inv.setItem(45, Text.item(Material.ARROW, "&7Halaman sebelumnya"));
            inv.setItem(53, Text.item(Material.ARROW, "&7Halaman berikutnya"));
            inv.setItem(49, Text.item(Material.CLOCK, "&6Quest harian",
                    "&7Halaman &f" + (page + 1) + "&7/&f" + pages,
                    "&7Aktif: &f" + pr.active.size() + "&7/&f" + core.plugin.getConfig().getInt("quests.max-active", 3),
                    "&7Selesai hari ini: &f" + pr.completedToday + "&7/&f" + core.plugin.getConfig().getInt("quests.per-day-limit", 6),
                    "&7Reset tengah malam."));
            addBack();
        }

        private ItemStack questItem(Profile pr, Defs.Quest q) {
            Defs.Rank r = core.rank(q.rank());
            List<String> lore = new ArrayList<>();
            lore.add("&7Rank: " + (r == null ? "?" : r.tag()) + (q.challenge() ? " &c(Tantangan)" : ""));
            lore.add("&7Tugas: &f" + q.obj().describe());
            lore.add("");
            lore.add("&3Hadiah:");
            lore.addAll(q.lore());
            if (q.lore().isEmpty()) {
                lore.add(" &b" + q.gp() + " GP");
            }
            lore.add("");
            boolean done = pr.done.contains(q.id());
            boolean act = pr.active.containsKey(q.id());
            if (done) {
                lore.add("&a✔ Selesai hari ini");
                return Text.item(q.icon(), "&a✔ " + Text.strip(q.name()), lore);
            }
            if (act) {
                if (q.obj().type() == Objective.Type.GET) {
                    Material m = Material.matchMaterial(q.obj().target());
                    int have = m == null ? 0 : core.tracker.count(player, m);
                    lore.add("&7Di tasmu: &f" + Math.min(have, q.obj().amount()) + "&7/&f" + q.obj().amount());
                    lore.add("&eKlik untuk menyerahkan item");
                } else {
                    lore.add("&7Progres: &f" + pr.active.get(q.id()) + "&7/&f" + q.obj().amount());
                }
                lore.add("&8Shift+klik untuk membatalkan");
                return Text.glow(Text.item(q.icon(), q.name(), lore));
            }
            lore.add("&eKlik untuk mengambil quest");
            return Text.item(q.icon(), q.name(), lore);
        }

        @Override
        public void click(int slot, boolean shift) {
            if (slot == 45) {
                page--;
            } else if (slot == 53) {
                page++;
            } else if (slot >= 0 && slot < shown.size()) {
                Defs.Quest q = shown.get(slot);
                Profile pr = core.profile(player);
                if (pr.active.containsKey(q.id())) {
                    if (shift) {
                        core.tracker.abandon(player, q.id());
                    } else {
                        core.tracker.turnInQuest(player, q.id());
                    }
                } else {
                    core.tracker.accept(player, q.id());
                }
            }
            render();
        }
    }

    // ------------------------------------------------------------ stat

    public static final class StatsMenu extends Base {
        private static final String[] KEYS = Profile.STATS;
        private static final Material[] ICONS = {Material.IRON_SWORD, Material.FEATHER, Material.ENCHANTED_BOOK,
                Material.GOLDEN_APPLE, Material.EMERALD};
        private static final String[] NAMES = {"&cSTR &7(Kekuatan)", "&aDEX &7(Ketangkasan)", "&9INT &7(Kecerdasan)",
                "&dVIT &7(Vitalitas)", "&6LUK &7(Keberuntungan)"};

        public StatsMenu(Core core, Player p) {
            super(core, p);
        }

        @Override
        protected String title() {
            return t("stats", "&8Stat Karakter");
        }

        @Override
        protected int size() {
            return 27;
        }

        private String effect(String key) {
            var c = core.plugin.getConfig();
            return switch (key) {
                case "str" -> "&7+" + c.getDouble("stats.str-melee-damage-percent", 1.0) + "% damage jarak dekat";
                case "dex" -> "&7+" + c.getDouble("stats.dex-crit-chance-percent", 0.5) + "% crit, +"
                        + c.getDouble("stats.dex-bow-damage-percent", 1.0) + "% damage panah";
                case "int" -> "&7+" + c.getDouble("stats.int-mana-per-point", 5) + " mana, +"
                        + c.getDouble("stats.int-skill-power-percent", 2.0) + "% kekuatan skill";
                case "vit" -> "&7+" + c.getDouble("stats.vit-health-per-point", 1.0) + " HP maksimal";
                default -> "&7+" + c.getDouble("stats.luk-reward-chance-percent", 0.5) + "% peluang hadiah x1.5";
            };
        }

        @Override
        protected void render() {
            fill();
            Profile pr = core.profile(player);
            for (int i = 0; i < KEYS.length; i++) {
                inv.setItem(10 + i, Text.item(ICONS[i], NAMES[i], "&7Nilai: &f" + pr.stat(KEYS[i]),
                        effect(KEYS[i]), "", "&eKlik: +1 poin", "&eShift+klik: +5 poin"));
            }
            inv.setItem(22, Text.item(Material.NETHER_STAR, "&6Poin tersedia: &f" + pr.statPoints,
                    "&7Level &f" + pr.level, "&7Dapat poin tiap naik level."));
            addBack();
        }

        @Override
        public void click(int slot, boolean shift) {
            int i = slot - 10;
            if (i < 0 || i >= KEYS.length) {
                return;
            }
            Profile pr = core.profile(player);
            int add = Math.min(shift ? 5 : 1, pr.statPoints);
            if (add <= 0) {
                core.msg(player, "&cTidak ada poin stat.");
                return;
            }
            pr.statPoints -= add;
            pr.stats.merge(KEYS[i], add, Integer::sum);
            core.applyBuffs(player);
            render();
        }
    }

    // ------------------------------------------------------------ rank guild

    public static final class RankMenu extends Base {
        public RankMenu(Core core, Player p) {
            super(core, p);
        }

        @Override
        protected String title() {
            return t("rank", "&8Guild Petualang");
        }

        @Override
        protected int size() {
            return 27;
        }

        @Override
        public void open() {
            super.open();
            core.tracker.talk(player, "guild");
        }

        @Override
        protected void render() {
            fill();
            Profile pr = core.profile(player);
            Defs.Rank cur = core.rank(pr.rank);
            Defs.Rank next = core.rank(pr.rank + 1);
            long now = System.currentTimeMillis();
            inv.setItem(11, Text.item(Material.NETHER_STAR, cur.label(), "&7Rank petualangmu",
                    "&7GP: &b" + pr.gp, "",
                    "&8Quest: rank ini, semua di bawahnya,",
                    "&8dan quest tantangan satu tingkat di atas."));
            if (next == null) {
                inv.setItem(13, Text.item(Material.BOOK, "&6Rank tertinggi", "&7Kamu di puncak guild."));
                inv.setItem(15, Text.item(Material.DRAGON_EGG, "&6Legenda Guild"));
            } else {
                inv.setItem(13, Text.item(Material.BOOK, "&eRank berikutnya", "&7Rank: " + next.label(),
                        "&7GP: " + (pr.gp >= next.gp() ? "&a" : "&c") + pr.gp + "&7/&f" + next.gp(),
                        "&7Ujian: &f" + (next.exam() == null ? "tanpa ujian" : next.desc())));
                inv.setItem(15, action(pr, next, now));
            }
            List<String> ladder = new ArrayList<>();
            for (Defs.Rank r : core.ranks) {
                String mark = r.index() < pr.rank ? "&a✔ " : (r.index() == pr.rank ? "&e▶ " : "&8• ");
                ladder.add(mark + r.label() + (r.index() == 0 ? "" : " &8(" + r.gp() + " GP)"));
            }
            inv.setItem(22, Text.item(Material.BOOKSHELF, "&bDaftar rank", ladder));
            addBack();
        }

        private ItemStack action(Profile pr, Defs.Rank next, long now) {
            Objective ex = next.exam();
            if (pr.examTarget >= 0) {
                List<String> lore = new ArrayList<>();
                lore.add("&7" + next.desc());
                if (ex != null && ex.type() != Objective.Type.MANUAL) {
                    lore.add("&7Progres: &f" + pr.examProgress + "&7/&f" + ex.amount());
                }
                if (pr.examDeadline > 0) {
                    lore.add("&7Sisa waktu: &c" + Text.time(pr.examDeadline - now));
                }
                if (ex != null && ex.type() == Objective.Type.GET) {
                    lore.add("&eKlik untuk menyerahkan item");
                }
                lore.add("&8Batalkan: /rank abandon");
                return Text.item(Material.CLOCK, "&eUjian berjalan", lore);
            }
            if (pr.examCooldown > now) {
                return Text.item(Material.BARRIER, "&cJeda ujian", "&7Ulang dalam &f" + Text.time(pr.examCooldown - now));
            }
            if (pr.gp < next.gp()) {
                return Text.item(Material.RED_CONCRETE, "&cGP belum cukup",
                        "&7Butuh &f" + next.gp() + " &7GP, kamu punya &f" + pr.gp,
                        "&8Kerjakan quest harian untuk GP.");
            }
            boolean direct = ex == null || ex.type() == Objective.Type.NONE;
            return Text.item(Material.LIME_CONCRETE, direct ? "&aNaik rank" : "&aMulai ujian",
                    "&7Menuju " + next.label(), "&7" + next.desc(), "", "&eKlik");
        }

        @Override
        public void click(int slot, boolean shift) {
            if (slot != 15) {
                return;
            }
            Profile pr = core.profile(player);
            if (pr.examTarget >= 0) {
                Defs.Rank t = core.rank(pr.examTarget);
                if (t != null && t.exam() != null && t.exam().type() == Objective.Type.GET) {
                    if (core.tracker.take(player, t.exam())) {
                        core.completeExam(player);
                    } else {
                        core.msg(player, "&cItem belum cukup: &f" + t.exam().describe());
                    }
                }
            } else {
                core.startExam(player);
            }
            render();
        }
    }
}
