import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.function.LongFunction;

/**
 * 第三章面向对象设计原则的连续案例实现。JDK 17+，无第三方依赖。
 * 编译：javac -encoding UTF-8 --release 17 Chapter3Examples.java
 * 演示：java Chapter3Examples     验证：java Chapter3Examples --test
 * 教学边界：单进程、单线程、内存状态；支付全部为本地模拟。
 */
public final class Chapter3Examples {
    private Chapter3Examples() {}

    static String text(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("标识或文本不能为空");
        return value;
    }

    static long nonnegative(long value) {
        if (value < 0) throw new IllegalArgumentException("金额或余额不能为负");
        return value;
    }

    // 开闭原则。折扣参数复用算法，预览和收银共用计价入口。
    enum Level {
        NORMAL(10000),
        SILVER(9800),
        GOLD(9600),
        PLATINUM(9500),
        ENTERPRISE(9300);
        final int basisPoints;

        Level(int basisPoints) {
            this.basisPoints = basisPoints;
        }
    }

    interface DiscountPolicy {
        long payable(long originalFen);
    }

    record BasisPointDiscount(int basisPoints) implements DiscountPolicy {
        BasisPointDiscount {
            if (basisPoints < 0 || basisPoints > 10000) throw new IllegalArgumentException("折扣越界");
        }

        public long payable(long originalFen) {
            nonnegative(originalFen);
            long q = originalFen / 10000, r = originalFen % 10000;
            return q * basisPoints + (r * basisPoints + 5000) / 10000;
        }
    }

    record BirthdayDiscount(DiscountPolicy base, boolean eligible) implements DiscountPolicy {
        BirthdayDiscount {
            Objects.requireNonNull(base);
        }

        public long payable(long originalFen) {
            long due = base.payable(originalFen);
            return eligible ? new BasisPointDiscount(6000).payable(due) : due;
        }
    }

    // 日期与时区是否满足生日条件，由调用方业务层提前判断。
    static DiscountPolicy discountFor(Level level, boolean birthdayEligible) {
        Objects.requireNonNull(level);
        return new BirthdayDiscount(
                new BasisPointDiscount(level.basisPoints),
                level == Level.PLATINUM && birthdayEligible);
    }

    record Order(String id, String memberId, long eligibleFen, long excludedFen) {
        Order {
            text(id);
            text(memberId);
            nonnegative(eligibleFen);
            nonnegative(excludedFen);
            Math.addExact(eligibleFen, excludedFen);
        }

        long originalFen() {
            return Math.addExact(eligibleFen, excludedFen);
        }
    }

    interface PricingPolicy {
        long payable(Order order);
    }

    record EligiblePricing(DiscountPolicy discount) implements PricingPolicy {
        EligiblePricing {
            Objects.requireNonNull(discount);
        }

        public long payable(Order order) {
            return Math.addExact(discount.payable(order.eligibleFen()), order.excludedFen());
        }
    }

    static final class PriceService {
        private final PricingPolicy pricing;

        PriceService(PricingPolicy pricing) {
            this.pricing = Objects.requireNonNull(pricing);
        }

        private long price(Order order) {
            long due = pricing.payable(Objects.requireNonNull(order));
            if (due < 0 || due > order.originalFen()) throw new IllegalStateException("违反计价范围契约");
            return due;
        }

        long preview(Order order) {
            return price(order);
        }

        long checkoutPrice(Order order) {
            return price(order);
        }
    }

    // 单一职责；查询与消费能力分离。
    static final class MemberProfile {
        final String memberId;
        final String displayName;
        final Level level;
        private String contact;

        MemberProfile(String memberId, String displayName, Level level, String contact) {
            this.memberId = text(memberId);
            this.displayName = text(displayName);
            this.level = Objects.requireNonNull(level);
            this.contact = text(contact);
        }

        void updateContact(String newContact) {
            contact = text(newContact).trim();
        }

        String contact() {
            return contact;
        }
    }

    interface PointQuery {
        long balance();
    }

    interface PointSpend {
        boolean spend(long amount);
    }

    // 正文3.5的能力组合；Account沿用正文3.6契约检查中的短名称。
    interface SpendablePointAccount extends PointQuery, PointSpend {}

    interface Account extends SpendablePointAccount {}

    // 正文3.6中的直接保存余额实现，供PointAccount组合使用。
    static final class MemoryAccount implements Account {
        private long balance;

        MemoryAccount(long initial) {
            balance = nonnegative(initial);
        }

        public long balance() {
            return balance;
        }

        public boolean spend(long amount) {
            if (amount <= 0 || amount > balance) return false;
            balance -= amount;
            return true;
        }

        void credit(long amount) {
            balance = Math.addExact(balance, nonnegative(amount));
        }
    }

    record PaymentSource(String channel, String tradeId) {
        PaymentSource {
            text(channel);
            text(tradeId);
        }
    }

    record PointCredit(String orderId, long amount) {
        PointCredit {
            text(orderId);
            nonnegative(amount);
        }
    }

    static final class PointAccount implements Account {
        final String memberId;
        private final MemoryAccount storage;
        private final Map<PaymentSource, PointCredit> credits = new HashMap<>();

        PointAccount(String memberId, long initial) {
            this.memberId = text(memberId);
            storage = new MemoryAccount(initial);
        }

        String memberId() {
            return memberId;
        }

        public long balance() {
            return storage.balance();
        }

        public boolean spend(long amount) {
            return storage.spend(amount);
        }

        boolean creditOnce(PaymentSource source, String orderId, long amount) {
            Objects.requireNonNull(source);
            PointCredit credit = new PointCredit(orderId, amount);
            PointCredit prior = credits.get(source);
            if (prior != null) {
                if (!prior.equals(credit)) throw new IllegalArgumentException("同一交易的来源订单或积分数量冲突");
                return false;
            }
            storage.credit(amount); // 溢出时先失败，余额与来源记录均不改变。
            credits.put(source, credit); // 即使新增0积分，也记录订单与交易来源。
            return true;
        }

        int creditCount() {
            return credits.size();
        }
    }

    record LedgerEntryDTO(String transactionId, long deltaFen, Instant occurredAt) {}

    record FinanceWalletDTO(long balanceFen, LedgerEntryDTO latest, Instant asOf) {}

