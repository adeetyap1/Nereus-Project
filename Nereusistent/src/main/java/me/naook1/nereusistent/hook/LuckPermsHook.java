package me.naook1.nereusistent.hook;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import org.bukkit.entity.Player;

/** Only used when LuckPerms is installed. Every call is safe: returns null on any problem. */
public final class LuckPermsHook {

    private LuckPermsHook() {
    }

    public static String primaryGroup(Player p) {
        try {
            return LuckPermsProvider.get().getPlayerAdapter(Player.class).getUser(p).getPrimaryGroup();
        } catch (Throwable t) {
            return null;
        }
    }

    public static String display(String groupId) {
        try {
            Group g = LuckPermsProvider.get().getGroupManager().getGroup(groupId);
            return g == null ? null : g.getDisplayName();
        } catch (Throwable t) {
            return null;
        }
    }
}
