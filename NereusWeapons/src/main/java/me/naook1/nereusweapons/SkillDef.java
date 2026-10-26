package me.naook1.nereusweapons;

import org.bukkit.Color;
import org.bukkit.Particle;

public record SkillDef(String type, String name, double cooldown, double damage, double range, double radius, double power,
                       double knockback, double angle, String effect, int effectSeconds, double healPercent, Particle particle,
                       Color color, String desc) {
}
