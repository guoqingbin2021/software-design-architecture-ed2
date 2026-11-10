package retail;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 为运行教材片段补充的内存打印实现；记录调用并支持显式关闭。 */
public class MemoryPrinter implements Printer {
    private final String model;
    private final List<String> receipts = new ArrayList<>();
    private boolean closed;
    private int closeCount;

    public MemoryPrinter() {
        this("MEMORY");
    }

    public MemoryPrinter(String model) {
        this.model = Objects.requireNonNull(model);
    }

    @Override
    public synchronized void print(String receipt) {
        if (closed) throw new IllegalStateException("Printer is closed: " + model);
        receipts.add(Objects.requireNonNull(receipt));
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            closeCount++;
        }
    }

    public String model() {
        return model;
    }

    public synchronized int printCount() {
        return receipts.size();
    }

    public synchronized List<String> receipts() {
        return List.copyOf(receipts);
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    public synchronized int closeCount() {
        return closeCount;
    }
}
