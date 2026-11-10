package com.example.pos.printer;

/**
 * 代码清单 1-2：假设备B（1.4.1 打印接口与设备适配，表 1-8）。
 * <p>
 * 另一款打印机实现替身，用于验证高层结算无需修改即可无缝替换底层输出设备。
 */
public class FakePrinterB implements ReceiptPrinter {

    private int outputs;

    @Override
    public void print(Receipt r) {
        outputs++;
    }

    public int getOutputs() {
        return outputs;
    }
}
