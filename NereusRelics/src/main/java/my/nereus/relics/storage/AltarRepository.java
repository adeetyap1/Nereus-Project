package my.nereus.relics.storage;

import my.nereus.relics.altar.Altar;
import my.nereus.relics.altar.AltarLocation;
import my.nereus.relics.offering.Requirement;
import my.nereus.relics.util.ItemCodec;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/** Semua SQL ada di sini. */
public final class AltarRepository {

    /** Pengiriman item tertunda (senjata pemenang, refund). */
    public record Delivery(long id, UUID player, ItemStack item, int amount, String reason, String altarId) {}

    /** Spesifikasi pengiriman baru (item sudah diserialisasi). */
    public record DeliverySpec(UUID player, String itemB64, int amount, String reason, String altarId) {}

    private final DatabaseManager db;
    private final Logger log;

    public AltarRepository(DatabaseManager db, Logger log) {
        this.db = db;
        this.log = log;
    }

    public void createSchema() throws SQLException {
        db.call(c -> {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS altars ("
                        + "id TEXT PRIMARY KEY, weapon_name TEXT, lore TEXT, world TEXT, x REAL, y REAL, z REAL,"
                        + "weapon_item TEXT, state TEXT NOT NULL, ritual_seconds INTEGER, announce_mode TEXT, announce_text TEXT,"
                        + "claimant TEXT, owner_uuid TEXT, owner_name TEXT, claimed_at INTEGER,"
                        + "weapon_entity TEXT, hologram_entity TEXT, settings TEXT, serial TEXT, created_at INTEGER)");
                s.execute("CREATE TABLE IF NOT EXISTS requirements ("
                        + "altar_id TEXT NOT NULL, idx INTEGER NOT NULL, mode TEXT, material TEXT, item TEXT, amount INTEGER, label TEXT,"
                        + "PRIMARY KEY (altar_id, idx), FOREIGN KEY (altar_id) REFERENCES altars(id) ON DELETE CASCADE)");
                s.execute("CREATE TABLE IF NOT EXISTS progress ("
                        + "altar_id TEXT NOT NULL, player_uuid TEXT NOT NULL, idx INTEGER NOT NULL, amount INTEGER NOT NULL,"
                        + "PRIMARY KEY (altar_id, player_uuid, idx), FOREIGN KEY (altar_id) REFERENCES altars(id) ON DELETE CASCADE)");
                s.execute("CREATE TABLE IF NOT EXISTS deliveries ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL, item TEXT NOT NULL, amount INTEGER NOT NULL,"
                        + "reason TEXT, altar_id TEXT, created_at INTEGER, delivered INTEGER NOT NULL DEFAULT 0)");
                s.execute("CREATE TABLE IF NOT EXISTS players (player_uuid TEXT PRIMARY KEY, name TEXT)");
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ load

    public List<Altar> loadAll() throws SQLException {
        return db.call(c -> {
            List<Altar> out = new ArrayList<>();
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM altars")) {
                while (rs.next()) {
                    Altar a = new Altar(rs.getString("id"));
                    a.weaponName(nz(rs.getString("weapon_name"), "Relik Kuno"));
                    String lore = rs.getString("lore");
                    a.lore(lore == null || lore.isEmpty() ? List.of() : List.of(lore.split("\n", -1)));
                    String w = rs.getString("world");
                    if (w != null && !w.isEmpty()) a.location(new AltarLocation(w, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z")));
                    String wi = rs.getString("weapon_item");
                    if (wi != null && !wi.isEmpty()) a.weaponItem(ItemCodec.decode(wi));
                    a.ritualSeconds(rs.getInt("ritual_seconds"));
                    try {
                        a.announceMode(Altar.AnnounceMode.valueOf(nz(rs.getString("announce_mode"), "PUBLIC")));
                    } catch (IllegalArgumentException e) {
                        a.announceMode(Altar.AnnounceMode.PUBLIC);
                    }
                    a.announceText(rs.getString("announce_text"));
                    a.serial(nz(rs.getString("serial"), UUID.randomUUID().toString()));
                    a.weaponEntity(uuid(rs.getString("weapon_entity")));
                    a.hologramEntity(uuid(rs.getString("hologram_entity")));
                    String st = rs.getString("settings");
                    if (st != null && !st.isEmpty()) {
                        YamlConfiguration y = new YamlConfiguration();
                        try {
                            y.loadFromString(st);
                            for (String k : y.getKeys(false)) a.settings().put(k, y.getString(k, ""));
                        } catch (Exception e) {
                            log.warning("Settings altar " + a.id() + " rusak, diabaikan.");
                        }
                    }
                    Altar.State state;
                    try {
                        state = Altar.State.valueOf(rs.getString("state"));
                    } catch (IllegalArgumentException e) {
                        state = Altar.State.INACTIVE;
                    }
                    a.restoreState(state, uuid(rs.getString("claimant")), uuid(rs.getString("owner_uuid")),
                            rs.getString("owner_name"), rs.getLong("claimed_at"));
                    out.add(a);
                }
            }
            for (Altar a : out) {
                try (PreparedStatement ps = c.prepareStatement("SELECT * FROM requirements WHERE altar_id=? ORDER BY idx")) {
                    ps.setString(1, a.id());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Material m = Material.matchMaterial(nz(rs.getString("material"), "STONE"));
                            a.requirements().add(Requirement.restore(
                                    Requirement.Mode.valueOf(rs.getString("mode")), m == null ? Material.STONE : m,
                                    rs.getString("item"), rs.getInt("amount"), rs.getString("label")));
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("SELECT player_uuid, idx, amount FROM progress WHERE altar_id=?")) {
                    ps.setString(1, a.id());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int idx = rs.getInt("idx");
                            if (idx < 0 || idx >= a.requirements().size()) continue;
                            a.progressOf(UUID.fromString(rs.getString("player_uuid")))[idx] = rs.getInt("amount");
                        }
                    }
                }
            }
            return out;
        });
    }

    public List<Delivery> loadPendingDeliveries() throws SQLException {
        return db.call(c -> {
            List<Delivery> out = new ArrayList<>();
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT * FROM deliveries WHERE delivered=0 ORDER BY id")) {
                while (rs.next()) {
                    try {
                        out.add(new Delivery(rs.getLong("id"), UUID.fromString(rs.getString("player_uuid")),
                                ItemCodec.decode(rs.getString("item")), rs.getInt("amount"),
                                rs.getString("reason"), rs.getString("altar_id")));
                    } catch (RuntimeException e) {
                        log.warning("Pengiriman #" + rs.getLong("id") + " tidak bisa dibaca: " + e);
                    }
                }
            }
            return out;
        });
    }

    public Map<UUID, String> loadNames() throws SQLException {
        return db.call(c -> {
            Map<UUID, String> out = new java.util.HashMap<>();
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT player_uuid, name FROM players")) {
                while (rs.next()) out.put(UUID.fromString(rs.getString(1)), rs.getString(2));
            }
            return out;
        });
    }

