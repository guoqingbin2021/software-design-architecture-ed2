package com.example.pos.printer;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 代码清单 1-2：结算业务协调类（1.4.1 打印接口与设备适配，图 1.7）。
 * <p>
 * 核心职责：
 * 1. 依赖倒置：仅通过抽象契约 {@link ReceiptPrinter} 协同打印，不绑定具体硬件驱动；
 * 2. 幂等防护：基于交易标识 saleId 校验重复提交，防范流水重放与主键冲突；
 * 3. 故障隔离与受控补打：打印异常时捕获并记入待打印集合 pendingPrint，补打仅输出小票而不重新提交流水。
 */
public class Checkout {

    private final ReceiptPrinter printer;
    private final Map<String, Receipt> committed = new HashMap<>();
    private final Set<String> pendingPrint = new HashSet<>();
    private int commits;

    public Checkout(ReceiptPrinter printer) {
        this.printer = Objects.requireNonNull(printer, "printer must not be null");
    }

    /**
     * 提交流水并尝试打印。
     * 若已存在相同交易标识：
     * - 若小票内容一致，视为网络重试或重复请求，幂等返回；
     * - 若小票内容不一致，视为标识冲突异常抛出。
     */
    public void submit(Receipt receipt) {
        Receipt old = committed.get(receipt.saleId());
        if (old != null) {
            if (!old.equals(receipt)) {
                throw new IllegalArgumentException("id conflict");
            }
            return;
        }
        committed.put(receipt.saleId(), receipt);
        commits++;
        printCommitted(receipt);
    }

    /**
     * 针对已提交流水调用打印机输出，若打印失败则进入待打印挂起集合。
     */
    public void printCommitted(Receipt receipt) {
        try {
            printer.print(receipt);
        } catch (RuntimeException ex) {
            pendingPrint.add(receipt.saleId());
            return;
        }
        pendingPrint.remove(receipt.saleId());
    }

    /**
     * 受控补打入口：按原有标识提取小票，只补打出纸，绝对不重新产生交易提交。
     */
    public void reprint(String id) {
        Receipt receipt = committed.get(id);
        if (receipt == null) {
            throw new IllegalArgumentException("sale not found");
        }
        printCommitted(receipt);
    }

    public int getCommits() {
        return commits;
    }

    public Map<String, Receipt> getCommitted() {
        return Collections.unmodifiableMap(committed);
    }

    public Set<String> getPendingPrint() {
        return Collections.unmodifiableSet(pendingPrint);
    }
}
