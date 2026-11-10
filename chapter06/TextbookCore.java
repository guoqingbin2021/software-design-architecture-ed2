import java.math.*;
import java.util.*;
import java.util.function.Predicate;

/** 计价策略、商品通知、草稿命令历史与调价检查示例。 */
public final class TextbookCore {
    // ---- 代码清单6-1　直接分支实现的计价方案 ----
    static BigDecimal directPrice(BigDecimal amount, String rule) {
        // 先校验并按分规范化，再选择一种互不叠加的规则。
        BigDecimal current = money(amount);
        if ("ORIGINAL".equals(rule)) return current;
        if ("RATE_90".equals(rule)) return money(current.multiply(new BigDecimal("0.9")));
        if ("MINUS_30_OVER_300".equals(rule)) {
            // 未达到满减门槛时保持原金额。
            if (current.compareTo(new BigDecimal("300.00")) < 0) return current;
            return money(current.subtract(new BigDecimal("30.00")));
        }
        throw new IllegalArgumentException("未知优惠规则：" + rule);
    }

    // ---- 代码清单6-2　金额契约、计价策略与环境对象 ----
    // 金额必须先校验非负，再舍入到分。
    static BigDecimal money(BigDecimal value) {
        Objects.requireNonNull(value, "金额不能为空");
        if (value.signum() < 0) throw new IllegalArgumentException("金额不能为负数");
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal fromCents(long cents) {
        if (cents < 0) throw new IllegalArgumentException("负分值");
        return BigDecimal.valueOf(cents, 2);
    }

    // 精确换算为整数分；非整分或溢出时抛出异常。
    static long toLongCents(BigDecimal amount) {
        Objects.requireNonNull(amount);
        if (amount.signum() < 0) throw new IllegalArgumentException("负金额");
        return amount.movePointRight(2).longValueExact();
    }

    // 共同接口只负责计价，不执行扣款和销售提交。
    interface PricingStrategy {
        BigDecimal apply(BigDecimal amount);
    }

    record OriginalPrice() implements PricingStrategy {
        public BigDecimal apply(BigDecimal amount) {
            return money(amount);
        }
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
            if (current.compareTo(threshold) < 0) return current;
            return money(current.subtract(reduction).max(BigDecimal.ZERO));
        }
    }

    // 环境对象通过接口使用由外部选定的策略。
    static final class PriceContext {
        private PricingStrategy strategy;

        PriceContext(PricingStrategy strategy) {
            setStrategy(strategy);
        }

        void setStrategy(PricingStrategy strategy) {
            this.strategy = Objects.requireNonNull(strategy);
        }

        BigDecimal price(BigDecimal original) {
            return money(strategy.apply(money(original)));
        }
    }

    // ---- 代码清单6-3　按顺序执行的组合优惠 ----
    // 按列表顺序叠加，每一步输出作为下一步输入。
    record CombinedDiscount(List<PricingStrategy> steps) implements PricingStrategy {
        CombinedDiscount {
            // 固定步骤及顺序；不复制策略对象本身。
            steps = List.copyOf(steps);
        }

        public BigDecimal apply(BigDecimal amount) {
            BigDecimal current = money(amount);
            // 每一步均以前一步的规范化结果为输入。
            for (PricingStrategy step : steps) {
                current = money(step.apply(current));
            }
            return current;
        }
    }

    // ---- 代码清单6-4　商品变更的订阅与本地通知 ----
    record ProductChange(long version, String productId, BigDecimal price) {}

    @FunctionalInterface
    interface Observer {
        void update(ProductChange change);
    }

    interface Subscription extends AutoCloseable {
        void close();
    }

    static final class Hub {
        private final LinkedHashMap<Long, Observer> observers = new LinkedHashMap<>();
        private final Map<String, ProductChange> catalog = new LinkedHashMap<>();
        private final List<String> failures = new ArrayList<>();
        private long nextId, version;
        private boolean notifying;

