package com.example.pos;

import com.example.pos.printer.Checkout;
import com.example.pos.printer.FakePrinterA;
import com.example.pos.printer.FakePrinterB;
import com.example.pos.printer.Receipt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 代码清单 1-2 及表 1-8 自动化回归测试：
 * 验证假设备替换、补打与出纸前断连等用例。
 */
class CheckoutPrinterTest {

    @Test
    @DisplayName("假设备A正常提交与出纸：成交计数为1，打印计数为1")
    void normalSubmitWithPrinterA() {
        Receipt r = new Receipt("S01-P01-0001", 4900L);
        FakePrinterA a = new FakePrinterA();
        Checkout checkout = new Checkout(a);

        checkout.submit(r);

        assertEquals(1, checkout.getCommits(), "成交计数应为1");
        assertEquals(1, a.getOutputs(), "打印输出计数应为1");
        assertFalse(checkout.getPendingPrint().contains(r.saleId()), "不应处于待打印状态");
    }

    @Test
    @DisplayName("假设备B替换验证：高层业务逻辑无需改动，输出计数与成交计数一致")
    void normalSubmitWithPrinterB() {
        Receipt r = new Receipt("S01-P01-0001", 4900L);
        FakePrinterB b = new FakePrinterB();
        Checkout checkout = new Checkout(b);

        checkout.submit(r);

        assertEquals(1, checkout.getCommits(), "成交计数应为1");
        assertEquals(1, b.getOutputs(), "假设备B输出计数应为1");
    }

    @Test
    @DisplayName("缺纸故障隔离与受控补打：缺纸不丢成交，补打不增流水，重试具备幂等性")
    void paperOutFaultAndControlledReprint() {
        FakePrinterA a = new FakePrinterA();
        Checkout checkout = new Checkout(a);

        // 注入缺纸故障
        a.setFailNext(true);
        Receipt r2 = new Receipt("S01-P01-0002", 4900L);
        checkout.submit(r2);

        // 验证：已完成成交提交，但打印未输出，进入待打印状态
        assertEquals(1, checkout.getCommits(), "交易已提交，流水不丢失");
        assertEquals(0, a.getOutputs(), "打印机缺纸，未完成物理出纸");
        assertTrue(checkout.getPendingPrint().contains(r2.saleId()), "小票应标记为待打印");

        // 模拟换纸后受控补打
        checkout.reprint(r2.saleId());
        assertEquals(1, checkout.getCommits(), "补打小票不得产生二次成交提交");
        assertEquals(1, a.getOutputs(), "补打成功后打印计数增加");
        assertFalse(checkout.getPendingPrint().contains(r2.saleId()), "补打完成后移除待打印标记");

        // 模拟外部因未收到回执重复重试提交：幂等保护
        checkout.submit(r2);
        assertEquals(1, checkout.getCommits(), "重复提交相同单号不应重复记账");
        assertEquals(1, a.getOutputs(), "已打印完成的流水不重复打纸");
    }

    @Test
    @DisplayName("出纸前断连与重试：流水记录保留，重试不重复提交")
    void disconnectBeforeOutputAndRetry() {
        FakePrinterA disconnected = new FakePrinterA();
        disconnected.setDisconnectNext(true);
        Checkout checkout = new Checkout(disconnected);

        Receipt r3 = new Receipt("S01-P01-0003", 4900L);
        checkout.submit(r3);

        assertEquals(1, checkout.getCommits(), "断连前已保存内存成交记录");
        assertEquals(0, disconnected.getOutputs(), "出纸前断连，物理出纸为0");
        assertEquals(r3, checkout.getCommitted().get(r3.saleId()));
        assertTrue(checkout.getPendingPrint().contains(r3.saleId()));

        // 重复请求重试
        checkout.submit(r3);
        assertEquals(1, checkout.getCommits(), "再次收到重试请求，仍按同一标识返回，流水不重复");
        assertEquals(0, disconnected.getOutputs());
    }

    @Test
    @DisplayName("相同交易标识但内容不一致时抛出冲突异常")
    void conflictSaleIdThrowsException() {
        FakePrinterB b = new FakePrinterB();
        Checkout checkout = new Checkout(b);

        Receipt r1 = new Receipt("S01-P01-0001", 4900L);
        checkout.submit(r1);

        Receipt conflict = new Receipt("S01-P01-0001", 5000L);
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> checkout.submit(conflict));
        assertEquals("id conflict", ex.getMessage());
    }

    @Test
    @DisplayName("补打不存在的小票抛出异常")
    void reprintNonExistentReceiptThrowsException() {
        FakePrinterB b = new FakePrinterB();
        Checkout checkout = new Checkout(b);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> checkout.reprint("NOT-EXISTS"));
        assertEquals("sale not found", ex.getMessage());
    }
}
