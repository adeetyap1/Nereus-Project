package me.naook1.nereusicons.hook;

import me.naook1.nereusicons.NereusIcons;

/** Only loaded when PlaceholderAPI is present. */
public final class PapiHook {

    private PapiHook() {
    }

    public static void registerExpansion(NereusIcons plugin) {
        new IconExpansion(plugin).register();
    }
}
