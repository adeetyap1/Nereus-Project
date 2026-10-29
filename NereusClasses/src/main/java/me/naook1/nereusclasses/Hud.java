package me.naook1.nereusclasses;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/** Custom HUD drawn with font glyphs in the actionbar (Java) / plain text (Bedrock). */
public final class Hud implements Listener {

    private static final Key FONT = Key.key("nereus", "hud");
    private final NereusClasses pl;
    private JsonObject chars;
    private final Map<Integer, String> spacer = new HashMap<>();
    private int total = 252;
    private BukkitTask task;
    private final Map<UUID, int[]> faces = new HashMap<>();
    private final Map<UUID, Long> faceTried = new HashMap<>();
    private final Map<UUID, String> status = new HashMap<>();
    private final Map<UUID, Long> statusUntil = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private int tick;
    private Method asGet, asUser, usMana, usMax, usPower;
    private boolean asTried;
    boolean enabled;

    Hud(NereusClasses pl) {
        this.pl = pl;
    }

    void start() {
        enabled = pl.getConfig().getBoolean("hud.enabled", true);
        if (!enabled) {
            return;
        }
        try (InputStream in = pl.getResource("hud.json")) {
            JsonObject o = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            chars = o.getAsJsonObject("chars");
            total = o.get("T").getAsInt();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("spacers").entrySet()) {
                spacer.put(Integer.parseInt(e.getKey()), e.getValue().getAsString());
            }
            nameX = o.get("name_x").getAsInt();
            hpX0 = o.getAsJsonArray("hpx").get(0).getAsInt();
            hpX1 = o.getAsJsonArray("hpx").get(1).getAsInt();
            mpX0 = o.getAsJsonArray("mpx").get(0).getAsInt();
            mpX1 = o.getAsJsonArray("mpx").get(1).getAsInt();
            JsonArray xp = o.getAsJsonArray("xp");
            xpX0 = xp.get(0).getAsInt();
            xpX1 = xp.get(1).getAsInt();
            JsonArray fc = o.getAsJsonArray("face");
            cardX = o.get("cx").getAsInt();
        } catch (Exception ex) {
            pl.getLogger().warning("HUD disabled, hud.json: " + ex);
            enabled = false;
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, pl);
        int period = Math.max(1, pl.getConfig().getInt("hud.period-ticks", 3));
        task = Bukkit.getScheduler().runTaskTimer(pl, this::loop, 20L, period);
    }

    private int nameX, hpX0, hpX1, mpX0, mpX1, xpX0, xpX1, cardX;

    void stop() {
        if (task != null) {
            task.cancel();
        }
        for (Map.Entry<UUID, BossBar> e : bars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                p.hideBossBar(e.getValue());
            }
        }
        bars.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        faces.remove(id);
        faceTried.remove(id);
        status.remove(id);
        statusUntil.remove(id);
        bars.remove(id);
    }

    // ------------------------------------------------------------------ status line from skills
    void status(Player p, String legacy) {
        if (legacy == null) {
            return;
        }
        status.put(p.getUniqueId(), legacy);
        statusUntil.put(p.getUniqueId(), System.currentTimeMillis() + 2500);
    }

    // ------------------------------------------------------------------ loop
    private static boolean bedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L;
    }

    private void loop() {
        tick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                if (bedrock(p)) {
                    if (tick % 4 == 0) {
                        bedrockBar(p);
                    }
                } else {
                    javaBar(p);
                    statusBoss(p);
                }
            } catch (Throwable t) {
                if (tick % 400 == 0) {
                    pl.getLogger().warning("HUD error: " + t);
                }
            }
        }
    }

    private String activeStatus(Player p) {
        Long u = statusUntil.get(p.getUniqueId());
        if (u == null || u < System.currentTimeMillis()) {
            return null;
        }
        return status.get(p.getUniqueId());
    }

    private void statusBoss(Player p) {
        String s = activeStatus(p);
        BossBar b = bars.get(p.getUniqueId());
        if (s == null) {
            if (b != null) {
                p.hideBossBar(b);
                bars.remove(p.getUniqueId());
            }
            return;
        }
        Component c = LegacyComponentSerializer.legacySection().deserialize(NereusClasses.color(s));
        if (b == null) {
            b = BossBar.bossBar(c, 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
            bars.put(p.getUniqueId(), b);
            p.showBossBar(b);
        } else {
            b.name(c);
        }
    }

    // ------------------------------------------------------------------ data
    private void ensureAura() {
        if (asTried) {
            return;
        }
        asTried = true;
        try {
            Class<?> api = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            asGet = api.getMethod("get");
            asUser = api.getMethod("getUser", UUID.class);
            Class<?> us = Class.forName("dev.aurelium.auraskills.api.user.SkillsUser");
            usMana = us.getMethod("getMana");
            usMax = us.getMethod("getMaxMana");
            usPower = us.getMethod("getPowerLevel");
        } catch (Throwable t) {
            asGet = null;
        }
    }

    /** {mana, maxMana, level} */
    private double[] aura(Player p) {
        ensureAura();
        if (asGet != null && Bukkit.getPluginManager().isPluginEnabled("AuraSkills")) {
            try {
                Object api = asGet.invoke(null);
                Object u = asUser.invoke(api, p.getUniqueId());
                if (u != null) {
                    return new double[] {((Number) usMana.invoke(u)).doubleValue(), ((Number) usMax.invoke(u)).doubleValue(),
                        ((Number) usPower.invoke(u)).doubleValue()};
                }
            } catch (Throwable ignored) {
                // fall through
            }
        }
        return new double[] {0, 0, p.getLevel()};
    }

    private double armor(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.GENERIC_ARMOR);
        return a == null ? 0 : a.getValue();
    }

    private double maxHp(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return a == null ? 20 : a.getValue();
    }

    // ------------------------------------------------------------------ bedrock (text only)
    private void bedrockBar(Player p) {
        double[] a = aura(p);
        String s = "&c❤ " + (int) Math.ceil(p.getHealth()) + "/" + (int) Math.ceil(maxHp(p)) + "  &b✦ " + (int) a[0] + "/" + (int) a[1]
            + "  &6Lv " + (int) a[2];
        String st = activeStatus(p);
        if (st != null) {
            s = st + "  &8| " + s;
        }
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(NereusClasses.color(s)));
    }

    // ------------------------------------------------------------------ java glyph hud
    private int cursor;
    private TextComponent.Builder out;
    private StringBuilder run;

    private void flush() {
        if (run.length() > 0) {
            out.append(Component.text(run.toString()).font(FONT).color(NamedTextColor.WHITE));
            run.setLength(0);
        }
    }

    private void moveTo(int x) {
        int d = x - cursor;
        if (d == 0) {
            return;
        }
        int sign = d > 0 ? 1 : -1;
        int rem = Math.abs(d);
        int[] ps = {256, 128, 64, 32, 16, 8, 4, 2, 1};
        for (int pwr : ps) {
            while (rem >= pwr) {
                run.append(spacer.get(sign * pwr));
                rem -= pwr;
            }
        }
        cursor = x;
    }

    private void put(String name, int x) {
        JsonObject g = chars.getAsJsonObject(name);
        moveTo(x);
        run.append(g.get("c").getAsString());
        cursor += g.get("adv").getAsInt();
    }

    private void bar(String pre, int x0, int px) {
        int rem = Math.max(0, px);
        int x = x0;
        for (int w : new int[] {32, 16, 8, 4, 2, 1}) {
            if ((rem & w) != 0) {
                put(pre + w, x);
                x += w;
            }
        }
    }

    private static final String ASCII_W = "";

    static int charW(char c) {
        switch (c) {
            case ' ':
                return 4;
            case 'i': case '!': case '.': case ',': case ':': case ';': case '|':
                return 2;
            case 'l': case '\'': case '`':
                return 3;
            case 'I': case 't': case '[': case ']':
                return 4;
            case 'f': case 'k': case '<': case '>': case '(': case ')': case '{': case '}': case '"': case '*':
                return 5;
            case '@': case '~':
                return 7;
            default:
                return 6;
        }
    }

    private void text(String s, int x, TextColor col) {
        moveTo(x);
        flush();
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (char c : s.toCharArray()) {
            if (c < 32 || c > 126) {
                c = '?';
            }
            sb.append(c);
            w += charW(c);
        }
        out.append(Component.text(sb.toString()).font(FONT).color(col));
        cursor += w;
    }

    private void javaBar(Player p) {
        double[] a = aura(p);
        out = Component.text();
        run = new StringBuilder();
        cursor = 0;
        put("front", 0);
        put("card", cardX);
        ItemStack off = p.getInventory().getItemInOffHand();
        if (off == null || off.getType().isAir()) {
            put("off_int", 7);
        }
        double hpf = Math.min(1, p.getHealth() / maxHp(p));
        bar("hp", hpX0, (int) Math.round(hpf * (hpX1 - hpX0 + 1)));
        double ff = Math.min(1, p.getFoodLevel() / 20.0);
        bar("mp", mpX0, (int) Math.round(ff * (mpX1 - mpX0 + 1)));
        bar("xp", xpX0, (int) Math.round(Math.min(1, p.getExp()) * (xpX1 - xpX0 + 1)));
        int orbs = a[1] <= 0 ? 0 : (int) Math.round(Math.min(1, a[0] / a[1]) * 10);
        JsonArray ox = chars.getAsJsonObject("orb").getAsJsonArray("xs");
        for (int i = 0; i < orbs; i++) {
            put("orb", ox.get(i).getAsInt());
        }
        int sh = (int) Math.round(Math.min(20, armor(p)) / 2.0);
        JsonArray sx = chars.getAsJsonObject("shield").getAsJsonArray("xs");
        for (int i = 0; i < sh; i++) {
            put("shield", sx.get(i).getAsInt());
        }
        // face
        int[] face = face(p);
        int fx = chars.getAsJsonObject("face0").get("x").getAsInt();
        for (int r = 0; r < 8; r++) {
            String fc = chars.getAsJsonObject("face" + r).get("c").getAsString();
            for (int c = 0; c < 8; c++) {
                int rgb = face[r * 8 + c];
                moveTo(fx + 2 * c);
                flush();
                out.append(Component.text(fc).font(FONT).color(TextColor.color(rgb & 0xFFFFFF)));
                cursor += chars.getAsJsonObject("face" + r).get("adv").getAsInt();
            }
        }
        String nm = p.getName();
        if (nm.length() > 12) {
            nm = nm.substring(0, 12);
        }
        text(nm, nameX, NamedTextColor.WHITE);
        text("  Lv." + (int) a[2], cursor, NamedTextColor.GOLD);
        moveTo(total);
        flush();
        p.sendActionBar(out.build());
    }

    // ------------------------------------------------------------------ skin face
    private static final int[] STEVE = new int[64];

    static {
        for (int i = 0; i < 64; i++) {
            int r = i / 8;
            STEVE[i] = r < 2 ? 0x3B2A1C : 0xC29673;
        }
        STEVE[2 * 8 + 1] = 0xFFFFFF;
        STEVE[2 * 8 + 2] = 0x3B3B8C;
        STEVE[2 * 8 + 5] = 0x3B3B8C;
        STEVE[2 * 8 + 6] = 0xFFFFFF;
        STEVE[5 * 8 + 3] = 0x8C4B3A;
        STEVE[5 * 8 + 4] = 0x8C4B3A;
    }

    private int[] face(Player p) {
        UUID id = p.getUniqueId();
        int[] f = faces.get(id);
        if (f != null) {
            return f;
        }
        long now = System.currentTimeMillis();
        Long t = faceTried.get(id);
        if (t == null || now - t > 60000) {
            faceTried.put(id, now);
            URL skin = null;
            try {
                skin = p.getPlayerProfile().getTextures().getSkin();
            } catch (Throwable ignored) {
                // none
            }
            final URL u = skin;
            if (u != null) {
                Bukkit.getScheduler().runTaskAsynchronously(pl, () -> {
                    try {
                        java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
                        c.setConnectTimeout(4000);
                        c.setReadTimeout(4000);
                        BufferedImage im = ImageIO.read(c.getInputStream());
                        int[] face = new int[64];
                        for (int y = 0; y < 8; y++) {
                            for (int x = 0; x < 8; x++) {
                                int base = im.getRGB(8 + x, 8 + y);
                                int hat = im.getHeight() >= 16 ? im.getRGB(40 + x, 8 + y) : 0;
                                face[y * 8 + x] = ((hat >>> 24) > 128 ? hat : base) & 0xFFFFFF;
                            }
                        }
                        faces.put(id, face);
                    } catch (Throwable ex) {
                        // keep fallback
                    }
                });
            }
        }
        return STEVE;
    }
}
