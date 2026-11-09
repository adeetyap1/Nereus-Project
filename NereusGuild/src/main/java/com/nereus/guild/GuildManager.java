package com.nereus.guild;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Logika rank, GP, ujian promosi, permission quest, dan penyimpanan. */
public final class GuildManager {
    private final NereusGuild plugin;
    private final PointsHook points = new PointsHook();

    private final List<RankDef> ranks = new ArrayList<>();
    private final Map<UUID, PlayerData> data = new HashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    private BukkitTask loop;
    private boolean dirty;
    private long lastSave = System.currentTimeMillis();

    public GuildManager(NereusGuild plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ config

    public void reloadRanks() {
        ranks.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("ranks");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                String code = key.toUpperCase(Locale.ROOT);
                ranks.add(new RankDef(
                        code,
                        s.getString("name", "Rank " + code),
                        s.getString("color", "&f"),
                        Math.max(0, s.getInt("gp", 0)),
                        parseExam(code, s.getConfigurationSection("exam")),
                        s.getBoolean("broadcast", false),
                        s.getStringList("commands")));
            }
        }
        if (ranks.isEmpty()) {
            ranks.add(new RankDef("F", "Rank F", "&f", 0, null, false, List.of()));
        }
        for (PlayerData d : data.values()) {
            d.rank = Math.min(d.rank, ranks.size() - 1);
        }
    }

    private ExamDef parseExam(String rankCode, ConfigurationSection e) {
        if (e == null) {
            return null;
        }
        ExamDef.Type type;
        try {
            type = ExamDef.Type.valueOf(e.getString("type", "NONE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Rank " + rankCode + ": exam.type tidak dikenal, dipakai NONE");
            type = ExamDef.Type.NONE;
        }
        EntityType entity = null;
        if (type == ExamDef.Type.KILL) {
            try {
                entity = EntityType.valueOf(e.getString("entity", "ZOMBIE").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Rank " + rankCode + ": exam.entity tidak dikenal, dipakai MANUAL");
                type = ExamDef.Type.MANUAL;
            }
        }
        return new ExamDef(type, entity, Math.max(1, e.getInt("amount", 1)), Math.max(0, e.getInt("minutes", 0)),
                e.getString("description", ""), e.getStringList("worlds"));
    }

    public List<RankDef> ranks() {
        return ranks;
    }

    /** Rank pada indeks, atau null kalau di luar daftar. */
    public RankDef rank(int index) {
        return index >= 0 && index < ranks.size() ? ranks.get(index) : null;
    }

    public int indexOf(String code) {
        for (int i = 0; i < ranks.size(); i++) {
            if (ranks.get(i).key().equalsIgnoreCase(code)) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        loop = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (loop != null) {
            loop.cancel();
        }
        for (Map.Entry<UUID, PermissionAttachment> en : attachments.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null) {
                p.removeAttachment(en.getValue());
            }
        }
        attachments.clear();
        save();
    }

    // ------------------------------------------------------------------ storage

    private File file() {
        return new File(plugin.getDataFolder(), "players.yml");
    }

    public void load() {
        data.clear();
        File f = file();
        if (!f.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection sec = y.getConfigurationSection("players");
        if (sec == null) {
            return;
        }
        for (String k : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(k);
            if (s == null) {
                continue;
            }
            UUID id;
            try {
                id = UUID.fromString(k);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            PlayerData d = new PlayerData();
            d.rank = Math.max(0, Math.min(s.getInt("rank", 0), ranks.size() - 1));
            d.cooldownUntil = s.getLong("cooldown", 0);
            ConfigurationSection e = s.getConfigurationSection("exam");
            if (e != null) {
                PlayerData.Exam ex = new PlayerData.Exam();
                ex.targetRank = e.getInt("target", d.rank + 1);
                ex.startedAt = e.getLong("started", 0);
                ex.deadline = e.getLong("deadline", 0);
                ex.progress = e.getInt("progress", 0);
                d.exam = ex;
            }
            data.put(id, d);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerData> en : data.entrySet()) {
            PlayerData d = en.getValue();
            if (d.rank == 0 && d.exam == null && d.cooldownUntil == 0) {
                continue;
            }
            String b = "players." + en.getKey();
            y.set(b + ".rank", d.rank);
            y.set(b + ".cooldown", d.cooldownUntil);
            if (d.exam != null) {
                y.set(b + ".exam.target", d.exam.targetRank);
                y.set(b + ".exam.started", d.exam.startedAt);
                y.set(b + ".exam.deadline", d.exam.deadline);
                y.set(b + ".exam.progress", d.exam.progress);
            }
        }
        File f = file();
        File parent = f.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try {
            y.save(f);
            dirty = false;
            lastSave = System.currentTimeMillis();
        } catch (IOException ex) {
            plugin.getLogger().severe("Gagal menyimpan players.yml: " + ex.getMessage());
        }
    }

    public PlayerData data(UUID id) {
        return data.computeIfAbsent(id, k -> new PlayerData());
    }

    // ------------------------------------------------------------------ GP

    public int gp(Player p) {
        if (!plugin.getConfig().getBoolean("points.enabled", true)) {
            return Integer.MAX_VALUE;
        }
        return points.look(p.getUniqueId());
    }

    public boolean gpEnabled() {
        return plugin.getConfig().getBoolean("points.enabled", true);
    }

    public boolean pointsAvailable() {
        return points.available();
    }

    // ------------------------------------------------------------------ sesi

    public void handleJoin(Player p) {
        PlayerData d = data(p.getUniqueId());
        d.rank = Math.min(d.rank, ranks.size() - 1);
        applyPermissions(p);
        if (d.exam != null) {
            if (rank(d.exam.targetRank) == null || rank(d.exam.targetRank).exam() == null) {
                d.exam = null;
                dirty = true;
            } else if (d.exam.deadline > 0 && System.currentTimeMillis() >= d.exam.deadline) {
                failExam(p, "exam-timeout");
            }
        }
    }

    public void handleQuit(Player p) {
        PermissionAttachment att = attachments.remove(p.getUniqueId());
        if (att != null) {
            p.removeAttachment(att);
        }
        if (dirty) {
            save();
        }
    }

    // ------------------------------------------------------------------ permission

    /** Beri permission quest sesuai aturan: rank sendiri, semua di bawahnya, dan satu tingkat di atas. */
    public void applyPermissions(Player p) {
        PlayerData d = data(p.getUniqueId());
        PermissionAttachment old = attachments.remove(p.getUniqueId());
        if (old != null) {
            p.removeAttachment(old);
        }
        String questPrefix = plugin.getConfig().getString("permissions.quest-prefix", "nereus.guild.");
        String challengePrefix = plugin.getConfig().getString("permissions.challenge-prefix", "nereus.guild.tantangan.");
        PermissionAttachment att = p.addAttachment(plugin);
        for (int i = 0; i <= d.rank && i < ranks.size(); i++) {
            att.setPermission(questPrefix + ranks.get(i).key().toLowerCase(Locale.ROOT), true);
        }
        RankDef above = rank(d.rank + 1);
        if (above != null) {
            att.setPermission(challengePrefix + above.key().toLowerCase(Locale.ROOT), true);
        }
        attachments.put(p.getUniqueId(), att);
        p.recalculatePermissions();
    }

    private void runCommands(List<String> commands, Player p, RankDef now, RankDef old) {
        for (String raw : commands) {
            String cmd = raw
                    .replace("%player%", p.getName())
                    .replace("%rank%", now.key())
                    .replace("%rank_name%", now.name())
                    .replace("%old_rank%", old == null ? "" : old.key());
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    /** Terapkan permission dan perintah sinkronisasi (meta tampilan) tanpa hadiah naik rank. */
    public void sync(Player p) {
        applyPermissions(p);
        RankDef now = ranks.get(data(p.getUniqueId()).rank);
        runCommands(plugin.getConfig().getStringList("sync-commands"), p, now, null);
    }

    /** Atur rank langsung (admin). Ujian yang berjalan dibatalkan. */
    public void setRank(Player p, int index) {
        PlayerData d = data(p.getUniqueId());
        d.rank = Math.max(0, Math.min(index, ranks.size() - 1));
        d.exam = null;
        d.cooldownUntil = 0;
        dirty = true;
        sync(p);
        save();
    }

    // ------------------------------------------------------------------ ujian

    public String describe(ExamDef ex) {
        if (ex == null || ex.type() == ExamDef.Type.NONE) {
            return "Tanpa ujian";
        }
        if (!ex.description().isEmpty()) {
            return ex.description();
        }
        if (ex.type() == ExamDef.Type.KILL) {
            String name = ex.entity().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            return "Bunuh " + ex.amount() + " " + name + (ex.minutes() > 0 ? " dalam " + ex.minutes() + " menit" : "");
        }
        return "Diselesaikan lewat tantangan khusus";
    }

    public String formatTime(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    /** Mulai ujian rank berikutnya. Mengirim pesan hasilnya ke pemain. */
    public void startExam(Player p) {
        PlayerData d = data(p.getUniqueId());
        RankDef target = rank(d.rank + 1);
        if (target == null) {
            plugin.send(p, "max-rank");
            return;
        }
        long now = System.currentTimeMillis();
        if (d.exam != null) {
            plugin.send(p, "exam-active");
            return;
        }
        if (d.cooldownUntil > now) {
            plugin.send(p, "exam-cooldown", "{time}", formatTime(d.cooldownUntil - now));
            return;
        }
        int gp = gp(p);
        if (gp < target.gp()) {
            plugin.send(p, "not-enough-gp", "{rank}", target.label(), "{gp}", String.valueOf(gp),
                    "{need}", String.valueOf(target.gp()));
            return;
        }
        ExamDef ex = target.exam();
        if (ex == null || ex.type() == ExamDef.Type.NONE) {
            promote(p);
            return;
        }
        PlayerData.Exam state = new PlayerData.Exam();
        state.targetRank = d.rank + 1;
        state.startedAt = now;
        state.deadline = ex.minutes() > 0 ? now + ex.minutes() * 60_000L : 0;
        state.progress = 0;
        d.exam = state;
        dirty = true;
        plugin.send(p, "exam-started", "{rank}", target.label(), "{desc}", describe(ex));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 0.8f);
    }

    public void failExam(Player p, String messageKey) {
        PlayerData d = data(p.getUniqueId());
        if (d.exam == null) {
            return;
        }
        d.exam = null;
        d.cooldownUntil = System.currentTimeMillis()
                + plugin.getConfig().getLong("exam.cooldown-minutes", 10) * 60_000L;
        dirty = true;
        plugin.send(p, messageKey);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
    }

    /** Batalkan ujian tanpa jeda (dipakai pemain sendiri atau admin). */
    public boolean cancelExam(Player p) {
        PlayerData d = data(p.getUniqueId());
        if (d.exam == null) {
            return false;
        }
        d.exam = null;
        dirty = true;
        return true;
    }

    /** Selesaikan ujian yang berjalan dan naikkan rank. False kalau tidak ada ujian. */
    public boolean completeExam(Player p) {
        PlayerData d = data(p.getUniqueId());
        if (d.exam == null) {
            return false;
        }
        promote(p);
        return true;
    }

    public void promote(Player p) {
        PlayerData d = data(p.getUniqueId());
        if (d.rank + 1 >= ranks.size()) {
            return;
        }
        RankDef old = ranks.get(d.rank);
        d.rank++;
        d.exam = null;
        d.cooldownUntil = 0;
        dirty = true;
        RankDef now = ranks.get(d.rank);

        applyPermissions(p);
        runCommands(plugin.getConfig().getStringList("sync-commands"), p, now, old);
        runCommands(plugin.getConfig().getStringList("promote-commands"), p, now, old);
        runCommands(now.commands(), p, now, old);

        plugin.send(p, "promoted", "{rank}", now.label());
        p.showTitle(Title.title(
                NereusGuild.LEGACY.deserialize(plugin.raw("title-main")),
                NereusGuild.LEGACY.deserialize(plugin.raw("title-sub", "{rank}", now.label()))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        if (now.broadcast()) {
            Component msg = NereusGuild.LEGACY.deserialize(plugin.raw("prefix")
                    + plugin.raw("promoted-broadcast", "{player}", p.getName(), "{rank}", now.label()));
            Bukkit.broadcast(msg);
        }
        save();
    }

    /** Dipanggil saat pemain membunuh mob. */
    public void onKill(Player p, EntityType type) {
        PlayerData d = data.get(p.getUniqueId());
        if (d == null || d.exam == null) {
            return;
        }
        RankDef target = rank(d.exam.targetRank);
        if (target == null || target.exam() == null) {
            return;
        }
        ExamDef ex = target.exam();
        if (ex.type() != ExamDef.Type.KILL || ex.entity() != type) {
            return;
        }
        if (!ex.worlds().isEmpty() && !ex.worlds().contains(p.getWorld().getName())) {
            return;
        }
        d.exam.progress++;
        dirty = true;
        if (d.exam.progress >= ex.amount()) {
            promote(p);
        } else {
            showProgress(p, d, target, ex);
        }
    }

    private void showProgress(Player p, PlayerData d, RankDef target, ExamDef ex) {
        String time = d.exam.deadline > 0 ? formatTime(d.exam.deadline - System.currentTimeMillis()) : "--:--";
        String text;
        if (ex.type() == ExamDef.Type.KILL) {
            text = plugin.raw("actionbar", "{rank}", target.label(), "{progress}", String.valueOf(d.exam.progress),
                    "{amount}", String.valueOf(ex.amount()), "{time}", time);
        } else {
            text = plugin.raw("actionbar-manual", "{rank}", target.label(), "{desc}", describe(ex), "{time}", time);
        }
        p.sendActionBar(NereusGuild.LEGACY.deserialize(text));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean bar = plugin.getConfig().getBoolean("exam.actionbar", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = data.get(p.getUniqueId());
            if (d == null || d.exam == null) {
                continue;
            }
            RankDef target = rank(d.exam.targetRank);
            if (target == null || target.exam() == null) {
                d.exam = null;
                dirty = true;
                continue;
            }
            if (d.exam.deadline > 0 && now >= d.exam.deadline) {
                failExam(p, "exam-timeout");
                continue;
            }
            if (bar) {
                showProgress(p, d, target, target.exam());
            }
        }
        if (dirty && now - lastSave > 30_000L) {
            save();
        }
    }
}
