import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 第六章核心示例验证，以及商品同步、取消明细和气象站选学模块。Java 17。 */
public final class Chapter6SupportingExamples {
    record PriceLog(long version, String productId, BigDecimal price) {}

    static final class PriceLogServer {
        final List<PriceLog> logs = new ArrayList<>();

        void append(String productId, String price) {
            if (productId == null || productId.isBlank())
                throw new IllegalArgumentException("商品编号为空");
            logs.add(
                    new PriceLog(
                            logs.size() + 1L,
                            productId,
                            TextbookCore.money(new BigDecimal(price))));
        }

        List<PriceLog> read(long completed, long target) {
            if (completed < 0 || target < completed || target > logs.size())
                throw new IllegalArgumentException("增量区间无效");
            return List.copyOf(logs.subList(Math.toIntExact(completed), Math.toIntExact(target)));
        }
    }

    record TerminalState(long completed, Map<String, PriceLog> catalog) {
        TerminalState {
            catalog = Map.copyOf(catalog);
        }
    }

    static final class StoreTerminal {
        TerminalState state = new TerminalState(0, Map.of());
        long target;

        void notice(long version) {
            target = Math.max(target, version);
        }

        void reconnect(PriceLogServer server) {
            notice(server.logs.size());
        }

        void sync(PriceLogServer server, boolean failAfterFirst, Runnable duringDownload) {
            long completed = state.completed();
            long fixedTarget = target;
            if (fixedTarget <= completed) return;
            List<PriceLog> batch = server.read(completed, fixedTarget);
            if (batch.size() != fixedTarget - completed) throw new IllegalStateException("增量不完整");
            Map<String, PriceLog> candidate = new HashMap<>(state.catalog());
            long expected = completed + 1;
            for (PriceLog log : batch) {
                if (log.version() != expected++) throw new IllegalStateException("连续版本不符");
                candidate.put(log.productId(), log);
                if (failAfterFirst) throw new IllegalStateException("模拟应用中途失败");
            }
            duringDownload.run();
            // 候选状态完整准备后一次替换；失败前只改候选副本，不改公开数据或断点。
            state = new TerminalState(fixedTarget, candidate);
        }
    }

    record Line(String productId, int quantity, long unitCents) {
        Line {
            if (productId == null || productId.isBlank() || quantity <= 0 || unitCents < 0)
                throw new IllegalArgumentException("明细无效");
        }
    }

    static final class LineDraft {
        final TextbookCore.DraftSale status = new TextbookCore.DraftSale();
        private List<Line> lines;

        LineDraft(List<Line> lines) {
            this.lines = List.copyOf(lines);
        }

        List<Line> lines() {
            return lines;
        }

        long totalCents() {
            long total = 0;
            for (Line line : lines)
                total =
                        Math.addExact(
                                total,
                                Math.multiplyExact((long) line.quantity(), line.unitCents()));
            return total;
        }

        void replace(List<Line> expected, List<Line> next) {
            if (status.isSubmitted()) throw new IllegalStateException("已提交明细不能按草稿修改");
            if (!lines.equals(expected)) throw new IllegalStateException("明细与历史冲突");
            List<Line> copy = List.copyOf(next);
            lines = copy;
        }
    }

    static final class CancelLine implements TextbookCore.Command {
        enum State {
            NEW,
            DONE,
            UNDONE
        }

        final LineDraft draft;
        final int index;
        List<Line> before;
        List<Line> after;
        State state = State.NEW;

        CancelLine(LineDraft draft, int index) {
            this.draft = draft;
            this.index = index;
        }

        public TextbookCore.DraftSale target() {
            return draft.status;
        }

        public void execute() {
            require(State.NEW);
            List<Line> original = draft.lines();
            List<Line> changed = new ArrayList<>(original);
            changed.remove(index);
            List<Line> savedAfter = List.copyOf(changed);
            draft.replace(original, savedAfter);
            before = original;
            after = savedAfter;
            state = State.DONE;
        }

