package com.example.pos.printer;

/**
 * 代码清单 1-2：打印接口（1.4.1 打印接口与设备适配，图 1.7）。
 * <p>
 * 依赖倒置原则正例：高层结算业务（Checkout）与具体驱动适配器共同依赖此稳定契约。
 * 业务只需描述“要打印什么”，装配环境决定接入哪种打印机型号。
 */
public interface ReceiptPrinter {

    /**
     * 打印一张已确定成交的小票。
     *
     * @param receipt 已完成持久化提交的小票对象
     * @throws RuntimeException 当设备发生缺纸、断连等异常时抛出
     */
    void print(Receipt receipt);
}
