package com.example.pos;

import com.example.pos.tax.TaxAdapter;
import com.example.pos.tax.TaxPolicyConfigHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 2.5.2：税费计算的中间对象。税率来源变化不影响调用方。
 */
class TaxAdapterTest {

    @Test
    @DisplayName("按当前税率计算税额")
    void taxFollowsCurrentRate() {
        long tax = new TaxAdapter().taxOf(4900L);
        assertEquals(143L, tax); // 4900×3/103，按分四舍五入
    }

    @Test
    @DisplayName("税率越界在配置入口被拒绝")
    void rateOutOfRangeRejected() {
        assertThrows(IllegalArgumentException.class, () -> TaxPolicyConfigHelper.update(101));
    }
}
