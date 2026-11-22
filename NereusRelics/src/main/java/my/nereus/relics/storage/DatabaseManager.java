package my.nereus.relics.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * SQLite dengan satu koneksi dan satu thread penulis (semua operasi berurutan). Setiap tugas dijalankan
 * dalam transaksi. Memakai driver sqlite-jdbc bawaan Paper (sama seperti plugin Nereus lain).
 */
public final class DatabaseManager {

    @FunctionalInterface
    public interface SqlTask<T> {
        T run(Connection c) throws SQLException;
    }

    private final Connection conn;
    private final ExecutorService exec;
    private final Logger log;

    public DatabaseManager(File file, Logger log) throws SQLException, ClassNotFoundException {
        this.log = log;
        Class.forName("org.sqlite.JDBC");
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=FULL");
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=5000");
        }
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "NereusRelics-DB");
            t.setDaemon(false);
            return t;
        });
    }

    private <T> T tx(SqlTask<T> task) throws SQLException {
        conn.setAutoCommit(false);
        try {
            T r = task.run(conn);
            conn.commit();
            return r;
        } catch (SQLException | RuntimeException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // abaikan
            }
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /** Menunggu hasil (dipakai hanya untuk operasi langka dan kritis: klaim, pengiriman, load awal). */
    public <T> T call(SqlTask<T> task) throws SQLException {
        Future<T> f = exec.submit(() -> tx(task));
        try {
            return f.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof SQLException se) throw se;
            throw new SQLException(e.getCause());
        } catch (InterruptedException | java.util.concurrent.TimeoutException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("DB timeout/interrupted", e);
        }
    }

    /** Asinkron, tidak memblokir main thread. */
    public void write(SqlTask<?> task) {
        try {
            exec.execute(() -> {
                try {
                    tx(task);
                } catch (SQLException | RuntimeException e) {
                    log.severe("Gagal menulis database: " + e);
                }
            });
        } catch (RuntimeException e) {
            log.severe("Antrian database sudah ditutup: " + e);
        }
    }

    public void close() {
        exec.shutdown();
        try {
            if (!exec.awaitTermination(15, TimeUnit.SECONDS)) log.warning("Antrian database belum kosong saat dimatikan.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            conn.close();
        } catch (SQLException ignored) {
            // abaikan
        }
    }
}
