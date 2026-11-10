package com.example.pos.sale;

import com.example.pos.member.MemberCard;
import com.example.pos.checkout.PaymentRequest;
import java.util.UUID;
import com.example.pos.tax.TaxAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 销售单领域对象（代码清单2-6、2-21、2-24）。
 * 掌握明细行集合，负责汇总整单金额（信息专家原则）；
 * 与 SaleLineItem 为组合关系，负责创建明细行（创建者原则）。
 */
public class Sale {

    private final List<SaleLineItem> lineItems = new ArrayList<>();
    private final TaxAdapter taxAdapter;
    private SaleStatus status = SaleStatus.OPEN;
    private String pendingTradeNo;
    private final String orderId = UUID.randomUUID().toString();
    private PaymentRequest paymentRequest;

    public Sale() {
        this(new TaxAdapter());
    }

    /** 代码清单2-24：Sale 依赖 TaxAdapter 完成税费计算（间接原则）。 */
    public Sale(TaxAdapter taxAdapter) {
        this.taxAdapter = Objects.requireNonNull(taxAdapter, "计税适配器不能为空");
    }

    /** 代码清单2-6：流水行由小票创建（组合关系，流水行不能离开小票单独存在）。 */
    public synchronized void addLineItem(Item item, int quantity) {
        if (status != SaleStatus.OPEN || paymentRequest != null)
            throw new IllegalStateException("付款开始后不得修改单据");
        SaleLineItem line = new SaleLineItem(item, quantity);
        lineItems.add(line);
    }

    public List<SaleLineItem> getLineItems() {
        return List.copyOf(lineItems);
    }

    public boolean hasItems() {
        return !lineItems.isEmpty();
    }

    /** 合计：只汇总各行小计，不越级读取商品单价（信息专家原则）。 */
    public long totalCents() {
        long total = 0L;
        for (SaleLineItem line : lineItems) {
            total = Math.addExact(total, line.subtotalCents());
        }
        return total;
    }

    /**
     * 整单应付：沿用第一章规则——商品总额满 4000 分减 500 分。
     * 整单条件需要整单信息，由持有明细集合的 Sale 判断（信息专家原则）。
     */
    public long amountDueCents() {
        long base = totalCents();
        long discount = base >= 4000L ? 500L : 0L;
        return Math.subtractExact(base, discount);
    }

    /** 代码清单2-21：面向抽象契约调用，不关心具体是哪一种会员卡（多态原则）。 */
    public long finalPriceCents(MemberCard card, long totalCents) {
        if (totalCents < 0) throw new IllegalArgumentException("negative amount");
        int percent = card.discountPercent();
        if (percent < 0 || percent > 100) throw new IllegalArgumentException("invalid percent");
        return (totalCents / 100) * percent + ((totalCents % 100) * percent + 50) / 100;
    }

    /** 代码清单2-24：委托中间对象 TaxAdapter 计算税额（间接原则）。 */
    public long taxCents(long totalCents) {
        return taxAdapter.taxOf(totalCents);
    }

    public SaleStatus getStatus() {
        return status;
    }

    public boolean isClosed() {
        return status == SaleStatus.SETTLED || status == SaleStatus.CANCELLED;
    }

    /** 状态变更归小票自己，外部只能通过方法请求，不能直接改字段。 */
    public void close(boolean paid) {
        if (isClosed()) {
            return;
        }
        this.status = paid ? SaleStatus.SETTLED : SaleStatus.CANCELLED;
    }

    public synchronized void markPending(String tradeNo) {
        if (isClosed()) return;
        this.status = SaleStatus.PENDING;
        this.pendingTradeNo = tradeNo;
    }

    public synchronized PaymentRequest paymentRequest() {
        return paymentRequest;
    }

    public synchronized PaymentRequest beginPayment() {
        if (!hasItems() || isClosed() || paymentRequest != null)
            throw new IllegalStateException("cannot start payment");
        long due = amountDueCents();
        paymentRequest =
                new PaymentRequest(
                        orderId, UUID.randomUUID().toString(), due, taxAdapter.taxOf(due));
        status = SaleStatus.PENDING;
        return paymentRequest;
    }

    public synchronized boolean completePayment() {
        if (isClosed()) return false;
        if (paymentRequest == null) throw new IllegalStateException("payment has not started");
        status = SaleStatus.SETTLED;
        return true;
    }

    public synchronized void rejectPayment() {
        if (isClosed()) return;
        status = SaleStatus.OPEN;
        paymentRequest = null;
        pendingTradeNo = null;
    }

    public String getPendingTradeNo() {
        return pendingTradeNo;
    }
}
