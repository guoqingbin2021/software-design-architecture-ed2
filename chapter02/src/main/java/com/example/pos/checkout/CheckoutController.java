package com.example.pos.checkout;

import com.example.pos.sale.Item;
import com.example.pos.sale.ItemCatalog;
import com.example.pos.sale.Sale;
import com.example.pos.tax.TaxAdapter;

/**
 * 代码清单2-17：结算用例的控制器（正例·片段，2.4.2）。
 * 承接结算场景下的系统事件，只负责接收事件、检查用例前置条件并按序协调调用，不亲自执行业务计算。
 */
public class CheckoutController {

    private final Sale sale;
    private final TaxAdapter taxAdapter;
    private final Printer printer;
    private final PaymentService paymentService;

    public CheckoutController(
            Sale sale, TaxAdapter taxAdapter, Printer printer, PaymentService paymentService) {
        this.sale = sale;
        this.taxAdapter = taxAdapter;
        this.printer = printer;
        this.paymentService = paymentService;
    }

    /** 事件入口：条码枪扫入商品条码 */
    public void onBarcodeScanned(String barcode) {
        Item item = ItemCatalog.lookup(barcode);
        sale.addLineItem(item, 1);
    }

    /** 事件入口重载：直接传入商品与数量 */
    public void onBarcodeScanned(Item item, int quantity) {
        sale.addLineItem(item, quantity);
    }

    /** 事件入口：收银员按下结算键，发起异步支付 */
    public void onSettlePressed(PaymentCallback callback) {
        if (!sale.hasItems()) {
            throw new IllegalStateException("销售单为空，不能发起结算");
        }
        paymentService.payAsync(sale, callback); // 支付交由支付服务异步执行
    }

    /** 支付确认完成后的协调入口：计税并驱动打印小票 */
    public void onPaid(long taxCents) {
        printer.printReceipt(sale, taxCents); // 打印交由打印对象执行
    }

    public long calculateTaxCents() {
        return sale.paymentRequest() == null
                ? taxAdapter.taxOf(sale.amountDueCents())
                : sale.paymentRequest().taxCents(); // 总额归小票，计税归计税对象
    }
}
