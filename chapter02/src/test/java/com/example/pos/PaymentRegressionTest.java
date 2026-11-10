package com.example.pos;

import com.example.pos.checkout.*;
import com.example.pos.sale.*;
import com.example.pos.tax.TaxAdapter;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class PaymentRegressionTest {
    static Sale fixed() {
        Sale s = new Sale();
        s.addLineItem(new Item("A", 1250), 2);
        s.addLineItem(new Item("B", 800), 3);
        return s;
    }

    @Test
    void gatewayMustChargeDiscounted4400Cents() {
        AtomicLong charged = new AtomicLong();
        Sale s = fixed();
        new PaymentService(
                        a -> {
                            charged.set(a.amountCents());
                            return PaymentReceipt.success(a, "TX");
                        },
                        Runnable::run)
                .payAsync(s, new Callback());
        assertEquals(4400, charged.get(), "网关扣款必须等于满减后应付");
    }

    @Test
    void transportExceptionMustRemainPending() {
        AtomicReference<String> result = new AtomicReference<>();
        new PaymentService(
                        a -> {
                            throw new RuntimeException("response lost");
                        },
                        Runnable::run)
                .payAsync(
                        fixed(),
                        new Callback() {
                            public void onPending(Sale s, String id) {
                                result.set("PENDING");
                            }

                            public void onFailure(Sale s, String reason) {
                                result.set("FAILURE");
                            }
                        });
        assertEquals("PENDING", result.get(), "响应丢失不能证明未扣款");
    }

    @Test
    void taxIncludedInPriceMustBeExtracted() {
        assertEquals(128, new TaxAdapter().taxOf(4400), "含税应付4400按教学3%提取税额并四舍五入");
    }

    static class Callback implements PaymentCallback {
        public void onSuccess(Sale s) {}

        public void onFailure(Sale s, String reason) {}

        public void onPending(Sale s, String id) {}
    }
}
