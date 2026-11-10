package com.example.pos.board;

/**
 * 代码清单2-18：按键与功能分离（正例，2.5.1）。
 * 按键本身只负责触发动作，不认识任何具体的业务功能实现。
 */
public class Button {

    private Action action; // 仅引用抽象功能契约

    /** 注入要执行的功能实现——更换按键功能只需替换注入的对象 */
    public void setAction(Action action) {
        this.action = action;
    }

    /** 按键被按下时触发：委托给抽象功能契约执行 */
    public void onClick() {
        if (action != null) {
            action.execute();
        }
    }
}
