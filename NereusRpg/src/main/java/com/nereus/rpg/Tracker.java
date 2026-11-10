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

    // ------------------------------------------------------------ quest harian

    public List<Defs.Quest> eligible(Profile pr) {
        List<Defs.Quest> out = new ArrayList<>();
        for (Defs.Quest q : core.quests.values()) {
            boolean ok = q.challenge() ? q.rank() == pr.rank + 1 : q.rank() <= pr.rank;
            if (ok) {
                out.add(q);
            }
        }
        out.sort((a, b) -> a.rank() != b.rank() ? Integer.compare(a.rank(), b.rank())
                : Boolean.compare(a.challenge(), b.challenge()));
        return out;
    }

    public void accept(Player p, String id) {
        Profile pr = core.profile(p);
        core.checkDay(pr);
        Defs.Quest q = core.quests.get(id);
        int max = core.plugin.getConfig().getInt("quests.max-active", 3);
        int limit = core.plugin.getConfig().getInt("quests.per-day-limit", 6);
        if (q == null || !eligible(pr).contains(q)) {
            core.msg(p, "&cQuest ini belum bisa kamu ambil.");
        } else if (pr.done.contains(id)) {
            core.msg(p, "&cQuest ini sudah selesai hari ini.");
        } else if (pr.active.containsKey(id)) {
            core.msg(p, "&cQuest ini sedang berjalan.");
        } else if (pr.active.size() >= max) {
            core.msg(p, "&cMaksimal &f" + max + " &cquest aktif sekaligus.");
        } else if (pr.completedToday + pr.active.size() >= limit) {
            core.msg(p, "&cBatas quest harian (&f" + limit + "&c) tercapai. Kembali besok.");
        } else {
            pr.active.put(id, 0);
            core.msg(p, "&aQuest diambil: " + q.name() + " &8- &f" + q.obj().describe());
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        }
    }

    public void abandon(Player p, String id) {
        Profile pr = core.profile(p);
        if (pr.active.remove(id) != null) {
            core.msg(p, "&7Quest dibatalkan.");
        }
    }

    public void turnInQuest(Player p, String id) {
        Profile pr = core.profile(p);
        Defs.Quest q = core.quests.get(id);
        if (q == null || !pr.active.containsKey(id) || q.obj().type() != Objective.Type.GET) {
            return;
        }
        if (take(p, q.obj())) {
            completeQuest(p, pr, q);
        } else {
            core.msg(p, "&cItem belum cukup: &f" + q.obj().describe());
        }
    }

    private void completeQuest(Player p, Profile pr, Defs.Quest q) {
        pr.active.remove(q.id());
        pr.done.add(q.id());
        pr.completedToday++;
        pr.totalQuests++;
        long xp = Math.round(core.plugin.getConfig().getInt("level.xp-quest-base", 40) * (q.rank() + 1) * (q.challenge() ? 1.5 : 1.0));
        p.showTitle(Title.title(Text.c("&aQuest Selesai"), Text.c(q.name())));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        core.reward(p, q.gp(), xp, q.commands());
        milestones(p, pr);
    }

    // ------------------------------------------------------------ progres

    public void progress(Player p, Objective.Type type, String target) {
        Profile pr = core.profile(p);
        core.checkDay(pr);

        for (String id : new ArrayList<>(pr.active.keySet())) {
            Defs.Quest q = core.quests.get(id);
            if (q == null || q.obj().type() != type || type == Objective.Type.GET
                    || !q.obj().target().equalsIgnoreCase(target)) {
                continue;
            }
            int cur = pr.active.get(id) + 1;
            if (cur >= q.obj().amount()) {
                completeQuest(p, pr, q);
            } else {
                pr.active.put(id, cur);
            }
        }

        Defs.Mission m = mission(pr);
        if (m != null && m.minRank() <= pr.rank && m.obj().type() == type && m.obj().target().equalsIgnoreCase(target)) {
            pr.storyProgress++;
            if (pr.storyProgress >= m.obj().amount()) {
                completeMission(p, pr, m);
            }
        }

        if (pr.examTarget >= 0) {
            Defs.Rank t = core.rank(pr.examTarget);
            if (t != null && t.exam() != null && t.exam().type() == type && t.exam().target().equalsIgnoreCase(target)) {
                pr.examProgress++;
                if (pr.examProgress >= t.exam().amount()) {
                    core.completeExam(p);
                }
            }
        }
    }

    public void kill(Player p, String entity) {
        Profile pr = core.profile(p);
        pr.totalKills++;
        progress(p, Objective.Type.KILL, entity);
        milestones(p, pr);
    }

    // ------------------------------------------------------------ misi cerita

    public Defs.Mission mission(Profile pr) {
        return pr.story >= 0 && pr.story < core.missions.size() ? core.missions.get(pr.story) : null;
    }

    public void turnInStory(Player p) {
        Profile pr = core.profile(p);
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
        Defs.Mission m = mission(pr);
        if (m != null && m.minRank() <= pr.rank && m.obj().type() == Objective.Type.TALK
                && m.obj().target().equalsIgnoreCase(target)) {
            completeMission(p, pr, m);
        }
    }

    public void rankChanged(Player p) {
        Profile pr = core.profile(p);
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

    /** Ambil item untuk tugas GET. Mengembalikan false jika kurang. */
    public boolean take(Player p, Objective o) {
        Material m = Material.matchMaterial(o.target());
        if (m == null || count(p, m) < o.amount()) {
            return false;
        }
        p.getInventory().removeItem(new ItemStack(m, o.amount()));
        return true;
    }
}
