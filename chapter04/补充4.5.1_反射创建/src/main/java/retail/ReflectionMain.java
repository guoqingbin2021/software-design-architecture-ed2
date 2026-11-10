package retail;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public final class ReflectionMain {
    private static int checks;

    public static void main(String[] args) {
        ReflectivePaymentFactory factory = new ReflectivePaymentFactory();
        for (String channel : new String[] {"ALI", "WECHAT", "UNION"}) {
            String result = factory.payment(channel).pay("O001", 4900L);
            check((channel + ":O001:4900").equals(result), "允许渠道创建正确产品");
            System.out.println(result);
        }
        reject(factory, null);
        reject(factory, "");
        reject(factory, "UNKNOWN");
        reject(factory, "java.lang.Runtime");
        expectCause("retail.NotDeployedPayment", ClassNotFoundException.class);
        expectCause(NotPayment.class.getName(), ClassCastException.class);
        expectCause(NoPublicConstructor.class.getName(), NoSuchMethodException.class);
        Throwable failure =
                expectCause(ThrowingConstructor.class.getName(), InvocationTargetException.class);
        check(failure.getCause().getCause() instanceof IllegalArgumentException, "构造失败原始原因保留");
        check(
                "constructor failed".equals(failure.getCause().getCause().getMessage()),
                "构造失败原始消息保留");
        System.out.println("PASS: " + checks + " reflection checks.");
    }

    private static void reject(ReflectivePaymentFactory factory, String channel) {
        try {
            factory.payment(channel);
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError("应在加载类前拒绝渠道: " + channel);
    }

    private static Throwable expectCause(String name, Class<? extends Throwable> cause) {
        ReflectivePaymentFactory factory = new ReflectivePaymentFactory(Map.of("FIXTURE", name));
        try {
            factory.payment("FIXTURE");
        } catch (IllegalStateException failure) {
            check(cause.isInstance(failure.getCause()), "保留原因: " + cause.getSimpleName());
            check(failure.getMessage().contains("FIXTURE"), "错误消息标明渠道");
            return failure;
        }
        throw new AssertionError("应拒绝无效实现: " + name);
    }

    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }

    public static final class NotPayment {
        public NotPayment() {}
    }

    public static final class NoPublicConstructor implements Payment {
        private NoPublicConstructor() {}

        public String pay(String orderNo, long amountFen) {
            return "unused";
        }
    }

    public static final class ThrowingConstructor implements Payment {
        public ThrowingConstructor() {
            throw new IllegalArgumentException("constructor failed");
        }

        public String pay(String orderNo, long amountFen) {
            return "unused";
        }
    }
}
