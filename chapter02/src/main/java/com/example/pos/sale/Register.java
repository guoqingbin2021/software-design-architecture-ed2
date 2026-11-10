package com.example.pos.sale;

/**
 * 代码清单2-6：创建职责的分配链（正例·Register部分，2.2.2）。
 * 收银机记录当班销售单，负责创建 Sale，不越级创建 SaleLineItem。
 */
public class Register {

    public Sale newSale() {
        return new Sale(); // 小票由收银机创建
    }
}
