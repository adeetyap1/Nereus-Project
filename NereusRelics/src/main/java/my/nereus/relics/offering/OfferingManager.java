package my.nereus.relics.offering;

import my.nereus.relics.NereusRelics;
import my.nereus.relics.altar.Altar;
import my.nereus.relics.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Persembahan: item yang dilempar pemain ke area altar dihitung per pemain (kontribusi tidak pernah digabung). */
public final class OfferingManager {

    private final NereusRelics plugin;

    public OfferingManager(NereusRelics plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ penanda item

    public boolean isMarked(Item item) {
        return item.getPersistentDataContainer().has(plugin.keys().offerThrower, PersistentDataType.STRING);
    }

    public UUID markerOf(Item item) {
        String s = item.getPersistentDataContainer().get(plugin.keys().offerThrower, PersistentDataType.STRING);
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean creativeBlocked(Player p) {
        if (plugin.getConfig().getBoolean("offering.allow-creative", false)) return false;
        return p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
    }

    private boolean matchesAny(Altar a, ItemStack s) {
        for (Requirement r : a.requirements()) if (r.matches(s)) return true;
        return false;
    }

    /** Dipanggil saat pemain menjatuhkan item. Melindungi item yang cocok dengan persembahan. */
    public void onDrop(Player p, Item item) {
        double guard = plugin.getConfig().getDouble("offering.guard-range", 12.0);
        Location at = item.getLocation();
        for (Altar a : plugin.altars().all()) {
            if (!a.isRunning()) continue;
            Location base = plugin.altars().baseLocation(a);
            if (base == null || base.getWorld() != at.getWorld() || base.distanceSquared(at) > guard * guard) continue;
            ItemStack s = item.getItemStack();
            if (!matchesAny(a, s)) continue;
            if (a.state() == Altar.State.RITUAL) {
                plugin.tell(p, "ritual-busy");
                return;
            }
            if (creativeBlocked(p)) {
                plugin.tell(p, "creative-blocked");
                return;
            }
            item.getPersistentDataContainer().set(plugin.keys().offerThrower, PersistentDataType.STRING, p.getUniqueId().toString());
            item.setInvulnerable(true);
            item.setOwner(p.getUniqueId());
            return;
        }
    }

    /** Lepas perlindungan (item sisa dikembalikan kepada pelempar). */
    public void release(Item item, UUID thrower) {
        item.getPersistentDataContainer().remove(plugin.keys().offerThrower);
        item.setThrower(null);
        item.setInvulnerable(false);
        item.setPickupDelay(0);
        if (thrower != null) item.setOwner(thrower);
    }

    /** Lepas semua item bertanda di sekitar altar (nonaktif/reset/hapus/klaim). */
    public void releaseAround(Altar a) {
        Location base = plugin.altars().baseLocation(a);
        if (base == null || !base.getWorld().isChunkLoaded(a.location().chunkX(), a.location().chunkZ())) return;
        double g = plugin.getConfig().getDouble("offering.guard-range", 12.0);
        for (Entity e : base.getWorld().getNearbyEntities(base, g, g, g, x -> x instanceof Item)) {
            Item it = (Item) e;
            if (isMarked(it)) release(it, markerOf(it));
        }
    }

    // ------------------------------------------------------------------ pemindaian

    public void scan() {
        for (Altar a : plugin.altars().all()) {
            if (a.state() == Altar.State.ACTIVE) scanAltar(a);
        }
    }

    private void scanAltar(Altar a) {
        Location base = plugin.altars().baseLocation(a);
        if (base == null) return;
        World w = base.getWorld();
        if (!w.isChunkLoaded(a.location().chunkX(), a.location().chunkZ())) return;
        double r = plugin.altars().offerRadius(a);
        double below = plugin.getConfig().getDouble("offering.height-below", 2.0);
        double above = plugin.getConfig().getDouble("offering.height-above", 4.0);
        Location center = new Location(w, base.getX(), base.getY() + (above - below) / 2.0, base.getZ());
        Collection<Entity> found = w.getNearbyEntities(center, r, (above + below) / 2.0, r, e -> e instanceof Item);
        for (Entity e : found) {
            if (a.state() != Altar.State.ACTIVE) break;
            processItem(a, (Item) e, base);
        }
        if (a.state() == Altar.State.ACTIVE) {
            for (UUID u : new ArrayList<>(a.progress().keySet())) {
                if (a.isComplete(u)) {
                    plugin.rituals().begin(a, u);
                    break;
                }
            }
        }
    }

    private void processItem(Altar a, Item item, Location base) {
        if (!item.isValid() || item.isDead()) return;
        UUID thrower = item.getThrower();
        if (thrower == null) return;
        Player online = Bukkit.getPlayer(thrower);
        if (online != null && creativeBlocked(online)) return;

        ItemStack stack = item.getItemStack();
        int[] pr = a.progressOf(thrower);
        if (pr.length < a.requirements().size()) {
            pr = Arrays.copyOf(pr, a.requirements().size());
            a.progress().put(thrower, pr);
        }
        int idx = -1;
        boolean matchedButFull = false;
        for (int i = 0; i < a.requirements().size(); i++) {
            Requirement r = a.requirements().get(i);
            if (!r.matches(stack)) continue;
            if (pr[i] < r.amount()) {
                idx = i;
                break;
            }
            matchedButFull = true;
        }
        if (idx < 0) {
            if (matchedButFull) {
                release(item, thrower);
                if (online != null) plugin.tellRaw(online, "&7Persembahan &f" + Text.itemLabel(stack) + " &7sudah penuh, item dikembalikan.");
            }
            return;
        }

        Requirement req = a.requirements().get(idx);
        int take = Math.min(req.amount() - pr[idx], stack.getAmount());
        int left = stack.getAmount() - take;
        pr[idx] += take;
        if (left <= 0) {
            plugin.clearLag().expect(item.getUniqueId());
            item.remove();
        } else {
            ItemStack rest = stack.clone();
            rest.setAmount(left);
            item.setItemStack(rest);
            release(item, thrower);
        }
        plugin.repo().saveProgressAsync(a.id(), thrower, idx, pr[idx]);
        a.lastOffer(plugin.nameOf(thrower) + " (" + take + "x " + req.label() + ")");

        plugin.effects().sound(base.clone().add(0, 1, 0), plugin.altars().str(a, "offer-sound", "effects.offer-sound"), 1.0f, 1.2f);
        plugin.effects().particle(base.clone().add(0, 1.2, 0), "happy_villager", 12, 0.4, 0.4, 0.4, 0.0);

        if (online != null) {
            plugin.tell(online, "offered", "amount", String.valueOf(take), "item", req.label(),
                    "have", String.valueOf(pr[idx]), "need", String.valueOf(req.amount()));
            if (a.isComplete(thrower)) {
                plugin.tell(online, "all-done");
            } else {
                plugin.tell(online, "remaining", "list", remainingList(a, pr));
            }
        }
        plugin.holograms().update(a);
        if (a.isComplete(thrower)) plugin.rituals().begin(a, thrower);
    }

    private String remainingList(Altar a, int[] pr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.requirements().size(); i++) {
            Requirement r = a.requirements().get(i);
            int miss = r.amount() - (i < pr.length ? pr[i] : 0);
            if (miss <= 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(miss).append("x ").append(r.label());
        }
        return sb.toString();
    }

    /** Dipakai command info. */
    public List<String> progressLines(Altar a) {
        List<String> out = new ArrayList<>();
        for (UUID u : a.progress().keySet()) {
            out.add(plugin.nameOf(u) + ": " + a.percent(u) + "%");
        }
        return out;
    }
}
