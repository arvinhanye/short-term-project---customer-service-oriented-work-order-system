package com.ticket.ui.admin;

import com.ticket.dao.mysql.CategoryDAO;
import com.ticket.dto.ConnectionPoolStatusDTO;
import com.ticket.dto.CrossTicketDTO;
import com.ticket.dto.PageResult;
import com.ticket.model.Category;
import com.ticket.model.Comment;
import com.ticket.model.ItemDetail;
import com.ticket.model.User;
import com.ticket.service.BusinessService;
import com.ticket.service.ConnectionPoolMonitorService;
import com.ticket.service.CrossDatabaseQueryService;
import com.ticket.service.MaintenanceService;
import com.ticket.service.StatisticsService;
import com.ticket.service.SystemHealthService;
import com.ticket.service.UserService;
import com.ticket.ui.MainFrame;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
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
    private final MainFrame mainFrame;
    private final BusinessService businessService = new BusinessService();
    private final CrossDatabaseQueryService crossDatabaseQueryService = new CrossDatabaseQueryService();
    private final StatisticsService statisticsService = new StatisticsService();
    private final UserService userService = new UserService();
    private final MaintenanceService maintenanceService = new MaintenanceService();
    private final SystemHealthService systemHealthService = new SystemHealthService();
    private final ConnectionPoolMonitorService connectionPoolMonitorService = new ConnectionPoolMonitorService();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final JLabel headerLabel = new JLabel("未登录");
    private final DefaultTableModel leftTableModel = new DefaultTableModel(new Object[]{"模块", "说明"}, 0);
    private final JTextArea centerArea = new JTextArea();
    private final JTextArea rightArea = new JTextArea();
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
        configureReadOnlyArea(centerArea);
        configureReadOnlyArea(rightArea);
        leftTableModel.addRow(new Object[]{"全部工单", "三栏式客服工作台入口"});
        leftTableModel.addRow(new Object[]{"分类管理", "维护一级/二级分类"});
        leftTableModel.addRow(new Object[]{"行为日志", "查看行为聚合、评论统计和评分分布"});
        leftTableModel.addRow(new Object[]{"系统日志", "查看审计与异常日志"});
        leftTableModel.addRow(new Object[]{"连接池监控", "查看 HikariCP 实时连接池状态"});
        leftTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        leftTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int row = leftTable.rowAtPoint(event.getPoint());
                if (row >= 0) {
                    leftTable.setRowSelectionInterval(row, row);
                    handleModuleClick(String.valueOf(leftTableModel.getValueAt(row, 0)));
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
            case "行为日志", "系统日志" -> loadStats();
            case "连接池监控" -> showConnectionPoolStatus();
            default -> centerArea.setText("已选择：" + moduleName);
        }
    }

    private void showTicketManager() {
        try {
            DefaultTableModel model = new DefaultTableModel(
                new Object[]{"记录编号", "工单编号", "标题", "用户", "状态", "分类", "优先级", "创建时间"}, 0);
            JTable table = new JTable(model);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setRowHeight(26);
            JTextArea detailArea = new JTextArea(14, 42);
            configureReadOnlyArea(detailArea);
            List<CrossTicketDTO> tickets = new ArrayList<>();

            JTextField keywordField = new JTextField(18);
            JComboBox<String> statusFilter = new JComboBox<>(new String[]{
                "全部状态", "0 待处理", "1 处理中", "2 已完成", "3 已关闭", "4 已取消"
            });
            JButton refreshButton = new JButton("查询/刷新");
            JButton detailButton = new JButton("查看详情");
            JButton replyButton = new JButton("客服回复");
            JButton noteButton = new JButton("内部备注");
            JButton statusButton = new JButton("状态流转");
            JButton assignButton = new JButton("分配客服");

            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
            toolbar.add(new JLabel("标题关键词"));
            toolbar.add(keywordField);
            toolbar.add(new JLabel("状态"));
            toolbar.add(statusFilter);
            toolbar.add(refreshButton);
            toolbar.add(detailButton);
            toolbar.add(replyButton);
            toolbar.add(noteButton);
            toolbar.add(statusButton);
            toolbar.add(assignButton);

            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(table), new JScrollPane(detailArea));
            splitPane.setResizeWeight(0.62);

            JDialog dialog = new JDialog(mainFrame, "工单管理与处理", false);
            dialog.setLayout(new BorderLayout());
            dialog.add(toolbar, BorderLayout.NORTH);
            dialog.add(splitPane, BorderLayout.CENTER);

            Runnable loadTickets = () -> loadTicketRows(model, tickets, keywordField.getText(), statusFilter.getSelectedIndex(), detailArea);
            refreshButton.addActionListener(event -> loadTickets.run());
            detailButton.addActionListener(event -> showSelectedTicketDetail(table, tickets, detailArea));
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() >= 2) {
                        showSelectedTicketDetail(table, tickets, detailArea);
                    }
                }
            });
            replyButton.addActionListener(event -> addTicketText(table, tickets, false, loadTickets, detailArea));
            noteButton.addActionListener(event -> addTicketText(table, tickets, true, loadTickets, detailArea));
            statusButton.addActionListener(event -> changeTicketStatus(table, tickets, loadTickets, detailArea));
            assignButton.addActionListener(event -> assignTicket(table, tickets, loadTickets, detailArea));

            loadTickets.run();
            dialog.setSize(1180, 680);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void loadTicketRows(DefaultTableModel model, List<CrossTicketDTO> tickets,
                                String keyword, int statusIndex, JTextArea detailArea) {
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
                tickets.add(ticket);
                ItemDetail.Metadata metadata = ticket.getItemDetail() == null
                    ? null : ticket.getItemDetail().getMetadata();
                model.addRow(new Object[]{
                    ticket.getOrder() == null ? "" : ticket.getOrder().getOrderId(),
                    ticket.getItem() == null ? "" : ticket.getItem().getItemId(),
                    ticket.getItem() == null ? "" : ticket.getItem().getTitle(),
                    ticket.getUser() == null ? "" : ticket.getUser().getUsername(),
                    ticket.getOrder() == null ? "" : statusText(ticket.getOrder().getStatus()),
                    ticket.getCategory() == null ? "" : ticket.getCategory().getName(),
                    metadata == null ? "" : metadata.getPriority(),
                    ticket.getOrder() == null ? "" : ticket.getOrder().getCreatedAt()
                });
            }
            detailArea.setText("已加载 " + tickets.size() + " 条工单。选中一行后点击上方按钮处理，双击行可查看详情。");
            centerArea.setText("工单管理：已加载 " + tickets.size() + " 条记录。");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "加载工单失败：" + ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showSelectedTicketDetail(JTable table, List<CrossTicketDTO> tickets, JTextArea detailArea) {
        CrossTicketDTO ticket = selectedTicket(table, tickets);
        if (ticket == null) {
            return;
        }
        try {
            CrossTicketDTO freshTicket = crossDatabaseQueryService.getTicket(currentUser, ticket.getItem().getItemId());
            detailArea.setText(formatTicketDetail(freshTicket));
            detailArea.setCaretPosition(0);
            rightArea.setText("当前工单：" + freshTicket.getItem().getTitle()
                + "\n状态：" + (freshTicket.getOrder() == null ? "" : statusText(freshTicket.getOrder().getStatus()))
                + "\n评论数：" + freshTicket.getCommentCount());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "加载详情失败：" + ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void addTicketText(JTable table, List<CrossTicketDTO> tickets, boolean internalNote,
                               Runnable reload, JTextArea detailArea) {
        CrossTicketDTO ticket = selectedTicket(table, tickets);
        if (ticket == null) {
            return;
        }
        JTextArea inputArea = new JTextArea(8, 42);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        int confirm = JOptionPane.showConfirmDialog(this, new JScrollPane(inputArea),
            internalNote ? "新增内部备注" : "新增客服回复", JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            if (internalNote) {
                businessService.addInternalNote(currentUser, ticket.getItem().getItemId(), inputArea.getText());
            } else {
                businessService.addAgentReply(currentUser, ticket.getItem().getItemId(), inputArea.getText());
            }
            reload.run();
            detailArea.setText((internalNote ? "内部备注" : "客服回复") + "已保存，请重新选择工单查看最新详情。");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void changeTicketStatus(JTable table, List<CrossTicketDTO> tickets, Runnable reload, JTextArea detailArea) {
        CrossTicketDTO ticket = selectedTicket(table, tickets);
        if (ticket == null || ticket.getOrder() == null) {
            return;
        }
        JComboBox<String> statusBox = new JComboBox<>(new String[]{"1 处理中", "2 已完成", "3 已关闭", "4 已取消"});
        int confirm = JOptionPane.showConfirmDialog(this, statusBox, "选择新状态", JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }
        int newStatus = Integer.parseInt(String.valueOf(statusBox.getSelectedItem()).substring(0, 1));
        try {
            businessService.changeOrderStatus(currentUser, ticket.getOrder().getOrderId(), newStatus);
            reload.run();
            detailArea.setText("状态已更新，请重新选择工单查看最新详情。");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void assignTicket(JTable table, List<CrossTicketDTO> tickets, Runnable reload, JTextArea detailArea) {
        CrossTicketDTO ticket = selectedTicket(table, tickets);
        if (ticket == null) {
            return;
        }
        JTextField adminIdField = new JTextField(String.valueOf(currentUser.getUserId()), 16);
        int confirm = JOptionPane.showConfirmDialog(this, adminIdField, "输入 ADMIN 用户编号", JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            businessService.assignAdmin(currentUser, ticket.getItem().getItemId(), Long.parseLong(adminIdField.getText().trim()));
            reload.run();
            detailArea.setText("客服分配已保存，请重新选择工单查看最新详情。");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private CrossTicketDTO selectedTicket(JTable table, List<CrossTicketDTO> tickets) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= tickets.size()) {
            JOptionPane.showMessageDialog(this, "请先选择一条工单。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return tickets.get(selectedRow);
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
                .append(comment.getCreatedAt())
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

    private void showCategoryManager() {
        try {
            DefaultTableModel model = new DefaultTableModel(new Object[]{"分类编号", "分类名称", "父分类编号"}, 0);
            JTable table = new JTable(model);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            JTextField nameField = new JTextField(16);
            JTextField parentIdField = new JTextField(8);
            JButton refreshButton = new JButton("刷新");
            JButton addButton = new JButton("新增");
            JButton updateButton = new JButton("修改所选");
            JButton deleteButton = new JButton("删除所选");

            JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
            form.add(new JLabel("分类名称"));
            form.add(nameField);
            form.add(new JLabel("父分类ID"));
            form.add(parentIdField);
            form.add(refreshButton);
            form.add(addButton);
            form.add(updateButton);
            form.add(deleteButton);

            JDialog dialog = new JDialog(mainFrame, "分类管理", false);
            dialog.setLayout(new BorderLayout());
            dialog.add(form, BorderLayout.NORTH);
            dialog.add(new JScrollPane(table), BorderLayout.CENTER);

            Runnable refresh = () -> loadCategoryRows(model);
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
            dialog.setSize(760, 520);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void loadCategoryRows(DefaultTableModel model) {
        try {
            model.setRowCount(0);
            for (Category category : categoryDAO.findAll()) {
                model.addRow(new Object[]{
                    category.getCategoryId(),
                    category.getName(),
                    category.getParentId() == null ? "" : category.getParentId()
                });
            }
            centerArea.setText("分类管理：已加载 " + model.getRowCount() + " 个分类。");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "加载分类失败：" + ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void saveCategory(Long categoryId, String name, String parentIdText, Runnable refresh) {
        try {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("分类名称不能为空");
            }
            Category category = new Category();
            category.setCategoryId(categoryId);
            category.setName(name.trim());
            category.setParentId(parseNullableLong(parentIdText));
            if (categoryId == null) {
                categoryDAO.insert(category);
            } else {
                categoryDAO.update(category);
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
            if (categoryDAO.countChildren(categoryId) > 0 || categoryDAO.countItems(categoryId) > 0) {
                JOptionPane.showMessageDialog(this, "该分类仍有子分类或工单，不能删除。", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(this, "确认删除分类 " + categoryId + "？",
                "删除确认", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                categoryDAO.delete(categoryId);
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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
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
        centerArea.setText("左侧为查询与列表，中间用于详情与回复记录，右侧用于客户资料、分类、优先级、状态操作。");
        rightArea.setText("这里将承载用户档案、分配客服、状态流转与内部备注。");
    }

    private void loadStats() {
        try {
            JDialog dialog = new JDialog(mainFrame, "数据统计与报表", false);
            dialog.setLayout(new BorderLayout());
            dialog.add(new AdminStatisticsPanel(statisticsService, currentUser), BorderLayout.CENTER);
            dialog.setSize(1100, 720);
            dialog.setLocationRelativeTo(this);
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

            JDialog dialog = new JDialog(mainFrame, "连接池状态监控", false);
            dialog.setLayout(new BorderLayout());
            dialog.add(toolbar, BorderLayout.NORTH);
            dialog.add(new JScrollPane(table), BorderLayout.CENTER);
            connectionPoolMonitorService.recordPanelView(currentUser);
            Runnable refresh = () -> refreshConnectionPoolModel(model, statusLabel);
            refresh.run();
            Timer timer = new Timer(800, event -> refresh.run());
            refreshButton.addActionListener(event -> refresh.run());
            simulateButton.addActionListener(event -> simulateConnectionUsage(simulateButton, refresh));
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
            dialog.setSize(860, 460);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void refreshConnectionPoolModel(DefaultTableModel model, JLabel statusLabel) {
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
            centerArea.setText("连接池读写分离状态：\n" + summary);
            rightArea.setText("SELECT 默认走 READ 池，写入、更新和事务默认走 WRITE 池。");
        } catch (Exception ex) {
            statusLabel.setText("刷新失败：" + ex.getMessage());
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

    private void simulateConnectionUsage(JButton simulateButton, Runnable refresh) {
        simulateButton.setEnabled(false);
        simulateButton.setText("占用中...");
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
            rightArea.setText("批量取消完成，影响 " + affectedRows + " 条工单。");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }
}
