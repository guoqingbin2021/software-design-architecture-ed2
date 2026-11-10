package com.example.pos.checkout;

import java.util.Objects;

/** 一次支付的不可变快照，金额与所含税额均为分。 */
public record PaymentRequest(String orderId, String attemptId, long amountCents, long taxCents) {
    public PaymentRequest {
        Objects.requireNonNull(orderId);
        Objects.requireNonNull(attemptId);
        if (orderId.isBlank()
                || attemptId.isBlank()
                || amountCents < 0
                || taxCents < 0
                || taxCents > amountCents)
            throw new IllegalArgumentException("invalid payment snapshot");
    }
}
