package me.naook1.nereusclasses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Data model + parser for the Mythic-style skill files. */
final class Model {
    private Model() {
    }

    /** mechanic / targeter option bag (keys lower-case) */
    static final class Opts {
        final Map<String, String> m = new HashMap<>();

        String get(String... keys) {
            for (String k : keys) {
                String v = m.get(k.toLowerCase(Locale.ROOT));
                if (v != null) {
                    return v;
                }
            }
            return null;
        }

        boolean has(String... keys) {
            return get(keys) != null;
        }

        String str(String def, String... keys) {
            String v = get(keys);
            return v == null ? def : v;
        }

        double num(Map<String, Double> mods, double def, String... keys) {
            String v = get(keys);
            if (v == null) {
                return def;
            }
            return Expr.eval(v, mods, def);
        }

        int integer(Map<String, Double> mods, int def, String... keys) {
            return (int) Math.round(num(mods, def, keys));
        }

        boolean bool(boolean def, String... keys) {
            String v = get(keys);
            if (v == null) {
                return def;
            }
            return v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") || v.equals("1");
        }
    }

    static final class Targeter {
        String name;
        Opts o = new Opts();
    }

    static final class Line {
        String raw;
        String name;          // lower-case mechanic name (e.g. effect:particles)
        Opts o = new Opts();
        Targeter tg;          // may be null
        String trigger;       // ~onSpawn, ~onTimer:10 ...
        double chance = 1.0;
    }

    static final class Cond {
        String name;
        Opts o = new Opts();
        boolean expect = true;
        String action;        // castinstead / cast / null
        String actionArg;
    }

    static final class Skill {
        String id;
        double cooldown;
        final List<Cond> conds = new ArrayList<>();
        final List<Cond> tconds = new ArrayList<>();
        final List<Line> lines = new ArrayList<>();
        boolean doubleFire;   // "hold to cast" pattern (aura + castinstead)
    }

    static final class MobDef {
        String id;
        String type = "ARMOR_STAND";
        final Map<String, double[]> pose = new LinkedHashMap<>();
        String itemHead;
        String itemHand;
        boolean small;
        boolean arms;
        final List<Line> lines = new ArrayList<>();
    }

