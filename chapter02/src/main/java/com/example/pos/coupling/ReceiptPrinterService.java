package com.example.pos.coupling;

/**
 * 代码清单2-8：数据耦合参数传递示例（2.3.1）。
 */
public class ReceiptPrinterService {

    public void printMemberSummary(ReceiptPrinter printer, String memberId, int points) {
        // 数据耦合：只传递所需的简单数据值，不传递完整的 Member 实体对象
        printer.print("会员号：" + memberId + "，当前积分：" + points);
    }
}