    static final class StoredValueAccount {
        final String memberId;
        // 对外仍是一个储值账户，内部可以由一个或多个账本承载余额。
        private List<Long> ledgerBalances;
        private LedgerEntryDTO latest;
        private final Set<String> transactions = new HashSet<>();

        StoredValueAccount(String memberId, long initialFen) {
            this.memberId = text(memberId);
            ledgerBalances = List.of(nonnegative(initialFen));
        }

        String memberId() {
            return memberId;
        }

        long balanceFen() {
            long total = 0;
            for (long value : ledgerBalances) total = Math.addExact(total, value);
            return total;
        }

        void reorganizeLedgers(long firstLedgerFen) {
            long total = balanceFen();
            if (firstLedgerFen < 0 || firstLedgerFen > total)
                throw new IllegalArgumentException("账本划分金额越界");
            ledgerBalances = List.of(firstLedgerFen, total - firstLedgerFen);
        }

        boolean debit(String transactionId, long amountFen, Instant time) {
            text(transactionId);
            Objects.requireNonNull(time);
            if (transactions.contains(transactionId))
                throw new IllegalArgumentException("储值流水编号重复");
            if (amountFen <= 0 || amountFen > balanceFen()) return false;
            LedgerEntryDTO entry = new LedgerEntryDTO(transactionId, -amountFen, time);
            long remaining = amountFen;
            List<Long> updated = new ArrayList<>();
            for (long balance : ledgerBalances) {
                long debit = Math.min(balance, remaining);
                updated.add(balance - debit);
                remaining -= debit;
            }
            ledgerBalances = List.copyOf(updated);
            transactions.add(transactionId);
            latest = entry;
            return true;
        }

        FinanceWalletDTO snapshot(Instant time) {
            return new FinanceWalletDTO(balanceFen(), latest, Objects.requireNonNull(time));
        }
    }

    // 依赖倒置。业务只依赖付款与查询能力；测试对象模拟本地通道。
    enum PaymentStatus {
        SUCCESS,
        REJECTED,
        PENDING
    }

    record PaymentResult(
            String orderId,
            String attemptId,
            String channel,
            long amountFen,
            PaymentStatus status,
            String tradeId) {}

    interface PaymentStart {
        PaymentResult pay(String orderId, String attemptId, long amountFen);
    }

    // 代码3-4的本地测试对象：只返回配置的状态，不连接支付平台。
    record PaymentStub(PaymentStatus status) implements PaymentStart {
        public PaymentResult pay(String orderId, String attemptId, long amountFen) {
            String trade = status == PaymentStatus.SUCCESS ? "local:" + attemptId : null;
            return new PaymentResult(orderId, attemptId, "local", amountFen, status, trade);
        }
    }

    interface PaymentQuery {
        PaymentResult query(String attemptId);
    }

    enum GatewayMode {
        IMMEDIATE,
        APPROVAL,
        LOST_RESPONSE
    }

    // 两种支付实现通过组合复用本地模拟状态，不把供应商细节放进结算服务。
    static final class LocalGateway {
        final String channel;
        private final GatewayMode mode;
        private final Map<String, PaymentResult> attempts = new HashMap<>();
        int payCalls, queryCalls, successfulCharges;

        LocalGateway(String channel, GatewayMode mode) {
            this.channel = text(channel);
            this.mode = Objects.requireNonNull(mode);
        }

        public PaymentResult pay(String orderId, String attemptId, long amountFen) {
            text(orderId);
            text(attemptId);
            nonnegative(amountFen);
            payCalls++;
            PaymentResult prior = attempts.get(attemptId);
            if (prior != null) {
                if (!prior.orderId().equals(orderId) || prior.amountFen() != amountFen)
                    throw new IllegalArgumentException("付款标识不能用于不同订单或金额");
                return prior;
            }
            PaymentStatus status =
                    switch (mode) {
                        case IMMEDIATE, LOST_RESPONSE -> PaymentStatus.SUCCESS;
                        case APPROVAL -> PaymentStatus.PENDING;
                    };
            PaymentResult stored = result(orderId, attemptId, amountFen, status);
            attempts.put(attemptId, stored);
            if (status == PaymentStatus.SUCCESS) successfulCharges++;
            // 模拟已扣款但响应丢失：调用者只能认为结果未知，之后查询原付款。
            return mode == GatewayMode.LOST_RESPONSE
                    ? result(orderId, attemptId, amountFen, PaymentStatus.PENDING)
                    : stored;
        }

        public PaymentResult query(String attemptId) {
            queryCalls++;
            PaymentResult result = attempts.get(attemptId);
            if (result == null) throw new IllegalArgumentException("未知付款标识");
            return result;
        }

        void approve(String attemptId, boolean approved) {
            PaymentResult prior = attempts.get(attemptId);
            if (prior == null || prior.status() != PaymentStatus.PENDING)
                throw new IllegalStateException("只有待审批付款可审批");
            PaymentStatus status = approved ? PaymentStatus.SUCCESS : PaymentStatus.REJECTED;
            attempts.put(attemptId, result(prior.orderId(), attemptId, prior.amountFen(), status));
            if (approved) successfulCharges++;
        }

        private PaymentResult result(
                String orderId, String attemptId, long amountFen, PaymentStatus status) {
            return new PaymentResult(
                    orderId,
                    attemptId,
                    channel,
                    amountFen,
                    status,
                    status == PaymentStatus.SUCCESS ? channel + ":" + attemptId : null);
        }
    }

    // 仅供装配和观察本地测试记录；CheckoutService只依赖PaymentStart/PaymentQuery。
    interface LocalPayment extends PaymentStart, PaymentQuery {
        String channel();

        int payCalls();

        int queryCalls();

        int successfulCharges();
    }

    static final class ImmediatePayment implements LocalPayment {
        private final LocalGateway gateway;

        ImmediatePayment(String channel) {
            this(channel, false);
        }

        ImmediatePayment(String channel, boolean loseFirstResponse) {
            gateway =
                    new LocalGateway(
                            channel,
                            loseFirstResponse ? GatewayMode.LOST_RESPONSE : GatewayMode.IMMEDIATE);
        }

        public PaymentResult pay(String orderId, String attemptId, long amountFen) {
            return gateway.pay(orderId, attemptId, amountFen);
        }

        public PaymentResult query(String attemptId) {
            return gateway.query(attemptId);
        }

        public String channel() {
            return gateway.channel;
        }

