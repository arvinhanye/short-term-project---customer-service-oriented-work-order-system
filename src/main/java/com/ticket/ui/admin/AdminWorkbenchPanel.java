package com.ticket.ui.admin;

import com.ticket.dto.ConnectionPoolStatusDTO;
import com.ticket.dto.CrossTicketDTO;
import com.ticket.dto.PageResult;
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
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
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
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

public class AdminWorkbenchPanel extends JPanel {
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter BEIJING_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss '北京时间'");
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
    private final DefaultTableModel leftTableModel = new DefaultTableModel(new Object[]{"模块", "说明"}, 0);
    private final JTextArea centerArea = new JTextArea();
    private final JTextArea rightArea = new JTextArea();
    private JDialog activeModuleDialog;
    private String activeModuleName;
    private String selectedModuleName;
    private User currentUser;

    public AdminWorkbenchPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton statsButton = new JButton("查看统计");
        JButton usersButton = new JButton("用户管理");
        JButton healthButton = new JButton("系统自检");
        JButton poolButton = new JButton("连接池监控");
        JButton batchButton = new JButton("批量取消超时");
        JButton logoutButton = new JButton("退出登录");
        topBar.add(headerLabel);
        topBar.add(statsButton);
        topBar.add(usersButton);
        topBar.add(healthButton);
        topBar.add(poolButton);
        topBar.add(batchButton);
        topBar.add(logoutButton);
        add(scrollableHeader(topBar), BorderLayout.NORTH);