        Subscription subscribe(Observer observer) {
            Objects.requireNonNull(observer);
            long id = ++nextId;
            observers.put(id, observer);
            // 返回只解除本次订阅的句柄。
            return () -> observers.remove(id);
        }

        ProductChange change(String productId, BigDecimal price) {
            if (notifying) throw new IllegalStateException("不得递归通知");
            ProductChange event = new ProductChange(version + 1, productId, money(price));
            catalog.put(productId, event);
            version = event.version();
            // 本轮接收者名单固定，后续订阅变化从下一轮生效。
            List<Observer> snapshot = List.copyOf(observers.values());
            notifying = true;
            try {
                for (Observer observer : snapshot) {
                    try {
                        observer.update(event);
                    }
                    // 隔离单个回调故障，继续通知其余观察者。
                    catch (RuntimeException ex) {
                        failures.add("v" + version + ":" + ex.getMessage());
                    }
                }
            } finally {
                notifying = false;
            }
            return event;
        }
    }

    static void localDemo() {
        // 客户端建立同一Java进程中的两个本地接收者。
        Hub hub = new Hub();
        Subscription label = hub.subscribe(e -> refreshLabel(e));
        Subscription cache = hub.subscribe(e -> updateLocalCache(e));
        hub.change("P001", new BigDecimal("12.50"));
        label.close(); // 下一轮仅通知收银缓存。
        hub.change("P001", new BigDecimal("11.00")); // v2仅通知收银缓存。
    }

    // ---- 代码清单6-5　未提交销售草稿的折扣命令与状态检查 ----
    public interface Command {
        // 返回本命令绑定的草稿，供历史管理器核对目标。
        DraftSale target();

        void execute();

        void undo();

        void redo();
    }

    public static final class DraftSale {
        public enum State {
            DRAFT,
            SUBMITTED
        }

        private int discountPercent = 100;
        private State state = State.DRAFT;

        public int discount() {
            return discountPercent;
        }

        public boolean isSubmitted() {
            return state == State.SUBMITTED;
        }

        public void submit() {
            if (state != State.DRAFT) throw new IllegalStateException("草稿已提交，不能重复提交");
            state = State.SUBMITTED;
        }

        // 先校验，再赋值；校验失败时草稿折扣保持原样。
        public void changeDiscount(int expected, int next) {
            if (state != State.DRAFT) throw new IllegalStateException("已提交单据不能按草稿编辑");
            if (next < 0 || next > 100) throw new IllegalArgumentException("折扣须在0至100之间");
            if (discountPercent != expected) throw new IllegalStateException("草稿状态与历史记录不一致");
            discountPercent = next;
        }
    }

    public static final class SetDiscount implements Command {
        public enum State {
            NEW,
            DONE,
            UNDONE
        }

        private final DraftSale sale;
        private final int after;
        private int before;
        private State state = State.NEW;

        public SetDiscount(DraftSale sale, int after) {
            this.sale = Objects.requireNonNull(sale);
            this.after = after;
        }

        public DraftSale target() {
            return sale;
        }

        public void execute() {
            require(State.NEW);
            int original = sale.discount();
            sale.changeDiscount(original, after);
            // 接收者修改成功后，才保存恢复值并更新命令状态。
            before = original;
            state = State.DONE;
        }

        public void undo() {
            require(State.DONE);
            // 撤销要求当前值仍等于本命令执行后的值。
            sale.changeDiscount(after, before);
            state = State.UNDONE;
        }

        public void redo() {
            require(State.UNDONE);
            // 重做使用首次保存的目标值，不重新读取界面参数。
            sale.changeDiscount(before, after);
            state = State.DONE;
        }

        private void require(State expected) {
            if (state != expected) throw new IllegalStateException("命令状态不允许该操作");
        }
    }

    // ---- 代码清单6-6　与草稿绑定的历史管理器 ----
    public static final class History {
        private final DraftSale sale;
        private final Deque<Command> undo = new ArrayDeque<>();
        private final Deque<Command> redo = new ArrayDeque<>();