        public int payCalls() {
            return gateway.payCalls;
        }

        public int queryCalls() {
            return gateway.queryCalls;
        }

        public int successfulCharges() {
            return gateway.successfulCharges;
        }
    }

    static final class EnterpriseApprovalPayment implements LocalPayment {
        private final LocalGateway gateway;

        EnterpriseApprovalPayment(String channel) {
            gateway = new LocalGateway(channel, GatewayMode.APPROVAL);
        }

        public PaymentResult pay(String orderId, String attemptId, long amountFen) {
            return gateway.pay(orderId, attemptId, amountFen);
        }

        public PaymentResult query(String attemptId) {
            return gateway.query(attemptId);
        }

        void approve(String attemptId, boolean approved) {
            gateway.approve(attemptId, approved);
        }

        public String channel() {
            return gateway.channel;
        }

        public int payCalls() {
            return gateway.payCalls;
        }

        public int queryCalls() {
            return gateway.queryCalls;
        }

        public int successfulCharges() {
            return gateway.successfulCharges;
        }
    }

    // 合成复用。更换积分策略不改计价与支付算法。
    interface PointPolicy {
        long earned(long paidFen);
    }

    static final class PerYuanPointPolicy implements PointPolicy {
        public long earned(long paidFen) {
            return nonnegative(paidFen) / 100;
        }
    }

    static final class NoPointPolicy implements PointPolicy {
        public long earned(long paidFen) {
            nonnegative(paidFen);
            return 0;
        }
    }

    static final class DoublePointPolicy implements PointPolicy {
        private final PointPolicy base;

        DoublePointPolicy(PointPolicy base) {
            this.base = Objects.requireNonNull(base);
        }

        public long earned(long paidFen) {
            return Math.multiplyExact(base.earned(paidFen), 2);
        }
    }

    record SettlementView(PaymentStatus paymentStatus, boolean completed, long paidFen) {}

    static final class Attempt {
        final Order order;
        final String id;
        final long dueFen;
        final PointAccount account;
        PaymentStatus status = PaymentStatus.PENDING;
        boolean completed;
        PaymentResult confirmedPayment;
        Long earnedPoints;

        Attempt(Order order, String id, long dueFen, PointAccount account) {
            this.order = order;
            this.id = id;
            this.dueFen = dueFen;
            this.account = account;
        }

        SettlementView view() {
            return new SettlementView(
                    status, completed, confirmedPayment == null ? 0 : confirmedPayment.amountFen());
        }
    }

    static final class CheckoutService {
        private final PriceService prices;
        private final PaymentStart starter;
        private final PaymentQuery query;
        private final String expectedChannel;
        private final PointPolicy pointPolicy;
        private final Map<String, Attempt> attempts = new HashMap<>();
        private final Map<String, String> orderAttempts = new HashMap<>();
        // 交易归属在确认付款时绑定，早于本地积分处理；本地失败也不解除绑定。
        private final Map<PaymentSource, String> paymentOwners = new HashMap<>();

        CheckoutService(
                PriceService prices,
                PaymentStart starter,
                PaymentQuery query,
                String channel,
                PointPolicy pointPolicy) {
            this.prices = Objects.requireNonNull(prices);
            this.starter = Objects.requireNonNull(starter);
            this.query = Objects.requireNonNull(query);
            expectedChannel = text(channel);
            this.pointPolicy = Objects.requireNonNull(pointPolicy);
        }

        SettlementView begin(Order order, String attemptId, PointAccount account) {
            Objects.requireNonNull(order);
            Objects.requireNonNull(account);
            text(attemptId);
            if (!order.memberId().equals(account.memberId))
                throw new IllegalArgumentException("会员账户不匹配");
            Attempt prior = attempts.get(attemptId);
            if (prior != null) {
                if (!prior.order.equals(order) || prior.account != account)
                    throw new IllegalArgumentException("同一付款标识的请求内容冲突");
                return prior.view(); // 返回保存状态；绝不再次调用付款接口。
            }
            if (orderAttempts.containsKey(order.id()))
                throw new IllegalStateException("此教学实现每个订单只创建一次付款尝试");
            Attempt current = new Attempt(order, attemptId, prices.checkoutPrice(order), account);
            attempts.put(attemptId, current);
            orderAttempts.put(order.id(), attemptId);
            PaymentResult result = starter.pay(order.id(), attemptId, current.dueFen);
            return acceptExpected(current, result);
        }

        SettlementView confirm(String attemptId) {
            Attempt expected = requireAttempt(attemptId);
            return acceptExpected(expected, query.query(attemptId));
        }

        private Attempt requireAttempt(String attemptId) {
            Attempt attempt = attempts.get(text(attemptId));
            if (attempt == null) throw new IllegalArgumentException("未知付款标识");
            return attempt;
        }

        private void verifyResult(Attempt expected, PaymentResult result) {
            Objects.requireNonNull(result);
            Objects.requireNonNull(result.status());
            if (!expected.id.equals(result.attemptId())
                    || !expected.order.id().equals(result.orderId())
                    || !expectedChannel.equals(result.channel())
                    || expected.dueFen != result.amountFen())
                throw new IllegalArgumentException("付款响应与本次请求的订单、标识、通道或金额不匹配");
            if (result.status() == PaymentStatus.SUCCESS) text(result.tradeId());
        }

        private SettlementView acceptExpected(Attempt expected, PaymentResult result) {
            // 主动付款、主动查询都按该次请求核对，不能仅凭响应标识找到另一笔尝试。
            verifyResult(expected, result);
            return applyResult(expected, result);
        }

        // 只接收可信通道适配器给出的结果。真实回调须先验签、验证来源，再到此入口。
        SettlementView acceptTrusted(PaymentResult result) {
            Objects.requireNonNull(result);
            Attempt attempt = requireAttempt(result.attemptId());
            return acceptExpected(attempt, result);
        }

