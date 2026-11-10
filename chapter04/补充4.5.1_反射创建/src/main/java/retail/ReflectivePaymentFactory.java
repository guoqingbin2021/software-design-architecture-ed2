package retail;

import java.util.Map;

public final class ReflectivePaymentFactory {
    private final Map<String, String> allowed;

    public ReflectivePaymentFactory() {
        this(
                Map.of(
                        "ALI",
                        "retail.AliPayGateway",
                        "WECHAT",
                        "retail.WeChatPay",
                        "UNION",
                        "retail.UnionPay"));
    }

    // 仅同包检查程序注入预先登记的测试类型；外部配置只能提供渠道标识。
    ReflectivePaymentFactory(Map<String, String> allowed) {
        this.allowed = Map.copyOf(allowed);
    }

    public Payment payment(String channel) {
        if (channel == null || channel.isBlank())
            throw new IllegalArgumentException("Missing channel");
        String name = allowed.get(channel);
        if (name == null) throw new IllegalArgumentException("Unknown channel: " + channel);
        try {
            Class<?> raw =
                    Class.forName(name, false, ReflectivePaymentFactory.class.getClassLoader());
            Class<? extends Payment> type = raw.asSubclass(Payment.class);
            java.lang.reflect.Constructor<? extends Payment> constructor = type.getConstructor();
            return constructor.newInstance();
        } catch (ClassCastException | ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot create payment: " + channel, ex);
        }
    }
}
