package com.nereus.rpg;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        protected Component titleComponent() {
            return Text.c(title());
        }

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

        /** Id font image ItemsAdder untuk latar menu, atau null untuk menu biasa. */
        protected String texturedImage() {
            return null;
        }

        public void open() {
            if (!(this instanceof HubMenu) && !core.member(player)) {
                core.guild.notMember(player);
                return;
            }
            String img = texturedImage();
            if (img != null) {
                var cfg = core.plugin.getConfig();
                Object w = IA.textured(this, size(), img, cfg.getInt("gui.title-offset", 0), cfg.getInt("gui.texture-offset", 0));
                Inventory in = w == null ? null : IA.internal(w);
                if (in != null) {
                    inv = in;
                    render();
                    if (IA.show(w, player)) {
                        return;
                    }
                }
                core.plugin.getLogger().warning("Menu bertekstur ItemsAdder gagal dibuka, memakai menu biasa.");
            }
            inv = Bukkit.createInventory(this, size(), titleComponent());
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
            return t("hub", "&8Nerus Guild");
        }

        @Override
        protected int size() {
            return 27;
        }

        @Override
        protected void render() {
            fill();
            Profile pr = core.profile(player);
            if (!pr.member()) {
                renderGate(pr);
                return;
            }
            Defs.Race c = core.clazz(pr);
            Defs.Rank r = core.activeRank(pr);
            long need = core.xpNeeded(pr.level);
            inv.setItem(10, Text.item(Material.NETHER_STAR, "&6" + player.getName(),
                    "&7Class: " + (c == null ? "&cbelum dipilih" : c.name()),
                    "&7Level: &f" + pr.level + " &8(" + pr.xp + "/" + need + " XP)",
                    "&7Rank guild: " + core.label(r, player),
                    "&7GP: &b" + pr.gp,
                    "&7Mana: &9" + (int) pr.mana + "&7/&9" + (int) core.maxMana(pr),
                    "&7Login beruntun: &f" + pr.streak + " hari"));
            inv.setItem(11, Text.item(c == null ? Material.BARRIER : c.icon(), "&eClass",
                    c == null ? "&7Klik untuk memilih ras" : "&7Klik untuk melihat / ganti ras"));
            inv.setItem(12, Text.item(Material.EXPERIENCE_BOTTLE, "&aStat Karakter",
                    "&7Poin tersedia: &f" + pr.statPoints, "&7Klik untuk membagi poin"));
            inv.setItem(13, Text.item(Material.WRITABLE_BOOK, "&bPapan Quest",
                    "&7Quest aktif: &f" + pr.active.size(),
                    "&7Tanpa batas jumlah quest."));
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
                sk.add("&8Klik kanan = pakai skill (busur: jongkok + klik kanan)");
                sk.add("&8Jongkok + klik kiri = ganti skill. Klik di sini juga ganti.");
            }
            inv.setItem(16, Text.item(Material.BLAZE_POWDER, "&cSkill", sk));
        }

        /** Tampilan untuk pemain UNREGISTERED / REVOKED. */
        private void renderGate(Profile pr) {
            var m = core.guild.msgs;
            if (pr.status == GuildStatus.REVOKED) {
                inv.setItem(13, Text.item(Material.BARRIER, m.get("gate-revoked-title"), m.list("gate-revoked-lore")));
                return;
            }
            inv.setItem(11, Text.item(Material.PLAYER_HEAD, m.get("gate-status-title"), m.list("gate-status-lore")));
            inv.setItem(13, Text.item(Material.WRITABLE_BOOK, m.get("gate-talk-title"), m.list("gate-talk-lore")));
            inv.setItem(15, Text.item(Material.BOOKSHELF, m.get("gate-ranks-title"), core.guild.rankLadderLore(null)));
        }

        @Override
        public void click(int slot, boolean shift) {
            Profile pr0 = core.profile(player);
            if (!pr0.member()) {
                if (slot == 13 && pr0.status == GuildStatus.UNREGISTERED) {
                    player.closeInventory();
                    core.guild.dialogue.open(player);
                }
                return;
            }
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
        private static final int[] TEX_SLOTS = {10, 13, 16};
        private final List<Defs.Race> list = new ArrayList<>();
        private String confirm = "";
        private int start;
        private final boolean tex;

        public ClassMenu(Core core, Player p) {
            super(core, p);
            list.addAll(core.classes.values());
            start = 9 + Math.max(0, (9 - list.size()) / 2);
            tex = core.plugin.getConfig().getBoolean("gui.itemsadder-class-menu", false)
                    && IA.present() && !IA.bedrock(p) && list.size() <= TEX_SLOTS.length;
        }

        private int slotOf(int i) {
            return tex ? TEX_SLOTS[i] : start + i;
        }

        @Override
        protected String texturedImage() {
            return tex ? "nereus_ui:class_select" : null;
        }

        @Override
        protected int backSlot() {
            return tex ? 8 : super.backSlot();
        }
        @Override
        protected String title() {
            return t("class", "&8Pilih Ras");
        }

        @Override
        protected int size() {
            return 27;
        }

        @Override
        protected void render() {
            if (!tex) {
                fill();
            }
            Profile pr = core.profile(player);
            for (int i = 0; i < list.size() && (tex || start + i < 18); i++) {
                Defs.Race c = list.get(i);
                List<String> lore = new ArrayList<>(c.desc());
                lore.add("");
                lore.add("&6Kemampuan pasif:");
                for (Defs.Ability ab : c.abilities().values()) {
                    if (!ab.weakness()) {
                        lore.add("&a+ &f" + ab.name() + " &7" + ab.desc());
                    }
                }
                lore.add("&6Kekurangan:");
                for (Defs.Ability ab : c.abilities().values()) {
                    if (ab.weakness()) {
                        lore.add("&c- &f" + ab.name() + " &7" + ab.desc());
                    }
                }
                if (!c.skills().isEmpty()) {
                    lore.add("&6Skill aktif:");
                    for (Defs.Skill s : c.skills()) {
                        lore.add("&7• &f" + s.name() + " &8(Lv " + s.level() + ", " + s.mana() + " mana)");
                    }
                }
                lore.add("");
                boolean cur = c.key().equals(pr.cls);
                lore.add(cur ? "&aRas kamu sekarang" : (pr.cls == null ? "&eKlik untuk memilih" : "&eKlik untuk ganti ras"));
                ItemStack it = Text.item(c.icon(), c.name(), lore);
                inv.setItem(slotOf(i), cur ? Text.glow(it) : it);
            }
            addBack();
        }

        @Override
        public void click(int slot, boolean shift) {
            int i = -1;
            for (int k = 0; k < list.size(); k++) {
                if (slotOf(k) == slot) {
                    i = k;
                }
            }
            if (i < 0) {
                return;
            }
            Defs.Race c = list.get(i);
            Profile pr = core.profile(player);
            if (c.key().equals(pr.cls)) {
                return;
            }
            boolean change = pr.cls != null;
            if (change) {
                if (!core.plugin.getConfig().getBoolean("class-change.allowed", true)) {
                    core.msg(player, "&cGanti ras dinonaktifkan.");
                    return;
                }
                long cd = core.plugin.getConfig().getLong("class-change.cooldown-hours", 24) * 3_600_000L;
                long left = pr.classChangedAt + cd - System.currentTimeMillis();
                if (left > 0) {
                    core.msg(player, "&cGanti ras bisa lagi dalam &f" + Text.time(left) + "&c.");
                    return;
                }
                if (!c.key().equals(confirm)) {
                    confirm = c.key();
                    core.msg(player, "&eKlik sekali lagi untuk ganti ras ke " + c.name() + "&e.");
                    return;
                }
            }
            core.setClass(player, c.key());
            if (change) {
                core.runConsole(player, core.plugin.getConfig().getStringList("class-change.commands"));
            }
            core.msg(player, "&aKamu sekarang ras " + c.name() + "&a! Kemampuan pasifnya aktif otomatis.");
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
            List<String> info = new ArrayList<>();
            info.add("&7Halaman &f" + (page + 1) + "&7/&f" + pages);
            info.addAll(cooldownLines(pr));
            inv.setItem(49, Text.item(Material.CLOCK, "&6Papan Quest", info));
            addBack();
        }

        private List<String> cooldownLines(Profile pr) {
            List<String> out = new ArrayList<>();
            out.add("&7Quest aktif: &f" + pr.active.size() + " &8(tanpa batas)");
            boolean any = false;
            for (Map.Entry<String, Long> e : pr.questCooldowns.entrySet()) {
                long left = core.cooldownLeft(pr, e.getKey());
                if (left > 0) {
                    if (!any) {
                        out.add("&7Cooldown jenis quest:");
                        any = true;
                    }
                    out.add(" &c" + core.categoryName(e.getKey()) + " &8- &f" + Text.time(left));
                }
            }
            if (!any) {
                out.add("&aSemua jenis quest siap diambil.");
            }
            return out;
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
            boolean act = pr.active.containsKey(q.id());
            lore.add("&7Jenis: &f" + core.categoryName(q.category()));
            if (act) {
                if (q.obj().type() == Objective.Type.GET) {
                    int have = core.tracker.countFor(player, q.obj());
                    lore.add("&7Di tasmu: &f" + Math.min(have, q.obj().amount()) + "&7/&f" + q.obj().amount());
                    lore.add("&eKlik untuk menyerahkan item");
                } else {
                    lore.add("&7Progres: &f" + pr.active.get(q.id()) + "&7/&f" + q.obj().amount());
                }
                lore.add("&8Shift+klik untuk membatalkan");
                return Text.glow(Text.item(q.icon(), q.name(), lore));
            }
            long left = core.cooldownLeft(pr, q.category());
            if (left > 0) {
                lore.add("&cCooldown " + core.categoryName(q.category()) + ": &f" + Text.time(left));
                lore.add("&7Quest jenis lain tetap bisa diambil.");
                return Text.item(q.icon(), "&8✖ " + Text.strip(q.name()), lore);
            }
            lore.add("&eKlik untuk mengambil quest");
            lore.add("&8Cooldown jenis ini: " + Text.time(core.cooldownMillis(q.category())) + " setelah diambil");
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
        private static final Material[] ICONS = {Material.IRON_SWORD, Material.SHIELD, Material.GOLDEN_APPLE,
                Material.FEATHER, Material.ENCHANTED_BOOK, Material.EMERALD};
        private static final String[] NAMES = {"&cSTR &7(Kekuatan)", "&bDEF &7(Pertahanan)", "&dVIT &7(Vitalitas)",
                "&aAGI &7(Kelincahan)", "&9INT &7(Kecerdasan)", "&6LUK &7(Keberuntungan)"};

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
                case "str" -> "&7+" + c.getDouble("stats.str-melee-damage-percent", 0.8) + "% damage jarak dekat (maks +"
                        + c.getInt("stats.str-damage-cap-percent", 60) + "%)";
                case "def" -> "&7-" + c.getDouble("stats.def-reduction-percent", 0.5) + "% damage diterima (maks -"
                        + c.getInt("stats.def-reduction-cap-percent", 35) + "%)";
                case "vit" -> "&7+" + c.getDouble("stats.vit-health-per-point", 1.0) + " HP maksimal (maks +"
                        + c.getInt("stats.vit-health-cap", 40) + ")";
                case "agi" -> "&7+" + c.getDouble("stats.agi-speed-percent", 0.2) + "% kecepatan, +"
                        + c.getDouble("stats.agi-crit-chance-percent", 0.3) + "% crit, +"
                        + c.getDouble("stats.agi-bow-damage-percent", 0.5) + "% damage panah";
                case "int" -> "&7+" + c.getDouble("stats.int-mana-per-point", 5) + " mana, +"
                        + c.getDouble("stats.int-skill-power-percent", 2.0) + "% kekuatan skill";
                default -> "&7+" + c.getDouble("stats.luk-reward-chance-percent", 0.5) + "% hadiah quest x1.5, +"
                        + c.getDouble("stats.luk-luck-per-point", 0.05) + " Luck (loot)";
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
            inv.setItem(11, Text.item(Material.NETHER_STAR, core.label(cur, player), "&7Rank petualangmu",
                    "&7GP: &b" + pr.gp, "",
                    "&8Quest: rank ini, semua di bawahnya,",
                    "&8dan quest tantangan satu tingkat di atas."));
            if (next == null) {
                inv.setItem(13, Text.item(Material.BOOK, "&6Rank tertinggi", "&7Kamu di puncak guild."));
                inv.setItem(15, Text.item(Material.DRAGON_EGG, "&6Legenda Guild"));
            } else {
                List<String> need = new ArrayList<>();
                need.add("&7Rank: " + core.label(next, player));
                List<String> um = core.unmet(player, pr, next);
                if (um.isEmpty()) {
                    need.add("&aSemua syarat terpenuhi");
                } else {
                    need.add("&cSyarat belum terpenuhi:");
                    for (String u : um) {
                        need.add("&8 - &7" + u);
                    }
                }
                need.add("&7Ujian: &f" + (next.exam() == null ? "tanpa ujian" : next.desc()));
                inv.setItem(13, Text.item(Material.BOOK, "&eRank berikutnya", need));
                inv.setItem(15, action(pr, next, now));
            }
            inv.setItem(22, Text.item(Material.BOOKSHELF, "&bDaftar rank", core.guild.rankLadderLore(pr)));
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
            if (!core.unmet(player, pr, next).isEmpty()) {
                return Text.item(Material.RED_CONCRETE, "&cSyarat belum terpenuhi",
                        "&7Lihat syarat di buku sebelah.", "&8Kerjakan quest harian untuk GP dan EXP.");
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
