package my.nereus.relics.altar;

import my.nereus.relics.NereusRelics;
import my.nereus.relics.offering.Requirement;
import my.nereus.relics.storage.AltarRepository.DeliverySpec;
import my.nereus.relics.util.ItemCodec;
import my.nereus.relics.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Registri altar + seluruh transisi state (aktifkan, nonaktifkan, reset, hapus) dan pemulihan entity. */
public final class AltarManager {

    private final NereusRelics plugin;
    private final Map<String, Altar> altars = new ConcurrentHashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();

    public AltarManager(NereusRelics plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ registri

    public void load(List<Altar> list) {
        for (Altar a : list) altars.put(a.id().toLowerCase(Locale.ROOT), a);
    }

    public Altar get(String id) {
        return id == null ? null : altars.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Altar> all() {
        return altars.values();
    }

    public Altar runningAltar() {
        for (Altar a : altars.values()) if (a.isRunning()) return a;
        return null;
    }

    public Altar create(String id) {
        Altar a = new Altar(id.toLowerCase(Locale.ROOT));
        if (altars.putIfAbsent(a.id(), a) != null) return null;
        plugin.repo().saveAsync(a);
        return a;
    }

    public void save(Altar a) {
        plugin.repo().saveAsync(a);
    }

    // ------------------------------------------------------------------ geometri & setting

    public Location baseLocation(Altar a) {
        AltarLocation l = a.location();
        return l == null ? null : l.toLocation();
    }

    private double num(Altar a, String key, String path, double def) {
        try {
            return Double.parseDouble(a.setting(key, String.valueOf(plugin.getConfig().getDouble(path, def))));
        } catch (NumberFormatException e) {
            return plugin.getConfig().getDouble(path, def);
        }
    }

    public double holoHeight(Altar a) { return num(a, "hologram-height", "hologram.height", 3.7); }
    public double weaponHeight(Altar a) { return num(a, "weapon-height", "weapon.height", 1.4); }
    public double offerRadius(Altar a) { return num(a, "offer-radius", "offering.radius", 2.5); }

    public String str(Altar a, String key, String path) {
        return a.setting(key, plugin.getConfig().getString(path, ""));
    }

    /** Titik berada di dalam zona proteksi blok altar. */
    public boolean inZone(Altar a, Location l) {
        AltarLocation al = a.location();
        if (al == null || l.getWorld() == null || !l.getWorld().getName().equals(al.world())) return false;
        int r = plugin.getConfig().getInt("protection.block-radius", 3);
        int depth = plugin.getConfig().getInt("protection.depth", 3);
        int height = plugin.getConfig().getInt("protection.height", 6);
        int bx = (int) Math.floor(al.x()), by = (int) Math.floor(al.y()), bz = (int) Math.floor(al.z());
        return Math.abs(l.getBlockX() - bx) <= r && Math.abs(l.getBlockZ() - bz) <= r
                && l.getBlockY() >= by - depth && l.getBlockY() <= by + height;
    }

    /** Altar berjalan yang zonanya memuat lokasi (atau null). */
    public Altar zoneAt(Location l) {
        for (Altar a : altars.values()) if (a.isRunning() && inZone(a, l)) return a;
        return null;
    }

    // ------------------------------------------------------------------ siklus hidup plugin

    /** Dipanggil satu tick setelah enable: pulihkan altar yang sedang berjalan dan mulai task berkala. */
    public void startup() {
        for (Altar a : altars.values()) {
            if (a.isRunning()) {
                ensureEntities(a, true);
                if (a.state() == Altar.State.RITUAL) plugin.rituals().resume(a);
            } else {
                cleanOrphansNear(a);
            }
        }
        int anim = Math.max(5, plugin.getConfig().getInt("weapon.animation-interval-ticks", 20));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Altar a : altars.values()) {
                if (!a.isRunning()) continue;
                plugin.weapons().tick(a);
                plugin.holograms().update(a);
            }
        }, anim, anim));
        int scan = Math.max(2, plugin.getConfig().getInt("offering.scan-interval-ticks", 10));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> plugin.offerings().scan(), scan, scan));
        long val = Math.max(10, plugin.getConfig().getInt("entity-recovery.validate-interval-seconds", 60)) * 20L;
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Altar a : altars.values()) if (a.isRunning()) ensureEntities(a, false);
            plugin.clearLag().purgeExpected();
        }, val, val));
    }

    public void shutdown() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
    }

    // ------------------------------------------------------------------ entity: pastikan ada, tanpa duplikat

    private void cleanOrphansNear(Altar a) {
        Location base = baseLocation(a);
        if (base == null) return;
        World w = base.getWorld();
        if (!w.isChunkLoaded(a.location().chunkX(), a.location().chunkZ())) return;
        reconcile(w.getChunkAt(a.location().chunkX(), a.location().chunkZ()));
    }

    /** Hapus entity berpenanda yatim/duplikat di chunk, dan perbaiki referensi UUID. */
    public void reconcile(Chunk ch) {
        Map<String, Map<String, List<Entity>>> byAltar = new HashMap<>();
        for (Entity e : ch.getEntities()) {
            String id = e.getPersistentDataContainer().get(plugin.keys().entityAltar, PersistentDataType.STRING);
            if (id == null) continue;
            String role = e.getPersistentDataContainer().get(plugin.keys().entityRole, PersistentDataType.STRING);
            if (role == null) continue;
            Altar a = get(id);
            if (a == null || !a.isRunning()) {
                plugin.clearLag().expect(e.getUniqueId());
                e.remove();
                continue;
            }
            byAltar.computeIfAbsent(id, k -> new HashMap<>()).computeIfAbsent(role, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<String, Map<String, List<Entity>>> en : byAltar.entrySet()) {
            Altar a = get(en.getKey());
            if (a == null) continue;
            boolean changed = false;
            for (Map.Entry<String, List<Entity>> re : en.getValue().entrySet()) {
                boolean weapon = re.getKey().equals(my.nereus.relics.weapon.WeaponDisplayManager.ROLE);
                UUID stored = weapon ? a.weaponEntity() : a.hologramEntity();
                List<Entity> list = re.getValue();
                Entity keep = list.get(0);
                for (Entity e : list) if (e.getUniqueId().equals(stored)) keep = e;
                for (Entity e : list) {
                    if (e != keep) {
                        plugin.clearLag().expect(e.getUniqueId());
                        e.remove();
                    }
                }
                if (!keep.getUniqueId().equals(stored)) {
                    if (weapon) a.weaponEntity(keep.getUniqueId());
                    else a.hologramEntity(keep.getUniqueId());
                    changed = true;
                }
            }
            if (changed) save(a);
        }
    }

    /** Pastikan weapon + hologram ada tepat satu untuk altar yang berjalan. */
    public void ensureEntities(Altar a, boolean force) {
        if (!a.isRunning()) return;
        Location base = baseLocation(a);
        if (base == null) return;
        World w = base.getWorld();
        int cx = a.location().chunkX(), cz = a.location().chunkZ();
        if (!w.isChunkLoaded(cx, cz)) {
            if (!force) return;
        }
        Chunk ch = w.getChunkAt(cx, cz);
        if (!ch.isEntitiesLoaded() && !force) return;
        reconcile(ch);
        boolean spawned = false;
        if (plugin.weapons().find(a) == null) {
            plugin.weapons().spawn(a);
            spawned = true;
        }
        if (plugin.holograms().find(a) == null) {
            plugin.holograms().spawn(a);
            spawned = true;
        }
        if (spawned) save(a);
    }

    private void removeEntities(Altar a) {
        plugin.weapons().remove(a);
        plugin.holograms().remove(a);
    }

    // ------------------------------------------------------------------ transisi admin

    /** @return pesan galat, atau null bila sukses. */
    public String activate(Altar a) {
        if (a.location() == null) return "Posisi altar belum diatur (/altar setpos).";
        if (a.weaponItem() == null) return "Senjata belum diatur (/altar setweapon).";
        if (a.requirements().isEmpty()) return "Belum ada item persembahan (/altar additem).";
        if (baseLocation(a) == null) return "Dunia altar tidak ditemukan.";
        for (Altar o : altars.values()) {
            if (o != a && o.isRunning()) return "Altar '" + o.id() + "' masih berjalan. Hanya satu altar boleh aktif.";
        }
        if (!a.activate()) return "Altar harus INACTIVE (sekarang " + a.state() + "). Gunakan /altar reset bila sudah CLAIMED.";
        try {
            plugin.repo().saveSync(a);
        } catch (SQLException e) {
            a.deactivate();
            plugin.getLogger().severe("Gagal menyimpan aktivasi: " + e);
            return "Database gagal menyimpan, altar tidak diaktifkan.";
        }
        ensureEntities(a, true);
        Location fx = baseLocation(a).add(0, weaponHeight(a), 0);
        plugin.effects().sound(fx, plugin.getConfig().getString("effects.activate-sound", ""), 2.0f, 1.0f);
        announce("activate", a, null);
        return null;
    }

    public String deactivate(Altar a) {
        if (a.state() == Altar.State.RITUAL) return "Ritual sedang berlangsung, tunggu selesai.";
        if (!a.deactivate()) return "Altar tidak sedang ACTIVE (sekarang " + a.state() + ").";
        List<DeliverySpec> specs = plugin.getConfig().getBoolean("offering.refund-on-deactivate", true)
                ? refundSpecs(a, null) : List.of();
        List<Long> ids;
        try {
            ids = plugin.repo().commit(a, true, false, specs);
        } catch (SQLException e) {
            a.activate();
            plugin.getLogger().severe("Gagal menonaktifkan: " + e);
            return "Database gagal, altar tetap aktif.";
        }
        a.progress().clear();
        removeEntities(a);
        plugin.offerings().releaseAround(a);
        save(a);
        plugin.deliveries().enqueue(specs, ids);
        return null;
    }

    public String reset(Altar a) {
        if (a.state() == Altar.State.RITUAL) return "Ritual sedang berlangsung, tunggu selesai.";
        if (a.state() == Altar.State.ACTIVE) {
            String err = deactivate(a);
            if (err != null) return err;
        }
        if (!a.reset()) return "Reset gagal.";
        try {
            plugin.repo().commit(a, true, false, List.of());
        } catch (SQLException e) {
            plugin.getLogger().severe("Gagal menyimpan reset: " + e);
            return "Database gagal menyimpan reset (state di memori sudah INACTIVE).";
        }
        a.progress().clear();
        return null;
    }

    public String remove(Altar a) {
        if (a.state() == Altar.State.RITUAL) return "Ritual sedang berlangsung, tunggu selesai.";
        boolean wasActive = a.state() == Altar.State.ACTIVE;
        List<DeliverySpec> specs = wasActive && plugin.getConfig().getBoolean("offering.refund-on-deactivate", true)
                ? refundSpecs(a, null) : List.of();
        List<Long> ids;
        try {
            ids = plugin.repo().commit(a, true, true, specs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Gagal menghapus altar: " + e);
            return "Database gagal, altar tidak dihapus.";
        }
        if (wasActive) a.deactivate();
        removeEntities(a);
        plugin.offerings().releaseAround(a);
        a.progress().clear();
        altars.remove(a.id());
        plugin.deliveries().enqueue(specs, ids);
        return null;
    }

    // ------------------------------------------------------------------ refund

    /** Spesifikasi pengembalian seluruh progres (kecuali pemain 'exclude'). */
    public List<DeliverySpec> refundSpecs(Altar a, UUID exclude) {
        List<DeliverySpec> out = new ArrayList<>();
        for (Map.Entry<UUID, int[]> en : a.progressSnapshot().entrySet()) {
            if (en.getKey().equals(exclude)) continue;
            int[] pr = en.getValue();
            for (int i = 0; i < pr.length && i < a.requirements().size(); i++) {
                if (pr[i] <= 0) continue;
                Requirement r = a.requirements().get(i);
                out.add(new DeliverySpec(en.getKey(), ItemCodec.encode(r.newStack(1)), pr[i], "REFUND", a.id()));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ pengumuman

    public void announce(String key, Altar a, String player) {
        List<String> tpl = plugin.getConfig().getStringList("announcements." + key);
        List<Component> lines = new ArrayList<>();
        for (String line : tpl) {
            if (line.contains("{location}")) {
                for (String loc : locationLines(a)) lines.add(Text.color(fill(loc, a, player)));
                continue;
            }
            lines.add(Text.color(fill(line, a, player)));
        }
        for (Player p : Bukkit.getOnlinePlayers()) for (Component c : lines) p.sendMessage(c);
        for (Component c : lines) Bukkit.getConsoleSender().sendMessage(c);
    }

    private String fill(String s, Altar a, String player) {
        return s.replace("{weapon}", a.weaponName()).replace("{player}", player == null ? "-" : player);
    }

    private List<String> locationLines(Altar a) {
        AltarLocation l = a.location();
        if (l == null) return List.of();
        switch (a.announceMode()) {
            case SECRET:
                return List.of();
            case ADMIN:
                return a.announceText().isBlank() ? List.of() : Text.splitPipe(a.announceText());
            case HIDDEN: {
                List<String> out = new ArrayList<>();
                for (String s : plugin.getConfig().getStringList("announcements.location-hidden")) {
                    out.add(s.replace("{region}", region(l)));
                }
                return out;
            }
            case PUBLIC:
            default: {
                List<String> out = new ArrayList<>();
                for (String s : plugin.getConfig().getStringList("announcements.location-public")) {
                    out.add(s.replace("{world}", l.world())
                            .replace("{x}", String.valueOf((int) Math.floor(l.x())))
                            .replace("{y}", String.valueOf((int) Math.floor(l.y())))
                            .replace("{z}", String.valueOf((int) Math.floor(l.z()))));
                }
                return out;
            }
        }
    }

    /** Wilayah kasar relatif terhadap spawn dunia (tanpa koordinat). */
    private String region(AltarLocation l) {
        World w = Bukkit.getWorld(l.world());
        double sx = 0, sz = 0;
        if (w != null) {
            sx = w.getSpawnLocation().getX();
            sz = w.getSpawnLocation().getZ();
        }
        double dx = l.x() - sx, dz = l.z() - sz;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 300) return "Sekitar Spawn";
        String[] names = {"Timur", "Tenggara", "Selatan", "Barat Daya", "Barat", "Barat Laut", "Utara", "Timur Laut"};
        double ang = Math.toDegrees(Math.atan2(dz, dx));
        if (ang < 0) ang += 360;
        String dir = names[(int) Math.round(ang / 45.0) % 8];
        return dir + (dist > 2500 ? " (jauh dari Spawn)" : " Spawn");
    }
}
