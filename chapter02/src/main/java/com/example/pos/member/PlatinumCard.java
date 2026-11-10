package com.example.pos.member;

public class PlatinumCard extends MemberCard {

    @Override
    public int discountPercent() {
        return 90; // 铂金卡9折
    }
}
