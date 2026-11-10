package com.example.pos.member;

public class DiamondCard extends MemberCard {

    @Override
    public int discountPercent() {
        return 80; // 钻石卡8折
    }
}
