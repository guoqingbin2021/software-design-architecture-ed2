package retail;

/** 内存模拟支付；金额单位始终为分。 */
public final class WeChatPay implements Payment {
    public WeChatPay() {}

    public String pay(String orderNo, long amountFen) {
        if (orderNo == null || orderNo.isBlank() || amountFen <= 0)
            throw new IllegalArgumentException("Invalid order");
        return "WECHAT:" + orderNo + ":" + amountFen;
    }
}
