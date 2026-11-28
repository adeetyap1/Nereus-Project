package com.nereus.rpg;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GUI quest 54 slot, tema dark fantasy beraksen emas. Lima tampilan: Available, Active, Completed, History, Details.
 * Border = Black Stained Glass Pane, separator = Gray Stained Glass Pane, navigasi = Arrow. Hanya chest biasa,
 * jadi berjalan di Java dan Bedrock (Geyser).
 */
public final class QuestUi {
    private QuestUi() {
    }

    enum Mode { AVAILABLE, ACTIVE, COMPLETED, HISTORY, DETAILS }

    private static final String[] TIER_COLOR = {"&7", "&f", "&a", "&b", "&9", "&6", "&c&l"};
    private static final int PER_PAGE_AVAILABLE = 18;
    private static final int PER_PAGE_LIST = 36;

    public static final class Menu extends Menus.Base {
        private Mode mode = Mode.AVAILABLE;
        private Mode backMode = Mode.AVAILABLE;
        private int page;
        private int catPage;
        private String cat = "";
        private String detail = "";
        private final Map<Integer, String> slotQuest = new HashMap<>();

        public Menu(Core core, Player p) {
            super(core, p);
        }

        @Override
        protected String title() {
            return "&8✦ &6Papan Quest &8✦";
        }

        @Override
        protected int size() {
            return 54;
        }

        private static ItemStack pane(Material m) {
            return Text.item(m, " ");
        }

        private void refresh() {
            inv.clear();
            render();
        }

        // ------------------------------------------------------------ render

        @Override
        protected void render() {
            slotQuest.clear();
            Profile pr = core.profile(player);
            core.checkDay(pr);
            ItemStack black = pane(Material.BLACK_STAINED_GLASS_PANE);
            for (int i = 0; i < 9; i++) {
                inv.setItem(i, black);
                inv.setItem(45 + i, black);
            }
            tabs(pr);
            addBack();
            switch (mode) {
                case AVAILABLE -> renderAvailable(pr);
                case ACTIVE -> renderActive(pr);
                case COMPLETED -> renderCompleted(pr);
                case HISTORY -> renderHistory(pr);
                case DETAILS -> renderDetails(pr);
            }
        }

        private void tabs(Profile pr) {
            inv.setItem(1, tab(Material.BOOK, "&6Quest Tersedia", "&7Quest yang bisa kamu ambil.", Mode.AVAILABLE));
            inv.setItem(2, tab(Material.CLOCK, "&eQuest Aktif &8(" + pr.active.size() + ")",
                    "&7Quest yang sedang berjalan.", Mode.ACTIVE));
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(player);
                sm.displayName(Text.c("&6" + player.getName()));
                List<String> l = new ArrayList<>();
                Defs.Rank rk = core.activeRank(pr);
                l.add("&7Rank: &f" + (rk == null ? "-" : core.tag(rk, null)));
                l.add("&7Level: &f" + pr.level + " &8| &7GP: &b" + pr.gp);
                l.add("&7Quest selesai: &f" + pr.totalQuests);
                l.add("&8Tanpa batas jumlah quest.");
                l.add("&8Cooldown " + Text.time(core.cooldownMillis("x")) + " per ID quest.");
                sm.lore(l.stream().map(Text::c).toList());
                head.setItemMeta(sm);
            }
            inv.setItem(4, head);
            inv.setItem(3, tab(Material.EMERALD, "&aQuest Selesai &8(" + pr.completedQuestIds.size() + ")",
                    "&7Quest yang pernah kamu selesaikan.", Mode.COMPLETED));
            inv.setItem(5, tab(Material.WRITABLE_BOOK, "&dRiwayat Quest", "&7Aktivitas terakhirmu.", Mode.HISTORY));
        }

        private ItemStack tab(Material m, String name, String lore, Mode target) {
            ItemStack it = Text.item(m, name, lore, "", mode == target ? "&aTab ini sedang dibuka" : "&eKlik untuk membuka");
            return mode == target ? Text.glow(it) : it;
        }

        // --- available

        private List<String> catEntries() {
            List<String> l = new ArrayList<>();
            l.add("");
            l.addAll(core.questCats.keySet());
            return l;
        }

