package com.ticket.ui.admin;

import com.ticket.dto.ReportDTO;
import com.ticket.model.User;
import com.ticket.service.StatisticsService;
import com.ticket.ui.theme.AppTheme;
import com.ticket.util.TimeFormatUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
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

    private final StatisticsService statisticsService;
    private final User currentUser;
    private final ViewMode viewMode;
    private final DefaultTableModel monthlyModel = readOnlyModel(new Object[]{"指标", "数量", "金额"});
    private final DefaultTableModel aggregateModel = readOnlyModel(new Object[]{});
    private final DefaultTableModel auditModel = readOnlyModel(new Object[]{});
    private final Map<String, JLabel> monthlyValueLabels = new LinkedHashMap<>();
    private final JLabel monthlyStatusLabel = new JLabel("待查询");
    private final JLabel aggregateTitleLabel = new JLabel("行为类型分布");
    private final JLabel aggregateStatusLabel = new JLabel("待查询");
    private final JLabel auditTitleLabel = new JLabel("系统日志查询结果");
    private final JLabel auditStatusLabel = new JLabel("待查询");
    private final JSpinner yearSpinner;
    private final JSpinner monthSpinner;
    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{"全部类型", "LOGIN", "LOGIN_FAIL", "DB_ERROR", "USER_DISABLED", "ITEM_DELETE", "STATUS_CHANGE", "ADMIN_OPERATION", "CROSS_DB_FAIL", "TX_ROLLBACK", "SYSTEM_STARTUP"});
    private final JComboBox<String> levelBox = new JComboBox<>(new String[]{"全部级别", "INFO", "WARN", "ERROR"});
    private final JTextField userIdField = new JTextField(8);
    private final JTextField keywordField = new JTextField(12);
    private final JSpinner auditLimitSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 200, 5));
    private SwingWorker<?, ?> monthlyWorker;
    private SwingWorker<?, ?> aggregateWorker;
    private SwingWorker<?, ?> auditWorker;

    private static DefaultTableModel readOnlyModel(Object[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

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
        AppTheme.styleComboBox(typeBox);
        AppTheme.styleComboBox(levelBox);
        AppTheme.styleInput(userIdField);
        AppTheme.styleInput(keywordField);
        setLayout(new BorderLayout());
        setBackground(AppTheme.PAGE);
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
        return buildMonthlyPanel();
    }

    private void loadInitialData() {
        if (viewMode == ViewMode.FULL) {
            loadMonthlyReport();
            return;
        }
        if (viewMode == ViewMode.BEHAVIOR) {
            loadAggregateTable(() -> statisticsService.actionTypeSummary(currentUser), "行为类型分布");
            return;
        }
        loadAuditLogs();
    }

    private JPanel buildMonthlyPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(AppTheme.PAGE);
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setOpaque(false);
        JButton refreshButton = new JButton("刷新");
        JButton serviceReportButton = new JButton("服务质量与导出");
        AppTheme.primary(refreshButton);
        AppTheme.secondary(serviceReportButton);
        toolbar.add(new JLabel("年份"));
        toolbar.add(yearSpinner);
        toolbar.add(new JLabel("月份"));
        toolbar.add(monthSpinner);
        toolbar.add(refreshButton);
        toolbar.add(serviceReportButton);
        JPanel toolbarCard = AppTheme.surface(new BorderLayout());
        toolbarCard.add(toolbar, BorderLayout.CENTER);
        monthlyStatusLabel.setForeground(AppTheme.MUTED);
        toolbarCard.add(monthlyStatusLabel, BorderLayout.EAST);
        panel.add(toolbarCard, BorderLayout.NORTH);

        JPanel metrics = new JPanel(new GridLayout(2, 4, 10, 10));
        metrics.setOpaque(false);
        metrics.add(createMonthlyMetricCard("total_count", "工单总数"));
        metrics.add(createMonthlyMetricCard("pending_count", "待处理"));
        metrics.add(createMonthlyMetricCard("processing_count", "处理中"));
        metrics.add(createMonthlyMetricCard("completed_count", "已完成"));
        metrics.add(createMonthlyMetricCard("closed_count", "已关闭"));
        metrics.add(createMonthlyMetricCard("cancelled_count", "已取消"));
        metrics.add(createMonthlyMetricCard("avg_amount", "平均金额"));
        metrics.add(createMonthlyMetricCard("period", "统计周期"));
        metrics.setPreferredSize(new Dimension(0, 190));

        JTable monthlyTable = new JTable(monthlyModel);
        AppTheme.styleTable(monthlyTable);
        JPanel detailCard = AppTheme.surface(new BorderLayout(0, 8));
        JLabel detailTitle = new JLabel("详细统计口径");
        detailTitle.setFont(detailTitle.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        detailCard.add(detailTitle, BorderLayout.NORTH);
        detailCard.add(AppTheme.scroll(monthlyTable), BorderLayout.CENTER);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.add(metrics, BorderLayout.NORTH);
        content.add(detailCard, BorderLayout.CENTER);
        panel.add(content, BorderLayout.CENTER);
        refreshButton.addActionListener(event -> loadMonthlyReport());
        serviceReportButton.addActionListener(event ->
            ServiceReportDialog.show(this, currentUser, statisticsService));
        return panel;
    }

    private JPanel createMonthlyMetricCard(String key, String title) {
        JPanel card = AppTheme.surface(new BorderLayout(0, 8));
        JLabel titleLabel = AppTheme.muted(title);
        JLabel valueLabel = new JLabel("—");
        valueLabel.setFont(valueLabel.getFont().deriveFont(java.awt.Font.BOLD, 22f));
        monthlyValueLabels.put(key, valueLabel);
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildAggregatePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(AppTheme.PAGE);
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel buttonPanel = new JPanel(new GridLayout(2, 5, 8, 8));
        buttonPanel.setOpaque(false);
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        javax.swing.JToggleButton firstButton = addAggregateButton(buttonPanel, group,
            "行为类型", () -> statisticsService.actionTypeSummary(currentUser));
        addAggregateButton(buttonPanel, group, "近 30 天趋势", () -> statisticsService.dailyActionTrend(currentUser, 30));
        addAggregateButton(buttonPanel, group, "热门工单", () -> statisticsService.hotItems(currentUser));
        addAggregateButton(buttonPanel, group, "用户活跃度", () -> statisticsService.userActions(currentUser));
        addAggregateButton(buttonPanel, group, "客户端分布", () -> statisticsService.clientUsage(currentUser));
        addAggregateButton(buttonPanel, group, "评分分布", () -> statisticsService.ratingDistribution(currentUser));
        addAggregateButton(buttonPanel, group, "评论标签", () -> statisticsService.commentTagSummary(currentUser));
        addAggregateButton(buttonPanel, group, "工单评论", () -> statisticsService.itemCommentStats(currentUser));
        addAggregateButton(buttonPanel, group, "最近行为", () -> statisticsService.recentActionLogs(currentUser, 50));
        JPanel emptySlot = new JPanel();
        emptySlot.setOpaque(false);
        buttonPanel.add(emptySlot);
        firstButton.setSelected(true);
        JPanel metricCard = AppTheme.surface(new BorderLayout(0, 8));
        JLabel metricTitle = new JLabel("统计口径");
        metricTitle.setFont(metricTitle.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        metricCard.add(metricTitle, BorderLayout.NORTH);
        metricCard.add(buttonPanel, BorderLayout.CENTER);
        panel.add(metricCard, BorderLayout.NORTH);

        JTable aggregateTable = new JTable(aggregateModel);
        AppTheme.styleTable(aggregateTable);
        aggregateTable.setAutoCreateRowSorter(true);
        JPanel resultCard = AppTheme.surface(new BorderLayout(0, 8));
        JPanel resultHeading = new JPanel(new BorderLayout(8, 0));
        resultHeading.setOpaque(false);
        aggregateTitleLabel.setFont(aggregateTitleLabel.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        aggregateStatusLabel.setForeground(AppTheme.MUTED);
        resultHeading.add(aggregateTitleLabel, BorderLayout.WEST);
        resultHeading.add(aggregateStatusLabel, BorderLayout.EAST);
        resultCard.add(resultHeading, BorderLayout.NORTH);
        resultCard.add(AppTheme.scroll(aggregateTable), BorderLayout.CENTER);
        panel.add(resultCard, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildAuditPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(AppTheme.PAGE);
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel filterFields = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterFields.setOpaque(false);
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setOpaque(false);
        JButton searchButton = new JButton("查询");
        JButton resetButton = new JButton("重置");
        JButton typeButton = new JButton("类型汇总");
        JButton levelButton = new JButton("级别汇总");
        JButton userButton = new JButton("用户汇总");
        JButton trendButton = new JButton("近 30 天趋势");
        AppTheme.primary(searchButton);
        AppTheme.secondary(resetButton);
        AppTheme.secondary(typeButton);
        AppTheme.secondary(levelButton);
        AppTheme.secondary(userButton);
        AppTheme.secondary(trendButton);
        filterFields.add(new JLabel("类型"));
        filterFields.add(typeBox);
        filterFields.add(new JLabel("级别"));
        filterFields.add(levelBox);
        filterFields.add(new JLabel("用户 ID"));
        filterFields.add(userIdField);
        filterFields.add(new JLabel("关键词"));
        filterFields.add(keywordField);
        filterFields.add(new JLabel("条数"));
        filterFields.add(auditLimitSpinner);
        toolbar.add(searchButton);
        toolbar.add(resetButton);
        JLabel quickSummaryLabel = new JLabel("快速汇总：");
        quickSummaryLabel.setForeground(AppTheme.MUTED);
        toolbar.add(quickSummaryLabel);
        toolbar.add(typeButton);
        toolbar.add(levelButton);
        toolbar.add(userButton);
        toolbar.add(trendButton);
        JPanel filterStack = new JPanel();
        filterStack.setOpaque(false);
        filterStack.setLayout(new javax.swing.BoxLayout(filterStack, javax.swing.BoxLayout.Y_AXIS));
        filterFields.setAlignmentX(LEFT_ALIGNMENT);
        toolbar.setAlignmentX(LEFT_ALIGNMENT);
        filterStack.add(filterFields);
        filterStack.add(javax.swing.Box.createVerticalStrut(10));
        filterStack.add(toolbar);
        JPanel toolbarCard = AppTheme.surface(new BorderLayout(0, 8));
        JLabel filterTitle = new JLabel("日志筛选与汇总");
        filterTitle.setFont(filterTitle.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        toolbarCard.add(filterTitle, BorderLayout.NORTH);
        toolbarCard.add(filterStack, BorderLayout.CENTER);
        panel.add(toolbarCard, BorderLayout.NORTH);

        JTable auditTable = new JTable(auditModel);
        AppTheme.styleTable(auditTable);
        auditTable.setAutoCreateRowSorter(true);
        JPanel resultCard = AppTheme.surface(new BorderLayout(0, 8));
        JPanel resultHeading = new JPanel(new BorderLayout(8, 0));
        resultHeading.setOpaque(false);
        auditTitleLabel.setFont(auditTitleLabel.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        auditStatusLabel.setForeground(AppTheme.MUTED);
        resultHeading.add(auditTitleLabel, BorderLayout.WEST);
        resultHeading.add(auditStatusLabel, BorderLayout.EAST);
        resultCard.add(resultHeading, BorderLayout.NORTH);
        resultCard.add(AppTheme.scroll(auditTable), BorderLayout.CENTER);
        panel.add(resultCard, BorderLayout.CENTER);

        searchButton.addActionListener(event -> loadAuditLogs());
        resetButton.addActionListener(event -> {
            typeBox.setSelectedIndex(0);
            levelBox.setSelectedIndex(0);
            userIdField.setText("");
            keywordField.setText("");
            auditLimitSpinner.setValue(30);
            loadAuditLogs();
        });
        userIdField.addActionListener(event -> loadAuditLogs());
        keywordField.addActionListener(event -> loadAuditLogs());
        typeButton.addActionListener(event -> loadAuditTable(() -> statisticsService.systemLogSummary(currentUser), "系统日志类型汇总"));
        levelButton.addActionListener(event -> loadAuditTable(() -> statisticsService.systemLogLevelSummary(currentUser), "系统日志级别汇总"));
        userButton.addActionListener(event -> loadAuditTable(() -> statisticsService.systemLogUserSummary(currentUser, limitValue()), "系统日志用户汇总"));
        trendButton.addActionListener(event -> loadAuditTable(() -> statisticsService.systemLogDailyTrend(currentUser, 30), "系统日志近 30 天趋势"));
        return panel;
    }

    private javax.swing.JToggleButton addAggregateButton(JPanel buttonPanel, javax.swing.ButtonGroup group,
                                                         String title, DocumentSupplier supplier) {
        javax.swing.JToggleButton button = new javax.swing.JToggleButton(title);
        AppTheme.segment(button);
        button.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        button.addActionListener(event -> loadAggregateTable(supplier, title));
        group.add(button);
        buttonPanel.add(button);
        return button;
    }

    private void loadAggregateTable(DocumentSupplier supplier, String title) {
        cancelWorker(aggregateWorker);
        aggregateTitleLabel.setText(title);
        aggregateStatusLabel.setText(title + "加载中…");
        aggregateWorker = loadAsync(supplier::get, rows -> {
            loadDocumentTable(aggregateModel, rows);
            aggregateStatusLabel.setText(title + "，共 " + rows.size() + " 条");
        }, title);
    }

    private void loadMonthlyReport() {
        cancelWorker(monthlyWorker);
        int year = (Integer) yearSpinner.getValue();
        int month = (Integer) monthSpinner.getValue();
        monthlyStatusLabel.setText(year + " 年 " + month + " 月加载中…");
        monthlyValueLabels.forEach((key, label) -> label.setText("…"));
        JLabel periodLabel = monthlyValueLabels.get("period");
        if (periodLabel != null) {
            periodLabel.setText(year + " 年 " + month + " 月");
        }
        monthlyWorker = loadAsync(() -> statisticsService.monthlyReport(currentUser, year, month), report -> {
            monthlyModel.setRowCount(0);
            monthlyValueLabels.forEach((key, label) -> {
                if (!"period".equals(key)) {
                    label.setText("—");
                }
            });
            for (ReportDTO row : report) {
                monthlyModel.addRow(new Object[]{monthlyLabel(row.getLabel()), row.getCount(), formatAmount(row.getAmount())});
                JLabel valueLabel = monthlyValueLabels.get(row.getLabel());
                if (valueLabel != null) {
                    valueLabel.setText(monthlyMetricValue(row));
                }
            }
            if (report.isEmpty()) {
                monthlyModel.addRow(new Object[]{"暂无数据", "—", "—"});
            }
            monthlyStatusLabel.setText(year + " 年 " + month + " 月 · " + (report.isEmpty() ? "暂无数据" : "已更新"));
        }, "月度报表");
    }

    private void loadAuditLogs() {
        cancelWorker(auditWorker);
        String type = selectedText(typeBox);
        String level = selectedText(levelBox);
        String userId = userIdField.getText();
        String keyword = keywordField.getText();
        int limit = limitValue();
        auditTitleLabel.setText("系统日志查询结果");
        auditStatusLabel.setText("系统日志查询结果加载中…");
        auditWorker = loadAsync(() -> statisticsService.auditLogs(currentUser, type, level, userId, keyword, limit),
            rows -> loadAuditTable(rows, "系统日志查询结果"), "系统日志查询结果");
    }

    private void loadAuditTable(List<Document> rows, String title) {
        try {
            auditTitleLabel.setText(title);
            loadDocumentTable(auditModel, rows);
            auditStatusLabel.setText(title + "，共 " + rows.size() + " 条");
        } catch (Exception ex) {
            showLoadError(title, ex);
        }
    }

    private void loadAuditTable(DocumentSupplier supplier, String title) {
        cancelWorker(auditWorker);
        auditTitleLabel.setText(title);
        auditStatusLabel.setText(title + "加载中…");
        auditWorker = loadAsync(supplier::get, rows -> loadAuditTable(rows, title), title);
    }

    private <T> SwingWorker<T, Void> loadAsync(Supplier<T> supplier, Consumer<T> onSuccess, String title) {
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() {
                return supplier.get();
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    onSuccess.accept(get());
                } catch (Exception ex) {
                    showLoadError(title, ex);
                }
            }
        };
        worker.execute();
        return worker;
    }

    private void cancelWorker(SwingWorker<?, ?> worker) {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
    }

    private void loadDocumentTable(DefaultTableModel model, List<Document> rows) {
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
        model.setColumnIdentifiers(columns.stream().map(this::columnLabel).toArray());
        model.setRowCount(0);
        if (flatRows.isEmpty()) {
            model.addRow(new Object[]{"暂无数据"});
            return;
        }
        for (Document row : flatRows) {
            List<Object> values = new ArrayList<>();
            for (String column : columns) {
                values.add(formatValue(row.get(column)));
            }
            model.addRow(values.toArray());
        }
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
            return TimeFormatUtil.format(date);
        }
        if (value instanceof BigDecimal amount) {
            return MONEY_FORMAT.format(amount);
        }
        return value == null ? "" : value;
    }

    private String columnLabel(String key) {
        return switch (key) {
            case "_id" -> "统计维度";
            case "_id.day" -> "日期";
            case "_id.action_type" -> "行为类型";
            case "_id.level" -> "日志级别";
            case "_id.tag" -> "评论标签";
            case "user_id" -> "用户 ID";
            case "item_id" -> "工单 ID";
            case "action_type" -> "行为类型";
            case "action_count" -> "行为次数";
            case "action_types" -> "行为类型集合";
            case "unique_user_count" -> "用户数";
            case "total_duration_seconds" -> "总耗时（秒）";
            case "avg_duration_seconds" -> "平均耗时（秒）";
            case "duration_seconds" -> "耗时（秒）";
            case "view_count" -> "查看次数";
            case "client_info.client_type" -> "客户端";
            case "client_info.ip", "action_detail.ip" -> "IP 地址";
            case "created_at" -> "发生时间";
            case "last_action_at" -> "最近行为时间";
            case "log_type" -> "日志类型";
            case "log_level" -> "日志级别";
            case "message" -> "日志内容";
            case "action_detail.operation" -> "操作";
            case "timestamp" -> "记录时间";
            case "count" -> "数量";
            case "levels" -> "级别集合";
            case "types" -> "类型集合";
            case "last_seen_at" -> "最近记录时间";
            case "comment_count" -> "评论数";
            case "internal_note_count" -> "内部备注数";
            case "rating_count" -> "评分数";
            case "avg_rating" -> "平均评分";
            case "last_comment_at" -> "最近评论时间";
            case "last_rating_at" -> "最近评分时间";
            default -> key;
        };
    }

    private String formatAmount(BigDecimal amount) {
        return amount == null ? "" : MONEY_FORMAT.format(amount);
    }

    private String monthlyMetricValue(ReportDTO row) {
        if ("avg_amount".equals(row.getLabel())) {
            return "¥ " + formatAmount(row.getAmount());
        }
        if ("total_count".equals(row.getLabel())) {
            return row.getCount() + " 笔 · ¥ " + formatAmount(row.getAmount());
        }
        return row.getCount() + " 笔";
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
        String text = value == null ? "" : value.toString();
        return text.startsWith("全部") ? "" : text;
    }

    private void showLoadError(String title, Exception ex) {
        String message = title + "加载失败：" + rootMessage(ex);
        if (title.startsWith("月度报表")) {
            monthlyStatusLabel.setForeground(AppTheme.DANGER);
            monthlyStatusLabel.setText(message);
            monthlyValueLabels.forEach((key, label) -> {
                if (!"period".equals(key)) {
                    label.setText("—");
                }
            });
        } else if (title.startsWith("系统日志")) {
            auditStatusLabel.setForeground(AppTheme.DANGER);
            auditStatusLabel.setText(message);
        } else {
            aggregateStatusLabel.setForeground(AppTheme.DANGER);
            aggregateStatusLabel.setText(message);
        }
    }

    @Override
    public void removeNotify() {
        cancelWorker(monthlyWorker);
        cancelWorker(aggregateWorker);
        cancelWorker(auditWorker);
        super.removeNotify();
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
