package com.ticket.ui.admin;

import com.ticket.dto.ReportDTO;
import com.ticket.model.User;
import com.ticket.service.StatisticsService;
import java.awt.BorderLayout;
import java.awt.Dimension;
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
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTabbedPane;
import javax.swing.table.DefaultTableModel;
import javax.swing.SwingWorker;
import org.bson.Document;

public class AdminStatisticsPanel extends JPanel {
    public enum ViewMode {
        FULL,
        BEHAVIOR,
        SYSTEM
    }

    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("0.00");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final StatisticsService statisticsService;
    private final User currentUser;
    private final ViewMode viewMode;
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
        this(statisticsService, currentUser, ViewMode.FULL);
    }

    public AdminStatisticsPanel(StatisticsService statisticsService, User currentUser, ViewMode viewMode) {
        this.statisticsService = statisticsService;
        this.currentUser = currentUser;
        this.viewMode = viewMode == null ? ViewMode.FULL : viewMode;
        LocalDate now = LocalDate.now();
        this.yearSpinner = new JSpinner(new SpinnerNumberModel(now.getYear(), 2000, 2100, 1));
        this.monthSpinner = new JSpinner(new SpinnerNumberModel(now.getMonthValue(), 1, 12, 1));
        setLayout(new BorderLayout());
        add(buildContent(), BorderLayout.CENTER);
        loadInitialData();
    }

    private JPanel buildContent() {
        if (viewMode == ViewMode.BEHAVIOR) {
            return buildAggregatePanel();
        }
        if (viewMode == ViewMode.SYSTEM) {
            return buildAuditPanel();
        }
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("月度报表", buildMonthlyPanel());
        tabs.addTab("行为日志统计", buildAggregatePanel());
        tabs.addTab("系统日志审计", buildAuditPanel());
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    private void loadInitialData() {
        if (viewMode == ViewMode.FULL) {
            loadMonthlyReport();
            loadAggregateTable(() -> statisticsService.actionTypeSummary(currentUser), "行为类型分布");
            loadAuditLogs();
            return;
        }
        if (viewMode == ViewMode.BEHAVIOR) {
            loadAggregateTable(() -> statisticsService.actionTypeSummary(currentUser), "行为类型分布");
            return;
        }
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
        panel.add(scrollableHeader(toolbar), BorderLayout.NORTH);
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
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(buttonPanel), new JScrollPane(new JTable(aggregateModel)));
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
        panel.add(scrollableHeader(toolbar), BorderLayout.NORTH);
        panel.add(new JScrollPane(new JTable(auditModel)), BorderLayout.CENTER);
        panel.add(auditStatusLabel, BorderLayout.SOUTH);
        searchButton.addActionListener(event -> loadAuditLogs());
        typeButton.addActionListener(event -> loadAuditTable(() -> statisticsService.systemLogSummary(currentUser), "系统日志类型汇总"));
        levelButton.addActionListener(event -> loadAuditTable(() -> statisticsService.systemLogLevelSummary(currentUser), "系统日志级别汇总"));
        userButton.addActionListener(event -> loadAuditTable(() -> statisticsService.systemLogUserSummary(currentUser, limitValue()), "系统日志用户汇总"));
        trendButton.addActionListener(event -> loadAuditTable(() -> statisticsService.systemLogDailyTrend(currentUser, 30), "系统日志近 30 天趋势"));
        return panel;
    }

    private void addAggregateButton(JPanel buttonPanel, String title, DocumentSupplier supplier) {
        JButton button = new JButton(title);
        button.addActionListener(event -> loadAggregateTable(supplier, title));
        buttonPanel.add(button);
    }

    private JScrollPane scrollableHeader(JPanel panel) {
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.setPreferredSize(new Dimension(0, panel.getPreferredSize().height + 18));
        return scrollPane;
    }

    private void loadAggregateTable(DocumentSupplier supplier, String title) {
        aggregateStatusLabel.setText(title + "加载中…");
        loadAsync(supplier::get, rows -> loadDocumentTable(aggregateModel, rows, title), title);
    }

    private void loadMonthlyReport() {
        int year = (Integer) yearSpinner.getValue();
        int month = (Integer) monthSpinner.getValue();
        loadAsync(() -> statisticsService.monthlyReport(currentUser, year, month), report -> {
            monthlyModel.setRowCount(0);
            for (ReportDTO row : report) {
                monthlyModel.addRow(new Object[]{monthlyLabel(row.getLabel()), row.getCount(), formatAmount(row.getAmount())});
            }
        }, "月度报表");
    }

    private void loadAuditLogs() {
        String type = selectedText(typeBox);
        String level = selectedText(levelBox);
        String userId = userIdField.getText();
        String keyword = keywordField.getText();
        int limit = limitValue();
        auditStatusLabel.setText("系统日志查询结果加载中…");
        loadAsync(() -> statisticsService.auditLogs(currentUser, type, level, userId, keyword, limit),
            rows -> loadAuditTable(rows, "系统日志查询结果"), "系统日志查询结果");
    }

    private void loadAuditTable(List<Document> rows, String title) {
        try {
            loadDocumentTable(auditModel, rows, title);
            auditStatusLabel.setText(title + "，共 " + rows.size() + " 条");
        } catch (Exception ex) {
            showLoadError(title, ex);
        }
    }

    private void loadAuditTable(DocumentSupplier supplier, String title) {
        auditStatusLabel.setText(title + "加载中…");
        loadAsync(supplier::get, rows -> loadAuditTable(rows, title), title);
    }

    private <T> void loadAsync(Supplier<T> supplier, Consumer<T> onSuccess, String title) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() {
                return supplier.get();
            }

            @Override
            protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (Exception ex) {
                    showLoadError(title, ex);
                }
            }
        }.execute();
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

    private void showLoadError(String title, Exception ex) {
        String message = title + "加载失败：" + rootMessage(ex);
        aggregateStatusLabel.setText(message);
        auditStatusLabel.setText(message);
        JOptionPane.showMessageDialog(this, message, "提示", JOptionPane.WARNING_MESSAGE);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @FunctionalInterface
    private interface DocumentSupplier {
        List<Document> get();
    }
}
