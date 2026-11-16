package com.nereus.guildmaster.db;

import com.nereus.guildmaster.GuildMaster;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Koneksi SQLite (bawaan) atau MySQL/MariaDB. Semua operasi lewat satu thread terpisah. */
public final class Database {
    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection c) throws SQLException;
    }

    private final GuildMaster plugin;
    private ExecutorService executor;
    private String url;
    private String user;
    private String pass;
    private boolean mysql;

    public Database(GuildMaster plugin) {
        this.plugin = plugin;
    }

    public boolean isMysql() {
        return mysql;
    }

    public String type() {
        return mysql ? "MySQL/MariaDB" : "SQLite";
    }

    /** Buka koneksi dan buat tabel. Mengembalikan false jika gagal (plugin tetap hidup tapi tanpa data). */
    public boolean open() {
        close();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("database");
        String type = s == null ? "sqlite" : s.getString("type", "sqlite");
        mysql = type.equalsIgnoreCase("mysql") || type.equalsIgnoreCase("mariadb");
        try {
            if (mysql) {
                String host = s.getString("host", "localhost");
                int port = s.getInt("port", 3306);
                String db = s.getString("name", "guildmaster");
                String extra = s.getString("options", "useSSL=false&characterEncoding=utf8");
                url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?" + extra;
                user = s.getString("user", "root");
                pass = s.getString("password", "");
            } else {
                File f = new File(plugin.getDataFolder(), "data/" + (s == null ? "guildmaster.db" : s.getString("file", "guildmaster.db")));
                File parent = f.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                url = "jdbc:sqlite:" + f.getAbsolutePath();
                user = null;
                pass = null;
                Class.forName("org.sqlite.JDBC");
            }
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "GuildMaster-DB");
                t.setDaemon(true);
                return t;
            });
            try (Connection c = connect(); Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS gm_adventurers ("
                        + "uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
                        + "name VARCHAR(32) NOT NULL,"
                        + "registered_at BIGINT NOT NULL,"
                        + "revoked INT NOT NULL DEFAULT 0)");
            }
            return true;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("Driver database tidak ditemukan: " + e.getMessage());
        } catch (SQLException e) {
            plugin.getLogger().severe("Gagal membuka database (" + type() + "): " + e.getMessage());
        }
        return false;
    }

    private Connection connect() throws SQLException {
        return user == null ? DriverManager.getConnection(url) : DriverManager.getConnection(url, user, pass);
    }

    /** Jalankan di thread database; hasil lewat CompletableFuture (jangan di-join di main thread). */
    public <T> CompletableFuture<T> call(SqlFunction<T> fn) {
        if (executor == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Database belum terbuka"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = connect()) {
                return fn.apply(c);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, executor);
    }

    public void close() {
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }
}
