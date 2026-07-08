package com.ticket.ui.admin;

import com.ticket.dto.ConnectionPoolStatusDTO;
import com.ticket.model.User;
import com.ticket.service.ConnectionPoolMonitorService;
import com.ticket.service.MaintenanceService;
import com.ticket.service.StatisticsService;
import com.ticket.service.SystemHealthService;
import com.ticket.service.UserService;
import com.ticket.ui.MainFrame;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

public class AdminWorkbenchPanel extends JPanel {
    private final MainFrame mainFrame;
    private final StatisticsService statisticsService = new StatisticsService();
    private final UserService userService = new UserService();
    private final MaintenanceService maintenanceService = new MaintenanceService();
    private final SystemHealthService systemHealthService = new SystemHealthService();
    private final ConnectionPoolMonitorService connectionPoolMonitorService = new ConnectionPoolMonitorService();
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
            DefaultTableModel model = new DefaultTableModel(new Object[]{"指标", "当前值", "说明"}, 0);

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
            ConnectionPoolStatusDTO status = connectionPoolMonitorService.currentStatus(currentUser);
            model.setRowCount(0);
            model.addRow(new Object[]{"连接池名称", status.getPoolName(), "HikariCP poolName"});
            model.addRow(new Object[]{"状态", status.getStatusText(), "根据连接占用和等待线程判断"});
            model.addRow(new Object[]{"最大连接数", status.getMaximumPoolSize(), "连接池允许创建的最大 MySQL 连接数"});
            model.addRow(new Object[]{"最小空闲连接", status.getMinimumIdle(), "连接池尽量保持的空闲连接数"});
            model.addRow(new Object[]{"正在使用连接", status.getActiveConnections(), "当前被业务代码借出的连接数"});
            model.addRow(new Object[]{"空闲连接", status.getIdleConnections(), "当前可直接复用的连接数"});
            model.addRow(new Object[]{"总连接数", status.getTotalConnections(), "当前池内实际连接总数"});
            model.addRow(new Object[]{"等待连接线程", status.getThreadsAwaitingConnection(), "正在等待连接的线程数量"});
            model.addRow(new Object[]{"使用率", status.getUsagePercent() + "%", "正在使用连接 / 最大连接数"});
            model.addRow(new Object[]{"连接等待超时", formatMs(status.getConnectionTimeoutMs()), "获取连接最长等待时间"});
            model.addRow(new Object[]{"空闲超时", formatMs(status.getIdleTimeoutMs()), "空闲连接保留时间"});
            model.addRow(new Object[]{"连接最长生命周期", formatMs(status.getMaxLifetimeMs()), "单个连接最长存活时间"});
            model.addRow(new Object[]{"泄漏检测阈值", formatMs(status.getLeakDetectionThresholdMs()), "0 表示未开启泄漏检测"});
            statusLabel.setText("状态：" + status.getStatusText() + "，使用率：" + status.getUsagePercent() + "%");
            centerArea.setText("连接池状态：" + status.getStatusText()
                + "\n正在使用连接：" + status.getActiveConnections()
                + "\n空闲连接：" + status.getIdleConnections()
                + "\n等待连接线程：" + status.getThreadsAwaitingConnection());
            rightArea.setText("连接池使用率：" + status.getUsagePercent() + "%");
        } catch (Exception ex) {
            statusLabel.setText("刷新失败：" + ex.getMessage());
        }
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
