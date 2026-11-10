package com.example.pos.sale;

import java.util.List;

/**
 * 代码清单 1-3：销售单（1.4.1 计价职责与优惠规则，图 1.6）。
 * <p>
 * 聚合明细集合，委托明细行计算小计并累计商品合计；
 * 使用 {@link Math#addExact(long, long)} 防范加法溢出。
 */
public record Sale(List<SaleLineItem> lines) {

    public Sale {
        lines = List.copyOf(lines);
    }

    /**
     * 商品合计（分）：各行小计之和。
     */
    public long totalCents() {
        long total = 0L;
        for (SaleLineItem line : lines) {
            total = Math.addExact(total, line.subtotalCents());
        }
        return total;
    }

    /**
     * 应付金额（分）：扣除整单优惠后的实收金额（代码清单1-3规则：满4000分减500分）。
     */
    public long amountDueCents() {
        return PricingPolicy.amountDueCents(this);
    }
}
