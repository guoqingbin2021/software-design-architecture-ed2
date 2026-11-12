import java.math.*;
import java.util.*;

/** 习题2-8至2-10。回执已通过外部来源验证；这里只演示本地核对与去重。 */
public class Chapter02Answers {
    public record Line(long price, int quantity) {
        public Line {
            if (price < 0 || quantity <= 0) throw new IllegalArgumentException("line");
        }
        public long subtotal() { return Math.multiplyExact(price, quantity); }
    }

    public record Sale(List<Line> lines) {
        public Sale { lines = List.copyOf(lines); }
        public long subtotal() {
            long sum = 0;
            for (Line line : lines) sum = Math.addExact(sum, line.subtotal());
            return sum;
        }
    }

    public static class TaxCalculator {
        public long included(long gross, BigDecimal rate) { return includedTax(gross, rate); }
    }

    /** 本题独立活动，不与满减叠加。 */
    public static long memberPrice(long original, String member) {
        int basisPoints = switch (member) {
            case "NORMAL" -> 10000;
            case "SILVER" -> 9800;
            case "GOLD" -> 9600;
            case "PLATINUM" -> 9500;
            default -> throw new IllegalArgumentException("member");
        };
        if (original < 0) throw new IllegalArgumentException("original");
        return BigDecimal.valueOf(original).multiply(BigDecimal.valueOf(basisPoints))
                .divide(BigDecimal.valueOf(10000), 0, RoundingMode.HALF_UP).longValueExact();
    }
    public static long includedTax(long gross, BigDecimal rate) {
        Objects.requireNonNull(rate);
        if (gross < 0 || rate.signum() < 0) throw new IllegalArgumentException("gross/rate");
        return BigDecimal.valueOf(gross)
                .multiply(rate)
                .divide(BigDecimal.ONE.add(rate), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public enum Outcome {
        PENDING,
        SUCCESS,
        REJECTED
    }

    public static class Payment {
        final String order, attempt;
        final long amount;
        Outcome state = Outcome.PENDING;
        int completions;
        boolean printPending;

        Payment(String order, String attempt, long amount) {
            if (order == null
                    || order.isBlank()
                    || attempt == null
                    || attempt.isBlank()
                    || amount < 0) throw new IllegalArgumentException("payment identity");
            this.order = order;
            this.attempt = attempt;
            this.amount = amount;
        }

        public void accept(String order, String attempt, long amount, Outcome outcome) {
            Objects.requireNonNull(outcome);
            if (!this.order.equals(order) || !this.attempt.equals(attempt) || this.amount != amount)
                throw new IllegalArgumentException("receipt mismatch");
            if (state == Outcome.SUCCESS) return;
            if (state == Outcome.REJECTED && outcome != Outcome.REJECTED)
                throw new IllegalStateException("contradictory final receipt");
            if (outcome == Outcome.SUCCESS) {
                state = Outcome.SUCCESS;
                completions++;
            } else state = outcome;
        }

        public void print(Runnable printer) {
            if (state != Outcome.SUCCESS) throw new IllegalStateException("not paid");
            try {
                printer.run();
                printPending = false;
            } catch (RuntimeException ex) {
                printPending = true;
            }
        }
    }

    static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }

    public static void main(String[] args) {
        Sale sale = new Sale(List.of(new Line(1250, 2), new Line(800, 3)));
        check(sale.subtotal() == 4900);
        long gross = 4400; // 题设给定的外部活动结果，Sale不另行应用折扣。
        long tax = new TaxCalculator().included(gross, new BigDecimal("0.03"));
        check(tax == 128 && gross - tax == 4272 && gross == 4400);
        check(memberPrice(10000, "NORMAL") == 10000 && memberPrice(10000, "SILVER") == 9800);
        check(memberPrice(10000, "GOLD") == 9600 && memberPrice(10000, "PLATINUM") == 9500);
        check(memberPrice(4900, "SILVER") == 4802);
        Payment p = new Payment("O001", "A001", 4400);
        try {
            p.accept("O002", "A001", 4400, Outcome.SUCCESS);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
            check(p.completions == 0);
        }
        for (int invalid = 0; invalid < 2; invalid++) {
            try {
                p.accept("O001", invalid == 0 ? "A002" : "A001",
                        invalid == 0 ? 4400 : 4401, Outcome.SUCCESS);
                throw new AssertionError();
            } catch (IllegalArgumentException expected) {
                check(p.completions == 0 && p.state == Outcome.PENDING);
            }
        }
        p.accept("O001", "A001", 4400, Outcome.PENDING);
        check(p.completions == 0);
        p.accept("O001", "A001", 4400, Outcome.SUCCESS);
        p.accept("O001", "A001", 4400, Outcome.SUCCESS);
        check(p.completions == 1);
        p.print(
                () -> {
                    throw new IllegalStateException("paper out");
                });
        check(p.state == Outcome.SUCCESS && p.printPending && p.amount == 4400);
        Payment rejected = new Payment("O002", "A002", 4400);
        rejected.accept("O002", "A002", 4400, Outcome.REJECTED);
        check(rejected.state == Outcome.REJECTED && rejected.completions == 0);
        try {
            rejected.accept("O002", "A002", 4400, Outcome.SUCCESS);
            throw new AssertionError();
        } catch (IllegalStateException expected) {
            check(rejected.state == Outcome.REJECTED && rejected.completions == 0);
        }
        System.out.println("2-8..2-10 PASS: payable=4400 tax=128 completions=1 printPending=true");
    }
}
