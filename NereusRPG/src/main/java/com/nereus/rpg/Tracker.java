package com.nereus.rpg;

import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Quest harian, misi cerita, progres ujian, dan pencapaian. */
public final class Tracker {
    private final Core core;

    public Tracker(Core core) {
        this.core = core;
    }

    // ------------------------------------------------------------ quest

    /** Konteks kejadian untuk syarat tambahan quest (cond). */
    public record Ctx(boolean bow, boolean melee, boolean night, boolean nether, boolean end, boolean underground,
                      boolean adult) {
        public static Ctx env(Player p) {
            var w = p.getWorld();
            var env = w.getEnvironment();
            return new Ctx(false, false, env == org.bukkit.World.Environment.NORMAL && !w.isDayTime(),
                    env == org.bukkit.World.Environment.NETHER, env == org.bukkit.World.Environment.THE_END,
                    p.getLocation().getY() < 50, true);
        }

        public Ctx weapon(boolean bow, boolean melee) {
            return new Ctx(bow, melee, night, nether, end, underground, adult);
        }

        public Ctx adult(boolean a) {
            return new Ctx(bow, melee, night, nether, end, underground, a);
        }

        public boolean ok(String cond) {
            return switch (cond == null ? "" : cond) {
                case "BOW" -> bow;
                case "MELEE" -> melee;
                case "NIGHT" -> night;
                case "NETHER" -> nether;
                case "END" -> end;
                case "UNDERGROUND" -> underground;
                case "ADULT" -> adult;
                default -> true;
            };
        }
    }

    /** Quest baru yang boleh diambil: tier pemain ke bawah, ditambah satu tier di atasnya. Quest lama tidak ditawarkan. */
    public List<Defs.Quest> eligible(Profile pr) {
        List<Defs.Quest> out = new ArrayList<>();
        for (Defs.Quest q : core.quests.values()) {
            if (!q.legacy() && q.rank() <= pr.rank + 1) {
                out.add(q);
            }
        }
        List<String> order = new ArrayList<>(core.questCats.keySet());
        out.sort((a, b) -> {
            int c = Integer.compare(order.indexOf(a.category()), order.indexOf(b.category()));
            return c != 0 ? c : Integer.compare(a.rank(), b.rank());
        });
        return out;
    }

    private static long statRaw(Player p, String name) {
        try {
            return p.getStatistic(org.bukkit.Statistic.valueOf(name));
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            return 0;
        }
    }

    private static long statScaled(String name, long delta) {
        if (name.endsWith("_ONE_CM")) {
            return delta / 100;
        }
        if (name.equals("TIME_SINCE_DEATH") || name.equals("PLAY_ONE_MINUTE")) {
            return delta / 1200;
        }
        return delta;
    }

    /** Progres saat ini: GET dihitung dari inventory (diserahkan saat klaim), STAT dari statistik, lainnya dari event. */
    public int progressOf(Player p, Profile pr, Defs.Quest q) {
        Integer stored = pr.active.get(q.id());
        if (stored == null) {
            return 0;
        }
        Objective o = q.obj();
        switch (o.type()) {
            case GET:
                return Math.min(o.amount(), countFor(p, o));
            case STAT: {
                long cur = statRaw(p, o.target());
                Long base = pr.questBase.get(q.id());
                if (base == null || (o.target().equals("TIME_SINCE_DEATH") && cur < base)) {
                    base = cur;
                    pr.questBase.put(q.id(), cur);
                }
                return (int) Math.min(o.amount(), statScaled(o.target(), Math.max(0, cur - base)));
            }
            default:
                return Math.min(stored, o.amount());
        }
    }

    public boolean isDone(Player p, Profile pr, Defs.Quest q) {
        return pr.active.containsKey(q.id()) && progressOf(p, pr, q) >= q.obj().amount();
    }

