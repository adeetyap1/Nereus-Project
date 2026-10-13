package me.naook1.nereusrealm.hook;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import org.bukkit.entity.Player;

/** Only used when LuckPerms is installed. Every call is safe: returns null on any problem. */
public final class LuckPermsHook {

    private LuckPermsHook() {
    }

    /** The player's primary LuckPerms group id, or null. */
    public static String primaryGroup(Player p) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            var user = lp.getPlayerAdapter(Player.class).getUser(p);
            return user.getPrimaryGroup();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Display name set in LuckPerms for the group, or null. */
    public static String display(String groupId) {
        try {
            Group g = LuckPermsProvider.get().getGroupManager().getGroup(groupId);
            return g == null ? null : g.getDisplayName();
        } catch (Throwable t) {
            return null;
        }
    }
}
