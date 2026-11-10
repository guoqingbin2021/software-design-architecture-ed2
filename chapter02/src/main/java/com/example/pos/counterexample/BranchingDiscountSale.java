package com.example.pos.counterexample;

/**
 * 代码清单2-20：按卡种分支的折扣计算（反例，2.5.1）。
 * 正例见 com.example.pos.member.MemberCard 与 com.example.pos.sale.Sale#finalPriceCents。
 */
@Deprecated
public class BranchingDiscountSale {

    public enum CardType {
        NORMAL,
        PLATINUM,
        GOLD,
        DIAMOND
    }

    public record LegacyCard(CardType type) {}

    public long finalPriceCents(LegacyCard card, long totalCents) {
        if (card.type() == CardType.NORMAL) {
            return totalCents * 95 / 100;
        } else if (card.type() == CardType.PLATINUM) {
            return totalCents * 90 / 100;
        } else if (card.type() == CardType.GOLD) {
            return totalCents * 85 / 100;
        } else if (card.type() == CardType.DIAMOND) {
            return totalCents * 80 / 100;
        }
        return totalCents;
    }
}
