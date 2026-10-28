package me.naook1.nereusclasses;

import java.util.Locale;
import java.util.Map;

/** tiny arithmetic evaluator for option values such as "<modifier.duration> * 20". */
final class Expr {
    private final String s;
    private int p;

    private Expr(String s) {
        this.s = s;
    }

    static double eval(String raw, Map<String, Double> mods, double def) {
        if (raw == null) {
            return def;
        }
        String t = raw.trim();
        // placeholders
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '<') {
                int e = t.indexOf('>', i);
                if (e > 0) {
                    String key = t.substring(i + 1, e).toLowerCase(Locale.ROOT);
                    double v = 0;
                    if (key.startsWith("modifier.") && mods != null) {
                        Double d = mods.get(key.substring(9));
                        v = d == null ? 0 : d;
                    } else if (key.startsWith("caster.level")) {
                        v = 1;
                    }
                    sb.append(v);
                    i = e;
                    continue;
                }
            }
            sb.append(c);
        }
        t = sb.toString().replace(" ", "");
        if (t.isEmpty()) {
            return def;
        }
        // operators such as ">2" are not numbers
        char f = t.charAt(0);
        if (f == '>' || f == '<' || f == '=') {
            t = t.substring(1);
            if (t.startsWith("=")) {
                t = t.substring(1);
            }
        }
        try {
            Expr x = new Expr(t);
            double v = x.sum();
            return Double.isNaN(v) || Double.isInfinite(v) ? def : v;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private double sum() {
        double v = prod();
        while (p < s.length()) {
            char c = s.charAt(p);
            if (c == '+') {
                p++;
                v += prod();
            } else if (c == '-') {
                p++;
                v -= prod();
            } else {
                break;
            }
        }
        return v;
    }

    private double prod() {
        double v = atom();
        while (p < s.length()) {
            char c = s.charAt(p);
            if (c == '*') {
                p++;
                v *= atom();
            } else if (c == '/') {
                p++;
                v /= atom();
            } else {
                break;
            }
        }
        return v;
    }

    private double atom() {
        if (p >= s.length()) {
            throw new IllegalStateException();
        }
        char c = s.charAt(p);
        if (c == '(') {
            p++;
            double v = sum();
            if (p < s.length() && s.charAt(p) == ')') {
                p++;
            }
            return v;
        }
        if (c == '-') {
            p++;
            return -atom();
        }
        int st = p;
        while (p < s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p) == '.')) {
            p++;
        }
        if (st == p) {
            throw new IllegalStateException();
        }
        return Double.parseDouble(s.substring(st, p));
    }
}
