package com.ticket.ui.admin;

import com.ticket.dto.ConnectionPoolStatusDTO;
import com.ticket.dto.CrossTicketDTO;
import com.ticket.dto.HealthCheckDTO;
import com.ticket.dto.PageResult;
import com.ticket.dto.ReportDTO;
import com.ticket.model.Category;
import com.ticket.model.Comment;
import com.ticket.model.ItemDetail;
import com.ticket.model.User;
import com.ticket.service.BusinessService;
import com.ticket.service.CategoryService;
import com.ticket.service.ConnectionPoolMonitorService;
import com.ticket.service.CrossDatabaseQueryService;
import com.ticket.service.MaintenanceService;
import com.ticket.service.StatisticsService;
import com.ticket.service.SystemHealthService;
import com.ticket.service.UserService;
import com.ticket.ui.MainFrame;
import com.ticket.ui.component.TextEntryDialog;
import com.ticket.ui.theme.AppTheme;
import com.ticket.ui.theme.StatusTagRenderer;
import com.ticket.util.CategoryDisplayUtil;
import com.ticket.util.TimeFormatUtil;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

public class AdminWorkbenchPanel extends JPanel {
    private final MainFrame mainFrame;
    private final BusinessService businessService = new BusinessService();
    private final CrossDatabaseQueryService crossDatabaseQueryService = new CrossDatabaseQueryService();
    private final StatisticsService statisticsService = new StatisticsService();
    private final UserService userService = new UserService();
    private final MaintenanceService maintenanceService = new MaintenanceService();
    private final SystemHealthService systemHealthService = new SystemHealthService();
    private final ConnectionPoolMonitorService connectionPoolMonitorService = new ConnectionPoolMonitorService();
    private final CategoryService categoryService = new CategoryService();
    private final JLabel headerLabel = new JLabel("未登录");
    private final CardLayout workspaceLayout = new CardLayout();
    private final JPanel workspaceCards = new JPanel(workspaceLayout);
    private final JPanel homePanel = new JPanel(new BorderLayout(0, 18));
    private final JLabel homeDateLabel = new JLabel("—");
    private final JLabel homeTimeLabel = new JLabel("—");
    private final JLabel homePeriodLabel = AppTheme.muted("本月");
    private final JLabel homeTotalLabel = new JLabel("—");
    private final JLabel homePendingLabel = new JLabel("—");
    private final JLabel homeProcessingLabel = new JLabel("—");
    private final JLabel homeCompletedLabel = new JLabel("—");
    private final JTextArea centerArea = new JTextArea();
    private final JTextArea rightArea = new JTextArea();
    private final java.util.Map<String, JButton> navigationButtons = new java.util.LinkedHashMap<>();
    private final java.util.Map<Long, String> categoryDisplayNames = new java.util.LinkedHashMap<>();
    private JPanel activeModulePage;
    private Runnable activeModuleCleanup = () -> { };
    private String activeModuleName;
    private String selectedModuleName;
    private User currentUser;
    private final Timer homeClockTimer = new Timer(1000, event -> updateHomeClock());
    private SwingWorker<List<ReportDTO>, Void> homeSummaryWorker;

