package com.example.pos.counterexample;

import com.example.pos.sale.Sale;
import com.example.pos.sale.SaleLineItem;

/**
 * 代码清单2-13：承担四种变化原因的结算控制器（反例，2.3.2）。
 * 正例见 com.example.pos.checkout.CheckoutController。
 */
@Deprecated
public class GodCheckoutController {

    public void settle(Sale sale) {
        long totalCents = 0L;
        for (SaleLineItem line : sale.getLineItems()) {
            totalCents += line.getItem().getUnitPriceCents() * line.getQuantity();
        }
        printReceipt(sale, totalCents);
        updateStock(sale);
        notifyCustomer(sale);
    }

    private void printReceipt(Sale sale, long totalCents) {
        /* 拼接小票排版格式并驱动硬件 */
    }

    private void updateStock(Sale sale) {
        /* 直接拼接并执行库存扣减SQL */
    }

    private void notifyCustomer(Sale sale) {
        /* 调用第三方短信网关发送通知 */
    }
}
