package com.example.pos.sale;

/**
 * 商品信息对象（2.1.1、2.3.1）。
 * 金额单位为整数分，字段与方法以 Cents 结尾。
 */
public class Item {

    private final String name;
    private long unitPriceCents;

    public Item(String name, long unitPriceCents) {
        this.name = name;
        changePrice(unitPriceCents);
    }

    public String getName() {
        return name;
    }

    public long getUnitPriceCents() {
        return unitPriceCents;
    }

    /**
     * 修改价格。合法性校验由商品对象自己负责（代码清单2-12正例调用此入口）。
     */
    public void changePrice(long newPriceCents) {
        if (newPriceCents < 0) {
            throw new IllegalArgumentException("价格不能为负：" + newPriceCents);
        }
        this.unitPriceCents = newPriceCents;
    }
}
