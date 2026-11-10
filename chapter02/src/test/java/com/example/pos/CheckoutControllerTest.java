package com.example.pos;

import com.example.pos.board.SaleBoard;
import com.example.pos.checkout.CheckoutCompletion;
import com.example.pos.checkout.CheckoutController;
import com.example.pos.checkout.PaymentReceipt;
import com.example.pos.checkout.PaymentService;
import com.example.pos.checkout.SettleButton;
import com.example.pos.checkout.SystemOperation;
import com.example.pos.sale.Register;
import com.example.pos.sale.Sale;
import com.example.pos.sale.SaleStatus;
import com.example.pos.tax.TaxAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.4、2.5.1、2.6：结算控制器、键位多态装配与异步支付回调测试（含幂等性验证）。
 */
class CheckoutControllerTest {

    @Test
    @DisplayName("异步支付成功：关闭小票、打印一次且重复回调具备幂等保护")
    void asyncCheckoutSuccessAndIdempotent() {
        Sale sale = new Register().newSale();
        AtomicInteger printCount = new AtomicInteger(0);
        AtomicInteger uiCount = new AtomicInteger(0);

        PaymentService paymentService =
                new PaymentService(
                        amount -> PaymentReceipt.success(amount, "TX-1001"),
                        Runnable::run // 同步执行器便于确定性断言
                        );
        CheckoutController controller =
                new CheckoutController(
                        sale,
                        new TaxAdapter(),
                        (s, tax) -> printCount.incrementAndGet(),
                        paymentService);
        CheckoutCompletion completion =
                new CheckoutCompletion(
                        controller,
                        task -> {
                            uiCount.incrementAndGet();
                            task.run();
                        });

        controller.onBarcodeScanned("A");
        controller.onBarcodeScanned("B");
        controller.onSettlePressed(completion);

        assertEquals(SaleStatus.SETTLED, sale.getStatus());
        assertEquals(1, printCount.get(), "支付成功触发一次打印");
        assertEquals(1, uiCount.get(), "UI提示触发一次");

        // 模拟网络重复投递同一笔成功通知：验证幂等保护
        completion.onSuccess(sale);
        assertEquals(1, printCount.get(), "重复回调不得重复打印小票");
    }

    @Test
    @DisplayName("支付结果待确认（Pending）：标记流水号且不关闭小票")
    void asyncCheckoutPendingMarksTradeNo() {
        Sale sale = new Register().newSale();
        PaymentService paymentService =
                new PaymentService(
                        amount -> PaymentReceipt.pending(amount, "TX-PENDING-99"), Runnable::run);
        CheckoutController controller =
                new CheckoutController(sale, new TaxAdapter(), (s, tax) -> {}, paymentService);
        CheckoutCompletion completion = new CheckoutCompletion(controller, Runnable::run);

        controller.onBarcodeScanned("A");
        controller.onSettlePressed(completion);

        assertEquals(SaleStatus.PENDING, sale.getStatus());
        assertEquals("TX-PENDING-99", sale.getPendingTradeNo());
    }

    @Test
    @DisplayName("空销售单发起结算抛出异常")
    void emptySaleCannotSettle() {
        Sale sale = new Register().newSale();
        PaymentService paymentService =
                new PaymentService(amount -> PaymentReceipt.success(amount, "TX-0"), Runnable::run);
        CheckoutController controller =
                new CheckoutController(sale, new TaxAdapter(), (s, tax) -> {}, paymentService);
        CheckoutCompletion completion = new CheckoutCompletion(controller, Runnable::run);

        assertThrows(IllegalStateException.class, () -> controller.onSettlePressed(completion));
    }

    @Test
    @DisplayName("界面按钮通过EventSink解耦，键盘按键通过Action多态替换")
    void buttonAndEventSinkDecoupling() {
        AtomicReference<SystemOperation> receivedOp = new AtomicReference<>();
        SettleButton settleButton = new SettleButton();
        settleButton.setSink((op, payload) -> receivedOp.set(op));
        settleButton.fireActionPerformed();
        assertEquals(SystemOperation.SETTLE, receivedOp.get());

        SaleBoard board = SaleBoard.createDefaultConfiguredBoard();
        AtomicInteger customActionCount = new AtomicInteger(0);
        board.getButton("M").setAction(customActionCount::incrementAndGet);
        board.getButton("M").onClick();
        assertTrue(customActionCount.get() == 1);
    }
}
