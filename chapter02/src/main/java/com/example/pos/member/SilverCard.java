package com.example.pos.member;

public class SilverCard extends MemberCard {

    @Override
    public int discountPercent() {
        return 92; // 银卡9.2折
    }
}
