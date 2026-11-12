import java.math.*;
import java.util.*;

/** 习题3-8至3-10，金额单位为分。 */
public class Chapter03Answers {
    public interface MemberPolicy {
        long quote(long eligible, boolean birthday);
        long reward(long paid);
    }

    public record RatePolicy(int basisPoints, boolean birthdayOffer, boolean earnsPoints)
            implements MemberPolicy {
        public RatePolicy {
            if (basisPoints < 0 || basisPoints > 10000) throw new IllegalArgumentException("rate");
        }
        public long quote(long eligible, boolean birthday) {
            long first = discount(eligible, basisPoints);
            return birthdayOffer && birthday ? discount(first, 6000) : first;
        }
        public long reward(long paid) {
            if (paid < 0) throw new IllegalArgumentException("paid");
            return earnsPoints ? paid / 100 : 0;
        }
    }

    private static final Map<String, MemberPolicy> policies = new HashMap<>(Map.of(
            "NORMAL", new RatePolicy(10000, false, true),
            "SILVER", new RatePolicy(9800, false, true),
            "GOLD", new RatePolicy(9600, false, true),
            "PLATINUM", new RatePolicy(9500, true, true),
            "ENTERPRISE", new RatePolicy(9300, false, false)));

    public static void register(String level, MemberPolicy policy) {
        if (level == null || level.isBlank()) throw new IllegalArgumentException("level");
        if (policies.putIfAbsent(level, Objects.requireNonNull(policy)) != null)
            throw new IllegalArgumentException("duplicate level");
    }

    static MemberPolicy policy(String level) {
        MemberPolicy p = policies.get(level);
        if (p == null) throw new IllegalArgumentException("level");
        return p;
    }
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
        return Math.addExact(policy(level).quote(eligible, birthday), excluded);
    }

    public static long reward(String level, long paid) {
        return policy(level).reward(paid);
    }

    public record PointsView(int balance, List<String> labels) {
        public PointsView { labels = List.copyOf(labels); }
    }

    public static class Points {
        int balance;
        final List<String> labels = new ArrayList<>();

        Points(int balance) {
            if (balance < 0) throw new IllegalArgumentException();
            this.balance = balance;
        }

        public void consume(int amount) {
            if (amount <= 0 || amount > balance) throw new IllegalArgumentException("points");
            balance -= amount;
        }

        public PointsView query() { return new PointsView(balance, labels); }
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
        PointsView oldSnapshot = account.query();
        account.consume(101);
        check(account.balance == 49 && oldSnapshot.balance() == 150 && account.query().balance() == 49);
        try {
            account.consume(50);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
            check(account.balance == 49);
        }
        PointsView snapshot = account.query();
        account.labels.add("VIP");
        check(snapshot.balance() == 49 && snapshot.labels().isEmpty());
        try {
            snapshot.labels().add("client edit");
            throw new AssertionError();
        } catch (UnsupportedOperationException expected) {
            check(account.labels.equals(List.of("VIP")));
        }
        for (int invalid : new int[] {0, -1}) {
            try { account.consume(invalid); throw new AssertionError(); }
            catch (IllegalArgumentException expected) { check(account.balance == 49); }
        }
        check(discount(Long.MAX_VALUE, 10000) == Long.MAX_VALUE);
        register("TRIAL", new RatePolicy(9000, false, false));
        check(price("TRIAL", false, 10000, 0) == 9000 && reward("TRIAL", 9000) == 0);
        System.out.println(
                "3-8..3-10 PASS: 10000,9800,9600,9500,5700,9300; birthday15=8; points=49");
    }
}
