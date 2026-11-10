package com.example.pos.counterexample;

import com.example.pos.sale.SaleLineItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码清单2-3：小票合计的越级计算（反例，2.1.1）。
 * 正例见 com.example.pos.sale.Sale#totalCents()。
 */
@Deprecated
public class LegacySale {

    private final List<SaleLineItem> lineItems = new ArrayList<>();

    public long totalCents() {
        long total = 0L;
        for (SaleLineItem line : lineItems) {
            // 越级：Sale 越过流水行，直接向商品取单价并重复实现行内乘法
            long price = line.getItem().getUnitPriceCents();
            total += price * line.getQuantity();
        }
        return total;
    }
}
