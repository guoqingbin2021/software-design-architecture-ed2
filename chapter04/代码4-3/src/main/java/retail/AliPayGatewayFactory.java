package retail;

/** 与代码4-3相同创建契约的具体工厂。 */
public final class AliPayGatewayFactory extends PaymentFactory {
    @Override
    public Payment createPayment() {
        return new AliPayGateway();
    }
}
