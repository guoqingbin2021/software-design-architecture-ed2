import java.util.*;

/**
 * 代码清单 1-2：接口装配与两种假设备的最小演示（1.4.1 打印接口与设备适配）。
 * <p>
 * 运行方式：
 *   javac PrintDemo.java
 *   java -ea PrintDemo
 * 说明：-ea 用于启用断言。通过时显示：
 *   A/B replacement and reprint: PASS
 */
public class PrintDemo {
    record Receipt(String saleId, long totalCents) {}

    interface ReceiptPrinter {
        void print(Receipt receipt);
    }

    static class FakePrinterA implements ReceiptPrinter {
        int outputs;
        boolean failNext;
        boolean disconnectNext;

        public void print(Receipt r) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("paper out");
            }
            if (disconnectNext) {
                disconnectNext = false;
                throw new RuntimeException("disconnected before output");
            }
            outputs++;
        }
    }

    static class FakePrinterB implements ReceiptPrinter {
        int outputs;

        public void print(Receipt r) {
            outputs++;
        }
    }

    static class Checkout {
        final ReceiptPrinter printer;
        final Map<String, Receipt> committed = new HashMap<>();
        final Set<String> pendingPrint = new HashSet<>();
        int commits;

        Checkout(ReceiptPrinter printer) {
            this.printer = printer;
        }

        void submit(Receipt receipt) {
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

        void printCommitted(Receipt receipt) {
            try {
                printer.print(receipt);
            } catch (RuntimeException ex) {
                pendingPrint.add(receipt.saleId());
                return;
            }
            pendingPrint.remove(receipt.saleId());
        }

        void reprint(String id) {
            Receipt receipt = committed.get(id);
            if (receipt == null) {
                throw new IllegalArgumentException("sale not found");
            }
            printCommitted(receipt);
        }
    }

    public static void main(String[] args) {
        Receipt r = new Receipt("S01-P01-0001", 4900L);
        FakePrinterA a = new FakePrinterA();
        Checkout ca = new Checkout(a);
        ca.submit(r);
        assert a.outputs == 1 && ca.commits == 1;

        FakePrinterB b = new FakePrinterB();
        Checkout cb = new Checkout(b);
        cb.submit(r);
        assert b.outputs == 1 && cb.commits == 1;

        a.failNext = true;
        Receipt r2 = new Receipt("S01-P01-0002", 4900L);
        ca.submit(r2);
        assert ca.commits == 2 && a.outputs == 1;
        assert ca.pendingPrint.contains(r2.saleId());
        ca.reprint(r2.saleId());
        assert a.outputs == 2 && ca.commits == 2;
        assert !ca.pendingPrint.contains(r2.saleId());
        ca.submit(r2);
        assert ca.commits == 2 && a.outputs == 2;

        FakePrinterA disconnected = new FakePrinterA();
        disconnected.disconnectNext = true;
        Checkout checkout = new Checkout(disconnected);
        Receipt r3 = new Receipt("S01-P01-0003", 4900L);
        checkout.submit(r3);
        assert checkout.commits == 1 && disconnected.outputs == 0;
        assert r3.equals(checkout.committed.get(r3.saleId()));
        assert checkout.pendingPrint.contains(r3.saleId());
        checkout.submit(r3);
        assert checkout.commits == 1 && disconnected.outputs == 0;
        System.out.println("A/B replacement and reprint: PASS");
    }
}
