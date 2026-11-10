package retail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 补充入口：首次访问从同一起跑点并发发起；只检查本类加载器中的引用复用。 */
public final class SingletonMain {
    private static final int THREADS = 16;
    private static final int READS_PER_THREAD = 1000;

    public static void main(String[] args) throws Exception {
        verify("EagerConfig", EagerConfig::getInstance);
        verify("LazyConfig", LazyConfig::getInstance);
        verify("DclConfig", DclConfig::getInstance);
        verify("HolderConfig", HolderConfig::getInstance);
        verify("EnumConfig", () -> EnumConfig.INSTANCE);
        System.out.println("CHECKS PASSED: five singleton access paths");
    }

    private static void verify(String name, Supplier<?> access) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                results.add(
                        workers.submit(
                                () -> {
                                    ready.countDown();
                                    start.await();
                                    Object first = access.get();
                                    Checks.that(first != null, name + " must return an instance");
                                    for (int j = 1; j < READS_PER_THREAD; j++)
                                        Checks.that(
                                                access.get() == first,
                                                name + " changed within one thread");
                                    return first;
                                }));
            }
            Checks.that(ready.await(10, TimeUnit.SECONDS), "Workers did not become ready");
            start.countDown();
            Object expected = results.get(0).get(10, TimeUnit.SECONDS);
            for (Future<Object> result : results)
                Checks.that(
                        result.get(10, TimeUnit.SECONDS) == expected,
                        name + " returned different references across threads");
            Checks.that(access.get() == expected, name + " did not retain the shared reference");
            System.out.println(name + ": sameReference=true, threads=" + THREADS);
        } finally {
            start.countDown();
            workers.shutdownNow();
            Checks.that(workers.awaitTermination(10, TimeUnit.SECONDS), "Workers did not stop");
        }
    }
}