        private void renderAvailable(Profile pr) {
            List<String> entries = catEntries();
            int pages = (entries.size() + 6) / 7;
            catPage = Math.max(0, Math.min(catPage, pages - 1));
            for (int i = 0; i < 9; i++) {
                inv.setItem(9 + i, pane(Material.BLACK_STAINED_GLASS_PANE));
            }
            if (catPage > 0) {
                inv.setItem(9, Text.item(Material.ARROW, "&eKategori sebelumnya"));
            }
            if (catPage < pages - 1) {
                inv.setItem(17, Text.item(Material.ARROW, "&eKategori berikutnya"));
            }
            for (int i = 0; i < 7; i++) {
                int idx = catPage * 7 + i;
                if (idx >= entries.size()) {
                    break;
                }
                String key = entries.get(idx);
                boolean sel = key.equals(cat);
                ItemStack it;
                if (key.isEmpty()) {
                    it = Text.item(Material.NETHER_STAR, "&6Semua Kategori", "&7Tampilkan semua quest.", "",
                            sel ? "&aDipilih" : "&eKlik untuk memilih");
                } else {
                    Defs.QuestCat qc = core.questCats.get(key);
                    it = Text.item(qc.icon(), qc.name(), "&7" + qc.desc(), "", sel ? "&aDipilih" : "&eKlik untuk memilih");
                }
                inv.setItem(10 + i, sel ? Text.glow(it) : it);
            }
            ItemStack gray = pane(Material.GRAY_STAINED_GLASS_PANE);
            for (int i = 18; i < 27; i++) {
                inv.setItem(i, gray);
            }
            List<Defs.Quest> list = new ArrayList<>();
            for (Defs.Quest q : core.tracker.eligible(pr)) {
                if (cat.isEmpty() || q.category().equals(cat)) {
                    list.add(q);
                }
            }
            int total = Math.max(1, (list.size() + PER_PAGE_AVAILABLE - 1) / PER_PAGE_AVAILABLE);
            page = Math.max(0, Math.min(page, total - 1));
            for (int i = 0; i < PER_PAGE_AVAILABLE; i++) {
                int idx = page * PER_PAGE_AVAILABLE + i;
                if (idx >= list.size()) {
                    break;
                }
                int slot = 27 + i;
                inv.setItem(slot, questItem(list.get(idx), pr));
                slotQuest.put(slot, list.get(idx).id());
            }
            if (list.isEmpty()) {
                inv.setItem(31, Text.item(Material.BARRIER, "&cTidak ada quest", "&7Naikkan rank untuk membuka quest tier lebih tinggi."));
            }
            pager(total);
        }

        // --- active / completed / history

        private void renderActive(Profile pr) {
            List<Defs.Quest> list = new ArrayList<>();
            for (String id : pr.active.keySet()) {
                Defs.Quest q = core.quests.get(id);
                if (q != null) {
                    list.add(q);
                }
            }
            listPage(list, pr, "&7Belum ada quest aktif. Ambil di tab Quest Tersedia.");
        }

        private void renderCompleted(Profile pr) {
            List<Defs.Quest> list = new ArrayList<>();
            for (String id : pr.completedQuestIds) {
                Defs.Quest q = core.quests.get(id);
                if (q != null) {
                    list.add(q);
                }
            }
            listPage(list, pr, "&7Belum ada quest yang selesai.");
        }

        private void listPage(List<Defs.Quest> list, Profile pr, String emptyMsg) {
            int total = Math.max(1, (list.size() + PER_PAGE_LIST - 1) / PER_PAGE_LIST);
            page = Math.max(0, Math.min(page, total - 1));
            for (int i = 0; i < PER_PAGE_LIST; i++) {
                int idx = page * PER_PAGE_LIST + i;
                if (idx >= list.size()) {
                    break;
                }
                int slot = 9 + i;
                inv.setItem(slot, questItem(list.get(idx), pr));
                slotQuest.put(slot, list.get(idx).id());
            }
            if (list.isEmpty()) {
                inv.setItem(22, Text.item(Material.BARRIER, "&cKosong", emptyMsg));
            }
            pager(total);
        }

        private void renderHistory(Profile pr) {
            List<String> h = new ArrayList<>(pr.history);
            java.util.Collections.reverse(h);
            int total = Math.max(1, (h.size() + PER_PAGE_LIST - 1) / PER_PAGE_LIST);
            page = Math.max(0, Math.min(page, total - 1));
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd MMM HH:mm", Locale.forLanguageTag("id"));
            for (int i = 0; i < PER_PAGE_LIST; i++) {
                int idx = page * PER_PAGE_LIST + i;
                if (idx >= h.size()) {
                    break;
                }
                String[] parts = h.get(idx).split("\\|", 3);
                String when = parts.length > 0 ? parts[0] : "";
                try {
                    when = fmt.format(new java.util.Date(Long.parseLong(parts[0])));
                } catch (NumberFormatException ignored) {
                    // tampilkan apa adanya
                }
                String kind = parts.length > 1 ? parts[1] : "";
                String what = parts.length > 2 ? parts[2] : "";
                Material m = switch (kind) {
                    case "quest" -> Material.EMERALD;
                    case "story" -> Material.ENCHANTED_BOOK;
                    case "rank" -> Material.NETHER_STAR;
                    default -> Material.PAPER;
                };
                inv.setItem(9 + i, Text.item(m, "&f" + Text.strip(what), "&8" + when, "&7Jenis: &f" + kind));
            }
            if (h.isEmpty()) {
                inv.setItem(22, Text.item(Material.BARRIER, "&cKosong", "&7Belum ada riwayat."));
            }
            pager(total);
        }

