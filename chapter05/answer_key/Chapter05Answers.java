import java.util.*;

/** 5-8装饰器、5-9代理与审计、5-10按岗位扩展。金额单位：分。 */
public class Chapter05Answers {
    public record Receipt(String id, long amount) {
        public Receipt {
            if (id == null || id.isBlank() || amount < 0) throw new IllegalArgumentException("receipt");
        }
    }
    public interface ReceiptPrinter { void print(Receipt receipt); }
    public static class LegacyPrinter {
        String lastText;
        public void printText(String text) { lastText = text; }
    }
    public static class LegacyPrinterAdapter implements ReceiptPrinter {
        private final LegacyPrinter legacy;
        public LegacyPrinterAdapter(LegacyPrinter legacy) { this.legacy = Objects.requireNonNull(legacy); }
        public void print(Receipt receipt) { legacy.printText(receipt.id() + "|" + receipt.amount()); }
    }

    public record Row(String store, String name, String phone) {}
    /** Actor由可信认证/授权层创建，调用者不能自报权限和门店。 */
    public record Actor(String id, Set<String> permissions, Set<String> allowedStores, String role) {
        public Actor {
            Objects.requireNonNull(id);
            permissions = Set.copyOf(permissions);
            allowedStores = Set.copyOf(allowedStores);
            Objects.requireNonNull(role);
        }
    }
    public interface Exporter { List<Map<String, String>> export(Actor actor); }

