package retail;

import java.util.List;

/** 补充运行入口：在同一订单与金额下检查三种渠道、两种设备的六种组合。 */
public final class SimpleFactoryMain {
    public static void main(String[] args) {
        int combinations = 0;
        for (String channel : new String[] {"ALI", "WECHAT", "UNION"}) {
            for (String model : new String[] {"SNBC", "SISS"}) {
                Payment payment = SimpleFactory.payment(channel);
                Printer printer = SimpleFactory.printer(model);
                MemoryPrinter device = (MemoryPrinter) printer;
                try (printer) {
                    CheckoutService checkout = new CheckoutService(payment, printer);
                    String result = checkout.checkout("O001", 4900L);
                    Checks.equal(channel + ":O001:4900", result, "Payment contract");
                    Checks.equal(model, device.model(), "Selected device");
                    Checks.equal(
                            List.of(result), device.receipts(), "Receipt content and call count");
                    System.out.println(channel + "/" + model + " -> " + result);
                    combinations++;
                }
                Checks.that(device.isClosed(), "Assembly owner closes the printer");
                printer.close();
                Checks.equal(1, device.closeCount(), "Idempotent close");
                Checks.throwsType(IllegalStateException.class, () -> printer.print("after close"));
            }
        }
        for (String invalid : new String[] {null, "", "UNKNOWN"}) {
            Checks.throwsType(IllegalArgumentException.class, () -> SimpleFactory.payment(invalid));
            Checks.throwsType(IllegalArgumentException.class, () -> SimpleFactory.printer(invalid));
        }
        Checks.equal(6, combinations, "All combinations checked");
        System.out.println("CHECKS PASSED: 6 combinations, invalid identifiers, cleanup");
    }
}
