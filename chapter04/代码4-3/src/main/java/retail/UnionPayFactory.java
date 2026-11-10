package retail;

public final class UnionPayFactory extends PaymentFactory {
    public Payment createPayment() {
        return new UnionPay();
    }
}