        private void pager(int total) {
            if (page > 0) {
                inv.setItem(46, Text.item(Material.ARROW, "&eHalaman sebelumnya"));
            }
            inv.setItem(49, Text.item(Material.PAPER, "&7Halaman &f" + (page + 1) + "&7/&f" + total));
            if (page < total - 1) {
                inv.setItem(52, Text.item(Material.ARROW, "&eHalaman berikutnya"));
            }
        }

        // --- item quest

        private String tierTag(Defs.Quest q) {
            String t = Core.RANK_ORDER.substring(q.rank(), q.rank() + 1);
            return TIER_COLOR[Math.min(q.rank(), TIER_COLOR.length - 1)] + "[" + t + "]";
        }

        private List<String> rewardLines(Defs.Quest q) {
            List<String> l = new ArrayList<>();
            l.add("&6Hadiah:");
            if (q.legacy()) {
                l.add(" &a+" + q.exp() + " EXP &8| &b+" + q.gp() + " GP" + (q.commands().isEmpty() ? "" : " &8| &7item/gold"));
            } else {
                l.add(" &6" + q.nr() + " NR &8| &a" + q.exp() + " EXP &8| &b" + q.gp() + " GP");
            }
            return l;
        }

        private ItemStack questItem(Defs.Quest q, Profile pr) {
            Objective o = q.obj();
            boolean active = pr.active.containsKey(q.id());
            int prog = active ? core.tracker.progressOf(player, pr, q) : 0;
            boolean done = active && prog >= o.amount();
            long cd = active ? 0 : core.cooldownLeft(pr, q.id());
            List<String> lore = new ArrayList<>();
            lore.add("&8" + (q.legacy() ? "Quest lama" : Text.strip(core.categoryName(q.category()))) + " &8| Tier &f"
                    + Core.RANK_ORDER.charAt(q.rank()));
            if (!q.desc().isEmpty()) {
                lore.add("&7" + q.desc());
            }
            lore.add("");
            lore.add("&6Misi: &f" + o.describe());
            if (active) {
                lore.add(Text.bar(prog, o.amount(), 20, "&a", "&7") + " &f" + prog + "/" + o.amount()
                        + " &8(" + (int) Math.round(100.0 * prog / o.amount()) + "%)");
            }
            lore.addAll(rewardLines(q));
            lore.add("");
            if (done) {
                lore.add("&a✔ Selesai! Klik untuk klaim hadiah.");
            } else if (active) {
                lore.add("&e⏳ Sedang berjalan. Klik untuk detail.");
            } else if (cd > 0) {
                lore.add("&c✖ Cooldown: &f" + Text.time(cd));
            } else {
                lore.add("&aKlik untuk melihat detail dan mengambil.");
            }
            String name = tierTag(q) + " &f" + Text.strip(q.name());
            ItemStack it = Text.item(cd > 0 && !active ? Material.GRAY_DYE : q.icon(), name, lore);
            return done ? Text.glow(it) : it;
        }

        // --- detail

