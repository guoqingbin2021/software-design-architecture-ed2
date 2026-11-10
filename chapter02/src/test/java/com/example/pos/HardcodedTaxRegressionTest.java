package com.example.pos;

import com.example.pos.counterexample.HardcodedTaxSale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HardcodedTaxRegressionTest {
    @Test
    void hardcodedFlagsStillUseTheSameIncludedTaxBasis() {
        assertEquals(128, new HardcodedTaxSale().taxCents(4400, 1));
    }
}