        public void undo() {
            require(State.DONE);
            draft.replace(after, before);
            state = State.UNDONE;
        }

        public void redo() {
            require(State.UNDONE);
            draft.replace(before, after);
            state = State.DONE;
        }

        void require(State expected) {
            if (state != expected) throw new IllegalStateException("命令状态不允许该操作");
        }
    }

    record WeatherReading(double temperature, double humidity) {
        WeatherReading {
            if (!Double.isFinite(temperature)
                    || !Double.isFinite(humidity)
                    || humidity < 0
                    || humidity > 100) throw new IllegalArgumentException("读数无效");
        }
    }

    static final class WeatherStation {
        final Chapter6BodyReference.LocalHub<WeatherReading> hub =
                new Chapter6BodyReference.LocalHub<>();
        WeatherReading current;

        void change(double temperature, double humidity) {
            WeatherReading reading = new WeatherReading(temperature, humidity);
            current = reading;
            hub.publish(reading);
        }
    }

    static void equal(Object expected, Object actual) {
        Chapter6BodyReference.equal(expected, actual);
    }

    static <T extends Throwable> void expect(Class<T> type, Runnable action) {
        Chapter6BodyReference.expect(type, action);
    }

    static int passed;

    static void test(String name, Runnable action) {
        action.run();
        passed++;
        System.out.println("PASS " + name);
    }

    static TextbookCore.Gate gate(TextbookCore.Stage stage, boolean accepted, List<String> trace) {
        return new TextbookCore.Gate(
                stage,
                request -> {
                    trace.add(stage.name());
                    return accepted;
                },
                "rejected");
    }

    static List<TextbookCore.Gate> allGates(List<String> trace) {
        return List.of(
                gate(TextbookCore.Stage.LOGIN, true, trace),
                gate(TextbookCore.Stage.PERMISSION, true, trace),
                gate(TextbookCore.Stage.SCOPE, true, trace),
                gate(TextbookCore.Stage.INPUT, true, trace));
    }

