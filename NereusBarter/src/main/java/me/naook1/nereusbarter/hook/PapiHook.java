package me.naook1.nereusbarter.hook;

import me.naook1.nereusbarter.NereusBarter;

/** Only loaded when PlaceholderAPI is present. */
public final class PapiHook {

    private PapiHook() {
    }

    public static void registerExpansion(NereusBarter plugin) {
        new BarterExpansion(plugin).register();
    }
}
