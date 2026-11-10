package com.example.pos.sale;

/**
 * 代码清单 1-3：整单优惠策略（1.4.1 计价职责与优惠规则，表 1-10）。
 * <p>
 * 规则：单笔销售单商品合计满 4000 分（40 元）立减 500 分（5 元）。
 */
public final class PricingPolicy {

    public static final long OFFER_THRESHOLD_CENTS = 4000L;
    public static final long OFFER_DISCOUNT_CENTS = 500L;

    private PricingPolicy() {}

    /**
     * 计算扣除整单优惠后的应付金额（分）。
     *
     * @param sale 销售单
     * @return 应付金额（分）
     */
    public static long amountDueCents(Sale sale) {
        long base = sale.totalCents();
        // One order-level offer: 5 yuan off if at least 40 yuan.
        long discount = base >= OFFER_THRESHOLD_CENTS ? OFFER_DISCOUNT_CENTS : 0L;
        return Math.subtractExact(base, discount);
    }
}
