import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 习题1-8至1-10：金额、成交快照与打印。仅使用内存教学对象。 */
public class Chapter01Answers {
    public record Line(long price, int quantity) {
        public Line {
            if (price < 0 || quantity <= 0) throw new IllegalArgumentException("price/quantity");
        }

        long amount() {
            return Math.multiplyExact(price, quantity);
        }
    }

    public static long subtotal(List<Line> lines) {
        long sum = 0;
        for (Line line : List.copyOf(lines)) sum = Math.addExact(sum, line.amount());
        return sum;
    }

    public static long payable(List<Line> lines) {
        long sum = subtotal(lines);
        return sum >= 4000 ? sum - 500 : sum;
    }

    public record Receipt(String id, List<Line> lines, long amount) {
        public Receipt {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
            lines = List.copyOf(lines);
            if (amount != payable(lines)) throw new IllegalArgumentException("amount");
        }
    }

    public interface Printer {
        void print(Receipt receipt);
    }

    public static class Checkout {
        final Map<String, Receipt> saved = new HashMap<>();
        final Set<String> pending = new HashSet<>();
        final Printer printer;

        public Checkout(Printer printer) {
            this.printer = Objects.requireNonNull(printer);
        }

        public void submit(Receipt receipt) {
            Receipt old = saved.get(receipt.id());
            if (old != null) {
                if (!old.equals(receipt)) throw new IllegalArgumentException("id conflict");
                return;
            }
            saved.put(receipt.id(), receipt);
            printSaved(receipt);
        }

        private void printSaved(Receipt r) {
            try {
                printer.print(r);
                pending.remove(r.id());
            } catch (RuntimeException ex) {
                pending.add(r.id());
            }
        }

        public void reprint(String id) {
            Receipt r = saved.get(id);
            if (r == null) throw new IllegalArgumentException("unknown id");
            printSaved(r);
        }
    }

    static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }

    public static void main(String[] args) {
        var lines = List.of(new Line(1250, 2), new Line(800, 3));
        check(payable(List.of(new Line(3999, 1))) == 3999);
        check(payable(List.of(new Line(4000, 1))) == 3500);
        check(payable(List.of()) == 0);
        Receipt r = new Receipt("S01-001", lines, payable(lines));
        check(subtotal(lines) == 4900 && r.amount() == 4400);
        check(payable(List.of(new Line(1250, 2), new Line(900, 3))) == 4700);
        List<Receipt> consoleReceived = new ArrayList<>(), memoryReceived = new ArrayList<>();
        Printer consolePrinter = x -> {
            consoleReceived.add(x);
            System.out.println("ConsolePrinter: " + x.id() + "|" + x.amount());
        };
        Printer memoryPrinter = memoryReceived::add;
        new Checkout(consolePrinter).submit(r);
        new Checkout(memoryPrinter).submit(r);
        check(consoleReceived.equals(List.of(r)) && memoryReceived.equals(List.of(r)));
        check(lines.equals(List.of(new Line(1250, 2), new Line(800, 3))));
        AtomicInteger outputs = new AtomicInteger();
        Checkout normal = new Checkout(x -> outputs.incrementAndGet());
        normal.submit(r);
        normal.submit(r);
        check(normal.saved.size() == 1 && outputs.get() == 1);
        AtomicInteger calls = new AtomicInteger();
        Checkout missingPaper =
                new Checkout(
                        x -> {
                            if (calls.getAndIncrement() == 0)
                                throw new IllegalStateException("paper out before output");
                        });
        missingPaper.submit(r);
        check(missingPaper.saved.size() == 1 && missingPaper.pending.contains(r.id()));
        missingPaper.submit(r);
        check(calls.get() == 1);
        missingPaper.reprint(r.id());
        check(missingPaper.pending.isEmpty() && calls.get() == 2 && missingPaper.saved.size() == 1);
        Receipt different = new Receipt(r.id(), List.of(new Line(4500, 1)), 4000);
        try {
            missingPaper.submit(different);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
            check(missingPaper.saved.get(r.id()).equals(r));
        }
        try {
            new Line(1, 0);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
        }
        try {
            subtotal(List.of(new Line(Long.MAX_VALUE, 2)));
            throw new AssertionError();
        } catch (ArithmeticException expected) {
        }
        try {
            new Line(-1, 1);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
        }
        try {
            subtotal(List.of(new Line(Long.MAX_VALUE, 1), new Line(1, 1)));
            throw new AssertionError();
        } catch (ArithmeticException expected) {
        }
        System.out.println(
                "1-8..1-10 PASS: subtotal=4900 payable=4400 new-price-payable=4700 saved=1");
    }
}
