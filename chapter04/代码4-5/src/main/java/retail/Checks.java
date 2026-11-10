package retail;

import java.util.Properties;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertiesPropertySource;

/** 显式检查，无需 -ea；按当前模块的实际检查项报告结果。 */
public final class Checks {
    private static int checks;

    public static void main(String[] args) {
        checkFake();
        for (String channel : new String[] {"ALI", "WECHAT", "UNION"})
            for (String model : new String[] {"SNBC", "SISS"}) checkContainer(channel, model);
        expectFailure(Missing.class, new Properties(), NoSuchBeanDefinitionException.class);
        expectFailure(Ambiguous.class, new Properties(), NoUniqueBeanDefinitionException.class);
        expectFailure(
                RetailConfig.class, properties("UNKNOWN", "SNBC"), IllegalArgumentException.class);
        expectFailure(
                RetailConfig.class, properties("ALI", "UNKNOWN"), IllegalArgumentException.class);
        Properties absent = new Properties();
        absent.setProperty("printer.model", "SNBC");
        expectFailure(RetailConfig.class, absent, IllegalStateException.class);
        System.out.println("PASS: " + checks + " Spring and service checks.");
    }

    private static void checkFake() {
        FakePayment payment = new FakePayment();
        MemoryPrinter printer = new MemoryPrinter("MEMORY");
        CheckoutService checkout = new CheckoutService(payment, printer);
        check("FAKE:O001:4900".equals(checkout.checkout("O001", 4900L)), "测试替身返回正确");
        check("O001".equals(payment.orderNo) && payment.amountFen == 4900L, "订单号及分金额正确传递");
        check(printer.printed().equals(java.util.List.of("FAKE:O001:4900")), "仅打印同一支付结果一次");
        try {
            checkout.checkout("", 4900L);
            throw new AssertionError("拒绝空订单");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        check(payment.calls == 1, "无效订单未调用支付");
        Payment failing =
                (orderNo, amountFen) -> {
                    throw new IllegalStateException("payment failed");
                };
        try {
            new CheckoutService(failing, printer).checkout("O001", 4900L);
            throw new AssertionError("支付异常传播");
        } catch (IllegalStateException expected) {
            checks++;
        }
        check(printer.printed().size() == 1, "支付失败不打印");
        printer.close();
    }

    private static void checkContainer(String channel, String model) {
        MemoryPrinter printer;
        try (var context = create(RetailConfig.class, properties(channel, model))) {
            CheckoutService checkout = context.getBean(CheckoutService.class);
            printer = (MemoryPrinter) context.getBean(Printer.class);
            check(model.equals(printer.model()), "按配置选择打印机");
            check(checkout == context.getBean(CheckoutService.class), "同一容器共享同一单例");
            check(!printer.isClosed(), "使用时设备未关闭");
            String expected = channel + ":O001:4900";
            check(expected.equals(checkout.checkout("O001", 4900L)), "按配置选择支付渠道");
            check(printer.printed().equals(java.util.List.of(expected)), "打印相同交易结果");
        }
        check(printer.isClosed() && printer.closeCount() == 1, "容器退出调用受管打印机 close 一次");
        try {
            printer.print("after close");
            throw new AssertionError("关闭后不可使用");
        } catch (IllegalStateException expected) {
            checks++;
        }
    }

    private static AnnotationConfigApplicationContext create(Class<?> config, Properties props) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment()
                .getPropertySources()
                .addFirst(new PropertiesPropertySource("store", props));
        context.register(config);
        try {
            context.refresh();
            return context;
        } catch (RuntimeException failure) {
            context.close();
            throw failure;
        }
    }

    private static Properties properties(String channel, String model) {
        Properties p = new Properties();
        p.setProperty("payment.channel", channel);
        p.setProperty("printer.model", model);
        return p;
    }

    private static void expectFailure(
            Class<?> config, Properties props, Class<? extends Throwable> cause) {
        try (var ignored = create(config, props)) {
            throw new AssertionError("预期装配失败: " + cause.getSimpleName());
        } catch (RuntimeException failure) {
            Throwable root = failure;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            check(
                    cause.isInstance(root),
                    "装配失败根因: "
                            + cause.getSimpleName()
                            + ", 实际: "
                            + root.getClass().getSimpleName());
            System.out.println("Expected startup rejection: " + root.getClass().getSimpleName());
        }
    }

    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }

    public static final class FakePayment implements Payment {
        String orderNo;
        long amountFen;
        int calls;

        public String pay(String orderNo, long amountFen) {
            this.orderNo = orderNo;
            this.amountFen = amountFen;
            calls++;
            return "FAKE:" + orderNo + ":" + amountFen;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Missing {
        @Bean(destroyMethod = "close")
        Printer printer() {
            return new MemoryPrinter("MEMORY");
        }

        @Bean
        CheckoutService checkout(Payment payment, Printer printer) {
            return new CheckoutService(payment, printer);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Ambiguous {
        @Bean
        Payment firstPayment() {
            return new AliPayGateway();
        }

        @Bean
        Payment secondPayment() {
            return new WeChatPay();
        }

        @Bean(destroyMethod = "close")
        Printer printer() {
            return new MemoryPrinter("MEMORY");
        }

        @Bean
        CheckoutService checkout(Payment payment, Printer printer) {
            return new CheckoutService(payment, printer);
        }
    }
}