    /** 门店过滤先完成，再交给脱敏与岗位投影。 */
    public static class StoreExport implements Exporter {
        private final List<Row> source;
        public StoreExport(List<Row> source) { this.source = List.copyOf(source); }
        public List<Map<String, String>> export(Actor actor) {
            return source.stream().filter(row -> actor.allowedStores().contains(row.store()))
                    .map(row -> {
                        Map<String, String> result = new LinkedHashMap<>();
                        result.put("store", row.store()); result.put("name", row.name());
                        result.put("phone", row.phone());
                        return Collections.unmodifiableMap(result);
                    }).toList();
        }
    }
    /** 列集合只由服务端岗位配置决定，接口不接收客户端列清单。 */
    public static class RoleExport implements Exporter {
        private final Exporter target;
        private final Map<String, List<String>> columns;
        public RoleExport(Exporter target, Map<String, List<String>> columns) {
            this.target = Objects.requireNonNull(target);
            Map<String, List<String>> copy = new HashMap<>();
            columns.forEach((role, fields) -> {
                if (!Set.of("store", "name", "phone").containsAll(fields))
                    throw new IllegalArgumentException("unknown field");
                copy.put(role, List.copyOf(fields));
            });
            this.columns = Map.copyOf(copy);
        }
        public List<Map<String, String>> export(Actor actor) {
            List<String> fields = columns.get(actor.role());
            if (fields == null) throw new SecurityException("role");
            return target.export(actor).stream().map(row -> {
                Map<String, String> result = new LinkedHashMap<>();
                for (String field : fields) result.put(field, row.get(field));
                return Collections.unmodifiableMap(result);
            }).toList();
        }
    }
    public static class MaskingDecorator implements Exporter {
        private final Exporter target;
        public MaskingDecorator(Exporter target) { this.target = Objects.requireNonNull(target); }
        public List<Map<String, String>> export(Actor actor) {
            return target.export(actor).stream().map(row -> {
                Map<String, String> result = new LinkedHashMap<>(row);
                if (result.containsKey("phone")) {
                    String phone = result.get("phone");
                    if (phone == null || !phone.matches("[0-9]{11}"))
                        throw new IllegalArgumentException("phone format");
                    result.put("phone", phone.substring(0, 3) + "****" + phone.substring(7));
                }
                return Collections.unmodifiableMap(result);
            }).toList();
        }
    }
    public static class ProtectionProxy implements Exporter {
        private final Exporter target;
        public ProtectionProxy(Exporter target) { this.target = Objects.requireNonNull(target); }
        public List<Map<String, String>> export(Actor actor) {
            if (!actor.permissions().contains("price:export")) throw new SecurityException("permission");
            return target.export(actor);
        }
    }
    public static class AuditDecorator implements Exporter {
        private final Exporter target;
        private final List<String> audit;
        public AuditDecorator(Exporter target, List<String> audit) { this.target = target; this.audit = audit; }
        public List<Map<String, String>> export(Actor actor) {
            try {
                var result = target.export(actor);
                audit.add(actor.id() + ":EXPORT:SUCCESS");
                return result;
            } catch (SecurityException ex) {
                audit.add(actor.id() + ":EXPORT:DENIED");
                throw ex;
            } catch (RuntimeException ex) {
                audit.add(actor.id() + ":EXPORT:FAILED");
                throw ex;
            }
        }
    }
    static Exporter assemble(List<Row> rows, Map<String, List<String>> columns, List<String> audit) {
        return new AuditDecorator(new ProtectionProxy(new RoleExport(new MaskingDecorator(new StoreExport(rows)), columns)), audit);
    }
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }

    public static void main(String[] args) {
        LegacyPrinter old = new LegacyPrinter();
        ReceiptPrinter printer = new LegacyPrinterAdapter(old);
        printer.print(new Receipt("P-A01", 4900));
        check(old.lastText.equals("P-A01|4900"));
        var rows = List.of(new Row("STORE-A", "张敏", "13812345678"),
                new Row("STORE-B", "李明", "13912345678"), new Row("STORE-C", "王宁", "13712345678"));
        Map<String, List<String>> columns = Map.of("CASHIER", List.of("store", "name", "phone"),
                "AUDITOR", List.of("store", "name"));
        Actor cashier = new Actor("U1", Set.of("price:export"), Set.of("STORE-A", "STORE-C"), "CASHIER");
        Actor auditor = new Actor("U2", Set.of("price:export"), cashier.allowedStores(), "AUDITOR");
        List<String> audit = new ArrayList<>();
        Exporter exporter = assemble(rows, columns, audit);
        var output = exporter.export(cashier);
        check(output.size() == 2 && output.get(0).get("phone").equals("138****5678"));
        check(output.get(1).get("store").equals("STORE-C") && output.get(0).size() == 3);
        var auditView = exporter.export(auditor);
        check(auditView.size() == 2 && auditView.get(0).keySet().equals(Set.of("store", "name")));
        check(rows.get(0).phone().equals("13812345678"));
        try { exporter.export(new Actor("U3", Set.of(), cashier.allowedStores(), "CASHIER"));
            throw new AssertionError();
        } catch (SecurityException expected) { check(audit.get(2).equals("U3:EXPORT:DENIED")); }
        RuntimeException original = new IllegalStateException("business failure");
        check(audit.stream().noneMatch(value -> value.matches(".*1[0-9]{10}.*")));
        // 成功/无权/业务异常分别计数；三条路径均通过同一保护代理和外层审计。
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        List<String> countedAudit = new ArrayList<>();
        Exporter counted = new AuditDecorator(new ProtectionProxy(actor -> {
            calls.incrementAndGet();
            if (actor.id().equals("FAIL")) throw original;
            return new RoleExport(new MaskingDecorator(new StoreExport(rows)), columns).export(actor);
        }), countedAudit);
        counted.export(cashier);
        check(calls.getAndSet(0) == 1);
        try { counted.export(new Actor("DENY", Set.of(), cashier.allowedStores(), "CASHIER"));
            throw new AssertionError();
        } catch (SecurityException expected) { check(calls.getAndSet(0) == 0); }
        try { counted.export(new Actor("FAIL", Set.of("price:export"), cashier.allowedStores(), "CASHIER"));
            throw new AssertionError();
        } catch (IllegalStateException ex) { check(ex == original && calls.getAndSet(0) == 1); }
        check(countedAudit.equals(List.of("U1:EXPORT:SUCCESS", "DENY:EXPORT:DENIED", "FAIL:EXPORT:FAILED")));

        // 只增加岗位列配置；代理、脱敏装饰器和适配器均不改动。
        Map<String, List<String>> extended = new HashMap<>(columns);
        extended.put("SUPERVISOR", List.of("store", "name"));
        var supervisor = assemble(rows, extended, new ArrayList<>()).export(
                new Actor("U4", Set.of("price:export"), cashier.allowedStores(), "SUPERVISOR"));
        check(supervisor.get(0).keySet().equals(Set.of("store", "name")));
        check(assemble(rows, extended, new ArrayList<>()).export(cashier).equals(output));
        check(rows.equals(List.of(new Row("STORE-A", "张敏", "13812345678"),
                new Row("STORE-B", "李明", "13912345678"), new Row("STORE-C", "王宁", "13712345678"))));
        System.out.println("5-8..5-10 PASS: fixed-role columns; masked STORE-A/STORE-C; audit SUCCESS/DENIED/FAILED");
    }
}