    // ------------------------------------------------------------------ tulis (async)

    public void saveAsync(Altar a) {
        Row r = Row.of(a);
        db.write(c -> {
            upsert(c, r);
            return null;
        });
    }

    public void saveProgressAsync(String altarId, UUID player, int idx, int amount) {
        db.write(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO progress(altar_id, player_uuid, idx, amount) VALUES(?,?,?,?) "
                            + "ON CONFLICT(altar_id, player_uuid, idx) DO UPDATE SET amount=excluded.amount")) {
                ps.setString(1, altarId);
                ps.setString(2, player.toString());
                ps.setInt(3, idx);
                ps.setInt(4, amount);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void saveNameAsync(UUID player, String name) {
        db.write(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO players(player_uuid, name) VALUES(?,?) ON CONFLICT(player_uuid) DO UPDATE SET name=excluded.name")) {
                ps.setString(1, player.toString());
                ps.setString(2, name);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ tulis (sinkron, kritis)

    /**
     * Satu transaksi atomik: simpan altar (atau hapus), opsional hapus progres, dan buat pengiriman baru.
     * Dipakai untuk klaim, deactivate, reset, remove. @return id pengiriman sesuai urutan spec.
     */
    public List<Long> commit(Altar a, boolean clearProgress, boolean deleteAltar, List<DeliverySpec> specs) throws SQLException {
        Row row = deleteAltar ? null : Row.of(a);
        String id = a.id();
        return db.call(c -> {
            if (clearProgress || deleteAltar) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM progress WHERE altar_id=?")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
            }
            if (deleteAltar) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM requirements WHERE altar_id=?")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM altars WHERE id=?")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
            } else {
                upsert(c, row);
            }
            List<Long> ids = new ArrayList<>();
            for (DeliverySpec d : specs) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO deliveries(player_uuid, item, amount, reason, altar_id, created_at, delivered) VALUES(?,?,?,?,?,?,0)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, d.player().toString());
                    ps.setString(2, d.itemB64());
                    ps.setInt(3, d.amount());
                    ps.setString(4, d.reason());
                    ps.setString(5, d.altarId());
                    ps.setLong(6, System.currentTimeMillis());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        ids.add(rs.next() ? rs.getLong(1) : -1L);
                    }
                }
            }
            return ids;
        });
    }

    /** Sinkron: tandai terkirim SEBELUM item diberikan (mencegah duplikasi bila server crash). */
    public boolean markDelivered(long id) throws SQLException {
        return db.call(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE deliveries SET delivered=1 WHERE id=? AND delivered=0")) {
                ps.setLong(1, id);
                return ps.executeUpdate() == 1;
            }
        });
    }

    public void unmarkDelivered(long id) throws SQLException {
        db.call(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE deliveries SET delivered=0 WHERE id=?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void saveSync(Altar a) throws SQLException {
        Row r = Row.of(a);
        db.call(c -> {
            upsert(c, r);
            return null;
        });
    }

    // ------------------------------------------------------------------ internal

    private record ReqRow(int idx, String mode, String material, String item, int amount, String label) {}

    private record Row(String id, String weaponName, String lore, String world, double x, double y, double z,
                       String weaponItem, String state, int ritualSeconds, String announceMode, String announceText,
                       String claimant, String ownerUuid, String ownerName, long claimedAt,
                       String weaponEntity, String hologramEntity, String settings, String serial,
                       List<ReqRow> reqs) {

        static Row of(Altar a) {
            AltarLocation l = a.location();
            ItemStack w = a.weaponItem();
            YamlConfiguration y = new YamlConfiguration();
            a.settings().forEach(y::set);
            List<ReqRow> reqs = new ArrayList<>();
            int i = 0;
            for (Requirement r : a.requirements()) {
                reqs.add(new ReqRow(i++, r.mode().name(), r.material().name(), r.itemB64(), r.amount(), r.label()));
            }
            return new Row(a.id(), a.weaponName(), String.join("\n", a.lore()),
                    l == null ? "" : l.world(), l == null ? 0 : l.x(), l == null ? 0 : l.y(), l == null ? 0 : l.z(),
                    w == null ? "" : ItemCodec.encode(w), a.state().name(), a.ritualSeconds(),
                    a.announceMode().name(), a.announceText(),
                    a.claimant() == null ? "" : a.claimant().toString(),
                    a.ownerUuid() == null ? "" : a.ownerUuid().toString(), a.ownerName() == null ? "" : a.ownerName(),
                    a.claimedAt(),
                    a.weaponEntity() == null ? "" : a.weaponEntity().toString(),
                    a.hologramEntity() == null ? "" : a.hologramEntity().toString(),
                    y.saveToString(), a.serial(), reqs);
        }
    }

    private static void upsert(Connection c, Row r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO altars(id, weapon_name, lore, world, x, y, z, weapon_item, state, ritual_seconds, announce_mode, announce_text,"
                        + "claimant, owner_uuid, owner_name, claimed_at, weapon_entity, hologram_entity, settings, serial, created_at)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT(id) DO UPDATE SET weapon_name=excluded.weapon_name, lore=excluded.lore, world=excluded.world,"
                        + "x=excluded.x, y=excluded.y, z=excluded.z, weapon_item=excluded.weapon_item, state=excluded.state,"
                        + "ritual_seconds=excluded.ritual_seconds, announce_mode=excluded.announce_mode, announce_text=excluded.announce_text,"
                        + "claimant=excluded.claimant, owner_uuid=excluded.owner_uuid, owner_name=excluded.owner_name,"
                        + "claimed_at=excluded.claimed_at, weapon_entity=excluded.weapon_entity, hologram_entity=excluded.hologram_entity,"
                        + "settings=excluded.settings, serial=excluded.serial")) {
            int i = 1;
            ps.setString(i++, r.id());
            ps.setString(i++, r.weaponName());
            ps.setString(i++, r.lore());
            ps.setString(i++, r.world());
            ps.setDouble(i++, r.x());
            ps.setDouble(i++, r.y());
            ps.setDouble(i++, r.z());
            ps.setString(i++, r.weaponItem());
            ps.setString(i++, r.state());
            ps.setInt(i++, r.ritualSeconds());
            ps.setString(i++, r.announceMode());
            ps.setString(i++, r.announceText());
            ps.setString(i++, r.claimant());
            ps.setString(i++, r.ownerUuid());
            ps.setString(i++, r.ownerName());
            ps.setLong(i++, r.claimedAt());
            ps.setString(i++, r.weaponEntity());
            ps.setString(i++, r.hologramEntity());
            ps.setString(i++, r.settings());
            ps.setString(i++, r.serial());
            ps.setLong(i, System.currentTimeMillis());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM requirements WHERE altar_id=?")) {
            ps.setString(1, r.id());
            ps.executeUpdate();
        }
        for (ReqRow q : r.reqs()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO requirements(altar_id, idx, mode, material, item, amount, label) VALUES(?,?,?,?,?,?,?)")) {
                ps.setString(1, r.id());
                ps.setInt(2, q.idx());
                ps.setString(3, q.mode());
                ps.setString(4, q.material());
                ps.setString(5, q.item());
                ps.setInt(6, q.amount());
                ps.setString(7, q.label());
                ps.executeUpdate();
            }
        }
    }

    private static String nz(String s, String def) {
        return s == null || s.isEmpty() ? def : s;
    }

    private static UUID uuid(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
