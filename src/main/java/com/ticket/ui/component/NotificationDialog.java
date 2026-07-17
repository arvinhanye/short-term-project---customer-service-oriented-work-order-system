package com.ticket.ui.component;

import com.ticket.model.Notification;
import com.ticket.model.User;
import com.ticket.service.NotificationService;
import com.ticket.ui.theme.AppTheme;
import com.ticket.ui.theme.WindowIconUtil;
import com.ticket.util.TimeFormatUtil;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

public final class NotificationDialog {
    private NotificationDialog() { }

    public static void show(Component parent, User actor, NotificationService service, Runnable onChanged) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "站内通知", JDialog.ModalityType.DOCUMENT_MODAL);
        WindowIconUtil.apply(dialog);
        dialog.setLayout(new BorderLayout(0, 10));
        dialog.getContentPane().setBackground(AppTheme.PAGE);

        DefaultTableModel model = new DefaultTableModel(
            new Object[]{"状态", "工单", "通知", "内容", "时间"}, 0) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(model);
        AppTheme.styleTable(table);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setMaxWidth(80);
        JLabel status = AppTheme.muted("正在加载通知…");
        JButton refresh = new JButton("刷新");
        JButton markAllRead = new JButton("全部标为已读");
        JButton deleteSelected = new JButton("删除选中");
        JButton clearRead = new JButton("清空已读");
        JButton close = new JButton("关闭");
        AppTheme.secondary(refresh);
        AppTheme.primary(markAllRead);
        AppTheme.danger(deleteSelected);
        AppTheme.secondary(clearRead);
        AppTheme.secondary(close);
        deleteSelected.setEnabled(false);
        clearRead.setEnabled(false);

        JPanel header = AppTheme.surface(new BorderLayout());
        JLabel title = new JLabel("通知中心");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);
        header.add(status, BorderLayout.EAST);
        dialog.add(header, BorderLayout.NORTH);
        JScrollPane scrollPane = AppTheme.scroll(table);
        scrollPane.setPreferredSize(new Dimension(820, 430));
        dialog.add(scrollPane, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        actions.add(refresh);
        actions.add(markAllRead);
        actions.add(deleteSelected);
        actions.add(clearRead);
        actions.add(close);
        dialog.add(actions, BorderLayout.SOUTH);

        List<Notification> currentNotifications = new ArrayList<>();

        Runnable load = () -> {
            refresh.setEnabled(false);
            status.setText("正在加载通知…");
            new SwingWorker<List<Notification>, Void>() {
                @Override protected List<Notification> doInBackground() { return service.recent(actor, 100); }
                @Override protected void done() {
                    refresh.setEnabled(true);
                    if (!dialog.isDisplayable()) return;
                    try {
                        List<Notification> notifications = get();
                        currentNotifications.clear();
                        currentNotifications.addAll(notifications);
                        model.setRowCount(0);
                        long unread = 0;
                        boolean hasRead = false;
                        for (Notification notification : notifications) {
                            if (notification.getReadAt() == null) unread++;
                            else hasRead = true;
                            model.addRow(new Object[]{notification.getReadAt() == null ? "未读" : "已读",
                                notification.getItemId() == null ? "—" : "#" + notification.getItemId(),
                                notification.getTitle(), notification.getContent(),
                                TimeFormatUtil.format(notification.getCreatedAt())});
                        }
                        status.setText("共 " + notifications.size() + " 条 · 未读 " + unread + " 条");
                        clearRead.setEnabled(hasRead);
                        deleteSelected.setEnabled(table.getSelectedRow() >= 0);
                    } catch (Exception ex) {
                        status.setText("加载失败：" + rootMessage(ex));
                    }
                }
            }.execute();
        };
        refresh.addActionListener(event -> load.run());
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                deleteSelected.setEnabled(table.getSelectedRow() >= 0);
            }
        });
        markAllRead.addActionListener(event -> new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() { service.markAllRead(actor); return null; }
            @Override protected void done() {
                try {
                    get();
                    if (onChanged != null) onChanged.run();
                    load.run();
                } catch (Exception ex) {
                    status.setText("操作失败：" + rootMessage(ex));
                }
            }
        }.execute());
        deleteSelected.addActionListener(event -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow < 0) return;
            int modelRow = table.convertRowIndexToModel(selectedRow);
            if (modelRow < 0 || modelRow >= currentNotifications.size()) return;
            Notification notification = currentNotifications.get(modelRow);
            if (JOptionPane.showConfirmDialog(dialog, "确定删除通知“" + notification.getTitle() + "”？",
                    "删除通知", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                    != JOptionPane.YES_OPTION) return;
            deleteSelected.setEnabled(false);
            new SwingWorker<Boolean, Void>() {
                @Override protected Boolean doInBackground() {
                    return service.delete(actor, notification.getNotificationId());
                }
                @Override protected void done() {
                    try {
                        if (!get()) status.setText("通知已不存在或无权删除");
                        if (onChanged != null) onChanged.run();
                        load.run();
                    } catch (Exception ex) {
                        status.setText("删除失败：" + rootMessage(ex));
                        deleteSelected.setEnabled(table.getSelectedRow() >= 0);
                    }
                }
            }.execute();
        });
        clearRead.addActionListener(event -> {
            if (JOptionPane.showConfirmDialog(dialog, "确定永久删除全部已读通知？未读通知会保留。",
                    "清空已读", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                    != JOptionPane.YES_OPTION) return;
            clearRead.setEnabled(false);
            new SwingWorker<Integer, Void>() {
                @Override protected Integer doInBackground() { return service.clearRead(actor); }
                @Override protected void done() {
                    try {
                        int deleted = get();
                        status.setText("已删除 " + deleted + " 条已读通知");
                        if (onChanged != null) onChanged.run();
                        load.run();
                    } catch (Exception ex) {
                        status.setText("清空失败：" + rootMessage(ex));
                        clearRead.setEnabled(true);
                    }
                }
            }.execute();
        });
        close.addActionListener(event -> dialog.dispose());
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        load.run();
        dialog.setVisible(true);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
