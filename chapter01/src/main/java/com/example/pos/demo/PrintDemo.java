package com.example.pos.demo;

import com.example.pos.printer.Checkout;
import com.example.pos.printer.FakePrinterA;
import com.example.pos.printer.FakePrinterB;
import com.example.pos.printer.Receipt;

/**
 * 代码清单 1-2：工程化演示入口（1.4.1 打印接口与设备适配）。
 * 运行：java -ea com.example.pos.demo.PrintDemo
 */
public class PrintDemo {

    public static void main(String[] args) {
        Receipt r = new Receipt("S01-P01-0001", 4900L);
        FakePrinterA a = new FakePrinterA();
        Checkout ca = new Checkout(a);
        ca.submit(r);
        assert a.getOutputs() == 1 && ca.getCommits() == 1;

        FakePrinterB b = new FakePrinterB();
        Checkout cb = new Checkout(b);
        cb.submit(r);
        assert b.getOutputs() == 1 && cb.getCommits() == 1;

        a.setFailNext(true);
        Receipt r2 = new Receipt("S01-P01-0002", 4900L);
        ca.submit(r2);
        assert ca.getCommits() == 2 && a.getOutputs() == 1;
        assert ca.getPendingPrint().contains(r2.saleId());
        ca.reprint(r2.saleId());
        assert a.getOutputs() == 2 && ca.getCommits() == 2;
        assert !ca.getPendingPrint().contains(r2.saleId());
        ca.submit(r2);
        assert ca.getCommits() == 2 && a.getOutputs() == 2;

        FakePrinterA disconnected = new FakePrinterA();
        disconnected.setDisconnectNext(true);
        Checkout checkout = new Checkout(disconnected);
        Receipt r3 = new Receipt("S01-P01-0003", 4900L);
        checkout.submit(r3);
        assert checkout.getCommits() == 1 && disconnected.getOutputs() == 0;
        assert r3.equals(checkout.getCommitted().get(r3.saleId()));
        assert checkout.getPendingPrint().contains(r3.saleId());
        checkout.submit(r3);
        assert checkout.getCommits() == 1 && disconnected.getOutputs() == 0;
        System.out.println("A/B replacement and reprint: PASS");
    }
}
