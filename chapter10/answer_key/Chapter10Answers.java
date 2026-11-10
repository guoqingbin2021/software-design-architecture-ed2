import java.time.*;
import java.util.*;

/** 习题10-8：两个纯函数。真实并发与事务另由原工程集成测试验证。 */
public class Chapter10Answers {
    public record Slot(int day, int start, int end) {
        public Slot {
            if (day < 1 || day > 7 || start < 1 || end > 12 || start > end)
                throw new IllegalArgumentException("slot");
        }
    }

    public static boolean inWindow(Instant now, Instant open, Instant close) {
        Objects.requireNonNull(now);
        Objects.requireNonNull(open);
        Objects.requireNonNull(close);
        if (!open.isBefore(close)) throw new IllegalArgumentException("window");
        return !now.isBefore(open) && now.isBefore(close);
    }

    public static boolean overlap(Slot a, Slot b) {
        return a.day() == b.day() && a.start() <= b.end() && b.start() <= a.end();
    }

    static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }

    public static void main(String[] args) {
        Instant open = Instant.parse("2026-09-01T01:00:00Z"), close = open.plusSeconds(3600);
        check(inWindow(open, open, close));
        check(inWindow(close.minusSeconds(1), open, close));
        check(!inWindow(close, open, close));
        Slot a = new Slot(1, 1, 2);
        check(overlap(a, new Slot(1, 2, 3)));
        check(!overlap(a, new Slot(1, 3, 4)));
        check(!overlap(a, new Slot(2, 1, 2)));
        for (int d1 = 1; d1 <= 7; d1++)
            for (int d2 = 1; d2 <= 7; d2++)
                for (int s1 = 1; s1 <= 12; s1++)
                    for (int s2 = 1; s2 <= 12; s2++) {
                        Slot x = new Slot(d1, s1, s1), y = new Slot(d2, s2, s2);
                        check(overlap(x, y) == (d1 == d2 && s1 == s2));
                        check(overlap(x, y) == overlap(y, x));
                    }
        System.out.println("10-8 PASS: window boundaries and weekly periods");
    }
}
