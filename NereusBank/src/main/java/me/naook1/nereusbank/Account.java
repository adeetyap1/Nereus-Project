package me.naook1.nereusbank;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One bank account. All amounts are whole currency units. */
public final class Account {

    public record Deposit(long amount, long start, long end, double rate) {
    }

    public record Tx(long time, String type, long amount, String note, long after) {
    }

    public final UUID id;
    public String name;
    public long balance;
    public String tier = "STANDARD";
    public String cardNo;
    public long created;
    public long lastInterest;
    public long totalInterest;

    public long loan;          // amount still owed (principal + interest)
    public long loanDue;       // epoch millis, 0 = no loan
    public long loanPenaltyAt; // last time an overdue penalty was applied
    public long loanInterest;  // interest added by the latest borrow (waived if repaid inside the grace window)
    public long loanAt;        // time of the latest borrow

    public long dayStamp;      // epoch day of dailyOut
    public long dailyOut;

    public final List<Deposit> deposits = new ArrayList<>();
    public final List<Tx> txs = new ArrayList<>();
    public final List<String> notices = new ArrayList<>();

    public Account(UUID id, String name) {
        this.id = id;
        this.name = name;
        long now = System.currentTimeMillis();
        this.created = now;
        this.lastInterest = now;
    }

    public long lockedInDeposits() {
        long sum = 0;
        for (Deposit d : deposits) {
            sum += d.amount();
        }
        return sum;
    }

    public long dailyUsed() {
        long today = System.currentTimeMillis() / 86_400_000L;
        return today == dayStamp ? dailyOut : 0;
    }

    public void addDaily(long amount) {
        long today = System.currentTimeMillis() / 86_400_000L;
        if (today != dayStamp) {
            dayStamp = today;
            dailyOut = 0;
        }
        dailyOut += amount;
    }

    public boolean overdue() {
        return loan > 0 && loanDue > 0 && System.currentTimeMillis() > loanDue;
    }
}
