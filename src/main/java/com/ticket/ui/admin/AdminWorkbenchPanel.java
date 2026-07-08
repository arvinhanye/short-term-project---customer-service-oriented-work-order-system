package com.ticket.ui.admin;

import com.ticket.model.User;
import com.ticket.service.MaintenanceService;
import com.ticket.service.StatisticsService;
import com.ticket.service.SystemHealthService;
import com.ticket.service.UserService;
import com.ticket.ui.MainFrame;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalDateTime;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

public class AdminWorkbenchPanel extends JPanel {
    private final MainFrame mainFrame;
    private final StatisticsService statisticsService = new StatisticsService();
    private final UserService userService = new UserService();
    private final MaintenanceService maintenanceService = new MaintenanceService();
    private final SystemHealthService systemHealthService = new SystemHealthService();
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
        JButton batchButton = new JButton("批量取消超时");
        JButton logoutButton = new JButton("退出登录");
        topBar.add(headerLabel);
        topBar.add(statsButton);
        topBar.add(usersButton);
        topBar.add(healthButton);
        topBar.add(batchButton);
        topBar.add(logoutButton);
        add(scrollableHeader(topBar), BorderLayout.NORTH);

        JTable leftTable = new JTable(leftTableModel);
        leftTableModel.addRow(new Object[]{"全部工单", "三栏式客服工作台入口"});
        leftTableModel.addRow(new Object[]{"分类管理", "维护一级/二级分类"});
        leftTableModel.addRow(new Object[]{"行为日志", "查看行为聚合、评论统计和评分分布"});
        leftTableModel.addRow(new Object[]{"系统日志", "查看审计与异常日志"});

        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(centerArea), new JScrollPane(rightArea));
        rightSplit.setResizeWeight(0.7);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(leftTable), rightSplit);
        splitPane.setResizeWeight(0.28);
        splitPane.setPreferredSize(new Dimension(1200, 700));
        add(splitPane, BorderLayout.CENTER);

        statsButton.addActionListener(event -> loadStats());
        usersButton.addActionListener(event -> loadUsers());
        healthButton.addActionListener(event -> runHealthCheck());
        batchButton.addActionListener(event -> batchCancelStalePendingOrders());
        logoutButton.addActionListener(event -> mainFrame.logout());
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
