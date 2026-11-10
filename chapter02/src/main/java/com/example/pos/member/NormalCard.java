package com.example.pos.member;

public class NormalCard extends MemberCard {

    @Override
    public int discountPercent() {
        return 95; // 普通卡9.5折
    }
}
