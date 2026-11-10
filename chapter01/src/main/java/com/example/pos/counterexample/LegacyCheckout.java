package com.example.pos.counterexample;

import com.example.pos.printer.Receipt;

/**
 * 代码清单 1-1：直接依赖具体打印机的旧实现（反例，1.4.1 节）。
 * <p>
 * 缺陷诊断：
 * 1. 强耦合具体硬件驱动：Checkout 直接 new PrinterADriver()，更换型号时必须侵入修改核心结算逻辑；
 * 2. 职责混杂：同时承担交易提交、驱动创建与异常恢复三种不同变化原因的职责；
 * 3. 错误重试引发重复提交：在捕获打印异常后直接调用 settleAgain(r)，导致已完成持久化提交的同一笔交易被二次提交，破坏幂等性。
 * <p>
 * 正例设计参见：
 * - 依赖倒置与打印接口：{@link com.example.pos.printer.ReceiptPrinter} 与 {@link com.example.pos.printer.Checkout}
 */
@Deprecated
public class LegacyCheckout {

    /**
     * 门店流水存储接口。
     */
    public interface Store {
        void commit(Receipt r);

        int getCommitCount();
    }

    /**
     * 具体硬件驱动：A型专用小票打印机驱动。
     */
    public static class PrinterADriver {
        private final boolean simulatePaperOut;

        public PrinterADriver() {
            this(false);
        }

        public PrinterADriver(boolean simulatePaperOut) {
            this.simulatePaperOut = simulatePaperOut;
        }

        public void print(Receipt r) {
            if (simulatePaperOut) {
                throw new RuntimeException("PrinterADriver: paper out");
            }
        }
    }

    private final Store store;
    private PrinterADriver driver;

    public LegacyCheckout(Store store) {
        this(store, new PrinterADriver(false));
    }

    public LegacyCheckout(Store store, PrinterADriver driver) {
        this.store = store;
        this.driver = driver;
    }

    /**
     * 代码清单 1-1 原始待诊断方法：
     * <pre>
     * void checkout(Receipt r) {
     *     store.commit(r);
     *     try {
     *         new PrinterADriver().print(r);
     *     } catch (RuntimeException ex) {
     *         settleAgain(r); // may submit the same sale again
     *     }
     * }
     * </pre>
     */
    public void checkout(Receipt r) {
        store.commit(r);
        try {
            driver.print(r);
        } catch (RuntimeException ex) {
            settleAgain(r); // may submit the same sale again
        }
    }

    public void settleAgain(Receipt r) {
        // 缺纸报错后重新进入结算流程，导致已成交交易再次提交（严重缺陷）
        checkout(r);
    }
}
