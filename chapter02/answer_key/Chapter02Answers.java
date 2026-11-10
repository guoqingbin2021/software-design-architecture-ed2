import java.math.*;
import java.util.*;

/** 习题2-8至2-10。回执已通过外部来源验证；这里只演示本地核对与去重。 */
public class Chapter02Answers {
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
        check(includedTax(4400, new BigDecimal("0.03")) == 128);
        Payment p = new Payment("O001", "A001", 4400);
        try {
            p.accept("O002", "A001", 4400, Outcome.SUCCESS);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
            check(p.completions == 0);
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
        System.out.println("2-8..2-10 PASS: payable=4400 tax=128 completions=1 printPending=true");
    }
}
