package retail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 与正文案例一致：共享范围为一个设备池，打印对象保留同步记录能力。 */
public final class DevicePoolMain {
    public static void main(String[] args) throws Exception {
        AtomicInteger constructions = new AtomicInteger();
        DevicePool pool =
                new DevicePool(
                        key -> {
                            constructions.incrementAndGet();
                            return new MemoryPrinter(key.model());
                        });
        var workers = Executors.newFixedThreadPool(8);
        List<java.util.concurrent.Future<MemoryPrinter>> requests = new ArrayList<>();
        MemoryPrinter shared;
        try {
            for (int i = 0; i < 64; i++)
                requests.add(workers.submit(() -> pool.get("S01", "P01", "SNBC")));
            shared = requests.get(0).get(10, TimeUnit.SECONDS);
            for (var request : requests)
                check(
                        request.get(10, TimeUnit.SECONDS) == shared,
                        "same identity reuses one object");
            check(constructions.get() == 1, "constructed once");
            check(pool.get("S01", "P02", "SNBC") != shared, "different device has its own object");
            check(
                    pool.get("S01", "P01", "SISS") != shared,
                    "different configuration has its own object");
            check(constructions.get() == 3, "one object per complete key");
            try (DevicePool other = new DevicePool()) {
                check(other.get("S01", "P01", "SNBC") != shared, "pools are independent");
            }
            for (int i = 0; i < 64; i++) {
                int order = i;
                workers.submit(() -> shared.print("O" + order + ":4900"));
            }
            workers.shutdown();
            check(
                    workers.awaitTermination(10, TimeUnit.SECONDS),
                    "all printing completed before close");
            check(shared.printCount() == 64, "driver records all calls");
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
            pool.close();
        }
        pool.close();
        check(shared.isClosed() && shared.closeCount() == 1, "released once");
        expectClosed(() -> pool.get("S01", "P01", "SNBC"));
        expectClosed(() -> shared.print("after close"));
        try (DevicePool fresh = new DevicePool()) {
            check(
                    !fresh.get("S01", "P01", "SNBC").isClosed(),
                    "closed pool does not affect a new pool");
        }
        System.out.println(
                "PASS: device pool; 64 shared lookups, identity separation, 64 prints, cleanup");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    private static void expectClosed(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("closed object must reject access");
    }
}
