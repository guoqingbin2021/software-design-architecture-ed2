package retail;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** 池拥有内部驱动；应用先停止任务并等待完成，再关闭池。 */
public final class DevicePool implements AutoCloseable {
    public record DeviceKey(String storeId, String deviceId, String model) {
        public DeviceKey {
            requireText(storeId);
            requireText(deviceId);
            requireText(model);
        }

        private static void requireText(String value) {
            if (value == null || value.isBlank())
                throw new IllegalArgumentException("Device identity is blank");
        }
    }

    private final Map<DeviceKey, MemoryPrinter> devices = new HashMap<>();
    private final Function<DeviceKey, MemoryPrinter> create;
    private boolean closed;

    public DevicePool() {
        this(key -> new MemoryPrinter(key.model()));
    }

    public DevicePool(Function<DeviceKey, MemoryPrinter> create) {
        this.create = Objects.requireNonNull(create);
    }

    public synchronized MemoryPrinter get(String storeId, String deviceId, String model) {
        if (closed) throw new IllegalStateException("Device pool is closed");
        DeviceKey key = new DeviceKey(storeId, deviceId, model);
        return devices.computeIfAbsent(key, k -> Objects.requireNonNull(create.apply(k)));
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        for (MemoryPrinter device : devices.values()) device.close();
        devices.clear();
    }
}
