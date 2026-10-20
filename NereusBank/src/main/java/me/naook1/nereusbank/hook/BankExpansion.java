package me.naook1.nereusbank.hook;

import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.naook1.nereusbank.Account;
import me.naook1.nereusbank.NereusBank;
import me.naook1.nereusbank.util.Fmt;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI, identifier "nbank": balance, balance_formatted, balance_compact, tier, loan, deposits,
 * interest_days, interest_preview, card.
 */
public final class BankExpansion extends PlaceholderExpansion {

    private final NereusBank plugin;

    public BankExpansion(NereusBank plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "nbank";
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
        if (player == null) {
            return "";
        }
        Account a = plugin.bank().get(player.getUniqueId());
        if (a == null) {
            return switch (params.toLowerCase(Locale.ROOT)) {
                case "tier" -> plugin.bank().baseTier().name();
                case "balance_formatted", "loan_formatted", "deposits_formatted" -> Fmt.money(0);
                case "balance_compact" -> "0";
                default -> "0";
            };
        }
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "balance" -> String.valueOf(a.balance);
            case "balance_formatted" -> Fmt.money(a.balance);
            case "balance_compact" -> Fmt.compact(a.balance);
            case "tier" -> plugin.bank().tier(a).name();
            case "tier_colored" -> plugin.bank().tier(a).label();
            case "loan" -> String.valueOf(a.loan);
            case "loan_formatted" -> Fmt.money(a.loan);
            case "deposits" -> String.valueOf(a.lockedInDeposits());
            case "deposits_formatted" -> Fmt.money(a.lockedInDeposits());
            case "interest_days" -> String.valueOf(Math.max(0,
                    (plugin.bank().nextInterestAt(a) - System.currentTimeMillis()) / 86_400_000L));
            case "interest_preview" -> Fmt.money(plugin.bank().interestPreview(a));
            case "card" -> a.cardNo == null ? "" : a.cardNo;
            default -> null;
        };
    }
}
