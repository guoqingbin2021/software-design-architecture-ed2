package com.example.pos.printer;

/**
 * 代码清单 1-2：假设备A（1.4.1 打印接口与设备适配，表 1-8）。
 * <p>
 * 支持注入“缺纸”（paper out）与“出纸前断连”（disconnected before output）两种物理故障，
 * 用于验证结算类的故障隔离与受控补打能力。
 */
public class FakePrinterA implements ReceiptPrinter {

    private int outputs;
    private boolean failNext;
    private boolean disconnectNext;

    @Override
    public void print(Receipt r) {
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("paper out");
        }
        if (disconnectNext) {
            disconnectNext = false;
            throw new RuntimeException("disconnected before output");
        }
        outputs++;
    }

    public int getOutputs() {
        return outputs;
    }

    public boolean isFailNext() {
        return failNext;
    }

    public void setFailNext(boolean failNext) {
        this.failNext = failNext;
    }

    public boolean isDisconnectNext() {
        return disconnectNext;
    }

    public void setDisconnectNext(boolean disconnectNext) {
        this.disconnectNext = disconnectNext;
    }
}
