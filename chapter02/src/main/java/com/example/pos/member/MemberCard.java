package com.example.pos.member;

/**
 * 代码清单2-21：会员折扣的多态实现（正例，2.5.1）。
 * 会员卡抽象基类：定义稳定的折扣契约。
 */
public abstract class MemberCard {

    /** 返回百分制折扣率（如 95 表示 9.5折），由各具体卡种自行实现 */
    public abstract int discountPercent();
}
