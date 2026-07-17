package com.ticket.ui.admin;

import com.ticket.dto.ConnectionPoolStatusDTO;
import com.ticket.dto.CrossTicketDTO;
import com.ticket.dto.HealthCheckDTO;
import com.ticket.dto.PageResult;
import com.ticket.model.Category;
import com.ticket.model.AssignmentRule;
import com.ticket.model.Comment;
import com.ticket.model.ItemDetail;
import com.ticket.model.StickerCatalog;
import com.ticket.model.TicketAttachment;
import com.ticket.model.TicketHistory;
import com.ticket.model.User;
import com.ticket.model.UserRole;
import com.ticket.service.BusinessService;
import com.ticket.service.AssignmentRuleService;
import com.ticket.service.CategoryService;
import com.ticket.service.ConnectionPoolMonitorService;
import com.ticket.service.CrossDatabaseQueryService;
import com.ticket.service.CrossDatabaseQueryService.AssignmentScope;
import com.ticket.service.CrossDatabaseQueryService.AssignedWorkOverview;
import com.ticket.service.MaintenanceService;
import com.ticket.service.KnowledgeService;
import com.ticket.service.NotificationService;
import com.ticket.service.StatisticsService;
import com.ticket.service.SystemHealthService;
import com.ticket.service.UserService;
import com.ticket.ui.MainFrame;
import com.ticket.ui.component.ChoiceDialog;
import com.ticket.ui.component.MessageComposerDialog;
import com.ticket.ui.component.NotificationDialog;
import com.ticket.ui.component.TextEntryDialog;
import com.ticket.ui.component.TicketAttachmentDialog;
import com.ticket.ui.component.DonutChartPanel;
import com.ticket.ui.theme.AppTheme;
import com.ticket.ui.theme.StatusTagRenderer;
import com.ticket.ui.theme.WindowIconUtil;
import com.ticket.util.CategoryDisplayUtil;
import com.ticket.util.TimeFormatUtil;
import com.ticket.util.SlaDisplayUtil;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
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
    private final AssignmentRuleService assignmentRuleService = new AssignmentRuleService();
    private final NotificationService notificationService = new NotificationService();
    private final KnowledgeService knowledgeService = new KnowledgeService();
    private final JLabel headerLabel = new JLabel("未登录");
    private final JButton notificationButton = new JButton("通知");
    private final CardLayout workspaceLayout = new CardLayout();
    private final JPanel workspaceCards = new JPanel(workspaceLayout);
    private final JPanel homePanel = new JPanel(new BorderLayout(0, 18));
    private final JLabel homeDateLabel = new JLabel("—");
    private final JLabel homeTimeLabel = new JLabel("—");
    private final JLabel homeTodoLabel = new JLabel("—");
    private final JLabel homePendingLabel = new JLabel("—");
    private final JLabel homeProcessingLabel = new JLabel("—");
    private final JLabel homeAwaitingLabel = new JLabel("—");
    private final JLabel homeCompletedLabel = new JLabel("—");
    private final DonutChartPanel homeStatusChart = new DonutChartPanel();
    private final DefaultTableModel homeRiskModel = new DefaultTableModel(
        new Object[]{"工单编号", "标题", "状态", "优先级", "SLA"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable homeRiskTable = new JTable(homeRiskModel);
    private final JLabel homeRiskHint = AppTheme.muted("正在加载个人待办…");
    private final JLabel homeUpdatedLabel = AppTheme.muted("尚未更新");
    private final List<CrossTicketDTO> homeRiskTickets = new ArrayList<>();
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
    private final Timer notificationTimer = new Timer(60_000, event -> refreshNotificationBadge());
    private SwingWorker<AssignedWorkOverview, Void> homeSummaryWorker;

    public AdminWorkbenchPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());
        setBackground(AppTheme.PAGE);
        JButton changePasswordButton = new JButton("修改密码");
        JButton logoutButton = new JButton("退出登录");
        AppTheme.secondary(notificationButton);
        AppTheme.secondary(changePasswordButton);
        AppTheme.secondary(logoutButton);
        headerLabel.setForeground(AppTheme.MUTED);
        add(AppTheme.pageHeader("管理中心", "工单、账号治理、数据与系统工具统一入口",
            headerLabel, notificationButton, changePasswordButton, logoutButton), BorderLayout.NORTH);

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
        notificationButton.addActionListener(event -> {
            if (currentUser != null) NotificationDialog.show(this, currentUser, notificationService,
                this::refreshNotificationBadge);
        });
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
        addNavigationButton(navigation, "权限管理", "权限管理");
        addNavigationButton(navigation, "分类管理", "分类管理");
        addNavigationButton(navigation, "分配规则", "分配规则");
        addNavigationButton(navigation, "知识库与快捷回复", "知识库与快捷回复");
        addNavigationSection(navigation, "数据分析");
        addNavigationButton(navigation, "数据统计", "数据报表");
        addNavigationButton(navigation, "行为日志", "行为日志");
        addNavigationButton(navigation, "系统日志", "系统日志");
        addNavigationSection(navigation, "系统工具");
        addNavigationButton(navigation, "系统自检", "系统自检");
        addNavigationButton(navigation, "连接池监控", "连接池监控");
        addNavigationButton(navigation, "批量维护", "批量维护");
        addNavigationButton(navigation, "数据生命周期", "数据生命周期");
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

        JPanel metrics = new JPanel(new java.awt.GridLayout(1, 5, 14, 0));
        metrics.setOpaque(false);
        metrics.add(createMetricCard("当前待办", homeTodoLabel));
        metrics.add(createMetricCard("我的待处理", homePendingLabel));
        metrics.add(createMetricCard("我的处理中", homeProcessingLabel));
        metrics.add(createMetricCard("待我确认", homeAwaitingLabel));
        metrics.add(createMetricCard("我的累计已完成", homeCompletedLabel));
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);
        content.add(metrics, BorderLayout.NORTH);
        content.add(createHomeDashboard(), BorderLayout.CENTER);
        homePanel.add(content, BorderLayout.CENTER);
        updateHomeClock();
    }

    private Component createHomeDashboard() {
        homeStatusChart.setCenterTitle("我的工单");
        homeStatusChart.setSelectionListener(status -> openMyTickets(status));
        JPanel chartHeader = new JPanel(new BorderLayout());
        chartHeader.setOpaque(false);
        JLabel chartTitle = new JLabel("我的工单状态");
        chartTitle.setFont(chartTitle.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        chartHeader.add(chartTitle, BorderLayout.WEST);
        chartHeader.add(homeUpdatedLabel, BorderLayout.EAST);
        JPanel chartCard = AppTheme.surface(new BorderLayout(0, 8));
        chartCard.add(chartHeader, BorderLayout.NORTH);
        chartCard.add(homeStatusChart, BorderLayout.CENTER);

        AppTheme.styleTable(homeRiskTable);
        homeRiskTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        homeRiskTable.getColumnModel().getColumn(0).setMaxWidth(100);
        homeRiskTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        homeRiskTable.getColumnModel().getColumn(2).setMaxWidth(100);
        homeRiskTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        homeRiskTable.getColumnModel().getColumn(3).setMaxWidth(100);
        homeRiskTable.getColumnModel().getColumn(4).setPreferredWidth(90);
        homeRiskTable.getColumnModel().getColumn(4).setMaxWidth(110);
        homeRiskTable.getColumnModel().getColumn(2).setCellRenderer(new StatusTagRenderer(StatusTagRenderer.Kind.STATUS));
        homeRiskTable.getColumnModel().getColumn(3).setCellRenderer(new StatusTagRenderer(StatusTagRenderer.Kind.PRIORITY));
        homeRiskTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() >= 2) {
                    showOverviewRiskTicket(homeRiskTable.getSelectedRow());
                }
            }
        });
        JPanel riskCard = AppTheme.surface(new BorderLayout(0, 8));
        JLabel riskTitle = new JLabel("待办风险");
        riskTitle.setFont(riskTitle.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        riskCard.add(riskTitle, BorderLayout.NORTH);
        riskCard.add(AppTheme.scroll(homeRiskTable), BorderLayout.CENTER);
        riskCard.add(homeRiskHint, BorderLayout.SOUTH);

        JPanel quickActions = new JPanel(new java.awt.GridLayout(1, 5, 8, 0));
        quickActions.setOpaque(false);
        quickActions.add(createHomeQuickButton("我的待处理", () -> openMyTickets(0)));
        quickActions.add(createHomeQuickButton("我的处理中", () -> openMyTickets(1)));
        quickActions.add(createHomeQuickButton("全部我的工单", () -> openMyTickets(null)));
        quickActions.add(createHomeQuickButton("待我确认", () ->
            showTicketManager(null, AssignmentFilterKind.PENDING_MINE)));
        quickActions.add(createHomeQuickButton("刷新概览", this::loadHomeSummary));
        JPanel quickCard = AppTheme.surface(new BorderLayout(0, 8));
        JLabel quickTitle = new JLabel("快捷操作");
        quickTitle.setFont(quickTitle.getFont().deriveFont(java.awt.Font.BOLD, 15f));
        quickCard.add(quickTitle, BorderLayout.NORTH);
        quickCard.add(quickActions, BorderLayout.CENTER);

        JPanel rightColumn = new JPanel(new BorderLayout(0, 12));
        rightColumn.setOpaque(false);
        rightColumn.add(riskCard, BorderLayout.CENTER);
        rightColumn.add(quickCard, BorderLayout.SOUTH);

        JSplitPane dashboard = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chartCard, rightColumn);
        dashboard.setResizeWeight(0.43);
        dashboard.setDividerLocation(0.43);
        dashboard.setDividerSize(8);
        dashboard.setBorder(null);
        dashboard.setOpaque(false);
        return dashboard;
    }

    private JButton createHomeQuickButton(String text, Runnable action) {
        JButton button = new JButton(text);
        AppTheme.secondary(button);
        button.addActionListener(event -> action.run());
        return button;
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
        homeTodoLabel.setText("—");
        homePendingLabel.setText("—");
        homeProcessingLabel.setText("—");
        homeAwaitingLabel.setText("—");
        homeCompletedLabel.setText("—");
        homeRiskHint.setText("正在加载个人待办…");
        homeUpdatedLabel.setText("更新中…");
        if (homeSummaryWorker != null && !homeSummaryWorker.isDone()) {
            homeSummaryWorker.cancel(true);
        }
        homeSummaryWorker = new SwingWorker<>() {
            @Override
            protected AssignedWorkOverview doInBackground() {
                return crossDatabaseQueryService.assignedWorkOverview(
                    actor, actor == null ? null : String.valueOf(actor.getUserId()), 6);
            }

            @Override
            protected void done() {
                if (isCancelled() || actor != currentUser || !isActiveModulePage("工作概览", homePanel)) {
                    return;
                }
                try {
                    AssignedWorkOverview overview = get();
                    Map<Integer, Long> counts = overview.statusCounts();
                    long pending = counts.getOrDefault(0, 0L);
                    long processing = counts.getOrDefault(1, 0L);
                    long awaiting = counts.getOrDefault(
                        CrossDatabaseQueryService.STATUS_PENDING_CONFIRMATION, 0L);
                    homeTodoLabel.setText(String.valueOf(pending + processing + awaiting));
                    homePendingLabel.setText(String.valueOf(pending));
                    homeProcessingLabel.setText(String.valueOf(processing));
                    homeAwaitingLabel.setText(String.valueOf(awaiting));
                    homeCompletedLabel.setText(String.valueOf(counts.getOrDefault(2, 0L)));
                    updateHomeDashboard(overview);
                } catch (Exception ex) {
                    homeRiskHint.setText("个人待办加载失败：" + rootMessage(ex));
                }
            }
        };
        homeSummaryWorker.execute();
    }

    private void updateHomeDashboard(AssignedWorkOverview overview) {
        Map<Integer, Long> counts = overview.statusCounts();
        homeStatusChart.setSegments(List.of(
            new DonutChartPanel.Segment("待处理", counts.getOrDefault(0, 0L), AppTheme.WARNING, 0),
            new DonutChartPanel.Segment("处理中", counts.getOrDefault(1, 0L), AppTheme.PRIMARY, 1),
            new DonutChartPanel.Segment("等待客户", counts.getOrDefault(5, 0L), new java.awt.Color(217, 119, 6), 5),
            new DonutChartPanel.Segment("暂挂", counts.getOrDefault(6, 0L), new java.awt.Color(100, 116, 139), 6),
            new DonutChartPanel.Segment("待确认", counts.getOrDefault(
                CrossDatabaseQueryService.STATUS_PENDING_CONFIRMATION, 0L),
                new java.awt.Color(124, 58, 237), CrossDatabaseQueryService.STATUS_PENDING_CONFIRMATION),
            new DonutChartPanel.Segment("已完成", counts.getOrDefault(2, 0L), AppTheme.SUCCESS, 2),
            new DonutChartPanel.Segment("已关闭", counts.getOrDefault(3, 0L), new java.awt.Color(107, 114, 128), 3),
            new DonutChartPanel.Segment("已取消", counts.getOrDefault(4, 0L), new java.awt.Color(190, 75, 75), 4)
        ));
        homeUpdatedLabel.setText("更新于 " + ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
            .format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        homeRiskTickets.clear();
        homeRiskTickets.addAll(overview.riskTickets());
        homeRiskModel.setRowCount(0);
        for (CrossTicketDTO ticket : homeRiskTickets) {
            ItemDetail.Metadata metadata = ticket.getItemDetail() == null ? null : ticket.getItemDetail().getMetadata();
            homeRiskModel.addRow(new Object[]{
                ticket.getItem() == null ? "" : ticket.getItem().getItemId(),
                ticket.getItem() == null ? "" : ticket.getItem().getTitle(),
                ticket.getOrder() == null ? "" : statusText(homeDisplayStatus(ticket)),
                metadata == null ? "" : nullToEmpty(metadata.getPriority()),
                ticket.getOrder() == null ? "—" : SlaDisplayUtil.countdown(ticket.getOrder())
            });
        }
        long awaiting = counts.getOrDefault(CrossDatabaseQueryService.STATUS_PENDING_CONFIRMATION, 0L);
        homeRiskHint.setText(awaiting > 0
            ? "有 " + awaiting + " 个转派申请待你确认；双击对应工单可查看详情。"
            : homeRiskTickets.isEmpty() ? "当前没有待处理、处理中或待确认的个人工单" : "");
    }

    private String waitingTime(CrossTicketDTO ticket) {
        LocalDateTime createdAt = ticket.getOrder() == null ? null : ticket.getOrder().getCreatedAt();
        if (createdAt == null) {
            return "—";
        }
        Duration duration = Duration.between(createdAt, LocalDateTime.now());
        if (duration.isNegative()) {
            duration = Duration.ZERO;
        }
        long days = duration.toDays();
        long hours = duration.minusDays(days).toHours();
        return days > 0 ? days + "天" + hours + "小时" : Math.max(1, duration.toHours()) + "小时";
    }

    private void openMyTickets(Integer status) {
        showModuleHint("全部工单");
        showTicketManager(status, Integer.valueOf(CrossDatabaseQueryService.STATUS_PENDING_CONFIRMATION).equals(status)
            ? AssignmentFilterKind.PENDING_MINE : AssignmentFilterKind.MINE);
    }

    private void showOverviewRiskTicket(int selectedRow) {
        if (selectedRow < 0 || selectedRow >= homeRiskTickets.size()) {
            return;
        }
        CrossTicketDTO summary = homeRiskTickets.get(selectedRow);
        if (summary.getItem() == null || summary.getItem().getItemId() == null) {
            return;
        }
        User actor = currentUser;
        Long itemId = summary.getItem().getItemId();
        homeRiskHint.setText("正在加载工单 " + itemId + " 的详情…");
        new SwingWorker<CrossTicketDTO, Void>() {
            @Override
            protected CrossTicketDTO doInBackground() {
                return crossDatabaseQueryService.getTicket(actor, itemId);
            }

            @Override
            protected void done() {
                if (actor != currentUser) {
                    return;
                }
                try {
                    JTextArea detail = new JTextArea(formatTicketDetail(get()), 24, 72);
                    configureReadOnlyArea(detail);
                    detail.setCaretPosition(0);
                    JScrollPane scrollPane = AppTheme.scroll(detail);
                    scrollPane.setPreferredSize(new Dimension(780, 520));
                    JOptionPane.showMessageDialog(AdminWorkbenchPanel.this, scrollPane,
                        "工单详情 · " + itemId, JOptionPane.PLAIN_MESSAGE);
                    homeRiskHint.setText("");
                } catch (Exception ex) {
                    homeRiskHint.setText("工单详情加载失败：" + rootMessage(ex));
                }
            }
        }.execute();
    }

    private void openModule(String moduleName) {
        if (!canOpenModule(moduleName)) {
            JOptionPane.showMessageDialog(this, "当前角色无权访问该模块。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        showModuleHint(moduleName);
        handleModuleClick(moduleName);
    }

    private boolean canOpenModule(String moduleName) {
        if (currentUser == null) {
            return false;
        }
        String role = currentUser.getRole();
        if ("ROOT".equals(role)) {
            return List.of("用户管理", "权限管理", "数据统计", "行为日志", "系统日志",
                "系统自检", "连接池监控", "数据生命周期")
                .contains(moduleName);
        }
        return "ADMIN".equals(role) && !List.of("权限管理", "数据生命周期").contains(moduleName);
    }

    private void updateNavigationForRole() {
        for (var entry : navigationButtons.entrySet()) {
            entry.getValue().setVisible(canOpenModule(entry.getKey()));
        }
    }

    private void handleModuleClick(String moduleName) {
        switch (moduleName) {
            case "工作概览" -> showAdminHome();
            case "全部工单" -> showTicketManager();
            case "用户管理" -> showUserManager();
            case "权限管理" -> showPermissionManager();
            case "分类管理" -> showCategoryManager();
            case "分配规则" -> showAssignmentRuleManager();
            case "知识库与快捷回复" -> showKnowledgeManager();
            case "数据统计" -> loadStats();
            case "行为日志" -> loadBehaviorLogs();
            case "系统日志" -> loadSystemLogs();
            case "系统自检" -> runHealthCheck();
            case "连接池监控" -> showConnectionPoolStatus();
            case "批量维护" -> showMaintenancePanel();
            case "数据生命周期" -> registerModulePage("数据生命周期", new DataLifecyclePanel(currentUser));
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
                rightArea.setText("服务层按角色层级校验，任何账号都不能禁用自己，ADMIN 不能操作同级账号。");
            }
            case "权限管理" -> {
                centerArea.setText("由 ROOT 创建后台账号并调整角色；临时密码只显示一次，首次登录必须修改。");
                rightArea.setText("角色变更会记录操作者、目标账号、修改前后角色和原因。最后一个有效 ROOT 受事务保护。");
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
            case "工作概览" -> "查看个人待办、风险工单与工作状态";
            case "全部工单" -> "查询工单并完成回复、备注、分配和状态流转";
            case "用户管理" -> "筛选账号并管理启用状态";
            case "权限管理" -> "创建后台账号并治理 ROOT 与 ADMIN 角色";
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
        showTicketManager(null, AssignmentFilterKind.ALL);
    }

    private void showTicketManager(Integer initialStatus, AssignmentFilterKind initialAssignment) {
        try {
            if (focusExistingModulePage("全部工单")) {
                return;
            }
            List<AdminOption> adminOptions = activeAdminOptions();
            java.util.Map<String, String> adminNameById = adminNameById(adminOptions);
            JComboBox<AssignmentFilter> assignmentFilterBox = new JComboBox<>(
                buildAssignmentFilters(adminOptions).toArray(new AssignmentFilter[0]));
            selectAssignmentFilter(assignmentFilterBox, initialAssignment);
            AppTheme.styleComboBox(assignmentFilterBox);
            DefaultTableModel model = new DefaultTableModel(
                new Object[]{"记录编号", "工单编号", "标题", "用户", "状态", "分类", "优先级", "催促", "分配客服", "SLA", "创建时间"}, 0);
            JTable table = new JTable(model);
            table.setDefaultEditor(Object.class, null);
            AppTheme.styleTable(table);
            table.getColumnModel().getColumn(4).setCellRenderer(new StatusTagRenderer(StatusTagRenderer.Kind.STATUS));
            table.getColumnModel().getColumn(6).setCellRenderer(new StatusTagRenderer(StatusTagRenderer.Kind.PRIORITY));
            JTextArea detailArea = new JTextArea(14, 0);
            configureReadOnlyArea(detailArea);
            detailArea.setMargin(new java.awt.Insets(12, 12, 12, 12));
            detailArea.setFont(detailArea.getFont().deriveFont(13f));
            List<CrossTicketDTO> tickets = new ArrayList<>();
            java.util.Map<Long, CrossTicketDTO> ticketDetailCache = new java.util.HashMap<>();

            JTextField keywordField = new JTextField(18);
            JComboBox<String> statusFilter = new JComboBox<>(new String[]{
                "全部状态", "0 待处理", "1 处理中", "2 已完成", "3 已关闭", "4 已取消",
                "5 等待客户回复", "6 暂挂", "99 待确认"
            });
            statusFilter.setSelectedIndex(statusFilterIndex(initialStatus));
            AppTheme.styleComboBox(statusFilter);
            AppTheme.styleInput(keywordField);
            JButton refreshButton = new JButton("刷新");
            JButton detailButton = new JButton("刷新详情");
            JButton replyButton = new JButton("回复客户");
            JButton noteButton = new JButton("添加内部备注");
            JButton attachmentButton = new JButton("查看附件");
            JButton templateButton = new JButton("快捷回复");
            JButton macroButton = new JButton("处理宏");
            JButton statusButton = new JButton("流转状态");
            JButton assignButton = new JButton("分配客服");
            JButton previousPageButton = new JButton("上一页");
            JButton nextPageButton = new JButton("下一页");
            JLabel ticketPageLabel = AppTheme.muted("第 1 页");
            AppTheme.secondary(refreshButton);
            AppTheme.secondary(detailButton);
            AppTheme.primary(replyButton);
            AppTheme.secondary(noteButton);
            AppTheme.secondary(attachmentButton);
            AppTheme.secondary(templateButton);
            AppTheme.secondary(macroButton);
            AppTheme.secondary(statusButton);
            AppTheme.secondary(assignButton);
            AppTheme.secondary(previousPageButton);
            AppTheme.secondary(nextPageButton);
            previousPageButton.setEnabled(false);
            nextPageButton.setEnabled(false);
            detailButton.setEnabled(false);
            setTicketActionButtonsEnabled(false, replyButton, noteButton, statusButton, assignButton);
            attachmentButton.setEnabled(false);
            templateButton.setEnabled(false);
            macroButton.setEnabled(false);
            detailButton.setToolTipText("从数据库重新读取当前工单的最新详情");
            replyButton.setToolTipText("向客户发送一条可见回复");
            noteButton.setToolTipText("添加仅管理员可见的协作备注");
            attachmentButton.setToolTipText("查看、预览或另存当前工单沟通记录中的附件");
            statusButton.setToolTipText("将工单流转到下一个可用状态");
            assignButton.setToolTipText("未分配工单需确认认领；转派必须由当前负责人发起并由目标管理员确认");

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

            JPanel communicationActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            communicationActions.setOpaque(false);
            communicationActions.add(replyButton);
            communicationActions.add(noteButton);
            communicationActions.add(attachmentButton);
            communicationActions.add(templateButton);
            communicationActions.add(macroButton);

            JPanel managementActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            managementActions.setOpaque(false);
            managementActions.add(statusButton);
            managementActions.add(assignButton);

            JPanel detailActions = new JPanel();
            detailActions.setLayout(new javax.swing.BoxLayout(detailActions, javax.swing.BoxLayout.Y_AXIS));
            detailActions.setBackground(AppTheme.SURFACE);
            detailActions.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.BORDER),
                javax.swing.BorderFactory.createEmptyBorder(12, 0, 0, 0)));
            JLabel communicationTitle = new JLabel("处理工单");
            communicationTitle.setFont(communicationTitle.getFont().deriveFont(java.awt.Font.BOLD, 14f));
            communicationTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel communicationHint = AppTheme.muted("回复对客户可见，内部备注仅用于团队协作");
            communicationHint.setFont(communicationHint.getFont().deriveFont(12f));
            communicationHint.setAlignmentX(Component.LEFT_ALIGNMENT);
            communicationActions.setAlignmentX(Component.LEFT_ALIGNMENT);
            communicationActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            JLabel managementTitle = AppTheme.muted("工单管理");
            managementTitle.setFont(managementTitle.getFont().deriveFont(java.awt.Font.BOLD, 12f));
            managementTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            managementActions.setAlignmentX(Component.LEFT_ALIGNMENT);
            managementActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            detailActions.add(communicationTitle);
            detailActions.add(javax.swing.Box.createVerticalStrut(3));
            detailActions.add(communicationHint);
            detailActions.add(javax.swing.Box.createVerticalStrut(10));
            detailActions.add(communicationActions);
            detailActions.add(javax.swing.Box.createVerticalStrut(14));
            detailActions.add(managementTitle);
            detailActions.add(javax.swing.Box.createVerticalStrut(6));
            detailActions.add(managementActions);
            JPanel detailPanel = AppTheme.surface(new BorderLayout(0, 8));
            JLabel detailTitle = new JLabel("工单详情");
            detailTitle.setFont(detailTitle.getFont().deriveFont(java.awt.Font.BOLD, 16f));
            JLabel ticketStateLabel = createTicketStateLabel();
            JPanel detailTitleRow = new JPanel(new BorderLayout(8, 0));
            detailTitleRow.setOpaque(false);
            detailTitleRow.add(detailTitle, BorderLayout.WEST);
            detailTitleRow.add(detailButton, BorderLayout.EAST);
            detailTitleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            JPanel detailHeading = new JPanel();
            detailHeading.setLayout(new javax.swing.BoxLayout(detailHeading, javax.swing.BoxLayout.Y_AXIS));
            detailHeading.setOpaque(false);
            ticketStateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            detailHeading.add(detailTitleRow);
            detailHeading.add(javax.swing.Box.createVerticalStrut(6));
            detailHeading.add(ticketStateLabel);
            detailPanel.add(detailHeading, BorderLayout.NORTH);
            detailPanel.add(AppTheme.scroll(detailArea), BorderLayout.CENTER);
            detailPanel.add(detailActions, BorderLayout.SOUTH);

            tablePanel.setMinimumSize(new Dimension(460, 0));
            detailPanel.setMinimumSize(new Dimension(330, 0));

            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tablePanel, detailPanel);
            splitPane.setResizeWeight(0.68);
            splitPane.setDividerSize(6);
            splitPane.setBorder(null);
            JPanel page = createModulePage();
            JPanel heading = new JPanel(new BorderLayout());
            heading.setBackground(AppTheme.PAGE);
            heading.add(AppTheme.pageHeader("工单管理与处理",
                "数据范围：全部历史工单；下方数量按当前状态、标题和分配客服筛选统计"), BorderLayout.NORTH);
            JPanel toolbarCard = AppTheme.surface(new BorderLayout());
            toolbarCard.add(toolbar, BorderLayout.CENTER);
            heading.add(toolbarCard, BorderLayout.CENTER);
            page.add(heading, BorderLayout.NORTH);
            page.add(splitPane, BorderLayout.CENTER);

            int[] ticketPage = {1};
            long[] ticketRequestVersion = {0};
            long[] ticketDetailRequestVersion = {0};
            Long[] ticketSelectionToRestore = {null};
            Runnable loadTickets = () -> {
                int selectedRow = table.getSelectedRow();
                if (ticketSelectionToRestore[0] == null && selectedRow >= 0 && selectedRow < tickets.size()
                    && tickets.get(selectedRow).getItem() != null) {
                    ticketSelectionToRestore[0] = tickets.get(selectedRow).getItem().getItemId();
                }
                ticketDetailRequestVersion[0]++;
                table.clearSelection();
                detailButton.setEnabled(false);
                setTicketActionButtonsEnabled(false, replyButton, noteButton, statusButton, assignButton);
                attachmentButton.setEnabled(false);
                templateButton.setEnabled(false);
                macroButton.setEnabled(false);
                setTicketState(ticketStateLabel, null);
                loadTicketRows(table, model, tickets, keywordField.getText(), statusFilter.getSelectedIndex(),
                    selectedAssignmentFilter(assignmentFilterBox), ticketPage[0], ticketRequestVersion,
                    ticketSelectionToRestore, adminNameById, detailArea, ticketStateLabel,
                    ticketPageLabel, previousPageButton, nextPageButton);
            };
            java.util.function.Consumer<Long> reloadTicketsKeepingSelection = itemId -> {
                ticketSelectionToRestore[0] = itemId;
                loadTickets.run();
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
                    if (selectedRow >= 0 && selectedRow < tickets.size()) {
                        loadSelectedTicketDetail(page, table, tickets, detailArea, ticketStateLabel,
                            ticketDetailRequestVersion, ticketDetailCache);
                    }
                    updateTicketActionButtons(table, tickets, ticketStateLabel,
                        replyButton, noteButton, statusButton, assignButton);
                    templateButton.setEnabled(replyButton.isEnabled());
                    macroButton.setEnabled(replyButton.isEnabled());
                    attachmentButton.setEnabled(selectedRow >= 0 && selectedRow < tickets.size());
                }
            });
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() >= 2) {
                        loadSelectedTicketDetail(page, table, tickets, detailArea, ticketStateLabel,
                            ticketDetailRequestVersion, ticketDetailCache);
                    }
                }
            });
            detailButton.addActionListener(event -> loadSelectedTicketDetail(
                page, table, tickets, detailArea, ticketStateLabel, ticketDetailRequestVersion, ticketDetailCache));
            replyButton.addActionListener(event -> addTicketText(
                page, table, tickets, false, detailArea, ticketStateLabel,
                ticketDetailRequestVersion, ticketDetailCache));
            noteButton.addActionListener(event -> addTicketText(
                page, table, tickets, true, detailArea, ticketStateLabel,
                ticketDetailRequestVersion, ticketDetailCache));
            attachmentButton.addActionListener(event -> showTicketAttachments(
                page, table, tickets, ticketDetailCache));
            templateButton.addActionListener(event -> applyReplyTemplate(page, table, tickets,
                detailArea, ticketStateLabel, ticketDetailRequestVersion, ticketDetailCache));
            macroButton.addActionListener(event -> applyHandlingMacro(page, table, tickets,
                reloadTicketsKeepingSelection, detailArea, ticketStateLabel, ticketDetailCache));
            statusButton.addActionListener(event -> changeTicketStatus(page, table, tickets,
                reloadTicketsKeepingSelection, detailArea, ticketStateLabel, ticketDetailCache));
            assignButton.addActionListener(event -> assignTicket(page, table, tickets,
                reloadTicketsKeepingSelection, detailArea, ticketStateLabel, ticketDetailCache));

            registerModulePage("全部工单", page);
            loadTickets.run();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void loadTicketRows(JTable table, DefaultTableModel model, List<CrossTicketDTO> tickets,
                                String keyword, int statusIndex, AssignmentFilter assignmentFilter,
                                int page, long[] requestVersions,
                                Long[] ticketSelectionToRestore,
                                java.util.Map<String, String> adminNameById, JTextArea detailArea, JLabel ticketStateLabel,
                                JLabel pageLabel, JButton previousPageButton, JButton nextPageButton) {
        Integer status = switch (statusIndex) {
            case 1, 2, 3, 4, 5, 6, 7 -> statusIndex - 1;
            case 8 -> CrossDatabaseQueryService.STATUS_PENDING_CONFIRMATION;
            default -> null;
        };
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        User actor = currentUser;
        AssignmentScope selectedAssignmentScope = assignmentScope(assignmentFilter);
        String selectedAdminId = assignmentAdminId(assignmentFilter, actor);
        long requestVersion = ++requestVersions[0];
        java.util.Map<Long, String> cachedCategoryNames = java.util.Map.copyOf(categoryDisplayNames);
        detailArea.setText("工单加载中…");
        pageLabel.setText("第 " + page + " 页 · 加载中…");
        previousPageButton.setEnabled(false);
        nextPageButton.setEnabled(false);
        new SwingWorker<TicketPageLoad, Void>() {
            @Override
            protected TicketPageLoad doInBackground() {
                PageResult<CrossTicketDTO> ticketPage = crossDatabaseQueryService.pageAdminTickets(
                    actor, status, normalizedKeyword, selectedAssignmentScope, selectedAdminId, page, 50);
                java.util.Map<Long, String> displayNames = cachedCategoryNames.isEmpty()
                    ? CategoryDisplayUtil.buildDisplayNames(categoryService.listAvailableCategories(actor))
                    : cachedCategoryNames;
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
                    pageLabel.setText("第 " + page + " / " + totalPages + " 页 · 当前筛选共 " + result.getTotal() + " 条");
                    previousPageButton.setEnabled(page > 1);
                    nextPageButton.setEnabled(page < totalPages);
                    renderTicketRows(result, model, tickets, adminNameById, detailArea, ticketStateLabel);
                    Long itemIdToRestore = ticketSelectionToRestore[0];
                    ticketSelectionToRestore[0] = null;
                    if (itemIdToRestore != null) {
                        selectTicketRow(table, tickets, itemIdToRestore);
                    }
                } catch (Exception ex) {
                    String message = "加载工单失败：" + rootMessage(ex);
                    pageLabel.setText("第 " + page + " 页 · 加载失败");
                    detailArea.setText(message);
                    detailArea.setCaretPosition(0);
                    previousPageButton.setEnabled(page > 1);
                }
            }
        }.execute();
    }

    private void renderTicketRows(PageResult<CrossTicketDTO> result, DefaultTableModel model, List<CrossTicketDTO> tickets,
                                  java.util.Map<String, String> adminNameById, JTextArea detailArea,
                                  JLabel ticketStateLabel) {
        try {
            tickets.clear();
            model.setRowCount(0);
            for (CrossTicketDTO ticket : result.getRecords()) {
                tickets.add(ticket);
                ItemDetail.Metadata metadata = ticket.getItemDetail() == null
                    ? null : ticket.getItemDetail().getMetadata();
                String assignedAdminId = metadata == null ? null : metadata.getAssignedAdminId();
                String assignmentText = assignedAdminDisplay(assignedAdminId, adminNameById);
                if (metadata != null && hasText(metadata.getTransferTargetAdminId())) {
                    assignmentText += " → "
                        + assignedAdminDisplay(metadata.getTransferTargetAdminId(), adminNameById) + "（待确认）";
                }
                model.addRow(new Object[]{
                    ticket.getOrder() == null ? "" : ticket.getOrder().getOrderId(),
                    ticket.getItem() == null ? "" : ticket.getItem().getItemId(),
                    ticket.getItem() == null ? "" : ticket.getItem().getTitle(),
                    ticket.getUser() == null ? "" : ticket.getUser().getUsername(),
                    ticket.getOrder() == null ? "" : statusText(
                        hasPendingTransfer(ticket) ? CrossDatabaseQueryService.STATUS_PENDING_CONFIRMATION
                            : ticket.getOrder().getStatus()),
                    categoryDisplay(ticket.getCategory()),
                    metadata == null ? "" : metadata.getPriority(),
                    metadata == null || metadata.getReminderCount() == 0 ? "" : metadata.getReminderCount() + " 次",
                    assignmentText,
                    ticket.getOrder() == null ? "—" : SlaDisplayUtil.countdown(ticket.getOrder()),
                    ticket.getOrder() == null ? "—" : TimeFormatUtil.format(ticket.getOrder().getCreatedAt())
                });
            }
            detailArea.setText(tickets.isEmpty()
                ? "暂无匹配的工单\n\n请调整标题、状态或分配客服筛选条件。"
                : "尚未选择工单\n\n从左侧列表选择一条工单，系统会自动读取完整的最新详情。\n双击列表行可再次强制刷新。");
            detailArea.setCaretPosition(0);
            setTicketState(ticketStateLabel, null);
            if (isSelectedModule("全部工单")) {
                centerArea.setText("工单管理：已加载 " + tickets.size() + " 条记录。");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "加载工单失败：" + ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void loadSelectedTicketDetail(Component parent, JTable table, List<CrossTicketDTO> tickets,
                                          JTextArea detailArea, JLabel ticketStateLabel,
                                          long[] requestVersions,
                                          java.util.Map<Long, CrossTicketDTO> detailCache) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= tickets.size()) {
            return;
        }
        CrossTicketDTO ticket = tickets.get(selectedRow);
        if (ticket.getItem() == null || ticket.getItem().getItemId() == null) {
            return;
        }
        Long itemId = ticket.getItem().getItemId();
        User actor = currentUser;
        long requestVersion = ++requestVersions[0];
        CrossTicketDTO cachedTicket = detailCache.get(itemId);
        CrossTicketDTO displayedTicket = cachedTicket == null ? ticket : cachedTicket;
        detailArea.setText(cachedTicket == null
            ? formatTicketLoadingDetail(ticket)
            : formatTicketDetail(cachedTicket));
        detailArea.setCaretPosition(0);
        refreshRightTicketSummary(displayedTicket);
        setTicketState(ticketStateLabel, displayedTicket);
        new SwingWorker<CrossTicketDTO, Void>() {
            @Override
            protected CrossTicketDTO doInBackground() {
                return crossDatabaseQueryService.getTicket(actor, itemId);
            }

            @Override
            protected void done() {
                if (actor != currentUser || requestVersion != requestVersions[0]) {
                    return;
                }
                int currentRow = table.getSelectedRow();
                if (currentRow < 0 || currentRow >= tickets.size()
                    || tickets.get(currentRow).getItem() == null
                    || !itemId.equals(tickets.get(currentRow).getItem().getItemId())) {
                    return;
                }
                try {
                    CrossTicketDTO freshTicket = get();
                    detailCache.put(itemId, freshTicket);
                    tickets.set(currentRow, freshTicket);
                    detailArea.setText(formatTicketDetail(freshTicket));
                    detailArea.setCaretPosition(0);
                    if (isSelectedModule("全部工单")) {
                        refreshRightTicketSummary(freshTicket);
                    }
                    setTicketState(ticketStateLabel, freshTicket);
                } catch (Exception ex) {
                    detailArea.setText(formatTicketDetail(displayedTicket)
                        + "\n\n提示：最新详情加载失败，当前显示上次可用内容。");
                    detailArea.setCaretPosition(0);
                    AppTheme.toast(parent, "加载详情失败：" + rootMessage(ex), true);
                }
            }
        }.execute();
    }

    private void addTicketText(Component parent, JTable table, List<CrossTicketDTO> tickets, boolean internalNote,
                               JTextArea detailArea, JLabel ticketStateLabel, long[] detailRequestVersions,
                               java.util.Map<Long, CrossTicketDTO> detailCache) {
        CrossTicketDTO ticket = selectedTicket(parent, table, tickets);
        if (ticket == null) {
            return;
        }
        if (!ensureCanProcessTicket(parent, ticket, ticketStateLabel)) {
            return;
        }
        Long itemId = ticket.getItem().getItemId();
        String title = internalNote ? "新增内部备注" : "新增客服回复";
        MessageComposerDialog.Result result = MessageComposerDialog.show(parent, title,
            internalNote ? "仅管理员可见；支持行内表情和拖放附件"
                : "对客户可见；支持行内表情和拖放文件或图片");
        if (!result.accepted()) {
            bringParentToFront(parent);
            return;
        }
        User actor = currentUser;
        AppTheme.toast(parent, result.files().isEmpty() ? "正在发送…" : "正在上传附件并发送…", false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                if (internalNote) {
                    businessService.addInternalNote(actor, itemId, result.text(), result.files(), result.stickerCode());
                } else {
                    businessService.addAdminReply(actor, itemId, result.text(), result.files(), result.stickerCode());
                }
                return null;
            }

            @Override
            protected void done() {
                if (actor != currentUser) {
                    return;
                }
                try {
                    get();
                    detailRequestVersions[0]++;
                    refreshTicketAfterOperation(parent, table, tickets, itemId, detailArea, ticketStateLabel,
                        detailCache, (internalNote ? "内部备注" : "客服回复") + "已保存。");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent, rootMessage(ex), "发送失败",
                        JOptionPane.WARNING_MESSAGE);
                } finally {
                    bringParentToFront(parent);
                }
            }
        }.execute();
    }

    private void showTicketAttachments(Component parent, JTable table, List<CrossTicketDTO> tickets,
                                       java.util.Map<Long, CrossTicketDTO> detailCache) {
        CrossTicketDTO selected = selectedTicket(parent, table, tickets);
        if (selected == null || selected.getItem() == null) {
            return;
        }
        Long itemId = selected.getItem().getItemId();
        CrossTicketDTO cached = detailCache.get(itemId);
        if (cached != null) {
            TicketAttachmentDialog.show(parent, currentUser, itemId, cached.getComments(), businessService);
            return;
        }
        User actor = currentUser;
        AppTheme.toast(parent, "正在加载附件列表…", false);
        new SwingWorker<CrossTicketDTO, Void>() {
            @Override
            protected CrossTicketDTO doInBackground() {
                return crossDatabaseQueryService.getTicket(actor, itemId);
            }

            @Override
            protected void done() {
                if (actor != currentUser) {
                    return;
                }
                try {
                    CrossTicketDTO ticket = get();
                    detailCache.put(itemId, ticket);
                    TicketAttachmentDialog.show(parent, actor, itemId, ticket.getComments(), businessService);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent, "加载附件失败：" + rootMessage(ex),
                        "附件错误", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void changeTicketStatus(Component parent, JTable table, List<CrossTicketDTO> tickets,
                                    java.util.function.Consumer<Long> reload,
                                    JTextArea detailArea, JLabel ticketStateLabel,
                                    java.util.Map<Long, CrossTicketDTO> detailCache) {
        CrossTicketDTO ticket = selectedTicket(parent, table, tickets);
        if (ticket == null || ticket.getOrder() == null) {
            return;
        }
        if (!ensureCanProcessTicket(parent, ticket, ticketStateLabel)) {
            return;
        }
        Long itemId = ticket.getItem().getItemId();
        TicketStatusOption[] statusOptions = statusOptions(ticket.getOrder().getStatus());
        if (statusOptions.length == 0) {
            JOptionPane.showMessageDialog(parent, "当前状态不能继续流转。", "提示", JOptionPane.INFORMATION_MESSAGE);
            bringParentToFront(parent);
            return;
        }
        JComboBox<TicketStatusOption> statusBox = new JComboBox<>(statusOptions);
        ChoiceDialog.Result<TicketStatusOption> result = ChoiceDialog.show(
            parent,
            "流转工单状态",
            "选择下一个状态",
            "当前状态：" + statusText(ticket.getOrder().getStatus()) + " · 工单 #" + itemId,
            "目标状态",
            statusBox,
            "确认流转");
        if (!result.accepted() || result.value() == null) {
            bringParentToFront(parent);
            return;
        }
        int newStatus = result.value().status();
        String reason = null;
        if (newStatus == 5 || newStatus == 6) {
            TextEntryDialog.Result reasonResult = TextEntryDialog.show(parent,
                newStatus == 5 ? "等待客户回复" : "暂挂工单",
                newStatus == 5 ? "可说明需要客户补充的内容" : "请填写暂挂原因",
                null, 4, 48);
            if (!reasonResult.accepted()) return;
            reason = reasonResult.text();
        }
        try {
            businessService.changeOrderStatus(currentUser, ticket.getOrder().getOrderId(), newStatus, reason);
            reload.accept(itemId);
            refreshTicketAfterOperation(parent, table, tickets, itemId, detailArea, ticketStateLabel,
                detailCache, "状态已更新。");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        } finally {
            bringParentToFront(parent);
        }
    }

    private void assignTicket(Component parent, JTable table, List<CrossTicketDTO> tickets,
                              java.util.function.Consumer<Long> reload,
                              JTextArea detailArea, JLabel ticketStateLabel,
                              java.util.Map<Long, CrossTicketDTO> detailCache) {
        CrossTicketDTO ticket = selectedTicket(parent, table, tickets);
        if (ticket == null) {
            return;
        }
        Long itemId = ticket.getItem().getItemId();
        String actorId = currentUser == null ? null : String.valueOf(currentUser.getUserId());
        String requesterId = transferRequesterAdminId(ticket);
        String targetId = transferTargetAdminId(ticket);
        String successMessage;
        try {
            if (hasPendingTransfer(ticket)) {
                String requestSummary = "工单 #" + itemId
                    + "\n发起人：管理员 #" + requesterId
                    + "\n目标：管理员 #" + targetId
                    + "\n原因：" + nullToEmpty(transferReason(ticket));
                if (actorId != null && actorId.equals(targetId)) {
                    Object[] options = {"接受接手", "拒绝", "暂不处理"};
                    int choice = JOptionPane.showOptionDialog(parent,
                        requestSummary + "\n\n接受后你将成为新负责人；拒绝后原归属保持不变。",
                        "确认接手邀请", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                        null, options, options[2]);
                    if (choice != 0 && choice != 1) {
                        return;
                    }
                    businessService.respondToTicketTransfer(
                        currentUser, itemId, transferRequestId(ticket), choice == 0);
                    successMessage = choice == 0 ? "已接受接手邀请。" : "已拒绝接手邀请。";
                } else if (actorId != null && actorId.equals(requesterId)) {
                    int confirm = JOptionPane.showConfirmDialog(parent,
                        requestSummary + "\n\n确定取消这次接手邀请吗？",
                        "取消接手邀请", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (confirm != JOptionPane.YES_OPTION) {
                        return;
                    }
                    businessService.cancelTicketTransfer(currentUser, itemId, transferRequestId(ticket));
                    successMessage = "接手邀请已取消。";
                } else {
                    AppTheme.toast(parent, "该工单存在其他管理员之间待确认的接手邀请。", true);
                    return;
                }
                reload.accept(itemId);
                refreshTicketAfterOperation(parent, table, tickets, itemId, detailArea, ticketStateLabel,
                    detailCache, successMessage);
                return;
            }

            String assignedId = assignedAdminId(ticket);
            if (hasText(assignedId) && !assignedId.equals(actorId)) {
                AppTheme.toast(parent, "该工单由管理员 #" + assignedId + " 负责，不能代替其发起转派。", true);
                return;
            }

            List<AdminOption> adminOptions = activeAdminOptions();
            if (hasText(assignedId)) {
                adminOptions = adminOptions.stream()
                    .filter(option -> !currentUser.getUserId().equals(option.user().getUserId()))
                    .toList();
            }
            if (adminOptions.isEmpty()) {
                JOptionPane.showMessageDialog(parent,
                    hasText(assignedId) ? "暂无其他可接手的启用 ADMIN。" : "暂无可分配的启用 ADMIN。",
                    "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            JComboBox<AdminOption> adminBox = new JComboBox<>(adminOptions.toArray(new AdminOption[0]));
            if (!hasText(assignedId)) {
                selectCurrentAdmin(adminBox);
            }
            ChoiceDialog.Result<AdminOption> result = ChoiceDialog.show(
                parent,
                hasText(assignedId) ? "申请转派" : "认领或邀请接手",
                hasText(assignedId) ? "选择拟接手管理员" : "选择自己可直接认领；选择他人需对方确认",
                "工单 #" + itemId + " · 当前归属：" + assignmentSummary(ticket),
                "目标管理员",
                adminBox,
                "下一步");
            if (!result.accepted() || result.value() == null) {
                return;
            }
            AdminOption selectedAdmin = result.value();
            if (!hasText(assignedId) && currentUser.getUserId().equals(selectedAdmin.user().getUserId())) {
                int confirm = JOptionPane.showConfirmDialog(parent,
                    "确认认领工单 #" + itemId + "？\n认领后只有你可以回复和流转该工单。",
                    "确认认领", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }
                businessService.claimTicket(currentUser, itemId);
                successMessage = "工单已确认认领。";
            } else {
                JLabel warning = AppTheme.muted("当前负责人：" + assignmentSummary(ticket)
                    + "；拟邀请：" + selectedAdmin.user().getUsername() + "。目标管理员接受后才会生效。");
                TextEntryDialog.Result reasonResult = TextEntryDialog.show(parent, "填写转派原因",
                    "请输入邀请接手的具体原因", warning, 4, 42);
                if (!reasonResult.accepted()) {
                    return;
                }
                String reason = reasonResult.text() == null ? "" : reasonResult.text().trim();
                int confirm = JOptionPane.showConfirmDialog(parent,
                    "工单 #" + itemId
                        + "\n目标管理员：" + selectedAdmin.user().getUsername()
                        + "\n原因：" + reason
                        + "\n\n确认发送接手邀请？在对方接受前，负责人不会改变。",
                    "确认发送接手邀请", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }
                businessService.requestTicketTransfer(currentUser, itemId,
                    selectedAdmin.user().getUserId(), reason);
                successMessage = "接手邀请已发送，等待目标管理员确认。";
            }
            reload.accept(itemId);
            refreshTicketAfterOperation(parent, table, tickets, itemId, detailArea, ticketStateLabel,
                detailCache, successMessage);
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

    private void updateTicketActionButtons(JTable table, List<CrossTicketDTO> tickets, JLabel ticketStateLabel,
                                           JButton replyButton, JButton noteButton, JButton statusButton, JButton assignButton) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= tickets.size()) {
            setTicketActionButtonsEnabled(false, replyButton, noteButton, statusButton, assignButton);
            setTicketState(ticketStateLabel, null);
            return;
        }
        CrossTicketDTO ticket = tickets.get(selectedRow);
        boolean terminal = ticket.getOrder() != null
            && (Integer.valueOf(3).equals(ticket.getOrder().getStatus())
                || Integer.valueOf(4).equals(ticket.getOrder().getStatus()));
        boolean canProcess = canCurrentAdminProcess(ticket) && !terminal;
        setTicketActionButtonsEnabled(canProcess, replyButton, noteButton);
        statusButton.setEnabled(canProcess && !hasPendingTransfer(ticket));
        boolean canManageAssignment = canCurrentAdminManageAssignment(ticket)
            && (!terminal || hasPendingTransfer(ticket));
        assignButton.setEnabled(canManageAssignment);
        assignButton.setText(assignmentButtonText(ticket));
        setTicketState(ticketStateLabel, ticket);
    }

    private void setTicketActionButtonsEnabled(boolean enabled, JButton... buttons) {
        for (JButton button : buttons) {
            button.setEnabled(enabled);
        }
    }

    private boolean ensureCanProcessTicket(Component parent, CrossTicketDTO ticket, JLabel ticketStateLabel) {
        if (canCurrentAdminProcess(ticket)) {
            return true;
        }
        String assignedId = assignedAdminId(ticket);
        String message = hasText(assignedId)
            ? "当前工单已分配给管理员 " + assignedId + "，你可以查看，但不能处理。"
            : "当前工单尚未分配，请先确认认领后再处理。";
        setTicketState(ticketStateLabel, ticket);
        AppTheme.toast(parent, message, true);
        return false;
    }

    private JLabel createTicketStateLabel() {
        JLabel label = new JLabel();
        label.setOpaque(true);
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD, 12f));
        setTicketState(label, null);
        return label;
    }

    private void setTicketState(JLabel label, CrossTicketDTO ticket) {
        if (label == null) {
            return;
        }
        String text;
        java.awt.Color foreground;
        java.awt.Color background;
        if (ticket == null) {
            text = "未选择工单";
            foreground = AppTheme.MUTED;
            background = new java.awt.Color(243, 244, 246);
        } else {
            String assignedAdminId = assignedAdminId(ticket);
            String actorId = currentUser == null ? null : String.valueOf(currentUser.getUserId());
            if (hasPendingTransfer(ticket) && actorId != null && actorId.equals(transferTargetAdminId(ticket))) {
                text = "待我确认接手";
                foreground = new java.awt.Color(180, 83, 9);
                background = new java.awt.Color(255, 247, 237);
            } else if (hasPendingTransfer(ticket) && actorId != null
                    && actorId.equals(transferRequesterAdminId(ticket))) {
                text = "接手邀请待确认";
                foreground = new java.awt.Color(180, 83, 9);
                background = new java.awt.Color(255, 247, 237);
            } else if (hasPendingTransfer(ticket)) {
                text = "存在待确认接手邀请";
                foreground = AppTheme.MUTED;
                background = new java.awt.Color(243, 244, 246);
            } else if (assignedAdminId == null || assignedAdminId.isBlank()) {
                text = "未分配 · 请先认领";
                foreground = new java.awt.Color(180, 83, 9);
                background = new java.awt.Color(255, 247, 237);
            } else if (canCurrentAdminProcess(ticket)) {
                text = "已分配给我 · 可处理";
                foreground = new java.awt.Color(21, 128, 61);
                background = new java.awt.Color(240, 253, 244);
            } else {
                text = "已分配给其他客服 · 仅查看";
                foreground = AppTheme.MUTED;
                background = new java.awt.Color(243, 244, 246);
            }
        }
        label.setText(text);
        label.setForeground(foreground);
        label.setBackground(background);
        label.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 9, 5, 9));
    }

    private boolean canCurrentAdminProcess(CrossTicketDTO ticket) {
        String assignedAdminId = assignedAdminId(ticket);
        return currentUser != null && hasText(assignedAdminId)
            && assignedAdminId.equals(String.valueOf(currentUser.getUserId()));
    }

    private boolean canCurrentAdminManageAssignment(CrossTicketDTO ticket) {
        if (currentUser == null) {
            return false;
        }
        String actorId = String.valueOf(currentUser.getUserId());
        if (hasPendingTransfer(ticket)) {
            return actorId.equals(transferTargetAdminId(ticket)) || actorId.equals(transferRequesterAdminId(ticket));
        }
        String assignedId = assignedAdminId(ticket);
        return !hasText(assignedId) || actorId.equals(assignedId);
    }

    private String assignmentButtonText(CrossTicketDTO ticket) {
        if (currentUser != null && hasPendingTransfer(ticket)) {
            String actorId = String.valueOf(currentUser.getUserId());
            if (actorId.equals(transferTargetAdminId(ticket))) {
                return "处理接手邀请";
            }
            if (actorId.equals(transferRequesterAdminId(ticket))) {
                return "取消接手邀请";
            }
        }
        return hasText(assignedAdminId(ticket)) ? "申请转派" : "认领/邀请接手";
    }

    private String assignedAdminId(CrossTicketDTO ticket) {
        if (ticket == null || ticket.getItemDetail() == null || ticket.getItemDetail().getMetadata() == null) {
            return null;
        }
        return ticket.getItemDetail().getMetadata().getAssignedAdminId();
    }

    private String transferRequesterAdminId(CrossTicketDTO ticket) {
        return ticketMetadata(ticket) == null ? null : ticketMetadata(ticket).getTransferRequestedByAdminId();
    }

    private String transferRequestId(CrossTicketDTO ticket) {
        if (ticket != null && ticket.getOrder() != null && hasText(ticket.getOrder().getTransferRequestId())) {
            return ticket.getOrder().getTransferRequestId();
        }
        return ticketMetadata(ticket) == null ? null : ticketMetadata(ticket).getTransferRequestId();
    }

    private String transferTargetAdminId(CrossTicketDTO ticket) {
        if (ticket != null && ticket.getOrder() != null && ticket.getOrder().getTransferTargetAdminId() != null) {
            return String.valueOf(ticket.getOrder().getTransferTargetAdminId());
        }
        return ticketMetadata(ticket) == null ? null : ticketMetadata(ticket).getTransferTargetAdminId();
    }

    private String transferReason(CrossTicketDTO ticket) {
        return ticketMetadata(ticket) == null ? null : ticketMetadata(ticket).getTransferReason();
    }

    private boolean hasPendingTransfer(CrossTicketDTO ticket) {
        return hasText(transferTargetAdminId(ticket));
    }

    private Integer homeDisplayStatus(CrossTicketDTO ticket) {
        String actorId = currentUser == null || currentUser.getUserId() == null
            ? null : String.valueOf(currentUser.getUserId());
        if (actorId != null && actorId.equals(transferTargetAdminId(ticket))) {
            return CrossDatabaseQueryService.STATUS_PENDING_CONFIRMATION;
        }
        return ticket == null || ticket.getOrder() == null ? null : ticket.getOrder().getStatus();
    }

    private Integer displayStatus(CrossTicketDTO ticket) {
        if (hasPendingTransfer(ticket)) {
            return CrossDatabaseQueryService.STATUS_PENDING_CONFIRMATION;
        }
        return ticket == null || ticket.getOrder() == null ? null : ticket.getOrder().getStatus();
    }

    private ItemDetail.Metadata ticketMetadata(CrossTicketDTO ticket) {
        return ticket == null || ticket.getItemDetail() == null ? null : ticket.getItemDetail().getMetadata();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String assignmentSummary(CrossTicketDTO ticket) {
        String assignedAdminId = assignedAdminId(ticket);
        if (assignedAdminId == null || assignedAdminId.isBlank()) {
            return "未分配";
        }
        if (currentUser != null && assignedAdminId.equals(String.valueOf(currentUser.getUserId()))) {
            return "我（" + currentUser.getUsername() + "）";
        }
        return "管理员 #" + assignedAdminId;
    }

    private List<AdminOption> activeAdminOptions() {
        return userService.listAssignableStaff(currentUser).stream()
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
        filters.add(new AssignmentFilter(AssignmentFilterKind.PENDING_MINE, null, "待我确认"));
        for (AdminOption option : adminOptions) {
            String adminId = String.valueOf(option.user().getUserId());
            filters.add(new AssignmentFilter(AssignmentFilterKind.ADMIN, adminId, option.toString()));
        }
        return filters;
    }

    private void selectAssignmentFilter(JComboBox<AssignmentFilter> filterBox, AssignmentFilterKind kind) {
        AssignmentFilterKind target = kind == null ? AssignmentFilterKind.ALL : kind;
        for (int index = 0; index < filterBox.getItemCount(); index++) {
            AssignmentFilter filter = filterBox.getItemAt(index);
            if (filter != null && filter.kind() == target) {
                filterBox.setSelectedIndex(index);
                return;
            }
        }
        filterBox.setSelectedIndex(0);
    }

    private AssignmentFilter selectedAssignmentFilter(JComboBox<AssignmentFilter> assignmentFilterBox) {
        AssignmentFilter filter = (AssignmentFilter) assignmentFilterBox.getSelectedItem();
        return filter == null ? new AssignmentFilter(AssignmentFilterKind.ALL, null, "全部工单") : filter;
    }

    private AssignmentScope assignmentScope(AssignmentFilter filter) {
        return switch (filter.kind()) {
            case ALL -> AssignmentScope.ALL;
            case UNASSIGNED -> AssignmentScope.UNASSIGNED;
            case MINE, ADMIN -> AssignmentScope.ASSIGNED_TO;
            case PENDING_MINE -> AssignmentScope.PENDING_TRANSFER_TO;
        };
    }

    private String assignmentAdminId(AssignmentFilter filter, User actor) {
        if (filter.kind() == AssignmentFilterKind.MINE
                || filter.kind() == AssignmentFilterKind.PENDING_MINE) {
            return actor == null ? null : String.valueOf(actor.getUserId());
        }
        return filter.kind() == AssignmentFilterKind.ADMIN ? filter.adminId() : null;
    }

    private String assignedAdminDisplay(String assignedAdminId, java.util.Map<String, String> adminNameById) {
        if (assignedAdminId == null || assignedAdminId.isBlank()) {
            return "未分配";
        }
        String adminName = adminNameById.get(assignedAdminId);
        return adminName == null ? assignedAdminId : assignedAdminId + " - " + adminName;
    }

    private void refreshTicketAfterOperation(Component parent, JTable table, List<CrossTicketDTO> tickets,
                                             Long itemId, JTextArea detailArea, JLabel ticketStateLabel,
                                             java.util.Map<Long, CrossTicketDTO> detailCache,
                                             String noticePrefix) {
        int row = selectTicketRow(table, tickets, itemId);
        try {
            CrossTicketDTO freshTicket = crossDatabaseQueryService.getTicket(currentUser, itemId);
            detailCache.put(itemId, freshTicket);
            if (row >= 0) {
                tickets.set(row, freshTicket);
            }
            detailArea.setText(formatTicketDetail(freshTicket));
            detailArea.setCaretPosition(0);
            refreshRightTicketSummary(freshTicket);
            setTicketState(ticketStateLabel, freshTicket);
            AppTheme.toast(parent, row >= 0 ? noticePrefix : noticePrefix + "该工单已不在当前筛选结果中。", false);
        } catch (Exception ex) {
            AppTheme.toast(parent, noticePrefix + "但刷新工单详情失败：" + ex.getMessage(), true);
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
            + "\n状态：" + (ticket.getOrder() == null ? "" : statusText(displayStatus(ticket)))
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

    private String formatTicketLoadingDetail(CrossTicketDTO ticket) {
        String summary = formatTicketDetail(ticket);
        int conversationStart = summary.indexOf("\n回复与备注：");
        if (conversationStart >= 0) {
            summary = summary.substring(0, conversationStart);
        }
        return summary + "\n\n正在同步完整的客户资料与回复记录…";
    }

    private String formatTicketDetail(CrossTicketDTO ticket) {
        StringBuilder builder = new StringBuilder();
        builder.append("工单编号：").append(ticket.getItem().getItemId()).append('\n');
        builder.append("标题：").append(ticket.getItem().getTitle()).append('\n');
        if (ticket.getOrder() != null) {
            builder.append("记录编号：").append(ticket.getOrder().getOrderId()).append('\n');
            builder.append("状态：").append(statusText(displayStatus(ticket))).append('\n');
            builder.append("金额：").append(ticket.getOrder().getAmount()).append('\n');
            builder.append("创建时间：").append(TimeFormatUtil.format(ticket.getOrder().getCreatedAt())).append('\n');
            builder.append("SLA：").append(SlaDisplayUtil.countdown(ticket.getOrder())).append('\n');
            builder.append("首次响应截止：")
                .append(TimeFormatUtil.format(ticket.getOrder().getFirstResponseDueAt())).append('\n');
            builder.append("下次响应截止：")
                .append(TimeFormatUtil.format(ticket.getOrder().getNextResponseDueAt())).append('\n');
            builder.append("解决截止：")
                .append(TimeFormatUtil.format(ticket.getOrder().getResolutionDueAt())).append('\n');
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
            if (metadata != null && hasText(metadata.getTransferTargetAdminId())) {
                builder.append("接手邀请：管理员 #")
                    .append(metadata.getTransferRequestedByAdminId())
                    .append(" → 管理员 #")
                    .append(metadata.getTransferTargetAdminId())
                    .append("（等待目标确认）").append('\n');
                builder.append("转派原因：").append(nullToEmpty(metadata.getTransferReason())).append('\n');
                builder.append("申请时间：").append(metadata.getTransferRequestedAt() == null ? "—"
                    : TimeFormatUtil.format(metadata.getTransferRequestedAt())).append('\n');
            }
            if (metadata != null) {
                builder.append("用户催促：").append(metadata.getReminderCount()).append(" 次").append('\n');
                builder.append("最近催促：").append(metadata.getLastRemindedAt() == null ? "尚未催促"
                    : TimeFormatUtil.format(metadata.getLastRemindedAt())).append('\n');
            }
            builder.append("\n描述：\n").append(nullToEmpty(detail.getDescription())).append('\n');
        }
        if (!ticket.getConsistencyWarnings().isEmpty()) {
            builder.append("\n数据提示：\n");
            for (String warning : ticket.getConsistencyWarnings()) {
                builder.append("- ").append(warning).append('\n');
            }
        }
        appendAdminTicketHistory(builder, ticket.getHistories());
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
                .append(inlineCommentText(comment))
                .append('\n');
            if (comment.getAttachments() != null) {
                for (TicketAttachment attachment : comment.getAttachments()) {
                    builder.append("[")
                        .append(attachment.isImage() ? "图片" : "文件")
                        .append("] ")
                        .append(nullToEmpty(attachment.getFileName()))
                        .append(" · ")
                        .append(formatFileSize(attachment.getSize()))
                        .append('\n');
                }
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024) {
            return String.format("%.1f KB", bytes / 1024d);
        }
        return String.format("%.1f MB", bytes / (1024d * 1024d));
    }

    private String inlineCommentText(Comment comment) {
        String content = nullToEmpty(comment.getContent());
        String legacyEmoji = StickerCatalog.display(comment.getStickerCode());
        if (legacyEmoji.isBlank()) {
            return content;
        }
        return content.isBlank() ? legacyEmoji : content + " " + legacyEmoji;
    }

    private void appendAdminTicketHistory(StringBuilder builder, List<TicketHistory> histories) {
        builder.append("\n流转历史：\n");
        if (histories == null || histories.isEmpty()) {
            builder.append("暂无历史记录\n");
            return;
        }
        for (TicketHistory history : histories) {
            builder.append("[").append(TimeFormatUtil.format(history.getOccurredAt())).append("] ")
                .append(adminHistoryText(history)).append('\n');
        }
    }

    private String adminHistoryText(TicketHistory history) {
        String actor = history.getActorUsername() == null || history.getActorUsername().isBlank()
            ? (history.getActorUserId() == null ? "系统" : "用户 #" + history.getActorUserId())
            : history.getActorUsername() + " (#" + history.getActorUserId() + ")";
        return switch (history.getEventType()) {
            case "TICKET_CREATED" -> actor + " 创建工单";
            case "AUTO_ASSIGNED" -> "系统自动分配工单" + reasonSuffix(history.getReason());
            case "MIGRATION_SNAPSHOT" -> "历史数据迁移快照，当时状态：" + statusText(history.getToStatus());
            case "TICKET_CLAIMED" -> actor + " 认领工单";
            case "TRANSFER_REQUESTED" -> actor + " 申请转派：" + adminFlow(history);
            case "TRANSFER_ACCEPTED" -> actor + " 接受转派：" + adminFlow(history);
            case "TRANSFER_REJECTED" -> actor + " 拒绝接手邀请" + reasonSuffix(history.getReason());
            case "TRANSFER_CANCELLED" -> actor + " 撤销接手邀请" + reasonSuffix(history.getReason());
            case "STATUS_CHANGED", "AUTO_CANCELLED", "BATCH_STATUS_CHANGED" -> actor + " 变更状态："
                + statusText(history.getFromStatus()) + " → " + statusText(history.getToStatus());
            case "REMINDER_SENT" -> actor + " 催促处理";
            case "CUSTOMER_REPLY_ADDED" -> actor + " 追加客户回复";
            case "ADMIN_REPLY_ADDED" -> actor + " 添加客服回复";
            case "INTERNAL_NOTE_ADDED" -> actor + " 添加内部备注";
            case "RATING_SUBMITTED" -> actor + " 提交工单评价";
            case "CATEGORY_REASSIGNED" -> "系统调整工单分类" + reasonSuffix(history.getReason());
            default -> actor + " " + history.getEventType();
        };
    }

    private String adminFlow(TicketHistory history) {
        String from = history.getFromAdminId() == null ? "未分配" : "管理员 #" + history.getFromAdminId();
        String to = history.getToAdminId() == null ? "未分配" : "管理员 #" + history.getToAdminId();
        return from + " → " + to + reasonSuffix(history.getReason());
    }

    private String reasonSuffix(String reason) {
        return reason == null || reason.isBlank() ? "" : "，原因：" + reason;
    }

    private String formatBeijingTime(Instant instant) {
        return TimeFormatUtil.format(instant);
    }

    private void showCategoryManager() {
        if (focusExistingModulePage("分类管理")) {
            return;
        }
        CategoryManagementPanel panel = new CategoryManagementPanel(currentUser, categoryService,
            categoryDisplayNames::clear, message -> {
                if (isSelectedModule("分类管理")) {
                    centerArea.setText(message);
                    rightArea.setText("分类结构与工单影响已同步");
                }
            });
        registerModulePage("分类管理", panel);
    }

    private void showKnowledgeManager() {
        if (focusExistingModulePage("知识库与快捷回复")) return;
        registerModulePage("知识库与快捷回复", new KnowledgeManagementPanel(currentUser, knowledgeService));
    }

    private void applyReplyTemplate(Component parent, JTable table, List<CrossTicketDTO> tickets,
                                    JTextArea detailArea, JLabel ticketStateLabel, long[] detailRequestVersions,
                                    java.util.Map<Long, CrossTicketDTO> detailCache) {
        CrossTicketDTO ticket = selectedTicket(parent, table, tickets);
        if (ticket == null || !ensureCanProcessTicket(parent, ticket, ticketStateLabel)) return;
        Long categoryId = ticket.getItem() == null ? null : ticket.getItem().getCategoryId();
        try {
            List<com.ticket.model.ReplyTemplate> templates = knowledgeService.templates(currentUser, categoryId);
            if (templates.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "当前分类没有可用的快捷回复。", "快捷回复",
                    JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            JComboBox<com.ticket.model.ReplyTemplate> choices = new JComboBox<>(
                templates.toArray(new com.ticket.model.ReplyTemplate[0]));
            ChoiceDialog.Result<com.ticket.model.ReplyTemplate> selection = ChoiceDialog.show(parent,
                "快捷回复", "选择标准回复", "将所选内容直接发送给客户", "回复模板", choices, "发送");
            if (!selection.accepted() || selection.value() == null) return;
            com.ticket.model.ReplyTemplate template = selection.value();
            businessService.addAdminReply(currentUser, ticket.getItem().getItemId(), template.getContent());
            detailRequestVersions[0]++;
            refreshTicketAfterOperation(parent, table, tickets, ticket.getItem().getItemId(), detailArea,
                ticketStateLabel, detailCache, "快捷回复已发送。");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, rootMessage(ex), "快捷回复失败", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void applyHandlingMacro(Component parent, JTable table, List<CrossTicketDTO> tickets,
                                    java.util.function.Consumer<Long> reload, JTextArea detailArea,
                                    JLabel ticketStateLabel, java.util.Map<Long, CrossTicketDTO> detailCache) {
        CrossTicketDTO ticket = selectedTicket(parent, table, tickets);
        if (ticket == null || !ensureCanProcessTicket(parent, ticket, ticketStateLabel)) return;
        try {
            List<com.ticket.model.HandlingMacro> macros = knowledgeService.macros(currentUser);
            if (macros.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "暂无启用的处理宏。", "处理宏", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            JComboBox<com.ticket.model.HandlingMacro> choices = new JComboBox<>(
                macros.toArray(new com.ticket.model.HandlingMacro[0]));
            ChoiceDialog.Result<com.ticket.model.HandlingMacro> selection = ChoiceDialog.show(parent,
                "应用处理宏", "选择处理宏", "处理宏会发送标准回复并可同时流转状态", "处理宏", choices, "应用");
            if (!selection.accepted() || selection.value() == null) return;
            com.ticket.model.HandlingMacro macro = selection.value();
            Long itemId = ticket.getItem().getItemId();
            knowledgeService.applyMacro(currentUser, itemId, macro.getMacroId());
            reload.accept(itemId);
            refreshTicketAfterOperation(parent, table, tickets, itemId, detailArea, ticketStateLabel,
                detailCache, "处理宏已应用。");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, rootMessage(ex), "处理宏失败", JOptionPane.WARNING_MESSAGE);
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
            case 5 -> "等待客户回复";
            case 6 -> "暂挂";
            case CrossDatabaseQueryService.STATUS_PENDING_CONFIRMATION -> "待确认";
            default -> "未知(" + status + ")";
        };
    }

    private String roleText(String role) {
        return switch (role == null ? "" : role) {
            case "ROOT" -> "系统所有者";
            case "ADMIN" -> "管理员";
            case "USER" -> "普通用户";
            default -> role == null ? "" : role;
        };
    }

    private TicketStatusOption[] statusOptions(Integer status) {
        if (status == null) {
            return new TicketStatusOption[0];
        }
        return switch (status) {
            case 0 -> new TicketStatusOption[]{
                new TicketStatusOption(1, "处理中"),
                new TicketStatusOption(4, "已取消")};
            case 1 -> new TicketStatusOption[]{
                new TicketStatusOption(2, "已完成"),
                new TicketStatusOption(5, "等待客户回复"),
                new TicketStatusOption(6, "暂挂"),
                new TicketStatusOption(4, "已取消")};
            case 2 -> new TicketStatusOption[]{new TicketStatusOption(3, "已关闭")};
            case 5, 6 -> new TicketStatusOption[]{new TicketStatusOption(1, "恢复处理中")};
            default -> new TicketStatusOption[0];
        };
    }

    private int statusFilterIndex(Integer status) {
        if (status == null) return 0;
        if (status >= 0 && status <= 6) return status + 1;
        return status == CrossDatabaseQueryService.STATUS_PENDING_CONFIRMATION ? 8 : 0;
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
        headerLabel.setText(roleText(user.getRole()) + " · " + user.getUsername());
        updateNavigationForRole();
        refreshNotificationBadge();
        notificationTimer.restart();
        if ("ROOT".equals(user.getRole())) {
            openModule("权限管理");
        } else {
            showAdminHome();
        }
    }

    /** 退出时停止当前模块后台任务并清除当前会话展示。 */
    public void clearSession() {
        closeActiveModule();
        homeClockTimer.stop();
        notificationTimer.stop();
        if (homeSummaryWorker != null) {
            homeSummaryWorker.cancel(true);
        }
        selectedModuleName = null;
        currentUser = null;
        categoryDisplayNames.clear();
        homeRiskTickets.clear();
        homeRiskModel.setRowCount(0);
        homeStatusChart.setSegments(List.of());
        homeUpdatedLabel.setText("尚未更新");
        homeRiskHint.setText("尚未加载个人待办");
        headerLabel.setText("未登录");
        notificationButton.setText("通知");
        updateNavigationSelection(null);
        centerArea.setText("");
        rightArea.setText("");
    }

    private void refreshNotificationBadge() {
        User actor = currentUser;
        if (actor == null) {
            notificationButton.setText("通知");
            return;
        }
        new SwingWorker<Long, Void>() {
            @Override protected Long doInBackground() { return notificationService.unreadCount(actor); }
            @Override protected void done() {
                if (actor != currentUser) return;
                try {
                    long unread = get();
                    notificationButton.setText(unread > 0 ? "通知 (" + Math.min(99, unread) + ")" : "通知");
                } catch (Exception ignored) {
                    notificationButton.setText("通知");
                }
            }
        }.execute();
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

    private void showPermissionManager() {
        if (focusExistingModulePage("权限管理")) {
            return;
        }
        User root = currentUser;
        UserService.requireRoot(root);
        DefaultTableModel model = new DefaultTableModel(
            new Object[]{"用户ID", "用户名", "邮箱", "角色", "状态", "密码状态", "创建时间"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        AppTheme.styleTable(table);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMaxWidth(90);
        table.getColumnModel().getColumn(3).setMaxWidth(120);
        table.getColumnModel().getColumn(4).setMaxWidth(90);

        JButton createButton = new JButton("创建后台账号");
        JButton changeRoleButton = new JButton("调整角色");
        JButton refreshButton = new JButton("刷新");
        AppTheme.primary(createButton);
        AppTheme.secondary(changeRoleButton);
        AppTheme.secondary(refreshButton);
        changeRoleButton.setEnabled(false);
        JLabel summaryLabel = AppTheme.muted("正在加载权限账号…");
        List<User> accounts = new ArrayList<>();

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.setOpaque(false);
        toolbar.add(createButton);
        toolbar.add(changeRoleButton);
        toolbar.add(refreshButton);

        JPanel page = createModulePage();
        page.add(AppTheme.pageHeader("权限管理", "创建后台账号、调整角色并保护系统所有者连续性"), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setBackground(AppTheme.PAGE);
        body.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 14, 12, 14));
        JPanel toolbarCard = AppTheme.surface(new BorderLayout());
        toolbarCard.add(toolbar, BorderLayout.WEST);
        toolbarCard.add(summaryLabel, BorderLayout.EAST);
        body.add(toolbarCard, BorderLayout.NORTH);
        JPanel listCard = AppTheme.surface(new BorderLayout(0, 8));
        listCard.add(new JLabel("ROOT / ADMIN 账号"), BorderLayout.NORTH);
        listCard.add(AppTheme.scroll(table), BorderLayout.CENTER);
        listCard.add(AppTheme.muted("任何角色不能操作自己；最后一个有效 ROOT 不能被禁用或降级。"), BorderLayout.SOUTH);
        body.add(listCard, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);

        Runnable render = () -> {
            model.setRowCount(0);
            for (User account : accounts) {
                if ("USER".equals(account.getRole())) {
                    continue;
                }
                model.addRow(new Object[]{account.getUserId(), account.getUsername(), account.getEmail(),
                    roleText(account.getRole()), Integer.valueOf(1).equals(account.getStatus()) ? "已启用" : "已禁用",
                    Integer.valueOf(1).equals(account.getMustChangePassword()) ? "待首次换密" : "正常",
                    TimeFormatUtil.format(account.getCreatedAt())});
            }
            long activeRoots = accounts.stream().filter(account -> "ROOT".equals(account.getRole()))
                .filter(account -> Integer.valueOf(1).equals(account.getStatus())).count();
            summaryLabel.setText("后台账号 " + model.getRowCount() + " · 有效 ROOT " + activeRoots);
            table.clearSelection();
            changeRoleButton.setEnabled(false);
        };

        Runnable load = () -> {
            refreshButton.setEnabled(false);
            new SwingWorker<List<User>, Void>() {
                @Override
                protected List<User> doInBackground() {
                    return userService.listUsers(root);
                }

                @Override
                protected void done() {
                    if (root != currentUser) {
                        return;
                    }
                    try {
                        accounts.clear();
                        accounts.addAll(get());
                        render.run();
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                        JOptionPane.showMessageDialog(page, cause.getMessage(), "加载失败", JOptionPane.WARNING_MESSAGE);
                    } finally {
                        refreshButton.setEnabled(true);
                    }
                }
            }.execute();
        };

        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row < 0) {
                    changeRoleButton.setEnabled(false);
                    return;
                }
                Long userId = Long.valueOf(String.valueOf(table.getValueAt(row, 0)));
                changeRoleButton.setEnabled(!root.getUserId().equals(userId));
            }
        });
        createButton.addActionListener(event -> createManagedAccount(page, createButton, load));
        changeRoleButton.addActionListener(event -> changeSelectedRole(page, table, changeRoleButton, load));
        refreshButton.addActionListener(event -> load.run());
        registerModulePage("权限管理", page);
        load.run();
    }

    private void createManagedAccount(Component parent, JButton button, Runnable reload) {
        User actor = currentUser;
        JTextField username = new JTextField(20);
        JTextField email = new JTextField(20);
        JTextField phone = new JTextField(20);
        JComboBox<UserRole> role = new JComboBox<>(new UserRole[]{UserRole.ROOT, UserRole.ADMIN});
        role.setSelectedItem(UserRole.ADMIN);
        JTextField reason = new JTextField(20);
        for (JTextField field : new JTextField[]{username, email, phone, reason}) {
            AppTheme.styleInput(field);
            field.setPreferredSize(new Dimension(330, 36));
        }
        AppTheme.styleComboBox(role);
        role.setPreferredSize(new Dimension(330, 36));

        JPanel form = new JPanel(new java.awt.GridBagLayout());
        form.setOpaque(false);
        addAccountFormRow(form, 0, "用户名", username);
        addAccountFormRow(form, 1, "邮箱", email);
        addAccountFormRow(form, 2, "手机号", phone);
        addAccountFormRow(form, 3, "角色", role);
        addAccountFormRow(form, 4, "创建原因", reason);

        JLabel status = AppTheme.muted("填写完成后创建；失败时窗口和已填内容会保留。");
        JButton cancel = new JButton("取消");
        JButton submit = new JButton("创建账号");
        AppTheme.secondary(cancel);
        AppTheme.primary(submit);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancel);
        actions.add(submit);

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(AppTheme.PAGE);
        content.setBorder(javax.swing.BorderFactory.createEmptyBorder(20, 24, 18, 24));
        JPanel heading = new JPanel(new BorderLayout(0, 5));
        heading.setOpaque(false);
        JLabel title = new JLabel("创建后台账号");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 20f));
        heading.add(title, BorderLayout.NORTH);
        heading.add(AppTheme.muted("临时密码仅显示一次，新账号首次登录必须修改密码。"), BorderLayout.CENTER);
        JPanel center = AppTheme.surface(new BorderLayout(0, 12));
        center.add(form, BorderLayout.CENTER);
        center.add(status, BorderLayout.SOUTH);
        content.add(heading, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);

        JDialog dialog = new JDialog(mainFrame, "创建后台账号", true);
        WindowIconUtil.apply(dialog);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(content);
        dialog.setResizable(false);
        AppTheme.closeOnEscape(dialog);
        dialog.getRootPane().setDefaultButton(submit);
        cancel.addActionListener(event -> dialog.dispose());
        submit.addActionListener(event -> {
            UserRole selectedRole = (UserRole) role.getSelectedItem();
            submit.setEnabled(false);
            cancel.setEnabled(false);
            status.setForeground(AppTheme.PRIMARY);
            status.setText("正在创建账号…");
            new SwingWorker<UserService.ManagedAccountResult, Void>() {
                @Override
                protected UserService.ManagedAccountResult doInBackground() {
                    return userService.createManagedAccount(actor, username.getText(), email.getText(), phone.getText(),
                        selectedRole == null ? null : selectedRole.name(), reason.getText());
                }

                @Override
                protected void done() {
                    if (actor != currentUser) {
                        dialog.dispose();
                        return;
                    }
                    try {
                        UserService.ManagedAccountResult result = get();
                        dialog.dispose();
                        showTemporaryPassword(parent, result.user().getUsername(), result.temporaryPassword());
                        reload.run();
                    } catch (Exception ex) {
                        status.setForeground(AppTheme.DANGER);
                        status.setText("创建失败：" + rootMessage(ex));
                        submit.setEnabled(true);
                        cancel.setEnabled(true);
                        username.requestFocusInWindow();
                    }
                }
            }.execute();
        });

        button.setEnabled(false);
        dialog.pack();
        dialog.setSize(new Dimension(570, 465));
        dialog.setLocationRelativeTo(parent);
        SwingUtilities.invokeLater(username::requestFocusInWindow);
        dialog.setVisible(true);
        button.setEnabled(true);
    }

    private void addAccountFormRow(JPanel form, int row, String labelText, javax.swing.JComponent field) {
        java.awt.GridBagConstraints labelConstraints = new java.awt.GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = java.awt.GridBagConstraints.WEST;
        labelConstraints.insets = new java.awt.Insets(5, 2, 5, 16);
        form.add(new JLabel(labelText), labelConstraints);

        java.awt.GridBagConstraints fieldConstraints = new java.awt.GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new java.awt.Insets(5, 0, 5, 2);
        form.add(field, fieldConstraints);
    }

    private void changeSelectedRole(Component parent, JTable table, JButton button, Runnable reload) {
        User actor = currentUser;
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        Long userId = Long.valueOf(String.valueOf(table.getValueAt(row, 0)));
        String username = String.valueOf(table.getValueAt(row, 1));
        String displayedRole = String.valueOf(table.getValueAt(row, 3));
        JComboBox<UserRole> role = new JComboBox<>(new UserRole[]{UserRole.ROOT, UserRole.ADMIN});
        for (UserRole candidate : UserRole.values()) {
            if (roleText(candidate.name()).equals(displayedRole)) {
                role.setSelectedItem(candidate);
                break;
            }
        }
        JTextField reason = new JTextField(20);
        AppTheme.styleComboBox(role);
        AppTheme.styleInput(reason);
        role.setPreferredSize(new Dimension(330, 36));
        reason.setPreferredSize(new Dimension(330, 36));
        JPanel form = new JPanel(new java.awt.GridBagLayout());
        form.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 10, 8, 10));
        addAccountFormRow(form, 0, "目标账号", new JLabel(username));
        addAccountFormRow(form, 1, "新角色", role);
        addAccountFormRow(form, 2, "调整原因", reason);
        if (JOptionPane.showConfirmDialog(parent, form, "调整角色",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        String password = promptCurrentPassword(parent);
        if (password == null) return;
        UserRole selectedRole = (UserRole) role.getSelectedItem();
        button.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                userService.changeUserRole(actor, userId, selectedRole == null ? null : selectedRole.name(),
                    reason.getText(), password);
                return null;
            }

            @Override
            protected void done() {
                if (actor != currentUser) {
                    return;
                }
                try {
                    get();
                    JOptionPane.showMessageDialog(parent, "角色已更新。", "操作成功", JOptionPane.INFORMATION_MESSAGE);
                    reload.run();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    JOptionPane.showMessageDialog(parent, cause.getMessage(), "角色调整失败", JOptionPane.WARNING_MESSAGE);
                } finally {
                    button.setEnabled(true);
                }
            }
        }.execute();
    }

    private String promptCurrentPassword(Component parent) {
        JPasswordField field = new JPasswordField(20);
        AppTheme.styleInput(field);
        if (JOptionPane.showConfirmDialog(parent, field, "敏感操作 · 重新输入当前密码",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return null;
        }
        String password = new String(field.getPassword());
        field.setText("");
        return password;
    }

    private void showTemporaryPassword(Component parent, String username, String password) {
        showTemporaryPassword(parent, "后台账号已创建", username, password);
    }

    private void showTemporaryPassword(Component parent, String dialogTitle, String username, String password) {
        JTextField passwordField = new JTextField(password);
        passwordField.setEditable(false);
        passwordField.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, 16));
        AppTheme.styleInput(passwordField);
        passwordField.setPreferredSize(new Dimension(500, 40));
        passwordField.setCaretPosition(0);

        JLabel title = new JLabel("临时密码（仅显示本次）");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 20f));
        JLabel account = new JLabel("账号：" + username);
        account.setFont(account.getFont().deriveFont(java.awt.Font.PLAIN, 14f));
        JPanel heading = new JPanel(new BorderLayout(0, 6));
        heading.setOpaque(false);
        heading.add(title, BorderLayout.NORTH);
        heading.add(account, BorderLayout.SOUTH);

        JPanel passwordCard = AppTheme.surface(new BorderLayout(0, 8));
        passwordCard.add(new JLabel("临时密码"), BorderLayout.NORTH);
        passwordCard.add(passwordField, BorderLayout.CENTER);

        JLabel hint = AppTheme.muted("用户使用该密码登录后，系统会强制其立即设置个人新密码。请通过安全渠道交付。");
        JButton confirm = new JButton("确定");
        AppTheme.primary(confirm);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        actions.add(confirm);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setBackground(AppTheme.PAGE);
        body.setBorder(javax.swing.BorderFactory.createEmptyBorder(20, 24, 18, 24));
        body.add(heading, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        center.add(passwordCard, BorderLayout.NORTH);
        center.add(hint, BorderLayout.SOUTH);
        body.add(center, BorderLayout.CENTER);
        body.add(actions, BorderLayout.SOUTH);

        JDialog dialog = new JDialog(mainFrame, dialogTitle, true);
        WindowIconUtil.apply(dialog);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(body);
        dialog.setResizable(false);
        AppTheme.closeOnEscape(dialog);
        dialog.getRootPane().setDefaultButton(confirm);
        confirm.addActionListener(event -> dialog.dispose());
        dialog.pack();
        dialog.setSize(new Dimension(620, 300));
        dialog.setLocationRelativeTo(parent);
        SwingUtilities.invokeLater(confirm::requestFocusInWindow);
        dialog.setVisible(true);
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
                setOpaque(true);
                setBackground(AppTheme.tableCellBackground(table, selected, row));
                setForeground("已启用".equals(text) ? AppTheme.SUCCESS : AppTheme.DANGER);
                setFont(getFont().deriveFont(java.awt.Font.BOLD));
                setBorder(AppTheme.tableCellBorder(selected, column));
                return this;
            }
        });

        JTextField keywordField = new JTextField(16);
        JComboBox<String> statusFilter = new JComboBox<>(new String[]{"全部状态", "已启用", "已禁用"});
        JComboBox<String> roleFilter = new JComboBox<>(new String[]{
            "全部角色", "ROOT · 系统所有者", "ADMIN · 管理员", "USER · 普通用户"
        });
        AppTheme.styleComboBox(statusFilter);
        AppTheme.styleComboBox(roleFilter);
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
        toolbar.add(new JLabel("角色"));
        toolbar.add(roleFilter);
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
            String selectedRole = switch (roleFilter.getSelectedIndex()) {
                case 1 -> "ROOT";
                case 2 -> "ADMIN";
                case 3 -> "USER";
                default -> null;
            };
            model.setRowCount(0);
            for (User user : loadedUsers) {
                boolean statusMatches = selectedStatus == 0
                    || (selectedStatus == 1 && Integer.valueOf(1).equals(user.getStatus()))
                    || (selectedStatus == 2 && !Integer.valueOf(1).equals(user.getStatus()));
                boolean keywordMatches = keyword.isBlank()
                    || nullToEmpty(user.getUsername()).toLowerCase().contains(keyword)
                    || nullToEmpty(user.getEmail()).toLowerCase().contains(keyword);
                boolean roleMatches = selectedRole == null || selectedRole.equals(user.getRole());
                if (statusMatches && roleMatches && keywordMatches) {
                    model.addRow(new Object[]{user.getUserId(), user.getUsername(), user.getEmail(), user.getPhone(),
                        roleText(user.getRole()), Integer.valueOf(1).equals(user.getStatus()) ? "已启用" : "已禁用",
                        Integer.valueOf(1).equals(user.getMustChangePassword()) ? "待首次换密" : "正常",
                        TimeFormatUtil.format(user.getCreatedAt())});
                }
            }
            long enabledCount = loadedUsers.stream().filter(user -> Integer.valueOf(1).equals(user.getStatus())).count();
            statusLabel.setForeground(AppTheme.MUTED);
            statusLabel.setText("共 " + loadedUsers.size() + " · 启用 " + enabledCount
                + " · 禁用 " + (loadedUsers.size() - enabledCount) + " · 显示 " + model.getRowCount());
            clearSelectedUser.run();
        };

        Runnable loadUsers = () -> {
            refreshButton.setEnabled(false);
            statusLabel.setForeground(AppTheme.MUTED);
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
                        statusLabel.setForeground(AppTheme.DANGER);
                        statusLabel.setText("用户列表加载失败：" + rootMessage(ex));
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
                User target = loadedUsers.stream().filter(user -> userId.equals(user.getUserId())).findFirst().orElse(null);
                boolean manageable = UserService.canManageAccount(manager, target);
                boolean lowerRole = target != null && manager != null
                    && UserRole.from(target.getRole()).isLowerThan(UserRole.from(manager.getRole()));
                selectedTitle.setText(username + (currentAccount ? "（当前账号）" : ""));
                selectedDetails.setForeground(AppTheme.TEXT);
                selectedDetails.setText("用户 ID：" + userId
                    + "\n角色：" + userTable.getValueAt(row, 4)
                    + "\n状态：" + status
                    + "\n邮箱：" + nullToEmpty(userTable.getValueAt(row, 2))
                    + "\n手机号：" + nullToEmpty(userTable.getValueAt(row, 3))
                    + "\n密码状态：" + nullToEmpty(userTable.getValueAt(row, 6))
                    + "\n创建时间：" + nullToEmpty(userTable.getValueAt(row, 7)));
                enableButton.setEnabled(manageable && "已禁用".equals(status));
                disableButton.setEnabled(manageable && "已启用".equals(status) && !currentAccount);
                resetPasswordButton.setEnabled(lowerRole && !currentAccount);
            }
        });
        searchButton.addActionListener(event -> renderUsers.run());
        keywordField.addActionListener(event -> renderUsers.run());
        statusFilter.addActionListener(event -> renderUsers.run());
        roleFilter.addActionListener(event -> renderUsers.run());
        resetButton.addActionListener(event -> {
            keywordField.setText("");
            statusFilter.setSelectedIndex(0);
            roleFilter.setSelectedIndex(0);
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
        String currentPassword = promptCurrentPassword(parent);
        if (currentPassword == null) return;
        String verifiedPassword = currentPassword;
        resetButton.setEnabled(false);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return userService.resetPassword(manager, userId, verifiedPassword);
            }

            @Override
            protected void done() {
                if (manager != currentUser) {
                    return;
                }
                try {
                    String temporaryPassword = get();
                    showTemporaryPassword(parent, "密码已重置", username, temporaryPassword);
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
        String reason = JOptionPane.showInputDialog(parent, "请输入" + action + "账号的原因：", action + "原因",
            JOptionPane.QUESTION_MESSAGE);
        if (reason == null) {
            return;
        }
        String currentPassword = promptCurrentPassword(parent);
        if (currentPassword == null) return;
        int confirm = JOptionPane.showConfirmDialog(parent, "确定要" + action + "账号“" + username + "”吗？",
            action + "账号", JOptionPane.YES_NO_OPTION, status == 1 ? JOptionPane.QUESTION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        String verifiedPassword = currentPassword;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                userService.changeUserStatus(manager, userId, status, reason, verifiedPassword);
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
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    JOptionPane.showMessageDialog(parent, "账号状态更新失败：" + cause.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
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
                            centerArea.setText(message);
                            rightArea.setText("系统状态：自检未完成");
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

            JPanel page = createModulePage();
            page.add(AppTheme.pageHeader("连接池监控", "对比 READ/WRITE 连接池负载与 HikariCP 配置",
                statusLabel, refreshButton, simulateButton), BorderLayout.NORTH);
            JPanel body = new JPanel(new BorderLayout(0, 10));
            body.setBackground(AppTheme.PAGE);
            body.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 14, 12, 14));
            body.add(summaryCards, BorderLayout.NORTH);
            body.add(monitorSection, BorderLayout.CENTER);
            page.add(body, BorderLayout.CENTER);
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
                        AppTheme.toast(page, "上一次刷新尚未完成，请稍候。", false);
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
                            if (isSelectedModule("连接池监控")) {
                                centerArea.setText("连接池读写分离状态：\n" + summary);
                                rightArea.setText("SELECT 默认走 READ 池，写入、更新和事务默认走 WRITE 池。");
                            }
                        } catch (Exception ex) {
                            String message = "连接池刷新失败：" + rootMessage(ex);
                            statusLabel.setForeground(AppTheme.DANGER);
                            statusLabel.setText("刷新失败");
                            if (manual) {
                                AppTheme.toast(page, message, true);
                            }
                        }
                    }
                };
                refreshWorker[0].execute();
            };

            Timer timer = new Timer(1000, event -> refresh.accept(false));
            refreshButton.addActionListener(event -> refresh.accept(true));
            simulateButton.addActionListener(event -> simulationWorker[0] = simulateConnectionUsage(
                page, simulateButton, () -> refresh.accept(false)));
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

    private void showAssignmentRuleManager() {
        if (focusExistingModulePage("分配规则")) return;
        JPanel page = createModulePage();
        page.add(AppTheme.pageHeader("自动分配规则",
            "按分类和优先级匹配；越具体、排序值越小的规则越先执行"), BorderLayout.NORTH);
        DefaultTableModel model = new DefaultTableModel(
            new Object[]{"编号", "规则名称", "分类", "优先级", "策略", "目标管理员", "状态", "顺序"}, 0) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(model);
        AppTheme.styleTable(table);
        List<AssignmentRule> rules = new ArrayList<>();
        JLabel status = AppTheme.muted("正在加载规则…");
        JButton create = new JButton("新建规则");
        JButton toggle = new JButton("启用 / 停用");
        JButton refresh = new JButton("刷新");
        AppTheme.primary(create);
        AppTheme.secondary(toggle);
        AppTheme.secondary(refresh);
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setOpaque(false);
        toolbar.add(create);
        toolbar.add(toggle);
        toolbar.add(refresh);
        toolbar.add(status);
        JPanel body = AppTheme.surface(new BorderLayout(0, 10));
        body.add(toolbar, BorderLayout.NORTH);
        body.add(AppTheme.scroll(table), BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);

        Runnable reload = () -> {
            User actor = currentUser;
            refresh.setEnabled(false);
            status.setText("正在加载规则…");
            new SwingWorker<List<AssignmentRule>, Void>() {
                @Override protected List<AssignmentRule> doInBackground() { return assignmentRuleService.list(actor); }
                @Override protected void done() {
                    refresh.setEnabled(true);
                    if (actor != currentUser || !page.isDisplayable()) return;
                    try {
                        rules.clear();
                        rules.addAll(get());
                        model.setRowCount(0);
                        for (AssignmentRule rule : rules) {
                            model.addRow(new Object[]{rule.getRuleId(), rule.getRuleName(),
                                rule.getCategoryId() == null ? "全部分类" : rule.getCategoryName(),
                                rule.getPriority() == null ? "全部优先级" : rule.getPriority(),
                                "LEAST_LOADED".equals(rule.getStrategy()) ? "最少待办" : "指定管理员",
                                rule.getTargetAdminId() == null ? "自动选择" : rule.getTargetAdminName(),
                                rule.isEnabled() ? "已启用" : "已停用", rule.getSortOrder()});
                        }
                        status.setText("共 " + rules.size() + " 条规则");
                    } catch (Exception ex) {
                        status.setText("加载失败：" + rootMessage(ex));
                    }
                }
            }.execute();
        };
        refresh.addActionListener(event -> reload.run());
        create.addActionListener(event -> createAssignmentRule(page, reload));
        toggle.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0 || row >= rules.size()) {
                AppTheme.toast(page, "请先选择一条规则", true);
                return;
            }
            AssignmentRule selected = rules.get(row);
            User actor = currentUser;
            toggle.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    assignmentRuleService.setEnabled(actor, selected.getRuleId(), !selected.isEnabled());
                    return null;
                }
                @Override protected void done() {
                    toggle.setEnabled(true);
                    try { get(); reload.run(); }
                    catch (Exception ex) { AppTheme.toast(page, rootMessage(ex), true); }
                }
            }.execute();
        });
        registerModulePage("分配规则", page);
        reload.run();
    }

    private void createAssignmentRule(Component parent, Runnable reload) {
        User actor = currentUser;
        JTextField name = new JTextField("新建自动分配规则", 22);
        JComboBox<RuleCategoryOption> category = new JComboBox<>();
        category.addItem(new RuleCategoryOption(null, "全部分类"));
        Map<Long, String> displayNames = CategoryDisplayUtil.buildDisplayNames(
            categoryService.listAvailableCategories(actor));
        for (Category value : categoryService.listAvailableCategories(actor)) {
            category.addItem(new RuleCategoryOption(value.getCategoryId(),
                displayNames.getOrDefault(value.getCategoryId(), value.getName())));
        }
        JComboBox<String> priority = new JComboBox<>(new String[]{"全部优先级", "LOW", "MEDIUM", "HIGH", "URGENT"});
        JComboBox<StrategyOption> strategy = new JComboBox<>(new StrategyOption[]{
            new StrategyOption("LEAST_LOADED", "分配给待办最少的管理员"),
            new StrategyOption("SPECIFIC_ADMIN", "分配给指定管理员")});
        JComboBox<AdminOption> admin = new JComboBox<>(activeAdminOptions().toArray(new AdminOption[0]));
        JTextField sortOrder = new JTextField("100", 8);
        admin.setEnabled(false);
        strategy.addActionListener(event -> {
            StrategyOption selected = (StrategyOption) strategy.getSelectedItem();
            admin.setEnabled(selected != null && "SPECIFIC_ADMIN".equals(selected.code()));
        });
        for (JComponent component : new JComponent[]{name, sortOrder}) AppTheme.styleInput(component);
        AppTheme.styleComboBox(category);
        AppTheme.styleComboBox(priority);
        AppTheme.styleComboBox(strategy);
        AppTheme.styleComboBox(admin);
        JPanel form = new JPanel(new java.awt.GridLayout(0, 2, 10, 10));
        form.add(new JLabel("规则名称")); form.add(name);
        form.add(new JLabel("匹配分类")); form.add(category);
        form.add(new JLabel("匹配优先级")); form.add(priority);
        form.add(new JLabel("执行策略")); form.add(strategy);
        form.add(new JLabel("目标管理员")); form.add(admin);
        form.add(new JLabel("排序值")); form.add(sortOrder);
        if (JOptionPane.showConfirmDialog(parent, form, "新建自动分配规则",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        RuleCategoryOption categoryValue = (RuleCategoryOption) category.getSelectedItem();
        StrategyOption strategyValue = (StrategyOption) strategy.getSelectedItem();
        AdminOption adminValue = (AdminOption) admin.getSelectedItem();
        int order;
        try { order = Integer.parseInt(sortOrder.getText().trim()); }
        catch (Exception ex) { AppTheme.toast(parent, "排序值必须是整数", true); return; }
        Long targetAdmin = strategyValue != null && "SPECIFIC_ADMIN".equals(strategyValue.code())
            && adminValue != null ? adminValue.user().getUserId() : null;
        String selectedPriority = priority.getSelectedIndex() == 0 ? null : String.valueOf(priority.getSelectedItem());
        try {
            assignmentRuleService.create(actor, name.getText(),
                categoryValue == null ? null : categoryValue.categoryId(), selectedPriority,
                strategyValue == null ? null : strategyValue.code(), targetAdmin, order);
            reload.run();
        } catch (Exception ex) {
            AppTheme.toast(parent, rootMessage(ex), true);
        }
    }

    private SwingWorker<Void, Void> simulateConnectionUsage(Component parent, JButton simulateButton,
                                                             Runnable refresh) {
        User actor = currentUser;
        simulateButton.setEnabled(false);
        simulateButton.setText("模拟进行中…");
        AppTheme.toast(parent, "连接占用模拟已开始，将持续 8 秒。", false);
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
                    AppTheme.toast(parent, "模拟占用已结束，连接已全部归还。", false);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    AppTheme.toast(parent, rootMessage(ex), true);
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

    private record AdminOption(User user) {
        @Override
        public String toString() {
            return user.getUserId() + " - " + user.getUsername();
        }
    }

    private record RuleCategoryOption(Long categoryId, String label) {
        @Override public String toString() { return label; }
    }

    private record StrategyOption(String code, String label) {
        @Override public String toString() { return label; }
    }

    private record TicketStatusOption(int status, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private enum AssignmentFilterKind {
        ALL,
        UNASSIGNED,
        MINE,
        PENDING_MINE,
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
        page.add(AppTheme.pageHeader("批量维护", "仅处理当前账号负责且没有待确认转派的超时工单"), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setBackground(AppTheme.PAGE);
        body.setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 20, 20, 20));
        JPanel card = AppTheme.surface(new BorderLayout(0, 16));
        JLabel title = new JLabel("取消超时待处理工单");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 18f));
        JTextArea description = new JTextArea("范围：当前账号负责、无待确认转派\n时间范围：30 天前\n源状态：待处理\n目标状态：已取消");
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
            "将把当前账号负责、无待确认转派且 30 天前仍为待处理的工单批量流转为已取消，是否继续？",
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
            String message = "执行失败：" + rootMessage(ex);
            resultLabel.setForeground(AppTheme.DANGER);
            resultLabel.setText(message);
            rightArea.setText(message);
        }
    }
}
