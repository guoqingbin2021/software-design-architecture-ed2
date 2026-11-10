package com.example.pos.checkout;

import com.example.pos.sale.Sale;
import java.util.Objects;
import java.util.concurrent.*;

/** 异步支付调度。已发起的尝试再次操作时只查询，传输异常保留待确认。 */
public class PaymentService {
    private final PaymentGateway gateway;
    private final Executor executor;

    public PaymentService(PaymentGateway gateway) {
        this(gateway, ForkJoinPool.commonPool());
    }

    public PaymentService(PaymentGateway gateway, Executor executor) {
        this.gateway = Objects.requireNonNull(gateway);
        this.executor = Objects.requireNonNull(executor);
    }

    public void payAsync(Sale sale, PaymentCallback callback) {
        final PaymentRequest request;
        final boolean first;
        synchronized (sale) {
            if (sale.isClosed()) return;
            first = sale.paymentRequest() == null;
            request = first ? sale.beginPayment() : sale.paymentRequest();
        }
        CompletableFuture.supplyAsync(
                        () -> first ? gateway.charge(request) : gateway.query(request), executor)
                // 非Async回调可能由完成线程执行，也可能在登记时已完成而由登记线程执行。
                // 需要固定执行器时使用whenCompleteAsync；UI更新仍由UiExecutor调度。
                .whenComplete(
                        (receipt, ex) -> {
                            synchronized (sale) {
                                if (!request.equals(sale.paymentRequest()) || sale.isClosed())
                                    return;
                                if (ex != null
                                        || receipt == null
                                        || !request.equals(receipt.request())) {
                                    // 超时、空响应与不匹配回执均不能证明未扣款。
                                    callback.onPending(sale, request.attemptId());
                                } else {
                                    switch (receipt.status()) {
                                        case SUCCESS -> callback.onSuccess(sale);
                                        case REJECTED ->
                                                callback.onFailure(sale, receipt.message());
                                        case PENDING ->
                                                callback.onPending(
                                                        sale,
                                                        receipt.tradeNo() == null
                                                                ? request.attemptId()
                                                                : receipt.tradeNo());
                                    }
                                }
                            }
                        });
    }
}
