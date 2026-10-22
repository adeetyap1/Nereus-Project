package me.naook1.nereusarmory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Java-only 3D parts on the wearer (crown, shoulder shells, cape) drawn as item displays that follow the player.
 * Bedrock cannot see display entities, those players see the painted armor skin instead.
 */
public final class Overlay {

    private record Part(ItemDisplay d, String kind, int side) {
    }

    private final NereusArmory plugin;
    private final Map<UUID, List<Part>> parts = new HashMap<>();
    private final Map<UUID, Float> bodyYaw = new HashMap<>();
    private final Map<UUID, Location> last = new HashMap<>();

    public Overlay(NereusArmory plugin) {
        this.plugin = plugin;
    }

    private ItemStack model(int cmd) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.setCustomModelData(cmd);
        it.setItemMeta(m);
        return it;
    }

    private Part spawn(Player p, String kind, int side, double scale) {
        int cmd = switch (kind) {
            case "crown" -> 9101;
            case "pauldron" -> 9102;
            default -> 9103;
        };
        ItemDisplay d = p.getWorld().spawn(p.getLocation(), ItemDisplay.class, e -> {
            e.setItemStack(model(cmd));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBillboard(Display.Billboard.FIXED);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setGravity(false);
            e.setTeleportDuration(2);
            e.setViewRange(1.0f);
            e.addScoreboardTag("nereusarmory_ov");
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f((float) scale), new AxisAngle4f()));
        });
        return new Part(d, kind, side);
    }

    public void remove(UUID id) {
        List<Part> l = parts.remove(id);
        if (l != null) {
            for (Part p : l) {
                p.d().remove();
            }
        }
        bodyYaw.remove(id);
        last.remove(id);
    }

    public void removeAll() {
        for (UUID id : new ArrayList<>(parts.keySet())) {
            remove(id);
        }
    }

    private static float wrap(float a) {
        a %= 360f;
        if (a >= 180f) {
            a -= 360f;
        }
        if (a < -180f) {
            a += 360f;
        }
        return a;
    }

    /** Runs every 2 ticks. */
    public void tick() {
        boolean on = plugin.getConfig().getBoolean("overlay.enabled", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean helm = "helm".equals(plugin.items().idOf(p.getInventory().getHelmet()));
            boolean chest = "chest".equals(plugin.items().idOf(p.getInventory().getChestplate()));
            boolean want = on && (helm || chest) && !p.isDead() && !p.isInvisible() && p.getGameMode() != GameMode.SPECTATOR;
            UUID id = p.getUniqueId();
            if (!want) {
                if (parts.containsKey(id)) {
                    remove(id);
                }
                continue;
            }
            List<Part> cur = parts.get(id);
            int expect = (helm ? 1 : 0) + (chest ? 3 : 0);
            boolean ok = cur != null && cur.size() == expect;
            if (ok) {
                for (Part pt : cur) {
                    if (!pt.d().isValid() || !pt.d().getWorld().equals(p.getWorld())) {
                        ok = false;
                        break;
                    }
                }
                boolean hasCrown = cur.stream().anyMatch(x -> x.kind().equals("crown"));
                if (hasCrown != helm) {
                    ok = false;
                }
            }
            var c = plugin.getConfig();
            if (!ok) {
                remove(id);
                List<Part> nl = new ArrayList<>();
                if (helm) {
                    nl.add(spawn(p, "crown", 0, c.getDouble("overlay.crown.scale", 1.0)));
                }
                if (chest) {
                    nl.add(spawn(p, "pauldron", -1, c.getDouble("overlay.pauldron.scale", 1.0)));
                    nl.add(spawn(p, "pauldron", 1, c.getDouble("overlay.pauldron.scale", 1.0)));
                    nl.add(spawn(p, "cape", 0, c.getDouble("overlay.cape.scale", 1.0)));
                }
                parts.put(id, nl);
                cur = nl;
            }
            position(p, cur);
        }
        parts.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private void position(Player p, List<Part> list) {
        var c = plugin.getConfig();
        Location pl = p.getLocation();
        float head = pl.getYaw();
        UUID id = p.getUniqueId();
        Location prev = last.put(id, pl.clone());
        boolean moving = prev != null && prev.getWorld().equals(pl.getWorld())
                && prev.toVector().setY(0).distanceSquared(pl.toVector().setY(0)) > 0.0004;
        float by = bodyYaw.getOrDefault(id, head);
        if (moving) {
            by = head;
        } else {
            float diff = wrap(head - by);
            if (Math.abs(diff) > 70f) {
                by = head - Math.signum(diff) * 70f;
            }
        }
        bodyYaw.put(id, by);
        double sneak = p.isSneaking() ? 0.22 : 0.0;
        double lean = p.isSneaking() ? 0.16 : 0.0;
        double r = Math.toRadians(by);
        double fx = -Math.sin(r), fz = Math.cos(r);       // forward
        double rx = -Math.cos(r), rz = -Math.sin(r);      // right
        double hr = Math.toRadians(head);
        double hfx = -Math.sin(hr), hfz = Math.cos(hr);
        for (Part pt : list) {
            double x = pl.getX(), y = pl.getY(), z = pl.getZ();
            float yaw = by;
            switch (pt.kind()) {
                case "crown" -> {
                    double s = c.getDouble("overlay.crown.scale", 1.0);
                    y += c.getDouble("overlay.crown.y", 1.93) - sneak + 0.5 * s;
                    x += hfx * lean;
                    z += hfz * lean;
                    yaw = head;
                }
                case "pauldron" -> {
                    double s = c.getDouble("overlay.pauldron.scale", 1.0);
                    double off = c.getDouble("overlay.pauldron.x", 0.36) * pt.side();
                    y += c.getDouble("overlay.pauldron.y", 1.38) - sneak + 0.5 * s;
                    x += rx * off + fx * lean;
                    z += rz * off + fz * lean;
                }
                default -> {
                    double s = c.getDouble("overlay.cape.scale", 1.0);
                    double back = c.getDouble("overlay.cape.back", 0.2);
                    y += c.getDouble("overlay.cape.top", 1.45) - sneak - 0.625 * s;
                    x += -fx * back + fx * lean;
                    z += -fz * back + fz * lean;
                }
            }
            pt.d().teleport(new Location(pl.getWorld(), x, y, z, yaw, 0f));
        }
    }
}
