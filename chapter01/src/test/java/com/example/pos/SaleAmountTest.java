package com.example.pos;

import com.example.pos.sale.PricingPolicy;
import com.example.pos.sale.Sale;
import com.example.pos.sale.SaleLineItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 代码清单 1-3 及表 1-1、表 1-9、表 1-10 自动化回归测试：
 * 验证行小计委托、商品合计、整单优惠满减、非法输入校验与防溢出保护。
 */
class SaleAmountTest {

    @Test
    @DisplayName("行小计与商品合计与章首49元固定小票严格一致")
    void subtotalAndTotalMatchTextbookExample() {
        // 商品A: 1250分×2，商品B: 800分×3
        SaleLineItem lineA = new SaleLineItem(1250L, 2);
        SaleLineItem lineB = new SaleLineItem(800L, 3);
        Sale sale = new Sale(List.of(lineA, lineB));

        assertEquals(2500L, lineA.subtotalCents(), "两件A商品小计：1250 × 2 = 2500分 (25.00元)");
        assertEquals(2400L, lineB.subtotalCents(), "三件B商品小计：800 × 3 = 2400分 (24.00元)");
        assertEquals(4900L, sale.totalCents(), "商品合计：2500 + 2400 = 4900分 (49.00元)");
        assertEquals(4400L, sale.amountDueCents(), "满4000减500整单优惠：4900 - 500 = 4400分 (44.00元)");
    }

    @Test
    @DisplayName("空销售单总额为0分，符合空单正常定义")
    void emptySaleTotalIsZero() {
        Sale emptySale = new Sale(List.of());
        assertEquals(0L, emptySale.totalCents());
        assertEquals(0L, emptySale.amountDueCents());
    }

    @Test
    @DisplayName("整单满减优惠门槛临界值测试（3999分与4000分）")
    void thresholdDiscountBoundary() {
        // 未达4000分门槛（3999分）：不减免
        Sale saleUnder = new Sale(List.of(new SaleLineItem(3999L, 1)));
        assertEquals(3999L, saleUnder.totalCents());
        assertEquals(3999L, saleUnder.amountDueCents(), "3999分未达4000分门槛，应付为3999分");

        // 恰好达4000分门槛：立减500分
        Sale saleAt = new Sale(List.of(new SaleLineItem(4000L, 1)));
        assertEquals(4000L, saleAt.totalCents());
        assertEquals(3500L, saleAt.amountDueCents(), "4000分达标，立减500分，应付为3500分");

        // 超过4000分门槛（例如4001分）：立减500分
        Sale saleAbove = new Sale(List.of(new SaleLineItem(4001L, 1)));
        assertEquals(4001L, saleAbove.totalCents());
        assertEquals(3501L, saleAbove.amountDueCents(), "4001分达标，立减500分，应付为3501分");
    }

    @Test
    @DisplayName("非法单价与数量校验：负单价或非正数量抛出异常")
    void invalidInputValidation() {
        assertThrows(IllegalArgumentException.class, () -> new SaleLineItem(-1L, 1), "负单价应显式拒绝");
        assertThrows(IllegalArgumentException.class, () -> new SaleLineItem(100L, 0), "零数量应显式拒绝");
        assertThrows(IllegalArgumentException.class, () -> new SaleLineItem(100L, -2), "负数量应显式拒绝");
    }

    @Test
    @DisplayName("防溢出测试：超过 long 最大值时由 Math.multiplyExact/addExact 抛出 ArithmeticException")
    void arithmeticOverflowProtection() {
        // 单行小计乘法溢出
        SaleLineItem overflowLine = new SaleLineItem(Long.MAX_VALUE, 2);
        assertThrows(ArithmeticException.class, overflowLine::subtotalCents, "乘法溢出应触发异常");

        // 整单合计加法溢出
        Sale overflowSale =
                new Sale(List.of(new SaleLineItem(Long.MAX_VALUE, 1), new SaleLineItem(1L, 1)));
        assertThrows(ArithmeticException.class, overflowSale::totalCents, "加法溢出应触发异常");
    }

    @Test
    @DisplayName("销售单明细集合具备防御性拷贝，不可从外部篡改")
    void immutableLinesList() {
        List<SaleLineItem> mutableList = new ArrayList<>();
        mutableList.add(new SaleLineItem(1250L, 1));
        Sale sale = new Sale(mutableList);

        // 外部修改原列表
        mutableList.add(new SaleLineItem(800L, 1));
        assertEquals(1, sale.lines().size(), "Sale 内部 lines 应为不可变快照");

        // 尝试直接修改内部列表抛出 UnsupportedOperationException
        assertThrows(
                UnsupportedOperationException.class,
                () -> sale.lines().add(new SaleLineItem(100L, 1)));
    }
}
