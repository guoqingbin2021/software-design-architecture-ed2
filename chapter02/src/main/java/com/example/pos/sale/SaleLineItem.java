package com.example.pos.sale;

import java.util.Objects;

/**
 * 代码清单2-2：正式类SaleLineItem的成交价快照实现（2.1.1、2.2.1）。
 * 掌握购买数量与成交单价快照，负责计算本行小计（信息专家原则）。
 */
public class SaleLineItem {

    private final Item item;
    private final int quantity;
    private final long dealUnitPriceCents; // 保存成交当时的单价快照，调价后旧小票仍可复现

    public SaleLineItem(Item item, int quantity) {
        this.item = Objects.requireNonNull(item, "商品不能为空");
        if (quantity <= 0) {
            throw new IllegalArgumentException("购买数量必须为正整数：" + quantity);
        }
        this.quantity = quantity;
        this.dealUnitPriceCents = item.getUnitPriceCents();
    }

    public Item getItem() {
        return item;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getDealUnitPriceCents() {
        return dealUnitPriceCents;
    }

    /** 行小计：所需信息（成交单价与数量）由本对象直接持有。 */
    public long subtotalCents() {
        return Math.multiplyExact(dealUnitPriceCents, quantity);
    }
}
