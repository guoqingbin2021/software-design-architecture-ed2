package com.example.pos.checkout;

import java.util.Objects;

/** 适配器把平台原报文转换为三种有明确含义的结果，并回传请求标识和金额。 */
public record PaymentReceipt(
        PaymentRequest request, Status status, String tradeNo, String message) {
    public enum Status {
        SUCCESS,
        REJECTED,
        PENDING
    }

    public PaymentReceipt {
        Objects.requireNonNull(request);
        Objects.requireNonNull(status);
        if (status == Status.SUCCESS && (tradeNo == null || tradeNo.isBlank()))
            throw new IllegalArgumentException("success requires platform trade number");
    }

    public static PaymentReceipt success(PaymentRequest request, String tradeNo) {
        return new PaymentReceipt(request, Status.SUCCESS, tradeNo, "ok");
    }

    public static PaymentReceipt pending(PaymentRequest request, String tradeNo) {
        return new PaymentReceipt(request, Status.PENDING, tradeNo, "pending");
    }

    public static PaymentReceipt rejected(PaymentRequest request, String reason) {
        return new PaymentReceipt(request, Status.REJECTED, null, reason);
    }
}
