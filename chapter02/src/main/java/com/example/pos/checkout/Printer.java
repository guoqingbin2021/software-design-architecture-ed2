package com.example.pos.checkout;

import com.example.pos.sale.Sale;

/** 打印接口：稳定契约，具体驱动由实现类承担（受变化影响的一端）。 */
public interface Printer {

    void printReceipt(Sale sale, long taxCents);
}
