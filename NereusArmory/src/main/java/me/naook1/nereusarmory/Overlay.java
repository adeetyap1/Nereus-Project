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
            case "phead" -> 9101;
            case "pbody" -> 9102;
            case "dhead" -> 9111;
            case "fhead" -> 9112;
            case "dbody" -> 9113;
            default -> 9114;
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
            String hid = plugin.items().idOf(p.getInventory().getHelmet());
            String cid = plugin.items().idOf(p.getInventory().getChestplate());
            String headKind = "helm".equals(hid) ? "phead" : "dhelm".equals(hid) ? "dhead" : "fhelm".equals(hid) ? "fhead" : null;
            String bodyKind = "chest".equals(cid) ? "pbody" : "dchest".equals(cid) ? "dbody" : "fchest".equals(cid) ? "fbody" : null;
            boolean want = on && (headKind != null || bodyKind != null) && !p.isDead() && !p.isInvisible() && p.getGameMode() != GameMode.SPECTATOR;
            UUID id = p.getUniqueId();
            if (!want) {
                if (parts.containsKey(id)) {
                    remove(id);
                }
                continue;
            }
            List<Part> cur = parts.get(id);
            int expect = (headKind != null ? 1 : 0) + (bodyKind != null ? 1 : 0);
            boolean ok = cur != null && cur.size() == expect;
            if (ok) {
                for (Part pt : cur) {
                    if (!pt.d().isValid() || !pt.d().getWorld().equals(p.getWorld())) {
                        ok = false;
                        break;
                    }
                }
                if (headKind != null && cur.stream().noneMatch(x -> x.kind().equals(headKind))) {
                    ok = false;
                }
                if (bodyKind != null && cur.stream().noneMatch(x -> x.kind().equals(bodyKind))) {
                    ok = false;
                }
            }
            var c = plugin.getConfig();
            if (!ok) {
                remove(id);
                List<Part> nl = new ArrayList<>();
                if (headKind != null) {
                    nl.add(spawn(p, headKind, 0, c.getDouble("overlay.head.scale", 1.0)));
                }
                if (bodyKind != null) {
                    nl.add(spawn(p, bodyKind, 0, c.getDouble("overlay.body.scale", 1.0)));
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
            if (pt.kind().endsWith("head")) {
                y += c.getDouble("overlay.head.y", 1.75) - sneak;
                x += hfx * lean;
                z += hfz * lean;
                yaw = head;
            } else {
                y += c.getDouble("overlay.body.y", 1.125) - sneak;
                x += fx * lean;
                z += fz * lean;
            }
            pt.d().teleport(new Location(pl.getWorld(), x, y, z, yaw, 0f));
        }
    }
}