        private SettlementView applyResult(Attempt attempt, PaymentResult result) {
            if (attempt.confirmedPayment != null) {
                if (!attempt.confirmedPayment.equals(result))
                    throw new IllegalStateException("已确认付款不能回退或更换交易号");
                return settleLocal(attempt);
            }
            if (attempt.status == PaymentStatus.REJECTED
                    && result.status() != PaymentStatus.REJECTED)
                throw new IllegalStateException("明确拒绝的尝试不能变成成功");
            if (result.status() == PaymentStatus.SUCCESS) {
                PaymentSource source = new PaymentSource(result.channel(), result.tradeId());
                String owner = paymentOwners.get(source);
                if (owner != null && !owner.equals(attempt.id))
                    throw new IllegalArgumentException("一个通道交易不能结算多个订单");
                paymentOwners.put(source, attempt.id);
                attempt.confirmedPayment = result; // 不可变的交易号、金额与订单事实。
                attempt.status = PaymentStatus.SUCCESS;
                return settleLocal(attempt);
            } else {
                attempt.status = result.status();
            }
            return attempt.view();
        }

        private SettlementView settleLocal(Attempt attempt) {
            if (attempt.completed) return attempt.view();
            PaymentResult paid = Objects.requireNonNull(attempt.confirmedPayment);
            if (attempt.earnedPoints == null)
                attempt.earnedPoints = nonnegative(pointPolicy.earned(paid.amountFen()));
            attempt.account.creditOnce(
                    new PaymentSource(paid.channel(), paid.tradeId()),
                    paid.orderId(),
                    attempt.earnedPoints);
            attempt.completed = true; // 只有本地入账完成后，才完成业务结算。
            return attempt.view();
        }

        SettlementView resumeLocal(String attemptId) {
            Attempt attempt = requireAttempt(attemptId);
            if (attempt.confirmedPayment == null)
                throw new IllegalStateException("尚未确认付款，不能恢复本地结算");
            return settleLocal(attempt); // 只恢复本地处理，不再次付款或查询通道。
        }

        SettlementView state(String attemptId) {
            return requireAttempt(attemptId).view();
        }
    }

    // 示例终端持有所需的接口；不以接口隔离代替权限检查。
    static final class MobilePoints {
        private final PointQuery points;

        MobilePoints(PointQuery points) {
            this.points = Objects.requireNonNull(points);
        }

        long display() {
            return points.balance();
        }
        // points.spend(1); // 不可编译：PointQuery没有消费方法。
    }

    static final class CashierPoints {
        private final Account points;

        CashierPoints(Account points) {
            this.points = Objects.requireNonNull(points);
        }

        boolean spend(long amount) {
            return points.spend(amount);
        }
    }

    // 同一契约检查两种存储实现，并识别违反前置条件的反例。
    static final class LedgerAccount implements Account {
        private final long initial;
        private final List<Long> deltas = new ArrayList<>();

        LedgerAccount(long initial) {
            this.initial = nonnegative(initial);
        }

        public long balance() {
            long total = initial;
            for (long delta : deltas) total = Math.addExact(total, delta);
            return total;
        }

        public boolean spend(long amount) {
            if (amount <= 0 || amount > balance()) return false;
            deltas.add(-amount);
            return true;
        }
    }

    static final class HundredOnlyAccount implements Account {
        private final Account delegate;

        HundredOnlyAccount(long initial) {
            delegate = new MemoryAccount(initial);
        }

        public long balance() {
            return delegate.balance();
        }

        public boolean spend(long amount) {
            return amount % 100 == 0 && delegate.spend(amount); // 故意保留的错误实现。
        }
    }

    // 迪米特法则。按用途返回不可变的值快照，服务先授权再取数。
    enum Role {
        MEMBER,
        FINANCE
    }

    record AuthContext(Role role, Set<String> memberScope) {
        AuthContext {
            Objects.requireNonNull(role);
            memberScope = Set.copyOf(memberScope);
        }
    }

    record MemberSummaryDTO(
            String displayName, String levelName, long availablePoints, Instant asOf) {}

    static final class MemberOverviewService {
        private final MemberProfile profile;
        private final PointAccount points;
        private final StoredValueAccount stored;

        MemberOverviewService(
                MemberProfile profile, PointAccount points, StoredValueAccount stored) {
            this.profile = Objects.requireNonNull(profile);
            this.points = Objects.requireNonNull(points);
            this.stored = Objects.requireNonNull(stored);
            if (!profile.memberId.equals(points.memberId)
                    || !profile.memberId.equals(stored.memberId))
                throw new IllegalArgumentException("资料与账户必须属于同一会员");
        }

        private void authorize(AuthContext context, boolean finance) {
            Objects.requireNonNull(context);
            String targetMemberId = finance ? stored.memberId() : points.memberId();
            if (!context.memberScope().contains(targetMemberId)
                    || (finance && context.role() != Role.FINANCE))
                throw new SecurityException("无权查询");
        }

        MemberSummaryDTO summary(AuthContext context, Instant time) {
            authorize(context, false);
            return new MemberSummaryDTO(
                    profile.displayName,
                    profile.level.name(),
                    points.balance(),
                    Objects.requireNonNull(time));
        }

        FinanceWalletDTO finance(AuthContext context, Instant time) {
            authorize(context, true);
            return stored.snapshot(time);
        }
    }

    static PriceService prices(Level level, boolean birthday) {
        return new PriceService(new EligiblePricing(discountFor(level, birthday)));
    }

    static CheckoutService checkout(PriceService prices, LocalPayment payment, PointPolicy points) {
        return new CheckoutService(prices, payment, payment, payment.channel(), points);
    }

    static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    // 自包含验证：检查失败就抛AssertionError、进程返回非0；不依赖java -ea。
    private static int checks;