        private void renderDetails(Profile pr) {
            Defs.Quest q = core.quests.get(detail);
            ItemStack gray = pane(Material.GRAY_STAINED_GLASS_PANE);
            for (int i = 9; i < 45; i++) {
                inv.setItem(i, gray);
            }
            if (q == null) {
                inv.setItem(22, Text.item(Material.BARRIER, "&cQuest tidak ditemukan"));
                inv.setItem(45, Text.item(Material.ARROW, "&eKembali ke daftar"));
                return;
            }
            Objective o = q.obj();
            boolean active = pr.active.containsKey(q.id());
            int prog = active ? core.tracker.progressOf(player, pr, q) : 0;
            boolean done = active && prog >= o.amount();
            long cd = core.cooldownLeft(pr, q.id());
            int pct = (int) Math.round(100.0 * prog / o.amount());

            inv.setItem(13, Text.item(q.icon(), tierTag(q) + " &f" + Text.strip(q.name()),
                    "&8Kategori: &f" + (q.legacy() ? "Quest lama" : Text.strip(core.categoryName(q.category()))),
                    "&8Tier: &f" + Core.RANK_ORDER.charAt(q.rank())));
            inv.setItem(20, Text.item(Material.BOOK, "&6Deskripsi",
                    q.desc().isEmpty() ? "&7-" : "&7" + q.desc(), "", "&6Objective:", "&f" + o.describe()));
            inv.setItem(22, Text.item(Material.CLOCK, "&eProgress",
                    active ? Text.bar(prog, o.amount(), 20, "&a", "&7") : "&7Belum diambil",
                    active ? "&f" + prog + "/" + o.amount() + " &8(" + pct + "%)" : "&7Target: &f" + o.amount(),
                    o.type() == Objective.Type.GET ? "&8Item diserahkan saat klaim." : "&8Dihitung otomatis."));
            List<String> rl = new ArrayList<>(rewardLines(q));
            rl.add("");
            rl.add("&8EXP, Nerith, dan Guild Point diberikan saat klaim.");
            inv.setItem(24, Text.item(Material.GOLD_INGOT, "&6Hadiah", rl));

            if (done) {
                inv.setItem(40, Text.glow(Text.item(Material.EMERALD_BLOCK, "&aKlaim Hadiah", "&7Terima hadiah quest ini.")));
            } else if (active) {
                inv.setItem(40, Text.item(Material.CLOCK, "&eQuest Berjalan", "&7Selesaikan dulu: &f" + pct + "%"));
                inv.setItem(42, Text.item(Material.RED_DYE, "&cBatalkan Quest",
                        "&7Progres hilang. Cooldown tetap berjalan.", "&eKlik untuk membatalkan"));
            } else if (q.legacy()) {
                inv.setItem(40, Text.item(Material.BARRIER, "&cQuest lama", "&7Tidak bisa diambil lagi."));
            } else if (cd > 0) {
                inv.setItem(40, Text.item(Material.GRAY_DYE, "&cCooldown", "&7Bisa diambil lagi dalam &f" + Text.time(cd),
                        "&7Quest lain tetap bebas diambil."));
            } else {
                inv.setItem(40, Text.item(Material.LIME_DYE, "&aAmbil Quest", "&7Cooldown " + Text.time(core.cooldownMillis(q.id()))
                        + " setelah diambil.", "&eKlik untuk mengambil"));
            }
            inv.setItem(45, Text.item(Material.ARROW, "&eKembali ke daftar"));
        }

        // ------------------------------------------------------------ klik

        @Override
        public void click(int slot, boolean shift) {
            Profile pr = core.profile(player);
            switch (slot) {
                case 1 -> switchTo(Mode.AVAILABLE);
                case 2 -> switchTo(Mode.ACTIVE);
                case 3 -> switchTo(Mode.COMPLETED);
                case 5 -> switchTo(Mode.HISTORY);
                default -> {
                    if (mode == Mode.DETAILS) {
                        clickDetails(slot, pr);
                    } else {
                        clickList(slot);
                    }
                }
            }
        }

        private void switchTo(Mode m) {
            mode = m;
            page = 0;
            refresh();
        }

        private void clickList(int slot) {
            if (mode == Mode.AVAILABLE) {
                List<String> entries = catEntries();
                if (slot == 9 && catPage > 0) {
                    catPage--;
                    refresh();
                    return;
                }
                if (slot == 17 && catPage < (entries.size() + 6) / 7 - 1) {
                    catPage++;
                    refresh();
                    return;
                }
                if (slot >= 10 && slot <= 16) {
                    int idx = catPage * 7 + (slot - 10);
                    if (idx < entries.size()) {
                        cat = entries.get(idx);
                        page = 0;
                        refresh();
                    }
                    return;
                }
            }
            if (slot == 46 && page > 0) {
                page--;
                refresh();
            } else if (slot == 52 && inv.getItem(52) != null && inv.getItem(52).getType() == Material.ARROW) {
                page++;
                refresh();
            } else if (slotQuest.containsKey(slot)) {
                detail = slotQuest.get(slot);
                backMode = mode;
                mode = Mode.DETAILS;
                refresh();
            }
        }

        private void clickDetails(int slot, Profile pr) {
            if (slot == 45) {
                mode = backMode == Mode.DETAILS ? Mode.AVAILABLE : backMode;
                refresh();
                return;
            }
            Defs.Quest q = core.quests.get(detail);
            if (q == null) {
                return;
            }
            if (slot == 40) {
                if (pr.active.containsKey(detail)) {
                    if (core.tracker.progressOf(player, pr, q) >= q.obj().amount()) {
                        core.tracker.claim(player, detail);
                    }
                } else {
                    core.tracker.accept(player, detail);
                }
                refresh();
            } else if (slot == 42 && pr.active.containsKey(detail)) {
                core.tracker.abandon(player, detail);
                refresh();
            }
        }
    }
}
