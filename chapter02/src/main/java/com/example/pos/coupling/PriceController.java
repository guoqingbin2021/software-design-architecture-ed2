package com.example.pos.coupling;

import com.example.pos.sale.Item;

import java.util.Map;

/**
 * 代码清单2-12：通过接口修改并保留校验（正例，2.3.1）。
 */
public class PriceController {

    public void adjustPrice(Map<Item, Long> items) {
        for (Item item : items.keySet()) {
            Long newPriceCents = items.get(item);
            // 正例：通过商品自身的方法修改价格，负数与越界由商品对象拒绝
            item.changePrice(newPriceCents);
        }
    }
}
