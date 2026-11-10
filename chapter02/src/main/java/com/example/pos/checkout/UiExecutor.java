package com.example.pos.checkout;

/** 界面线程调度入口的抽象，便于测试替身。 */
public interface UiExecutor {

    void execute(Runnable task);
}
