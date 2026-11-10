package com.example.pos.sale;

/**
 * 代码清单 1-3：销售明细行（1.4.1 计价职责与优惠规则，图 1.6）。
 * <p>
 * 信息专家原则正例：掌握成交单价（分）与购买数量，负责独立计算本行小计；
 * 使用 {@link Math#multiplyExact(long, int)} 防范金额计算溢出。
 */
public record SaleLineItem(long unitPriceCents, int quantity) {

    public SaleLineItem {
        if (unitPriceCents < 0 || quantity <= 0) {
            throw new IllegalArgumentException(
                    "invalid line: unitPriceCents=" + unitPriceCents + ", quantity=" + quantity);
        }
    }

    /**
     * 计算本行小计（整数分）。
     */
    public long subtotalCents() {
        return Math.multiplyExact(unitPriceCents, quantity);
    }
}
