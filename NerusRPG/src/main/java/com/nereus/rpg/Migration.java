package com.nereus.rpg;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Migrasi data dari plugin lama: NereusRpg (plugins/NereusRpg/players.yml + konfigurasi) dan GuildMaster
 * (plugins/GuildMaster, SQLite atau MySQL). Aturan: backup dulu, data lama tidak pernah dihapus/diubah,
 * file pemain yang sudah ada tidak ditimpa (tidak ada duplikasi), konflik dilaporkan di logs/.
 */
public final class Migration {
    private static final String MARKER = "migration-done.txt";

    private final NerusRPG plugin;
    private final Core core;

    public Migration(NerusRPG plugin, Core core) {
        this.plugin = plugin;
        this.core = core;
    }

    private File oldRpgDir() {
        return new File(plugin.getDataFolder().getParentFile(), "NereusRpg");
    }

    private File oldGuildDir() {
        return new File(plugin.getDataFolder().getParentFile(), "GuildMaster");
    }

    private File marker() {
        return new File(new File(plugin.getDataFolder(), "logs"), MARKER);
    }

    public boolean pending() {
        return !marker().exists() && (new File(oldRpgDir(), "players.yml").isFile() || oldGuildDir().isDirectory());
    }

    /** Dipanggil saat startup (belum ada pemain): berjalan sinkron supaya tidak berebut dengan login. */
    public void runStartupIfNeeded() {
        if (!plugin.getConfig().getBoolean("migration.auto", true) || !pending()) {
            return;
        }
        plugin.getLogger().info("Data NereusRpg/GuildMaster lama ditemukan, memulai migrasi otomatis...");
        List<String> report = migrate();
        for (String l : report) {
            plugin.getLogger().info("[migrasi] " + l);
        }
    }

