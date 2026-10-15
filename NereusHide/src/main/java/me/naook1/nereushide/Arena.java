package me.naook1.nereushide;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

/** The single arena: lobby, hide spawn, seeker room, end point and the two corners of the play area. */
public final class Arena {

    private final NereusHide plugin;
    private final File file;
    private Location lobby;
    private Location hide;
    private Location seeker;
    private Location end;
    private Location pos1;
    private Location pos2;

    public Arena(NereusHide plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arena.yml");
    }

    public void load() {
        lobby = hide = seeker = end = pos1 = pos2 = null;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        lobby = y.getLocation("lobby");
        hide = y.getLocation("hide");
        seeker = y.getLocation("seeker");
        end = y.getLocation("end");
        pos1 = y.getLocation("pos1");
        pos2 = y.getLocation("pos2");
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("lobby", lobby);
        y.set("hide", hide);
        y.set("seeker", seeker);
        y.set("end", end);
        y.set("pos1", pos1);
        y.set("pos2", pos2);
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            Files.writeString(file.toPath(), y.saveToString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save arena.yml: " + e.getMessage());
        }
    }

    public void set(String what, Location at) {
        Location l = at.clone();
        switch (what) {
            case "lobby" -> lobby = l;
            case "hide" -> hide = l;
            case "seeker" -> seeker = l;
            case "end" -> end = l;
            case "pos1" -> pos1 = l;
            case "pos2" -> pos2 = l;
            default -> {
                return;
            }
        }
        save();
    }

    public Location lobby() {
        return lobby;
    }

    public Location hide() {
        return hide;
    }

    /** Seeker waiting room; falls back to the hide spawn. */
    public Location seekerRoom() {
        return seeker != null ? seeker : hide;
    }

    public Location end() {
        return end;
    }

    public boolean hasBounds() {
        return pos1 != null && pos2 != null && pos1.getWorld() != null && pos1.getWorld().equals(pos2.getWorld());
    }

    /** Required parts: lobby, hide spawn and both corners. */
    public boolean complete() {
        return lobby != null && hide != null && hasBounds();
    }

    public String missing() {
        StringBuilder sb = new StringBuilder();
        if (lobby == null) {
            sb.append("lobby ");
        }
        if (hide == null) {
            sb.append("hide ");
        }
        if (!hasBounds()) {
            sb.append("pos1/pos2 ");
        }
        return sb.toString().trim();
    }

    /** Inside the play area. Height is ignored unless arena.check-height is true (corners are usually set on the ground). */
    public boolean contains(Location l) {
        if (!hasBounds()) {
            return true;
        }
        if (l == null || l.getWorld() == null || !l.getWorld().equals(pos1.getWorld())) {
            return false;
        }
        boolean flat = l.getX() >= Math.min(pos1.getX(), pos2.getX()) && l.getX() <= Math.max(pos1.getX(), pos2.getX()) + 1
                && l.getZ() >= Math.min(pos1.getZ(), pos2.getZ()) && l.getZ() <= Math.max(pos1.getZ(), pos2.getZ()) + 1;
        if (!flat) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("arena.check-height", false)) {
            return true;
        }
        return l.getY() >= Math.min(pos1.getY(), pos2.getY()) - 2 && l.getY() <= Math.max(pos1.getY(), pos2.getY()) + 3;
    }

    /** The hide spawn must be inside the play area, otherwise players would be pulled back forever. */
    public boolean hideInside() {
        return hide == null || contains(hide);
    }
}
