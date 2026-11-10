package com.example.pos.counterexample;

/** 代码2-9的调用端反例：调用者必须掌握标志位与税率分支的对应。 */
public final class SaleTaxSample {
    public long calculateTaxByFlag(HardcodedTaxSale sale, long grossCents, int rateFlag) {
        return sale.taxCents(grossCents, rateFlag);
    }
}
