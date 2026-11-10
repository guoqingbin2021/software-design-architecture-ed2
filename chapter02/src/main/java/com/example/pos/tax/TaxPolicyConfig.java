package com.example.pos.tax;

/**
 * 税率配置源（2.5.2，代码清单2-23）。
 * 真实系统通过配置中心下发并按生效时间管理，不在业务代码里硬编码。
 */
public final class TaxPolicyConfig {

    private static volatile int currentRate = 3; // 百分制税率

    private TaxPolicyConfig() {}

    public static int currentRate() {
        return currentRate;
    }

    static void updateRate(int ratePercent) {
        if (ratePercent < 0 || ratePercent > 100) {
            throw new IllegalArgumentException("税率越界：" + ratePercent);
        }
        currentRate = ratePercent;
    }
}
