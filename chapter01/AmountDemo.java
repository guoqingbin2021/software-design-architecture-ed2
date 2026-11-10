import java.util.List;

/**
 * 代码清单 1-3：49元汇总与整单优惠的自动化断言（1.4.1 计价职责与优惠规则）。
 * <p>
 * 运行方式：
 *   javac AmountDemo.java
 *   java -ea AmountDemo
 * 说明：断言覆盖两行小计、4900 分原单、5200分新价单、4400 分满减应付及空单汇总。通过时显示：
 *   49.00, 52.00 and 44.00 yuan: PASS
 */
public class AmountDemo {
    record SaleLineItem(long unitPriceCents, int quantity) {
        SaleLineItem {
            if (unitPriceCents < 0 || quantity <= 0) {
                throw new IllegalArgumentException("invalid line");
            }
        }

        long subtotalCents() {
            return Math.multiplyExact(unitPriceCents, quantity);
        }
    }

    record Sale(List<SaleLineItem> lines) {
        Sale {
            lines = List.copyOf(lines);
        }

        long totalCents() {
            long total = 0L;
            for (SaleLineItem line : lines) {
                total = Math.addExact(total, line.subtotalCents());
            }
            return total;
        }
    }

    static long amountDueCents(Sale sale) {
        long base = sale.totalCents();
        // One order-level offer: 5 yuan off if at least 40 yuan.
        long discount = base >= 4000L ? 500L : 0L;
        return Math.subtractExact(base, discount);
    }

    public static void main(String[] args) {
        Sale sale = new Sale(List.of(new SaleLineItem(1250L, 2), new SaleLineItem(800L, 3)));
        assert sale.lines().get(0).subtotalCents() == 2500L;
        assert sale.lines().get(1).subtotalCents() == 2400L;
        assert sale.totalCents() == 4900L;
        assert amountDueCents(sale) == 4400L;
        Sale repricedSale =
                new Sale(List.of(new SaleLineItem(1250L, 2), new SaleLineItem(900L, 3)));
        assert repricedSale.totalCents() == 5200L;
        assert sale.totalCents() == 4900L; // 新单调价不改变原快照。
        assert new Sale(List.of()).totalCents() == 0L;
        System.out.println("49.00, 52.00 and 44.00 yuan: PASS");
    }
}
