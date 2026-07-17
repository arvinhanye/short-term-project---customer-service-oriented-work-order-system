package com.ticket.ui.admin;

import com.ticket.model.ServiceMetrics;
import com.ticket.model.User;
import com.ticket.service.ReportExportService;
import com.ticket.service.StatisticsService;
import com.ticket.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import org.bson.Document;

public final class ServiceReportDialog extends JDialog {
    private final User actor;
    private final StatisticsService statisticsService;
    private final ReportExportService exportService = new ReportExportService();
    private final JTextField fromField = new JTextField(LocalDate.now().minusDays(30).toString(), 10);
    private final JTextField toField = new JTextField(LocalDate.now().plusDays(1).toString(), 10);
    private final JPanel metrics = new JPanel(new GridLayout(3, 3, 8, 8));
    private final DefaultTableModel loadModel = new DefaultTableModel(
        new Object[]{"管理员", "累计分配", "当前积压", "SLA 超时", "平均积压小时"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JLabel status = AppTheme.muted("待加载");
    private ServiceMetrics currentMetrics;
    private List<Document> currentLoads = List.of();
    private List<Document> currentTrend = List.of();

    private ServiceReportDialog(Component parent, User actor, StatisticsService statisticsService) {
        super(SwingUtilities.getWindowAncestor(parent), "服务质量报表与导出", ModalityType.APPLICATION_MODAL);
        this.actor = actor;
        this.statisticsService = statisticsService;
        setLayout(new BorderLayout(0, 10));
        setPreferredSize(new Dimension(980, 680));
        JPanel toolbar = AppTheme.surface(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton refresh = new JButton("刷新");
        JButton csv = new JButton("导出 CSV");
        JButton xlsx = new JButton("导出 Excel");
        JButton pdf = new JButton("导出 PDF");
        AppTheme.primary(refresh); AppTheme.secondary(csv); AppTheme.secondary(xlsx); AppTheme.secondary(pdf);
        toolbar.add(new JLabel("开始")); toolbar.add(fromField); toolbar.add(new JLabel("结束（不含）")); toolbar.add(toField);
        toolbar.add(refresh); toolbar.add(csv); toolbar.add(xlsx); toolbar.add(pdf); toolbar.add(status);
        add(toolbar, BorderLayout.NORTH);
        metrics.setOpaque(false);
        JTable loadTable = new JTable(loadModel);
        AppTheme.styleTable(loadTable);
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(metrics, BorderLayout.NORTH);
        JPanel loadCard = AppTheme.surface(new BorderLayout(0, 6));
        loadCard.add(new JLabel("管理员负载"), BorderLayout.NORTH);
        loadCard.add(AppTheme.scroll(loadTable), BorderLayout.CENTER);
        body.add(loadCard, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        refresh.addActionListener(event -> load());
        csv.addActionListener(event -> export("csv"));
        xlsx.addActionListener(event -> export("xlsx"));
        pdf.addActionListener(event -> export("pdf"));
        pack();
        setLocationRelativeTo(parent);
        load();
    }

    public static void show(Component parent, User actor, StatisticsService statisticsService) {
        new ServiceReportDialog(parent, actor, statisticsService).setVisible(true);
    }

    private void load() {
        java.time.LocalDateTime from;
        java.time.LocalDateTime to;
        try {
            from = LocalDate.parse(fromField.getText().trim()).atStartOfDay();
            to = LocalDate.parse(toField.getText().trim()).atStartOfDay();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "日期格式应为 yyyy-MM-dd", "日期错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        status.setText("加载中…");
        new SwingWorker<Snapshot, Void>() {
            @Override protected Snapshot doInBackground() {
                return new Snapshot(statisticsService.serviceMetrics(actor, from, to),
                    statisticsService.administratorLoad(actor), statisticsService.satisfactionTrend(actor, 30));
            }
            @Override protected void done() {
                try {
                    Snapshot snapshot = get();
                    currentMetrics = snapshot.metrics(); currentLoads = snapshot.loads(); currentTrend = snapshot.trend();
                    render(); status.setText("已更新");
                } catch (Exception ex) {
                    status.setText("加载失败");
                    JOptionPane.showMessageDialog(ServiceReportDialog.this, root(ex), "加载失败", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void render() {
        metrics.removeAll();
        addMetric("工单总量", currentMetrics.ticketCount());
        addMetric("首次响应（分钟）", format(currentMetrics.averageFirstResponseMinutes()));
        addMetric("解决时间（分钟）", format(currentMetrics.averageResolutionMinutes()));
        addMetric("SLA 达标率", format(currentMetrics.slaComplianceRate()) + "%");
        addMetric("平均积压（小时）", format(currentMetrics.averageBacklogHours()));
        addMetric("一次解决率", format(currentMetrics.firstContactResolutionRate()) + "%");
        addMetric("平均满意度", format(currentMetrics.averageSatisfaction()));
        addMetric("当前积压", currentMetrics.openBacklog());
        addMetric("SLA 超时", currentMetrics.breachedCount());
        loadModel.setRowCount(0);
        for (Document load : currentLoads) loadModel.addRow(new Object[]{load.getString("username"),
            load.get("total_assigned"), load.get("open_count"), load.get("breached_count"),
            format(((Number) load.get("avg_backlog_hours")).doubleValue())});
        metrics.revalidate(); metrics.repaint();
    }

    private void addMetric(String name, Object value) {
        JPanel card = AppTheme.surface(new BorderLayout(0, 4));
        card.add(AppTheme.muted(name), BorderLayout.NORTH);
        JLabel label = new JLabel(String.valueOf(value));
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD, 20f));
        card.add(label, BorderLayout.CENTER);
        metrics.add(card);
    }

    private void export(String extension) {
        if (currentMetrics == null) {
            JOptionPane.showMessageDialog(this, "请先加载报表。", "导出", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Path target = chooseExportTarget(extension);
        if (target == null) return;
        try {
            exportService.export(target, currentMetrics, currentLoads, currentTrend);
            JOptionPane.showMessageDialog(this, "报表已导出：\n" + target.toAbsolutePath(), "导出成功",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "导出失败", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** AWT FileDialog delegates to the native macOS save panel, including overwrite confirmation. */
    private Path chooseExportTarget(String extension) {
        String normalizedExtension = extension == null ? "" : extension.trim().toLowerCase(java.util.Locale.ROOT);
        String formatName = switch (normalizedExtension) {
            case "xlsx" -> "Excel";
            case "pdf" -> "PDF";
            default -> "CSV";
        };
        FileDialog chooser = new FileDialog(this, "导出" + formatName + "报表", FileDialog.SAVE);
        chooser.setMultipleMode(false);
        chooser.setFile("service-report-" + LocalDate.now() + "." + normalizedExtension);
        chooser.setFilenameFilter((directory, name) -> name == null
            || name.toLowerCase(java.util.Locale.ROOT).endsWith("." + normalizedExtension));
        chooser.setVisible(true);
        String selectedName = chooser.getFile();
        String selectedDirectory = chooser.getDirectory();
        chooser.dispose();
        if (selectedName == null || selectedName.isBlank()) return null;
        Path target = selectedDirectory == null || selectedDirectory.isBlank()
            ? Path.of(selectedName) : Path.of(selectedDirectory, selectedName);
        if (!target.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                .endsWith("." + normalizedExtension)) {
            target = target.resolveSibling(target.getFileName() + "." + normalizedExtension);
        }
        return target;
    }

    private String format(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
    private String root(Throwable value) { Throwable current = value; while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage(); }
    private record Snapshot(ServiceMetrics metrics, List<Document> loads, List<Document> trend) { }
}