        JTable leftTable = new JTable(leftTableModel);
        leftTable.setDefaultEditor(Object.class, null);
        leftTable.setRowHeight(34);
        leftTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        configureReadOnlyArea(centerArea);
        configureReadOnlyArea(rightArea);
        leftTableModel.addRow(new Object[]{"全部工单", "查询、查看详情、回复、备注、状态流转、分配客服"});
        leftTableModel.addRow(new Object[]{"分类管理", "新增、修改、删除一级/二级分类"});
        leftTableModel.addRow(new Object[]{"行为日志", "查看行为聚合、评论统计、评分分布"});
        leftTableModel.addRow(new Object[]{"系统日志", "查看登录、工单处理、批处理、异常审计"});
        leftTableModel.addRow(new Object[]{"连接池监控", "查看 READ/WRITE 连接池实时状态"});
        leftTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        leftTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                String moduleName = selectedModuleName(leftTable);
                if (moduleName != null) {
                    showModuleHint(moduleName);
                }
            }
        });
        leftTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                int row = leftTable.rowAtPoint(event.getPoint());
                if (row >= 0) {
                    leftTable.setRowSelectionInterval(row, row);
                    String moduleName = String.valueOf(leftTableModel.getValueAt(row, 0));
                    showModuleHint(moduleName);
                }
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                int row = leftTable.rowAtPoint(event.getPoint());
                if (row >= 0) {
                    String moduleName = String.valueOf(leftTableModel.getValueAt(row, 0));
                    if (event.getClickCount() >= 2) {
                        handleModuleClick(moduleName);
                    }
                }
            }
        });

        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(centerArea), new JScrollPane(rightArea));
        rightSplit.setResizeWeight(0.7);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(leftTable), rightSplit);
        splitPane.setResizeWeight(0.28);
        splitPane.setPreferredSize(new Dimension(1200, 700));
        add(splitPane, BorderLayout.CENTER);

        statsButton.addActionListener(event -> loadStats());
        usersButton.addActionListener(event -> loadUsers());
        healthButton.addActionListener(event -> runHealthCheck());
        poolButton.addActionListener(event -> showConnectionPoolStatus());
        batchButton.addActionListener(event -> batchCancelStalePendingOrders());
        logoutButton.addActionListener(event -> mainFrame.logout());
    }

    private void handleModuleClick(String moduleName) {
        switch (moduleName) {
            case "全部工单" -> showTicketManager();
            case "分类管理" -> showCategoryManager();
            case "行为日志" -> loadBehaviorLogs();
            case "系统日志" -> loadSystemLogs();
            case "连接池监控" -> showConnectionPoolStatus();
            default -> centerArea.setText("已选择：" + moduleName);
        }
    }

    private void showModuleHint(String moduleName) {
        selectedModuleName = moduleName;
        switch (moduleName) {
            case "全部工单" -> {
                centerArea.setText("""
                    全部工单

                    双击打开后可进入工单管理与处理窗口。

                    可执行操作：
                    - 按标题关键词查询工单
                    - 按状态筛选待处理、处理中、已完成、已关闭、已取消工单
                    - 查看工单详情、客户资料、分类、优先级和历史回复
                    - 添加客服回复或内部备注
                    - 执行状态流转或分配客服
                    """);
                rightArea.setText("工单处理摘要会显示在这里。打开工单管理窗口并双击工单后，可查看当前工单状态、标题和评论数。");
            }
            case "分类管理" -> {
                centerArea.setText("""
                    分类管理

                    双击打开后可维护一级/二级分类。

                    可执行操作：
                    - 新增分类
                    - 修改分类名称或父分类
                    - 删除没有子分类且没有关联工单的分类

                    父分类ID留空表示一级分类。
                    """);
                rightArea.setText("分类管理摘要会显示在这里，包括分类数量和最近操作结果。");
            }
            case "行为日志" -> {
                centerArea.setText("""
                    行为日志

                    双击后将打开独立的行为日志窗口。

                    可查看内容：
                    - 行为类型分布
                    - 近 30 天行为趋势
                    - 热门工单
                    - 用户活跃度
                    - 客户端分布
                    - 评分分布、评论标签、最近行为
                    """);
                rightArea.setText("行为日志摘要会显示在独立统计窗口中。");
            }
            case "系统日志" -> {
                centerArea.setText("""
                    系统日志

                    双击后将打开独立的系统日志窗口。

                    可查看内容：
                    - 审计日志查询
                    - 系统日志类型汇总
                    - 日志级别汇总
                    - 用户操作汇总
                    - 近 30 天系统日志趋势
                    """);
                rightArea.setText("系统日志摘要会显示在独立审计窗口中。");
            }
            case "连接池监控" -> {
                centerArea.setText("""
                    连接池监控

                    双击打开后可查看 READ/WRITE 两个 HikariCP 连接池的状态。

                    可观察指标：
                    - 正在使用连接
                    - 空闲连接
                    - 等待连接线程
                    - 使用率
                    - 连接超时、空闲超时、最大生命周期
                    """);
                rightArea.setText("连接池摘要会显示在这里，包括 READ/WRITE 池状态和使用率。");
            }
            default -> {
                centerArea.setText("已选择：" + moduleName);
                rightArea.setText("模块摘要会显示在这里。");
            }
        }
    }

    private String selectedModuleName(JTable leftTable) {
        int row = leftTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return String.valueOf(leftTableModel.getValueAt(row, 0));
    }

    private boolean isSelectedModule(String moduleName) {
        return moduleName.equals(selectedModuleName);
    }

    private boolean focusExistingModuleDialog(String moduleName) {
        if (activeModuleDialog == null || !activeModuleDialog.isDisplayable()) {
            activeModuleDialog = null;
            activeModuleName = null;
            return false;
        }
        if (moduleName.equals(activeModuleName)) {
            activeModuleDialog.toFront();
            activeModuleDialog.requestFocus();
            return true;
        }
        activeModuleDialog.dispose();
        activeModuleDialog = null;
        activeModuleName = null;
        return false;
    }

    private void registerModuleDialog(String moduleName, JDialog dialog) {
        activeModuleName = moduleName;
        activeModuleDialog = dialog;
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                clearActiveDialog(dialog);
            }

            @Override
            public void windowClosing(WindowEvent event) {
                clearActiveDialog(dialog);
            }
        });
    }

    private void clearActiveDialog(JDialog dialog) {
        if (activeModuleDialog == dialog) {
            activeModuleDialog = null;
            activeModuleName = null;
        }
    }

    private JDialog createIndependentDialog(String title) {
        JDialog dialog = new JDialog((java.awt.Frame) null, title, false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        return dialog;
    }

    private void showTicketManager() {
        try {
            if (focusExistingModuleDialog("全部工单")) {
                return;
            }
            List<AdminOption> adminOptions = activeAdminOptions();
            java.util.Map<String, String> adminNameById = adminNameById(adminOptions);
            javax.swing.DefaultListModel<AssignmentFilter> assignmentFilterModel = buildAssignmentFilterModel(adminOptions);
            javax.swing.JList<AssignmentFilter> assignmentFilterList = new javax.swing.JList<>(assignmentFilterModel);
            assignmentFilterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            assignmentFilterList.setSelectedIndex(0);
            assignmentFilterList.setVisibleRowCount(8);
            DefaultTableModel model = new DefaultTableModel(
                new Object[]{"记录编号", "工单编号", "标题", "用户", "状态", "分类", "优先级", "分配客服", "创建时间"}, 0);
            JTable table = new JTable(model);
            table.setDefaultEditor(Object.class, null);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setRowHeight(26);
            JTextArea detailArea = new JTextArea(14, 42);
            configureReadOnlyArea(detailArea);
            List<CrossTicketDTO> tickets = new ArrayList<>();

            JTextField keywordField = new JTextField(18);
            JComboBox<String> statusFilter = new JComboBox<>(new String[]{
                "全部状态", "0 待处理", "1 处理中", "2 已完成", "3 已关闭", "4 已取消"
            });
            JButton replyButton = new JButton("客服回复");
            JButton noteButton = new JButton("内部备注");
            JButton statusButton = new JButton("状态流转");
            JButton assignButton = new JButton("分配客服");
            setTicketActionButtonsEnabled(false, replyButton, noteButton, statusButton, assignButton);

            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
            toolbar.add(new JLabel("标题关键词"));
            toolbar.add(keywordField);
            toolbar.add(new JLabel("状态"));
            toolbar.add(statusFilter);
            toolbar.add(replyButton);
            toolbar.add(noteButton);
            toolbar.add(statusButton);
            toolbar.add(assignButton);

            JSplitPane tableSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(table), new JScrollPane(detailArea));
            tableSplitPane.setResizeWeight(0.62);
            JPanel assignmentPanel = new JPanel(new BorderLayout());
            assignmentPanel.add(new JLabel("分配分类"), BorderLayout.NORTH);
            assignmentPanel.add(new JScrollPane(assignmentFilterList), BorderLayout.CENTER);
            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, assignmentPanel, tableSplitPane);
            splitPane.setResizeWeight(0.18);
            JTextArea noticeArea = createNoticeArea("双击左侧分配分类、标题回车或切换状态后会刷新工单。");

            JDialog dialog = createIndependentDialog("工单管理与处理");
            dialog.setLayout(new BorderLayout());
            dialog.add(scrollableHeader(toolbar), BorderLayout.NORTH);
            dialog.add(splitPane, BorderLayout.CENTER);
            dialog.add(createNoticePane(noticeArea), BorderLayout.SOUTH);

            Runnable loadTickets = () -> {
                loadTicketRows(model, tickets, keywordField.getText(), statusFilter.getSelectedIndex(),
                    selectedAssignmentFilter(assignmentFilterList), adminNameById, detailArea, noticeArea);
                updateTicketActionButtons(table, tickets, noticeArea, replyButton, noteButton, statusButton, assignButton);
            };
            keywordField.addActionListener(event -> loadTickets.run());
            statusFilter.addActionListener(event -> loadTickets.run());
            assignmentFilterList.addListSelectionListener(event -> {
                if (!event.getValueIsAdjusting()) {
                    AssignmentFilter filter = selectedAssignmentFilter(assignmentFilterList);
                    showNotice(noticeArea, "已选择分配分类：" + filter + "。双击该分类后切换查询结果。");
                }
            });
            assignmentFilterList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() >= 2 && assignmentFilterList.locationToIndex(event.getPoint()) >= 0) {
                        loadTickets.run();
                    }
                }
            });
            table.getSelectionModel().addListSelectionListener(event -> {
                if (!event.getValueIsAdjusting()) {
                    updateTicketActionButtons(table, tickets, noticeArea, replyButton, noteButton, statusButton, assignButton);
                }
            });
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() >= 2) {
                        showSelectedTicketDetail(dialog, table, tickets, detailArea, noticeArea);
                    }
                }
            });
            replyButton.addActionListener(event -> addTicketText(dialog, table, tickets, false, loadTickets, detailArea, noticeArea));
            noteButton.addActionListener(event -> addTicketText(dialog, table, tickets, true, loadTickets, detailArea, noticeArea));
            statusButton.addActionListener(event -> changeTicketStatus(dialog, table, tickets, loadTickets, detailArea, noticeArea));
            assignButton.addActionListener(event -> assignTicket(dialog, table, tickets, loadTickets, detailArea, noticeArea));

            loadTickets.run();
            dialog.setSize(1180, 680);
            dialog.setMinimumSize(new Dimension(760, 420));
            dialog.setLocationRelativeTo(this);
            registerModuleDialog("全部工单", dialog);
            dialog.setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void loadTicketRows(DefaultTableModel model, List<CrossTicketDTO> tickets,
                                String keyword, int statusIndex, AssignmentFilter assignmentFilter,
                                java.util.Map<String, String> adminNameById, JTextArea detailArea, JTextArea noticeArea) {
        try {
            Integer status = statusIndex <= 0 ? null : statusIndex - 1;
            PageResult<CrossTicketDTO> result = keyword == null || keyword.isBlank()
                ? crossDatabaseQueryService.pageTickets(currentUser, status, 1, 50)
                : crossDatabaseQueryService.searchTickets(currentUser, keyword.trim(), 1, 50);
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
                    ticket.getCategory() == null ? "" : ticket.getCategory().getName(),
                    metadata == null ? "" : metadata.getPriority(),
                    assignedAdminDisplay(assignedAdminId, adminNameById),
                    ticket.getOrder() == null ? "" : ticket.getOrder().getCreatedAt()
                });
            }
            detailArea.setText("已加载 " + tickets.size() + " 条工单。当前分配分类：" + assignmentFilter + "。双击行可查看详情。");
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
        JTextArea inputArea = new JTextArea(8, 42);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        int confirm = JOptionPane.showConfirmDialog(parent, new JScrollPane(inputArea),
            internalNote ? "新增内部备注" : "新增客服回复", JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) {
            bringParentToFront(parent);
            return;
        }
        try {
            if (internalNote) {
                businessService.addInternalNote(currentUser, ticket.getItem().getItemId(), inputArea.getText());
            } else {
                businessService.addAgentReply(currentUser, ticket.getItem().getItemId(), inputArea.getText());
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

    private javax.swing.DefaultListModel<AssignmentFilter> buildAssignmentFilterModel(List<AdminOption> adminOptions) {
        javax.swing.DefaultListModel<AssignmentFilter> model = new javax.swing.DefaultListModel<>();
        model.addElement(new AssignmentFilter(AssignmentFilterKind.ALL, null, "全部工单"));
        model.addElement(new AssignmentFilter(AssignmentFilterKind.UNASSIGNED, null, "未分配"));
        model.addElement(new AssignmentFilter(AssignmentFilterKind.MINE, null, "我的工单"));
        for (AdminOption option : adminOptions) {
            String adminId = String.valueOf(option.user().getUserId());
            model.addElement(new AssignmentFilter(AssignmentFilterKind.ADMIN, adminId, option.toString()));
        }
        return model;
    }

    private AssignmentFilter selectedAssignmentFilter(javax.swing.JList<AssignmentFilter> assignmentFilterList) {
        AssignmentFilter filter = assignmentFilterList.getSelectedValue();
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
            builder.append("创建时间：").append(ticket.getOrder().getCreatedAt()).append('\n');
        }
        builder.append("分类：").append(ticket.getCategory() == null ? "" : ticket.getCategory().getName()).append('\n');
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
        if (instant == null) {
            return "";
        }
        return BEIJING_TIME_FORMATTER.format(instant.atZone(BEIJING_ZONE));
    }

    private void showCategoryManager() {
        try {
            if (focusExistingModuleDialog("分类管理")) {
                return;
            }
            DefaultTableModel model = new DefaultTableModel(new Object[]{"分类编号", "分类名称", "父分类编号"}, 0);
            JTable table = new JTable(model);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            JTextField nameField = new JTextField(16);
            JTextField parentIdField = new JTextField(8);
            JButton refreshButton = new JButton("刷新");
            JButton addButton = new JButton("新增");
            JButton updateButton = new JButton("修改所选");
            JButton deleteButton = new JButton("删除所选");
            JTextArea noticeArea = createNoticeArea("分类操作提示会显示在这里。");

            JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
            form.add(new JLabel("分类名称"));
            form.add(nameField);
            form.add(new JLabel("父分类ID"));
            form.add(parentIdField);
            form.add(refreshButton);
            form.add(addButton);
            form.add(updateButton);
            form.add(deleteButton);

            JDialog dialog = createIndependentDialog("分类管理");
            dialog.setLayout(new BorderLayout());
            dialog.add(scrollableHeader(form), BorderLayout.NORTH);
            dialog.add(new JScrollPane(table), BorderLayout.CENTER);
            dialog.add(createNoticePane(noticeArea), BorderLayout.SOUTH);

            Runnable refresh = () -> loadCategoryRows(model, noticeArea);
            refreshButton.addActionListener(event -> refresh.run());
            table.getSelectionModel().addListSelectionListener(event -> {
                int row = table.getSelectedRow();
                if (!event.getValueIsAdjusting() && row >= 0) {
                    nameField.setText(String.valueOf(model.getValueAt(row, 1)));
                    parentIdField.setText(String.valueOf(model.getValueAt(row, 2)));
                }
            });
            addButton.addActionListener(event -> saveCategory(null, nameField.getText(), parentIdField.getText(), refresh));
            updateButton.addActionListener(event -> {
                int row = table.getSelectedRow();
                if (row < 0) {
                    JOptionPane.showMessageDialog(this, "请先选择分类。", "提示", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                saveCategory(Long.parseLong(String.valueOf(model.getValueAt(row, 0))),
                    nameField.getText(), parentIdField.getText(), refresh);
            });
            deleteButton.addActionListener(event -> deleteSelectedCategory(table, model, refresh));

            refresh.run();
            dialog.setSize(900, 560);
            dialog.setMinimumSize(new Dimension(720, 420));
            dialog.setLocationRelativeTo(this);
            registerModuleDialog("分类管理", dialog);
            dialog.setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void loadCategoryRows(DefaultTableModel model, JTextArea noticeArea) {
        try {
            model.setRowCount(0);
            for (var category : categoryService.listCategories(currentUser)) {
                model.addRow(new Object[]{
                    category.getCategoryId(),
                    category.getName(),
                    category.getParentId() == null ? "" : category.getParentId()
                });
            }
            if (isSelectedModule("分类管理")) {
                centerArea.setText("分类管理：已加载 " + model.getRowCount() + " 个分类。");
                rightArea.setText("当前分类数量：" + model.getRowCount());
            }
            showNotice(noticeArea, "分类管理已加载 " + model.getRowCount() + " 个分类：填写名称和父分类ID后可新增或修改分类。");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "加载分类失败：" + ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void saveCategory(Long categoryId, String name, String parentIdText, Runnable refresh) {
        try {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("分类名称不能为空");
            }
            if (categoryId == null) {
                categoryService.createCategory(currentUser, name, parseNullableLong(parentIdText));
            } else {
                categoryService.updateCategory(currentUser, categoryId, name, parseNullableLong(parentIdText));
            }
            refresh.run();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void deleteSelectedCategory(JTable table, DefaultTableModel model, Runnable refresh) {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择分类。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Long categoryId = Long.parseLong(String.valueOf(model.getValueAt(row, 0)));
        try {
            int confirm = JOptionPane.showConfirmDialog(this, "确认删除分类 " + categoryId + "？",
                "删除确认", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                categoryService.deleteCategory(currentUser, categoryId);
                refresh.run();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private Long parseNullableLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value.trim());
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

    private JScrollPane scrollableHeader(JPanel panel) {
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.setPreferredSize(new Dimension(0, panel.getPreferredSize().height + 18));
        return scrollPane;
    }

    public void bindUser(User user) {
        this.currentUser = user;
        headerLabel.setText("ADMIN：" + user.getUsername());
        showAdminHome();
    }

    private void showAdminHome() {
        centerArea.setText("""
            管理员工作台

            你可以从左侧模块进入具体功能：

            1. 全部工单
               打开工单管理窗口，支持按标题和状态查询。
               选中工单后可查看详情、客服回复、添加内部备注、状态流转、分配客服。

            2. 分类管理
               维护工单分类。新增二级分类时填写父分类ID；一级分类的父分类ID留空。

            3. 行为日志 / 系统日志
               查看用户行为、评分、评论、审计日志和异常记录。

            4. 连接池监控
               查看 READ/WRITE 两个 HikariCP 连接池状态，并可模拟连接占用。
            """);
        rightArea.setText("""
            当前处理建议

            - 新工单：进入“全部工单”，筛选“待处理”，查看详情后回复或流转为处理中。
            - 处理中：补充客服回复或内部备注，完成后流转为已完成。
            - 分类维护：先确认分类下没有子分类或工单，再执行删除。
            - 审计检查：处理关键操作后，可到“系统日志”查看操作记录。

            快捷入口也在顶部按钮区保留：统计、用户管理、自检、连接池监控、批量取消超时。
            """);
    }

    private void loadStats() {
        showStatisticsDialog("数据统计与报表", "完整统计", AdminStatisticsPanel.ViewMode.FULL);
    }

    private void loadBehaviorLogs() {
        showStatisticsDialog("行为日志统计", "行为日志", AdminStatisticsPanel.ViewMode.BEHAVIOR);
    }

    private void loadSystemLogs() {
        showStatisticsDialog("系统日志审计", "系统日志", AdminStatisticsPanel.ViewMode.SYSTEM);
    }

    private void showStatisticsDialog(String title, String moduleName, AdminStatisticsPanel.ViewMode viewMode) {
        try {
            if (focusExistingModuleDialog(moduleName)) {
                return;
            }
            JDialog dialog = createIndependentDialog(title);
            dialog.setLayout(new BorderLayout());
            dialog.add(new AdminStatisticsPanel(statisticsService, currentUser, viewMode), BorderLayout.CENTER);
            dialog.setSize(1100, 720);
            dialog.setMinimumSize(new Dimension(760, 440));
            dialog.setLocationRelativeTo(this);
            registerModuleDialog(moduleName, dialog);
            dialog.setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void loadUsers() {
        try {
            var users = userService.listUsers(currentUser);
            centerArea.setText("用户列表：\n" + users.stream().map(User::getUsername).toList());
            rightArea.setText("当前共 " + users.size() + " 个用户");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void runHealthCheck() {
        try {
            var result = systemHealthService.runFullCheck(currentUser);
            centerArea.setText("系统自检结果：\n" + result);
            rightArea.setText(result.isHealthy() ? "系统状态：稳定" : "系统状态：存在异常，请查看失败项");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showConnectionPoolStatus() {
        try {
            if (focusExistingModuleDialog("连接池监控")) {
                return;
            }
            DefaultTableModel model = new DefaultTableModel(new Object[]{"连接角色", "指标", "当前值", "说明"}, 0);

            JTable table = new JTable(model);
            table.setEnabled(false);
            table.setRowHeight(26);
            JButton refreshButton = new JButton("刷新");
            JButton simulateButton = new JButton("模拟占用连接");
            JLabel statusLabel = new JLabel("每 0.8 秒自动刷新");
            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
            toolbar.add(refreshButton);
            toolbar.add(simulateButton);
            toolbar.add(statusLabel);
            JTextArea noticeArea = createNoticeArea("连接池状态会自动刷新，也可手动刷新。");

            JDialog dialog = createIndependentDialog("连接池状态监控");
            dialog.setLayout(new BorderLayout());
            dialog.add(scrollableHeader(toolbar), BorderLayout.NORTH);
            dialog.add(new JScrollPane(table), BorderLayout.CENTER);
            dialog.add(createNoticePane(noticeArea), BorderLayout.SOUTH);
            connectionPoolMonitorService.recordPanelView(currentUser);
            Runnable refresh = () -> refreshConnectionPoolModel(model, statusLabel, noticeArea);
            refresh.run();
            Timer timer = new Timer(800, event -> refresh.run());
            refreshButton.addActionListener(event -> refresh.run());
            simulateButton.addActionListener(event -> simulateConnectionUsage(simulateButton, refresh, noticeArea));
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    timer.stop();
                }

                @Override
                public void windowClosing(WindowEvent event) {
                    timer.stop();
                }
            });
            timer.start();
            dialog.setSize(900, 560);
            dialog.setMinimumSize(new Dimension(720, 420));
            dialog.setLocationRelativeTo(this);
            registerModuleDialog("连接池监控", dialog);
            dialog.setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void refreshConnectionPoolModel(DefaultTableModel model, JLabel statusLabel, JTextArea noticeArea) {
        try {
            var statuses = connectionPoolMonitorService.currentStatuses(currentUser);
            model.setRowCount(0);
            for (ConnectionPoolStatusDTO status : statuses) {
                addPoolRows(model, status);
            }
            String summary = statuses.stream()
                .map(status -> status.getRole() + "=" + status.getStatusText() + "(" + status.getUsagePercent() + "%)")
                .reduce((left, right) -> left + "，" + right)
                .orElse("无连接池状态");
            statusLabel.setText("状态：" + summary);
            if (isSelectedModule("连接池监控")) {
                centerArea.setText("连接池读写分离状态：\n" + summary);
                rightArea.setText("SELECT 默认走 READ 池，写入、更新和事务默认走 WRITE 池。");
            }
            showNotice(noticeArea, "连接池监控已刷新：" + summary);
        } catch (Exception ex) {
            statusLabel.setText("刷新失败：" + ex.getMessage());
            showNotice(noticeArea, "连接池监控刷新失败：" + ex.getMessage());
        }
    }

    private void addPoolRows(DefaultTableModel model, ConnectionPoolStatusDTO status) {
        String role = "WRITE".equals(status.getRole()) ? "写池" : "读池";
        model.addRow(new Object[]{role, "连接池名称", status.getPoolName(), "HikariCP poolName"});
        model.addRow(new Object[]{role, "状态", status.getStatusText(), "根据连接占用和等待线程判断"});
        model.addRow(new Object[]{role, "最大连接数", status.getMaximumPoolSize(), "连接池允许创建的最大 MySQL 连接数"});
        model.addRow(new Object[]{role, "最小空闲连接", status.getMinimumIdle(), "连接池尽量保持的空闲连接数"});
        model.addRow(new Object[]{role, "正在使用连接", status.getActiveConnections(), "当前被业务代码借出的连接数"});
        model.addRow(new Object[]{role, "空闲连接", status.getIdleConnections(), "当前可直接复用的连接数"});
        model.addRow(new Object[]{role, "总连接数", status.getTotalConnections(), "当前池内实际连接总数"});
        model.addRow(new Object[]{role, "等待连接线程", status.getThreadsAwaitingConnection(), "正在等待连接的线程数量"});
        model.addRow(new Object[]{role, "使用率", status.getUsagePercent() + "%", "正在使用连接 / 最大连接数"});
        model.addRow(new Object[]{role, "连接等待超时", formatMs(status.getConnectionTimeoutMs()), "获取连接最长等待时间"});
        model.addRow(new Object[]{role, "空闲超时", formatMs(status.getIdleTimeoutMs()), "空闲连接保留时间"});
        model.addRow(new Object[]{role, "连接最长生命周期", formatMs(status.getMaxLifetimeMs()), "单个连接最长存活时间"});
        model.addRow(new Object[]{role, "泄漏检测阈值", formatMs(status.getLeakDetectionThresholdMs()), "0 表示未开启泄漏检测"});
    }

    private void simulateConnectionUsage(JButton simulateButton, Runnable refresh, JTextArea noticeArea) {
        simulateButton.setEnabled(false);
        simulateButton.setText("占用中...");
        showNotice(noticeArea, "正在模拟占用连接，请稍候。");
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                connectionPoolMonitorService.simulateConnectionUsage(currentUser, 3, 8);
                return null;
            }

            @Override
            protected void done() {
                simulateButton.setEnabled(true);
                simulateButton.setText("模拟占用连接");
                refresh.run();
                try {
                    get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    JOptionPane.showMessageDialog(AdminWorkbenchPanel.this,
                        rootMessage(ex), "提示", JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
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

    private void batchCancelStalePendingOrders() {
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "将把 30 天前仍为待处理的工单批量流转为已取消，是否继续？",
            "批处理确认",
            JOptionPane.YES_NO_OPTION
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            int affectedRows = maintenanceService.batchUpdateOrderStatus(currentUser, 0, 4, LocalDateTime.now().minusDays(30));
            rightArea.setText("批量取消超时工单完成，影响 " + affectedRows + " 条。");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }
}
