package com.nereus.rpg;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Data RPG satu pemain. */
public final class Profile {
    public static final String[] STATS = {"str", "dex", "int", "vit", "luk"};

    public final UUID id;
    public String name = "";
    public String cls;
    /** 0 selama belum mendaftar; menjadi 1 saat pendaftaran Nerus Guild. */
    public int level = 0;
    /** Status Nerus Guild. Data tidak pernah dihapus saat REVOKED. */
    public GuildStatus status = GuildStatus.UNREGISTERED;
    public long registeredAt;
    /** Siapa/apa yang mendaftarkan: PLAYER, ADMIN, MIGRATION. */
    public String registeredBy = "";
    public long updatedAt;
    /** Total EXP seumur hidup (xp = EXP di level berjalan). */
    public long totalXp;
    /** true setelah admin memulihkan keanggotaan, sampai dialog "dipulihkan" ditampilkan. */
    public boolean restoredNotice;
    /** ID quest NerusRPG yang pernah selesai (seumur hidup). */
    public final Set<String> completedQuestIds = new java.util.LinkedHashSet<>();
    /** Riwayat rank: "epochMillis|dari|ke|alasan". */
    public final java.util.List<String> rankHistory = new java.util.ArrayList<>();
    public long xp;
    public int statPoints;
    public final Map<String, Integer> stats = new HashMap<>();
    public int rank;
    public int gp;
    public double mana = -1;
    public long classChangedAt;
    public int story;
    public int storyProgress;
    public boolean introSeen;
    public String day = "";
    public final Map<String, Integer> active = new LinkedHashMap<>();
    public final Set<String> done = new HashSet<>();
    public int completedToday;
    public int totalQuests;
    public int totalKills;
    public final Set<String> milestones = new HashSet<>();
    public int streak;
    public String lastLogin = "";
    public int examTarget = -1;
    public int examProgress;
    public long examDeadline;
    public long examCooldown;
    public int skillSel;
    public final Map<String, Long> cooldowns = new HashMap<>();
    /** Riwayat selesai: "epochMillis|quest atau story|nama", terbaru di akhir, maksimal 50. */
    public final java.util.List<String> history = new java.util.ArrayList<>();

    public Profile(UUID id) {
        this.id = id;
    }

    public boolean member() {
        return status == GuildStatus.REGISTERED;
    }

    public int stat(String k) {
        return stats.getOrDefault(k, 0);
    }

    public void save(ConfigurationSection s) {
        updatedAt = System.currentTimeMillis();
        s.set("data-version", 2);
        s.set("name", name);
        s.set("guild.status", status.name());
        s.set("guild.registered-at", registeredAt);
        s.set("guild.registered-by", registeredBy);
        s.set("guild.restored-notice", restoredNotice);
        s.set("total-xp", totalXp);
        s.set("completed-quest-ids", new java.util.ArrayList<>(completedQuestIds));
        s.set("rank-history", new java.util.ArrayList<>(rankHistory));
        s.set("last-updated", updatedAt);
        s.set("class", cls);
        s.set("level", level);
        s.set("xp", xp);
        s.set("stat-points", statPoints);
        for (String k : STATS) {
            s.set("stats." + k, stat(k));
        }
        s.set("rank", rank);
        s.set("gp", gp);
        s.set("mana", mana);
        s.set("class-changed-at", classChangedAt);
        s.set("story", story);
        s.set("story-progress", storyProgress);
        s.set("intro-seen", introSeen);
        s.set("day", day);
        for (Map.Entry<String, Integer> e : active.entrySet()) {
            s.set("active." + e.getKey(), e.getValue());
        }
        s.set("done", new java.util.ArrayList<>(done));
        s.set("completed-today", completedToday);
        s.set("total-quests", totalQuests);
        s.set("total-kills", totalKills);
        s.set("milestones", new java.util.ArrayList<>(milestones));
        s.set("streak", streak);
        s.set("last-login", lastLogin);
        s.set("exam-target", examTarget);
        s.set("exam-progress", examProgress);
        s.set("exam-deadline", examDeadline);
        s.set("exam-cooldown", examCooldown);
        s.set("skill-sel", skillSel);
        s.set("history", new java.util.ArrayList<>(history));
    }

    public void load(ConfigurationSection s) {
        name = s.getString("name", "");
        status = GuildStatus.parse(s.getString("guild.status", "UNREGISTERED"));
        registeredAt = s.getLong("guild.registered-at", 0);
        registeredBy = s.getString("guild.registered-by", "");
        restoredNotice = s.getBoolean("guild.restored-notice", false);
        totalXp = s.getLong("total-xp", 0);
        completedQuestIds.addAll(s.getStringList("completed-quest-ids"));
        rankHistory.addAll(s.getStringList("rank-history"));
        updatedAt = s.getLong("last-updated", 0);
        cls = s.getString("class", null);
        level = Math.max(0, s.getInt("level", 0));
        xp = s.getLong("xp", 0);
        statPoints = s.getInt("stat-points", 0);
        for (String k : STATS) {
            stats.put(k, s.getInt("stats." + k, 0));
        }
        rank = s.getInt("rank", 0);
        gp = s.getInt("gp", 0);
        mana = s.getDouble("mana", -1);
        classChangedAt = s.getLong("class-changed-at", 0);
        story = s.getInt("story", 0);
        storyProgress = s.getInt("story-progress", 0);
        introSeen = s.getBoolean("intro-seen", false);
        day = s.getString("day", "");
        ConfigurationSection a = s.getConfigurationSection("active");
        if (a != null) {
            for (String k : a.getKeys(false)) {
                active.put(k, a.getInt(k));
            }
        }
        done.addAll(s.getStringList("done"));
        completedToday = s.getInt("completed-today", 0);
        totalQuests = s.getInt("total-quests", 0);
        totalKills = s.getInt("total-kills", 0);
        milestones.addAll(s.getStringList("milestones"));
        streak = s.getInt("streak", 0);
        lastLogin = s.getString("last-login", "");
        examTarget = s.getInt("exam-target", -1);
        examProgress = s.getInt("exam-progress", 0);
        examDeadline = s.getLong("exam-deadline", 0);
        examCooldown = s.getLong("exam-cooldown", 0);
        skillSel = s.getInt("skill-sel", 0);
        history.addAll(s.getStringList("history"));
    }
}
