package com.example.pos.member;

public class GoldCard extends MemberCard {

    @Override
    public int discountPercent() {
        return 85; // 黄金卡8.5折
    }
}
