package com.example.pos.checkout;

import com.example.pos.sale.Sale;

/** 付款事实和打印结果分别处理；UI更新交给注入的界面线程执行器。 */
public class CheckoutCompletion implements PaymentCallback {
    private final CheckoutController controller;
    private final UiExecutor uiExecutor;

    public CheckoutCompletion(CheckoutController controller, UiExecutor uiExecutor) {
        this.controller = controller;
        this.uiExecutor = uiExecutor;
    }

    public CheckoutCompletion(UiExecutor uiExecutor) {
        this(null, uiExecutor);
    }

    public void onSuccess(Sale sale) {
        if (!sale.completePayment()) return; // 同一内存对象的原子状态转移
        String message = "支付成功，应付：" + sale.paymentRequest().amountCents() + "分";
        try {
            if (controller != null) controller.onPaid(sale.paymentRequest().taxCents());
        } catch (RuntimeException printError) {
            // 不回滚已成功的付款；设备是否出纸须另行核实。
            message += "；打印结果待核实";
        }
        final String result = message;
        uiExecutor.execute(() -> showMessage(result));
    }

    public void onFailure(Sale sale, String reason) {
        sale.rejectPayment();
        uiExecutor.execute(() -> showMessage("支付已明确拒绝：" + reason));
    }

    public void onPending(Sale sale, String tradeNo) {
        sale.markPending(tradeNo);
        uiExecutor.execute(() -> showMessage("支付结果待确认，请查询原付款，请勿重新扣款"));
    }

    private void showMessage(String message) {
        System.out.println(message);
    }
}
