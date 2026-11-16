package com.nereus.guildmaster.rpg;

import com.nereus.rpg.Core;
import com.nereus.rpg.Defs;
import com.nereus.rpg.NereusRpg;
import com.nereus.rpg.Objective;
import com.nereus.rpg.Profile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter ke plugin NereusRpg (com.nereus.rpg). Semua pemanggilan memakai method publik yang memang ada:
 * Core.profile, Core.rank, Core.rankIndex, Core.xpNeeded, Core.clazz, Core.quests, Core.tracker.eligible,
 * Tracker.accept / turnInQuest / abandon / count / mission / questXp, dan Profile.history.
 */
public final class NereusRpgAdapter implements RpgAdapter {
    private final Core core;

    private NereusRpgAdapter(Core core) {
        this.core = core;
    }

    /** null jika NereusRpg tidak terpasang atau belum siap. */
    public static NereusRpgAdapter tryCreate() {
        try {
            Plugin p = Bukkit.getPluginManager().getPlugin("NereusRpg");
            if (p instanceof NereusRpg rpg && p.isEnabled() && rpg.core() != null) {
                return new NereusRpgAdapter(rpg.core());
            }
        } catch (LinkageError ignored) {
            // versi RPG tidak cocok
        }
        return null;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String name() {
        return "NereusRpg";
    }

    @Override
    public int rankIndex(String key) {
        return core.rankIndex(key);
    }

    @Override
    public RpgProfile profile(Player p) {
        Profile pr = core.profile(p);
        core.checkDay(pr);
        Defs.Rank r = core.rank(pr.rank);
        Defs.Clazz cl = core.clazz(pr);
        Defs.Mission m = core.tracker.mission(pr);
        var cfg = core.plugin.getConfig();
        return new RpgProfile(pr.rank, r == null ? "?" : r.key(), r == null ? "?" : r.label(),
                pr.level, pr.xp, core.xpNeeded(pr.level), cl == null ? null : cl.name(), pr.gp,
                pr.totalQuests, pr.totalKills, pr.completedToday,
                cfg.getInt("quests.per-day-limit", 6), pr.streak, pr.active.size(),
                cfg.getInt("quests.max-active", 3), m == null ? null : m.title());
    }

    @Override
    public List<QuestInfo> quests(Player p) {
        Profile pr = core.profile(p);
        core.checkDay(pr);
        List<QuestInfo> out = new ArrayList<>();
        for (Defs.Quest q : core.tracker.eligible(pr)) {
            State st = pr.active.containsKey(q.id()) ? State.ACTIVE
                    : pr.done.contains(q.id()) ? State.DONE_TODAY : State.AVAILABLE;
            Defs.Rank r = core.rank(q.rank());
            List<String> rewards = new ArrayList<>(q.lore());
            if (rewards.isEmpty()) {
                rewards.add("&e+" + q.gp() + " GP");
            }
            rewards.add("&b+" + core.tracker.questXp(q) + " XP");
            out.add(new QuestInfo(q.id(), q.name(), r == null ? "?" : r.tag(), q.challenge(),
                    q.icon() == null ? Material.PAPER : q.icon(), q.obj().describe(), rewards, st));
        }
        return out;
    }

    @Override
    public List<ActiveQuest> activeQuests(Player p) {
        Profile pr = core.profile(p);
        core.checkDay(pr);
        List<ActiveQuest> out = new ArrayList<>();
        for (var e : pr.active.entrySet()) {
            Defs.Quest q = core.quests.get(e.getKey());
            if (q == null) {
                continue;
            }
            Objective o = q.obj();
            boolean get = o.type() == Objective.Type.GET;
            int progress = e.getValue();
            if (get) {
                Material m = Material.matchMaterial(o.target());
                progress = m == null ? 0 : core.tracker.count(p, m);
            }
            int shown = Math.min(progress, o.amount());
            out.add(new ActiveQuest(q.id(), q.name(), o.describe(), shown, o.amount(), get,
                    get && progress >= o.amount(), q.icon() == null ? Material.PAPER : q.icon()));
        }
        return out;
    }

    @Override
    public List<HistoryEntry> history(Player p) {
        Profile pr = core.profile(p);
        List<HistoryEntry> out = new ArrayList<>();
        for (int i = pr.history.size() - 1; i >= 0; i--) {
            String[] parts = pr.history.get(i).split("\\|", 3);
            if (parts.length < 3) {
                continue;
            }
            try {
                out.add(new HistoryEntry(Long.parseLong(parts[0]), parts[1], parts[2]));
            } catch (NumberFormatException ignored) {
                // baris rusak dilewati
            }
        }
        return out;
    }

    @Override
    public boolean accept(Player p, String questId) {
        core.tracker.accept(p, questId);
        return core.profile(p).active.containsKey(questId);
    }

    @Override
    public boolean turnIn(Player p, String questId) {
        core.tracker.turnInQuest(p, questId);
        return !core.profile(p).active.containsKey(questId);
    }

    @Override
    public void abandon(Player p, String questId) {
        core.tracker.abandon(p, questId);
    }
}
