package com.example.pos.counterexample;

import javax.swing.JButton;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * 代码清单2-14、2-15：控制器原则反例合集（2.4.1）。
 * 含把功能写死在界面控件中（代码清单2-14）与业务对象直接实现硬件监听接口（代码清单2-15）。
 */
@Deprecated
public class HardcodedSettleButton extends JButton implements ActionListener {

    public HardcodedSettleButton() {
        super("结算");
        setSize(120, 70);
        addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // 反例（代码清单2-14）：把结算算价、打印、状态变更与积分逻辑全部硬编码在按钮控件内部
    }

    /** 代码清单2-15：业务对象直接监听外部设备事件反例示意 */
    public interface BarcodeListener {
        void onBarcode(String barcode);
    }

    public interface KeyListener {
        void onPrintKey();
    }
}
