package com.nereus.guildmaster.rpg;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Pintu masuk GuildMaster ke sistem Nereus RPG. GuildMaster tidak menyimpan quest, XP, atau rank sendiri;
 * semuanya dibaca lewat adapter ini. Implementasi bawaan: {@link NereusRpgAdapter}.
 * Plugin RPG lain bisa diintegrasikan dengan membuat implementasi interface ini.
 */
public interface RpgAdapter {
    /** true jika sistem RPG siap dipakai. */
    boolean available();

    String name();

    /** Indeks rank dari kunci (F=0 ... SSS), atau -1 jika tidak dikenal. */
    int rankIndex(String key);

    /** Snapshot profil pemain, atau null jika RPG tidak tersedia. */
    RpgProfile profile(Player p);

    /** Quest yang boleh dilihat pemain beserta statusnya. */
    List<QuestInfo> quests(Player p);

    List<ActiveQuest> activeQuests(Player p);

    List<HistoryEntry> history(Player p);

    /** Ambil quest. Pesan hasil dikirim oleh RPG sendiri. true jika quest kini aktif. */
    boolean accept(Player p, String questId);

    /** Serahkan quest bertipe kumpulkan-item. Hadiah dibagikan RPG. true jika selesai. */
    boolean turnIn(Player p, String questId);

    void abandon(Player p, String questId);

    enum State { AVAILABLE, ACTIVE, DONE_TODAY }

    record RpgProfile(int rankIndex, String rankKey, String rankLabel, int level, long xp, long xpNeeded,
                      String className, int gp, int totalQuests, int totalKills, int completedToday,
                      int dailyLimit, int streak, int activeCount, int activeMax, String storyTitle) {
    }

    record QuestInfo(String id, String name, String rankLabel, boolean challenge, Material icon, String objective,
                     List<String> rewards, State state) {
    }

    record ActiveQuest(String id, String name, String objective, int progress, int required, boolean turnIn,
                       boolean ready, Material icon) {
    }

    record HistoryEntry(long time, String kind, String name) {
    }
}
