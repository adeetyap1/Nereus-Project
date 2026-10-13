package me.naook1.nereuslottery.hook;

import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.naook1.nereuslottery.NereusLottery;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI expansion, identifier "lottery".
 * %lottery_pot% %lottery_next% (countdown) %lottery_players% %lottery_tickets% %lottery_price% %lottery_mine%
 */
public final class LotteryExpansion extends PlaceholderExpansion {

    private final NereusLottery plugin;

    public LotteryExpansion(NereusLottery plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "lottery";
    }

    @Override
    public String getAuthor() {
        return "naook1/adityap";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        var l = plugin.lottery();
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "pot" -> l.fmt(l.pot());
            case "next" -> l.nextText();
            case "players" -> String.valueOf(l.participants());
            case "tickets" -> String.valueOf(l.totalTickets());
            case "price" -> l.fmt(l.price());
            case "mine" -> player == null ? "0" : String.valueOf(l.ticketsOf(player.getUniqueId()));
            default -> null;
        };
    }
}
