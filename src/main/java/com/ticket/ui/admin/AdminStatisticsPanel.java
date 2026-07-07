package com.ticket.ui.admin;

import com.ticket.dto.ReportDTO;
import com.ticket.model.User;
import com.ticket.service.StatisticsService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTabbedPane;
import javax.swing.table.DefaultTableModel;
import org.bson.Document;

public class AdminStatisticsPanel extends JPanel {
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("0.00");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final StatisticsService statisticsService;
    private final User currentUser;
    private final DefaultTableModel monthlyModel = new DefaultTableModel(new Object[]{"指标", "数量", "金额"}, 0);
    private final DefaultTableModel aggregateModel = new DefaultTableModel();
    private final DefaultTableModel auditModel = new DefaultTableModel();
    private final JLabel aggregateStatusLabel = new JLabel("请选择左侧统计口径");
    private final JLabel auditStatusLabel = new JLabel("可按类型、级别、用户或关键词筛选系统日志");
    private final JSpinner yearSpinner;
    private final JSpinner monthSpinner;
    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{"", "LOGIN", "LOGIN_FAIL", "DB_ERROR", "USER_DISABLED", "ITEM_DELETE", "STATUS_CHANGE", "ADMIN_OPERATION", "CROSS_DB_FAIL", "TX_ROLLBACK", "SYSTEM_STARTUP"});
    private final JComboBox<String> levelBox = new JComboBox<>(new String[]{"", "INFO", "WARN", "ERROR"});
    private final JTextField userIdField = new JTextField(8);
    private final JTextField keywordField = new JTextField(12);
    private final JSpinner auditLimitSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 200, 5));

    public AdminStatisticsPanel(StatisticsService statisticsService, User currentUser) {
        this.statisticsService = statisticsService;
        this.currentUser = currentUser;
        LocalDate now = LocalDate.now();
        this.yearSpinner = new JSpinner(new SpinnerNumberModel(now.getYear(), 2000, 2100, 1));
        this.monthSpinner = new JSpinner(new SpinnerNumberModel(now.getMonthValue(), 1, 12, 1));
        setLayout(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("月度报表", buildMonthlyPanel());
        tabs.addTab("MongoDB 聚合统计", buildAggregatePanel());
        tabs.addTab("系统日志审计", buildAuditPanel());
        add(tabs, BorderLayout.CENTER);
        loadMonthlyReport();
        loadDocumentTable(aggregateModel, statisticsService.actionTypeSummary(currentUser), "行为类型分布");
        loadAuditLogs();
    }

    private JPanel buildMonthlyPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshButton = new JButton("刷新");
        toolbar.add(new JLabel("年份"));
        toolbar.add(yearSpinner);
        toolbar.add(new JLabel("月份"));
        toolbar.add(monthSpinner);
        toolbar.add(refreshButton);
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(new JTable(monthlyModel)), BorderLayout.CENTER);
        refreshButton.addActionListener(event -> loadMonthlyReport());
        return panel;
    }

    private JPanel buildAggregatePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel(new GridLayout(0, 1, 4, 4));
        addAggregateButton(buttonPanel, "行为类型", () -> statisticsService.actionTypeSummary(currentUser));
        addAggregateButton(buttonPanel, "近 30 天趋势", () -> statisticsService.dailyActionTrend(currentUser, 30));
        addAggregateButton(buttonPanel, "热门工单", () -> statisticsService.hotItems(currentUser));
        addAggregateButton(buttonPanel, "用户活跃度", () -> statisticsService.userActions(currentUser));
        addAggregateButton(buttonPanel, "客户端分布", () -> statisticsService.clientUsage(currentUser));
        addAggregateButton(buttonPanel, "评分分布", () -> statisticsService.ratingDistribution(currentUser));
        addAggregateButton(buttonPanel, "评论标签", () -> statisticsService.commentTagSummary(currentUser));
        addAggregateButton(buttonPanel, "工单评论", () -> statisticsService.itemCommentStats(currentUser));
        addAggregateButton(buttonPanel, "最近行为", () -> statisticsService.recentActionLogs(currentUser, 50));
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buttonPanel, new JScrollPane(new JTable(aggregateModel)));
        splitPane.setResizeWeight(0.16);
        panel.add(splitPane, BorderLayout.CENTER);
        panel.add(aggregateStatusLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildAuditPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton searchButton = new JButton("查询");
        JButton typeButton = new JButton("类型汇总");
        JButton levelButton = new JButton("级别汇总");
        JButton userButton = new JButton("用户汇总");
        JButton trendButton = new JButton("近 30 天趋势");
        toolbar.add(new JLabel("类型"));
        toolbar.add(typeBox);
        toolbar.add(new JLabel("级别"));
        toolbar.add(levelBox);
        toolbar.add(new JLabel("用户ID"));
        toolbar.add(userIdField);
        toolbar.add(new JLabel("关键词"));
        toolbar.add(keywordField);
        toolbar.add(new JLabel("条数"));
        toolbar.add(auditLimitSpinner);
        toolbar.add(searchButton);
        toolbar.add(typeButton);
        toolbar.add(levelButton);
        toolbar.add(userButton);
        toolbar.add(trendButton);
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(new JTable(auditModel)), BorderLayout.CENTER);
        panel.add(auditStatusLabel, BorderLayout.SOUTH);
        searchButton.addActionListener(event -> loadAuditLogs());
        typeButton.addActionListener(event -> loadAuditTable(statisticsService.systemLogSummary(currentUser), "系统日志类型汇总"));
        levelButton.addActionListener(event -> loadAuditTable(statisticsService.systemLogLevelSummary(currentUser), "系统日志级别汇总"));
        userButton.addActionListener(event -> loadAuditTable(statisticsService.systemLogUserSummary(currentUser, limitValue()), "系统日志用户汇总"));
        trendButton.addActionListener(event -> loadAuditTable(statisticsService.systemLogDailyTrend(currentUser, 30), "系统日志近 30 天趋势"));
        return panel;
    }

    private void addAggregateButton(JPanel buttonPanel, String title, DocumentSupplier supplier) {
        JButton button = new JButton(title);
        button.addActionListener(event -> loadDocumentTable(aggregateModel, supplier.get(), title));
        buttonPanel.add(button);
    }

    private void loadMonthlyReport() {
        monthlyModel.setRowCount(0);
        int year = (Integer) yearSpinner.getValue();
        int month = (Integer) monthSpinner.getValue();
        List<ReportDTO> report = statisticsService.monthlyReport(currentUser, year, month);
        for (ReportDTO row : report) {
            monthlyModel.addRow(new Object[]{monthlyLabel(row.getLabel()), row.getCount(), formatAmount(row.getAmount())});
        }
    }

    private void loadAuditLogs() {
        List<Document> logs = statisticsService.auditLogs(
            currentUser,
            selectedText(typeBox),
            selectedText(levelBox),
            userIdField.getText(),
            keywordField.getText(),
            limitValue()
        );
        loadAuditTable(logs, "系统日志查询结果");
    }

    private void loadAuditTable(List<Document> rows, String title) {
        loadDocumentTable(auditModel, rows, title);
        auditStatusLabel.setText(title + "，共 " + rows.size() + " 条");
    }

    private void loadDocumentTable(DefaultTableModel model, List<Document> rows, String title) {
        Set<String> columns = new LinkedHashSet<>();
        List<Document> flatRows = new ArrayList<>();
        for (Document row : rows) {
            Document flat = flatten(row);
            flatRows.add(flat);
            columns.addAll(flat.keySet());
        }
        if (columns.isEmpty()) {
            columns.add("结果");
        }
        model.setColumnIdentifiers(columns.toArray());
        model.setRowCount(0);
        for (Document row : flatRows) {
            List<Object> values = new ArrayList<>();
            for (String column : columns) {
                values.add(formatValue(row.get(column)));
            }
            model.addRow(values.toArray());
        }
        aggregateStatusLabel.setText(title + "，共 " + rows.size() + " 条");
    }

    private Document flatten(Document source) {
        Document target = new Document();
        for (String key : source.keySet()) {
            Object value = source.get(key);
            if (value instanceof Document document) {
                for (String nestedKey : document.keySet()) {
                    target.append(key + "." + nestedKey, document.get(nestedKey));
                }
            } else {
                target.append(key, value);
            }
        }
        return target;
    }

    private Object formatValue(Object value) {
        if (value instanceof Date date) {
            return DATE_FORMAT.format(date);
        }
        if (value instanceof BigDecimal amount) {
            return MONEY_FORMAT.format(amount);
        }
        return value == null ? "" : value;
    }

    private String formatAmount(BigDecimal amount) {
        return amount == null ? "" : MONEY_FORMAT.format(amount);
    }

    private String monthlyLabel(String label) {
        return switch (label) {
            case "total_count" -> "工单总数";
            case "pending_count" -> "待处理";
            case "processing_count" -> "处理中";
            case "completed_count" -> "已完成";
            case "closed_count" -> "已关闭";
            case "cancelled_count" -> "已取消";
            case "avg_amount" -> "平均金额";
            default -> label;
        };
    }

    private int limitValue() {
        return (Integer) auditLimitSpinner.getValue();
    }

    private String selectedText(JComboBox<String> box) {
        Object value = box.getSelectedItem();
        return value == null ? "" : value.toString();
    }

    @FunctionalInterface
    private interface DocumentSupplier {
        List<Document> get();
    }
}
