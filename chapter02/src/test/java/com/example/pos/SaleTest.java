package com.example.pos;

import com.example.pos.sale.Item;
import com.example.pos.sale.Sale;
import com.example.pos.sale.SaleLineItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 代码清单2-4：小票职责分配的断言（2.2.1、2.2.3）。
 * 固定数据沿用章首的4900分小票：商品A 1250分×2，商品B 800分×3。
 */
class SaleTest {

    private static Sale fixedReceipt() {
        Sale sale = new Sale();
        sale.addLineItem(new Item("A", 1250L), 2);
        sale.addLineItem(new Item("B", 800L), 3);
        return sale;
    }

    @Test
    @DisplayName("行小计与整单合计与章首4900分小票一致")
    void subtotalAndTotalMatchTheFixedReceipt() {
        Sale sale = fixedReceipt();
        List<SaleLineItem> lines = sale.getLineItems();

        assertEquals(2500L, lines.get(0).subtotalCents(), "A行：1250 × 2");
        assertEquals(2400L, lines.get(1).subtotalCents(), "B行：800 × 3");
        assertEquals(4900L, sale.totalCents(), "合计：2500 + 2400");
        assertEquals(4400L, sale.amountDueCents(), "整单满4000分减500分");
    }

    @Test
    @DisplayName("成交单价是快照，调价后已开的小票不变")
    void priceSnapshotSurvivesLaterPriceChange() {
        Sale sale = fixedReceipt();
        Item itemA = sale.getLineItems().get(0).getItem();

        itemA.changePrice(1500L); // 商品档案调价

        assertEquals(2500L, sale.getLineItems().get(0).subtotalCents(), "已成交的小票仍按成交价快照计算");
    }

    @Test
    @DisplayName("空销售单的合计为 0")
    void emptySaleTotalsZero() {
        assertEquals(0L, new Sale().totalCents());
        assertEquals(0L, new Sale().amountDueCents(), "空单不触发整单优惠");
    }

    @Test
    @DisplayName("非法数量在创建流水行时被拒绝")
    void invalidQuantityRejectedAtCreation() {
        assertThrows(
                IllegalArgumentException.class, () -> new SaleLineItem(new Item("A", 1250L), 0));
    }

    @Test
    @DisplayName("价格为负在商品入口被拒绝")
    void negativePriceRejected() {
        Item item = new Item("A", 1250L);
        assertThrows(IllegalArgumentException.class, () -> item.changePrice(-1L));
    }
}
