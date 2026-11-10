package retail;

public final class LegacyCheckout implements AutoCloseable {
    private final Payment payment = new AliPayGateway();
    private final Printer printer = new SNBCPrinter();

    public String checkout(String orderNo, long amountFen) {
        String result = payment.pay(orderNo, amountFen);
        printer.print(result);
        return result;
    }

    public void close() {
        printer.close();
    }

    // 以下 main 为片段补充运行与检查入口，未改变上方直接创建的旧实现。
    public static void main(String[] args) {
        LegacyCheckout checkout = new LegacyCheckout();
        MemoryPrinter device = (MemoryPrinter) checkout.printer;
        try (checkout) {
            String result = checkout.checkout("O001", 4900L);
            Checks.equal("ALI:O001:4900", result, "Payment result");
            Checks.equal(java.util.List.of(result), device.receipts(), "Receipt content");
            Checks.equal(1, device.printCount(), "Print once after payment");
            Checks.throwsType(IllegalArgumentException.class, () -> checkout.checkout("O001", 0L));
            Checks.equal(1, device.printCount(), "Failed payment must not print");
            System.out.println(result);
            System.out.println("printer=" + device.model() + ", prints=" + device.printCount());
        }
        Checks.that(device.isClosed(), "Owner closes printer");
        checkout.close();
        Checks.equal(1, device.closeCount(), "Repeated close is harmless");
        Checks.throwsType(IllegalStateException.class, () -> device.print("after close"));
        System.out.println("CHECKS PASSED: direct creation, receipt, failure, cleanup");
    }
}