    public AdminWorkbenchPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());
        setBackground(AppTheme.PAGE);
        JButton changePasswordButton = new JButton("修改密码");
        JButton logoutButton = new JButton("退出登录");
        AppTheme.secondary(changePasswordButton);
        AppTheme.secondary(logoutButton);
        headerLabel.setForeground(AppTheme.MUTED);
        add(AppTheme.pageHeader("管理员中心", "工单、用户、数据与系统工具统一入口",
            headerLabel, changePasswordButton, logoutButton), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(AppTheme.PAGE);
        body.add(createNavigationPanel(), BorderLayout.WEST);
        body.add(createWorkspacePanel(), BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        changePasswordButton.addActionListener(event -> {
            if (currentUser != null) {
                mainFrame.showPasswordChange(currentUser, false);
            }
        });
        logoutButton.addActionListener(event -> mainFrame.logout());
    }

    private JPanel createNavigationPanel() {
        JPanel navigation = new JPanel();
        navigation.setLayout(new javax.swing.BoxLayout(navigation, javax.swing.BoxLayout.Y_AXIS));
        navigation.setBackground(new java.awt.Color(248, 249, 250));
        navigation.setBorder(javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createMatteBorder(0, 0, 0, 1, AppTheme.BORDER),
            javax.swing.BorderFactory.createEmptyBorder(14, 12, 14, 12)));
        navigation.setPreferredSize(new Dimension(205, 0));

        addNavigationSection(navigation, "概览");
        addNavigationButton(navigation, "工作概览", "工作概览");
        addNavigationSection(navigation, "业务管理");
        addNavigationButton(navigation, "全部工单", "工单处理");
        addNavigationButton(navigation, "用户管理", "用户管理");
        addNavigationButton(navigation, "分类管理", "分类管理");
        addNavigationSection(navigation, "数据分析");
        addNavigationButton(navigation, "数据统计", "数据报表");
        addNavigationButton(navigation, "行为日志", "行为日志");
        addNavigationButton(navigation, "系统日志", "系统日志");
        addNavigationSection(navigation, "系统工具");
        addNavigationButton(navigation, "系统自检", "系统自检");
        addNavigationButton(navigation, "连接池监控", "连接池监控");
        addNavigationButton(navigation, "批量维护", "批量维护");
        navigation.add(javax.swing.Box.createVerticalGlue());

        return navigation;
    }

    private void addNavigationSection(JPanel navigation, String title) {
        if (navigation.getComponentCount() > 0) {
            navigation.add(javax.swing.Box.createVerticalStrut(12));
        }
        JLabel label = new JLabel(title);
        label.setForeground(AppTheme.MUTED);
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD, 12f));
        label.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 10, 5, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        navigation.add(label);
    }

    private void addNavigationButton(JPanel navigation, String moduleName, String label) {
        JButton button = new JButton(label);
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        button.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBackground(new java.awt.Color(248, 249, 250));
        button.setForeground(AppTheme.TEXT);
        button.setBorder(javax.swing.BorderFactory.createEmptyBorder(9, 12, 9, 12));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.addActionListener(event -> openModule(moduleName));
        navigationButtons.put(moduleName, button);
        navigation.add(button);
        navigation.add(javax.swing.Box.createVerticalStrut(2));
    }

    private JPanel createWorkspacePanel() {
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(AppTheme.PAGE);
        workspaceCards.setBackground(AppTheme.PAGE);
        workspaceCards.setBorder(javax.swing.BorderFactory.createEmptyBorder(16, 18, 18, 18));
        buildHomePanel();
        workspaceCards.add(homePanel, "工作概览");
        workspace.add(workspaceCards, BorderLayout.CENTER);
        return workspace;
    }

    private void buildHomePanel() {
        homePanel.setBackground(AppTheme.PAGE);

        JPanel welcome = AppTheme.surface(new BorderLayout(20, 0));
        JPanel welcomeText = new JPanel(new java.awt.GridLayout(0, 1, 0, 5));
        welcomeText.setOpaque(false);
        JLabel title = new JLabel("工作概览");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 26f));
        welcomeText.add(title);
        welcomeText.add(homePeriodLabel);
        welcome.add(welcomeText, BorderLayout.WEST);

        JPanel clock = new JPanel(new java.awt.GridLayout(0, 1, 0, 3));
        clock.setOpaque(false);
        homeDateLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        homeDateLabel.setForeground(AppTheme.MUTED);
        homeTimeLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        homeTimeLabel.setForeground(AppTheme.PRIMARY_DARK);
        homeTimeLabel.setFont(homeTimeLabel.getFont().deriveFont(java.awt.Font.BOLD, 30f));
        clock.add(homeDateLabel);
        clock.add(homeTimeLabel);
        welcome.add(clock, BorderLayout.EAST);
        homePanel.add(welcome, BorderLayout.NORTH);

        JPanel metrics = new JPanel(new java.awt.GridLayout(1, 4, 14, 0));
        metrics.setOpaque(false);
        metrics.add(createMetricCard("工单总量", homeTotalLabel));
        metrics.add(createMetricCard("待处理", homePendingLabel));
        metrics.add(createMetricCard("处理中", homeProcessingLabel));
        metrics.add(createMetricCard("已完成", homeCompletedLabel));
        JPanel metricArea = new JPanel(new BorderLayout());
        metricArea.setOpaque(false);
        metricArea.add(metrics, BorderLayout.NORTH);
        homePanel.add(metricArea, BorderLayout.CENTER);
        updateHomeClock();
    }

    private void updateHomeClock() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        homeDateLabel.setText(now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE", java.util.Locale.CHINA)));
        homeTimeLabel.setText(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    private JPanel createMetricCard(String title, JLabel valueLabel) {
        JPanel card = AppTheme.surface(new BorderLayout(0, 8));
        valueLabel.setFont(valueLabel.getFont().deriveFont(java.awt.Font.BOLD, 28f));
        valueLabel.setForeground(AppTheme.TEXT);
        card.add(AppTheme.muted(title), BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private void loadHomeSummary() {
        User actor = currentUser;
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        homePeriodLabel.setText(now.getYear() + "年" + now.getMonthValue() + "月");
        homeTotalLabel.setText("—");
        homePendingLabel.setText("—");
        homeProcessingLabel.setText("—");
        homeCompletedLabel.setText("—");
        if (homeSummaryWorker != null && !homeSummaryWorker.isDone()) {
            homeSummaryWorker.cancel(true);
        }
        homeSummaryWorker = new SwingWorker<>() {
            @Override
            protected List<ReportDTO> doInBackground() {
                return statisticsService.monthlyReport(actor, now.getYear(), now.getMonthValue());
            }

            @Override
            protected void done() {
                if (isCancelled() || actor != currentUser || !isActiveModulePage("工作概览", homePanel)) {
                    return;
                }
                try {
                    Map<String, Long> counts = get().stream().collect(Collectors.toMap(
                        ReportDTO::getLabel, ReportDTO::getCount, (left, right) -> right));
                    homeTotalLabel.setText(String.valueOf(counts.getOrDefault("total_count", 0L)));
                    homePendingLabel.setText(String.valueOf(counts.getOrDefault("pending_count", 0L)));
                    homeProcessingLabel.setText(String.valueOf(counts.getOrDefault("processing_count", 0L)));
                    homeCompletedLabel.setText(String.valueOf(counts.getOrDefault("completed_count", 0L)));
                } catch (Exception ex) {
                    homePeriodLabel.setText("数据加载失败");
                }
            }
        };
        homeSummaryWorker.execute();
    }

    private void openModule(String moduleName) {
        showModuleHint(moduleName);
        handleModuleClick(moduleName);
    }

    private void handleModuleClick(String moduleName) {
        switch (moduleName) {
            case "工作概览" -> showAdminHome();
            case "全部工单" -> showTicketManager();
            case "用户管理" -> showUserManager();
            case "分类管理" -> showCategoryManager();
            case "数据统计" -> loadStats();
            case "行为日志" -> loadBehaviorLogs();
            case "系统日志" -> loadSystemLogs();
            case "系统自检" -> runHealthCheck();
            case "连接池监控" -> showConnectionPoolStatus();
            case "批量维护" -> showMaintenancePanel();
            default -> {
                centerArea.setText("已选择：" + moduleName);
                rightArea.setText("暂无可用操作。");
            }
        }
    }

    private void showModuleHint(String moduleName) {
        selectedModuleName = moduleName;
        updateNavigationSelection(moduleName);
        switch (moduleName) {
            case "工作概览" -> {
                centerArea.setText("所有功能都已集中到左侧导航。单击栏目后，内容会直接在右侧工作区切换。");
                rightArea.setText("当前会话已就绪。建议先处理待办工单，再检查用户、报表与系统状态。");
            }
            case "全部工单" -> {
                centerArea.setText("按标题、状态和分配客服筛选工单；选中工单后可查看详情、回复、添加内部备注、流转状态或分配客服。");
                rightArea.setText("工单处理工作区已打开。完成操作后，这里会保留当前工单摘要。");
            }
            case "用户管理" -> {
                centerArea.setText("按用户名、邮箱和账号状态筛选用户，并对账号执行启用或禁用操作。");
                rightArea.setText("账号状态变更会经过 ADMIN 权限校验并写入审计日志。");
            }
            case "分类管理" -> {
                centerArea.setText("分类采用严格两级结构：一级分类用于归组，二级分类用于细分工单。");
                rightArea.setText("只有一级分类可作为父分类；删除前会检查二级分类与关联工单。");
            }
            case "数据统计" -> {
                centerArea.setText("查看月度工单报表、行为聚合统计和系统日志审计结果。");
                rightArea.setText("月度报表使用 MySQL 存储过程，行为与日志统计使用 MongoDB 聚合管道。");
            }
            case "行为日志" -> {
                centerArea.setText("查看行为类型、近 30 天趋势、热门工单、用户活跃度、评分与评论聚合结果。");
                rightArea.setText("行为日志保存在 MongoDB，并通过聚合管道生成统计结果。");
            }
            case "系统日志" -> {
                centerArea.setText("按类型、级别、用户和关键词查询审计日志，并查看类型、级别、用户与趋势汇总。");
                rightArea.setText("登录、状态变更、批处理与异常等关键操作均可在此追踪。");
            }
            case "系统自检" -> {
                centerArea.setText("检查 MySQL 读写连接、MongoDB 连接、分类 DAO 和工单分页查询。");
                rightArea.setText("自检正在后台执行，完成后会显示各检查项结果。");
            }
            case "连接池监控" -> {
                centerArea.setText("实时查看 READ/WRITE 两个 HikariCP 连接池的占用、空闲、等待线程、使用率与超时配置。");
                rightArea.setText("监控窗口每 1 秒后台刷新；模拟占用会根据当前负载保留业务连接余量。");
            }
            case "批量维护" -> {
                centerArea.setText("将 30 天前仍为待处理状态的工单批量流转为已取消。");
                rightArea.setText("这是高影响操作，系统会在执行前要求再次确认。");
            }
            default -> {
                centerArea.setText("已选择：" + moduleName);
                rightArea.setText("暂无模块状态。");
            }
        }
    }

    private void updateNavigationSelection(String moduleName) {
        for (var entry : navigationButtons.entrySet()) {
            boolean selected = entry.getKey().equals(moduleName);
            entry.getValue().setBackground(selected ? new java.awt.Color(232, 243, 255) : new java.awt.Color(248, 249, 250));
            entry.getValue().setForeground(selected ? AppTheme.PRIMARY_DARK : AppTheme.TEXT);
            entry.getValue().setFont(entry.getValue().getFont().deriveFont(selected ? java.awt.Font.BOLD : java.awt.Font.PLAIN));
        }
    }

    private String moduleSubtitle(String moduleName) {
        return switch (moduleName) {
            case "工作概览" -> "查看当前时间、工作提示与系统状态";
            case "全部工单" -> "查询工单并完成回复、备注、分配和状态流转";
            case "用户管理" -> "筛选账号并管理启用状态";
            case "分类管理" -> "维护工单使用的一级和二级分类";
            case "数据统计" -> "查看 MySQL 月度报表与 MongoDB 聚合统计";
            case "行为日志" -> "分析用户行为、评论和评分数据";
            case "系统日志" -> "查询关键操作、异常和审计记录";
            case "系统自检" -> "验证数据库连接与核心查询链路";
            case "连接池监控" -> "观察 HikariCP 读写连接池实时状态";
            case "批量维护" -> "处理长期未响应的待处理工单";
            default -> "管理员功能";
        };
    }

    private boolean isSelectedModule(String moduleName) {
        return moduleName.equals(selectedModuleName);
    }

    private boolean focusExistingModulePage(String moduleName) {
        return moduleName.equals(activeModuleName) && activeModulePage != null;
    }

    private void registerModulePage(String moduleName, JPanel page) {
        registerModulePage(moduleName, page, () -> { });
    }

    private void registerModulePage(String moduleName, JPanel page, Runnable cleanup) {
        closeActiveModule();
        activeModuleName = moduleName;
        activeModulePage = page;
        activeModuleCleanup = cleanup == null ? () -> { } : cleanup;
        workspaceCards.removeAll();
        workspaceCards.add(page, moduleName);
        workspaceLayout.show(workspaceCards, moduleName);
        workspaceCards.revalidate();
        workspaceCards.repaint();
    }

    private void closeActiveModule() {
        try {
            activeModuleCleanup.run();
        } finally {
            activeModuleCleanup = () -> { };
            activeModulePage = null;
            activeModuleName = null;
        }
    }

    private boolean isActiveModulePage(String moduleName, JPanel page) {
        return currentUser != null && moduleName.equals(activeModuleName) && activeModulePage == page;
    }

    private JPanel createModulePage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(AppTheme.PAGE);
        return page;
    }

    private void showTicketManager() {
        try {
            if (focusExistingModulePage("全部工单")) {
                return;
            }
            List<AdminOption> adminOptions = activeAdminOptions();
            java.util.Map<String, String> adminNameById = adminNameById(adminOptions);
            JComboBox<AssignmentFilter> assignmentFilterBox = new JComboBox<>(
                buildAssignmentFilters(adminOptions).toArray(new AssignmentFilter[0]));
            AppTheme.styleComboBox(assignmentFilterBox);
            DefaultTableModel model = new DefaultTableModel(
                new Object[]{"记录编号", "工单编号", "标题", "用户", "状态", "分类", "优先级", "分配客服", "创建时间"}, 0);
            JTable table = new JTable(model);
            table.setDefaultEditor(Object.class, null);
            AppTheme.styleTable(table);
            table.getColumnModel().getColumn(4).setCellRenderer(new StatusTagRenderer(StatusTagRenderer.Kind.STATUS));
            table.getColumnModel().getColumn(6).setCellRenderer(new StatusTagRenderer(StatusTagRenderer.Kind.PRIORITY));
            JTextArea detailArea = new JTextArea(14, 42);
            configureReadOnlyArea(detailArea);
            List<CrossTicketDTO> tickets = new ArrayList<>();

            JTextField keywordField = new JTextField(18);
            JComboBox<String> statusFilter = new JComboBox<>(new String[]{
                "全部状态", "0 待处理", "1 处理中", "2 已完成", "3 已关闭", "4 已取消"
            });
            AppTheme.styleComboBox(statusFilter);
            AppTheme.styleInput(keywordField);
            JButton refreshButton = new JButton("刷新");
            JButton detailButton = new JButton("查看详情");
            JButton replyButton = new JButton("客服回复");
            JButton noteButton = new JButton("内部备注");
            JButton statusButton = new JButton("状态流转");
            JButton assignButton = new JButton("分配客服");
            JButton previousPageButton = new JButton("上一页");
            JButton nextPageButton = new JButton("下一页");
            JLabel ticketPageLabel = AppTheme.muted("第 1 页");
            AppTheme.secondary(refreshButton);
            AppTheme.secondary(detailButton);
            AppTheme.primary(replyButton);
            AppTheme.secondary(noteButton);
            AppTheme.secondary(statusButton);
            AppTheme.secondary(assignButton);
            AppTheme.secondary(previousPageButton);
            AppTheme.secondary(nextPageButton);
            previousPageButton.setEnabled(false);
            nextPageButton.setEnabled(false);
            detailButton.setEnabled(false);
            setTicketActionButtonsEnabled(false, replyButton, noteButton, statusButton, assignButton);

            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            toolbar.setOpaque(false);
            toolbar.add(new JLabel("标题"));
            toolbar.add(keywordField);
            toolbar.add(new JLabel("状态"));
            toolbar.add(statusFilter);
            toolbar.add(new JLabel("分配客服"));
            toolbar.add(assignmentFilterBox);
            toolbar.add(refreshButton);

            JPanel pagination = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            pagination.setOpaque(false);
            pagination.add(ticketPageLabel);
            pagination.add(previousPageButton);
            pagination.add(nextPageButton);
            JPanel tablePanel = AppTheme.surface(new BorderLayout(0, 8));
            JLabel tableTitle = new JLabel("工单列表");
            tableTitle.setFont(tableTitle.getFont().deriveFont(java.awt.Font.BOLD, 15f));
            tablePanel.add(tableTitle, BorderLayout.NORTH);
            tablePanel.add(AppTheme.scroll(table), BorderLayout.CENTER);
            tablePanel.add(pagination, BorderLayout.SOUTH);

            JPanel detailActions = new JPanel(new java.awt.GridLayout(0, 2, 8, 8));
            detailActions.setOpaque(false);
            detailActions.add(detailButton);
            detailActions.add(replyButton);
            detailActions.add(noteButton);
            detailActions.add(statusButton);
            detailActions.add(assignButton);
            JPanel detailPanel = AppTheme.surface(new BorderLayout(0, 8));
            JLabel detailTitle = new JLabel("工单详情与处理");
            detailTitle.setFont(detailTitle.getFont().deriveFont(java.awt.Font.BOLD, 15f));
            detailPanel.add(detailTitle, BorderLayout.NORTH);
            detailPanel.add(AppTheme.scroll(detailArea), BorderLayout.CENTER);
            detailPanel.add(detailActions, BorderLayout.SOUTH);

            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tablePanel, detailPanel);
            splitPane.setResizeWeight(0.68);
            splitPane.setDividerSize(6);
            splitPane.setBorder(null);
            JTextArea noticeArea = createNoticeArea("就绪");

            JPanel page = createModulePage();
            JPanel heading = new JPanel(new BorderLayout());
            heading.setBackground(AppTheme.PAGE);
            heading.add(AppTheme.pageHeader("工单管理与处理", "筛选工单，在同一窗口完成回复、备注、分配和状态流转"), BorderLayout.NORTH);
            JPanel toolbarCard = AppTheme.surface(new BorderLayout());
            toolbarCard.add(toolbar, BorderLayout.CENTER);
            heading.add(toolbarCard, BorderLayout.CENTER);
            page.add(heading, BorderLayout.NORTH);
            page.add(splitPane, BorderLayout.CENTER);
            page.add(createNoticePane(noticeArea), BorderLayout.SOUTH);

            int[] ticketPage = {1};
            long[] ticketRequestVersion = {0};
            Runnable loadTickets = () -> {
                loadTicketRows(model, tickets, keywordField.getText(), statusFilter.getSelectedIndex(),
                    selectedAssignmentFilter(assignmentFilterBox), ticketPage[0], ticketRequestVersion,
                    adminNameById, detailArea, noticeArea, ticketPageLabel, previousPageButton, nextPageButton);
                detailButton.setEnabled(false);
                updateTicketActionButtons(table, tickets, noticeArea, replyButton, noteButton, statusButton, assignButton);
            };
            keywordField.addActionListener(event -> {
                ticketPage[0] = 1;
                loadTickets.run();
            });
            statusFilter.addActionListener(event -> {
                ticketPage[0] = 1;
                loadTickets.run();
            });
            assignmentFilterBox.addActionListener(event -> {
                ticketPage[0] = 1;
                loadTickets.run();
            });
            refreshButton.addActionListener(event -> loadTickets.run());
            previousPageButton.addActionListener(event -> {
                if (ticketPage[0] > 1) {
                    ticketPage[0]--;
                    loadTickets.run();
                }
            });
            nextPageButton.addActionListener(event -> {
                ticketPage[0]++;
                loadTickets.run();
            });
            table.getSelectionModel().addListSelectionListener(event -> {
                if (!event.getValueIsAdjusting()) {
                    int selectedRow = table.getSelectedRow();
                    detailButton.setEnabled(selectedRow >= 0 && selectedRow < tickets.size());
                    updateTicketActionButtons(table, tickets, noticeArea, replyButton, noteButton, statusButton, assignButton);
                }
            });
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() >= 2) {
                        showSelectedTicketDetail(page, table, tickets, detailArea, noticeArea);
                    }
                }
            });
            detailButton.addActionListener(event -> showSelectedTicketDetail(page, table, tickets, detailArea, noticeArea));
            replyButton.addActionListener(event -> addTicketText(page, table, tickets, false, loadTickets, detailArea, noticeArea));
            noteButton.addActionListener(event -> addTicketText(page, table, tickets, true, loadTickets, detailArea, noticeArea));
            statusButton.addActionListener(event -> changeTicketStatus(page, table, tickets, loadTickets, detailArea, noticeArea));
            assignButton.addActionListener(event -> assignTicket(page, table, tickets, loadTickets, detailArea, noticeArea));

            registerModulePage("全部工单", page);
            loadTickets.run();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void loadTicketRows(DefaultTableModel model, List<CrossTicketDTO> tickets,
                                String keyword, int statusIndex, AssignmentFilter assignmentFilter,
                                int page, long[] requestVersions,
                                java.util.Map<String, String> adminNameById, JTextArea detailArea, JTextArea noticeArea,
                                JLabel pageLabel, JButton previousPageButton, JButton nextPageButton) {
        Integer status = statusIndex <= 0 ? null : statusIndex - 1;
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        User actor = currentUser;
        long requestVersion = ++requestVersions[0];
        detailArea.setText("工单加载中…");
        pageLabel.setText("第 " + page + " 页 · 加载中…");
        previousPageButton.setEnabled(false);
        nextPageButton.setEnabled(false);
        new SwingWorker<TicketPageLoad, Void>() {
            @Override
            protected TicketPageLoad doInBackground() {
                PageResult<CrossTicketDTO> ticketPage = crossDatabaseQueryService.pageTickets(
                    actor, status, normalizedKeyword, page, 50);
                java.util.Map<Long, String> displayNames = CategoryDisplayUtil.buildDisplayNames(
                    categoryService.listCategories(actor));
                return new TicketPageLoad(ticketPage, displayNames);
            }

            @Override
            protected void done() {
                if (actor != currentUser || requestVersion != requestVersions[0]) {
                    return;
                }
                try {
                    TicketPageLoad load = get();
                    PageResult<CrossTicketDTO> result = load.page();
                    categoryDisplayNames.clear();
                    categoryDisplayNames.putAll(load.categoryDisplayNames());
                    int totalPages = Math.max(1, (int) Math.ceil(result.getTotal() / 50.0));
                    pageLabel.setText("第 " + page + " / " + totalPages + " 页 · 共 " + result.getTotal() + " 条");
                    previousPageButton.setEnabled(page > 1);
                    nextPageButton.setEnabled(page < totalPages);
                    renderTicketRows(result, model, tickets, status, assignmentFilter, adminNameById, detailArea, noticeArea);
                } catch (Exception ex) {
                    pageLabel.setText("第 " + page + " 页 · 加载失败");
                    previousPageButton.setEnabled(page > 1);
                    JOptionPane.showMessageDialog(AdminWorkbenchPanel.this, "加载工单失败：" + ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void renderTicketRows(PageResult<CrossTicketDTO> result, DefaultTableModel model, List<CrossTicketDTO> tickets,
                                  Integer status, AssignmentFilter assignmentFilter,
                                  java.util.Map<String, String> adminNameById, JTextArea detailArea, JTextArea noticeArea) {
        try {
            tickets.clear();
            model.setRowCount(0);
            for (CrossTicketDTO ticket : result.getRecords()) {
                if (status != null && (ticket.getOrder() == null || !status.equals(ticket.getOrder().getStatus()))) {
                    continue;
                }
                if (!matchesAssignmentFilter(ticket, assignmentFilter)) {
                    continue;
                }
                tickets.add(ticket);
                ItemDetail.Metadata metadata = ticket.getItemDetail() == null
                    ? null : ticket.getItemDetail().getMetadata();
                String assignedAdminId = metadata == null ? null : metadata.getAssignedAdminId();
                model.addRow(new Object[]{
                    ticket.getOrder() == null ? "" : ticket.getOrder().getOrderId(),
                    ticket.getItem() == null ? "" : ticket.getItem().getItemId(),
                    ticket.getItem() == null ? "" : ticket.getItem().getTitle(),
                    ticket.getUser() == null ? "" : ticket.getUser().getUsername(),
                    ticket.getOrder() == null ? "" : statusText(ticket.getOrder().getStatus()),
                    categoryDisplay(ticket.getCategory()),
                    metadata == null ? "" : metadata.getPriority(),
                    assignedAdminDisplay(assignedAdminId, adminNameById),
                    ticket.getOrder() == null ? "—" : TimeFormatUtil.format(ticket.getOrder().getCreatedAt())
                });
            }
            detailArea.setText("工单数：" + tickets.size() + "\n分配筛选：" + assignmentFilter);
            if (isSelectedModule("全部工单")) {
                centerArea.setText("工单管理：已加载 " + tickets.size() + " 条记录。");
            }
            showNotice(noticeArea, "工单管理已加载 " + tickets.size() + " 条记录；分配分类：" + assignmentFilter + "。");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "加载工单失败：" + ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showSelectedTicketDetail(JTable table, List<CrossTicketDTO> tickets, JTextArea detailArea) {
        showSelectedTicketDetail(this, table, tickets, detailArea, null);
    }

    private void showSelectedTicketDetail(Component parent, JTable table, List<CrossTicketDTO> tickets,
                                          JTextArea detailArea, JTextArea noticeArea) {
        CrossTicketDTO ticket = selectedTicket(parent, table, tickets);
        if (ticket == null) {
            return;
        }
        try {
            CrossTicketDTO freshTicket = crossDatabaseQueryService.getTicket(currentUser, ticket.getItem().getItemId());
            detailArea.setText(formatTicketDetail(freshTicket));
            detailArea.setCaretPosition(0);
            if (isSelectedModule("全部工单")) {
                refreshRightTicketSummary(freshTicket);
            }
            showNotice(noticeArea, "已打开工单 " + freshTicket.getItem().getItemId() + " 的详情。");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "加载详情失败：" + ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        } finally {
            bringParentToFront(parent);
        }
    }

    private void addTicketText(Component parent, JTable table, List<CrossTicketDTO> tickets, boolean internalNote,
                               Runnable reload, JTextArea detailArea, JTextArea noticeArea) {
        CrossTicketDTO ticket = selectedTicket(parent, table, tickets);
        if (ticket == null) {
            return;
        }
        if (!ensureCanProcessTicket(parent, ticket, noticeArea)) {
            return;
        }
        Long itemId = ticket.getItem().getItemId();
        String title = internalNote ? "新增内部备注" : "新增客服回复";
        TextEntryDialog.Result result = TextEntryDialog.show(parent, title,
            internalNote ? "请输入仅管理员可见的备注" : "请输入客服回复内容", null, 8, 42);
        if (!result.accepted()) {
            bringParentToFront(parent);
            return;
        }
        try {
            if (internalNote) {
                businessService.addInternalNote(currentUser, ticket.getItem().getItemId(), result.text());
            } else {
                businessService.addAgentReply(currentUser, ticket.getItem().getItemId(), result.text());
            }
            reload.run();
            refreshTicketAfterOperation(parent, table, tickets, itemId, detailArea, noticeArea,
                (internalNote ? "内部备注" : "客服回复") + "已保存。");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        } finally {
            bringParentToFront(parent);
        }
    }

    private void changeTicketStatus(Component parent, JTable table, List<CrossTicketDTO> tickets, Runnable reload,
                                    JTextArea detailArea, JTextArea noticeArea) {
        CrossTicketDTO ticket = selectedTicket(parent, table, tickets);
        if (ticket == null || ticket.getOrder() == null) {
            return;
        }
        if (!ensureCanProcessTicket(parent, ticket, noticeArea)) {
            return;
        }
        Long itemId = ticket.getItem().getItemId();
        String[] statusOptions = statusOptions(ticket.getOrder().getStatus());
        if (statusOptions.length == 0) {
            JOptionPane.showMessageDialog(parent, "当前状态不能继续流转。", "提示", JOptionPane.INFORMATION_MESSAGE);
            bringParentToFront(parent);
            return;
        }
        JComboBox<String> statusBox = new JComboBox<>(statusOptions);
        int confirm = JOptionPane.showConfirmDialog(parent, statusBox, "选择新状态", JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) {
            bringParentToFront(parent);
            return;
        }
        int newStatus = Integer.parseInt(String.valueOf(statusBox.getSelectedItem()).substring(0, 1));
        try {
            businessService.changeOrderStatus(currentUser, ticket.getOrder().getOrderId(), newStatus);
            reload.run();
            refreshTicketAfterOperation(parent, table, tickets, itemId, detailArea, noticeArea, "状态已更新。");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        } finally {
            bringParentToFront(parent);
        }
    }

    private void assignTicket(Component parent, JTable table, List<CrossTicketDTO> tickets, Runnable reload,
                              JTextArea detailArea, JTextArea noticeArea) {
        CrossTicketDTO ticket = selectedTicket(parent, table, tickets);
        if (ticket == null) {
            return;
        }
        if (!ensureCanProcessTicket(parent, ticket, noticeArea)) {
            return;
        }
        Long itemId = ticket.getItem().getItemId();
        List<AdminOption> adminOptions = activeAdminOptions();
        if (adminOptions.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "暂无可分配的启用 ADMIN 用户。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JComboBox<AdminOption> adminBox = new JComboBox<>(adminOptions.toArray(new AdminOption[0]));
        selectCurrentAdmin(adminBox);
        int confirm = JOptionPane.showConfirmDialog(parent, adminBox, "选择客服管理员", JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) {
            bringParentToFront(parent);
            return;
        }
        try {
            AdminOption selectedAdmin = (AdminOption) adminBox.getSelectedItem();
            if (selectedAdmin == null) {
                throw new IllegalArgumentException("请选择客服管理员");
            }
            businessService.assignAdmin(currentUser, ticket.getItem().getItemId(), selectedAdmin.user().getUserId());
            reload.run();
            refreshTicketAfterOperation(parent, table, tickets, itemId, detailArea, noticeArea, "客服分配已保存。");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        } finally {
            bringParentToFront(parent);
        }
    }

    private CrossTicketDTO selectedTicket(JTable table, List<CrossTicketDTO> tickets) {
        return selectedTicket(this, table, tickets);
    }

    private CrossTicketDTO selectedTicket(Component parent, JTable table, List<CrossTicketDTO> tickets) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= tickets.size()) {
            JOptionPane.showMessageDialog(parent, "请先选择一条工单。", "提示", JOptionPane.INFORMATION_MESSAGE);
            bringParentToFront(parent);
            return null;
        }
        return tickets.get(selectedRow);
    }

    private void updateTicketActionButtons(JTable table, List<CrossTicketDTO> tickets, JTextArea noticeArea,
                                           JButton replyButton, JButton noteButton, JButton statusButton, JButton assignButton) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= tickets.size()) {
            setTicketActionButtonsEnabled(false, replyButton, noteButton, statusButton, assignButton);
            return;
        }
        CrossTicketDTO ticket = tickets.get(selectedRow);
        boolean canProcess = canCurrentAdminProcess(ticket);
        setTicketActionButtonsEnabled(canProcess, replyButton, noteButton, statusButton, assignButton);
        String assignedAdminId = assignedAdminId(ticket);
        if (assignedAdminId == null || assignedAdminId.isBlank()) {
            showNotice(noticeArea, "当前工单尚未分配客服，当前管理员可处理或分配。");
        } else if (canProcess) {
            showNotice(noticeArea, "当前工单已分配给你，可继续回复、备注、流转状态或重新分配。");
        } else {
            showNotice(noticeArea, "当前工单已分配给管理员 " + assignedAdminId + "，你可以查看，但不能处理。");
        }
    }

    private void setTicketActionButtonsEnabled(boolean enabled, JButton... buttons) {
        for (JButton button : buttons) {
            button.setEnabled(enabled);
        }
    }

    private boolean ensureCanProcessTicket(Component parent, CrossTicketDTO ticket, JTextArea noticeArea) {
        if (canCurrentAdminProcess(ticket)) {
            return true;
        }
        String message = "当前工单已分配给管理员 " + assignedAdminId(ticket) + "，你可以查看，但不能处理。";
        showNotice(noticeArea, message);
        JOptionPane.showMessageDialog(parent, message, "提示", JOptionPane.INFORMATION_MESSAGE);
        return false;
    }

    private boolean canCurrentAdminProcess(CrossTicketDTO ticket) {
        String assignedAdminId = assignedAdminId(ticket);
        return assignedAdminId == null
            || assignedAdminId.isBlank()
            || (currentUser != null && assignedAdminId.equals(String.valueOf(currentUser.getUserId())));
    }

    private String assignedAdminId(CrossTicketDTO ticket) {
        if (ticket == null || ticket.getItemDetail() == null || ticket.getItemDetail().getMetadata() == null) {
            return null;
        }
        return ticket.getItemDetail().getMetadata().getAssignedAdminId();
    }

    private List<AdminOption> activeAdminOptions() {
        return userService.listUsers(currentUser).stream()
            .filter(user -> "ADMIN".equals(user.getRole()))
            .filter(user -> user.getStatus() != null && user.getStatus() == 1)
            .map(AdminOption::new)
            .toList();
    }

    private java.util.Map<String, String> adminNameById(List<AdminOption> adminOptions) {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        for (AdminOption option : adminOptions) {
            result.put(String.valueOf(option.user().getUserId()), option.user().getUsername());
        }
        return result;
    }

    private List<AssignmentFilter> buildAssignmentFilters(List<AdminOption> adminOptions) {
        List<AssignmentFilter> filters = new ArrayList<>();
        filters.add(new AssignmentFilter(AssignmentFilterKind.ALL, null, "全部工单"));
        filters.add(new AssignmentFilter(AssignmentFilterKind.UNASSIGNED, null, "未分配"));
        filters.add(new AssignmentFilter(AssignmentFilterKind.MINE, null, "我的工单"));
        for (AdminOption option : adminOptions) {
            String adminId = String.valueOf(option.user().getUserId());
            filters.add(new AssignmentFilter(AssignmentFilterKind.ADMIN, adminId, option.toString()));
        }
        return filters;
    }

    private AssignmentFilter selectedAssignmentFilter(JComboBox<AssignmentFilter> assignmentFilterBox) {
        AssignmentFilter filter = (AssignmentFilter) assignmentFilterBox.getSelectedItem();
        return filter == null ? new AssignmentFilter(AssignmentFilterKind.ALL, null, "全部工单") : filter;
    }

    private boolean matchesAssignmentFilter(CrossTicketDTO ticket, AssignmentFilter filter) {
        String assignedAdminId = assignedAdminId(ticket);
        return switch (filter.kind()) {
            case ALL -> true;
            case UNASSIGNED -> assignedAdminId == null || assignedAdminId.isBlank();
            case MINE -> currentUser != null && assignedAdminId != null
                && assignedAdminId.equals(String.valueOf(currentUser.getUserId()));
            case ADMIN -> filter.adminId() != null && filter.adminId().equals(assignedAdminId);
        };
    }

    private String assignedAdminDisplay(String assignedAdminId, java.util.Map<String, String> adminNameById) {
        if (assignedAdminId == null || assignedAdminId.isBlank()) {
            return "未分配";
        }
        String adminName = adminNameById.get(assignedAdminId);
        return adminName == null ? assignedAdminId : assignedAdminId + " - " + adminName;
    }

    private void refreshTicketAfterOperation(Component parent, JTable table, List<CrossTicketDTO> tickets,
                                             Long itemId, JTextArea detailArea, JTextArea noticeArea, String noticePrefix) {
        int row = selectTicketRow(table, tickets, itemId);
        try {
            CrossTicketDTO freshTicket = crossDatabaseQueryService.getTicket(currentUser, itemId);
            detailArea.setText(formatTicketDetail(freshTicket));
            detailArea.setCaretPosition(0);
            refreshRightTicketSummary(freshTicket);
            String suffix = row >= 0 ? "详情与右侧摘要已刷新。" : "详情与右侧摘要已刷新；该工单已不在当前筛选结果中。";
            showNotice(noticeArea, noticePrefix + suffix);
        } catch (Exception ex) {
            showNotice(noticeArea, noticePrefix + "但刷新工单详情失败：" + ex.getMessage());
            JOptionPane.showMessageDialog(parent, "刷新工单详情失败：" + ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private int selectTicketRow(JTable table, List<CrossTicketDTO> tickets, Long itemId) {
        for (int index = 0; index < tickets.size(); index++) {
            CrossTicketDTO candidate = tickets.get(index);
            if (candidate.getItem() != null && itemId.equals(candidate.getItem().getItemId())) {
                table.setRowSelectionInterval(index, index);
                return index;
            }
        }
        table.clearSelection();
        return -1;
    }

    private void refreshRightTicketSummary(CrossTicketDTO ticket) {
        rightArea.setText("当前工单：" + ticket.getItem().getTitle()
            + "\n状态：" + (ticket.getOrder() == null ? "" : statusText(ticket.getOrder().getStatus()))
            + "\n评论数：" + ticket.getCommentCount());
    }

    private void selectCurrentAdmin(JComboBox<AdminOption> adminBox) {
        if (currentUser == null || currentUser.getUserId() == null) {
            return;
        }
        for (int index = 0; index < adminBox.getItemCount(); index++) {
            AdminOption option = adminBox.getItemAt(index);
            if (currentUser.getUserId().equals(option.user().getUserId())) {
                adminBox.setSelectedIndex(index);
                return;
            }
        }
    }

    private String formatTicketDetail(CrossTicketDTO ticket) {
        StringBuilder builder = new StringBuilder();
        builder.append("工单编号：").append(ticket.getItem().getItemId()).append('\n');
        builder.append("标题：").append(ticket.getItem().getTitle()).append('\n');
        if (ticket.getOrder() != null) {
            builder.append("记录编号：").append(ticket.getOrder().getOrderId()).append('\n');
            builder.append("状态：").append(statusText(ticket.getOrder().getStatus())).append('\n');
            builder.append("金额：").append(ticket.getOrder().getAmount()).append('\n');
            builder.append("创建时间：").append(TimeFormatUtil.format(ticket.getOrder().getCreatedAt())).append('\n');
        }
        builder.append("分类：").append(categoryDisplay(ticket.getCategory())).append('\n');
        builder.append("客户：").append(ticket.getUser() == null ? "" : ticket.getUser().getUsername()).append('\n');
        if (ticket.getProfile() != null) {
            builder.append("客户资料：")
                .append(nullToEmpty(ticket.getProfile().getRealName()))
                .append(" / ")
                .append(ticket.getUser() == null ? "" : nullToEmpty(ticket.getUser().getPhone()))
                .append(" / ")
                .append(nullToEmpty(ticket.getProfile().getAddress()))
                .append('\n');
        }
        ItemDetail detail = ticket.getItemDetail();
        if (detail != null) {
            ItemDetail.Metadata metadata = detail.getMetadata();
            builder.append("优先级：").append(metadata == null ? "" : nullToEmpty(metadata.getPriority())).append('\n');
            builder.append("分配客服：").append(metadata == null ? "" : nullToEmpty(metadata.getAssignedAdminId())).append('\n');
            builder.append("\n描述：\n").append(nullToEmpty(detail.getDescription())).append('\n');
        }
        if (!ticket.getConsistencyWarnings().isEmpty()) {
            builder.append("\n数据提示：\n");
            for (String warning : ticket.getConsistencyWarnings()) {
                builder.append("- ").append(warning).append('\n');
            }
        }
        builder.append("\n回复与备注：\n");
        if (ticket.getComments().isEmpty()) {
            builder.append("暂无回复\n");
        }
        for (Comment comment : ticket.getComments()) {
            builder.append("[")
                .append(formatBeijingTime(comment.getCreatedAt()))
                .append("] 用户 ")
                .append(comment.getUserId())
                .append(" ")
                .append(comment.getTags())
                .append('\n')
                .append(nullToEmpty(comment.getContent()))
                .append("\n\n");
        }
        return builder.toString();
    }

    private String formatBeijingTime(Instant instant) {
        return TimeFormatUtil.format(instant);
    }

    private void showCategoryManager() {
        try {
            if (focusExistingModulePage("分类管理")) {
                return;
            }
            DefaultTableModel model = new DefaultTableModel(new Object[]{"分类编号", "分类名称", "层级", "父分类"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            JTable table = new JTable(model);
            AppTheme.styleTable(table);
            table.getColumnModel().getColumn(0).setPreferredWidth(80);
            table.getColumnModel().getColumn(0).setMaxWidth(100);
            table.getColumnModel().getColumn(2).setPreferredWidth(85);
            table.getColumnModel().getColumn(2).setMaxWidth(100);
            JTextField nameField = new JTextField();
            AppTheme.styleInput(nameField);
            JComboBox<ParentCategoryOption> parentBox = new JComboBox<>();
            AppTheme.styleComboBox(parentBox);
            JButton refreshButton = new JButton("刷新");
            JButton clearButton = new JButton("新建分类");
            JButton addButton = new JButton("保存为新分类");
            JButton updateButton = new JButton("保存修改");
            JButton deleteButton = new JButton("删除分类");
            AppTheme.secondary(refreshButton);
            AppTheme.secondary(clearButton);
            AppTheme.primary(addButton);
            AppTheme.secondary(updateButton);
            AppTheme.danger(deleteButton);
            updateButton.setEnabled(false);
            deleteButton.setEnabled(false);
            JLabel selectedLabel = AppTheme.muted("新建分类");
            JTextArea noticeArea = createNoticeArea("就绪");
            List<Category> categories = new ArrayList<>();

            JPanel tablePanel = AppTheme.surface(new BorderLayout(0, 8));
            JPanel tableHeading = new JPanel(new BorderLayout(8, 0));
            tableHeading.setOpaque(false);
            JLabel tableTitle = new JLabel("分类列表");
            tableTitle.setFont(tableTitle.getFont().deriveFont(java.awt.Font.BOLD, 16f));
            tableHeading.add(tableTitle, BorderLayout.WEST);
            tableHeading.add(refreshButton, BorderLayout.EAST);
            tablePanel.add(tableHeading, BorderLayout.NORTH);
            tablePanel.add(AppTheme.scroll(table), BorderLayout.CENTER);

            JPanel formPanel = AppTheme.surface(new BorderLayout(0, 12));
            JPanel formFields = new JPanel();
            formFields.setLayout(new javax.swing.BoxLayout(formFields, javax.swing.BoxLayout.Y_AXIS));
            formFields.setOpaque(false);
            JLabel formTitle = new JLabel("分类信息");
            formTitle.setFont(formTitle.getFont().deriveFont(java.awt.Font.BOLD, 16f));
            formTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            selectedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel nameLabel = new JLabel("分类名称");
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel parentLabel = new JLabel("父分类");
            parentLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            parentBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            parentBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            formFields.add(formTitle);
            formFields.add(javax.swing.Box.createVerticalStrut(4));
            formFields.add(selectedLabel);
            formFields.add(javax.swing.Box.createVerticalStrut(18));
            formFields.add(nameLabel);
            formFields.add(javax.swing.Box.createVerticalStrut(6));
            formFields.add(nameField);
            formFields.add(javax.swing.Box.createVerticalStrut(14));
            formFields.add(parentLabel);
            formFields.add(javax.swing.Box.createVerticalStrut(6));
            formFields.add(parentBox);
            JPanel formActions = new JPanel(new java.awt.GridLayout(0, 1, 0, 8));
            formActions.setOpaque(false);
            formActions.add(addButton);
            formActions.add(updateButton);
            formActions.add(clearButton);
            formActions.add(deleteButton);
            formPanel.add(formFields, BorderLayout.CENTER);
            formPanel.add(formActions, BorderLayout.SOUTH);

            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tablePanel, formPanel);
            splitPane.setResizeWeight(0.68);
            splitPane.setDividerSize(6);
            splitPane.setBorder(null);

            JPanel page = createModulePage();
            page.add(AppTheme.pageHeader("分类管理", "严格两级分类：一级归组，二级细分；仅一级分类可作为父分类"), BorderLayout.NORTH);
            JPanel body = new JPanel(new BorderLayout());
            body.setBackground(AppTheme.PAGE);
            body.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 14, 12, 14));
            body.add(splitPane, BorderLayout.CENTER);
            page.add(body, BorderLayout.CENTER);

            Runnable resetForm = () -> {
                table.clearSelection();
                nameField.setText("");
                rebuildParentCategoryOptions(parentBox, categories, null, null);
                selectedLabel.setText("新建分类");
                updateButton.setEnabled(false);
                deleteButton.setEnabled(false);
                addButton.setEnabled(true);
                nameField.requestFocusInWindow();
                showNotice(noticeArea, "新建");
            };
            Runnable refresh = () -> loadCategoryRows(model, categories, parentBox, noticeArea);
            refreshButton.addActionListener(event -> {
                refresh.run();
                resetForm.run();
            });
            table.getSelectionModel().addListSelectionListener(event -> {
                int row = table.getSelectedRow();
                if (!event.getValueIsAdjusting() && row >= 0) {
                    Long categoryId = Long.valueOf(String.valueOf(model.getValueAt(row, 0)));
                    Category category = categories.stream()
                        .filter(candidate -> categoryId.equals(candidate.getCategoryId()))
                        .findFirst().orElse(null);
                    if (category != null) {
                        nameField.setText(category.getName());
                        rebuildParentCategoryOptions(parentBox, categories, categoryId, category.getParentId());
                        selectedLabel.setText("正在编辑：" + categoryId + " - " + category.getName());
                        updateButton.setEnabled(true);
                        deleteButton.setEnabled(true);
                        addButton.setEnabled(false);
                        showNotice(noticeArea, "分类 #" + categoryId);
                    }
                }
            });
            clearButton.addActionListener(event -> resetForm.run());
            addButton.addActionListener(event -> saveCategory(page, null, nameField.getText(), selectedParentId(parentBox), () -> {
                refresh.run();
                resetForm.run();
            }));
            updateButton.addActionListener(event -> {
                int row = table.getSelectedRow();
                if (row < 0) {
                    JOptionPane.showMessageDialog(page, "请先选择分类。", "提示", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                saveCategory(page, Long.parseLong(String.valueOf(model.getValueAt(row, 0))),
                    nameField.getText(), selectedParentId(parentBox), () -> {
                        refresh.run();
                        resetForm.run();
                    });
            });
            deleteButton.addActionListener(event -> deleteSelectedCategory(table, model, () -> {
                refresh.run();
                resetForm.run();
            }));
            nameField.addActionListener(event -> {
                if (updateButton.isEnabled()) {
                    updateButton.doClick();
                } else {
                    addButton.doClick();
                }
            });

            registerModulePage("分类管理", page);
            refresh.run();
            resetForm.run();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void loadCategoryRows(DefaultTableModel model, List<Category> categories,
                                  JComboBox<ParentCategoryOption> parentBox, JTextArea noticeArea) {
        try {
            categories.clear();
            categories.addAll(categoryService.listCategories(currentUser));
            java.util.Map<Long, String> categoryNames = new java.util.LinkedHashMap<>();
            for (Category category : categories) {
                categoryNames.put(category.getCategoryId(), category.getName());
            }
            categoryDisplayNames.clear();
            categoryDisplayNames.putAll(CategoryDisplayUtil.buildDisplayNames(categories));
            model.setRowCount(0);
            for (Category category : categories) {
                Category parent = category.getParentId() == null ? null : categories.stream()
                    .filter(candidate -> category.getParentId().equals(candidate.getCategoryId()))
                    .findFirst().orElse(null);
                String parentDisplay = category.getParentId() == null ? "—"
                    : category.getParentId() + " - " + categoryNames.getOrDefault(category.getParentId(), "未知分类");
                String levelDisplay = category.getParentId() == null ? "一级分类"
                    : parent == null || parent.getParentId() != null ? "层级异常" : "二级分类";
                model.addRow(new Object[]{
                    category.getCategoryId(),
                    category.getName(),
                    levelDisplay,
                    parentDisplay
                });
            }
            rebuildParentCategoryOptions(parentBox, categories, null, null);
            if (isSelectedModule("分类管理")) {
                centerArea.setText("分类管理：已加载 " + model.getRowCount() + " 个分类。");
                rightArea.setText("当前分类数量：" + model.getRowCount());
            }
            showNotice(noticeArea, "分类数：" + model.getRowCount());
        } catch (Exception ex) {
            Component parent = SwingUtilities.getWindowAncestor(noticeArea);
            JOptionPane.showMessageDialog(parent == null ? this : parent,
                "加载分类失败：" + ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void rebuildParentCategoryOptions(JComboBox<ParentCategoryOption> parentBox, List<Category> categories,
                                              Long excludedCategoryId, Long selectedParentId) {
        parentBox.removeAllItems();
        parentBox.addItem(new ParentCategoryOption(null, "不选择（一级分类）"));
        int selectedIndex = 0;
        for (Category category : categories) {
            if (category.getParentId() != null || category.getCategoryId().equals(excludedCategoryId)) {
                continue;
            }
            parentBox.addItem(new ParentCategoryOption(category.getCategoryId(), category.getName()));
            if (category.getCategoryId().equals(selectedParentId)) {
                selectedIndex = parentBox.getItemCount() - 1;
            }
        }
        parentBox.setSelectedIndex(selectedIndex);
    }

    private Long selectedParentId(JComboBox<ParentCategoryOption> parentBox) {
        ParentCategoryOption selected = (ParentCategoryOption) parentBox.getSelectedItem();
        return selected == null ? null : selected.categoryId();
    }

    private void saveCategory(Component parent, Long categoryId, String name, Long parentId, Runnable refresh) {
        try {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("分类名称不能为空");
            }
            if (categoryId == null) {
                categoryService.createCategory(currentUser, name, parentId);
            } else {
                categoryService.updateCategory(currentUser, categoryId, name, parentId);
            }
            refresh.run();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void deleteSelectedCategory(JTable table, DefaultTableModel model, Runnable refresh) {
        Component parent = SwingUtilities.getWindowAncestor(table);
        if (parent == null) {
            parent = this;
        }
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(parent, "请先选择分类。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Long categoryId = Long.parseLong(String.valueOf(model.getValueAt(row, 0)));
        try {
            int confirm = JOptionPane.showConfirmDialog(parent, "确认删除分类 " + categoryId + "？",
                "删除确认", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                categoryService.deleteCategory(currentUser, categoryId);
                refresh.run();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
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
            default -> "未知(" + status + ")";
        };
    }

    private String roleText(String role) {
        return switch (role == null ? "" : role) {
            case "ADMIN" -> "管理员";
            case "USER" -> "普通用户";
            default -> role == null ? "" : role;
        };
    }

    private String[] statusOptions(Integer status) {
        if (status == null) {
            return new String[0];
        }
        return switch (status) {
            case 0 -> new String[]{"1 处理中", "2 已完成", "3 已关闭", "4 已取消"};
            case 1 -> new String[]{"2 已完成", "3 已关闭", "4 已取消"};
            case 2 -> new String[]{"3 已关闭"};
            default -> new String[0];
        };
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private JTextArea createNoticeArea(String message) {
        JTextArea textArea = new JTextArea(message);
        configureReadOnlyArea(textArea);
        textArea.setRows(1);
        textArea.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8));
        return textArea;
    }

    private JScrollPane createNoticePane(JTextArea noticeArea) {
        JScrollPane scrollPane = new JScrollPane(noticeArea);
        scrollPane.setPreferredSize(new Dimension(0, 38));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        return scrollPane;
    }

    private void showNotice(JTextArea noticeArea, String message) {
        if (noticeArea == null) {
            return;
        }
        noticeArea.setText(message == null ? "" : message);
        noticeArea.setCaretPosition(0);
    }

    private void bringParentToFront(Component parent) {
        if (parent != null) {
            parent.requestFocusInWindow();
        }
    }

    private void configureReadOnlyArea(JTextArea textArea) {
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
    }

    public void bindUser(User user) {
        this.currentUser = user;
        headerLabel.setText("管理员 · " + user.getUsername());
        showAdminHome();
    }

    /** 退出时停止当前模块后台任务并清除当前会话展示。 */
    public void clearSession() {
        closeActiveModule();
        homeClockTimer.stop();
        if (homeSummaryWorker != null) {
            homeSummaryWorker.cancel(true);
        }
        selectedModuleName = null;
        currentUser = null;
        categoryDisplayNames.clear();
        headerLabel.setText("未登录");
        updateNavigationSelection(null);
        centerArea.setText("");
        rightArea.setText("");
    }

    private void showAdminHome() {
        showModuleHint("工作概览");
        updateHomeClock();
        registerModulePage("工作概览", homePanel, () -> {
            homeClockTimer.stop();
            if (homeSummaryWorker != null) {
                homeSummaryWorker.cancel(true);
            }
        });
        homeClockTimer.start();
        loadHomeSummary();
    }

    private void loadStats() {
        showStatisticsDialog("月度报表", "数据统计", AdminStatisticsPanel.ViewMode.FULL);
    }

    private void loadBehaviorLogs() {
        showStatisticsDialog("行为日志统计", "行为日志", AdminStatisticsPanel.ViewMode.BEHAVIOR);
    }

    private void loadSystemLogs() {
        showStatisticsDialog("系统日志审计", "系统日志", AdminStatisticsPanel.ViewMode.SYSTEM);
    }

    private void showStatisticsDialog(String title, String moduleName, AdminStatisticsPanel.ViewMode viewMode) {
        try {
            if (focusExistingModulePage(moduleName)) {
                return;
            }
            JPanel page = createModulePage();
            page.add(AppTheme.pageHeader(title, moduleSubtitle(moduleName)), BorderLayout.NORTH);
            page.add(new AdminStatisticsPanel(statisticsService, currentUser, viewMode), BorderLayout.CENTER);
            registerModulePage(moduleName, page);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showUserManager() {
        if (focusExistingModulePage("用户管理")) {
            return;
        }
        User manager = currentUser;
        DefaultTableModel model = new DefaultTableModel(
            new Object[]{"用户ID", "用户名", "邮箱", "手机号", "角色", "状态", "密码状态", "创建时间"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable userTable = new JTable(model);
        AppTheme.styleTable(userTable);
        userTable.setAutoCreateRowSorter(true);
        userTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        userTable.getColumnModel().getColumn(0).setMaxWidth(90);
        userTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        userTable.getColumnModel().getColumn(4).setMaxWidth(100);
        userTable.getColumnModel().getColumn(5).setPreferredWidth(80);
        userTable.getColumnModel().getColumn(5).setMaxWidth(100);
        userTable.getColumnModel().getColumn(5).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                            boolean focused, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                setHorizontalAlignment(CENTER);
                String text = String.valueOf(value);
                setForeground(selected ? AppTheme.TEXT : ("已启用".equals(text) ? AppTheme.SUCCESS : AppTheme.DANGER));
                return this;
            }
        });

        JTextField keywordField = new JTextField(16);
        JComboBox<String> statusFilter = new JComboBox<>(new String[]{"全部状态", "已启用", "已禁用"});
        AppTheme.styleComboBox(statusFilter);
        AppTheme.styleInput(keywordField);
        JButton searchButton = new JButton("筛选");
        JButton resetButton = new JButton("重置");
        JButton refreshButton = new JButton("刷新");
        JButton enableButton = new JButton("启用账号");
        JButton disableButton = new JButton("禁用账号");
        JButton resetPasswordButton = new JButton("重置为临时密码");
        AppTheme.primary(searchButton);
        AppTheme.secondary(resetButton);
        AppTheme.secondary(refreshButton);
        AppTheme.secondary(enableButton);
        AppTheme.danger(disableButton);
        AppTheme.secondary(resetPasswordButton);
        enableButton.setEnabled(false);
        disableButton.setEnabled(false);
        resetPasswordButton.setEnabled(false);
        JLabel statusLabel = AppTheme.muted("正在加载用户列表…");
        JLabel selectedTitle = new JLabel("尚未选择账号");
        selectedTitle.setFont(selectedTitle.getFont().deriveFont(java.awt.Font.BOLD, 17f));
        JTextArea selectedDetails = new JTextArea("—");
        configureReadOnlyArea(selectedDetails);
        selectedDetails.setOpaque(false);
        selectedDetails.setForeground(AppTheme.MUTED);
        selectedDetails.setBorder(null);
        List<User> loadedUsers = new ArrayList<>();

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.setOpaque(false);
        toolbar.add(new JLabel("用户名/邮箱"));
        toolbar.add(keywordField);
        toolbar.add(new JLabel("状态"));
        toolbar.add(statusFilter);
        toolbar.add(searchButton);
        toolbar.add(resetButton);
        toolbar.add(refreshButton);

        JPanel page = createModulePage();
        page.add(AppTheme.pageHeader("用户管理", "筛选账号，在右侧查看详情并执行启用或禁用操作"), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setBackground(AppTheme.PAGE);
        body.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 14, 12, 14));
        JPanel filterCard = AppTheme.surface(new BorderLayout());
        filterCard.add(toolbar, BorderLayout.CENTER);
        body.add(filterCard, BorderLayout.NORTH);

        JPanel listCard = AppTheme.surface(new BorderLayout(0, 8));
        JPanel listHeading = new JPanel(new BorderLayout(8, 0));
        listHeading.setOpaque(false);
        JLabel listTitle = new JLabel("账号列表");
        listTitle.setFont(listTitle.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        listHeading.add(listTitle, BorderLayout.WEST);
        listHeading.add(statusLabel, BorderLayout.EAST);
        listCard.add(listHeading, BorderLayout.NORTH);
        listCard.add(AppTheme.scroll(userTable), BorderLayout.CENTER);

        JPanel detailCard = AppTheme.surface(new BorderLayout(0, 14));
        JPanel detailContent = new JPanel();
        detailContent.setOpaque(false);
        detailContent.setLayout(new javax.swing.BoxLayout(detailContent, javax.swing.BoxLayout.Y_AXIS));
        selectedTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        selectedDetails.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailContent.add(selectedTitle);
        detailContent.add(javax.swing.Box.createVerticalStrut(12));
        detailContent.add(selectedDetails);
        detailContent.add(javax.swing.Box.createVerticalGlue());

        JPanel accountActions = new JPanel(new java.awt.GridLayout(0, 1, 0, 8));
        accountActions.setOpaque(false);
        accountActions.add(enableButton);
        accountActions.add(disableButton);
        accountActions.add(resetPasswordButton);
        detailCard.add(detailContent, BorderLayout.CENTER);
        detailCard.add(accountActions, BorderLayout.SOUTH);
        detailCard.setPreferredSize(new Dimension(310, 0));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listCard, detailCard);
        splitPane.setResizeWeight(0.72);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);
        body.add(splitPane, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);

        Runnable clearSelectedUser = () -> {
            userTable.clearSelection();
            selectedTitle.setText("尚未选择账号");
            selectedDetails.setForeground(AppTheme.MUTED);
            selectedDetails.setText("—");
            enableButton.setEnabled(false);
            disableButton.setEnabled(false);
            resetPasswordButton.setEnabled(false);
        };

        Runnable renderUsers = () -> {
            String keyword = keywordField.getText() == null ? "" : keywordField.getText().trim().toLowerCase();
            int selectedStatus = statusFilter.getSelectedIndex();
            model.setRowCount(0);
            for (User user : loadedUsers) {
                boolean statusMatches = selectedStatus == 0
                    || (selectedStatus == 1 && Integer.valueOf(1).equals(user.getStatus()))
                    || (selectedStatus == 2 && !Integer.valueOf(1).equals(user.getStatus()));
                boolean keywordMatches = keyword.isBlank()
                    || nullToEmpty(user.getUsername()).toLowerCase().contains(keyword)
                    || nullToEmpty(user.getEmail()).toLowerCase().contains(keyword);
                if (statusMatches && keywordMatches) {
                    model.addRow(new Object[]{user.getUserId(), user.getUsername(), user.getEmail(), user.getPhone(),
                        roleText(user.getRole()), Integer.valueOf(1).equals(user.getStatus()) ? "已启用" : "已禁用",
                        Integer.valueOf(1).equals(user.getMustChangePassword()) ? "待首次换密" : "正常",
                        TimeFormatUtil.format(user.getCreatedAt())});
                }
            }
            long enabledCount = loadedUsers.stream().filter(user -> Integer.valueOf(1).equals(user.getStatus())).count();
            statusLabel.setText("共 " + loadedUsers.size() + " · 启用 " + enabledCount
                + " · 禁用 " + (loadedUsers.size() - enabledCount) + " · 显示 " + model.getRowCount());
            clearSelectedUser.run();
        };

        Runnable loadUsers = () -> {
            refreshButton.setEnabled(false);
            statusLabel.setText("用户列表加载中…");
            new SwingWorker<List<User>, Void>() {
                @Override
                protected List<User> doInBackground() {
                    return userService.listUsers(manager);
                }

                @Override
                protected void done() {
                    if (manager != currentUser) {
                        return;
                    }
                    try {
                        loadedUsers.clear();
                        loadedUsers.addAll(get());
                        renderUsers.run();
                        centerArea.setText("用户管理：共 " + loadedUsers.size() + " 个账号。可在右侧工作区筛选并调整账号状态。");
                        rightArea.setText("账号状态变更会调用 UserService 权限校验，并写入系统审计日志。");
                    } catch (Exception ex) {
                        statusLabel.setText("用户列表加载失败");
                        JOptionPane.showMessageDialog(page, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
                    } finally {
                        refreshButton.setEnabled(true);
                    }
                }
            }.execute();
        };

        userTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                int row = userTable.getSelectedRow();
                if (row < 0) {
                    clearSelectedUser.run();
                    return;
                }
                Long userId = Long.valueOf(String.valueOf(userTable.getValueAt(row, 0)));
                String username = String.valueOf(userTable.getValueAt(row, 1));
                String status = String.valueOf(userTable.getValueAt(row, 5));
                boolean currentAccount = manager != null && manager.getUserId().equals(userId);
                selectedTitle.setText(username + (currentAccount ? "（当前账号）" : ""));
                selectedDetails.setForeground(AppTheme.TEXT);
                selectedDetails.setText("用户 ID：" + userId
                    + "\n角色：" + userTable.getValueAt(row, 4)
                    + "\n状态：" + status
                    + "\n邮箱：" + nullToEmpty(userTable.getValueAt(row, 2))
                    + "\n手机号：" + nullToEmpty(userTable.getValueAt(row, 3))
                    + "\n密码状态：" + nullToEmpty(userTable.getValueAt(row, 6))
                    + "\n创建时间：" + nullToEmpty(userTable.getValueAt(row, 7)));
                enableButton.setEnabled("已禁用".equals(status));
                disableButton.setEnabled("已启用".equals(status) && !currentAccount);
                resetPasswordButton.setEnabled(!currentAccount);
            }
        });
        searchButton.addActionListener(event -> renderUsers.run());
        keywordField.addActionListener(event -> renderUsers.run());
        statusFilter.addActionListener(event -> renderUsers.run());
        resetButton.addActionListener(event -> {
            keywordField.setText("");
            statusFilter.setSelectedIndex(0);
            renderUsers.run();
        });
        refreshButton.addActionListener(event -> loadUsers.run());
        enableButton.addActionListener(event -> changeSelectedUserStatus(page, userTable, 1, loadUsers));
        disableButton.addActionListener(event -> changeSelectedUserStatus(page, userTable, 0, loadUsers));
        resetPasswordButton.addActionListener(event -> resetSelectedUserPassword(
            page, userTable, resetPasswordButton, loadUsers));

        registerModulePage("用户管理", page);
        loadUsers.run();
    }

    private void resetSelectedUserPassword(Component parent, JTable table, JButton resetButton, Runnable reload) {
        User manager = currentUser;
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(parent, "请先选择一个账号。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Long userId = Long.valueOf(String.valueOf(table.getValueAt(row, 0)));
        String username = String.valueOf(table.getValueAt(row, 1));
        if (manager != null && manager.getUserId().equals(userId)) {
            JOptionPane.showMessageDialog(parent, "当前账号请使用顶部的修改密码功能。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(parent,
            "确定为账号“" + username + "”生成新的临时密码吗？\n原密码将立即失效。",
            "重置用户密码", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        resetButton.setEnabled(false);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return userService.resetPassword(manager, userId);
            }

            @Override
            protected void done() {
                if (manager != currentUser) {
                    return;
                }
                try {
                    String temporaryPassword = get();
                    JTextField passwordField = new JTextField(temporaryPassword, 24);
                    passwordField.setEditable(false);
                    passwordField.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, 15));
                    AppTheme.styleInput(passwordField);
                    JPanel resultPanel = new JPanel(new BorderLayout(0, 10));
                    resultPanel.setOpaque(false);
                    resultPanel.add(new JLabel("临时密码（仅显示本次，请安全交给用户）："), BorderLayout.NORTH);
                    resultPanel.add(passwordField, BorderLayout.CENTER);
                    resultPanel.add(AppTheme.muted("用户使用该密码登录后，系统会强制其立即设置个人新密码。"), BorderLayout.SOUTH);
                    JOptionPane.showMessageDialog(parent, resultPanel, "密码已重置", JOptionPane.INFORMATION_MESSAGE);
                    reload.run();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    JOptionPane.showMessageDialog(parent, "密码重置失败：" + cause.getMessage(),
                        "提示", JOptionPane.WARNING_MESSAGE);
                    resetButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void changeSelectedUserStatus(Component parent, JTable table, int status, Runnable reload) {
        User manager = currentUser;
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(parent, "请先选择一个账号。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Long userId = Long.valueOf(String.valueOf(table.getValueAt(row, 0)));
        String username = String.valueOf(table.getValueAt(row, 1));
        if (manager != null && manager.getUserId().equals(userId)) {
            JOptionPane.showMessageDialog(parent, "不能在当前会话中禁用自己的账号。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String action = status == 1 ? "启用" : "禁用";
        int confirm = JOptionPane.showConfirmDialog(parent, "确定要" + action + "账号“" + username + "”吗？",
            action + "账号", JOptionPane.YES_NO_OPTION, status == 1 ? JOptionPane.QUESTION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                userService.changeUserStatus(manager, userId, status);
                return null;
            }

            @Override
            protected void done() {
                if (manager != currentUser) {
                    return;
                }
                try {
                    get();
                    JOptionPane.showMessageDialog(parent, "账号已" + action + "。", "操作成功", JOptionPane.INFORMATION_MESSAGE);
                    reload.run();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent, "账号状态更新失败：" + ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void runHealthCheck() {
        try {
            if (focusExistingModulePage("系统自检")) {
                return;
            }
            User actor = currentUser;
            List<String> checkNames = List.of("MySQL 写库连接", "MySQL 读库连接", "MongoDB 连接", "分类 DAO 查询", "工单分页查询");
            DefaultTableModel model = new DefaultTableModel(new Object[]{"检查项", "状态", "耗时", "说明"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            JTable table = new JTable(model);
            AppTheme.styleTable(table);
            table.setRowHeight(42);
            table.getColumnModel().getColumn(0).setPreferredWidth(180);
            table.getColumnModel().getColumn(1).setPreferredWidth(90);
            table.getColumnModel().getColumn(1).setMaxWidth(110);
            table.getColumnModel().getColumn(2).setPreferredWidth(90);
            table.getColumnModel().getColumn(2).setMaxWidth(110);
            table.getColumnModel().getColumn(1).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable source, Object value, boolean selected,
                                                                boolean focused, int row, int column) {
                    super.getTableCellRendererComponent(source, value, selected, focused, row, column);
                    String status = String.valueOf(value);
                    setHorizontalAlignment(CENTER);
                    setForeground(selected ? AppTheme.TEXT : switch (status) {
                        case "通过" -> AppTheme.SUCCESS;
                        case "失败", "未完成" -> AppTheme.DANGER;
                        default -> AppTheme.PRIMARY;
                    });
                    return this;
                }
            });

            JButton rerunButton = new JButton("重新检查");
            AppTheme.primary(rerunButton);
            JLabel overallValue = new JLabel("等待检查");
            JLabel passedValue = new JLabel("—");
            JLabel failedValue = new JLabel("—");
            JLabel durationValue = new JLabel("—");
            JPanel summary = new JPanel(new java.awt.GridLayout(1, 4, 10, 0));
            summary.setOpaque(false);
            summary.add(createHealthSummaryCard("系统状态", overallValue));
            summary.add(createHealthSummaryCard("通过项目", passedValue));
            summary.add(createHealthSummaryCard("失败项目", failedValue));
            summary.add(createHealthSummaryCard("总耗时", durationValue));

            JTextArea noticeArea = createNoticeArea("就绪");
            JPanel tableCard = AppTheme.surface(new BorderLayout(0, 8));
            JLabel tableTitle = new JLabel("检查明细");
            tableTitle.setFont(tableTitle.getFont().deriveFont(java.awt.Font.BOLD, 16f));
            tableCard.add(tableTitle, BorderLayout.NORTH);
            tableCard.add(AppTheme.scroll(table), BorderLayout.CENTER);

            JPanel page = createModulePage();
            page.add(AppTheme.pageHeader("系统自检", "检查数据库连接、DAO 查询和工单分页链路", rerunButton), BorderLayout.NORTH);
            JPanel body = new JPanel(new BorderLayout(0, 10));
            body.setBackground(AppTheme.PAGE);
            body.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 14, 12, 14));
            body.add(summary, BorderLayout.NORTH);
            body.add(tableCard, BorderLayout.CENTER);
            page.add(body, BorderLayout.CENTER);
            page.add(createNoticePane(noticeArea), BorderLayout.SOUTH);

            @SuppressWarnings("unchecked")
            SwingWorker<HealthCheckDTO, Void>[] activeWorker = new SwingWorker[1];
            Runnable executeCheck = () -> {
                if (activeWorker[0] != null && !activeWorker[0].isDone()) {
                    activeWorker[0].cancel(true);
                }
                model.setRowCount(0);
                for (String checkName : checkNames) {
                    model.addRow(new Object[]{checkName, "检查中", "—", "正在执行"});
                }
                rerunButton.setEnabled(false);
                overallValue.setText("检查中…");
                overallValue.setForeground(AppTheme.PRIMARY);
                passedValue.setText("—");
                failedValue.setText("—");
                durationValue.setText("—");
                showNotice(noticeArea, "系统自检正在后台执行，请稍候。");
                centerArea.setText("系统自检正在执行，共 5 个检查项。");

                activeWorker[0] = new SwingWorker<>() {
                    @Override
                    protected HealthCheckDTO doInBackground() {
                        return systemHealthService.runFullCheck(actor);
                    }

                    @Override
                    protected void done() {
                        if (isCancelled() || actor != currentUser || !isActiveModulePage("系统自检", page)) {
                            return;
                        }
                        try {
                            HealthCheckDTO result = get();
                            model.setRowCount(0);
                            for (HealthCheckDTO.CheckResult check : result.getCheckResults()) {
                                model.addRow(new Object[]{check.name(), check.passed() ? "通过" : "失败",
                                    check.durationMillis() + " ms", check.message()});
                            }
                            int passedCount = result.getPassedChecks().size();
                            int failedCount = result.getFailedChecks().size();
                            overallValue.setText(result.isHealthy() ? "运行正常" : "存在异常");
                            overallValue.setForeground(result.isHealthy() ? AppTheme.SUCCESS : AppTheme.DANGER);
                            passedValue.setText(passedCount + " / " + result.getCheckResults().size());
                            passedValue.setForeground(AppTheme.SUCCESS);
                            failedValue.setText(String.valueOf(failedCount));
                            failedValue.setForeground(failedCount == 0 ? AppTheme.SUCCESS : AppTheme.DANGER);
                            durationValue.setText(result.getTotalDurationMillis() + " ms");
                            showNotice(noticeArea, TimeFormatUtil.format(result.getCheckedAt())
                                + " · 通过 " + passedCount + " · 失败 " + failedCount);
                            centerArea.setText("系统自检：通过 " + passedCount + " 项，失败 " + failedCount + " 项。");
                            rightArea.setText(result.isHealthy() ? "系统状态：稳定" : "系统状态：存在异常，请查看失败项");
                        } catch (Exception ex) {
                            String message = "系统自检失败：" + rootMessage(ex);
                            for (int row = 0; row < model.getRowCount(); row++) {
                                model.setValueAt("未完成", row, 1);
                                model.setValueAt("—", row, 2);
                                model.setValueAt(message, row, 3);
                            }
                            overallValue.setText("执行失败");
                            overallValue.setForeground(AppTheme.DANGER);
                            showNotice(noticeArea, message);
                            centerArea.setText(message);
                            rightArea.setText("系统状态：自检未完成");
                            JOptionPane.showMessageDialog(page, message, "提示", JOptionPane.WARNING_MESSAGE);
                        } finally {
                            rerunButton.setEnabled(true);
                        }
                    }
                };
                activeWorker[0].execute();
            };
            rerunButton.addActionListener(event -> executeCheck.run());
            registerModulePage("系统自检", page, () -> {
                if (activeWorker[0] != null) {
                    activeWorker[0].cancel(true);
                }
            });
            executeCheck.run();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private JPanel createHealthSummaryCard(String title, JLabel valueLabel) {
        JPanel card = AppTheme.surface(new BorderLayout(0, 8));
        valueLabel.setFont(valueLabel.getFont().deriveFont(java.awt.Font.BOLD, 20f));
        card.add(AppTheme.muted(title), BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private void showConnectionPoolStatus() {
        try {
            if (focusExistingModulePage("连接池监控")) {
                return;
            }
            User actor = currentUser;
            DefaultTableModel liveModel = new DefaultTableModel(
                new Object[]{"连接池", "状态", "使用率", "使用中", "空闲", "总连接", "最大连接", "可用余量", "等待线程"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            DefaultTableModel configModel = new DefaultTableModel(
                new Object[]{"连接池", "池名称", "最小空闲", "连接等待超时", "空闲超时", "最长生命周期", "泄漏检测"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            JTable liveTable = new JTable(liveModel);
            JTable configTable = new JTable(configModel);
            AppTheme.styleTable(liveTable);
            AppTheme.styleTable(configTable);
            liveTable.setRowHeight(40);
            configTable.setRowHeight(40);
            liveTable.getColumnModel().getColumn(1).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable source, Object value, boolean selected,
                                                                boolean focused, int row, int column) {
                    super.getTableCellRendererComponent(source, value, selected, focused, row, column);
                    String status = String.valueOf(value);
                    setHorizontalAlignment(CENTER);
                    setForeground(selected ? AppTheme.TEXT : poolStatusColor(status));
                    return this;
                }
            });

            JButton refreshButton = new JButton("刷新");
            JButton simulateButton = new JButton("模拟占用连接");
            AppTheme.secondary(refreshButton);
            AppTheme.primary(simulateButton);
            JLabel statusLabel = AppTheme.muted("每 1 秒自动刷新");
            PoolStatusCard writeCard = createPoolStatusCard("WRITE", "写连接池");
            PoolStatusCard readCard = createPoolStatusCard("READ", "读连接池");
            JPanel summaryCards = new JPanel(new java.awt.GridLayout(1, 2, 10, 0));
            summaryCards.setOpaque(false);
            summaryCards.add(writeCard.panel());
            summaryCards.add(readCard.panel());
            summaryCards.setPreferredSize(new Dimension(0, 145));

            JPanel livePanel = AppTheme.surface(new BorderLayout(0, 8));
            livePanel.add(new JLabel("读写连接池实时对比"), BorderLayout.NORTH);
            livePanel.add(AppTheme.scroll(liveTable), BorderLayout.CENTER);
            JPanel configPanel = AppTheme.surface(new BorderLayout(0, 8));
            configPanel.add(new JLabel("HikariCP 参数配置"), BorderLayout.NORTH);
            configPanel.add(AppTheme.scroll(configTable), BorderLayout.CENTER);
            java.awt.CardLayout monitorCardLayout = new java.awt.CardLayout();
            JPanel monitorCards = new JPanel(monitorCardLayout);
            monitorCards.setOpaque(false);
            monitorCards.add(livePanel, "live");
            monitorCards.add(configPanel, "config");
            javax.swing.JToggleButton liveTabButton = new javax.swing.JToggleButton("实时指标");
            javax.swing.JToggleButton configTabButton = new javax.swing.JToggleButton("参数配置");
            AppTheme.segment(liveTabButton);
            AppTheme.segment(configTabButton);
            liveTabButton.setFont(liveTabButton.getFont().deriveFont(java.awt.Font.BOLD, 14f));
            configTabButton.setFont(configTabButton.getFont().deriveFont(java.awt.Font.BOLD, 14f));
            liveTabButton.setPreferredSize(new Dimension(112, 36));
            configTabButton.setPreferredSize(new Dimension(112, 36));
            javax.swing.ButtonGroup monitorTabGroup = new javax.swing.ButtonGroup();
            monitorTabGroup.add(liveTabButton);
            monitorTabGroup.add(configTabButton);
            liveTabButton.setSelected(true);
            liveTabButton.addActionListener(event -> monitorCardLayout.show(monitorCards, "live"));
            configTabButton.addActionListener(event -> monitorCardLayout.show(monitorCards, "config"));
            JPanel monitorTabBar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            monitorTabBar.setOpaque(false);
            monitorTabBar.add(liveTabButton);
            monitorTabBar.add(configTabButton);
            JPanel monitorSection = new JPanel(new BorderLayout(0, 8));
            monitorSection.setOpaque(false);
            monitorSection.add(monitorTabBar, BorderLayout.NORTH);
            monitorSection.add(monitorCards, BorderLayout.CENTER);

            JTextArea noticeArea = createNoticeArea("刷新周期：1 秒 · 模拟上限：3");

            JPanel page = createModulePage();
            page.add(AppTheme.pageHeader("连接池监控", "对比 READ/WRITE 连接池负载与 HikariCP 配置",
                statusLabel, refreshButton, simulateButton), BorderLayout.NORTH);
            JPanel body = new JPanel(new BorderLayout(0, 10));
            body.setBackground(AppTheme.PAGE);
            body.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 14, 12, 14));
            body.add(summaryCards, BorderLayout.NORTH);
            body.add(monitorSection, BorderLayout.CENTER);
            page.add(body, BorderLayout.CENTER);
            page.add(createNoticePane(noticeArea), BorderLayout.SOUTH);
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    connectionPoolMonitorService.recordPanelView(actor);
                    return null;
                }
            }.execute();

            @SuppressWarnings("unchecked")
            SwingWorker<List<ConnectionPoolStatusDTO>, Void>[] refreshWorker = new SwingWorker[1];
            @SuppressWarnings("unchecked")
            SwingWorker<Void, Void>[] simulationWorker = new SwingWorker[1];
            java.util.function.Consumer<Boolean> refresh = manual -> {
                if (refreshWorker[0] != null && !refreshWorker[0].isDone()) {
                    if (manual) {
                        showNotice(noticeArea, "上一次刷新尚未完成，请稍候。");
                    }
                    return;
                }
                if (manual) {
                    statusLabel.setText("正在刷新…");
                }
                refreshWorker[0] = new SwingWorker<>() {
                    @Override
                    protected List<ConnectionPoolStatusDTO> doInBackground() {
                        return connectionPoolMonitorService.currentStatuses(actor);
                    }

                    @Override
                    protected void done() {
                        if (isCancelled() || actor != currentUser || !isActiveModulePage("连接池监控", page)) {
                            return;
                        }
                        try {
                            List<ConnectionPoolStatusDTO> statuses = get();
                            updatePoolDashboard(statuses, liveModel, configModel, writeCard, readCard);
                            String summary = statuses.stream()
                                .map(status -> status.getRole() + "=" + status.getStatusText()
                                    + "(" + status.getUsagePercent() + "%)")
                                .reduce((left, right) -> left + "，" + right)
                                .orElse("无连接池状态");
                            statusLabel.setForeground(AppTheme.MUTED);
                            statusLabel.setText("已更新 " + TimeFormatUtil.format(LocalDateTime.now()));
                            showNotice(noticeArea, "实时状态：" + summary);
                            if (isSelectedModule("连接池监控")) {
                                centerArea.setText("连接池读写分离状态：\n" + summary);
                                rightArea.setText("SELECT 默认走 READ 池，写入、更新和事务默认走 WRITE 池。");
                            }
                        } catch (Exception ex) {
                            String message = "连接池刷新失败：" + rootMessage(ex);
                            statusLabel.setForeground(AppTheme.DANGER);
                            statusLabel.setText("刷新失败");
                            showNotice(noticeArea, message);
                        }
                    }
                };
                refreshWorker[0].execute();
            };

            Timer timer = new Timer(1000, event -> refresh.accept(false));
            refreshButton.addActionListener(event -> refresh.accept(true));
            simulateButton.addActionListener(event -> simulationWorker[0] = simulateConnectionUsage(
                page, simulateButton, () -> refresh.accept(false), noticeArea));
            registerModulePage("连接池监控", page, () -> {
                timer.stop();
                if (refreshWorker[0] != null) {
                    refreshWorker[0].cancel(true);
                }
                if (simulationWorker[0] != null) {
                    simulationWorker[0].cancel(true);
                }
            });
            timer.start();
            refresh.accept(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void updatePoolDashboard(List<ConnectionPoolStatusDTO> statuses,
                                     DefaultTableModel liveModel, DefaultTableModel configModel,
                                     PoolStatusCard writeCard, PoolStatusCard readCard) {
        liveModel.setRowCount(0);
        configModel.setRowCount(0);
        for (ConnectionPoolStatusDTO status : statuses) {
            String role = "WRITE".equals(status.getRole()) ? "写池（WRITE）" : "读池（READ）";
            liveModel.addRow(new Object[]{role, status.getStatusText(), status.getUsagePercent() + "%",
                status.getActiveConnections(), status.getIdleConnections(), status.getTotalConnections(),
                status.getMaximumPoolSize(), status.getAvailableConnections(), status.getThreadsAwaitingConnection()});
            configModel.addRow(new Object[]{role, status.getPoolName(), status.getMinimumIdle(),
                formatMs(status.getConnectionTimeoutMs()), formatMs(status.getIdleTimeoutMs()),
                formatMs(status.getMaxLifetimeMs()), status.getLeakDetectionThresholdMs() <= 0
                    ? "未开启" : formatMs(status.getLeakDetectionThresholdMs())});
            updatePoolStatusCard("WRITE".equals(status.getRole()) ? writeCard : readCard, status);
        }
    }

    private PoolStatusCard createPoolStatusCard(String role, String title) {
        JPanel panel = AppTheme.surface(new BorderLayout(0, 8));
        JPanel heading = new JPanel(new BorderLayout(8, 0));
        heading.setOpaque(false);
        JLabel titleLabel = new JLabel(title + " · " + role);
        titleLabel.setFont(titleLabel.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        JLabel statusLabel = new JLabel("等待刷新");
        statusLabel.setForeground(AppTheme.MUTED);
        heading.add(titleLabel, BorderLayout.WEST);
        heading.add(statusLabel, BorderLayout.EAST);
        javax.swing.JProgressBar usageBar = new javax.swing.JProgressBar(0, 100);
        usageBar.setStringPainted(false);
        usageBar.setPreferredSize(new Dimension(0, 10));
        usageBar.setMinimumSize(new Dimension(0, 10));
        JPanel usagePanel = new JPanel(new java.awt.GridBagLayout());
        usagePanel.setOpaque(false);
        java.awt.GridBagConstraints usageConstraints = new java.awt.GridBagConstraints();
        usageConstraints.gridx = 0;
        usageConstraints.gridy = 0;
        usageConstraints.weightx = 1;
        usageConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        usagePanel.add(usageBar, usageConstraints);
        JLabel detailLabel = AppTheme.muted("使用中 — · 空闲 — · 等待 —");
        JLabel poolNameLabel = AppTheme.muted("连接池尚未初始化");
        panel.add(heading, BorderLayout.NORTH);
        panel.add(usagePanel, BorderLayout.CENTER);
        JPanel footer = new JPanel(new java.awt.GridLayout(0, 1, 0, 3));
        footer.setOpaque(false);
        footer.add(detailLabel);
        footer.add(poolNameLabel);
        panel.add(footer, BorderLayout.SOUTH);
        return new PoolStatusCard(panel, statusLabel, usageBar, detailLabel, poolNameLabel);
    }

    private void updatePoolStatusCard(PoolStatusCard card, ConnectionPoolStatusDTO status) {
        int usage = status.getUsagePercent();
        java.awt.Color color = poolStatusColor(status.getStatusText());
        card.statusLabel().setText(status.getStatusText() + " · 使用率 " + usage + "%");
        card.statusLabel().setForeground(color);
        card.usageBar().setValue(usage);
        card.usageBar().setForeground(color);
        card.detailLabel().setText("使用中 " + status.getActiveConnections() + " / 最大 "
            + status.getMaximumPoolSize() + " · 空闲 " + status.getIdleConnections()
            + " · 等待 " + status.getThreadsAwaitingConnection());
        card.poolNameLabel().setText(status.getPoolName());
    }

    private java.awt.Color poolStatusColor(String status) {
        return switch (status == null ? "" : status) {
            case "正常" -> AppTheme.SUCCESS;
            case "负载较高" -> AppTheme.WARNING;
            default -> AppTheme.DANGER;
        };
    }

    private SwingWorker<Void, Void> simulateConnectionUsage(Component parent, JButton simulateButton,
                                                             Runnable refresh, JTextArea noticeArea) {
        User actor = currentUser;
        simulateButton.setEnabled(false);
        simulateButton.setText("模拟进行中…");
        showNotice(noticeArea, "正在占用最多 3 条写连接，持续 8 秒；实时指标会继续自动刷新。");
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                connectionPoolMonitorService.simulateConnectionUsage(actor, 3, 8);
                return null;
            }

            @Override
            protected void done() {
                if (isCancelled() || actor != currentUser || !parent.isDisplayable()) {
                    return;
                }
                simulateButton.setEnabled(true);
                simulateButton.setText("模拟占用连接");
                refresh.run();
                try {
                    get();
                    showNotice(noticeArea, "模拟占用已结束，连接已全部归还连接池。");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    JOptionPane.showMessageDialog(parent,
                        rootMessage(ex), "提示", JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        worker.execute();
        return worker;
    }

    private record PoolStatusCard(JPanel panel, JLabel statusLabel, javax.swing.JProgressBar usageBar,
                                  JLabel detailLabel, JLabel poolNameLabel) {
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private String categoryDisplay(Category category) {
        if (category == null) {
            return "";
        }
        return categoryDisplayNames.getOrDefault(category.getCategoryId(), category.getName());
    }

    private record TicketPageLoad(PageResult<CrossTicketDTO> page,
                                  java.util.Map<Long, String> categoryDisplayNames) {
    }

    private record ParentCategoryOption(Long categoryId, String name) {
        @Override
        public String toString() {
            return categoryId == null ? name : categoryId + " - " + name;
        }
    }

    private record AdminOption(User user) {
        @Override
        public String toString() {
            return user.getUserId() + " - " + user.getUsername();
        }
    }

    private enum AssignmentFilterKind {
        ALL,
        UNASSIGNED,
        MINE,
        ADMIN
    }

    private record AssignmentFilter(AssignmentFilterKind kind, String adminId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private String formatMs(long value) {
        if (value <= 0) {
            return String.valueOf(value);
        }
        return value + " ms";
    }

    private void showMaintenancePanel() {
        if (focusExistingModulePage("批量维护")) {
            return;
        }
        JPanel page = createModulePage();
        page.add(AppTheme.pageHeader("批量维护", "集中处理长期未响应的待处理工单"), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setBackground(AppTheme.PAGE);
        body.setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 20, 20, 20));
        JPanel card = AppTheme.surface(new BorderLayout(0, 16));
        JLabel title = new JLabel("取消超时待处理工单");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 18f));
        JTextArea description = new JTextArea("时间范围：30 天前\n源状态：待处理\n目标状态：已取消");
        configureReadOnlyArea(description);
        description.setOpaque(false);
        description.setForeground(AppTheme.MUTED);
        description.setBorder(null);
        JButton executeButton = new JButton("执行批量维护");
        AppTheme.danger(executeButton);
        JLabel resultLabel = AppTheme.muted("尚未执行批量维护");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(executeButton);
        actions.add(resultLabel);
        card.add(title, BorderLayout.NORTH);
        card.add(description, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        body.add(card, BorderLayout.NORTH);
        page.add(body, BorderLayout.CENTER);
        executeButton.addActionListener(event -> batchCancelStalePendingOrders(resultLabel));
        registerModulePage("批量维护", page);
    }

    private void batchCancelStalePendingOrders(JLabel resultLabel) {
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "将把 30 天前仍为待处理的工单批量流转为已取消，是否继续？",
            "批处理确认",
            JOptionPane.YES_NO_OPTION
        );
        if (confirm != JOptionPane.YES_OPTION) {
            resultLabel.setText("已取消本次操作");
            return;
        }
        try {
            resultLabel.setText("正在执行…");
            int affectedRows = maintenanceService.batchUpdateOrderStatus(currentUser, 0, 4, LocalDateTime.now().minusDays(30));
            String message = "批量维护完成，影响 " + affectedRows + " 条工单。";
            resultLabel.setForeground(AppTheme.SUCCESS);
            resultLabel.setText(message);
            rightArea.setText(message);
        } catch (Exception ex) {
            resultLabel.setForeground(AppTheme.DANGER);
            resultLabel.setText("执行失败：" + ex.getMessage());
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }
}