    static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }

    static void eq(long actual, long expected, String label) {
        check(actual == expected, label + ": expected=" + expected + ", actual=" + actual);
    }

    static void raises(Class<? extends Throwable> type, Runnable action, String label) {
        checks++;
        try {
            action.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) return;
            throw new AssertionError(label + ": unexpected exception", error);
        }
        throw new AssertionError(label + ": expected " + type.getSimpleName());
    }

    static void contract(LongFunction<Account> factory) {
        long[][] cases = {
            {150, 101, 1, 49}, {80, 50, 1, 30}, {20, 30, 0, 20}, {100, 0, 0, 100}, {100, -1, 0, 100}
        };
        for (long[] c : cases) {
            Account a = factory.apply(c[0]);
            check(a.spend(c[1]) == (c[2] == 1), "LSP spend return for " + c[1]);
            eq(a.balance(), c[3], "LSP balance");
        }
        Account a = factory.apply(100);
        eq(a.balance(), 100, "query 1");
        eq(a.balance(), 100, "query 2");
    }

    static void testPricing() {
        long[] expected = {10000, 9800, 9600, 9500, 9300};
        Order order = new Order("PRICE", "M001", 10000, 0);
        for (Level level : Level.values()) {
            PriceService p = prices(level, false);
            eq(p.preview(order), expected[level.ordinal()], "preview " + level);
            eq(p.checkoutPrice(order), expected[level.ordinal()], "checkout " + level);
        }
        eq(prices(Level.PLATINUM, true).preview(order), 5700, "birthday platinum");
        eq(prices(Level.ENTERPRISE, true).preview(order), 9300, "enterprise no birthday");
        eq(discountFor(Level.PLATINUM, true).payable(15), 8, "sequential rounding");
        eq(new BasisPointDiscount(5700).payable(15), 9, "single rounding differs");
        for (long value : new long[] {0, 1, 15, Long.MAX_VALUE}) {
            long expectedBig =
                    BigInteger.valueOf(value)
                            .multiply(BigInteger.valueOf(9300))
                            .add(BigInteger.valueOf(5000))
                            .divide(BigInteger.valueOf(10000))
                            .longValueExact();
            eq(new BasisPointDiscount(9300).payable(value), expectedBig, "overflow-safe rounding");
        }
        raises(IllegalArgumentException.class, () -> new BasisPointDiscount(-1), "negative rate");
        raises(IllegalArgumentException.class, () -> new BasisPointDiscount(10001), "rate above 1");
        raises(
                IllegalArgumentException.class,
                () -> new BasisPointDiscount(9300).payable(-1),
                "negative price");
        raises(IllegalArgumentException.class, () -> new Order("x", "m", -1, 0), "negative order");
        raises(
                ArithmeticException.class,
                () -> new Order("x", "m", Long.MAX_VALUE, 1),
                "sum overflow");
    }

    static void testResponsibilities() {
        MemberProfile profile = new MemberProfile("M001", "会员", Level.GOLD, "old");
        PointAccount points = new PointAccount("M001", 100);
        StoredValueAccount stored = new StoredValueAccount("M001", 5000);
        profile.updateContact("new");
        check(profile.contact().equals("new"), "contact changed");
        eq(points.balance(), 100, "contact leaves points");
        eq(stored.balanceFen(), 5000, "contact leaves stored");
        raises(IllegalArgumentException.class, () -> profile.updateContact(" "), "invalid contact");
        check(profile.contact().equals("new"), "failed contact unchanged");
        eq(points.balance(), 100, "invalid contact leaves points");
        eq(stored.balanceFen(), 5000, "invalid contact leaves stored");
        PointAccount spendOnly = new PointAccount("M001", 100);
        StoredValueAccount spendStored = new StoredValueAccount("M001", 5000);
        check(spendOnly.spend(30), "spend 30");
        eq(spendOnly.balance(), 70, "points 70");
        eq(spendStored.balanceFen(), 5000, "point spending leaves stored balance");
        StoredValueAccount debitOnly = new StoredValueAccount("M001", 5000);
        PointAccount debitPoints = new PointAccount("M001", 100);
        check(debitOnly.debit("D1", 1200, T0), "debit 1200");
        eq(debitOnly.balanceFen(), 3800, "stored 3800");
        eq(debitPoints.balance(), 100, "stored debit leaves points");
        check(!debitOnly.debit("D2", 4000, T0), "insufficient stored");
        eq(debitOnly.balanceFen(), 3800, "failure unchanged");
        raises(
                IllegalArgumentException.class,
                () -> debitOnly.debit("D1", 1, T0),
                "duplicate debit id");
        PaymentSource source = new PaymentSource("points-test", "T1");
        PointAccount credit = new PointAccount("M001", 100);
        check(credit.creditOnce(source, "ORDER-1", 50), "first credit records source order");
        check(!credit.creditOnce(source, "ORDER-1", 50), "same source is idempotent");
        raises(
                IllegalArgumentException.class,
                () -> credit.creditOnce(source, "ORDER-2", 50),
                "credit source order conflict");
        raises(
                IllegalArgumentException.class,
                () -> credit.creditOnce(source, "ORDER-1", 51),
                "credit amount conflict");
        eq(credit.balance(), 150, "conflicting credits leave balance");
    }

    static void testPaymentStates() {
        PointAccount account = new PointAccount("M002", 0);
        Order order = new Order("APPROVE", "M002", 10000, 0);
        var gateway = new EnterpriseApprovalPayment("enterprise");
        CheckoutService service =
                checkout(prices(Level.ENTERPRISE, false), gateway, new NoPointPolicy());
        SettlementView pending = service.begin(order, "A1", account);
        check(
                pending.paymentStatus() == PaymentStatus.PENDING && !pending.completed(),
                "approval pending");
        eq(account.balance(), 0, "pending no points");
        service.begin(order, "A1", account);
        eq(gateway.payCalls(), 1, "repeat begin no pay");
        gateway.approve("A1", true);
        check(service.confirm("A1").completed(), "approval success settles");
        eq(account.balance(), 0, "enterprise zero points");
        eq(account.creditCount(), 1, "record zero award fact");
        service.confirm("A1");
        eq(account.creditCount(), 1, "duplicate zero award ignored");
        eq(gateway.successfulCharges(), 1, "one charge");
        raises(
                IllegalArgumentException.class,
                () -> service.begin(new Order("OTHER", "M002", 10000, 0), "A1", account),
                "attempt conflict");
        raises(
                IllegalStateException.class,
                () -> service.begin(order, "A2", account),
                "no second attempt");
        var reject = new EnterpriseApprovalPayment("reject");
        PointAccount rejectedAccount = new PointAccount("M001", 100);
        CheckoutService rejected =
                checkout(prices(Level.GOLD, false), reject, new PerYuanPointPolicy());
        rejected.begin(new Order("REJECT", "M001", 10000, 0), "R1", rejectedAccount);
        reject.approve("R1", false);
        SettlementView rejection = rejected.confirm("R1");
        check(
                rejection.paymentStatus() == PaymentStatus.REJECTED && !rejection.completed(),
                "rejected not complete");
        eq(rejectedAccount.balance(), 100, "reject no award");
        eq(reject.successfulCharges(), 0, "reject no charge");
        var lost = new ImmediatePayment("lost", true);
        PointAccount recoveredAccount = new PointAccount("M001", 100);
        CheckoutService recovered =
                checkout(prices(Level.GOLD, false), lost, new PerYuanPointPolicy());
        Order recoveredOrder = new Order("LOST", "M001", 10000, 0);
        check(
                recovered.begin(recoveredOrder, "L1", recoveredAccount).paymentStatus()
                        == PaymentStatus.PENDING,
                "lost pending");
        eq(recoveredAccount.balance(), 100, "unknown no award");
        recovered.begin(recoveredOrder, "L1", recoveredAccount);
        eq(lost.payCalls(), 1, "lost no second pay");
        PaymentResult result = lost.query("L1");
        for (PaymentResult bad :
                List.of(
                        new PaymentResult("WRONG", "L1", "lost", 9600, PaymentStatus.SUCCESS, "t"),
                        new PaymentResult("LOST", "L1", "other", 9600, PaymentStatus.SUCCESS, "t"),
                        new PaymentResult("LOST", "L1", "lost", 9601, PaymentStatus.SUCCESS, "t"),
                        new PaymentResult(
                                "LOST", "L1", "lost", 9600, PaymentStatus.SUCCESS, null))) {
            raises(
                    IllegalArgumentException.class,
                    () -> recovered.acceptTrusted(bad),
                    "invalid result rejected");
        }
        eq(recoveredAccount.balance(), 100, "invalid results no award");
        check(recovered.confirm("L1").completed(), "query recovers success");
        recovered.acceptTrusted(result);
        recovered.confirm("L1");
        eq(recoveredAccount.balance(), 196, "gold credited once");
        eq(recoveredAccount.creditCount(), 1, "one credit fact");
        eq(lost.payCalls(), 1, "confirm no new pay");
        eq(lost.successfulCharges(), 1, "one recovered charge");
        eq(recovered.state("L1").paidFen(), 9600, "historical paid amount");
        raises(
                IllegalArgumentException.class,
                () -> lost.pay("DIFFERENT", "L1", 9600),
                "gateway idempotency order");
        raises(
                IllegalArgumentException.class,
                () -> lost.pay("LOST", "L1", 9601),
                "gateway idempotency amount");
        testPaymentResponseCorrelation();
        testLocalSettlementRecovery();
        testCreditOverflowRecovery();
        testResponseExceptionRecovery();
    }

    // 正文3.8.3：主动请求的响应必须与原请求绑定，其他已存在付款也不能串入。
    static void testPaymentResponseCorrelation() {
        var payment = new EnterpriseApprovalPayment("correlation");
        boolean[] wrongStart = {false};
        PaymentStart starter =
                (order, id, amount) ->
                        wrongStart[0] ? payment.query("B") : payment.pay(order, id, amount);
        PaymentQuery wrongQuery = ignored -> payment.query("B");
        CheckoutService service =
                new CheckoutService(
                        prices(Level.GOLD, false),
                        starter,
                        wrongQuery,
                        payment.channel(),
                        new PerYuanPointPolicy());
        PointAccount account = new PointAccount("M001", 100);
        service.begin(new Order("ORDER-B", "M001", 10000, 0), "B", account);
        service.begin(new Order("ORDER-A", "M001", 10000, 0), "A", account);
        payment.approve("B", true);
        raises(
                IllegalArgumentException.class,
                () -> service.confirm("A"),
                "query cannot return another attempt");
        check(
                !service.state("A").completed() && !service.state("B").completed(),
                "mismatched query changes no order");
        eq(account.balance(), 100, "mismatched query gives no points");
        wrongStart[0] = true;
        raises(
                IllegalArgumentException.class,
                () -> service.begin(new Order("ORDER-C", "M001", 10000, 0), "C", account),
                "pay response cannot belong to another attempt");
        check(
                service.state("C").paymentStatus() == PaymentStatus.PENDING
                        && !service.state("B").completed(),
                "mismatched pay changes no settlement");
        eq(account.creditCount(), 0, "mismatched response records no credit");
        int queries = payment.queryCalls();
        raises(
                IllegalArgumentException.class,
                () -> service.confirm("UNKNOWN"),
                "unknown query rejected before channel call");
        eq(payment.queryCalls(), queries, "unknown attempt does not query channel");
        check(
                service.acceptTrusted(payment.query("B")).completed(),
                "matching trusted B result can settle B");
        check(!service.state("A").completed(), "A remains pending after B settles");
        eq(account.balance(), 196, "only B earns points");
    }

    // 正文3.8.3：确认付款与本地结算分开保存，本地失败后沿用原交易恢复。
    static void testLocalSettlementRecovery() {
        var payment = new EnterpriseApprovalPayment("recovery");
        int[] calculations = {0};
        PointPolicy failsOnce =
                paid -> {
                    if (calculations[0]++ == 0) throw new IllegalStateException("模拟本地积分计算失败");
                    return paid / 100;
                };
        CheckoutService service = checkout(prices(Level.GOLD, false), payment, failsOnce);
        PointAccount account = new PointAccount("M001", 100);
        service.begin(new Order("RECOVER-A", "M001", 10000, 0), "A", account);
        service.begin(new Order("RECOVER-B", "M001", 10000, 0), "B", account);
        raises(
                IllegalStateException.class,
                () -> service.resumeLocal("B"),
                "pending payment cannot settle locally");
        payment.approve("A", true);
        raises(
                IllegalStateException.class,
                () -> service.confirm("A"),
                "local processing failure surfaced");
        SettlementView unfinished = service.state("A");
        check(
                unfinished.paymentStatus() == PaymentStatus.SUCCESS && !unfinished.completed(),
                "payment success retained before completion");
        eq(unfinished.paidFen(), 9600, "confirmed amount retained on local failure");
        eq(account.balance(), 100, "failed local calculation leaves points");
        PaymentResult original = payment.query("A");
        raises(
                IllegalStateException.class,
                () ->
                        service.acceptTrusted(
                                new PaymentResult(
                                        "RECOVER-A",
                                        "A",
                                        payment.channel(),
                                        9600,
                                        PaymentStatus.SUCCESS,
                                        "OTHER-TRADE")),
                "confirmed trade cannot be replaced after local failure");
        raises(
                IllegalArgumentException.class,
                () ->
                        service.acceptTrusted(
                                new PaymentResult(
                                        "RECOVER-B",
                                        "B",
                                        payment.channel(),
                                        9600,
                                        PaymentStatus.SUCCESS,
                                        original.tradeId())),
                "unfinished payment source cannot settle another order");
        raises(
                IllegalStateException.class,
                () ->
                        service.acceptTrusted(
                                new PaymentResult(
                                        "RECOVER-A",
                                        "A",
                                        payment.channel(),
                                        9600,
                                        PaymentStatus.PENDING,
                                        null)),
                "confirmed payment cannot revert to pending");
        check(
                service.state("B").paymentStatus() == PaymentStatus.PENDING,
                "conflicting trade leaves other attempt pending");
        int queries = payment.queryCalls(), pays = payment.payCalls();
        check(service.resumeLocal("A").completed(), "resume original local settlement");
        eq(payment.queryCalls(), queries, "local recovery does not query channel");
        eq(payment.payCalls(), pays, "local recovery does not initiate payment");
        eq(account.balance(), 196, "recovery awards once");
        service.resumeLocal("A");
        service.acceptTrusted(original);
        service.confirm("A");
        eq(account.balance(), 196, "repeated recovery and notification give no extra points");
        eq(account.creditCount(), 1, "one source record after recovery");
        eq(calculations[0], 2, "completed settlement does not recalculate rewards");
        eq(payment.successfulCharges(), 1, "recovery never charges again");
    }

    static void testCreditOverflowRecovery() {
        var payment = new ImmediatePayment("credit-overflow");
        int[] calculations = {0};
        PointPolicy policy =
                paid -> {
                    calculations[0]++;
                    return paid / 100;
                };
        CheckoutService service = checkout(prices(Level.GOLD, false), payment, policy);
        PointAccount account = new PointAccount("M001", Long.MAX_VALUE);
        raises(
                ArithmeticException.class,
                () -> service.begin(new Order("OVERFLOW", "M001", 10000, 0), "O1", account),
                "local account overflow does not erase payment");
        eq(service.state("O1").paidFen(), 9600, "overflow retains actual payment");
        check(!service.state("O1").completed(), "overflow leaves local settlement unfinished");
        eq(account.balance(), Long.MAX_VALUE, "overflow leaves balance");
        eq(account.creditCount(), 0, "overflow records no completed credit");
        check(account.spend(96), "free capacity for account recovery");
        check(service.resumeLocal("O1").completed(), "resume after account capacity correction");
        eq(account.balance(), Long.MAX_VALUE, "recovered credit applied once");
        eq(calculations[0], 1, "earned amount retained across account failure");
        eq(payment.payCalls(), 1, "overflow recovery does not repay");
    }

    static void testResponseExceptionRecovery() {
        var payment = new ImmediatePayment("response-exception");
        PaymentStart throwingStart =
                (order, id, amount) -> {
                    payment.pay(order, id, amount);
                    throw new IllegalStateException("模拟付款响应中断");
                };
        CheckoutService service =
                new CheckoutService(
                        prices(Level.GOLD, false),
                        throwingStart,
                        payment,
                        payment.channel(),
                        new PerYuanPointPolicy());
        PointAccount account = new PointAccount("M001", 100);
        Order order = new Order("RESPONSE", "M001", 10000, 0);
        raises(
                IllegalStateException.class,
                () -> service.begin(order, "E1", account),
                "unknown response exception surfaced");
        check(
                service.state("E1").paymentStatus() == PaymentStatus.PENDING
                        && !service.state("E1").completed(),
                "response exception keeps original attempt pending");
        eq(account.balance(), 100, "unknown response gives no points");
        service.begin(order, "E1", account);
        eq(payment.payCalls(), 1, "repeat after response exception does not repay");
        check(service.confirm("E1").completed(), "query original payment after response exception");
        eq(account.balance(), 196, "response exception recovery earns once");
        eq(payment.successfulCharges(), 1, "response exception caused one charge");
    }

    static void testComposition() {
        long[] expectedPoints = {96, 0, 192};
        PointPolicy[] policies = {
            new PerYuanPointPolicy(),
            new NoPointPolicy(),
            new DoublePointPolicy(new PerYuanPointPolicy())
        };
        for (int i = 0; i < policies.length; i++) {
            PointAccount a = new PointAccount("M001", 100);
            var g = new ImmediatePayment("combination" + i);
            CheckoutService c = checkout(prices(Level.GOLD, false), g, policies[i]);
            SettlementView v = c.begin(new Order("COMBO" + i, "M001", 10000, 0), "C" + i, a);
            eq(v.paidFen(), 9600, "point policy leaves price");
            eq(a.balance(), 100 + expectedPoints[i], "composed point rule");
        }
        // 只更换支付实现，金卡计价、结算调用和积分规则沿用原对象契约。
        for (LocalPayment payment :
                List.of(
                        new ImmediatePayment("replace-immediate"),
                        new EnterpriseApprovalPayment("replace-approval"))) {
            PointAccount account = new PointAccount("M001", 100);
            PriceService samePricing = prices(Level.GOLD, false);
            Order order = new Order("REPLACE", "M001", 10000, 0);
            eq(samePricing.preview(order), 9600, "payment replacement leaves preview");
            eq(payment.payCalls(), 0, "preview has no payment side effect");
            CheckoutService service = checkout(samePricing, payment, new PerYuanPointPolicy());
            service.begin(order, "REPLACE-A", account);
            if (payment instanceof EnterpriseApprovalPayment approval)
                approval.approve("REPLACE-A", true);
            eq(
                    service.confirm("REPLACE-A").paidFen(),
                    9600,
                    "payment replacement leaves paid amount");
            eq(account.balance(), 196, "payment replacement leaves reward rule");
            eq(payment.payCalls(), 1, "channel replacement uses same single-payment flow");
        }
        eq(new PerYuanPointPolicy().earned(99), 0, "sub-yuan truncates");
        PointAccount enterpriseExisting = new PointAccount("M002", 150);
        check(enterpriseExisting.spend(101), "no awards does not disable spending");
        eq(enterpriseExisting.balance(), 49, "enterprise existing points");
    }

    static void testSegregatedInterfaces() {
        PointAccount a = new PointAccount("M001", 100);
        MobilePoints mobile = new MobilePoints(a);
        CashierPoints cashier = new CashierPoints(a);
        eq(mobile.display(), 100, "query terminal");
        check(cashier.spend(30), "cashier capability");
        eq(mobile.display(), 70, "query after spend");
        check(
                Arrays.stream(PointQuery.class.getMethods())
                        .noneMatch(m -> m.getName().equals("spend")),
                "read interface excludes spend");
    }

    static void testSubstitution() {
        contract(MemoryAccount::new);
        contract(LedgerAccount::new);
        contract(n -> new PointAccount("M001", n));
        // 这是预期被检出的反例；修正的PointAccount与LedgerAccount均通过原共同测试。
        raises(
                AssertionError.class,
                () -> contract(HundredOnlyAccount::new),
                "bad LSP implementation detected");
    }

    static void testQuerySnapshots() {
        MemberProfile p = new MemberProfile("M001", "小明", Level.GOLD, "contact");
        PointAccount a = new PointAccount("M001", 100);
        StoredValueAccount stored = new StoredValueAccount("M001", 5000);
        MemberOverviewService overview = new MemberOverviewService(p, a, stored);
        AuthContext member = new AuthContext(Role.MEMBER, Set.of("M001"));
        AuthContext finance = new AuthContext(Role.FINANCE, Set.of("M001"));
        MemberSummaryDTO old = overview.summary(member, T0);
        a.spend(20);
        eq(old.availablePoints(), 100, "old point snapshot");
        eq(overview.summary(member, T0.plusSeconds(1)).availablePoints(), 80, "new point snapshot");
        check(old.asOf().equals(T0), "snapshot time");
        FinanceWalletDTO oldFinance = overview.finance(finance, T0);
        check(oldFinance.latest() == null, "no initial transaction");
        stored.reorganizeLedgers(500);
        eq(
                overview.finance(finance, T0).balanceFen(),
                5000,
                "ledger reorganization keeps query contract");
        check(
                overview.finance(finance, T0).latest() == null,
                "reorganization is not a debit transaction");
        stored.debit("D1", 1200, T0.plusSeconds(1));
        FinanceWalletDTO latest = overview.finance(finance, T0.plusSeconds(2));
        eq(oldFinance.balanceFen(), 5000, "old stored snapshot");
        eq(latest.balanceFen(), 3800, "new stored snapshot");
        eq(latest.latest().deltaFen(), -1200, "debit transaction");
        raises(
                SecurityException.class,
                () -> overview.finance(member, T0),
                "finance role required");
        raises(
                SecurityException.class,
                () -> overview.finance(new AuthContext(Role.FINANCE, Set.of("M002")), T0),
                "finance scope required");
        raises(
                SecurityException.class,
                () -> overview.summary(new AuthContext(Role.MEMBER, Set.of()), T0),
                "summary scope required");
        raises(
                IllegalArgumentException.class,
                () -> new MemberOverviewService(p, new PointAccount("M002", 0), stored),
                "assembly member mismatch");
        Set<String> fields = new HashSet<>();
        for (var component : MemberSummaryDTO.class.getRecordComponents())
            fields.add(component.getName());
        check(
                fields.equals(Set.of("displayName", "levelName", "availablePoints", "asOf")),
                "mobile DTO field boundary");
    }

    static void testPricingBoundaries() {
        Order partial = new Order("PARTIAL", "M001", 8000, 2000);
        PriceService p = prices(Level.PLATINUM, true);
        eq(p.preview(partial), 6560, "partial birthday");
        eq(p.checkoutPrice(partial), 6560, "partial both entries");
        PricingPolicy[] active = {new EligiblePricing(discountFor(Level.GOLD, false))};
        PriceService configurable = new PriceService(o -> active[0].payable(o));
        var historyGateway = new ImmediatePayment("history");
        PointAccount historyAccount = new PointAccount("M001", 100);
        CheckoutService history = checkout(configurable, historyGateway, new PerYuanPointPolicy());
        Order historicalOrder = new Order("HISTORY", "M001", 10000, 0);
        history.begin(historicalOrder, "H1", historyAccount);
        active[0] = new EligiblePricing(new BasisPointDiscount(9300));
        eq(configurable.preview(historicalOrder), 9300, "new price configuration");
        eq(history.state("H1").paidFen(), 9600, "saved historical paid amount");
        eq(
                history.begin(historicalOrder, "H1", historyAccount).paidFen(),
                9600,
                "repeat uses saved price");
        eq(historyGateway.payCalls(), 1, "history no new payment");
    }

    static void runTests() {
        Runnable[] groups = {
            Chapter3Examples::testPricing,
            Chapter3Examples::testResponsibilities,
            Chapter3Examples::testPaymentStates,
            Chapter3Examples::testComposition,
            Chapter3Examples::testSegregatedInterfaces,
            Chapter3Examples::testSubstitution,
            Chapter3Examples::testQuerySnapshots,
            Chapter3Examples::testPricingBoundaries
        };
        for (int i = 0; i < groups.length; i++) {
            groups[i].run();
            System.out.println("PASS scenario group " + (i + 1));
        }
        System.out.println(
                "ALL PASS: 8 scenario groups; "
                        + checks
                        + " checks (includes expected counterexample).");
    }

    static void demo() {
        Order order = new Order("DEMO", "M001", 10000, 0);
        System.out.println("预览与收银计价（分）");
        for (Level level : Level.values()) {
            PriceService p = prices(level, false);
            System.out.println(level + ": " + p.preview(order) + " / " + p.checkoutPrice(order));
        }
        System.out.println("铂金生日: " + prices(Level.PLATINUM, true).preview(order));
        var g = new ImmediatePayment("demo", true);
        PointAccount a = new PointAccount("M001", 100);
        CheckoutService c = checkout(prices(Level.GOLD, false), g, new PerYuanPointPolicy());
        System.out.println("初次响应: " + c.begin(order, "DEMO-A", a));
        System.out.println("查询原付款: " + c.confirm("DEMO-A"));
        c.confirm("DEMO-A");
        System.out.println("重复通知后积分=" + a.balance() + ", 付款调用次数=" + g.payCalls());
        System.out.println(
                "部分商品生日优惠="
                        + prices(Level.PLATINUM, true)
                                .preview(new Order("PART", "M001", 8000, 2000)));
        System.out.println("运行 java Chapter3Examples --test 执行回归验证。");
    }

    public static void main(String[] args) {
        if (args.length == 0) demo();
        else if (args.length == 1 && args[0].equals("--test")) runTests();
        else throw new IllegalArgumentException("用法: java Chapter3Examples [--test]");
    }
}
