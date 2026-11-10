package retail;

import java.util.Objects;

public final class CheckoutService {
    private final Payment payment;
    private final Printer printer;

    public CheckoutService(Payment payment, Printer printer) {
        this.payment = Objects.requireNonNull(payment);
        this.printer = Objects.requireNonNull(printer);
    }

    public String checkout(String orderNo, long amountFen) {
        if (orderNo == null || orderNo.isBlank() || amountFen <= 0)
            throw new IllegalArgumentException("Invalid order");
        String result = payment.pay(orderNo, amountFen);
        printer.print(result);
        return result;
    }
}