        public History(DraftSale sale) {
            this.sale = Objects.requireNonNull(sale);
        }

        public void execute(Command command) {
            requireDraft();
            Objects.requireNonNull(command);
            // 历史只能接收作用于绑定草稿的命令。
            if (command.target() != sale) throw new IllegalArgumentException("命令目标不是本历史管理的草稿");
            command.execute();
            // 命令成功返回后才入栈；失败不会清空原重做历史。
            undo.push(command);
            redo.clear();
        }

        public void undo() {
            requireDraft();
            if (undo.isEmpty()) return;
            // 先查看栈顶；撤销成功后才将命令移到另一栈。
            Command command = undo.peek();
            command.undo();
            undo.pop();
            redo.push(command);
        }

        public void redo() {
            requireDraft();
            if (redo.isEmpty()) return;
            // 重做同样在成功后移栈。
            Command command = redo.peek();
            command.redo();
            redo.pop();
            undo.push(command);
        }

        // 即使历史栈为空，提交后的草稿也拒绝历史操作。
        private void requireDraft() {
            if (sale.isSubmitted()) throw new IllegalStateException("已提交单据不能使用草稿历史");
        }
    }

    // ---- 代码清单6-7　验证必要检查的调价请求链 ----
    public enum Stage {
        LOGIN,
        PERMISSION,
        SCOPE,
        INPUT
    }

    public record Subject(boolean authenticated, Set<String> permissions, Set<String> stores) {
        public Subject {
            // 保存不可修改的授权集合；这些数据须由可信服务端提供。
            permissions = Set.copyOf(permissions);
            stores = Set.copyOf(stores);
        }
    }

    public record Request(Subject subject, String storeId, BigDecimal newPrice) {}

    public record Gate(Stage stage, Predicate<Request> accepts, String rejection) {
        public Gate {
            Objects.requireNonNull(stage);
            Objects.requireNonNull(accepts);
            Objects.requireNonNull(rejection);
        }

        public void check(Request request) {
            // 拒绝时抛出异常，后续节点与业务均不执行。
            if (!accepts.test(request)) throw new SecurityException(rejection);
        }
    }

    @FunctionalInterface
    public interface PriceService {
        void adjust(Request request);
    }

    public static final class Chain {
        private static final List<Stage> ORDER =
                List.of(Stage.LOGIN, Stage.PERMISSION, Stage.SCOPE, Stage.INPUT);
        private final List<Gate> gates;

        public Chain(List<Gate> gates) {
            this.gates = List.copyOf(gates);
            // 同时核对阶段数量和顺序，拒绝缺项、重复与错序。
            if (!this.gates.stream().map(Gate::stage).toList().equals(ORDER))
                throw new IllegalArgumentException("须完整且依序装配四项必要检查");
        }

        public void handle(Request request, PriceService target) {
            Objects.requireNonNull(request);
            Objects.requireNonNull(target);
            // 按固定次序检查；判断自身抛出的异常也会中止本次调用。
            for (Gate gate : gates) gate.check(request);
            // 只有全部必要检查成功返回后，才调用一次业务入口。
            target.adjust(request);
        }
    }

    // 本地通知演示回调与状态查询方法。
    static final List<String> localTrace = new ArrayList<>();

    static void refreshLabel(ProductChange e) {
        localTrace.add("label:v" + e.version());
    }

    static void updateLocalCache(ProductChange e) {
        localTrace.add("cache:v" + e.version());
    }

    static List<String> failures(Hub h) {
        return List.copyOf(h.failures);
    }

    static List<Integer> historyState(History h) {
        return List.of(h.sale.discount(), h.undo.size(), h.redo.size());
    }

    static String commandState(SetDiscount c) {
        return c.state.name();
    }

    public static void main(String[] args) {
        localDemo();
        System.out.println(localTrace);
    }
}
