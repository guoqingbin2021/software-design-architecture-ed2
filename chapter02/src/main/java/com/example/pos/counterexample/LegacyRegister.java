package com.example.pos.counterexample;

import com.example.pos.sale.Item;
import com.example.pos.sale.SaleLineItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码清单2-5：在Register中越级创建流水对象（反例，2.2.2）。
 * 正例见 com.example.pos.sale.Register 与 com.example.pos.sale.Sale#addLineItem(Item, int)。
 */
@Deprecated
public class LegacyRegister {

    private final List<SaleLineItem> exposedLines = new ArrayList<>();

    public void startSale(Item item, int quantity) {
        // 越级创建流水行：让 Register 直接感知了 SaleLineItem 的构造参数细节
        SaleLineItem line = new SaleLineItem(item, quantity);
        exposedLines.add(line);
    }
}
