package com.ticket.ui.admin;

import com.ticket.model.User;
import com.ticket.service.DataLifecycleService;
import com.ticket.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

public class DataLifecyclePanel extends JPanel {
    private final User actor;
    private final DataLifecycleService service = new DataLifecycleService();
    private final JLabel status = AppTheme.muted("所有操作都会写入 data_lifecycle_runs 审计表");

    public DataLifecyclePanel(User actor) {
        this.actor = actor;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.PAGE);
        add(AppTheme.pageHeader("数据生命周期与恢复演练", "归档日志、清理孤儿附件并验证备份可恢复性"), BorderLayout.NORTH);
        JPanel actions = AppTheme.surface(new FlowLayout(FlowLayout.LEFT, 10, 8));
        JSpinner days = new JSpinner(new SpinnerNumberModel(180, 30, 3650, 30));
        JButton archive = new JButton("归档过期日志");
        JButton cleanup = new JButton("清理孤儿附件");
        JButton verify = new JButton("验证 SQL 备份");
        AppTheme.primary(archive); AppTheme.secondary(cleanup); AppTheme.secondary(verify);
        actions.add(new JLabel("日志保留天数")); actions.add(days); actions.add(archive); actions.add(cleanup); actions.add(verify);
        add(actions, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        archive.addActionListener(event -> {
            if (!confirm("将早于保留期的日志复制到归档集合并从在线集合删除，是否继续？")) return;
            run("日志归档", () -> service.archiveLogs(actor, (Integer) days.getValue()) + " 条日志已归档");
        });
        cleanup.addActionListener(event -> {
            if (!confirm("将永久删除没有任何评论引用的 GridFS 文件，是否继续？")) return;
            run("附件清理", () -> service.cleanOrphanAttachments(actor) + " 个孤儿附件已删除");
        });
        verify.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            run("备份验证", () -> "校验通过，SHA-256：" + service.verifyBackupArtifact(actor,
                chooser.getSelectedFile().toPath()));
        });
    }

    private boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(this, message, "敏感操作确认", JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private void run(String title, Task task) {
        try {
            status.setText(title + "执行中…");
            String result = task.run();
            status.setText(result);
            JOptionPane.showMessageDialog(this, result, title, JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            status.setText(title + "失败：" + ex.getMessage());
            JOptionPane.showMessageDialog(this, ex.getMessage(), title + "失败", JOptionPane.WARNING_MESSAGE);
        }
    }

    @FunctionalInterface private interface Task { String run(); }
}
