package retail;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 用内存记录模拟设备输出；关闭后拒绝继续使用。 */
public class MemoryPrinter implements Printer {
    private final String model;
    private final List<String> printed = new ArrayList<>();
    private boolean closed;
    private int closeCount;

    public MemoryPrinter(String model) {
        this.model = Objects.requireNonNull(model);
    }

    public synchronized void print(String content) {
        if (closed) throw new IllegalStateException("Printer closed: " + model);
        printed.add(Objects.requireNonNull(content));
        System.out.println(model + " print: " + content);
    }

    public synchronized void close() {
        if (!closed) {
            closed = true;
            closeCount++;
            System.out.println(model + " closed");
        }
    }

    public String model() {
        return model;
    }

    public synchronized List<String> printed() {
        return List.copyOf(printed);
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    public synchronized int closeCount() {
        return closeCount;
    }
}
