package com.nereus.rpg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Satu tugas. target boleh satu nama atau beberapa (dipisah koma); format daftar seperti ['A', 'B'] dari impor lama
 * juga dibaca. Boleh wildcard: "*_LOG" (akhiran) atau "NETHERITE_*" (awalan). cond = syarat tambahan (BOW, MELEE,
 * NIGHT, NETHER, END, UNDERGROUND, ADULT) atau kosong.
 */
public record Objective(Type type, String target, int amount, String cond) {
    public enum Type {
        KILL, BREAK, GET, RANK, TALK, MANUAL, NONE,
        HARVEST, PLANT, PLACE, CRAFT, SMELT, SMITH, ENCHANT, BREED, TAME, SHEAR, MILK, EAT, SLEEP,
        SELL, SELL_VALUE, BUY_VALUE, STAT
    }

    public Objective(Type type, String target, int amount) {
        this(type, target, amount, "");
    }

    public static Objective parse(String type, String target, int amount) {
        return parse(type, target, amount, "");
    }

    public static Objective parse(String type, String target, int amount, String cond) {
        Type t;
        try {
            t = Type.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            t = Type.NONE;
        }
        return new Objective(t, normalize(target), Math.max(1, amount), cond == null ? "" : cond.toUpperCase(Locale.ROOT).trim());
    }

    /** "['A', 'B']" / "A, B" / "A" -> "A,B". */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace("[", "").replace("]", "").replace("\"", "").replace("'", "");
        List<String> parts = new ArrayList<>();
        for (String p : s.split(",")) {
            String x = p.trim();
            if (!x.isEmpty()) {
                parts.add(x);
            }
        }
        return String.join(",", parts);
    }

    public List<String> targets() {
        List<String> out = new ArrayList<>();
        for (String p : target.split(",")) {
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    /** Cocok dengan salah satu target (tanpa peduli huruf besar/kecil, mendukung wildcard * di awal/akhir). */
    public boolean matches(String what) {
        if (what == null) {
            return false;
        }
        if (target.isEmpty()) {
            return type == Type.ENCHANT || type == Type.SLEEP || type == Type.SELL_VALUE || type == Type.BUY_VALUE;
        }
        String w = what.toUpperCase(Locale.ROOT);
        for (String p : target.split(",")) {
            String x = p.toUpperCase(Locale.ROOT);
            if (x.startsWith("*") && w.endsWith(x.substring(1))) {
                return true;
            }
            if (x.endsWith("*") && w.startsWith(x.substring(0, x.length() - 1))) {
                return true;
            }
            if (x.equals(w)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasWildcard() {
        return target.contains("*");
    }

    private String label() {
        List<String> t = targets();
        if (t.isEmpty()) {
            return "";
        }
        if (t.size() == 1) {
            return Text.pretty(t.get(0).replace("*", ""));
        }
        return Text.pretty(t.get(0).replace("*", "")) + " (+" + (t.size() - 1) + " jenis lain)";
    }

    private String condText() {
        return switch (cond) {
            case "BOW" -> " dengan panah";
            case "MELEE" -> " dengan senjata jarak dekat";
            case "NIGHT" -> " saat malam";
            case "NETHER" -> " di Nether";
            case "END" -> " di The End";
            case "UNDERGROUND" -> " di bawah tanah";
            case "ADULT" -> " (dewasa)";
            default -> "";
        };
    }

    /** Satuan untuk STAT: jarak = blok, waktu = menit. */
    public String statUnit() {
        if (target.endsWith("_ONE_CM")) {
            return "blok";
        }
        if (target.equals("TIME_SINCE_DEATH") || target.equals("PLAY_ONE_MINUTE")) {
            return "menit";
        }
        return "kali";
    }

    public String describe() {
        String what = label();
        String c = condText();
        return switch (type) {
            case KILL -> "Bunuh " + amount + " " + what + c;
            case BREAK -> "Hancurkan " + amount + " " + what + c;
            case GET -> "Kumpulkan dan serahkan " + amount + " " + what;
            case RANK -> "Capai rank " + target.toUpperCase(Locale.ROOT);
            case TALK -> "Kunjungi " + what;
            case MANUAL -> "Diselesaikan oleh staff";
            case NONE -> "Tanpa tugas";
            case HARVEST -> "Panen " + amount + " " + what + " matang";
            case PLANT -> "Tanam " + amount + " " + what;
            case PLACE -> "Pasang " + amount + " blok " + what;
            case CRAFT -> "Buat " + amount + " " + what;
            case SMELT -> "Lebur " + amount + " " + what + " di tungku";
            case SMITH -> "Tempa " + amount + " " + what + " di smithing table";
            case ENCHANT -> "Sihir perlengkapan " + amount + " kali";
            case BREED -> "Kawinkan " + amount + " " + what;
            case TAME -> "Jinakkan " + amount + " " + what;
            case SHEAR -> "Cukur " + amount + " " + what;
            case MILK -> "Perah " + amount + " " + what;
            case EAT -> "Makan " + amount + " makanan";
            case SLEEP -> "Tidur di ranjang " + amount + " malam";
            case SELL -> "Jual " + amount + " " + what + " ke shop";
            case SELL_VALUE -> "Hasilkan " + amount + " NR dari menjual item";
            case BUY_VALUE -> "Belanja " + amount + " NR di shop";
            case STAT -> target.equals("TIME_SINCE_DEATH") ? "Bertahan hidup " + amount + " menit tanpa mati"
                    : target.equals("WALK_ONE_CM") ? "Berjalan " + amount + " blok"
                    : target.equals("SWIM_ONE_CM") ? "Berenang " + amount + " blok"
                    : target.equals("CLIMB_ONE_CM") ? "Memanjat " + amount + " blok"
                    : amount + " " + Text.pretty(target);
        };
    }
}
