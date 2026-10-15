package me.naook1.nereusbarter;

import java.util.UUID;
import me.naook1.nereusbarter.gui.BarterMenu;
import org.bukkit.inventory.ItemStack;

/** State of one barter between two players. Side 0 = the one who asked, side 1 = the one who accepted. */
public final class BarterSession {

    public static final int OFFER_SLOTS = 16;

    public final UUID[] ids = new UUID[2];
    public final String[] names = new String[2];
    public final ItemStack[][] offer = new ItemStack[2][OFFER_SLOTS];
    public final long[] money = new long[2];
    public final boolean[] ready = new boolean[2];
    public final boolean[] confirmed = new boolean[2];
    public final BarterMenu[] menus = new BarterMenu[2];
    public boolean confirmStage;
    public long unlockAt;
    public boolean ended;

    public BarterSession(UUID a, String nameA, UUID b, String nameB) {
        ids[0] = a;
        ids[1] = b;
        names[0] = nameA;
        names[1] = nameB;
    }

    public int sideOf(UUID id) {
        return ids[0].equals(id) ? 0 : ids[1].equals(id) ? 1 : -1;
    }

    public static int other(int side) {
        return 1 - side;
    }

    public boolean hasAnything() {
        for (int s = 0; s < 2; s++) {
            if (money[s] > 0) {
                return true;
            }
            for (ItemStack i : offer[s]) {
                if (i != null) {
                    return true;
                }
            }
        }
        return false;
    }
}
