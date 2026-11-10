package com.example.pos.printer;

import java.util.Objects;

/**
 * 代码清单 1-2：小票记录对象（1.4.1 打印接口与设备适配）。
 * 沿用全书统一的金额约定：金额以整数分保存（long），避免浮点误差。
 */
public record Receipt(String saleId, long totalCents) {

    public Receipt {
        Objects.requireNonNull(saleId, "saleId must not be null");
        if (totalCents < 0) {
            throw new IllegalArgumentException("totalCents must be non-negative: " + totalCents);
        }
    }
}
