import java.math.*;
import java.util.*;

/** 习题8-8及8-10的值对象验证，与JPA实体片段分开编译。 */
public class Chapter08Answers {
    public record Score(BigDecimal value) {
        public Score {
            Objects.requireNonNull(value, "score");
            if (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0)
                throw new IllegalArgumentException("score range");
            value = value.setScale(2, RoundingMode.UNNECESSARY);
        }
    }

    static void rejects(String s) {
        try {
            new Score(new BigDecimal(s));
        } catch (IllegalArgumentException | ArithmeticException expected) {
            return;
        }
        throw new AssertionError("accepted " + s);
    }

    public static void main(String[] args) {
        for (String s : List.of("0", "100", "85.00")) new Score(new BigDecimal(s));
        rejects("-0.01");
        rejects("100.01");
        rejects("85.555");
        if (!new Score(new BigDecimal("85.0")).equals(new Score(new BigDecimal("85.00"))))
            throw new AssertionError();
        try {
            new Score(null);
            throw new AssertionError();
        } catch (NullPointerException expected) {
        }
        System.out.println("8-8/8-10 PASS: range, exact scale, equal value, null rejection");
    }
}