    // ------------------------------------------------------------------ parsing
    static Opts parseOpts(String raw) {
        Opts o = new Opts();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '"') {
                q = !q;
            }
            if (ch == ';' && !q) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        if (cur.length() > 0) {
            parts.add(cur.toString());
        }
        for (String p : parts) {
            int e = p.indexOf('=');
            if (e > 0) {
                String k = p.substring(0, e).trim().toLowerCase(Locale.ROOT);
                String v = p.substring(e + 1).trim();
                if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                    v = v.substring(1, v.length() - 1);
                }
                o.m.put(k, v);
            }
        }
        return o;
    }

    private static int matchBrace(String s, int start) {
        int d = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                d++;
            } else if (c == '}') {
                d--;
                if (d == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    static Line parseLine(String s) {
        Line l = new Line();
        l.raw = s;
        s = s.trim();
        int i = 0;
        while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == ':')) {
            i++;
        }
        l.name = s.substring(0, i).toLowerCase(Locale.ROOT);
        String rest = s.substring(i);
        if (rest.startsWith("{")) {
            int e = matchBrace(rest, 0);
            if (e < 0) {
                e = rest.length() - 1;
            }
            l.o = parseOpts(rest.substring(1, e));
            rest = rest.substring(e + 1);
        } else {
            String t = rest.trim();
            int cut = t.length();
            for (char c : new char[]{'@', '~', '?'}) {
                int k = t.indexOf(c);
                if (k >= 0 && k < cut) {
                    cut = k;
                }
            }
            String arg = t.substring(0, cut).trim();
            if (!arg.isEmpty()) {
                l.o.m.put("_arg", arg);
            }
            rest = t.substring(cut);
        }
        // targeter
        int at = rest.indexOf('@');
        if (at >= 0) {
            int j = at + 1;
            while (j < rest.length() && !Character.isWhitespace(rest.charAt(j)) && rest.charAt(j) != '{') {
                j++;
            }
            Targeter t = new Targeter();
            t.name = rest.substring(at + 1, j).toLowerCase(Locale.ROOT);
            if (j < rest.length() && rest.charAt(j) == '{') {
                int e = matchBrace(rest, j);
                if (e < 0) {
                    e = rest.length() - 1;
                }
                t.o = parseOpts(rest.substring(j + 1, e));
            }
            l.tg = t;
        }
        int tr = rest.indexOf('~');
        if (tr >= 0) {
            int j = tr + 1;
            while (j < rest.length() && !Character.isWhitespace(rest.charAt(j))) {
                j++;
            }
            l.trigger = rest.substring(tr + 1, j);
        }
        int ch = rest.indexOf(" 0.");
        if (ch >= 0 && rest.lastIndexOf('?') >= 0) {
            try {
                l.chance = Double.parseDouble(rest.substring(rest.lastIndexOf('?') + 1).trim());
            } catch (NumberFormatException ignored) {
                // no chance
            }
        }
        return l;
    }

    static Cond parseCond(String s) {
        Cond c = new Cond();
        s = s.trim();
        int i = 0;
        while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) {
            i++;
        }
        c.name = s.substring(0, i).toLowerCase(Locale.ROOT);
        String rest = s.substring(i).trim();
        if (rest.startsWith("{")) {
            int e = matchBrace(rest, 0);
            if (e < 0) {
                e = rest.length() - 1;
            }
            c.o = parseOpts(rest.substring(1, e));
            rest = rest.substring(e + 1).trim();
        }
        if (!rest.isEmpty()) {
            String[] p = rest.split("\\s+");
            String w = p[0].toLowerCase(Locale.ROOT);
            if (w.equals("true") || w.equals("false")) {
                c.expect = w.equals("true");
            } else if (w.equals("castinstead") || w.equals("cast") || w.equals("castinsteadif")) {
                c.action = w.startsWith("castinstead") ? "castinstead" : "cast";
                c.actionArg = p.length > 1 ? p[1] : null;
            }
        }
        return c;
    }

    @SuppressWarnings("unchecked")
    static Skill parseSkill(String id, Map<String, Object> d) {
        Skill s = new Skill();
        s.id = id;
        Object cd = d.get("Cooldown");
        if (cd instanceof Number n) {
            s.cooldown = n.doubleValue();
        } else if (cd != null) {
            try {
                s.cooldown = Double.parseDouble(cd.toString());
            } catch (NumberFormatException ignored) {
                s.cooldown = 0;
            }
        }
        for (Object o : listOf(d.get("Conditions"))) {
            s.conds.add(parseCond(o.toString()));
        }
        for (Object o : listOf(d.get("TargetConditions"))) {
            s.tconds.add(parseCond(o.toString()));
        }
        for (Object o : listOf(d.get("Skills"))) {
            if (o instanceof String str) {
                s.lines.add(parseLine(str));
            }
        }
        // hold-to-cast pattern: castinstead + hasaurastacks, aura applied by the same skill
        for (Cond c : s.conds) {
            if ("castinstead".equals(c.action) && c.name.startsWith("hasaura")) {
                String an = c.o.str("", "auraname", "n", "aura");
                for (Line l : s.lines) {
                    if (l.name.equals("aura") && an.equalsIgnoreCase(l.o.str("", "auraname", "n"))) {
                        s.doubleFire = true;
                    }
                }
            }
        }
        return s;
    }

    static List<Object> listOf(Object o) {
        if (o instanceof List<?> l) {
            return new ArrayList<>(l);
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    static MobDef parseMob(String id, Map<String, Object> d) {
        MobDef m = new MobDef();
        m.id = id;
        Object t = d.get("Type");
        if (t != null) {
            m.type = t.toString().toUpperCase(Locale.ROOT);
        }
        Object p = d.get("Pose");
        if (p instanceof Map<?, ?> pm) {
            for (Map.Entry<?, ?> e : pm.entrySet()) {
                String[] a = e.getValue().toString().split(",");
                if (a.length == 3) {
                    try {
                        m.pose.put(e.getKey().toString().toLowerCase(Locale.ROOT),
                                new double[]{Double.parseDouble(a[0].trim()), Double.parseDouble(a[1].trim()), Double.parseDouble(a[2].trim())});
                    } catch (NumberFormatException ignored) {
                        // skip bad pose
                    }
                }
            }
        }
        if (d.get("ItemHead") != null) {
            m.itemHead = d.get("ItemHead").toString();
        }
        if (d.get("ItemHand") != null) {
            m.itemHand = d.get("ItemHand").toString();
        }
        Object op = d.get("Options");
        if (op instanceof Map<?, ?> om) {
            m.small = "true".equalsIgnoreCase(String.valueOf(om.get("Small")));
            m.arms = "true".equalsIgnoreCase(String.valueOf(om.get("HasArms")));
        }
        for (Object o : listOf(d.get("Skills"))) {
            if (o instanceof String str) {
                m.lines.add(parseLine(str));
            }
        }
        return m;
    }
}
