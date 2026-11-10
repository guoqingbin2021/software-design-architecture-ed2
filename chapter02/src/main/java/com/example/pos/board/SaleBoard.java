package com.example.pos.board;

import java.util.HashMap;
import java.util.Map;

/**
 * 代码清单2-19：键位与功能的装配（2.5.1，图2-10）。
 */
public class SaleBoard {

    private final Map<String, Button> buttons = new HashMap<>();

    public SaleBoard() {
        buttons.put("M", new Button());
        buttons.put("Y", new Button());
        buttons.put("P", new Button());
    }

    public Button getButton(String key) {
        return buttons.computeIfAbsent(key, k -> new Button());
    }

    /** 演示代码清单2-19的键位装配过程 */
    public static SaleBoard createDefaultConfiguredBoard() {
        SaleBoard board = new SaleBoard();
        board.getButton("M").setAction(new QueryMemberAction()); // M键 = 查询会员
        board.getButton("Y").setAction(new VipPaymentAction()); // Y键 = 储值消费
        board.getButton("P").setAction(new ItemQueryAction()); // P键 = 商品查询
        return board;
    }

    public static class QueryMemberAction implements Action {
        @Override
        public void execute() {
            // 执行查询会员操作
        }
    }

    public static class VipPaymentAction implements Action {
        @Override
        public void execute() {
            // 执行会员储值消费操作
        }
    }

    public static class ItemQueryAction implements Action {
        @Override
        public void execute() {
            // 执行商品查询操作
        }
    }
}
