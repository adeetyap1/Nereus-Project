package my.nereus.relics.delivery;

import my.nereus.relics.NereusRelics;
import my.nereus.relics.storage.AltarRepository.Delivery;
import my.nereus.relics.storage.AltarRepository.DeliverySpec;
import my.nereus.relics.util.ItemCodec;
import my.nereus.relics.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pengiriman item tertunda (senjata pemenang, refund). Urutan anti-duplikasi:
 * baris database ditandai "delivered" secara sinkron SEBELUM item diberikan; bila pemberian gagal, tanda dibatalkan.
 */
public final class DeliveryManager {

    private final NereusRelics plugin;
    private final Map<UUID, List<Delivery>> pending = new ConcurrentHashMap<>();

    public DeliveryManager(NereusRelics plugin) {
        this.plugin = plugin;
    }

    public void load(List<Delivery> list) {
        for (Delivery d : list) add(d);
    }

    private void add(Delivery d) {
        pending.computeIfAbsent(d.player(), k -> new CopyOnWriteArrayList<>()).add(d);
    }

    /** Dipanggil setelah transaksi database sukses; pemain online langsung menerima. */
    public void enqueue(List<DeliverySpec> specs, List<Long> ids) {
        for (int i = 0; i < specs.size() && i < ids.size(); i++) {
            DeliverySpec s = specs.get(i);
            try {
                add(new Delivery(ids.get(i), s.player(), ItemCodec.decode(s.itemB64()), s.amount(), s.reason(), s.altarId()));
            } catch (RuntimeException e) {
                plugin.getLogger().severe("Item pengiriman tidak bisa didekode: " + e);
            }
        }
        for (DeliverySpec s : specs) {
            Player p = Bukkit.getPlayer(s.player());
            if (p != null) process(p);
        }
    }

    public int pendingCount(UUID player) {
        List<Delivery> l = pending.get(player);
        return l == null ? 0 : l.size();
    }

    /** Main thread. Aman dipanggil berulang. */
    public void process(Player p) {
        if (!p.isOnline() || p.isDead()) return;
        List<Delivery> list = pending.get(p.getUniqueId());
        if (list == null || list.isEmpty()) return;
        for (Delivery d : new ArrayList<>(list)) {
            boolean claimed;
            try {
                claimed = plugin.repo().markDelivered(d.id());
            } catch (SQLException e) {
                plugin.getLogger().severe("Gagal menandai pengiriman #" + d.id() + ": " + e);
                continue;
            }
            list.remove(d);
            if (!claimed) continue; // sudah pernah terkirim
            try {
                give(p, d);
            } catch (RuntimeException e) {
                plugin.getLogger().severe("Gagal memberi item pengiriman #" + d.id() + ": " + e);
                try {
                    plugin.repo().unmarkDelivered(d.id());
                    list.add(d);
                } catch (SQLException ex) {
                    plugin.getLogger().severe("PENTING: pengiriman #" + d.id() + " perlu diperiksa manual di tabel deliveries.");
                }
                break;
            }
        }
    }

    private void give(Player p, Delivery d) {
        boolean dropped = false;
        int remaining = d.amount();
        ItemStack base = d.item();
        while (remaining > 0) {
            int n = Math.min(remaining, Math.max(1, base.getMaxStackSize()));
            ItemStack stack = base.clone();
            stack.setAmount(n);
            remaining -= n;
            for (ItemStack left : p.getInventory().addItem(stack).values()) {
                dropFor(p, left);
                dropped = true;
            }
        }
        String label = Text.itemLabel(base);
        if ("WEAPON".equals(d.reason())) {
            plugin.tell(p, "delivered-weapon", "item", label);
        } else {
            plugin.tell(p, "delivered-refund", "amount", String.valueOf(d.amount()), "item", label);
        }
        if (dropped) plugin.tell(p, "delivered-dropped");
    }

    private void dropFor(Player p, ItemStack stack) {
        Location l = p.getLocation();
        Item it = l.getWorld().dropItem(l, stack);
        it.setOwner(p.getUniqueId());
        it.setUnlimitedLifetime(true);
        it.setInvulnerable(true);
        it.setPickupDelay(0);
    }
}
