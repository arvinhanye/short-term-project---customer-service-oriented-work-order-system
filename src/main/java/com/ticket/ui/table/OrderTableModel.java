package com.ticket.ui.table;

import com.ticket.model.Order;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

public class OrderTableModel extends AbstractTableModel {
    private final String[] columns = {"记录编号", "用户编号", "工单编号", "金额", "状态", "创建时间"};
    private List<Order> orders = new ArrayList<>();

    public void setOrders(List<Order> orders) {
        this.orders = orders;
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return orders.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Order order = orders.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> order.getOrderId();
            case 1 -> order.getUserId();
            case 2 -> order.getItemId();
            case 3 -> order.getAmount();
            case 4 -> order.getStatus();
            case 5 -> order.getCreatedAt();
            default -> "";
        };
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }
}
