import java.math.*;
import java.util.*;
import java.util.function.*;

/** 习题6-8至6-10。内存草稿、本地观察者与单进程请求去重。 */
public class Chapter06Answers {
    static long rate(long n) {
        if (n < 0) throw new IllegalArgumentException("amount");
        return BigDecimal.valueOf(n)
                .multiply(new BigDecimal("0.90"))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    static long reduction(long n) {
        if (n < 0) throw new IllegalArgumentException("amount");
        return n >= 30000 ? n - 3000 : n;
    }

    public static long price(long amount, boolean rateFirst) {
        List<LongUnaryOperator> rules =
                rateFirst
                        ? List.of(Chapter06Answers::rate, Chapter06Answers::reduction)
                        : List.of(Chapter06Answers::reduction, Chapter06Answers::rate);
        for (LongUnaryOperator rule : rules) amount = rule.applyAsLong(amount);
        return amount;
    }

    record Change(int before, int after) {
        Change {
            if (before <= 0 || after <= 0) throw new IllegalArgumentException("quantity");
        }

        void execute(Draft draft) {
            if (draft.quantity != before) throw new IllegalStateException("draft changed");
            draft.quantity = after;
        }

        void undo(Draft draft) {
            if (draft.quantity != after) throw new IllegalStateException("draft changed");
            draft.quantity = before;
        }
    }

    public static class Draft {
        int quantity = 1;
        final Deque<Change> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

        public void edit(int next) {
            if (next <= 0) throw new IllegalArgumentException("quantity");
            Change c = new Change(quantity, next);
            c.execute(this);
            undo.push(c);
            redo.clear();
        }

        public void undo() {
            if (undo.isEmpty()) throw new IllegalStateException("no undo");
            Change c = undo.peek();
            c.undo(this);
            undo.pop();
            redo.push(c);
        }

        public void redo() {
            if (redo.isEmpty()) throw new IllegalStateException("no redo");
            Change c = redo.peek();
            c.execute(this);
            redo.pop();
            undo.push(c);
        }
    }

    public static class Notifier {
        final Set<Consumer<String>> subscribers = new LinkedHashSet<>();
        final List<RuntimeException> failures = new ArrayList<>();

        public void subscribe(Consumer<String> c) {
            subscribers.add(Objects.requireNonNull(c));
        }

        public void unsubscribe(Consumer<String> c) {
            subscribers.remove(c);
        }

        public void publish(String event) {
            for (Consumer<String> c : List.copyOf(subscribers)) {
                try {
                    c.accept(event);
                } catch (RuntimeException ex) {
                    failures.add(ex);
                }
            }
        }
    }

    public static class Submission {
        final Map<String, Long> saved = new HashMap<>();
        final List<String> path = new ArrayList<>();

        public boolean submit(
                String key, long amount, boolean logged, boolean allowed, Notifier n) {
            path.clear();
            path.add("login");
            if (!logged) throw new SecurityException("login");
            path.add("permission");
            if (!allowed) throw new SecurityException("permission");
            path.add("parameters");
            if (key == null || key.isBlank() || amount < 0) throw new IllegalArgumentException();
            if (saved.containsKey(key)) {
                if (saved.get(key).longValue() != amount)
                    throw new IllegalArgumentException("key conflict");
                return false;
            }
            saved.put(key, amount);
            n.publish(key);
            return true;
        }
    }

    static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }

    public static void main(String[] args) {
        check(price(40000, true) == 33000 && price(40000, false) == 33300);
        Draft d = new Draft();
        d.edit(2);
        d.edit(3);
        d.undo();
        check(d.quantity == 2);
        try {
            d.edit(0);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
            check(d.redo.size() == 1);
        }
        d.redo();
        check(d.quantity == 3 && d.undo.size() == 2 && d.redo.isEmpty());
        try {
            d.edit(0);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
            check(d.quantity == 3 && d.undo.size() == 2);
        }
        Notifier n = new Notifier();
        List<String> events = new ArrayList<>();
        n.subscribe(
                e -> {
                    throw new IllegalStateException("refresh");
                });
        n.subscribe(events::add);
        Submission s = new Submission();
        check(s.submit("R1", 33000, true, true, n));
        check(
                !s.submit("R1", 33000, true, true, n)
                        && s.saved.size() == 1
                        && events.size() == 1
                        && n.failures.size() == 1);
        check(s.path.equals(List.of("login", "permission", "parameters")));
        try {
            s.submit("R2", 33000, true, false, n);
            throw new AssertionError();
        } catch (SecurityException expected) {
            check(s.saved.size() == 1 && events.size() == 1);
        }
        Notifier snapshot = new Notifier();
        List<String> trace = new ArrayList<>();
        Consumer<String> second = e -> trace.add("second");
        snapshot.subscribe(
                e -> {
                    trace.add("first");
                    snapshot.unsubscribe(second);
                });
        snapshot.subscribe(second);
        snapshot.publish("one");
        snapshot.publish("two");
        check(trace.equals(List.of("first", "second", "first")));
        System.out.println("6-8..6-10 PASS: 33000/33300 quantity=3 saves=1 notifications=1");
    }
}
