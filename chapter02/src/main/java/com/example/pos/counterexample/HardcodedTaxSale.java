package com.example.pos.counterexample;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 代码清单2-22：用标志位选择硬编码税率（反例，2.5.2）。
 * 正例见 com.example.pos.tax.TaxAdapter 与 com.example.pos.sale.Sale#taxCents(long)。
 */
@Deprecated
public class HardcodedTaxSale {

    public long taxCents(long grossCents, int rateFlag) {
        if (rateFlag == 1) {
            return includedTax(grossCents, 3); // 3% 税率
        } else if (rateFlag == 2) {
            return includedTax(grossCents, 6); // 6% 税率
        } else if (rateFlag == 3) {
            return includedTax(grossCents, 11); // 11% 税率
        }
        return 0L;
    }

    private long includedTax(long grossCents, int rate) {
        return BigDecimal.valueOf(grossCents)
                .multiply(BigDecimal.valueOf(rate))
                .divide(BigDecimal.valueOf(100L + rate), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
