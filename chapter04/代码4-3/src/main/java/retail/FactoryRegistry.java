package retail;

import java.util.Map;

/** 标识只选择已登记的工厂；产品的创建仍由各具体工厂完成。 */
public final class FactoryRegistry {
    private static final Map<String, PaymentFactory> PAYMENTS =
            Map.of(
                    "ALI", new AliPayGatewayFactory(),
                    "WECHAT", new WeChatPayFactory(),
                    "UNION", new UnionPayFactory());
    private static final Map<String, PrinterFactory> PRINTERS =
            Map.of(
                    "SNBC", new SNBCPrinterFactory(),
                    "SISS", new SissPrinterFactory());

    private FactoryRegistry() {}

    public static Payment payment(String channel) {
        if (channel == null) throw new IllegalArgumentException("Missing channel");
        PaymentFactory factory = PAYMENTS.get(channel);
        if (factory == null) throw new IllegalArgumentException("Unknown channel: " + channel);
        return factory.createPayment();
    }

    public static Printer printer(String model) {
        if (model == null) throw new IllegalArgumentException("Missing model");
        PrinterFactory factory = PRINTERS.get(model);
        if (factory == null) throw new IllegalArgumentException("Unknown model: " + model);
        return factory.createPrinter();
    }
}
