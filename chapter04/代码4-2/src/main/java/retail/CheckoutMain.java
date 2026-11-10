package retail;

import java.util.List;

/** 补充入口：只使用内存对象，分别检查成功、非法输入与失败传播。 */
public final class CheckoutMain {
    public static void main(String[] args) {
        Checks.FakePayment payment = new Checks.FakePayment();
        MemoryPrinter printer = new MemoryPrinter();
        try (printer) {
            CheckoutService checkout = new CheckoutService(payment, printer);
            String result = checkout.checkout("O001", 4900L);
            Checks.equal("FAKE:O001:4900", result, "Return value");
            Checks.equal("O001", payment.orderNo, "Order passed unchanged");
            Checks.equal(4900L, payment.amountFen, "Amount passed in fen");
            Checks.equal(1, payment.calls, "Pay once");
            Checks.equal(1, printer.printCount(), "Print once");
            Checks.equal(List.of(result), printer.receipts(), "Print payment result");
            Checks.throwsType(IllegalArgumentException.class, () -> checkout.checkout(null, 4900));
            Checks.throwsType(IllegalArgumentException.class, () -> checkout.checkout(" ", 4900));
            Checks.throwsType(IllegalArgumentException.class, () -> checkout.checkout("O001", 0));
            Checks.throwsType(IllegalArgumentException.class, () -> checkout.checkout("O001", -1));
            Checks.equal(1, payment.calls, "Invalid input must not reach payment");
            Checks.equal(1, printer.printCount(), "Invalid input must not print");
            Checks.throwsType(NullPointerException.class, () -> new CheckoutService(null, printer));
            Checks.throwsType(NullPointerException.class, () -> new CheckoutService(payment, null));
            System.out.println(result);
            System.out.println(
                    "order="
                            + payment.orderNo
                            + ", amountFen="
                            + payment.amountFen
                            + ", paymentCalls="
                            + payment.calls
                            + ", prints="
                            + printer.printCount());
        }
        Checks.that(printer.isClosed(), "Assembly owner closes printer");
        checkFailureOrder();
        System.out.println(
                "CHECKS PASSED: injection, arguments, invalid input, failure order, cleanup");
    }

    private static void checkFailureOrder() {
        IllegalStateException paymentFailure =
                new IllegalStateException("Simulated payment failure");
        try (MemoryPrinter printer = new MemoryPrinter()) {
            Payment failing =
                    (order, amount) -> {
                        throw paymentFailure;
                    };
            CheckoutService checkout = new CheckoutService(failing, printer);
            Checks.that(
                    Checks.throwsType(
                                    IllegalStateException.class,
                                    () -> checkout.checkout("O001", 4900))
                            == paymentFailure,
                    "Original payment failure propagates");
            Checks.equal(0, printer.printCount(), "Payment failure prevents printing");
        }
        Checks.FakePayment paid = new Checks.FakePayment();
        IllegalStateException printFailure = new IllegalStateException("Simulated print failure");
        try (Printer failingPrinter =
                new Printer() {
                    @Override
                    public void print(String receipt) {
                        throw printFailure;
                    }

                    @Override
                    public void close() {}
                }) {
            CheckoutService checkout = new CheckoutService(paid, failingPrinter);
            Checks.that(
                    Checks.throwsType(
                                    IllegalStateException.class,
                                    () -> checkout.checkout("O001", 4900))
                            == printFailure,
                    "Original print failure propagates");
            Checks.equal(1, paid.calls, "Print failure must not retry payment");
        }
    }
}
