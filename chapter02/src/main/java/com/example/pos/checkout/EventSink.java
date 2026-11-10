package com.example.pos.checkout;

/**
 * 代码清单2-16：基于抽象事件接收接口的界面解耦（正例，2.4.1）。
 * 界面层只依赖抽象事件接收契约，替换控制器实现时界面代码无需改动。
 */
public interface EventSink {

    void onEvent(SystemOperation op, Object payload);
}
