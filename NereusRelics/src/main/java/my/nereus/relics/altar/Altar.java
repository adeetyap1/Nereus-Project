package my.nereus.relics.altar;

import my.nereus.relics.offering.Requirement;
import my.nereus.relics.util.Keys;
import my.nereus.relics.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Model satu altar. Perubahan state hanya lewat metode synchronized (kunci untuk atomic claim). */
public final class Altar {

    public enum State { INACTIVE, ACTIVE, RITUAL, CLAIMED }

    public enum AnnounceMode { PUBLIC, HIDDEN, SECRET, ADMIN }

    private final String id;
    private volatile String weaponName = "Relik Kuno";
    private volatile List<String> lore = new ArrayList<>();
    private volatile AltarLocation location;
    private volatile ItemStack weaponItem;
    private final List<Requirement> requirements = new CopyOnWriteArrayList<>();
    private volatile int ritualSeconds = 5;
    private volatile AnnounceMode announceMode = AnnounceMode.PUBLIC;
    private volatile String announceText = "";

    private State state = State.INACTIVE;
    private UUID claimant;
    private UUID ownerUuid;
    private String ownerName;
    private long claimedAt;
    private volatile String serial = UUID.randomUUID().toString();

    private volatile UUID weaponEntity;
    private volatile UUID hologramEntity;
    private final Map<String, String> settings = new ConcurrentHashMap<>();

    /** progres per pemain: indeks = indeks requirement. */
    private final Map<UUID, int[]> progress = new ConcurrentHashMap<>();
    private volatile String lastOffer = "-";

    public Altar(String id) {
        this.id = id;
    }

    public String id() { return id; }
    public String weaponName() { return weaponName; }
    public void weaponName(String n) { this.weaponName = n; }
    public List<String> lore() { return lore; }
    public void lore(List<String> l) { this.lore = new ArrayList<>(l); }
    public AltarLocation location() { return location; }
    public void location(AltarLocation l) { this.location = l; }
    public ItemStack weaponItem() { return weaponItem == null ? null : weaponItem.clone(); }
    public void weaponItem(ItemStack i) { this.weaponItem = i == null ? null : i.clone(); }
    public List<Requirement> requirements() { return requirements; }
    public int ritualSeconds() { return ritualSeconds; }
    public void ritualSeconds(int s) { this.ritualSeconds = Math.max(1, Math.min(120, s)); }
    public AnnounceMode announceMode() { return announceMode; }
    public void announceMode(AnnounceMode m) { this.announceMode = m; }
    public String announceText() { return announceText; }
    public void announceText(String t) { this.announceText = t == null ? "" : t; }
    public String serial() { return serial; }
    public void serial(String s) { this.serial = s; }
    public UUID weaponEntity() { return weaponEntity; }
    public void weaponEntity(UUID u) { this.weaponEntity = u; }
    public UUID hologramEntity() { return hologramEntity; }
    public void hologramEntity(UUID u) { this.hologramEntity = u; }
    public Map<String, String> settings() { return settings; }
    public Map<UUID, int[]> progress() { return progress; }
    public String lastOffer() { return lastOffer; }
    public void lastOffer(String s) { this.lastOffer = s; }

    // ------------------------------------------------------------ state (atomik)

    public synchronized State state() { return state; }
    public synchronized UUID claimant() { return claimant; }
    public synchronized UUID ownerUuid() { return ownerUuid; }
    public synchronized String ownerName() { return ownerName; }
    public synchronized long claimedAt() { return claimedAt; }

    public synchronized boolean isRunning() { return state == State.ACTIVE || state == State.RITUAL; }

    /** Dipakai saat load dari database. */
    public synchronized void restoreState(State s, UUID claimant, UUID owner, String ownerName, long claimedAt) {
        this.state = s;
        this.claimant = claimant;
        this.ownerUuid = owner;
        this.ownerName = ownerName;
        this.claimedAt = claimedAt;
    }

    public synchronized boolean activate() {
        if (state != State.INACTIVE) return false;
        state = State.ACTIVE;
        claimant = null;
        return true;
    }