    public void accept(Player p, String id) {
        Profile pr = core.profile(p);
        if (!pr.member()) {
            core.guild.notMember(p);
            return;
        }
        core.checkDay(pr);
        Defs.Quest q = core.quests.get(id);
        if (q == null || q.legacy() || !eligible(pr).contains(q)) {
            core.msg(p, "&cQuest ini belum bisa kamu ambil.");
            return;
        }
        if (pr.active.containsKey(id)) {
            core.msg(p, "&cQuest ini sedang berjalan.");
            return;
        }
        // Tidak ada batas jumlah quest. Cooldown hanya berlaku untuk quest dengan ID yang sama.
        long left = core.cooldownLeft(pr, id);
        if (left > 0) {
            core.msg(p, "&cQuest ini masih cooldown: &f" + Text.time(left) + "&c. Quest lain tetap bisa diambil.");
            return;
        }
        pr.active.put(id, 0);
        if (q.obj().type() == Objective.Type.STAT) {
            pr.questBase.put(id, statRaw(p, q.obj().target()));
        }
        long cd = core.cooldownMillis(id);
        if (cd > 0) {
            pr.questCooldowns.put(id, System.currentTimeMillis() + cd);
        }
        core.msg(p, "&aQuest diambil: " + q.name() + " &8- &f" + q.obj().describe());
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    public void abandon(Player p, String id) {
        Profile pr = core.profile(p);
        if (!pr.member()) {
            return;
        }
        if (pr.active.remove(id) != null) {
            pr.questBase.remove(id);
            core.msg(p, "&7Quest dibatalkan. Cooldown quest ini tetap berjalan.");
        }
    }

    /** Klaim hadiah quest yang sudah selesai. Quest dihapus dari daftar aktif lebih dulu supaya tidak bisa dobel. */
    public void claim(Player p, String id) {
        Profile pr = core.profile(p);
        if (!pr.member()) {
            return;
        }
        Defs.Quest q = core.quests.get(id);
        if (q == null || !pr.active.containsKey(id)) {
            return;
        }
        if (progressOf(p, pr, q) < q.obj().amount()) {
            core.msg(p, "&cQuest belum selesai: &f" + q.obj().describe());
            return;
        }
        if (q.obj().type() == Objective.Type.GET && !take(p, q.obj())) {
            core.msg(p, "&cItem belum cukup: &f" + q.obj().describe());
            return;
        }
        if (pr.active.remove(id) == null) {
            return;
        }
        pr.questBase.remove(id);
        completeQuest(p, pr, q);
    }

    /** Dipakai quest lama bertipe GET. */
    public void turnInQuest(Player p, String id) {
        claim(p, id);
    }

    /** EXP quest (dipakai juga oleh tampilan hadiah). */
    public long questXp(Defs.Quest q) {
        return q.exp();
    }

    private void completeQuest(Player p, Profile pr, Defs.Quest q) {
        pr.active.remove(q.id());
        pr.done.add(q.id());
        pr.completedQuestIds.add(q.id());
        pr.questCounts.merge(q.id(), 1, Integer::sum);
        core.logHistory(pr, "quest", q.name());
        pr.completedToday++;
        pr.totalQuests++;
        p.showTitle(Title.title(Text.c("&aQuest Selesai"), Text.c(q.name())));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        core.rewardQuest(p, q);
        milestones(p, pr);
    }

    // ------------------------------------------------------------ progres

    public void progress(Player p, Objective.Type type, String target) {
        progress(p, type, target, 1, Ctx.env(p));
    }

    public void progress(Player p, Objective.Type type, String target, int amount, Ctx ctx) {
        Profile pr = core.profile(p);
        if (!pr.member() || amount <= 0) {
            return;
        }
        if ((p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR)
                && !core.plugin.getConfig().getBoolean("quests.count-creative", false)) {
            return;
        }
        core.checkDay(pr);

        for (String id : new ArrayList<>(pr.active.keySet())) {
            Defs.Quest q = core.quests.get(id);
            if (q == null || q.obj().type() != type || type == Objective.Type.GET || type == Objective.Type.STAT
                    || !q.obj().matches(target) || !ctx.ok(q.obj().cond())) {
                continue;
            }
            int before = pr.active.get(id);
            if (before >= q.obj().amount()) {
                continue;
            }
            int cur = Math.min(q.obj().amount(), before + amount);
            pr.active.put(id, cur);
            if (cur >= q.obj().amount()) {
                if (q.legacy()) {
                    pr.active.remove(id);
                    completeQuest(p, pr, q);
                } else {
                    core.msg(p, "&aQuest selesai: " + q.name() + " &7- buka &f/quest &7lalu klaim hadiahmu.");
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
                }
            }
        }

        Defs.Mission m = mission(pr);
        if (m != null && m.minRank() <= pr.rank && m.obj().type() == type && m.obj().matches(target)) {
            pr.storyProgress++;
            if (pr.storyProgress >= m.obj().amount()) {
                completeMission(p, pr, m);
            }
        }

        if (pr.examTarget >= 0) {
            Defs.Rank t = core.rank(pr.examTarget);
            if (t != null && t.exam() != null && t.exam().type() == type && t.exam().matches(target)) {
                pr.examProgress++;
                if (pr.examProgress >= t.exam().amount()) {
                    core.completeExam(p);
                }
            }
        }
    }

    public void kill(Player p, String entity, Ctx ctx) {
        Profile pr = core.profile(p);
        if (!pr.member()) {
            return;
        }
        pr.totalKills++;
        progress(p, Objective.Type.KILL, entity, 1, ctx);
        milestones(p, pr);
    }

    public void kill(Player p, String entity) {
        kill(p, entity, Ctx.env(p));
    }

    // ------------------------------------------------------------ misi cerita

    public Defs.Mission mission(Profile pr) {
        return pr.story >= 0 && pr.story < core.missions.size() ? core.missions.get(pr.story) : null;
    }

    public void turnInStory(Player p) {
        Profile pr = core.profile(p);
        if (!pr.member()) {
            core.guild.notMember(p);
            return;
        }
        Defs.Mission m = mission(pr);
        if (m == null || m.obj().type() != Objective.Type.GET || m.minRank() > pr.rank) {
            core.msg(p, "&cTidak ada misi yang perlu diserahkan.");
        } else if (take(p, m.obj())) {
            completeMission(p, pr, m);
        } else {
            core.msg(p, "&cItem belum cukup: &f" + m.obj().describe());
        }
    }

    public void talk(Player p, String target) {
        Profile pr = core.profile(p);
        if (!pr.member()) {
            return;
        }
        Defs.Mission m = mission(pr);
        if (m != null && m.minRank() <= pr.rank && m.obj().type() == Objective.Type.TALK
                && m.obj().matches(target)) {
            completeMission(p, pr, m);
        }
    }

    public void rankChanged(Player p) {
        Profile pr = core.profile(p);
        if (!pr.member()) {
            return;
        }
        for (int guard = 0; guard < 20; guard++) {
            Defs.Mission m = mission(pr);
            if (m == null || m.minRank() > pr.rank || m.obj().type() != Objective.Type.RANK
                    || pr.rank < core.rankIndex(m.obj().target())) {
                break;
            }
            completeMission(p, pr, m);
        }
    }

    private void completeMission(Player p, Profile pr, Defs.Mission m) {
        pr.story++;
        pr.storyProgress = 0;
        core.logHistory(pr, "story", m.title());
        p.showTitle(Title.title(Text.c("&dMisi Selesai"), Text.c(m.title())));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        core.reward(p, m.gp(), m.xp(), m.commands());
        Defs.Mission next = mission(pr);
        if (next == null) {
            core.msg(p, "&6Kisah utama tamat. Legendamu terus berlanjut!");
        } else if (next.minRank() > pr.rank) {
            core.msg(p, "&dMisi berikutnya: " + next.title() + " &7(butuh " + core.rank(next.minRank()).label() + "&7).");
        } else {
            core.msg(p, "&dMisi baru: " + next.title() + " &7- ketik &f/story");
        }
    }

    // ------------------------------------------------------------ pencapaian

    private void milestones(Player p, Profile pr) {
        check(p, pr, "quests-completed", pr.totalQuests);
        check(p, pr, "kills", pr.totalKills);
    }

    private void check(Player p, Profile pr, String cat, int value) {
        ConfigurationSection sec = core.plugin.getConfig().getConfigurationSection("milestones." + cat);
        if (sec == null) {
            return;
        }
        for (String k : sec.getKeys(false)) {
            int need;
            try {
                need = Integer.parseInt(k);
            } catch (NumberFormatException e) {
                continue;
            }
            if (value >= need && pr.milestones.add(cat + ":" + need)) {
                core.msg(p, "&6Pencapaian: &f" + sec.getString(k + ".name", k));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.4f);
                core.runConsole(p, sec.getStringList(k + ".commands"));
            }
        }
    }

    // ------------------------------------------------------------ item

    public int count(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() == m) {
                n += it.getAmount();
            }
        }
        return n;
    }

    /** Ambil item untuk tugas GET (boleh beberapa jenis material). Mengembalikan false jika kurang. */
    public boolean take(Player p, Objective o) {
        List<Material> mats = materials(o);
        int have = 0;
        for (Material m : mats) {
            have += count(p, m);
        }
        if (mats.isEmpty() || have < o.amount()) {
            return false;
        }
        int left = o.amount();
        for (Material m : mats) {
            int n = Math.min(left, count(p, m));
            if (n > 0) {
                p.getInventory().removeItem(new ItemStack(m, n));
                left -= n;
            }
            if (left <= 0) {
                break;
            }
        }
        return true;
    }

    public List<Material> materials(Objective o) {
        List<Material> out = new ArrayList<>();
        for (String t : o.targets()) {
            Material m = Material.matchMaterial(t);
            if (m != null && !out.contains(m)) {
                out.add(m);
            }
        }
        return out;
    }

    /** Jumlah item milik pemain yang cocok dengan tugas GET. */
    public int countFor(Player p, Objective o) {
        int n = 0;
        for (Material m : materials(o)) {
            n += count(p, m);
        }
        return n;
    }
}
