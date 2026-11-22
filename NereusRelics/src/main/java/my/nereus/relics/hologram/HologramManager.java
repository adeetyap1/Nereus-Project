package my.nereus.relics.hologram;

import my.nereus.relics.NereusRelics;
import my.nereus.relics.altar.Altar;
import my.nereus.relics.offering.Requirement;
import my.nereus.relics.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Hologram teks memakai TextDisplay (multi-baris, color code, update dinamis). */
public final class HologramManager {

    public static final String ROLE = "hologram";

    private final NereusRelics plugin;
    private final Map<String, String> lastText = new ConcurrentHashMap<>();

    public HologramManager(NereusRelics plugin) {
        this.plugin = plugin;
    }

    public TextDisplay find(Altar a) {
        UUID id = a.hologramEntity();
        if (id == null) return null;
        Entity e = org.bukkit.Bukkit.getEntity(id);
        return e instanceof TextDisplay t && t.isValid() ? t : null;
    }

    public TextDisplay spawn(Altar a) {
        Location base = plugin.altars().baseLocation(a);
        if (base == null) return null;
        double h = plugin.altars().holoHeight(a);
        Location l = base.clone().add(0, h, 0);
        Component text = render(a);
        TextDisplay d = l.getWorld().spawn(l, TextDisplay.class, e -> {
            e.text(text);
            e.setBillboard(Display.Billboard.CENTER);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setLineWidth(plugin.getConfig().getInt("hologram.line-width", 260));
            e.setSeeThrough(false);
            e.setShadowed(true);
            e.setDefaultBackground(false);
            e.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
            e.setViewRange((float) (plugin.getConfig().getDouble("hologram.view-range", 48) / 64.0));
            e.setGravity(false);
            e.setInvulnerable(true);
            e.setPersistent(true);
            e.getPersistentDataContainer().set(plugin.keys().entityAltar, PersistentDataType.STRING, a.id());
            e.getPersistentDataContainer().set(plugin.keys().entityRole, PersistentDataType.STRING, ROLE);
        });
        a.hologramEntity(d.getUniqueId());
        lastText.put(a.id(), Text.plain(text));
        return d;
    }

    public void remove(Altar a) {
        UUID id = a.hologramEntity();
        if (id != null) {
            plugin.clearLag().expect(id);
            Entity e = org.bukkit.Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        a.hologramEntity(null);
        lastText.remove(a.id());
    }

    /** Memperbarui teks hanya bila berubah (hemat paket). */
    public void update(Altar a) {
        TextDisplay d = find(a);
        if (d == null) return;
        Component c = render(a);
        String plain = Text.plain(c);
        if (plain.equals(lastText.get(a.id()))) return;
        d.text(c);
        lastText.put(a.id(), plain);
    }

    public Component render(Altar a) {
        List<Component> lines = new ArrayList<>();
        for (String l : expand(a)) lines.add(Text.color(l));
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    private List<String> expand(Altar a) {
        boolean ritual = a.state() == Altar.State.RITUAL;
        List<String> tpl = plugin.getConfig().getStringList(ritual ? "hologram.ritual" : "hologram.active");
        List<String> out = new ArrayList<>();
        String reqFmt = plugin.getConfig().getString("hologram.requirement-line", "&f{amount}x {item}");
        Map.Entry<UUID, Integer> leader = a.leader();
        UUID claimant = a.claimant();
        for (String line : tpl) {
            if (line.contains("{lore}")) {
                if (a.lore().isEmpty()) continue;
                for (String lore : a.lore()) out.add(line.replace("{lore}", lore));
                continue;
            }
            if (line.contains("{requirements}")) {
                for (Requirement r : a.requirements()) {
                    out.add(line.replace("{requirements}",
                            reqFmt.replace("{amount}", String.valueOf(r.amount())).replace("{item}", r.label())));
                }
                continue;
            }
            out.add(line
                    .replace("{weapon}", a.weaponName())
                    .replace("{status}", ritual ? "RITUAL" : "AKTIF")
                    .replace("{leader}", leader == null ? "-" : plugin.nameOf(leader.getKey()))
                    .replace("{leader_percent}", leader == null ? "0" : String.valueOf(leader.getValue()))
                    .replace("{last}", a.lastOffer())
                    .replace("{claimant}", claimant == null ? "-" : plugin.nameOf(claimant))
                    .replace("{seconds}", String.valueOf(plugin.rituals().remainingSeconds(a)))
                    .replace("{owner}", a.ownerName() == null ? "-" : a.ownerName()));
        }
        return out;
    }
}