    /** /gm migrate: berjalan di thread IO; hasil dilaporkan ke pengirim. */
    public void run(CommandSender sender) {
        if (!pending() && !new File(oldRpgDir(), "players.yml").isFile() && !oldGuildDir().isDirectory()) {
            sender.sendMessage(Text.c(Text.PREFIX + "&cTidak ada data lama (plugins/NereusRpg atau plugins/GuildMaster)."));
            return;
        }
        sender.sendMessage(Text.c(Text.PREFIX + "&7Migrasi dimulai (backup dulu)..."));
        core.store.run(() -> {
            List<String> report = migrate();
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (String l : report) {
                    sender.sendMessage(Text.c("&8[migrasi] &7" + l));
                }
            });
        });
    }

    private record GmRow(String name, long registeredAt, boolean revoked) {
    }

    private Map<UUID, GmRow> readGuildMaster(List<String> rep) {
        Map<UUID, GmRow> out = new HashMap<>();
        File dir = oldGuildDir();
        if (!dir.isDirectory()) {
            return out;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File(dir, "config.yml"));
        ConfigurationSection s = cfg.getConfigurationSection("database");
        String type = s == null ? "sqlite" : s.getString("type", "sqlite");
        boolean mysql = type.equalsIgnoreCase("mysql") || type.equalsIgnoreCase("mariadb");
        String url;
        String user = null;
        String pass = null;
        if (mysql) {
            url = "jdbc:mysql://" + s.getString("host", "localhost") + ":" + s.getInt("port", 3306) + "/"
                    + s.getString("name", "guildmaster") + "?" + s.getString("options", "useSSL=false&characterEncoding=utf8");
            user = s.getString("user", "root");
            pass = s.getString("password", "");
        } else {
            File f = new File(dir, "data/" + (s == null ? "guildmaster.db" : s.getString("file", "guildmaster.db")));
            if (!f.isFile()) {
                rep.add("GuildMaster: file database " + f.getName() + " tidak ada, dilewati.");
                return out;
            }
            url = "jdbc:sqlite:" + f.getAbsolutePath();
        }
        try {
            if (!mysql) {
                Class.forName("org.sqlite.JDBC");
            }
            try (Connection c = user == null ? DriverManager.getConnection(url) : DriverManager.getConnection(url, user, pass);
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT uuid, name, registered_at, revoked FROM gm_adventurers")) {
                while (rs.next()) {
                    try {
                        out.put(UUID.fromString(rs.getString(1)), new GmRow(rs.getString(2), rs.getLong(3), rs.getInt(4) != 0));
                    } catch (IllegalArgumentException e) {
                        rep.add("KONFLIK: GuildMaster UUID tidak valid '" + rs.getString(1) + "' dilewati.");
                    }
                }
            }
            rep.add("GuildMaster: " + out.size() + " pendaftaran dibaca (" + (mysql ? "MySQL" : "SQLite") + ").");
        } catch (ClassNotFoundException | SQLException e) {
            rep.add("KONFLIK: GuildMaster database gagal dibaca (" + e.getMessage() + "). Pendaftaran lama tidak ikut dimigrasi.");
        }
        return out;
    }

    private static boolean hasProgress(Profile p) {
        return p.level > 1 || p.xp > 0 || p.rank > 0 || p.totalQuests > 0 || p.cls != null || p.gp > 0
                || p.totalKills > 0 || p.story > 0;
    }

    /** Menjalankan seluruh migrasi. Aman dipanggil ulang (file yang ada tidak ditimpa). */
    public synchronized List<String> migrate() {
        List<String> rep = new ArrayList<>();
        String policy = plugin.getConfig().getString("migration.legacy-players", "REGISTER_IF_PROGRESS").toUpperCase();
        File rpgDir = oldRpgDir();
        File players = new File(rpgDir, "players.yml");
        Backups backups = plugin.backups();
        // 1) backup semua sumber
        for (File src : new File[]{players, new File(oldGuildDir(), "data")}) {
            if (src.exists()) {
                File z = backups.zip(src, "premigrasi-" + (src == players ? "nereusrpg" : "guildmaster"));
                rep.add(z == null ? "KONFLIK: backup " + src.getName() + " gagal; migrasi data itu dihentikan." : "Backup: " + z.getName());
                if (z == null) {
                    return rep;
                }
            }
        }
        // 2) konfigurasi (disalin saat startup, lihat preConfigs)
        rep.addAll(pre);
        pre.clear();

        Map<UUID, GmRow> gm = readGuildMaster(rep);
        Map<UUID, Profile> result = new HashMap<>();
        int skipped = 0;
        int clamped = 0;
        if (players.isFile()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(players);
            ConfigurationSection ps = y.getConfigurationSection("players");
            if (ps != null) {
                for (String key : ps.getKeys(false)) {
                    UUID id;
                    try {
                        id = UUID.fromString(key);
                    } catch (IllegalArgumentException e) {
                        rep.add("KONFLIK: UUID tidak valid '" + key + "' di players.yml dilewati.");
                        continue;
                    }
                    ConfigurationSection sec = ps.getConfigurationSection(key);
                    if (sec == null) {
                        continue;
                    }
                    Profile p = new Profile(id);
                    p.load(sec);
                    if (p.rank > core.ranks.size() - 1 && !core.ranks.isEmpty()) {
                        rep.add("KONFLIK: " + p.name + " rank lama indeks " + p.rank + " (SS/SSS) diturunkan ke S.");
                        p.rank = core.ranks.size() - 1;
                        clamped++;
                    }
                    long total = p.xp;
                    for (int l = 1; l < p.level; l++) {
                        total += core.xpNeeded(l);
                    }
                    p.totalXp = total;
                    p.completedQuestIds.addAll(p.done);
                    result.put(id, p);
                }
            }
        }
        // 3) status guild
        int reg = 0;
        int rev = 0;
        int unreg = 0;
        for (Map.Entry<UUID, Profile> e : result.entrySet()) {
            Profile p = e.getValue();
            GmRow row = gm.get(e.getKey());
            boolean progress = hasProgress(p);
            if (row != null) {
                p.status = row.revoked() ? GuildStatus.REVOKED : GuildStatus.REGISTERED;
                p.registeredAt = row.registeredAt();
                p.registeredBy = "MIGRATION";
                if (row.revoked() && progress) {
                    rep.add("KONFLIK: " + p.name + " DICABUT di GuildMaster tetapi punya progres RPG. Status REVOKED, data dipertahankan.");
                }
            } else if (progress && policy.equals("REGISTER_IF_PROGRESS")) {
                p.status = GuildStatus.REGISTERED;
                p.registeredAt = System.currentTimeMillis();
                p.registeredBy = "MIGRATION";
            } else {
                p.status = GuildStatus.UNREGISTERED;
                if (!progress) {
                    p.level = 0;
                    p.rank = 0;
                }
            }
            if (p.status == GuildStatus.REGISTERED || p.status == GuildStatus.REVOKED) {
                if (p.level < 1) {
                    p.level = 1;
                }
                core.logRank(p, "NONE", core.rank(p.rank) == null ? "F" : core.rank(p.rank).key(), "MIGRATION");
            }
            switch (p.status) {
                case REGISTERED -> reg++;
                case REVOKED -> rev++;
                default -> unreg++;
            }
        }
        // pendaftar GuildMaster tanpa data RPG
        for (Map.Entry<UUID, GmRow> e : gm.entrySet()) {
            if (result.containsKey(e.getKey())) {
                continue;
            }
            Profile p = new Profile(e.getKey());
            p.name = e.getValue().name();
            p.status = e.getValue().revoked() ? GuildStatus.REVOKED : GuildStatus.REGISTERED;
            p.registeredAt = e.getValue().registeredAt();
            p.registeredBy = "MIGRATION";
            p.level = 1;
            p.rank = 0;
            core.logRank(p, "NONE", "F", "MIGRATION");
            result.put(e.getKey(), p);
            if (p.status == GuildStatus.REGISTERED) {
                reg++;
            } else {
                rev++;
            }
        }
        // 4) tulis file (tidak menimpa)
        int written = 0;
        for (Map.Entry<UUID, Profile> e : result.entrySet()) {
            if (core.store.exists(e.getKey()) || core.loaded(e.getKey()) != null) {
                skipped++;
                rep.add("Dilewati (data baru sudah ada, tidak ditimpa): " + e.getValue().name + " " + e.getKey());
                continue;
            }
            core.store.saveNow(e.getValue());
            written++;
        }
        rep.add("Selesai: " + written + " pemain ditulis ke playerdata/ (REGISTERED " + reg + ", REVOKED " + rev
                + ", UNREGISTERED " + unreg + "), dilewati " + skipped + ", rank SS/SSS dipotong ke S: " + clamped + ".");
        rep.add("Data lama TIDAK dihapus. Setelah yakin, folder plugins/NereusRpg dan plugins/GuildMaster boleh dihapus, "
                + "dan jar NereusRpg/GuildMaster HARUS dilepas dari server.");
        writeReport(rep);
        return rep;
    }

    private final List<String> pre = new ArrayList<>();

    /**
     * Langkah awal startup (SEBELUM config/default dibuat): salin konfigurasi lama milik admin
     * (config, classes, quests, missions, weapons, menus). File yang sudah ada tidak ditimpa.
     */
    public void preConfigs() {
        pre.clear();
        File rpgDir = oldRpgDir();
        if (!plugin.getConfig().getBoolean("migration.auto", true) && new File(plugin.getDataFolder(), "config.yml").exists()) {
            return;
        }
        if (marker().exists() || !rpgDir.isDirectory()) {
            return;
        }
        plugin.getDataFolder().mkdirs();
        for (String name : new String[]{"classes.yml", "quests.yml", "missions.yml", "weapons.yml"}) {
            copyIfMissing(new File(rpgDir, name), new File(plugin.getDataFolder(), name), pre);
        }
        File[] list = new File(rpgDir, "menus").listFiles((d, n) -> n.endsWith(".yml"));
        if (list != null) {
            for (File f : list) {
                copyIfMissing(f, new File(new File(plugin.getDataFolder(), "menus"), f.getName()), pre);
            }
        }
        File oldCfg = new File(rpgDir, "config.yml");
        File newCfg = new File(plugin.getDataFolder(), "config.yml");
        if (oldCfg.isFile() && !newCfg.exists()) {
            try {
                String old = Files.readString(oldCfg.toPath(), StandardCharsets.UTF_8);
                try (var in = plugin.getResource("config-guild-additions.yml")) {
                    String add = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    Files.writeString(newCfg.toPath(), old + "\n" + add, StandardCharsets.UTF_8);
                }
                pre.add("config.yml lama dipakai (ditambah bagian Nerus Guild). Bagian 'ranks:' lama diabaikan; rank kini di ranks.yml.");
            } catch (IOException e) {
                pre.add("KONFLIK: config.yml lama gagal disalin (" + e.getMessage() + ").");
            }
        }
    }

    private void copyIfMissing(File from, File to, List<String> rep) {
        if (!from.isFile() || to.exists()) {
            return;
        }
        try {
            if (to.getParentFile() != null) {
                to.getParentFile().mkdirs();
            }
            Files.copy(from.toPath(), to.toPath());
            rep.add("Disalin: " + from.getName());
        } catch (IOException e) {
            rep.add("KONFLIK: gagal menyalin " + from.getName() + ": " + e.getMessage());
        }
    }

    private void writeReport(List<String> rep) {
        File dir = new File(plugin.getDataFolder(), "logs");
        dir.mkdirs();
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        try {
            Files.write(new File(dir, "migration-" + stamp + ".txt").toPath(), rep, StandardCharsets.UTF_8);
            Files.writeString(marker().toPath(), "Migrasi selesai " + stamp + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Laporan migrasi gagal ditulis: " + e.getMessage());
        }
    }
}
