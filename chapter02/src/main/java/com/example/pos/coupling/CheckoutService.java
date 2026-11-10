package com.example.pos.coupling;

import java.util.Objects;

/**
 * 代码清单2-7：第一章代码清单1-2中的打印契约与注入（节选，2.3.1）。
 */
public final class CheckoutService {

    private final ReceiptPrinter printer; // 仅依赖稳定接口

    public CheckoutService(ReceiptPrinter printer) {
        this.printer = Objects.requireNonNull(printer); // 具体实现由外部注入
    }

    public void printReceiptText(String text) {
        printer.print(text);
    }
}
