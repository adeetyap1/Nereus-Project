package com.nereus.guildmaster.rpg;

import org.bukkit.entity.Player;

import java.util.List;

/** Dipakai saat NereusRpg tidak ada: semua kosong, registrasi ditolak dengan pesan jelas. */
public final class NoRpgAdapter implements RpgAdapter {
    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String name() {
        return "(tidak ada)";
    }

    @Override
    public int rankIndex(String key) {
        return -1;
    }

    @Override
    public RpgProfile profile(Player p) {
        return null;
    }

    @Override
    public List<QuestInfo> quests(Player p) {
        return List.of();
    }

    @Override
    public List<ActiveQuest> activeQuests(Player p) {
        return List.of();
    }

    @Override
    public List<HistoryEntry> history(Player p) {
        return List.of();
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
        // tidak ada
    }
}
