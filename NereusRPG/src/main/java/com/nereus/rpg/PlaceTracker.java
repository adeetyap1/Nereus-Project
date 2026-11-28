package com.nereus.rpg;

import org.bukkit.block.Block;

import java.util.HashSet;
import java.util.Set;

/** Mengingat blok buatan pemain (ditaruh pemain) supaya tidak dihitung untuk quest/bonus. */
public final class PlaceTracker {
    private final Set<Long> placed = new HashSet<>();

    private static long key(Block b) {
        return b.getBlockKey() * 31L + b.getWorld().getUID().hashCode();
    }

    public void mark(Block b) {
        if (placed.size() > 200_000) {
            placed.clear();
        }
        placed.add(key(b));
    }

    public boolean contains(Block b) {
        return placed.contains(key(b));
    }

    /** true jika blok tadi buatan pemain (dan dihapus dari catatan). */
    public boolean remove(Block b) {
        return placed.remove(key(b));
    }
}
