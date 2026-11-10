package retail;

import java.util.Objects;

/** 显式检查，不需要 java -ea；任一检查失败即抛出 AssertionError。 */
public final class Checks {
    private Checks() {}

    public static void that(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void equal(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual))
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
    }

    public static <T extends Throwable> T throwsType(Class<T> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable ex) {
            if (type.isInstance(ex)) return type.cast(ex);
            throw new AssertionError("Expected " + type.getSimpleName() + " but got " + ex, ex);
        }
        throw new AssertionError(
                "Expected " + type.getSimpleName() + " but no exception was thrown");
    }
}
