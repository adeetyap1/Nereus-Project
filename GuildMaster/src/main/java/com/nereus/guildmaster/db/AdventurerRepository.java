package com.nereus.guildmaster.db;

import com.nereus.guildmaster.adventurer.Adventurer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Query tabel gm_adventurers. */
public final class AdventurerRepository {
    private final Database db;

    public AdventurerRepository(Database db) {
        this.db = db;
    }

    public CompletableFuture<Adventurer> load(UUID id) {
        return db.call(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT name, registered_at, revoked FROM gm_adventurers WHERE uuid = ?")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Adventurer(id, rs.getString(1), rs.getLong(2), rs.getInt(3) != 0);
                    }
                    return null;
                }
            }
        });
    }

    /** Sisipkan catatan baru. false jika sudah ada (kunci utama menjaga dari duplikasi). */
    public CompletableFuture<Boolean> insert(Adventurer a) {
        return db.call(c -> {
            try (PreparedStatement chk = c.prepareStatement("SELECT 1 FROM gm_adventurers WHERE uuid = ?")) {
                chk.setString(1, a.uuid().toString());
                try (ResultSet rs = chk.executeQuery()) {
                    if (rs.next()) {
                        return false;
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO gm_adventurers (uuid, name, registered_at, revoked) VALUES (?, ?, ?, 0)")) {
                ps.setString(1, a.uuid().toString());
                ps.setString(2, a.name());
                ps.setLong(3, a.registeredAt());
                ps.executeUpdate();
                return true;
            } catch (java.sql.SQLException e) {
                // balapan: baris yang sama baru saja disisipkan di tempat lain
                try (PreparedStatement chk = c.prepareStatement("SELECT 1 FROM gm_adventurers WHERE uuid = ?")) {
                    chk.setString(1, a.uuid().toString());
                    try (ResultSet rs = chk.executeQuery()) {
                        if (rs.next()) {
                            return false;
                        }
                    }
                }
                throw e;
            }
        });
    }

    public CompletableFuture<Boolean> setRevoked(UUID id, boolean revoked) {
        return db.call(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE gm_adventurers SET revoked = ? WHERE uuid = ?")) {
                ps.setInt(1, revoked ? 1 : 0);
                ps.setString(2, id.toString());
                return ps.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Boolean> delete(UUID id) {
        return db.call(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM gm_adventurers WHERE uuid = ?")) {
                ps.setString(1, id.toString());
                return ps.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Void> updateName(UUID id, String name) {
        return db.call(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE gm_adventurers SET name = ? WHERE uuid = ?")) {
                ps.setString(1, name);
                ps.setString(2, id.toString());
                ps.executeUpdate();
                return null;
            }
        });
    }

    public CompletableFuture<Integer> count() {
        return db.call(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM gm_adventurers WHERE revoked = 0");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }
}
