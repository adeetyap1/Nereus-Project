package com.nereus.guildmaster.rpg;

import com.nereus.guildmaster.GuildMaster;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Quest dari ODailyQuests, rank/profil dari Nereus RPG (delegate).
 * ODailyQuests membagikan quest harian otomatis dan melacak progres sendiri, jadi tidak ada "ambil", "serahkan",
 * atau "batalkan" di sini. Dipanggil lewat refleksi hanya untuk method yang tercantum di dokumentasi API resmi:
 * ODailyQuestsAPI.getPlayerQuests(String) -> PlayerQuests { getQuests(), getAchievedQuests(), getTotalAchievedQuests() };
 * AbstractQuest { getQuestName(), getCategoryName(), getQuestType() };
 * Progression { getAdvancement(), getRequiredAmount(), isAchieved() }.
 */
public final class OdqAdapter implements RpgAdapter {
    private final GuildMaster gm;
    private final RpgAdapter base;
    private final Method getPlayerQuests;

    private OdqAdapter(GuildMaster gm, RpgAdapter base, Method getPlayerQuests) {
        this.gm = gm;
        this.base = base;
        this.getPlayerQuests = getPlayerQuests;
    }

    /** null jika ODailyQuests tidak ada atau API-nya tidak cocok. */
    public static OdqAdapter tryCreate(GuildMaster gm, RpgAdapter base) {
        try {
            Plugin odq = Bukkit.getPluginManager().getPlugin("ODailyQuests");
            if (odq == null || !odq.isEnabled()) {
                return null;
            }
            Class<?> api = Class.forName("com.ordwen.odailyquests.api.ODailyQuestsAPI", true, odq.getClass().getClassLoader());
            return new OdqAdapter(gm, base, api.getMethod("getPlayerQuests", String.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            gm.getLogger().warning("API ODailyQuests tidak cocok: " + e);
            return null;
        }
    }

    // ------------------------------------------------------------------ refleksi

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        Method m = target.getClass().getMethod(name);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private Object playerQuests(Player p) {
        try {
            return getPlayerQuests.invoke(null, p.getName());
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private record Row(String name, String category, String type, int progress, int required, boolean achieved) {
    }

    private List<Row> rows(Player p) {
        List<Row> out = new ArrayList<>();
        Object pq = playerQuests(p);
        if (pq == null) {
            return out;
        }
        try {
            Object map = call(pq, "getQuests");
            if (map instanceof Map<?, ?> m) {
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    Object quest = e.getKey();
                    Object prog = e.getValue();
                    out.add(new Row(
                            clean(String.valueOf(call(quest, "getQuestName"))),
                            clean(String.valueOf(call(quest, "getCategoryName"))),
                            String.valueOf(call(quest, "getQuestType")),
                            ((Number) call(prog, "getAdvancement")).intValue(),
                            ((Number) call(prog, "getRequiredAmount")).intValue(),
                            (Boolean) call(prog, "isAchieved")));
                }
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            gm.getLogger().warning("Gagal membaca quest ODailyQuests: " + e);
        }
        return out;
    }

    /** Kode warna § dan &amp; diseragamkan ke &amp;. */
    private static String clean(String s) {
        return s.replace('§', '&');
    }

    private int intOf(Object pq, String method) {
        try {
            return ((Number) call(pq, method)).intValue();
        } catch (ReflectiveOperationException | ClassCastException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ tampilan jenis quest

    private String typeLabel(String type) {
        ConfigurationSection s = gm.getConfig().getConfigurationSection("odq.type-labels");
        String v = s == null ? null : s.getString(type.toUpperCase());
        if (v != null) {
            return v;
        }
        String low = type.toLowerCase().replace('_', ' ');
        return low.isEmpty() ? low : Character.toUpperCase(low.charAt(0)) + low.substring(1);
    }

    private Material icon(String type) {
        ConfigurationSection s = gm.getConfig().getConfigurationSection("odq.type-icons");
        String v = s == null ? null : s.getString(type.toUpperCase());
        Material m = v == null ? null : Material.matchMaterial(v);
        return m == null ? Material.PAPER : m;
    }

    private String objective(Row r) {
        return typeLabel(r.type()) + " &fx" + r.required();
    }

    // ------------------------------------------------------------------ RpgAdapter

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String name() {
        return "ODailyQuests" + (base.available() ? " + " + base.name() : "");
    }

    @Override
    public int rankIndex(String key) {
        return base.rankIndex(key);
    }

    @Override
    public RpgProfile profile(Player p) {
        RpgProfile b = base.profile(p);
        if (b == null) {
            return null;
        }
        Object pq = playerQuests(p);
        if (pq == null) {
            return b;
        }
        int total = 0;
        int done = intOf(pq, "getAchievedQuests");
        try {
            Object m = call(pq, "getQuests");
            total = m instanceof Map<?, ?> mm ? mm.size() : 0;
        } catch (ReflectiveOperationException ignored) {
            // biarkan 0
        }
        return new RpgProfile(b.rankIndex(), b.rankKey(), b.rankLabel(), b.level(), b.xp(), b.xpNeeded(), b.className(),
                b.gp(), intOf(pq, "getTotalAchievedQuests"), b.totalKills(), done, total, b.streak(),
                Math.max(0, total - done), total, b.storyTitle());
    }

    @Override
    public List<QuestInfo> quests(Player p) {
        List<QuestInfo> out = new ArrayList<>();
        int i = 0;
        for (Row r : rows(p)) {
            List<String> rewards = gm.getConfig().getStringList("odq.reward-lines");
            State st = r.achieved() ? State.DONE_TODAY : State.ACTIVE;
            out.add(new QuestInfo("odq-" + (i++), r.name(), r.category(), false, icon(r.type()), objective(r), rewards, st));
        }
        return out;
    }

    @Override
    public List<ActiveQuest> activeQuests(Player p) {
        List<ActiveQuest> out = new ArrayList<>();
        int i = 0;
        for (Row r : rows(p)) {
            if (!r.achieved()) {
                out.add(new ActiveQuest("odq-" + (i++), r.name(), objective(r), Math.min(r.progress(), r.required()),
                        r.required(), false, false, icon(r.type())));
            }
        }
        return out;
    }

    @Override
    public List<HistoryEntry> history(Player p) {
        // ODailyQuests tidak menyimpan riwayat per hari lewat API; yang ada hanya selesai hari ini.
        long midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        List<HistoryEntry> out = new ArrayList<>();
        for (Row r : rows(p)) {
            if (r.achieved()) {
                out.add(new HistoryEntry(midnight, "quest", r.name()));
            }
        }
        return out;
    }

    @Override
    public boolean accept(Player p, String questId) {
        return false;
    }

    @Override
    public boolean turnIn(Player p, String questId) {
        return false;
    }

    @Override
    public void abandon(Player p, String questId) {
        // ODailyQuests mengatur quest otomatis
    }
}
