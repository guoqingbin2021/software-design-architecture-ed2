import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 促销结算、草稿历史、请求检查与事件通知的单线程内存示例。
 * 使用Java 17标准库；数据仅存于内存，不提供持久化事务和可靠消息投递。
 */
public final class Chapter6BodyReference {
    static BigDecimal money(BigDecimal value) {
        Objects.requireNonNull(value, "金额不能为空");
        if (value.signum() < 0) throw new IllegalArgumentException("负金额");
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal fromCents(long cents) {
        if (cents < 0) throw new IllegalArgumentException("负分值");
        return BigDecimal.valueOf(cents, 2);
    }

    static long toLongCents(BigDecimal amount) {
        Objects.requireNonNull(amount);
        if (amount.signum() < 0) throw new IllegalArgumentException("负金额");
        return amount.movePointRight(2).longValueExact();
    }

    interface PricingStrategy {
        BigDecimal apply(BigDecimal amount);
    }

    record RateDiscount(BigDecimal rate) implements PricingStrategy {
        RateDiscount {
            Objects.requireNonNull(rate);
            if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException("折扣率须在[0,1]内");
        }

        public BigDecimal apply(BigDecimal amount) {
            return money(money(amount).multiply(rate));
        }
    }

    record ThresholdReduction(BigDecimal threshold, BigDecimal reduction)
            implements PricingStrategy {
        ThresholdReduction {
            threshold = money(threshold);
            reduction = money(reduction);
        }

        public BigDecimal apply(BigDecimal amount) {
            BigDecimal current = money(amount);
            return current.compareTo(threshold) < 0
                    ? current
                    : money(current.subtract(reduction).max(BigDecimal.ZERO));
        }
    }

    record CombinedDiscount(List<PricingStrategy> steps) implements PricingStrategy {
        CombinedDiscount {
            steps = List.copyOf(steps);
        }

        public BigDecimal apply(BigDecimal amount) {
            BigDecimal result = money(amount);
            for (PricingStrategy step : steps) result = money(step.apply(result));
            return result;
        }
    }

    static final ThresholdReduction PROMOTION =
            new ThresholdReduction(new BigDecimal("300"), new BigDecimal("30"));

    static BigDecimal quote(DraftSale sale) {
        PricingStrategy rules =
                new CombinedDiscount(
                        List.of(
                                new RateDiscount(BigDecimal.valueOf(sale.discountPercent, 2)),
                                PROMOTION));
        // 每次从未优惠的原始金额开始，命令参数只进入一次折扣策略。
        return rules.apply(fromCents(sale.originalCents));
    }

    static final class DraftSale {
        enum State {
            DRAFT,
            SUBMITTED
        }

        final String id;
        final String ownerId;
        final long originalCents;
        private int discountPercent = 100;
        private State state = State.DRAFT;

        DraftSale(String id, String ownerId, long originalCents) {
            this.id = Objects.requireNonNull(id);
            this.ownerId = Objects.requireNonNull(ownerId);
            fromCents(originalCents);
            this.originalCents = originalCents;
        }

        void requireDraft() {
            if (state != State.DRAFT) throw new IllegalStateException("已提交单据不能编辑");
        }

        void changeDiscount(int expected, int next) {
            requireDraft();
            if (next < 0 || next > 100) throw new IllegalArgumentException("非法折扣");
            if (discountPercent != expected) throw new IllegalStateException("草稿与历史冲突");
            discountPercent = next;
        }
    }

    interface Command {
        DraftSale target();

        void execute();

        void undo();

        void redo();
    }

    static final class SetDiscount implements Command {
        enum State {
            NEW,
            DONE,
            UNDONE
        }

        final DraftSale sale;
        final int after;
        int before;
        State state = State.NEW;

        SetDiscount(DraftSale sale, int after) {
            this.sale = Objects.requireNonNull(sale);
            this.after = after;
        }

        public DraftSale target() {
            return sale;
        }

        public void execute() {
            require(State.NEW);
            int original = sale.discountPercent;
            sale.changeDiscount(original, after);
            before = original;
            state = State.DONE;
        }

        public void undo() {
            require(State.DONE);
            sale.changeDiscount(after, before);
            state = State.UNDONE;
        }

        public void redo() {
            require(State.UNDONE);
            sale.changeDiscount(before, after);
            state = State.DONE;
        }

        void require(State expected) {
            if (state != expected) throw new IllegalStateException("命令状态不允许该操作");
        }
    }

    static final class History {
        final DraftSale sale;
        final Deque<Command> undo = new ArrayDeque<>();
        final Deque<Command> redo = new ArrayDeque<>();

        History(DraftSale sale) {
            this.sale = Objects.requireNonNull(sale);
        }

        void execute(Command command) {
            sale.requireDraft();
            Objects.requireNonNull(command);
            if (command.target() != sale) throw new IllegalArgumentException("命令目标错误");
            command.execute();
            undo.push(command);
            redo.clear();
        }

        void undo() {
            sale.requireDraft();
            if (undo.isEmpty()) return;
            Command command = undo.peek();
            command.undo();
            undo.pop();
            redo.push(command);
        }

        void redo() {
            sale.requireDraft();
            if (redo.isEmpty()) return;
            Command command = redo.peek();
            command.redo();
            redo.pop();
            undo.push(command);
        }
    }

    // 该上下文由可信服务端代码构造；不可直接信任客户端上报的权限集合。
    record Principal(
            String userId, boolean authenticated, Set<String> permissions, Set<String> stores) {
        Principal {
            userId = Objects.requireNonNull(userId);
            permissions = Set.copyOf(permissions);
            stores = Set.copyOf(stores);
        }
    }

    record QueryRequest(String storeId) {}

    record AdjustRequest(String storeId, BigDecimal newPrice) {}

    record CheckoutRequest(String requestId, String saleId, int discountPercent) {}

    static void gate(List<String> trace, String stage, boolean accepted) {
        trace.add(stage);
        if (!accepted) throw new SecurityException(stage + " rejected");
    }

    static void login(Principal principal, List<String> trace) {
        gate(trace, "LOGIN", principal != null && principal.authenticated());
    }

    // 三种场景分别声明政策和业务入口，避免用调价PriceService.adjust代替查询或结算。
    static void query(
            Principal principal, QueryRequest request, List<String> trace, Runnable queryAction) {
        login(principal, trace);
        gate(trace, "PERMISSION", principal.permissions().contains("price:query"));
        gate(trace, "SCOPE", principal.stores().contains(request.storeId()));
        queryAction.run();
    }

    static void adjust(
            Principal principal, AdjustRequest request, List<String> trace, Runnable adjustAction) {
        login(principal, trace);
        gate(trace, "PERMISSION", principal.permissions().contains("price:adjust"));
        gate(trace, "SCOPE", principal.stores().contains(request.storeId()));
        gate(
                trace,
                "INPUT",
                request.newPrice() != null
                        && request.newPrice().signum() > 0
                        && money(request.newPrice()).signum() > 0);
        adjustAction.run();
    }

    static void checkCheckout(Principal principal, List<String> trace) {
        login(principal, trace);
        gate(trace, "PERMISSION", principal.permissions().contains("sale:submit"));
    }

    record ProductChange(long version, String productId, BigDecimal price) {}

    record SaleCommitted(String eventId, String saleId, long payableCents) {}

    interface Subscription extends AutoCloseable {
        void close();
    }

    static final class LocalHub<E> {
        final LinkedHashMap<Long, Consumer<E>> observers = new LinkedHashMap<>();
        final List<String> failures = new ArrayList<>();
        long nextId;
        boolean notifying;

        Subscription subscribe(Consumer<E> observer) {
            long id = ++nextId;
            observers.put(id, Objects.requireNonNull(observer));
            return () -> observers.remove(id);
        }

        void publish(E event) {
            if (notifying) throw new IllegalStateException("不得递归通知");
            List<Consumer<E>> snapshot = List.copyOf(observers.values());
            notifying = true;
            try {
                for (Consumer<E> observer : snapshot) {
                    try {
                        observer.accept(event);
                    } catch (RuntimeException failure) {
                        failures.add(failure.toString());
                    }
                }
            } finally {
                notifying = false;
            }
        }
    }

    record SaleResult(String saleId, long payableCents, String eventId) {}

    record CompletedRequest(String userId, CheckoutRequest request, SaleResult result) {}

    record StoreState(
            Map<String, CompletedRequest> requests,
            Map<String, SaleResult> sales,
            Map<String, SaleCommitted> events) {
        StoreState {
            requests = Map.copyOf(requests);
            sales = Map.copyOf(sales);
            events = Map.copyOf(events);
        }
    }

    enum Fault {
        NONE,
        BEFORE_COMMIT,
        RESPONSE_LOST
    }

    static final class ResponseLost extends RuntimeException {}

    static final class CheckoutService {
        StoreState store = new StoreState(Map.of(), Map.of(), Map.of());
        final LocalHub<SaleCommitted> hub = new LocalHub<>();

        SaleResult submit(
                Principal principal,
                DraftSale sale,
                CheckoutRequest request,
                Fault fault,
                List<String> trace) {
            // 通知回调期间拒绝再次提交，避免销售保存后无法发布对应事件。
            if (hub.notifying) throw new IllegalStateException("通知回调期间不能提交销售单");
            checkCheckout(principal, trace);
            if (request.requestId() == null || request.requestId().isBlank())
                throw new IllegalArgumentException("缺少请求标识");
            if (!sale.id.equals(request.saleId()) || !sale.ownerId.equals(principal.userId()))
                throw new SecurityException("销售单与可信用户不匹配");
            CompletedRequest previous = store.requests().get(request.requestId());
            if (previous != null) {
                if (!previous.userId().equals(principal.userId())
                        || !previous.request().equals(request))
                    throw new IllegalArgumentException("同一请求标识不得更换用户、销售单或参数");
                return previous.result();
            }
            sale.requireDraft();
            if (store.sales().containsKey(sale.id)) throw new IllegalStateException("销售单已提交");
            if (sale.discountPercent != request.discountPercent())
                throw new IllegalStateException("提交参数与当前草稿不一致");

            // 此处重新计算；不接收调用方早先的试算金额，也不使用已优惠金额作为输入。
            long cents = toLongCents(quote(sale));
            String eventId = "sale-committed:" + sale.id;
            SaleResult result = new SaleResult(sale.id, cents, eventId);
            SaleCommitted event = new SaleCommitted(eventId, sale.id, cents);
            Map<String, CompletedRequest> requests = new HashMap<>(store.requests());
            Map<String, SaleResult> sales = new HashMap<>(store.sales());
            Map<String, SaleCommitted> events = new HashMap<>(store.events());
            requests.put(
                    request.requestId(), new CompletedRequest(principal.userId(), request, result));
            sales.put(sale.id, result);
            events.put(eventId, event);
            StoreState next = new StoreState(requests, sales, events);
            if (fault == Fault.BEFORE_COMMIT) throw new IllegalStateException("模拟保存前失败");
            // 单线程可观察提交点：先完整准备新状态；以下赋值间无回调或故障注入。
            // 不模拟进程崩溃，不承诺持久化原子性或并发安全。
            sale.state = DraftSale.State.SUBMITTED;
            store = next;
            hub.publish(event);
            if (fault == Fault.RESPONSE_LOST) throw new ResponseLost();
            return result;
        }

        void replay(String eventId) {
            hub.publish(Objects.requireNonNull(store.events().get(eventId)));
        }
    }

    static Principal user(Set<String> permissions) {
        return new Principal("u1", true, permissions, Set.of("S1"));
    }

    static Principal cashier() {
        return user(Set.of("sale:submit"));
    }

    static DraftSale sale(String id) {
        return new DraftSale(id, "u1", 40000);
    }

    static CheckoutRequest request(String id, DraftSale sale) {
        return new CheckoutRequest(id, sale.id, sale.discountPercent);
    }

    static DraftSale discounted(String id) {
        DraftSale sale = sale(id);
        new History(sale).execute(new SetDiscount(sale, 90));
        return sale;
    }

    static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual))
            throw new AssertionError("expected=" + expected + ", actual=" + actual);
    }

    static void amount(String expected, BigDecimal actual) {
        equal(new BigDecimal(expected), actual);
    }

    static <T extends Throwable> void expect(Class<T> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("expected " + type.getSimpleName(), failure);
        }
        throw new AssertionError("missing " + type.getSimpleName());
    }

    static int passed;

    static void test(String name, Runnable action) {
        action.run();
        passed++;
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) {
        test(
                "money shared chapter-3/chapter-6 samples",
                () -> {
                    long[] inputs = {10000, 10000, 10000, 1, 5, 5};
                    long[] outputs = {9300, 9600, 5700, 1, 3, 2};
                    String[][] rates = {
                        {"0.93"},
                        {"0.96"},
                        {"0.95", "0.6"},
                        {"0.95", "0.6"},
                        {"0.5"},
                        {"0.5", "0.5"}
                    };
                    for (int i = 0; i < inputs.length; i++) {
                        BigDecimal value = fromCents(inputs[i]);
                        for (String rate : rates[i])
                            value = new RateDiscount(new BigDecimal(rate)).apply(value);
                        equal(outputs[i], toLongCents(value));
                    }
                });
        test(
                "money rejects negative, fractional cent, overflow and invalid rate",
                () -> {
                    expect(IllegalArgumentException.class, () -> money(new BigDecimal("-0.001")));
                    expect(IllegalArgumentException.class, () -> fromCents(-1));
                    expect(ArithmeticException.class, () -> toLongCents(new BigDecimal("0.001")));
                    expect(
                            ArithmeticException.class,
                            () -> toLongCents(new BigDecimal("92233720368547758.08")));
                    expect(
                            IllegalArgumentException.class,
                            () -> new RateDiscount(new BigDecimal("1.01")));
                    amount("0.00", PROMOTION.apply(BigDecimal.ZERO));
                });
        test(
                "ordered promotions produce 330.00 and 333.00",
                () -> {
                    PricingStrategy rate = new RateDiscount(new BigDecimal("0.9"));
                    amount(
                            "330.00",
                            new CombinedDiscount(List.of(rate, PROMOTION))
                                    .apply(new BigDecimal("400")));
                    amount(
                            "333.00",
                            new CombinedDiscount(List.of(PROMOTION, rate))
                                    .apply(new BigDecimal("400")));
                    amount(
                            "270.00",
                            new CombinedDiscount(List.of(rate, PROMOTION))
                                    .apply(new BigDecimal("300")));
                });
        test(
                "draft trace 100/370.00 -> 90/330.00 -> undo/370.00 -> redo/330.00",
                () -> {
                    DraftSale sale = sale("trace");
                    History history = new History(sale);
                    amount("370.00", quote(sale));
                    history.execute(new SetDiscount(sale, 90));
                    amount("330.00", quote(sale));
                    history.undo();
                    amount("370.00", quote(sale));
                    history.redo();
                    amount("330.00", quote(sale));
                    equal(40000L, sale.originalCents);
                });
        test(
                "failed edit preserves command state, discount and both history stacks",
                () -> {
                    DraftSale sale = sale("history");
                    History history = new History(sale);
                    history.execute(new SetDiscount(sale, 90));
                    history.undo();
                    SetDiscount invalid = new SetDiscount(sale, 110);
                    expect(IllegalArgumentException.class, () -> history.execute(invalid));
                    equal(100, sale.discountPercent);
                    equal(0, history.undo.size());
                    equal(1, history.redo.size());
                    equal(SetDiscount.State.NEW, invalid.state);
                    history.redo();
                    equal(90, sale.discountPercent);
                });
        test(
                "wrong target, repeated command and new branch history",
                () -> {
                    DraftSale sale = sale("branch");
                    History history = new History(sale);
                    expect(
                            IllegalArgumentException.class,
                            () -> history.execute(new SetDiscount(sale("other"), 90)));
                    SetDiscount command = new SetDiscount(sale, 90);
                    history.execute(command);
                    expect(IllegalStateException.class, () -> history.execute(command));
                    history.execute(new SetDiscount(sale, 80));
                    history.undo();
                    equal(90, sale.discountPercent);
                    equal(1, history.undo.size());
                    equal(1, history.redo.size());
                    history.execute(new SetDiscount(sale, 70));
                    equal(0, history.redo.size());
                    equal(2, history.undo.size());
                    equal(70, sale.discountPercent);
                });
        test(
                "query policy executes query with LOGIN/PERMISSION/SCOPE",
                () -> {
                    List<String> trace = new ArrayList<>();
                    int[] calls = {0};
                    query(
                            user(Set.of("price:query")),
                            new QueryRequest("S1"),
                            trace,
                            () -> calls[0]++);
                    equal(List.of("LOGIN", "PERMISSION", "SCOPE"), trace);
                    equal(1, calls[0]);
                });
        test(
                "query permission cannot authorize adjustment",
                () -> {
                    List<String> trace = new ArrayList<>();
                    int[] calls = {0};
                    expect(
                            SecurityException.class,
                            () ->
                                    adjust(
                                            user(Set.of("price:query")),
                                            new AdjustRequest("S1", new BigDecimal("11")),
                                            trace,
                                            () -> calls[0]++));
                    equal(List.of("LOGIN", "PERMISSION"), trace);
                    equal(0, calls[0]);
                });
        test(
                "adjustment scope rejection stops before input and business",
                () -> {
                    List<String> trace = new ArrayList<>();
                    int[] calls = {0};
                    expect(
                            SecurityException.class,
                            () ->
                                    adjust(
                                            user(Set.of("price:adjust")),
                                            new AdjustRequest("S2", new BigDecimal("11")),
                                            trace,
                                            () -> calls[0]++));
                    equal(List.of("LOGIN", "PERMISSION", "SCOPE"), trace);
                    equal(0, calls[0]);
                });
        test(
                "adjustment invalid price does not execute business",
                () -> {
                    List<String> trace = new ArrayList<>();
                    int[] calls = {0};
                    expect(
                            SecurityException.class,
                            () ->
                                    adjust(
                                            user(Set.of("price:adjust")),
                                            new AdjustRequest("S1", BigDecimal.ZERO),
                                            trace,
                                            () -> calls[0]++));
                    equal(List.of("LOGIN", "PERMISSION", "SCOPE", "INPUT"), trace);
                    equal(0, calls[0]);
                });
        test(
                "adjustment policy executes exactly once after four checks",
                () -> {
                    List<String> trace = new ArrayList<>();
                    int[] calls = {0};
                    adjust(
                            user(Set.of("price:adjust")),
                            new AdjustRequest("S1", new BigDecimal("11")),
                            trace,
                            () -> calls[0]++);
                    equal(List.of("LOGIN", "PERMISSION", "SCOPE", "INPUT"), trace);
                    equal(1, calls[0]);
                });
        test(
                "adjustment requires positive price after cent normalization",
                () -> {
                    List<String> trace = new ArrayList<>();
                    int[] calls = {0};
                    expect(
                            SecurityException.class,
                            () ->
                                    adjust(
                                            user(Set.of("price:adjust")),
                                            new AdjustRequest("S1", new BigDecimal("0.001")),
                                            trace,
                                            () -> calls[0]++));
                    equal(0, calls[0]);
                    trace.clear();
                    adjust(
                            user(Set.of("price:adjust")),
                            new AdjustRequest("S1", new BigDecimal("0.005")),
                            trace,
                            () -> calls[0]++);
                    equal(1, calls[0]);
                });
        test(
                "unauthenticated query stops at login",
                () -> {
                    List<String> trace = new ArrayList<>();
                    int[] calls = {0};
                    expect(
                            SecurityException.class,
                            () -> query(null, new QueryRequest("S1"), trace, () -> calls[0]++));
                    equal(List.of("LOGIN"), trace);
                    equal(0, calls[0]);
                });
        test(
                "checkout recalculates current draft instead of stale 370.00 quote",
                () -> {
                    DraftSale sale = sale("recalculate");
                    amount("370.00", quote(sale));
                    History history = new History(sale);
                    history.execute(new SetDiscount(sale, 90));
                    CheckoutService service = new CheckoutService();
                    List<String> trace = new ArrayList<>();
                    int[] notices = {0};
                    service.hub.subscribe(e -> notices[0]++);
                    SaleResult result =
                            service.submit(cashier(), sale, request("r1", sale), Fault.NONE, trace);
                    equal(33000L, result.payableCents());
                    equal(1, service.store.sales().size());
                    equal(1, notices[0]);
                    equal(List.of("LOGIN", "PERMISSION"), trace);
                    expect(IllegalStateException.class, history::undo);
                    expect(IllegalStateException.class, history::redo);
                    expect(
                            IllegalStateException.class,
                            () -> history.execute(new SetDiscount(sale, 80)));
                    equal(90, sale.discountPercent);
                    equal(1, history.undo.size());
                    equal(0, history.redo.size());
                });
        test(
                "submitted draft rejects even empty-history undo/redo",
                () -> {
                    DraftSale sale = sale("empty-history");
                    History history = new History(sale);
                    new CheckoutService()
                            .submit(
                                    cashier(),
                                    sale,
                                    request("empty", sale),
                                    Fault.NONE,
                                    new ArrayList<>());
                    expect(IllegalStateException.class, history::undo);
                    expect(IllegalStateException.class, history::redo);
                    equal(0, history.undo.size());
                    equal(0, history.redo.size());
                });
        test(
                "checkout denial leaves draft editable with zero result/event/notification",
                () -> {
                    DraftSale sale = discounted("denial");
                    CheckoutService service = new CheckoutService();
                    List<String> trace = new ArrayList<>();
                    int[] notices = {0};
                    service.hub.subscribe(e -> notices[0]++);
                    expect(
                            SecurityException.class,
                            () ->
                                    service.submit(
                                            user(Set.of()),
                                            sale,
                                            request("denied", sale),
                                            Fault.NONE,
                                            trace));
                    equal(List.of("LOGIN", "PERMISSION"), trace);
                    equal(DraftSale.State.DRAFT, sale.state);
                    equal(0, service.store.requests().size());
                    equal(0, service.store.sales().size());
                    equal(0, service.store.events().size());
                    equal(0, notices[0]);
                    new History(sale).execute(new SetDiscount(sale, 80));
                    equal(DraftSale.State.DRAFT, sale.state);
                    equal(80, sale.discountPercent);
                    amount("290.00", quote(sale));
                });
        test(
                "pre-commit failure has no partial result/event and retry can commit",
                () -> {
                    DraftSale sale = discounted("fail");
                    CheckoutService service = new CheckoutService();
                    CheckoutRequest request = request("fail-request", sale);
                    int[] notices = {0};
                    service.hub.subscribe(e -> notices[0]++);
                    expect(
                            IllegalStateException.class,
                            () ->
                                    service.submit(
                                            cashier(),
                                            sale,
                                            request,
                                            Fault.BEFORE_COMMIT,
                                            new ArrayList<>()));
                    equal(DraftSale.State.DRAFT, sale.state);
                    equal(0, service.store.requests().size());
                    equal(0, service.store.sales().size());
                    equal(0, service.store.events().size());
                    equal(0, notices[0]);
                    equal(
                            33000L,
                            service.submit(cashier(), sale, request, Fault.NONE, new ArrayList<>())
                                    .payableCents());
                    equal(1, service.store.sales().size());
                    equal(1, notices[0]);
                });
        test(
                "lost response retries original request without duplicate sale or event",
                () -> {
                    DraftSale sale = discounted("timeout");
                    CheckoutService service = new CheckoutService();
                    CheckoutRequest request = request("timeout-request", sale);
                    int[] notices = {0};
                    service.hub.subscribe(e -> notices[0]++);
                    expect(
                            ResponseLost.class,
                            () ->
                                    service.submit(
                                            cashier(),
                                            sale,
                                            request,
                                            Fault.RESPONSE_LOST,
                                            new ArrayList<>()));
                    equal(DraftSale.State.SUBMITTED, sale.state);
                    equal(1, service.store.sales().size());
                    SaleResult result =
                            service.submit(cashier(), sale, request, Fault.NONE, new ArrayList<>());
                    equal(33000L, result.payableCents());
                    equal(1, service.store.requests().size());
                    equal(1, service.store.sales().size());
                    equal(1, service.store.events().size());
                    equal(1, notices[0]);
                    expect(
                            IllegalArgumentException.class,
                            () ->
                                    service.submit(
                                            cashier(),
                                            sale,
                                            new CheckoutRequest(request.requestId(), sale.id, 80),
                                            Fault.NONE,
                                            new ArrayList<>()));
                    expect(
                            IllegalStateException.class,
                            () ->
                                    service.submit(
                                            cashier(),
                                            sale,
                                            new CheckoutRequest("new-id", sale.id, 90),
                                            Fault.NONE,
                                            new ArrayList<>()));
                });
        test(
                "same request id cannot change user or sale",
                () -> {
                    CheckoutService service = new CheckoutService();
                    DraftSale sale = discounted("bound");
                    service.submit(
                            cashier(),
                            sale,
                            request("bound-request", sale),
                            Fault.NONE,
                            new ArrayList<>());
                    DraftSale otherSale = discounted("other-sale");
                    expect(
                            IllegalArgumentException.class,
                            () ->
                                    service.submit(
                                            cashier(),
                                            otherSale,
                                            request("bound-request", otherSale),
                                            Fault.NONE,
                                            new ArrayList<>()));
                    Principal otherUser =
                            new Principal("u2", true, Set.of("sale:submit"), Set.of("S1"));
                    expect(
                            SecurityException.class,
                            () ->
                                    service.submit(
                                            otherUser,
                                            sale,
                                            request("bound-request", sale),
                                            Fault.NONE,
                                            new ArrayList<>()));
                    equal(1, service.store.sales().size());
                    equal(DraftSale.State.DRAFT, otherSale.state);
                });
        test(
                "stale draft parameters are rejected without saving result",
                () -> {
                    DraftSale sale = discounted("stale");
                    CheckoutService service = new CheckoutService();
                    expect(
                            IllegalStateException.class,
                            () ->
                                    service.submit(
                                            cashier(),
                                            sale,
                                            new CheckoutRequest("stale-request", sale.id, 100),
                                            Fault.NONE,
                                            new ArrayList<>()));
                    equal(0, service.store.sales().size());
                    equal(DraftSale.State.DRAFT, sale.state);
                });
        test(
                "notification failures and callback submissions preserve state and later delivery",
                () -> {
                    CheckoutService service = new CheckoutService();
                    DraftSale sale = discounted("notify");
                    int[] delivered = {0};
                    service.hub.subscribe(
                            e -> {
                                throw new IllegalStateException("display failed");
                            });
                    service.hub.subscribe(e -> delivered[0]++);
                    SaleResult result =
                            service.submit(
                                    cashier(),
                                    sale,
                                    request("notify-request", sale),
                                    Fault.NONE,
                                    new ArrayList<>());
                    equal(33000L, result.payableCents());
                    equal(1, delivered[0]);
                    equal(1, service.hub.failures.size());
                    equal(DraftSale.State.SUBMITTED, sale.state);
                    equal(1, service.store.events().size());

                    CheckoutService callbackService = new CheckoutService();
                    DraftSale first = discounted("callback-first");
                    DraftSale nested = discounted("callback-nested");
                    CheckoutRequest firstRequest = request("callback-first-request", first);
                    CheckoutRequest nestedRequest = request("callback-nested-request", nested);
                    List<String> nestedTrace = new ArrayList<>();
                    List<String> retryTrace = new ArrayList<>();
                    List<String> notices = new ArrayList<>();
                    callbackService.hub.subscribe(
                            e -> {
                                if (e.saleId().equals(first.id))
                                    callbackService.submit(
                                            cashier(),
                                            nested,
                                            nestedRequest,
                                            Fault.NONE,
                                            nestedTrace);
                            });
                    callbackService.hub.subscribe(
                            e -> {
                                if (e.saleId().equals(first.id))
                                    callbackService.submit(
                                            cashier(), first, firstRequest, Fault.NONE, retryTrace);
                            });
                    callbackService.hub.subscribe(e -> notices.add(e.saleId()));
                    SaleResult firstResult =
                            callbackService.submit(
                                    cashier(), first, firstRequest, Fault.NONE, new ArrayList<>());
                    equal(DraftSale.State.SUBMITTED, first.state);
                    equal(DraftSale.State.DRAFT, nested.state);
                    equal(1, callbackService.store.requests().size());
                    equal(1, callbackService.store.sales().size());
                    equal(1, callbackService.store.events().size());
                    equal(2, callbackService.hub.failures.size());
                    equal(List.of(), nestedTrace);
                    equal(List.of(), retryTrace);
                    equal(List.of(first.id), notices);
                    StoreState saved = callbackService.store;
                    equal(
                            firstResult,
                            callbackService.submit(
                                    cashier(), first, firstRequest, Fault.NONE, new ArrayList<>()));
                    equal(saved, callbackService.store);
                    equal(List.of(first.id), notices);
                    SaleResult nestedResult =
                            callbackService.submit(
                                    cashier(),
                                    nested,
                                    nestedRequest,
                                    Fault.NONE,
                                    new ArrayList<>());
                    equal(33000L, nestedResult.payableCents());
                    equal(DraftSale.State.SUBMITTED, nested.state);
                    equal(2, callbackService.store.requests().size());
                    equal(2, callbackService.store.sales().size());
                    equal(2, callbackService.store.events().size());
                    equal(2, callbackService.hub.failures.size());
                    equal(List.of(first.id, nested.id), notices);
                });
        test(
                "duplicate sale event has one receiver effect by event-id deduplication",
                () -> {
                    CheckoutService service = new CheckoutService();
                    DraftSale sale = discounted("duplicate");
                    Set<String> received = new HashSet<>();
                    int[] effects = {0};
                    service.hub.subscribe(
                            e -> {
                                if (received.add(e.eventId())) effects[0]++;
                            });
                    SaleResult result =
                            service.submit(
                                    cashier(),
                                    sale,
                                    request("dup-request", sale),
                                    Fault.NONE,
                                    new ArrayList<>());
                    service.replay(result.eventId());
                    service.replay(result.eventId());
                    equal(1, effects[0]);
                    equal(1, service.store.sales().size());
                });
        test(
                "product change and sale committed use different event types and subscriptions",
                () -> {
                    LocalHub<ProductChange> products = new LocalHub<>();
                    CheckoutService service = new CheckoutService();
                    int[] productNotices = {0};
                    int[] saleNotices = {0};
                    Subscription product = products.subscribe(e -> productNotices[0]++);
                    service.hub.subscribe(e -> saleNotices[0]++);
                    products.publish(new ProductChange(1, "P001", new BigDecimal("11.00")));
                    equal(1, productNotices[0]);
                    equal(0, saleNotices[0]);
                    product.close();
                    products.publish(new ProductChange(2, "P001", new BigDecimal("12.50")));
                    equal(1, productNotices[0]);
                    DraftSale sale = discounted("typed-events");
                    service.submit(
                            cashier(),
                            sale,
                            request("typed-request", sale),
                            Fault.NONE,
                            new ArrayList<>());
                    equal(1, saleNotices[0]);
                    equal(1, productNotices[0]);
                });
        System.out.println("RESULT " + passed + "/" + passed + " checks passed");
    }
}
