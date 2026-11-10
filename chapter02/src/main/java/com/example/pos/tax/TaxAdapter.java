package com.example.pos.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 教学模型：单一税率，输入已分摊优惠后的含税金额，提取所含税额，不再加收。 */
public class TaxAdapter {
    public int currentRatePercent() {
        return TaxPolicyConfig.currentRate();
    }

    public long taxOf(long grossCents) {
        if (grossCents < 0) throw new IllegalArgumentException("negative gross amount");
        int rate = currentRatePercent();
        return BigDecimal.valueOf(grossCents)
                .multiply(BigDecimal.valueOf(rate))
                .divide(BigDecimal.valueOf(100L + rate), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
