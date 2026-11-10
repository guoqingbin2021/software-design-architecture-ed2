package retail;

/** 教材4.3.1节未编号片段；printer方法为配套运行补齐。 */
public final class SimpleFactory {
    private SimpleFactory() {}

    public static Payment payment(String channel) {
        if (channel == null) throw new IllegalArgumentException("Missing channel");
        return switch (channel) {
            case "ALI" -> new AliPayGateway();
            case "WECHAT" -> new WeChatPay();
            case "UNION" -> new UnionPay();
            default -> throw new IllegalArgumentException("Unknown channel: " + channel);
        };
    }

    public static Printer printer(String model) {
        if (model == null) throw new IllegalArgumentException("Missing model");
        return switch (model) {
            case "SNBC" -> new SNBCPrinter();
            case "SISS" -> new SissPrinter();
            default -> throw new IllegalArgumentException("Unknown model: " + model);
        };
    }
}
