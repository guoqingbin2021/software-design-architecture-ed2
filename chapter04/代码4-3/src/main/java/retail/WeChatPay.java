package retail;

/** 为运行片段补充的本地支付实现，不连接支付平台。 */
public final class WeChatPay implements Payment {
    @Override
    public String pay(String orderNo, long amountFen) {
        if (orderNo == null || orderNo.isBlank() || amountFen <= 0)
            throw new IllegalArgumentException("Invalid order");
        return "WECHAT:" + orderNo + ":" + amountFen;
    }
}
