package com.example.pos.counterexample;

import com.example.pos.sale.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * 代码清单2-9、2-10、2-11：耦合反例合集（2.3.1）。
 * 含控制耦合（rateFlag）、公共耦合（全局可变Map）与内容耦合（绕过校验改字段）。
 */
@Deprecated
public class LegacyPriceController {

    /** 代码清单2-10：公共耦合全局可变状态反例 */
    public static final Map<String, Item> GLOBAL_MUTABLE_ALL = new HashMap<>();

    /** 代码清单2-9：控制耦合标志参数传递反例 */
    public long calculateTaxByFlag(HardcodedTaxSale sale, long totalCents, int rateFlag) {
        return sale.taxCents(totalCents, rateFlag);
    }

    /** 代码清单2-11：通过非正常入口修改内部字段（内容耦合反例） */
    public void adjustPrice(Map<Item, Long> items) {
        for (Item item : items.keySet()) {
            Long newPriceCents = items.get(item);
            // 反例示意：若 unitPriceCents 为非私有字段，直接 item.unitPriceCents = newPriceCents 将绕过校验
            throw new UnsupportedOperationException(
                    "反例示意：严禁绕过接口直接写字段，应改用 item.changePrice(" + newPriceCents + ")");
        }
    }
}
