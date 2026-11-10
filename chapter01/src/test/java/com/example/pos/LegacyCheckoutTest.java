package com.example.pos;

import com.example.pos.counterexample.LegacyCheckout;
import com.example.pos.printer.Receipt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 代码清单 1-1 反例缺陷复现与诊断测试：
 * 验证旧版实现将交易持久化、驱动创建与异常重试耦合在一起所带来的“重复提交”严重缺陷。
 */
class LegacyCheckoutTest {

    static class MockStore implements LegacyCheckout.Store {
        final List<Receipt> committedReceipts = new ArrayList<>();

        @Override
        public void commit(Receipt r) {
            committedReceipts.add(r);
        }

        @Override
        public int getCommitCount() {
            return committedReceipts.size();
        }
    }

    @Test
    @DisplayName("诊断代码清单1-1缺陷：打印失败时调用settleAgain导致重复commit")
    void demonstrateDuplicateCommitBugInLegacyCheckout() {
        MockStore store = new MockStore();
        // 构造注入缺纸报错的驱动
        LegacyCheckout.PrinterADriver faultyDriver =
                new LegacyCheckout.PrinterADriver(true) {
                    private int callCount = 0;

                    @Override
                    public void print(Receipt r) {
                        callCount++;
                        if (callCount == 1) {
                            throw new RuntimeException("PrinterADriver: paper out");
                        }
                        // 第二次模拟重试成功
                    }
                };

        LegacyCheckout legacyCheckout = new LegacyCheckout(store, faultyDriver);
        Receipt receipt = new Receipt("S01-P01-0001", 4900L);

        // 执行旧版结算
        legacyCheckout.checkout(receipt);

        // 验证缺陷：由于打印失败直接进入 settleAgain(r)，导致同一张小票向存储执行了 2 次 commit！
        assertEquals(
                2, store.getCommitCount(), "【缺陷复现】旧版直接重试结算导致同一笔销售产生重复记账/重复流水（commitCount = 2）！");
        assertEquals("S01-P01-0001", store.committedReceipts.get(0).saleId());
        assertEquals("S01-P01-0001", store.committedReceipts.get(1).saleId());
    }
}