    public synchronized boolean deactivate() {
        if (state != State.ACTIVE) return false;
        state = State.INACTIVE;
        return true;
    }

    /** Atomic: hanya satu pemain yang dapat mengunci klaim. */
    public synchronized boolean tryBeginRitual(UUID player) {
        if (state != State.ACTIVE || claimant != null) return false;
        state = State.RITUAL;
        claimant = player;
        return true;
    }

    /** Atomic: RITUAL -> CLAIMED hanya untuk pemilik kunci klaim, satu kali. */
    public synchronized boolean tryFinishClaim(UUID player, String name) {
        if (state != State.RITUAL || claimant == null || !claimant.equals(player)) return false;
        state = State.CLAIMED;
        ownerUuid = player;
        ownerName = name;
        claimedAt = System.currentTimeMillis();
        return true;
    }

    /** Ritual dibatalkan karena validasi gagal: kembali ACTIVE tanpa pemegang klaim. */
    public synchronized boolean revertRitual() {
        if (state != State.RITUAL) return false;
        state = State.ACTIVE;
        claimant = null;
        return true;
    }

    /** Admin reset: kembali INACTIVE dan hapus data klaim. */
    public synchronized boolean reset() {
        if (state == State.RITUAL) return false;
        state = State.INACTIVE;
        claimant = null;
        ownerUuid = null;
        ownerName = null;
        claimedAt = 0;
        serial = UUID.randomUUID().toString();
        return true;
    }

    // ------------------------------------------------------------ progres

    public int[] progressOf(UUID p) {
        return progress.computeIfAbsent(p, k -> new int[Math.max(1, requirements.size())]);
    }

    public int percent(UUID p) {
        int[] pr = progress.get(p);
        if (pr == null || requirements.isEmpty()) return 0;
        long have = 0, need = 0;
        for (int i = 0; i < requirements.size(); i++) {
            int req = requirements.get(i).amount();
            need += req;
            have += Math.min(req, i < pr.length ? pr[i] : 0);
        }
        return need == 0 ? 0 : (int) (have * 100 / need);
    }

    public boolean isComplete(UUID p) {
        int[] pr = progress.get(p);
        if (pr == null || requirements.isEmpty()) return false;
        for (int i = 0; i < requirements.size(); i++) {
            if ((i < pr.length ? pr[i] : 0) < requirements.get(i).amount()) return false;
        }
        return true;
    }

    /** Pemain dengan progres tertinggi (atau null). */
    public Map.Entry<UUID, Integer> leader() {
        UUID best = null;
        int bestPct = -1;
        for (UUID u : progress.keySet()) {
            int p = percent(u);
            if (p > bestPct) {
                bestPct = p;
                best = u;
            }
        }
        return best == null || bestPct <= 0 ? null : Map.entry(best, bestPct);
    }

    public Map<UUID, int[]> progressSnapshot() {
        Map<UUID, int[]> copy = new HashMap<>();
        progress.forEach((k, v) -> copy.put(k, v.clone()));
        return copy;
    }

    // ------------------------------------------------------------ setting per altar

    public String setting(String key, String def) {
        String v = settings.get(key);
        return v == null || v.isBlank() ? def : v;
    }

    // ------------------------------------------------------------ item senjata

    /** Item senjata final (nama, lore, penanda unik PDC). */
    public ItemStack buildWeapon(Keys keys) {
        ItemStack item = weaponItem();
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.itemText(weaponName));
        List<Component> lines = new ArrayList<>();
        if (meta.hasLore() && meta.lore() != null) lines.addAll(meta.lore());
        for (String l : lore) lines.add(Text.itemText(l));
        if (!lines.isEmpty()) meta.lore(lines);
        meta.getPersistentDataContainer().set(keys.weaponAltar, PersistentDataType.STRING, id);
        meta.getPersistentDataContainer().set(keys.weaponSerial, PersistentDataType.STRING, serial);
        item.setItemMeta(meta);
        return item;
    }
}
