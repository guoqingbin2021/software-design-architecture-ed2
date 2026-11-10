package com.example.pos.checkout;

/** 网关应以attemptId幂等扣款，拒绝同标识下订单或金额冲突；示例不连接真实平台。 */
public interface PaymentGateway {
    PaymentReceipt charge(PaymentRequest request);

    /** 查询只核对原尝试，不产生新的扣款。适配器未提供查询时维持待确认。 */
    default PaymentReceipt query(PaymentRequest request) {
        return PaymentReceipt.pending(request, null);
    }
}
