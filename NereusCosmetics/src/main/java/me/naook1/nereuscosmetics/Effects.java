package me.naook1.nereuscosmetics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/** Draws cosmetics with server side particles: Java and Bedrock (through Geyser) both see them. */
final class Effects {

    private Effects() {
    }

    record Pt(double lat, double up, double fwd, double k) {
    }

    private static Pt p(double lat, double up, double fwd, double k) {
        return new Pt(lat, up, fwd, k);
    }

    static List<Pt> points(Cosmetic c, Player pl, double t, boolean moving) {
        List<Pt> out = new ArrayList<>();
        double sneak = pl.isSneaking() ? -0.25 : 0.0;
        double head = pl.getEyeHeight() + 0.12;
        switch (c.effect()) {
            case "halo" -> {
                int n = 14;
                for (int i = 0; i < n; i++) {
                    double a = t * 0.12 + i * Math.PI * 2 / n;
                    out.add(p(Math.cos(a) * 0.34, head + 0.42 + Math.sin(t * 0.08) * 0.03, Math.sin(a) * 0.34, i / (double) n));
                }
            }
            case "crown" -> {
                int n = 12;
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n;
                    out.add(p(Math.cos(a) * 0.3, head + 0.22, Math.sin(a) * 0.3, 0.2));
                }
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    out.add(p(Math.cos(a) * 0.3, head + 0.34, Math.sin(a) * 0.3, 0.6));
                    out.add(p(Math.cos(a) * 0.3, head + 0.46, Math.sin(a) * 0.3, 1.0));
                }
            }
            case "horns" -> {
                for (int s = -1; s <= 1; s += 2) {
                    for (int j = 0; j <= 9; j++) {
                        double u = j / 9.0;
                        out.add(p(s * (0.2 + 0.34 * u - 0.1 * u * u), head + 0.08 + 0.55 * u - 0.1 * u * u, 0.04 * u, u));
                        if (j % 3 == 0) {
                            out.add(p(s * (0.2 + 0.34 * u - 0.1 * u * u), head + 0.08 + 0.55 * u - 0.1 * u * u, 0.06 + 0.04 * u, u));
                        }
                    }
                }
            }
            case "wings" -> {
                double[] len = {0.55, 0.7, 0.85, 0.92, 0.8, 0.65, 0.5};
                double flap = Math.sin(t * 0.14) * 0.32;
                for (int s = -1; s <= 1; s += 2) {
                    for (int i = 0; i < len.length; i++) {
                        double th = Math.toRadians(78 - i * 17);
                        for (int m = 1; m <= 4; m++) {
                            double r = len[i] * m / 4.0;
                            double lat = 0.14 + Math.cos(th) * r;
                            double up = 1.3 + Math.sin(th) * r * 0.9 + sneak;
                            double back = 0.2 + lat * Math.sin(flap);
                            out.add(p(s * lat * Math.cos(flap), up, -back, m / 4.0));
                        }
                    }
                }
            }
            case "cape" -> {
                for (int i = 0; i < 5; i++) {
                    for (int j = 0; j < 6; j++) {
                        double sway = Math.sin(t * 0.18 + j * 0.6 + i) * 0.045 * j + (moving ? 0.12 * j * 0.3 : 0);
                        out.add(p((i - 2) * 0.12, 1.4 - j * 0.2 + sneak, -(0.2 + 0.02 * j + Math.abs(sway)), j / 5.0));
                    }
                }
            }
            case "orbit" -> {
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.18 + i * Math.PI * 2 / 3;
                    out.add(p(Math.cos(a) * 0.85, 0.9 + Math.sin(t * 0.1 + i) * 0.3, Math.sin(a) * 0.85, i / 2.0));
                }
            }
            case "spiral" -> {
                for (int s = 0; s < 2; s++) {
                    for (int j = 0; j < 14; j++) {
                        double u = j / 14.0;
                        double a = t * 0.15 + u * Math.PI * 4 + s * Math.PI;
                        out.add(p(Math.cos(a) * 0.55, u * 1.9, Math.sin(a) * 0.55, u));
                    }
                }
            }
            case "trail" -> {
                if (moving) {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    for (int i = 0; i < 3; i++) {
                        out.add(p(r.nextDouble(-0.2, 0.2), 0.05 + r.nextDouble() * 0.15, r.nextDouble(-0.2, 0.2), r.nextDouble()));
                    }
                }
            }
            default -> {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (int i = 0; i < 4; i++) {
                    out.add(p(r.nextDouble(-0.6, 0.6), r.nextDouble(0, 2), r.nextDouble(-0.6, 0.6), r.nextDouble()));
                }
            }
        }
        return out;
    }

    static Color mix(Color a, Color b, double k) {
        if (b == null) {
            return a;
        }
        k = Math.max(0, Math.min(1, k));
        return Color.fromRGB((int) (a.getRed() + (b.getRed() - a.getRed()) * k), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * k),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * k));
    }

    /** Draw one cosmetic for one wearer to the given viewers. */
    static void draw(Cosmetic c, Player pl, double t, boolean moving, List<Player> viewers) {
        List<Pt> pts = points(c, pl, t, moving);
        if (pts.isEmpty() || viewers.isEmpty()) {
            return;
        }
        Location l = pl.getLocation();
        double yaw = Math.toRadians(l.getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);       // forward
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);      // right
        boolean trail = c.effect().equals("trail");
        for (Pt pt : pts) {
            double x, y, z;
            if (trail) {
                // trail stays in world axes around the feet (no rotation)
                x = l.getX() + pt.lat();
                z = l.getZ() + pt.fwd();
            } else {
                x = l.getX() + rx * pt.lat() + fx * pt.fwd();
                z = l.getZ() + rz * pt.lat() + fz * pt.fwd();
            }
            y = l.getY() + pt.up();
            Object data = null;
            if (c.particle() == Particle.DUST) {
                data = new Particle.DustOptions(mix(c.c1(), c.c2(), pt.k()), c.size());
            }
            for (Player v : viewers) {
                if (data != null) {
                    v.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, data);
                } else {
                    v.spawnParticle(c.particle(), x, y, z, 1, 0, 0, 0, 0);
                }
            }
        }
    }
}
