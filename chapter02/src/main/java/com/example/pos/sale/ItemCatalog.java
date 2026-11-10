package com.example.pos.sale;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 商品档案查询服务（2.2.2、2.4.2）。
 * 提供受控的只读查询与商品登记入口，避免外部直接修改内部Map（对比代码清单2-10反例）。
 */
public final class ItemCatalog {

    private static final Map<String, Item> ITEMS = new ConcurrentHashMap<>();

    static {
        ITEMS.put("A", new Item("A", 1250L));
        ITEMS.put("B", new Item("B", 800L));
    }

    private ItemCatalog() {}

    public static void register(String barcode, Item item) {
        ITEMS.put(barcode, item);
    }

    public static Item lookup(String barcode) {
        Item item = ITEMS.get(barcode);
        if (item == null) {
            throw new IllegalArgumentException("未找到条码对应的商品：" + barcode);
        }
        return item;
    }
}
