package com.ticket.ui.theme;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/** Compact, accessible status and priority tag renderer for ticket tables. */
public class StatusTagRenderer extends DefaultTableCellRenderer {
    public enum Kind { STATUS, PRIORITY }

    private final Kind kind;

    public StatusTagRenderer(Kind kind) {
        this.kind = kind;
        setHorizontalAlignment(CENTER);
        setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused,
                                                    int row, int column) {
        super.getTableCellRendererComponent(table, value, selected, focused, row, column);
        String text = value == null ? "—" : String.valueOf(value);
        setText(text);
        Color color = colorFor(text);
        setForeground(color);
        setBackground(selected ? AppTheme.TABLE_SELECTED
            : new Color((color.getRed() + 255 * 9) / 10,
                (color.getGreen() + 255 * 9) / 10,
                (color.getBlue() + 255 * 9) / 10));
        setBorder(AppTheme.tableCellBorder(selected, column));
        return this;
    }

    private Color colorFor(String value) {
        if (kind == Kind.PRIORITY) {
            return switch (value) {
                case "紧急" -> AppTheme.DANGER;
                case "高" -> new Color(234, 88, 12);
                case "中" -> AppTheme.WARNING;
                default -> AppTheme.SUCCESS;
            };
        }
        return switch (value) {
            case "待处理" -> AppTheme.WARNING;
            case "处理中" -> AppTheme.PRIMARY;
            case "已完成" -> AppTheme.SUCCESS;
            case "已关闭", "已取消" -> AppTheme.MUTED;
            default -> AppTheme.MUTED;
        };
    }
}
