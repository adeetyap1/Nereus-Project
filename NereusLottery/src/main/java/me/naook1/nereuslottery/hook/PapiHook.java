package me.naook1.nereuslottery.hook;

import me.naook1.nereuslottery.NereusLottery;

/** Only loaded when PlaceholderAPI is present. */
public final class PapiHook {

    private PapiHook() {
    }

    public static void registerExpansion(NereusLottery plugin) {
        new LotteryExpansion(plugin).register();
    }
}
