package com.example.pos;

import com.example.pos.checkout.*;
import com.example.pos.sale.*;
import com.example.pos.tax.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaymentSafetyTest {
    static Sale fixed() {
        Sale s = new Sale();
        s.addLineItem(new Item("A", 1250), 2);
        s.addLineItem(new Item("B", 800), 3);
        return s;
    }

    static CheckoutController controller(Sale sale, PaymentGateway gateway, Printer printer) {
        return new CheckoutController(
                sale, new TaxAdapter(), printer, new PaymentService(gateway, Runnable::run));
    }

    @Test
    void lostResponseQueriesOriginalAttemptAndUsesFrozenAmountAndTax() {
        Sale sale = fixed();
        AtomicInteger charges = new AtomicInteger(),
                queries = new AtomicInteger(),
                prints = new AtomicInteger();
        AtomicReference<PaymentRequest> accepted = new AtomicReference<>();
        AtomicLong printedDue = new AtomicLong(), printedTax = new AtomicLong();
        PaymentGateway gateway =
                new PaymentGateway() {
                    public PaymentReceipt charge(PaymentRequest r) {
                        charges.incrementAndGet();
                        accepted.set(r);
                        throw new RuntimeException("accepted but response lost");
                    }

                    public PaymentReceipt query(PaymentRequest r) {
                        queries.incrementAndGet();
                        assertEquals(accepted.get(), r);
                        return PaymentReceipt.success(r, "platform-001");
                    }
                };
        CheckoutController c =
                controller(
                        sale,
                        gateway,
                        (s, tax) -> {
                            prints.incrementAndGet();
                            printedDue.set(s.paymentRequest().amountCents());
                            printedTax.set(tax);
                        });
        CheckoutCompletion done = new CheckoutCompletion(c, Runnable::run);
        c.onSettlePressed(done);
        assertEquals(SaleStatus.PENDING, sale.getStatus());
        assertEquals(4400, accepted.get().amountCents());
        assertThrows(IllegalStateException.class, () -> sale.addLineItem(new Item("X", 100), 1));
        TaxPolicyConfigHelper.update(13);
        try {
            c.onSettlePressed(done);
        } finally {
            TaxPolicyConfigHelper.update(3);
        }
        assertEquals(SaleStatus.SETTLED, sale.getStatus());
        assertEquals(1, charges.get());
        assertEquals(1, queries.get());
        assertEquals(4400, printedDue.get());
        assertEquals(128, printedTax.get());
        assertEquals(1, prints.get());
        done.onSuccess(sale);
        c.onSettlePressed(done);
        assertEquals(1, prints.get());
        assertEquals(1, charges.get());
    }

    @Test
    void explicitRejectionDoesNotSettleAndNewAttemptKeepsOrderIdentity() {
        Sale sale = fixed();
        AtomicReference<PaymentRequest> rejected = new AtomicReference<>();
        AtomicInteger prints = new AtomicInteger();
        CheckoutController c =
                controller(
                        sale,
                        r -> {
                            rejected.set(r);
                            return PaymentReceipt.rejected(r, "insufficient funds");
                        },
                        (s, t) -> prints.incrementAndGet());
        c.onSettlePressed(new CheckoutCompletion(c, Runnable::run));
        assertEquals(SaleStatus.OPEN, sale.getStatus());
        assertEquals(0, prints.get());
        assertNull(sale.paymentRequest());
        CheckoutController retry =
                controller(
                        sale,
                        r -> {
                            assertEquals(rejected.get().orderId(), r.orderId());
                            assertNotEquals(rejected.get().attemptId(), r.attemptId());
                            return PaymentReceipt.success(r, "second-attempt");
                        },
                        (s, t) -> prints.incrementAndGet());
        retry.onSettlePressed(new CheckoutCompletion(retry, Runnable::run));
        assertEquals(1, prints.get());
    }

    @Test
    void mismatchedReceiptCannotConfirmPayment() {
        Sale sale = fixed();
        AtomicInteger prints = new AtomicInteger();
        CheckoutController c =
                controller(
                        sale,
                        r ->
                                PaymentReceipt.success(
                                        new PaymentRequest(r.orderId(), r.attemptId(), 4900, 143),
                                        "wrong-amount"),
                        (s, t) -> prints.incrementAndGet());
        c.onSettlePressed(new CheckoutCompletion(c, Runnable::run));
        assertEquals(SaleStatus.PENDING, sale.getStatus());
        assertEquals(0, prints.get());
    }

    @Test
    void concurrentDuplicateSuccessOnlyPrintsOnce() throws Exception {
        Sale sale = fixed();
        sale.beginPayment();
        AtomicInteger prints = new AtomicInteger();
        CheckoutController c =
                controller(
                        sale,
                        r -> PaymentReceipt.success(r, "unused"),
                        (s, t) -> prints.incrementAndGet());
        CheckoutCompletion done = new CheckoutCompletion(c, Runnable::run);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture.allOf(
                            CompletableFuture.runAsync(() -> done.onSuccess(sale), pool),
                            CompletableFuture.runAsync(() -> done.onSuccess(sale), pool))
                    .get(3, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, prints.get());
        assertEquals(SaleStatus.SETTLED, sale.getStatus());
    }

    @Test
    void printerFailureDoesNotUndoSuccessfulPayment() {
        Sale sale = fixed();
        CheckoutController c =
                controller(
                        sale,
                        r -> PaymentReceipt.success(r, "paid"),
                        (s, t) -> {
                            throw new RuntimeException("printer uncertain");
                        });
        c.onSettlePressed(new CheckoutCompletion(c, Runnable::run));
        assertEquals(SaleStatus.SETTLED, sale.getStatus());
    }

    @Test
    void taxExtractionRejectsNegativeInputAndDoesNotOverflow() {
        TaxAdapter tax = new TaxAdapter();
        assertThrows(IllegalArgumentException.class, () -> tax.taxOf(-1));
        assertEquals(
                new java.math.BigDecimal(Long.MAX_VALUE)
                        .multiply(java.math.BigDecimal.valueOf(3))
                        .divide(
                                java.math.BigDecimal.valueOf(103),
                                0,
                                java.math.RoundingMode.HALF_UP)
                        .longValueExact(),
                tax.taxOf(Long.MAX_VALUE));
    }
}
