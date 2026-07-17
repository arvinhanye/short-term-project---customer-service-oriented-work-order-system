package com.ticket.ui.table;

import com.ticket.dto.CrossTicketDTO;
import com.ticket.model.ItemDetail;
import com.ticket.util.TimeFormatUtil;
import com.ticket.util.SlaDisplayUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.table.AbstractTableModel;

public class OrderTableModel extends AbstractTableModel {
    private final String[] columns = {"工单编号", "标题", "分类", "优先级", "状态", "SLA", "金额", "创建时间", "更新时间"};
    private List<CrossTicketDTO> tickets = new ArrayList<>();
    private Map<Long, String> categoryDisplayNames = Map.of();

    public void setTickets(List<CrossTicketDTO> tickets) {
        this.tickets = tickets == null ? new ArrayList<>() : tickets;
        fireTableDataChanged();
    }

    public CrossTicketDTO getTicketAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= tickets.size()) {
            return null;
        }
        return tickets.get(rowIndex);
    }

    public void setCategoryDisplayNames(Map<Long, String> categoryDisplayNames) {
        this.categoryDisplayNames = categoryDisplayNames == null ? Map.of() : Map.copyOf(categoryDisplayNames);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return tickets.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        CrossTicketDTO ticket = tickets.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> ticket.getItem() == null ? "" : ticket.getItem().getItemId();
            case 1 -> ticket.getItem() == null ? "" : ticket.getItem().getTitle();
            case 2 -> ticket.getCategory() == null ? ""
                : categoryDisplayNames.getOrDefault(ticket.getCategory().getCategoryId(), ticket.getCategory().getName());
            case 3 -> priorityText(ticket);
            case 4 -> ticket.getOrder() == null ? "" : statusText(ticket.getOrder().getStatus());
            case 5 -> ticket.getOrder() == null ? "—" : SlaDisplayUtil.countdown(ticket.getOrder());
            case 6 -> ticket.getOrder() == null ? "" : ticket.getOrder().getAmount();
            case 7 -> ticket.getOrder() == null ? "—" : TimeFormatUtil.format(ticket.getOrder().getCreatedAt());
            case 8 -> ticket.getItem() == null ? "—" : TimeFormatUtil.format(ticket.getItem().getUpdatedAt());
            default -> "";
        };
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    private String priorityText(CrossTicketDTO ticket) {
        ItemDetail detail = ticket.getItemDetail();
        ItemDetail.Metadata metadata = detail == null ? null : detail.getMetadata();
        String priority = metadata == null ? "" : metadata.getPriority();
        return switch (priority == null ? "" : priority) {
            case "LOW" -> "低";
            case "MEDIUM" -> "中";
            case "HIGH" -> "高";
            case "URGENT" -> "紧急";
            default -> priority == null ? "" : priority;
        };
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case 0 -> "待处理";
            case 1 -> "处理中";
            case 2 -> "已完成";
            case 3 -> "已关闭";
            case 4 -> "已取消";
            case 5 -> "等待客户回复";
            case 6 -> "暂挂";
            default -> "未知(" + status + ")";
        };
    }
}
