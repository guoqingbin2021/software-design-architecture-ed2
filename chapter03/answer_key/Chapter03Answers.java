import java.math.*;
import java.util.*;

/** 习题3-8至3-10，金额单位为分。 */
public class Chapter03Answers {
    public static long discount(long amount, int basisPoints) {
        if (amount < 0 || basisPoints < 0 || basisPoints > 10000)
            throw new IllegalArgumentException("amount/rate");
        return BigInteger.valueOf(amount)
                .multiply(BigInteger.valueOf(basisPoints))
                .add(BigInteger.valueOf(5000))
                .divide(BigInteger.valueOf(10000))
                .longValueExact();
    }

    public static long price(String level, boolean birthday, long eligible, long excluded) {
        if (eligible < 0 || excluded < 0) throw new IllegalArgumentException("amount");
        int rate =
                switch (level) {
                    case "NORMAL" -> 10000;
                    case "SILVER" -> 9800;
                    case "GOLD" -> 9600;
                    case "PLATINUM" -> 9500;
                    case "ENTERPRISE" -> 9300;
                    default -> throw new IllegalArgumentException("level");
                };
        long current = discount(eligible, rate);
        if (level.equals("PLATINUM") && birthday) current = discount(current, 6000);
        return Math.addExact(current, excluded);
    }

    public static long reward(String level, long paid) {
        if (paid < 0) throw new IllegalArgumentException("paid");
        return switch (level) {
            case "ENTERPRISE" -> 0;
            case "NORMAL", "SILVER", "GOLD", "PLATINUM" -> paid / 100;
            default -> throw new IllegalArgumentException("level");
        };
    }

    public static class Points {
        int balance;

        Points(int balance) {
            if (balance < 0) throw new IllegalArgumentException();
            this.balance = balance;
        }

        public void consume(int amount) {
            if (amount <= 0 || amount > balance) throw new IllegalArgumentException("points");
            balance -= amount;
        }
    }

    static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }

    public static void main(String[] args) {
        String[] levels = {"NORMAL", "SILVER", "GOLD", "PLATINUM", "PLATINUM", "ENTERPRISE"};
        long[] amounts = {10000, 9800, 9600, 9500, 5700, 9300}, points = {100, 98, 96, 95, 57, 0};
        for (int i = 0; i < levels.length; i++) {
            boolean birthday = i == 4;
            long preview = price(levels[i], birthday, 10000, 0);
            long checkout = price(levels[i], birthday, 10000, 0);
            check(
                    preview == amounts[i]
                            && preview == checkout
                            && reward(levels[i], checkout) == points[i]);
        }
        check(price("PLATINUM", true, 15, 0) == 8 && discount(15, 5700) == 9);
        check(price("PLATINUM", true, 8000, 2000) == 6560);
        Points account = new Points(150);
        account.consume(101);
        check(account.balance == 49);
        try {
            account.consume(50);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
            check(account.balance == 49);
        }
        System.out.println(
                "3-8..3-10 PASS: 10000,9800,9600,9500,5700,9300; birthday15=8; points=49");
    }
}
