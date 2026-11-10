package com.example.pos.demo;

import com.example.pos.sale.Sale;
import com.example.pos.sale.SaleLineItem;

import java.util.List;

/**
 * 代码清单 1-3：工程化演示入口（1.4.1 计价职责与优惠规则）。
 * 运行：java -ea com.example.pos.demo.AmountDemo
 */
public class AmountDemo {

    public static void main(String[] args) {
        Sale sale = new Sale(List.of(new SaleLineItem(1250L, 2), new SaleLineItem(800L, 3)));
        assert sale.lines().get(0).subtotalCents() == 2500L;
        assert sale.lines().get(1).subtotalCents() == 2400L;
        assert sale.totalCents() == 4900L;
        assert sale.amountDueCents() == 4400L;
        Sale repricedSale =
                new Sale(List.of(new SaleLineItem(1250L, 2), new SaleLineItem(900L, 3)));
        assert repricedSale.totalCents() == 5200L;
        assert sale.totalCents() == 4900L; // 新单调价不改变原快照。
        assert new Sale(List.of()).totalCents() == 0L;
        System.out.println("49.00, 52.00 and 44.00 yuan: PASS");
    }
}