    public static void main(String[] args) {
        test(
                "listing 6-1 direct branch agrees with strategy results",
                () -> {
                    String[] names = {"ORIGINAL", "RATE_90", "MINUS_30_OVER_300"};
                    TextbookCore.PricingStrategy[] strategies = {
                        new TextbookCore.OriginalPrice(),
                        new TextbookCore.RateDiscount(new BigDecimal("0.9")),
                        new TextbookCore.ThresholdReduction(
                                new BigDecimal("300"), new BigDecimal("30"))
                    };
                    String[] expected = {"400.00", "360.00", "370.00"};
                    for (int i = 0; i < names.length; i++) {
                        BigDecimal direct =
                                TextbookCore.directPrice(new BigDecimal("400"), names[i]);
                        BigDecimal strategy =
                                new TextbookCore.PriceContext(strategies[i])
                                        .price(new BigDecimal("400"));
                        equal(new BigDecimal(expected[i]), direct);
                        equal(direct, strategy);
                    }
                });
        test(
                "listings 6-2/6-3 reproduce all six amount samples",
                () -> {
                    long[] in = {10000, 10000, 10000, 1, 5, 5};
                    long[] out = {9300, 9600, 5700, 1, 3, 2};
                    String[][] rates = {
                        {"0.93"},
                        {"0.96"},
                        {"0.95", "0.6"},
                        {"0.95", "0.6"},
                        {"0.5"},
                        {"0.5", "0.5"}
                    };
                    for (int i = 0; i < in.length; i++) {
                        List<TextbookCore.PricingStrategy> steps = new ArrayList<>();
                        for (String rate : rates[i])
                            steps.add(new TextbookCore.RateDiscount(new BigDecimal(rate)));
                        var context =
                                new TextbookCore.PriceContext(
                                        new TextbookCore.CombinedDiscount(steps));
                        equal(
                                out[i],
                                TextbookCore.toLongCents(
                                        context.price(TextbookCore.fromCents(in[i]))));
                        context.setStrategy(new TextbookCore.OriginalPrice());
                        equal(
                                in[i],
                                TextbookCore.toLongCents(
                                        context.price(TextbookCore.fromCents(in[i]))));
                    }
                });
        test(
                "listing 6-4 client delivers v1 to both subscribers and v2 only to cache",
                () -> {
                    TextbookCore.localTrace.clear();
                    TextbookCore.localDemo();
                    equal(List.of("label:v1", "cache:v1", "cache:v2"), TextbookCore.localTrace);
                });
        test(
                "observer snapshot survives unsubscribe and isolates callback failure",
                () -> {
                    TextbookCore.Hub hub = new TextbookCore.Hub();
                    List<String> trace = new ArrayList<>();
                    TextbookCore.Subscription[] c = new TextbookCore.Subscription[1];
                    hub.subscribe(
                            e -> {
                                trace.add("A" + e.version());
                                if (e.version() == 1) {
                                    c[0].close();
                                    hub.subscribe(next -> trace.add("D" + next.version()));
                                }
                            });
                    hub.subscribe(
                            e -> {
                                trace.add("B" + e.version());
                                throw new IllegalStateException("display");
                            });
                    c[0] = hub.subscribe(e -> trace.add("C" + e.version()));
                    hub.change("P001", new BigDecimal("12.50"));
                    hub.change("P001", new BigDecimal("11.00"));
                    equal(List.of("A1", "B1", "C1", "A2", "B2", "D2"), trace);
                    equal(2, TextbookCore.failures(hub).size());
                    c[0].close();
                    c[0].close();
                });
        test(
                "observer rejects recursive publication without stopping later observer",
                () -> {
                    TextbookCore.Hub hub = new TextbookCore.Hub();
                    List<Long> seen = new ArrayList<>();
                    hub.subscribe(e -> hub.change("P002", BigDecimal.ONE));
                    hub.subscribe(e -> seen.add(e.version()));
                    hub.change("P001", BigDecimal.TEN);
                    equal(List.of(1L), seen);
                    equal(1, TextbookCore.failures(hub).size());
                });
        test(
                "continuous sync reconnects from c=1 through target=3",
                () -> {
                    PriceLogServer server = new PriceLogServer();
                    StoreTerminal terminal = new StoreTerminal();
                    server.append("P001", "12.50");
                    terminal.reconnect(server);
                    terminal.sync(server, false, () -> {});
                    server.append("P001", "11.00");
                    server.append("P002", "8.00");
                    equal(1L, terminal.state.completed());
                    terminal.reconnect(server);
                    terminal.sync(server, false, () -> {});
                    equal(3L, terminal.state.completed());
                    equal(new BigDecimal("11.00"), terminal.state.catalog().get("P001").price());
                    equal(new BigDecimal("8.00"), terminal.state.catalog().get("P002").price());
                });
        test(
                "continuous sync partial application failure leaves data and checkpoint unchanged",
                () -> {
                    PriceLogServer server = new PriceLogServer();
                    StoreTerminal terminal = new StoreTerminal();
                    server.append("P001", "12.50");
                    terminal.reconnect(server);
                    terminal.sync(server, false, () -> {});
                    TerminalState before = terminal.state;
                    server.append("P001", "11.00");
                    server.append("P002", "8.00");
                    terminal.reconnect(server);
                    expect(
                            IllegalStateException.class,
                            () -> terminal.sync(server, true, () -> {}));
                    equal(before, terminal.state);
                    equal(1L, terminal.state.completed());
                    terminal.sync(server, false, () -> {});
                    equal(3L, terminal.state.completed());
                });
        test(
                "duplicate/out-of-order notices do not roll back progress or reapply completed logs",
                () -> {
                    PriceLogServer server = new PriceLogServer();
                    StoreTerminal terminal = new StoreTerminal();
                    server.append("P001", "10");
                    server.append("P001", "11");
                    server.append("P001", "12");
                    terminal.reconnect(server);
                    terminal.sync(server, false, () -> {});
                    TerminalState before = terminal.state;
                    terminal.notice(3);
                    terminal.notice(2);
                    terminal.notice(3);
                    terminal.sync(server, false, () -> {});
                    equal(before, terminal.state);
                    equal(3L, terminal.target);
                });
        test(
                "new notification during download schedules a later round without moving fixed target",
                () -> {
                    PriceLogServer server = new PriceLogServer();
                    StoreTerminal terminal = new StoreTerminal();
                    server.append("P001", "10");
                    terminal.notice(1);
                    terminal.sync(
                            server,
                            false,
                            () -> {
                                server.append("P001", "11");
                                terminal.notice(2);
                            });
                    equal(1L, terminal.state.completed());
                    equal(2L, terminal.target);
                    equal(new BigDecimal("10.00"), terminal.state.catalog().get("P001").price());
                    terminal.sync(server, false, () -> {});
                    equal(2L, terminal.state.completed());
                });
        test(
                "listings 6-5/6-6 preserve command state and history on failed edit and after submission",
                () -> {
                    TextbookCore.DraftSale sale = new TextbookCore.DraftSale();
                    TextbookCore.History history = new TextbookCore.History(sale);
                    TextbookCore.SetDiscount first = new TextbookCore.SetDiscount(sale, 90);
                    TextbookCore.SetDiscount second = new TextbookCore.SetDiscount(sale, 80);
                    history.execute(first);
                    history.execute(second);
                    history.undo();
                    equal(List.of(90, 1, 1), TextbookCore.historyState(history));
                    expect(
                            IllegalArgumentException.class,
                            () -> history.execute(new TextbookCore.SetDiscount(sale, 110)));
                    equal(List.of(90, 1, 1), TextbookCore.historyState(history));
                    history.redo();
                    equal(List.of(80, 2, 0), TextbookCore.historyState(history));
                    sale.submit();
                    equal("DONE", TextbookCore.commandState(first));
                    equal("DONE", TextbookCore.commandState(second));
                    expect(IllegalStateException.class, history::undo);
                    expect(IllegalStateException.class, history::redo);
                    TextbookCore.SetDiscount forbidden = new TextbookCore.SetDiscount(sale, 70);
                    expect(IllegalStateException.class, () -> history.execute(forbidden));
                    equal(List.of(80, 2, 0), TextbookCore.historyState(history));
                    equal("DONE", TextbookCore.commandState(first));
                    equal("DONE", TextbookCore.commandState(second));
                    equal("NEW", TextbookCore.commandState(forbidden));
                });
        test(
                "two consecutive undo and two redo restore 100 then 80 with exact stack sizes",
                () -> {
                    TextbookCore.DraftSale sale = new TextbookCore.DraftSale();
                    TextbookCore.History history = new TextbookCore.History(sale);
                    history.execute(new TextbookCore.SetDiscount(sale, 90));
                    history.execute(new TextbookCore.SetDiscount(sale, 80));
                    equal(List.of(80, 2, 0), TextbookCore.historyState(history));
                    history.undo();
                    equal(List.of(90, 1, 1), TextbookCore.historyState(history));
                    history.undo();
                    equal(List.of(100, 0, 2), TextbookCore.historyState(history));
                    history.redo();
                    equal(List.of(90, 1, 1), TextbookCore.historyState(history));
                    history.redo();
                    equal(List.of(80, 2, 0), TextbookCore.historyState(history));
                });
        test(
                "external draft mutation rejects undo without changing command or history",
                () -> {
                    TextbookCore.DraftSale sale = new TextbookCore.DraftSale();
                    TextbookCore.History history = new TextbookCore.History(sale);
                    TextbookCore.SetDiscount command = new TextbookCore.SetDiscount(sale, 90);
                    history.execute(command);
                    sale.changeDiscount(90, 70);
                    equal(List.of(70, 1, 0), TextbookCore.historyState(history));
                    equal("DONE", TextbookCore.commandState(command));
                    expect(IllegalStateException.class, history::undo);
                    equal(List.of(70, 1, 0), TextbookCore.historyState(history));
                    equal("DONE", TextbookCore.commandState(command));
                    sale.changeDiscount(70, 90);
                    history.undo();
                    equal(List.of(100, 0, 1), TextbookCore.historyState(history));
                    equal("UNDONE", TextbookCore.commandState(command));
                });
        test(
                "cancel-line command restores product, quantity, price and original position",
                () -> {
                    List<Line> original =
                            List.of(
                                    new Line("P001", 2, 1250),
                                    new Line("P002", 3, 800),
                                    new Line("P003", 1, 100));
                    LineDraft draft = new LineDraft(original);
                    TextbookCore.History history = new TextbookCore.History(draft.status);
                    CancelLine cancel = new CancelLine(draft, 1);
                    equal(5000L, draft.totalCents());
                    history.execute(new TextbookCore.SetDiscount(draft.status, 90));
                    history.execute(cancel);
                    equal(List.of(original.get(0), original.get(2)), draft.lines());
                    equal(2600L, draft.totalCents());
                    history.undo();
                    equal(original, draft.lines());
                    equal(5000L, draft.totalCents());
                    history.redo();
                    equal(2600L, draft.totalCents());
                    equal(CancelLine.State.DONE, cancel.state);
                });
        test(
                "invalid cancellation and submitted cancellation do not alter lines or history",
                () -> {
                    LineDraft draft = new LineDraft(List.of(new Line("P001", 1, 1000)));
                    TextbookCore.History history = new TextbookCore.History(draft.status);
                    CancelLine invalid = new CancelLine(draft, 9);
                    expect(IndexOutOfBoundsException.class, () -> history.execute(invalid));
                    equal(CancelLine.State.NEW, invalid.state);
                    equal(List.of(100, 0, 0), TextbookCore.historyState(history));
                    draft.status.submit();
                    expect(
                            IllegalStateException.class,
                            () -> history.execute(new CancelLine(draft, 0)));
                    equal(1000L, draft.totalCents());
                    equal(List.of(100, 0, 0), TextbookCore.historyState(history));
                });
        test(
                "listing 6-7 chain rejects empty, missing, duplicate and misordered stages",
                () -> {
                    List<TextbookCore.Gate> gates = allGates(new ArrayList<>());
                    expect(IllegalArgumentException.class, () -> new TextbookCore.Chain(List.of()));
                    expect(
                            IllegalArgumentException.class,
                            () -> new TextbookCore.Chain(gates.subList(0, 3)));
                    expect(
                            IllegalArgumentException.class,
                            () ->
                                    new TextbookCore.Chain(
                                            List.of(
                                                    gates.get(0),
                                                    gates.get(1),
                                                    gates.get(1),
                                                    gates.get(3))));
                    expect(
                            IllegalArgumentException.class,
                            () ->
                                    new TextbookCore.Chain(
                                            List.of(
                                                    gates.get(1),
                                                    gates.get(0),
                                                    gates.get(2),
                                                    gates.get(3))));
                });
        test(
                "chain refusal and lookup exception stop successors and business",
                () -> {
                    List<String> trace = new ArrayList<>();
                    int[] calls = {0};
                    TextbookCore.Request request =
                            new TextbookCore.Request(null, "S1", BigDecimal.TEN);
                    new TextbookCore.Chain(allGates(trace)).handle(request, r -> calls[0]++);
                    equal(List.of("LOGIN", "PERMISSION", "SCOPE", "INPUT"), trace);
                    equal(1, calls[0]);
                    trace.clear();
                    calls[0] = 0;
                    List<TextbookCore.Gate> rejected = new ArrayList<>(allGates(trace));
                    rejected.set(1, gate(TextbookCore.Stage.PERMISSION, false, trace));
                    expect(
                            SecurityException.class,
                            () ->
                                    new TextbookCore.Chain(rejected)
                                            .handle(request, r -> calls[0]++));
                    equal(List.of("LOGIN", "PERMISSION"), trace);
                    equal(0, calls[0]);
                    trace.clear();
                    rejected.set(
                            1,
                            new TextbookCore.Gate(
                                    TextbookCore.Stage.PERMISSION,
                                    r -> {
                                        trace.add("PERMISSION");
                                        throw new IllegalStateException(
                                                "simulated authorization timeout");
                                    },
                                    "unavailable"));
                    expect(
                            IllegalStateException.class,
                            () ->
                                    new TextbookCore.Chain(rejected)
                                            .handle(request, r -> calls[0]++));
                    equal(List.of("LOGIN", "PERMISSION"), trace);
                    equal(0, calls[0]);
                });
        test(
                "chain checks server-side permission, store scope and positive price",
                () -> {
                    TextbookCore.Subject subject =
                            new TextbookCore.Subject(true, Set.of("price:adjust"), Set.of("S1"));
                    List<TextbookCore.Gate> gates =
                            List.of(
                                    new TextbookCore.Gate(
                                            TextbookCore.Stage.LOGIN,
                                            r -> r.subject() != null && r.subject().authenticated(),
                                            "login"),
                                    new TextbookCore.Gate(
                                            TextbookCore.Stage.PERMISSION,
                                            r -> r.subject().permissions().contains("price:adjust"),
                                            "permission"),
                                    new TextbookCore.Gate(
                                            TextbookCore.Stage.SCOPE,
                                            r -> r.subject().stores().contains(r.storeId()),
                                            "scope"),
                                    new TextbookCore.Gate(
                                            TextbookCore.Stage.INPUT,
                                            r ->
                                                    r.newPrice() != null
                                                            && r.newPrice().signum() > 0
                                                            && TextbookCore.money(r.newPrice())
                                                                            .signum()
                                                                    > 0,
                                            "input"));
                    TextbookCore.Chain chain = new TextbookCore.Chain(gates);
                    int[] calls = {0};
                    chain.handle(
                            new TextbookCore.Request(subject, "S1", BigDecimal.TEN),
                            r -> calls[0]++);
                    expect(
                            SecurityException.class,
                            () ->
                                    chain.handle(
                                            new TextbookCore.Request(subject, "S2", BigDecimal.TEN),
                                            r -> calls[0]++));
                    expect(
                            SecurityException.class,
                            () ->
                                    chain.handle(
                                            new TextbookCore.Request(
                                                    subject, "S1", BigDecimal.ZERO),
                                            r -> calls[0]++));
                    expect(
                            SecurityException.class,
                            () ->
                                    chain.handle(
                                            new TextbookCore.Request(
                                                    subject, "S1", new BigDecimal("0.001")),
                                            r -> calls[0]++));
                    chain.handle(
                            new TextbookCore.Request(subject, "S1", new BigDecimal("0.005")),
                            r -> calls[0]++);
                    equal(2, calls[0]);
                });
        test(
                "weather elective is a complete local observer example with unsubscribe and failures",
                () -> {
                    WeatherStation station = new WeatherStation();
                    List<Double> temperatures = new ArrayList<>();
                    List<Double> humidities = new ArrayList<>();
                    Chapter6BodyReference.Subscription temperature =
                            station.hub.subscribe(r -> temperatures.add(r.temperature()));
                    station.hub.subscribe(
                            r -> {
                                throw new IllegalStateException("offline display");
                            });
                    station.hub.subscribe(r -> humidities.add(r.humidity()));
                    station.change(26.5, 65);
                    temperature.close();
                    station.change(27, 60);
                    equal(List.of(26.5), temperatures);
                    equal(List.of(65.0, 60.0), humidities);
                    equal(new WeatherReading(27, 60), station.current);
                    equal(2, station.hub.failures.size());
                });
        System.out.println("RESULT " + passed + "/" + passed + " supporting checks passed");
    }
}
