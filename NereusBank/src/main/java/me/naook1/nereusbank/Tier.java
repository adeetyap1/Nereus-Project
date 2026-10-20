package me.naook1.nereusbank;

/** Tier settings loaded from config.yml. */
public record Tier(String id, int order, String name, String color, double interest, long interestCap,
                   double transferFee, long dailyLimit, long maxBalance, long loanLimit, double loanRate,
                   double depositBonus, long cardPrice, long cardMinBalance, int model) {

    public String label() {
        return color + name;
    }
}
