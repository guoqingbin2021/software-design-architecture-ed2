import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** 习题5-8至5-10。allowedStores必须来自可信服务端授权结果。 */
public class Chapter05Answers {
    public record Row(String store, String name, String phone) {}

    public static List<Row> export(List<Row> rows, Set<String> allowedStores) {
        Set<String> allowed = Set.copyOf(allowedStores);
        return List.copyOf(rows).stream()
                .filter(r -> allowed.contains(r.store()))
                .map(
                        r -> {
                            if (r.phone() == null || !r.phone().matches("[0-9]{11}"))
                                throw new IllegalArgumentException("phone format");
                            return new Row(
                                    r.store(),
                                    r.name(),
                                    r.phone().substring(0, 3) + "****" + r.phone().substring(7));
                        })
                .toList();
    }

    public static <T> T audited(boolean allowed, Supplier<T> target, List<String> audit) {
        if (!allowed) {
            audit.add("EXPORT:DENIED");
            throw new SecurityException("denied");
        }
        try {
            T result = target.get();
            audit.add("EXPORT:SUCCESS");
            return result;
        } catch (RuntimeException ex) {
            audit.add("EXPORT:FAILED");
            throw ex;
        }
    }

    static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }

    public static void main(String[] args) {
        var rows =
                List.of(
                        new Row("S01", "张敏", "13812345678"),
                        new Row("S02", "李明", "13912345678"),
                        new Row("S03", "王宁", "13712345678"));
        List<String> audit = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        var output =
                audited(
                        true,
                        () -> {
                            calls.incrementAndGet();
                            return export(rows, Set.of("S01", "S03"));
                        },
                        audit);
        check(
                output.size() == 2
                        && output.get(0).phone().equals("138****5678")
                        && output.get(1).store().equals("S03"));
        check(rows.get(0).phone().equals("13812345678") && calls.get() == 1);
        try {
            audited(
                    false,
                    () -> {
                        calls.incrementAndGet();
                        return rows;
                    },
                    audit);
            throw new AssertionError();
        } catch (SecurityException expected) {
            check(calls.get() == 1);
        }
        RuntimeException original = new IllegalStateException("business failure");
        try {
            audited(
                    true,
                    () -> {
                        calls.incrementAndGet();
                        throw original;
                    },
                    audit);
            throw new AssertionError();
        } catch (IllegalStateException ex) {
            check(ex == original && calls.get() == 2);
        }
        check(audit.equals(List.of("EXPORT:SUCCESS", "EXPORT:DENIED", "EXPORT:FAILED")));
        check(audit.stream().noneMatch(s -> s.contains("13812345678")));
        System.out.println("5-8..5-10 PASS: stores=S01,S03 audit=SUCCESS,DENIED,FAILED");
    }
}
