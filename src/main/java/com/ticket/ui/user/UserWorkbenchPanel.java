package com.ticket.ui.user;

import com.ticket.dto.CrossTicketDTO;
import com.ticket.dto.CursorPageResult;
import com.ticket.dto.ItemDetailDTO;
import com.ticket.model.Category;
import com.ticket.model.Comment;
import com.ticket.model.ItemDetail;
import com.ticket.model.Profile;
import com.ticket.model.TicketHistory;
import com.ticket.model.User;
import com.ticket.service.BusinessService;
import com.ticket.service.CategoryService;
import com.ticket.service.CrossDatabaseQueryService;
import com.ticket.service.CrossDatabaseQueryService.UserWorkOverview;
import com.ticket.service.RecommendService;
import com.ticket.service.UserService;
import com.ticket.ui.MainFrame;
import com.ticket.ui.component.DonutChartPanel;
import com.ticket.ui.component.TextEntryDialog;
import com.ticket.ui.table.OrderTableModel;
import com.ticket.ui.theme.AppTheme;
import com.ticket.ui.theme.StatusTagRenderer;
import com.ticket.ui.theme.WindowIconUtil;
import com.ticket.util.CategoryDisplayUtil;
import com.ticket.util.TimeFormatUtil;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

public class UserWorkbenchPanel extends JPanel {
    private final MainFrame mainFrame;
    private final BusinessService businessService = new BusinessService();
    private final CrossDatabaseQueryService crossDatabaseQueryService = new CrossDatabaseQueryService();
    private final UserService userService = new UserService();
    private final RecommendService recommendService = new RecommendService();
    private final CategoryService categoryService = new CategoryService();
    private final Map<Long, String> categoryNameById = new HashMap<>();
    private final JLabel headerLabel = new JLabel("未登录");
    private final CardLayout workspaceLayout = new CardLayout();
    private final JPanel workspaceCards = new JPanel(workspaceLayout);
    private final Map<String, JToggleButton> workspaceNavigation = new java.util.LinkedHashMap<>();
    private String activeWorkspace = "home";
    private final JPanel homePanel = new JPanel(new BorderLayout(0, 14));
    private final JLabel homeGreetingLabel = new JLabel("服务概览");
    private final JLabel homeTotalLabel = new JLabel("—");
    private final JLabel homePendingLabel = new JLabel("—");
    private final JLabel homeProcessingLabel = new JLabel("—");
    private final JLabel homeCompletedLabel = new JLabel("—");
    private final JLabel homeUpdatedLabel = AppTheme.muted("尚未更新");
    private final JLabel homeRecentHint = AppTheme.muted("正在加载最近工单…");
    private final DonutChartPanel homeStatusChart = new DonutChartPanel();
    private final DefaultTableModel homeRecentModel = new DefaultTableModel(
        new Object[]{"工单编号", "标题", "状态", "优先级", "创建时间"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable homeRecentTable = new JTable(homeRecentModel);
    private final List<CrossTicketDTO> homeRecentTickets = new ArrayList<>();
    private SwingWorker<UserWorkOverview, Void> homeOverviewWorker;
    private final OrderTableModel tableModel = new OrderTableModel();
    private final JTable table = new JTable(tableModel);
    private final JTextField titleField = new JTextField(24);
    private final JComboBox<CategoryOption> categoryBox = new JComboBox<>();
    private final JTextField amountField = new JTextField(8);
    private final JComboBox<PriorityOption> priorityBox = new JComboBox<>();
    private final JTextArea descriptionArea = new JTextArea(8, 48);
    private final JButton createTicketButton = new JButton("提交工单");
    private final JComboBox<StatusOption> statusFilterBox = new JComboBox<>();
    private final JTextField keywordField = new JTextField(18);
    private final JLabel pageInfoLabel = new JLabel("第 1 页 / 共 0 条");
    private final JLabel ordersStatusLabel = AppTheme.muted("准备加载工单");
    private final JButton previousPageButton = new JButton("上一页");
    private final JButton nextPageButton = new JButton("下一页");
    private final JTextField realNameField = new JTextField(20);
    private final JTextField emailField = new JTextField(20);
    private final JTextField phoneField = new JTextField(14);
    private final JTextField idCardField = new JTextField(20);
    private final JTextField addressField = new JTextField(20);
    private final JComboBox<String> preferredContactBox = new JComboBox<>(
        new String[] {"手机号", "邮箱", "站内消息"});
    private final JComboBox<String> notificationBox = new JComboBox<>(
        new String[] {"所有回复", "状态变更", "仅处理结果", "不通知"});
    private final JTextArea notesArea = new JTextArea(4, 20);
    private final JButton realNameAuthButton = new JButton("实名认证");
    private final JButton saveProfileButton = new JButton("保存资料");
    private JScrollPane descriptionScrollPane;
    private Border titleNormalBorder;
    private Border categoryNormalBorder;
    private Border descriptionNormalBorder;
    private int currentPage = 1;
    private final int pageSize = 20;
    private long currentTotal = 0;
    private final Map<Integer, OrderCursor> pageCursors = new HashMap<>();
    private User currentUser;
    private long sessionVersion;
    private long orderRequestVersion;

    public UserWorkbenchPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());
        setBackground(AppTheme.PAGE);
        initOptionModels();
        configureFormControls();
        JButton refreshButton = new JButton("刷新当前页");
        JButton changePasswordButton = new JButton("修改密码");
        JButton logoutButton = new JButton("退出登录");
        AppTheme.secondary(refreshButton);
        AppTheme.secondary(changePasswordButton);
        AppTheme.secondary(logoutButton);
        headerLabel.setForeground(AppTheme.MUTED);
        add(AppTheme.pageHeader("工单中心", "提交、跟进和评价你的服务请求",
            headerLabel, refreshButton, changePasswordButton, logoutButton), BorderLayout.NORTH);

        configureTicketTable();
        add(buildTabContent(), BorderLayout.CENTER);

        refreshButton.addActionListener(event -> refreshActiveWorkspace());
        changePasswordButton.addActionListener(event -> {
            if (currentUser != null) {
                mainFrame.showPasswordChange(currentUser, false);
            }
        });
        previousPageButton.addActionListener(event -> {
            if (currentPage > 1) {
                currentPage--;
                loadOrders();
            }
        });
        nextPageButton.addActionListener(event -> {
            if (currentPage < totalPages()) {
                currentPage++;
                loadOrders();
            }
        });
        logoutButton.addActionListener(event -> mainFrame.logout());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) {
                    showSelectedTicketDetail();
                }
            }
        });
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                    event.consume();
                    showSelectedTicketDetail();
                }
            }
        });
    }

    private JPanel buildTabContent() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(AppTheme.PAGE);
        workspaceCards.setBackground(AppTheme.PAGE);
        workspaceCards.setBorder(BorderFactory.createEmptyBorder(16, 18, 18, 18));
        buildHomePanel();
        workspaceCards.add(homePanel, "home");
        workspaceCards.add(buildMyTicketsPanel(), "orders");
        workspaceCards.add(buildCreateTicketPanel(), "create");
        workspaceCards.add(buildProfilePanel(), "profile");

        JPanel navigation = new JPanel();
        navigation.setLayout(new javax.swing.BoxLayout(navigation, javax.swing.BoxLayout.Y_AXIS));
        navigation.setBackground(new Color(248, 249, 250));
        navigation.setPreferredSize(new Dimension(205, 0));
        navigation.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, AppTheme.BORDER),
            BorderFactory.createEmptyBorder(18, 12, 14, 12)));
        JLabel sectionLabel = new JLabel("工单服务");
        sectionLabel.setForeground(AppTheme.MUTED);
        sectionLabel.setFont(sectionLabel.getFont().deriveFont(java.awt.Font.BOLD, 12f));
        sectionLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 0));
        sectionLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        navigation.add(sectionLabel);
        ButtonGroup group = new ButtonGroup();
        JToggleButton homeButton = new JToggleButton("服务概览");
        JToggleButton ordersButton = new JToggleButton("我的工单");
        JToggleButton createButton = new JToggleButton("创建工单");
        JToggleButton profileButton = new JToggleButton("联系信息");
        for (JToggleButton button : new JToggleButton[]{homeButton, ordersButton, createButton, profileButton}) {
            styleSideNavigationButton(button);
            group.add(button);
            navigation.add(button);
            navigation.add(javax.swing.Box.createVerticalStrut(4));
        }
        workspaceNavigation.put("home", homeButton);
        workspaceNavigation.put("orders", ordersButton);
        workspaceNavigation.put("create", createButton);
        workspaceNavigation.put("profile", profileButton);
        navigation.add(javax.swing.Box.createVerticalGlue());
        homeButton.setSelected(true);
        homeButton.addActionListener(event -> {
            showWorkspace("home");
            loadHomeOverview();
        });
        ordersButton.addActionListener(event -> showWorkspace("orders"));
        createButton.addActionListener(event -> showWorkspace("create"));
        profileButton.addActionListener(event -> showWorkspace("profile"));
        panel.add(navigation, BorderLayout.WEST);
        panel.add(workspaceCards, BorderLayout.CENTER);
        return panel;
    }

    private void showWorkspace(String workspace) {
        String target = workspaceNavigation.containsKey(workspace) ? workspace : "home";
        activeWorkspace = target;
        workspaceLayout.show(workspaceCards, target);
        JToggleButton navigationButton = workspaceNavigation.get(target);
        if (navigationButton != null) {
            navigationButton.setSelected(true);
        }
    }

    private void refreshActiveWorkspace() {
        switch (activeWorkspace) {
            case "home" -> loadHomeOverview();
            case "profile" -> loadProfile();
            case "create" -> loadCategories();
            default -> loadOrders();
        }
    }

    private void styleSideNavigationButton(JToggleButton button) {
        button.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
        button.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        button.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.getModel().addChangeListener(event -> {
            boolean selected = button.isSelected();
            button.setBackground(selected ? new Color(232, 243, 255) : new Color(248, 249, 250));
            button.setForeground(selected ? AppTheme.PRIMARY_DARK : AppTheme.TEXT);
            button.setFont(button.getFont().deriveFont(selected ? java.awt.Font.BOLD : java.awt.Font.PLAIN));
        });
    }

    private void buildHomePanel() {
        homePanel.setBackground(AppTheme.PAGE);

        JPanel welcome = AppTheme.surface(new BorderLayout(16, 0));
        JPanel welcomeText = new JPanel(new GridLayout(0, 1, 0, 5));
        welcomeText.setOpaque(false);
        homeGreetingLabel.setFont(homeGreetingLabel.getFont().deriveFont(java.awt.Font.BOLD, 25f));
        welcomeText.add(homeGreetingLabel);
        welcome.add(welcomeText, BorderLayout.WEST);
        homeUpdatedLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        welcome.add(homeUpdatedLabel, BorderLayout.EAST);
        homePanel.add(welcome, BorderLayout.NORTH);

        JPanel metrics = new JPanel(new GridLayout(1, 4, 14, 0));
        metrics.setOpaque(false);
        metrics.add(createHomeMetricCard("全部工单", homeTotalLabel));
        metrics.add(createHomeMetricCard("待处理", homePendingLabel));
        metrics.add(createHomeMetricCard("处理中", homeProcessingLabel));
        metrics.add(createHomeMetricCard("已完成", homeCompletedLabel));

        homeStatusChart.setCenterTitle("我的工单");
        homeStatusChart.setSelectionListener(this::openOrders);
        JPanel chartCard = AppTheme.surface(new BorderLayout(0, 8));
        JLabel chartTitle = new JLabel("工单状态分布");
        chartTitle.setFont(chartTitle.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        chartCard.add(chartTitle, BorderLayout.NORTH);
        chartCard.add(homeStatusChart, BorderLayout.CENTER);
        chartCard.setMinimumSize(new Dimension(320, 0));

        AppTheme.styleTable(homeRecentTable);
        homeRecentTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        homeRecentTable.getColumnModel().getColumn(0).setMaxWidth(110);
        homeRecentTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        homeRecentTable.getColumnModel().getColumn(2).setMaxWidth(100);
        homeRecentTable.getColumnModel().getColumn(3).setPreferredWidth(75);
        homeRecentTable.getColumnModel().getColumn(3).setMaxWidth(90);
        homeRecentTable.getColumnModel().getColumn(4).setPreferredWidth(150);
        homeRecentTable.getColumnModel().getColumn(2).setCellRenderer(
            new StatusTagRenderer(StatusTagRenderer.Kind.STATUS));
        homeRecentTable.getColumnModel().getColumn(3).setCellRenderer(
            new StatusTagRenderer(StatusTagRenderer.Kind.PRIORITY));
        homeRecentTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() >= 2) {
                    showHomeRecentTicket(homeRecentTable.getSelectedRow());
                }
            }
        });
        JPanel recentCard = AppTheme.surface(new BorderLayout(0, 8));
        JLabel recentTitle = new JLabel("最近工单");
        recentTitle.setFont(recentTitle.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        recentCard.add(recentTitle, BorderLayout.NORTH);
        recentCard.add(AppTheme.scroll(homeRecentTable), BorderLayout.CENTER);
        recentCard.add(homeRecentHint, BorderLayout.SOUTH);

        JPanel quickActions = new JPanel(new GridLayout(1, 4, 8, 0));
        quickActions.setOpaque(false);
        quickActions.add(createHomeQuickButton("创建新工单", () -> showWorkspace("create"), true));
        quickActions.add(createHomeQuickButton("查看处理中", () -> openOrders(1), false));
        quickActions.add(createHomeQuickButton("全部工单", () -> openOrders(null), false));
        quickActions.add(createHomeQuickButton("联系信息", () -> showWorkspace("profile"), false));
        JPanel quickCard = AppTheme.surface(new BorderLayout(0, 8));
        JLabel quickTitle = new JLabel("快捷操作");
        quickTitle.setFont(quickTitle.getFont().deriveFont(java.awt.Font.BOLD, 15f));
        quickCard.add(quickTitle, BorderLayout.NORTH);
        quickCard.add(quickActions, BorderLayout.CENTER);

        JPanel rightColumn = new JPanel(new BorderLayout(0, 12));
        rightColumn.setOpaque(false);
        rightColumn.add(recentCard, BorderLayout.CENTER);
        rightColumn.add(quickCard, BorderLayout.SOUTH);
        rightColumn.setMinimumSize(new Dimension(430, 0));

        JSplitPane dashboard = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chartCard, rightColumn);
        dashboard.setResizeWeight(0.38);
        dashboard.setDividerSize(8);
        dashboard.setBorder(null);
        dashboard.setOpaque(false);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);
        content.add(metrics, BorderLayout.NORTH);
        content.add(dashboard, BorderLayout.CENTER);
        homePanel.add(content, BorderLayout.CENTER);
    }

    private JPanel createHomeMetricCard(String title, JLabel valueLabel) {
        JPanel card = AppTheme.surface(new BorderLayout(0, 8));
        valueLabel.setFont(valueLabel.getFont().deriveFont(java.awt.Font.BOLD, 28f));
        valueLabel.setForeground(AppTheme.TEXT);
        card.add(AppTheme.muted(title), BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JButton createHomeQuickButton(String text, Runnable action, boolean primary) {
        JButton button = new JButton(text);
        if (primary) {
            AppTheme.primary(button);
        } else {
            AppTheme.secondary(button);
        }
        button.addActionListener(event -> action.run());
        return button;
    }

    private void loadHomeOverview() {
        if (currentUser == null) {
            return;
        }
        User actor = currentUser;
        long expectedSession = sessionVersion;
        homeGreetingLabel.setText("你好，" + actor.getUsername());
        homeTotalLabel.setText("—");
        homePendingLabel.setText("—");
        homeProcessingLabel.setText("—");
        homeCompletedLabel.setText("—");
        homeUpdatedLabel.setText("更新中…");
        homeRecentHint.setText("正在加载最近工单…");
        if (homeOverviewWorker != null && !homeOverviewWorker.isDone()) {
            homeOverviewWorker.cancel(true);
        }
        homeOverviewWorker = new SwingWorker<>() {
            @Override
            protected UserWorkOverview doInBackground() {
                return crossDatabaseQueryService.userWorkOverview(actor, 6);
            }

            @Override
            protected void done() {
                if (isCancelled() || !isCurrentSession(actor, expectedSession)) {
                    return;
                }
                try {
                    updateHomeOverview(get());
                } catch (Exception ex) {
                    homeUpdatedLabel.setText("更新失败");
                    homeRecentHint.setText("概览加载失败，请点击顶部刷新重试。");
                }
            }
        };
        homeOverviewWorker.execute();
    }

    private void updateHomeOverview(UserWorkOverview overview) {
        Map<Integer, Long> counts = overview.statusCounts();
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        homeTotalLabel.setText(String.valueOf(total));
        homePendingLabel.setText(String.valueOf(counts.getOrDefault(0, 0L)));
        homeProcessingLabel.setText(String.valueOf(counts.getOrDefault(1, 0L)));
        homeCompletedLabel.setText(String.valueOf(counts.getOrDefault(2, 0L)));
        homeStatusChart.setSegments(List.of(
            new DonutChartPanel.Segment("待处理", counts.getOrDefault(0, 0L), AppTheme.WARNING, 0),
            new DonutChartPanel.Segment("处理中", counts.getOrDefault(1, 0L), AppTheme.PRIMARY, 1),
            new DonutChartPanel.Segment("已完成", counts.getOrDefault(2, 0L), AppTheme.SUCCESS, 2),
            new DonutChartPanel.Segment("已关闭", counts.getOrDefault(3, 0L), new Color(107, 114, 128), 3),
            new DonutChartPanel.Segment("已取消", counts.getOrDefault(4, 0L), new Color(190, 75, 75), 4)
        ));

        homeRecentTickets.clear();
        homeRecentTickets.addAll(overview.recentTickets());
        homeRecentModel.setRowCount(0);
        for (CrossTicketDTO ticket : homeRecentTickets) {
            ItemDetail.Metadata metadata = ticket.getItemDetail() == null ? null : ticket.getItemDetail().getMetadata();
            homeRecentModel.addRow(new Object[]{
                ticket.getItem() == null ? "" : ticket.getItem().getItemId(),
                ticket.getItem() == null ? "" : ticket.getItem().getTitle(),
                ticket.getOrder() == null ? "" : statusText(ticket.getOrder().getStatus()),
                priorityText(metadata == null ? null : metadata.getPriority()),
                ticket.getOrder() == null ? "—" : TimeFormatUtil.format(ticket.getOrder().getCreatedAt())
            });
        }
        homeRecentHint.setText(homeRecentTickets.isEmpty()
            ? "还没有工单，可以从下方快捷入口创建第一张工单。" : "双击可查看工单详情");
        homeUpdatedLabel.setText("更新于 " + java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    private String priorityText(String priority) {
        return switch (priority == null ? "" : priority) {
            case "URGENT" -> "紧急";
            case "HIGH" -> "高";
            case "MEDIUM" -> "中";
            case "LOW" -> "低";
            default -> priority == null ? "" : priority;
        };
    }

    private void showHomeRecentTicket(int selectedRow) {
        if (selectedRow < 0 || selectedRow >= homeRecentTickets.size()) {
            return;
        }
        CrossTicketDTO ticket = homeRecentTickets.get(selectedRow);
        if (ticket.getItem() != null && ticket.getItem().getItemId() != null) {
            showTicketDetailDialog(ticket.getItem().getItemId());
        }
    }

    private void openOrders(Integer status) {
        keywordField.setText("");
        int targetIndex = 0;
        for (int index = 0; index < statusFilterBox.getItemCount(); index++) {
            StatusOption option = statusFilterBox.getItemAt(index);
            if (java.util.Objects.equals(option.status(), status)) {
                targetIndex = index;
                break;
            }
        }
        resetPagination();
        showWorkspace("orders");
        if (statusFilterBox.getSelectedIndex() == targetIndex) {
            loadOrders();
        } else {
            statusFilterBox.setSelectedIndex(targetIndex);
        }
    }

    private void configureFormControls() {
        AppTheme.styleComboBox(statusFilterBox);
        AppTheme.styleComboBox(categoryBox);
        AppTheme.styleComboBox(priorityBox);
        AppTheme.styleComboBox(preferredContactBox);
        AppTheme.styleComboBox(notificationBox);
        for (JComponent component : new JComponent[]{titleField, amountField, realNameField, emailField,
            phoneField, idCardField, addressField, keywordField}) {
            AppTheme.styleInput(component);
        }
    }

    public void bindUser(User user) {
        sessionVersion++;
        this.currentUser = user;
        resetPagination();
        headerLabel.setText("当前用户：" + user.getUsername());
        showWorkspace("home");
        loadCategories();
        loadOrders();
        loadProfile();
        loadHomeOverview();
    }

    /** 退出时清理当前用户视图，避免下一位用户看到上一会话的缓存数据。 */
    public void clearSession() {
        sessionVersion++;
        orderRequestVersion++;
        if (homeOverviewWorker != null) {
            homeOverviewWorker.cancel(true);
        }
        currentUser = null;
        resetPagination();
        currentTotal = 0;
        tableModel.setTickets(List.of());
        tableModel.setCategoryDisplayNames(Map.of());
        categoryBox.removeAllItems();
        categoryNameById.clear();
        titleField.setText("");
        amountField.setText("");
        descriptionArea.setText("");
        clearTicketValidation();
        createTicketButton.setEnabled(true);
        createTicketButton.setText("提交工单");
        keywordField.setText("");
        statusFilterBox.setSelectedIndex(0);
        headerLabel.setText("未登录");
        homeGreetingLabel.setText("服务概览");
        homeTotalLabel.setText("—");
        homePendingLabel.setText("—");
        homeProcessingLabel.setText("—");
        homeCompletedLabel.setText("—");
        homeUpdatedLabel.setText("尚未更新");
        homeRecentHint.setText("登录后可查看服务概览");
        homeRecentTickets.clear();
        homeRecentModel.setRowCount(0);
        homeStatusChart.setSegments(List.of());
        saveProfileButton.setEnabled(true);
        ordersStatusLabel.setForeground(AppTheme.MUTED);
        ordersStatusLabel.setText("登录后可查看工单");
        updatePageControls();
    }

    private void initOptionModels() {
        statusFilterBox.addItem(new StatusOption(null, "全部状态"));
        statusFilterBox.addItem(new StatusOption(0, "待处理"));
        statusFilterBox.addItem(new StatusOption(1, "处理中"));
        statusFilterBox.addItem(new StatusOption(2, "已完成"));
        statusFilterBox.addItem(new StatusOption(3, "已关闭"));
        statusFilterBox.addItem(new StatusOption(4, "已取消"));

        priorityBox.addItem(new PriorityOption("LOW", "低"));
        priorityBox.addItem(new PriorityOption("MEDIUM", "中"));
        priorityBox.addItem(new PriorityOption("HIGH", "高"));
        priorityBox.addItem(new PriorityOption("URGENT", "紧急"));
        priorityBox.setSelectedIndex(1);
    }

    private JPanel buildMyTicketsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(AppTheme.PAGE);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filters.setOpaque(false);
        JButton searchButton = new JButton("搜索");
        JButton resetButton = new JButton("重置");
        AppTheme.primary(searchButton);
        AppTheme.secondary(resetButton);
        filters.add(new JLabel("状态"));
        filters.add(statusFilterBox);
        filters.add(new JLabel("关键词"));
        filters.add(keywordField);
        filters.add(searchButton);
        filters.add(resetButton);
        filters.add(previousPageButton);
        filters.add(nextPageButton);
        filters.add(pageInfoLabel);
        AppTheme.secondary(previousPageButton);
        AppTheme.secondary(nextPageButton);
        pageInfoLabel.setForeground(AppTheme.MUTED);
        JPanel filterCard = AppTheme.surface(new BorderLayout());
        filterCard.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AppTheme.BORDER),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        JPanel titleLine = new JPanel(new BorderLayout());
        titleLine.setOpaque(false);
        JLabel title = new JLabel("我的工单");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 17f));
        titleLine.add(title, BorderLayout.WEST);
        filterCard.add(titleLine, BorderLayout.NORTH);
        filterCard.add(filters, BorderLayout.CENTER);
        panel.add(filterCard, BorderLayout.NORTH);
        JScrollPane tablePane = AppTheme.scroll(table);
        tablePane.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0),
            BorderFactory.createLineBorder(AppTheme.BORDER)));
        JPanel listBody = new JPanel(new BorderLayout());
        listBody.setOpaque(false);
        listBody.add(tablePane, BorderLayout.CENTER);
        ordersStatusLabel.setBorder(BorderFactory.createEmptyBorder(8, 4, 2, 4));
        listBody.add(ordersStatusLabel, BorderLayout.SOUTH);
        panel.add(listBody, BorderLayout.CENTER);
        searchButton.addActionListener(event -> {
            resetPagination();
            loadOrders();
        });
        resetButton.addActionListener(event -> {
            statusFilterBox.setSelectedIndex(0);
            keywordField.setText("");
            resetPagination();
            loadOrders();
        });
        keywordField.addActionListener(event -> {
            resetPagination();
            loadOrders();
        });
        statusFilterBox.addActionListener(event -> {
            if (currentUser != null) {
                resetPagination();
                loadOrders();
            }
        });
        updatePageControls();
        return panel;
    }

    private JPanel buildCreateTicketPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(AppTheme.PAGE);
        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(AppTheme.PAGE);
        content.setBorder(BorderFactory.createEmptyBorder(12, 2, 2, 2));
        GridBagConstraints outer = new GridBagConstraints();
        outer.gridx = 0;
        outer.gridy = 0;
        outer.weightx = 1;
        outer.weighty = 1;
        outer.fill = GridBagConstraints.BOTH;
        outer.insets = new Insets(0, 0, 0, 0);
        JPanel form = AppTheme.surface(new GridBagLayout());
        JButton recommendButton = new JButton("推荐分类");
        AppTheme.primary(createTicketButton);
        AppTheme.secondary(recommendButton);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(7, 4, 7, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel title = new JLabel("创建新工单");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 18f));
        form.add(title, gbc);
        gbc.gridwidth = 1;
        addTicketFormRow(form, gbc, 1, "标题 *", titleField);
        addTicketFormRow(form, gbc, 2, "分类 *", categoryBox);
        addTicketFormRow(form, gbc, 3, "金额", amountField);
        addTicketFormRow(form, gbc, 4, "优先级", priorityBox);
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel("问题描述 *"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionScrollPane = AppTheme.scroll(descriptionArea);
        descriptionScrollPane.setPreferredSize(new Dimension(420, 210));
        form.add(descriptionScrollPane, gbc);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(recommendButton);
        actions.add(createTicketButton);
        gbc.gridx = 1;
        gbc.gridy = 6;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.EAST;
        form.add(actions, gbc);
        content.add(form, outer);
        panel.add(content, BorderLayout.CENTER);

        titleNormalBorder = titleField.getBorder();
        categoryNormalBorder = categoryBox.getBorder();
        descriptionNormalBorder = descriptionScrollPane.getBorder();
        installTicketValidationListeners();

        createTicketButton.addActionListener(event -> {
            try {
                if (!validateTicketForm()) {
                    return;
                }
                User actor = currentUser;
                long expectedSession = sessionVersion;
                CategoryOption categoryOption = (CategoryOption) categoryBox.getSelectedItem();
                PriorityOption priorityOption = (PriorityOption) priorityBox.getSelectedItem();
                String ticketTitle = titleField.getText();
                BigDecimal amount = new BigDecimal(amountField.getText().isBlank() ? "0.00" : amountField.getText());
                String description = descriptionArea.getText();
                String priority = priorityOption == null ? "MEDIUM" : priorityOption.value();
                createTicketButton.setEnabled(false);
                createTicketButton.setText("提交中…");
                new SwingWorker<Long, Void>() {
                    @Override
                    protected Long doInBackground() {
                        return businessService.createTicket(actor, ticketTitle, categoryOption.categoryId(),
                            amount, description, priority);
                    }

                    @Override
                    protected void done() {
                        if (!isCurrentSession(actor, expectedSession)) {
                            return;
                        }
                        try {
                            long itemId = get();
                            AppTheme.toast(UserWorkbenchPanel.this, "工单提交成功，编号：" + itemId, false);
                            titleField.setText("");
                            amountField.setText("");
                            descriptionArea.setText("");
                            resetPagination();
                            loadOrders();
                            loadHomeOverview();
                        } catch (Exception ex) {
                            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                            AppTheme.toast(UserWorkbenchPanel.this,
                                cause.getMessage() == null ? "工单提交失败，请稍后重试" : cause.getMessage(), true);
                        } finally {
                            createTicketButton.setEnabled(true);
                            createTicketButton.setText("提交工单");
                        }
                    }
                }.execute();
            } catch (Exception ex) {
                String message = ex instanceof NumberFormatException ? "金额格式不正确，请输入有效数字" : ex.getMessage();
                AppTheme.toast(this, message == null ? "请检查填写内容" : message, true);
            }
        });
        titleField.addActionListener(event -> createTicketButton.doClick());
        amountField.addActionListener(event -> createTicketButton.doClick());
        AppTheme.submitOnEnter(descriptionArea, createTicketButton::doClick);

        recommendButton.addActionListener(event -> {
            User actor = currentUser;
            long expectedSession = sessionVersion;
            new SwingWorker<List<Category>, Void>() {
                @Override
                protected List<Category> doInBackground() {
                    return recommendService.recommendCategories(actor);
                }

                @Override
                protected void done() {
                    if (!isCurrentSession(actor, expectedSession)) {
                        return;
                    }
                    try {
                        showRecommendations(get());
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(UserWorkbenchPanel.this, "加载推荐分类失败", "提示", JOptionPane.WARNING_MESSAGE);
                    }
                }
            }.execute();
        });
        return panel;
    }

    private boolean validateTicketForm() {
        CategoryOption category = (CategoryOption) categoryBox.getSelectedItem();
        boolean titleMissing = titleField.getText() == null || titleField.getText().isBlank();
        boolean categoryMissing = category == null || category.categoryId() == null;
        boolean descriptionMissing = descriptionArea.getText() == null || descriptionArea.getText().isBlank();
        setValidationBorder(titleField, titleNormalBorder, titleMissing);
        setValidationBorder(categoryBox, categoryNormalBorder, categoryMissing);
        setValidationBorder(descriptionScrollPane, descriptionNormalBorder, descriptionMissing);
        if (!titleMissing && !categoryMissing && !descriptionMissing) {
            return true;
        }
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (titleMissing) {
            missing.add("标题");
        }
        if (categoryMissing) {
            missing.add("分类");
        }
        if (descriptionMissing) {
            missing.add("问题描述");
        }
        AppTheme.toast(this, "请完成必填项：" + String.join("、", missing), true);
        if (titleMissing) {
            titleField.requestFocusInWindow();
        } else if (categoryMissing) {
            categoryBox.requestFocusInWindow();
        } else {
            descriptionArea.requestFocusInWindow();
        }
        return false;
    }

    private void setValidationBorder(JComponent component, Border normalBorder, boolean invalid) {
        if (component == null || normalBorder == null) {
            return;
        }
        component.setBorder(invalid
            ? BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AppTheme.DANGER, 2), normalBorder)
            : normalBorder);
    }

    private void installTicketValidationListeners() {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                clearCompletedValidation();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                clearCompletedValidation();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                clearCompletedValidation();
            }
        };
        titleField.getDocument().addDocumentListener(listener);
        descriptionArea.getDocument().addDocumentListener(listener);
        categoryBox.addActionListener(event -> {
            CategoryOption category = (CategoryOption) categoryBox.getSelectedItem();
            if (category != null && category.categoryId() != null) {
                setValidationBorder(categoryBox, categoryNormalBorder, false);
            }
        });
    }

    private void clearCompletedValidation() {
        if (!titleField.getText().isBlank()) {
            setValidationBorder(titleField, titleNormalBorder, false);
        }
        if (!descriptionArea.getText().isBlank()) {
            setValidationBorder(descriptionScrollPane, descriptionNormalBorder, false);
        }
    }

    private void clearTicketValidation() {
        setValidationBorder(titleField, titleNormalBorder, false);
        setValidationBorder(categoryBox, categoryNormalBorder, false);
        setValidationBorder(descriptionScrollPane, descriptionNormalBorder, false);
    }

    private void addTicketFormRow(JPanel form, GridBagConstraints gbc, int row, String label, JComponent component) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(component, gbc);
    }

    private void configureTicketTable() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        AppTheme.styleTable(table);
        table.getColumnModel().getColumn(3).setCellRenderer(new StatusTagRenderer(StatusTagRenderer.Kind.PRIORITY));
        table.getColumnModel().getColumn(4).setCellRenderer(new StatusTagRenderer(StatusTagRenderer.Kind.STATUS));
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(6).setPreferredWidth(190);
        table.getColumnModel().getColumn(7).setPreferredWidth(190);
    }

    private JPanel buildProfilePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        AppTheme.primary(saveProfileButton);

        JPanel blocks = new JPanel(new GridLayout(2, 2, 12, 12));
        blocks.setBorder(BorderFactory.createEmptyBorder(14, 14, 10, 14));

        JPanel contactBlock = buildProfileBlock("基础联系");
        addFormRow(contactBlock, 0, "姓名/称呼", realNameField);
        addFormRow(contactBlock, 1, "邮箱", emailField);
        addFormRow(contactBlock, 2, "手机号", phoneField);

        JPanel preferenceBlock = buildProfileBlock("联系偏好");
        addFormRow(preferenceBlock, 0, "首选联系方式", preferredContactBox);
        addFormRow(preferenceBlock, 1, "通知偏好", notificationBox);
        addFormRow(preferenceBlock, 2, "服务地址", addressField);

        JPanel verificationBlock = buildProfileBlock("实名认证");
        addFormRow(verificationBlock, 0, "认证状态", realNameAuthButton);

        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        JPanel notesBlock = buildProfileBlock("补充说明");
        JScrollPane notesScrollPane = new JScrollPane(notesArea);
        notesScrollPane.setPreferredSize(new Dimension(320, 110));
        GridBagConstraints noteConstraints = new GridBagConstraints();
        noteConstraints.gridx = 0;
        noteConstraints.gridy = 0;
        noteConstraints.weightx = 1;
        noteConstraints.weighty = 1;
        noteConstraints.fill = GridBagConstraints.BOTH;
        noteConstraints.insets = new Insets(6, 8, 8, 8);
        notesBlock.add(notesScrollPane, noteConstraints);

        blocks.add(contactBlock);
        blocks.add(preferenceBlock);
        blocks.add(verificationBlock);
        blocks.add(notesBlock);
        panel.add(blocks, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 14, 12, 14));
        actions.add(saveProfileButton);
        panel.add(actions, BorderLayout.SOUTH);

        saveProfileButton.addActionListener(event -> saveProfile());
        realNameField.addActionListener(event -> saveProfileButton.doClick());
        emailField.addActionListener(event -> saveProfileButton.doClick());
        phoneField.addActionListener(event -> saveProfileButton.doClick());
        addressField.addActionListener(event -> saveProfileButton.doClick());
        AppTheme.submitOnEnter(notesArea, saveProfileButton::doClick);
        realNameAuthButton.addActionListener(event -> showRealNameAuthDialog());
        return panel;
    }

    private JPanel buildProfileBlock(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
    }

    private void addFormRow(JPanel panel, int row, String label, JComponent component) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(7, 8, 7, 8);
        panel.add(new JLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(7, 0, 7, 8);
        panel.add(component, fieldConstraints);
    }

    private JComponent scrollableHeader(JPanel panel) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        wrapper.add(panel, BorderLayout.WEST);
        return wrapper;
    }

    private void showRecommendations(List<Category> recommendations) {
        JTextArea textArea = new JTextArea(formatRecommendations(recommendations), 12, 52);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(520, 280));
        JOptionPane.showMessageDialog(this, scrollPane, "推荐分类", JOptionPane.INFORMATION_MESSAGE);
    }

    private String formatRecommendations(List<Category> recommendations) {
        if (recommendations.isEmpty()) {
            return "暂无推荐";
        }
        StringBuilder builder = new StringBuilder("推荐分类（按最近提交工单的分类排序）：\n");
        for (int index = 0; index < recommendations.size(); index++) {
            Category recommendation = recommendations.get(index);
            builder.append(index + 1)
                .append(". ")
                .append(categoryNameById.getOrDefault(recommendation.getCategoryId(), recommendation.getName()))
                .append("（ID ")
                .append(recommendation.getCategoryId())
                .append("）")
                .append("\n\n");
        }
        return builder.toString().trim();
    }

    private void loadCategories() {
        User actor = currentUser;
        long expectedSession = sessionVersion;
        categoryBox.removeAllItems();
        categoryNameById.clear();
        tableModel.setCategoryDisplayNames(Map.of());
        new SwingWorker<List<Category>, Void>() {
            @Override
            protected List<Category> doInBackground() {
                return categoryService.listAvailableCategories(actor);
            }

            @Override
            protected void done() {
                if (!isCurrentSession(actor, expectedSession)) {
                    return;
                }
                try {
                    List<Category> categories = get();
                    Map<Long, String> displayNames = CategoryDisplayUtil.buildDisplayNames(categories);
                    tableModel.setCategoryDisplayNames(displayNames);
                    for (Category category : categories) {
                        String displayName = displayNames.get(category.getCategoryId());
                        categoryNameById.put(category.getCategoryId(), displayName);
                        if (!displayName.startsWith("层级异常｜")) {
                            categoryBox.addItem(new CategoryOption(category.getCategoryId(), displayName));
                        }
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(UserWorkbenchPanel.this, "加载分类失败", "提示", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void loadOrders() {
        if (currentUser == null) {
            return;
        }
        ordersStatusLabel.setForeground(AppTheme.PRIMARY);
        ordersStatusLabel.setText("工单列表加载中…");
        previousPageButton.setEnabled(false);
        nextPageButton.setEnabled(false);
        StatusOption statusOption = (StatusOption) statusFilterBox.getSelectedItem();
        Integer status = statusOption == null ? null : statusOption.status();
        String keyword = keywordField.getText();
        User actor = currentUser;
        long expectedSession = sessionVersion;
        long requestVersion = ++orderRequestVersion;
        int requestedPage = currentPage;
        OrderCursor cursor = pageCursors.get(requestedPage);
        new SwingWorker<CursorPageResult<CrossTicketDTO>, Void>() {
            @Override
            protected CursorPageResult<CrossTicketDTO> doInBackground() {
                return crossDatabaseQueryService.pageMyTicketsAfter(actor, status, keyword,
                    cursor == null ? null : cursor.createdAt(), cursor == null ? null : cursor.orderId(), pageSize);
            }

            @Override
            protected void done() {
                if (!isCurrentSession(actor, expectedSession) || requestVersion != orderRequestVersion) {
                    return;
                }
                try {
                    CursorPageResult<CrossTicketDTO> result = get();
                    currentTotal = result.getTotal();
                    tableModel.setTickets(result.getRecords());
                    if (result.hasNext()) {
                        pageCursors.put(requestedPage + 1, new OrderCursor(result.getNextCreatedAt(), result.getNextOrderId()));
                    } else {
                        pageCursors.remove(requestedPage + 1);
                    }
                    updatePageControls();
                    ordersStatusLabel.setForeground(AppTheme.MUTED);
                    ordersStatusLabel.setText(result.getRecords().isEmpty()
                        ? "没有符合当前条件的工单，可调整筛选条件或创建新工单。"
                        : "已加载 " + result.getRecords().size() + " 条，本次结果共 " + currentTotal + " 条");
                } catch (Exception ex) {
                    ordersStatusLabel.setForeground(AppTheme.DANGER);
                    ordersStatusLabel.setText("工单列表加载失败，请检查数据库连接后重试。");
                    updatePageControls();
                }
            }
        }.execute();
    }

    private boolean isCurrentSession(User actor, long expectedSession) {
        return actor != null && currentUser == actor && sessionVersion == expectedSession;
    }

    private void updatePageControls() {
        int totalPages = totalPages();
        pageInfoLabel.setText("第 " + currentPage + " 页 / 共 " + currentTotal + " 条");
        previousPageButton.setEnabled(currentPage > 1);
        nextPageButton.setEnabled(pageCursors.containsKey(currentPage + 1));
    }

    private int totalPages() {
        if (currentTotal <= 0) {
            return 1;
        }
        return (int) Math.ceil(currentTotal / (double) pageSize);
    }

    private void resetPagination() {
        currentPage = 1;
        pageCursors.clear();
    }

    private record OrderCursor(LocalDateTime createdAt, Long orderId) {
    }

    private void loadProfile() {
        User actor = currentUser;
        long expectedSession = sessionVersion;
        realNameField.setText("");
        emailField.setText(actor == null ? "" : nullToEmpty(actor.getEmail()));
        phoneField.setText(actor == null ? "" : nullToEmpty(actor.getPhone()));
        idCardField.setText("");
        addressField.setText("");
        notesArea.setText("");
        preferredContactBox.setSelectedIndex(0);
        notificationBox.setSelectedIndex(0);
        saveProfileButton.setEnabled(false);
        new SwingWorker<Profile, Void>() {
            @Override
            protected Profile doInBackground() {
                return userService.getProfile(actor.getUserId());
            }

            @Override
            protected void done() {
                if (!isCurrentSession(actor, expectedSession)) {
                    saveProfileButton.setEnabled(true);
                    return;
                }
                try {
                    Profile profile = get();
                    if (profile != null) {
                        realNameField.setText(profile.getRealName());
                        idCardField.setText(profile.getIdCard());
                        addressField.setText(profile.getAddress());
                        loadProfileNotes(profile.getNotes());
                    }
                    updateRealNameAuthButton();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(UserWorkbenchPanel.this, "加载联系信息失败", "提示", JOptionPane.WARNING_MESSAGE);
                } finally {
                    saveProfileButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void saveProfile() {
        saveProfile(true, null);
    }

    private void saveProfile(boolean showMessage, Runnable onSuccess) {
        User actor = currentUser;
        long expectedSession = sessionVersion;
        Profile profile = new Profile();
        profile.setUserId(actor.getUserId());
        profile.setRealName(realNameField.getText());
        profile.setIdCard(idCardField.getText());
        profile.setAddress(addressField.getText());
        profile.setNotes(formatProfileNotes());
        User updatedUser = new User();
        updatedUser.setUserId(actor.getUserId());
        updatedUser.setEmail(emailField.getText());
        updatedUser.setPhone(phoneField.getText());
        saveProfileButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                userService.updateUser(actor, updatedUser);
                userService.saveProfile(actor, profile);
                return null;
            }

            @Override
            protected void done() {
                if (!isCurrentSession(actor, expectedSession)) {
                    saveProfileButton.setEnabled(true);
                    return;
                }
                try {
                    get();
                    actor.setEmail(updatedUser.getEmail());
                    actor.setPhone(updatedUser.getPhone());
                    updateRealNameAuthButton();
                    if (showMessage) {
                        JOptionPane.showMessageDialog(UserWorkbenchPanel.this, "资料已保存");
                    }
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    JOptionPane.showMessageDialog(UserWorkbenchPanel.this, cause.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
                } finally {
                    saveProfileButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void loadProfileNotes(String notes) {
        notesArea.setText("");
        if (notes == null || notes.isBlank()) {
            return;
        }
        for (String line : notes.split("\\R")) {
            if (line.startsWith("首选联系方式：")) {
                selectComboValue(preferredContactBox, line.substring("首选联系方式：".length()));
            } else if (line.startsWith("通知偏好：")) {
                selectComboValue(notificationBox, line.substring("通知偏好：".length()));
            } else if (line.startsWith("备注：")) {
                notesArea.append(line.substring("备注：".length()));
            } else {
                if (!notesArea.getText().isBlank()) {
                    notesArea.append("\n");
                }
                notesArea.append(line);
            }
        }
    }

    private void selectComboValue(JComboBox<String> comboBox, String value) {
        for (int index = 0; index < comboBox.getItemCount(); index++) {
            if (comboBox.getItemAt(index).equals(value)) {
                comboBox.setSelectedIndex(index);
                return;
            }
        }
    }

    private String formatProfileNotes() {
        return "首选联系方式：" + preferredContactBox.getSelectedItem()
            + "\n通知偏好：" + notificationBox.getSelectedItem()
            + "\n备注：" + notesArea.getText().trim();
    }

    private void showRealNameAuthDialog() {
        JTextField authNameField = new JTextField(realNameField.getText(), 20);
        JTextField authIdCardField = new JTextField(idCardField.getText(), 24);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.add(new JLabel("真实姓名"));
        form.add(authNameField);
        form.add(new JLabel("证件号码"));
        form.add(authIdCardField);
        panel.add(form, BorderLayout.CENTER);
        int option = JOptionPane.showConfirmDialog(this, panel,
            "实名认证", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return;
        }
        if (authNameField.getText().isBlank() || authIdCardField.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "真实姓名和证件号码不能为空。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        realNameField.setText(authNameField.getText().trim());
        idCardField.setText(authIdCardField.getText().trim());
        saveProfile(false, () -> JOptionPane.showMessageDialog(this, "实名认证已完成"));
    }

    private void updateRealNameAuthButton() {
        boolean verified = idCardField.getText() != null && !idCardField.getText().isBlank();
        realNameAuthButton.setText(verified ? "已实名认证" : "实名认证");
        realNameAuthButton.setEnabled(!verified);
    }

    private void showSelectedTicketDetail() {
        CrossTicketDTO ticket = selectedTicket();
        if (ticket == null || ticket.getItem() == null) {
            JOptionPane.showMessageDialog(this, "请先选择一条工单。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        showTicketDetailDialog(ticket.getItem().getItemId());
    }

    private CrossTicketDTO selectedTicket() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(selectedRow);
        return tableModel.getTicketAt(modelRow);
    }

    private void showTicketDetailDialog(Long itemId) {
        JDialog dialog = new JDialog(mainFrame, "工单详情 #" + itemId, true);
        WindowIconUtil.apply(dialog);
        AppTheme.closeOnEscape(dialog);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(AppTheme.PAGE);
        JTextArea detailArea = new JTextArea(22, 72);
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        dialog.add(AppTheme.scroll(detailArea), BorderLayout.CENTER);

        JLabel summary = AppTheme.muted("正在加载工单信息、处理状态和沟通记录…");
        dialog.add(AppTheme.pageHeader("工单详情 #" + itemId, "查看处理状态、沟通记录并继续跟进", summary), BorderLayout.NORTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setBackground(AppTheme.SURFACE);
        actions.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.BORDER));
        JButton refreshButton = new JButton("刷新");
        JButton replyButton = new JButton("追加回复");
        JButton urgeButton = new JButton("催促处理");
        JButton rateButton = new JButton("评价");
        JButton closeButton = new JButton("关闭");
        AppTheme.secondary(refreshButton);
        AppTheme.primary(replyButton);
        AppTheme.secondary(urgeButton);
        AppTheme.secondary(rateButton);
        AppTheme.secondary(closeButton);
        actions.add(refreshButton);
        actions.add(replyButton);
        actions.add(urgeButton);
        actions.add(rateButton);
        actions.add(closeButton);
        dialog.add(actions, BorderLayout.SOUTH);

        replyButton.setEnabled(false);
        urgeButton.setEnabled(false);
        rateButton.setEnabled(false);
        Runnable refreshDetail = () -> loadTicketDetail(
            itemId, detailArea, summary, replyButton, urgeButton, rateButton);
        refreshButton.addActionListener(event -> refreshDetail.run());
        replyButton.addActionListener(event -> {
            if (addCustomerReply(itemId)) {
                refreshDetail.run();
                loadOrders();
                loadHomeOverview();
            }
        });
        urgeButton.addActionListener(event -> {
            if (urgeTicket(itemId)) {
                refreshDetail.run();
                loadOrders();
                loadHomeOverview();
            }
        });
        rateButton.addActionListener(event -> {
            if (rateTicket(itemId)) {
                refreshDetail.run();
                loadOrders();
                loadHomeOverview();
            }
        });
        closeButton.addActionListener(event -> dialog.dispose());

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        refreshDetail.run();
        dialog.setVisible(true);
    }

    private void loadTicketDetail(Long itemId, JTextArea detailArea, JLabel summary,
                                  JButton replyButton, JButton urgeButton, JButton rateButton) {
        detailArea.setText("正在加载工单详情...");
        summary.setText("正在加载最新状态…");
        replyButton.setEnabled(false);
        urgeButton.setEnabled(false);
        rateButton.setEnabled(false);
        new SwingWorker<ItemDetailDTO, Void>() {
            @Override
            protected ItemDetailDTO doInBackground() {
                return businessService.getTicketDetail(currentUser, itemId);
            }

            @Override
            protected void done() {
                try {
                    ItemDetailDTO ticket = get();
                    detailArea.setText(formatTicketDetail(ticket));
                    detailArea.setCaretPosition(0);
                    Integer status = ticket.getOrder() == null ? null : ticket.getOrder().getStatus();
                    ItemDetail.Metadata metadata = ticket.getItemDetail() == null
                        ? null : ticket.getItemDetail().getMetadata();
                    int reminderCount = metadata == null ? 0 : metadata.getReminderCount();
                    summary.setText("状态：" + statusText(status) + " · 已催促 " + reminderCount + " 次");
                    replyButton.setEnabled(!Integer.valueOf(3).equals(status) && !Integer.valueOf(4).equals(status));
                    urgeButton.setEnabled(Integer.valueOf(0).equals(status) || Integer.valueOf(1).equals(status));
                    rateButton.setEnabled(Integer.valueOf(2).equals(status) || Integer.valueOf(3).equals(status));
                } catch (Exception ex) {
                    detailArea.setText("加载工单详情失败：" + ex.getMessage());
                    summary.setText("详情加载失败");
                }
            }
        }.execute();
    }

    private boolean urgeTicket(Long itemId) {
        int confirm = JOptionPane.showConfirmDialog(this,
            "确认催促工单 #" + itemId + "？\n每 30 分钟最多催促一次，管理员会看到催促次数和时间。",
            "催促处理", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return false;
        }
        try {
            businessService.urgeTicket(currentUser, itemId);
            JOptionPane.showMessageDialog(this, "催促已提交，管理员会在工单列表中看到提醒。",
                "催促成功", JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
            return false;
        }
    }

    private boolean addCustomerReply(Long itemId) {
        TextEntryDialog.Result result = TextEntryDialog.show(this, "追加回复", "请输入回复内容", null, 6, 42);
        if (!result.accepted()) {
            return false;
        }
        try {
            businessService.addCustomerReply(currentUser, itemId, result.text());
            JOptionPane.showMessageDialog(this, "回复已提交");
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
            return false;
        }
    }

    private boolean rateTicket(Long itemId) {
        JComboBox<Integer> ratingBox = new JComboBox<>(new Integer[] {5, 4, 3, 2, 1});
        AppTheme.styleComboBox(ratingBox);
        JPanel ratingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ratingPanel.setOpaque(false);
        ratingPanel.add(new JLabel("评分"));
        ratingPanel.add(ratingBox);
        TextEntryDialog.Result result = TextEntryDialog.show(this, "评价工单", "请输入评价内容", ratingPanel, 5, 42);
        if (!result.accepted()) {
            return false;
        }
        try {
            businessService.rateTicket(currentUser, itemId, (Integer) ratingBox.getSelectedItem(), result.text());
            JOptionPane.showMessageDialog(this, "评价已提交");
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
            return false;
        }
    }

    private String formatTicketDetail(ItemDetailDTO ticket) {
        StringBuilder builder = new StringBuilder();
        if (ticket.getItem() != null) {
            builder.append("工单编号：").append(ticket.getItem().getItemId()).append('\n');
            builder.append("标题：").append(nullToEmpty(ticket.getItem().getTitle())).append('\n');
            builder.append("分类：").append(categoryText(ticket.getItem().getCategoryId())).append('\n');
            builder.append("更新时间：").append(TimeFormatUtil.format(ticket.getItem().getUpdatedAt())).append('\n');
        }
        if (ticket.getOrder() != null) {
            builder.append("记录编号：").append(ticket.getOrder().getOrderId()).append('\n');
            builder.append("状态：").append(statusText(ticket.getOrder().getStatus())).append('\n');
            builder.append("金额：").append(ticket.getOrder().getAmount()).append('\n');
            builder.append("创建时间：").append(TimeFormatUtil.format(ticket.getOrder().getCreatedAt())).append('\n');
        }
        if (ticket.getUser() != null) {
            builder.append("提交人：").append(nullToEmpty(ticket.getUser().getUsername()))
                .append(" / ").append(nullToEmpty(ticket.getUser().getPhone()))
                .append(" / ").append(nullToEmpty(ticket.getUser().getEmail())).append('\n');
        }
        if (ticket.getProfile() != null) {
            builder.append("联系信息：")
                .append(nullToEmpty(ticket.getProfile().getRealName()))
                .append("，")
                .append(nullToEmpty(ticket.getProfile().getAddress()))
                .append('\n');
        }

        ItemDetail detail = ticket.getItemDetail();
        if (detail != null) {
            ItemDetail.Metadata metadata = detail.getMetadata();
            builder.append("优先级：")
                .append(metadata == null ? "" : nullToEmpty(metadata.getPriority()))
                .append('\n');
            if (metadata != null) {
                builder.append("处理归属：")
                    .append(metadata.getAssignedAdminId() == null || metadata.getAssignedAdminId().isBlank()
                        ? "等待管理员认领" : "已有管理员负责")
                    .append('\n');
                if (metadata.getTransferTargetAdminId() != null && !metadata.getTransferTargetAdminId().isBlank()) {
                    builder.append("协作状态：管理员接手确认中").append('\n');
                }
                builder.append("催促次数：").append(metadata.getReminderCount()).append('\n');
                builder.append("最近催促：")
                    .append(metadata.getLastRemindedAt() == null ? "尚未催促"
                        : TimeFormatUtil.format(metadata.getLastRemindedAt()))
                    .append('\n');
            }
            builder.append("\n描述：\n")
                .append(nullToEmpty(detail.getDescription()))
                .append('\n');
        }

        appendUserTicketHistory(builder, ticket.getHistories());
        builder.append("\n沟通记录：\n");
        if (ticket.getComments() == null || ticket.getComments().isEmpty()) {
            builder.append("暂无沟通记录。\n");
        } else {
            for (Comment comment : ticket.getComments()) {
                builder.append("- ")
                    .append(formatBeijingTime(comment.getCreatedAt()))
                    .append(" 用户 ")
                    .append(nullToEmpty(comment.getUserId()))
                    .append(" ")
                    .append(commentTypeText(comment))
                    .append('\n')
                    .append("  ")
                    .append(nullToEmpty(comment.getContent()))
                    .append('\n');
                if (comment.getRating() != null && !comment.getRating().isBlank()) {
                    builder.append("  评分：").append(comment.getRating()).append('\n');
                }
            }
        }
        return builder.toString();
    }

    private void appendUserTicketHistory(StringBuilder builder, List<TicketHistory> histories) {
        builder.append("\n工单进度：\n");
        if (histories == null || histories.isEmpty()) {
            builder.append("暂无进度记录\n");
            return;
        }
        for (TicketHistory history : histories) {
            builder.append("[").append(TimeFormatUtil.format(history.getOccurredAt())).append("] ")
                .append(userHistoryText(history)).append('\n');
        }
    }

    private String userHistoryText(TicketHistory history) {
        return switch (history.getEventType()) {
            case "TICKET_CREATED" -> "工单已创建";
            case "MIGRATION_SNAPSHOT" -> "历史工单已接入进度追踪，当时状态：" + statusText(history.getToStatus());
            case "TICKET_CLAIMED" -> "工单已由客服接单";
            case "STATUS_CHANGED", "AUTO_CANCELLED", "BATCH_STATUS_CHANGED" -> "状态更新："
                + statusText(history.getFromStatus()) + " → " + statusText(history.getToStatus());
            case "REMINDER_SENT" -> "已提交催促";
            case "CUSTOMER_REPLY_ADDED" -> "已追加回复";
            case "ADMIN_REPLY_ADDED" -> "客服已回复";
            case "RATING_SUBMITTED" -> "已提交评价";
            case "CATEGORY_REASSIGNED" -> "工单分类已调整";
            default -> "工单已更新";
        };
    }

    private String commentTypeText(Comment comment) {
        if (comment.getTags() == null || comment.getTags().isEmpty()) {
            return "";
        }
        String tag = comment.getTags().get(0);
        return switch (tag) {
            case "CUSTOMER_REPLY" -> "客户回复";
            case "ADMIN_REPLY" -> "客服回复";
            case "CUSTOMER_RATING" -> "客户评价";
            default -> tag;
        };
    }

    private String categoryText(Long categoryId) {
        if (categoryId == null) {
            return "";
        }
        return categoryNameById.getOrDefault(categoryId, String.valueOf(categoryId));
    }

    private String formatBeijingTime(Instant instant) {
        return TimeFormatUtil.format(instant);
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

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record CategoryOption(Long categoryId, String name) {
        @Override
        public String toString() {
            return name == null ? "" : name;
        }
    }

    private record PriorityOption(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record StatusOption(Integer status, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
