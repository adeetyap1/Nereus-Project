package me.naook1.nereusrpg;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerData {

    public static final String[] STAT_NAMES = {"STR", "DEX", "INT", "VIT", "LUK"};

    final UUID id;
    String name = "?";
    int level = 1;
    double xp = 0;
    int points = 0;
    final int[] stats = new int[5];
    String clazz = "";
    boolean comp = false;
    int selected = 0;
    long kills = 0;
    long pvpKills = 0;
    double seasonXp = 0;
    long modeChangeAt = 0;
    long respecAt = 0;

    // runtime only
    double mana = 0;
    final Map<String, Long> cooldowns = new HashMap<>();
    long combatUntil = 0;
    String hitText = "";
    long hitUntil = 0;
    Gear gear = Gear.NONE;

    PlayerData(UUID id) {
        this.id = id;
    }

    public record Gear(double dmg, double hp, double mana, double crit, double luck, double def, double lifesteal) {
        static final Gear NONE = new Gear(0, 0, 0, 0, 0, 0, 0);

        Gear plus(Gear o) {
            return new Gear(dmg + o.dmg, hp + o.hp, mana + o.mana, crit + o.crit, luck + o.luck, def + o.def, lifesteal + o.lifesteal);
        }
    }
}
