package com.ticket.ui.admin;

import com.ticket.model.User;
import com.ticket.service.StatisticsService;
import com.ticket.service.UserService;
import com.ticket.ui.MainFrame;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.JButton;
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
        JButton logoutButton = new JButton("退出登录");
        topBar.add(headerLabel);
        topBar.add(statsButton);
        topBar.add(usersButton);
        topBar.add(logoutButton);
        add(topBar, BorderLayout.NORTH);

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
        logoutButton.addActionListener(event -> mainFrame.logout());
    }

    public void bindUser(User user) {
        this.currentUser = user;
        headerLabel.setText("ADMIN：" + user.getUsername());
        centerArea.setText("左侧为查询与列表，中间用于详情与回复记录，右侧用于客户资料、分类、优先级、状态操作。");
        rightArea.setText("这里将承载用户档案、分配客服、状态流转与内部备注。");
    }

    private void loadStats() {
        try {
            centerArea.setText("行为日志概览：\n" + statisticsService.behaviorDashboard(currentUser));
            rightArea.setText("系统日志统计：\n" + statisticsService.systemLogSummary(currentUser)
                + "\n\n最近审计日志：\n" + statisticsService.auditLogs(currentUser, 10));
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
}
