package com.example.pos;

import com.example.pos.member.MemberCardFactory;
import com.example.pos.sale.Item;
import com.example.pos.sale.Sale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 2.5.1：会员折扣的多态实现。
 * 同一笔 4900 分订单，五种卡折后金额各不相同，Sale 代码不变。
 */
class MemberCardTest {

    private static Sale fixedReceipt() {
        Sale sale = new Sale();
        sale.addLineItem(new Item("A", 1250L), 2);
        sale.addLineItem(new Item("B", 800L), 3);
        return sale;
    }

    @Test
    @DisplayName("五种卡对同一笔订单的折后金额")
    void discountDiffersByCardType() {
        Sale sale = fixedReceipt();
        long total = sale.totalCents();

        assertEquals(4655L, sale.finalPriceCents(MemberCardFactory.of("NORMAL"), total));
        assertEquals(4508L, sale.finalPriceCents(MemberCardFactory.of("SILVER"), total));
        assertEquals(4410L, sale.finalPriceCents(MemberCardFactory.of("PLATINUM"), total));
        assertEquals(4165L, sale.finalPriceCents(MemberCardFactory.of("GOLD"), total));
        assertEquals(3920L, sale.finalPriceCents(MemberCardFactory.of("DIAMOND"), total));
    }

    @Test
    @DisplayName("新增卡种只影响工厂，不触及Sale")
    void addingCardTypeTouchesOnlyFactory() {
        Sale sale = fixedReceipt();
        // SilverCard 是后加的卡种；Sale 的 finalPriceCents 未做任何修改
        assertEquals(4508L, sale.finalPriceCents(MemberCardFactory.of("SILVER"), 4900L));
    }
}
