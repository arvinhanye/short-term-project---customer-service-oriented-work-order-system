package com.ticket.ui.component;

import com.ticket.model.Comment;
import com.ticket.model.TicketAttachment;
import com.ticket.model.User;
import com.ticket.service.BusinessService;
import com.ticket.ui.theme.AppTheme;
import com.ticket.ui.theme.WindowIconUtil;
import com.ticket.util.TimeFormatUtil;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/** Lists visible ticket attachments and securely downloads them through the service layer. */
public final class TicketAttachmentDialog {
    private TicketAttachmentDialog() {
    }

    public static void show(Component parent, User actor, Long itemId, List<Comment> comments,
                            BusinessService businessService) {
        List<Entry> entries = flatten(comments);
        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "当前沟通记录中没有附件。", "工单附件",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "工单附件 #" + itemId,
            java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        WindowIconUtil.apply(dialog);
        AppTheme.closeOnEscape(dialog);

        DefaultListModel<Entry> model = new DefaultListModel<>();
        entries.forEach(model::addElement);
        JList<Entry> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setVisibleRowCount(9);

        JButton previewButton = new JButton("预览 / 打开");
        JButton saveButton = new JButton("另存为");
        JButton closeButton = new JButton("关闭");
        AppTheme.primary(previewButton);
        AppTheme.secondary(saveButton);
        AppTheme.secondary(closeButton);
        JLabel status = AppTheme.muted("共 " + entries.size() + " 个附件；打开前会再次校验访问权限");

        previewButton.addActionListener(event -> {
            Entry entry = list.getSelectedValue();
            if (entry != null) {
                downloadForPreview(dialog, actor, itemId, entry.attachment(), businessService,
                    previewButton, saveButton, status);
            }
        });
        saveButton.addActionListener(event -> {
            Entry entry = list.getSelectedValue();
            if (entry != null) {
                chooseAndSave(dialog, actor, itemId, entry.attachment(), businessService,
                    previewButton, saveButton, status);
            }
        });
        closeButton.addActionListener(event -> dialog.dispose());
        list.addListSelectionListener(event -> {
            boolean selected = list.getSelectedIndex() >= 0;
            previewButton.setEnabled(selected);
            saveButton.setEnabled(selected);
        });

        JPanel header = AppTheme.pageHeader("工单附件", "图片可直接预览，其他文件使用系统默认应用打开", status);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setBackground(AppTheme.SURFACE);
        actions.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        actions.add(closeButton);
        actions.add(saveButton);
        actions.add(previewButton);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(AppTheme.PAGE);
        dialog.add(header, BorderLayout.NORTH);
        dialog.add(AppTheme.scroll(list), BorderLayout.CENTER);
        dialog.add(actions, BorderLayout.SOUTH);
        dialog.setSize(new Dimension(720, 430));
        dialog.setMinimumSize(new Dimension(600, 360));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static void downloadForPreview(Component parent, User actor, Long itemId,
                                           TicketAttachment attachment, BusinessService service,
                                           JButton previewButton, JButton saveButton, JLabel status) {
        setBusy(previewButton, saveButton, status, true, "正在安全下载…");
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                String suffix = suffixOf(attachment.getFileName());
                Path target = Files.createTempFile("ticket-attachment-", suffix);
                target.toFile().deleteOnExit();
                service.downloadAttachment(actor, itemId, attachment.getFileId(), target);
                return target;
            }

            @Override
            protected void done() {
                setBusy(previewButton, saveButton, status, false, "下载完成");
                try {
                    Path downloaded = get();
                    if (attachment.isImage()) {
                        showImage(parent, downloaded, attachment);
                    } else if (Desktop.isDesktopSupported()
                            && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                        Desktop.getDesktop().open(downloaded.toFile());
                    } else {
                        JOptionPane.showMessageDialog(parent, "系统不支持直接打开，请使用“另存为”。",
                            "无法打开", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent, "打开附件失败：" + rootMessage(ex),
                        "附件错误", JOptionPane.WARNING_MESSAGE);
                    status.setText("附件打开失败");
                }
            }
        }.execute();
    }

    private static void chooseAndSave(Component parent, User actor, Long itemId,
                                      TicketAttachment attachment, BusinessService service,
                                      JButton previewButton, JButton saveButton, JLabel status) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("保存附件");
        chooser.setSelectedFile(new File(safeFileName(attachment.getFileName())));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path destination = chooser.getSelectedFile().toPath();
        if (Files.exists(destination)) {
            int confirm = JOptionPane.showConfirmDialog(parent, "文件已存在，是否覆盖？", "确认覆盖",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }
        setBusy(previewButton, saveButton, status, true, "正在保存…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                service.downloadAttachment(actor, itemId, attachment.getFileId(), destination);
                return null;
            }

            @Override
            protected void done() {
                setBusy(previewButton, saveButton, status, false, "附件已保存到 " + destination);
                try {
                    get();
                    JOptionPane.showMessageDialog(parent, "附件已保存。", "保存成功",
                        JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent, "保存附件失败：" + rootMessage(ex),
                        "附件错误", JOptionPane.WARNING_MESSAGE);
                    status.setText("附件保存失败");
                }
            }
        }.execute();
    }

    private static void showImage(Component parent, Path path, TicketAttachment attachment) throws Exception {
        BufferedImage source = ImageIO.read(path.toFile());
        if (source == null) {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile());
                return;
            }
            throw new IllegalArgumentException("无法识别图片格式");
        }
        double ratio = Math.min(1d, Math.min(900d / source.getWidth(), 620d / source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        Image rendered = ratio < 1d
            ? source.getScaledInstance(width, height, Image.SCALE_SMOOTH) : source;
        JLabel imageLabel = new JLabel(new ImageIcon(rendered));
        imageLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane scrollPane = new JScrollPane(imageLabel);
        scrollPane.setPreferredSize(new Dimension(Math.min(940, width + 30), Math.min(680, height + 30)));
        JOptionPane.showMessageDialog(parent, scrollPane,
            attachment.getFileName() + " · " + formatSize(attachment.getSize()), JOptionPane.PLAIN_MESSAGE);
    }

    private static void setBusy(JButton previewButton, JButton saveButton, JLabel status,
                                boolean busy, String message) {
        previewButton.setEnabled(!busy);
        saveButton.setEnabled(!busy);
        status.setText(message);
    }

    private static List<Entry> flatten(List<Comment> comments) {
        List<Entry> entries = new ArrayList<>();
        if (comments == null) {
            return entries;
        }
        for (Comment comment : comments) {
            if (comment.getAttachments() == null) {
                continue;
            }
            for (TicketAttachment attachment : comment.getAttachments()) {
                entries.add(new Entry(comment, attachment));
            }
        }
        return entries;
    }

    private static String suffixOf(String fileName) {
        String safe = safeFileName(fileName);
        int dot = safe.lastIndexOf('.');
        return dot >= 0 && dot < safe.length() - 1 ? safe.substring(dot) : ".bin";
    }

    private static String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "attachment";
        }
        return new File(fileName).getName().replaceAll("[\\p{Cntrl}]", "_");
    }

    private static String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "未知错误" : current.getMessage();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024) {
            return String.format("%.1f KB", bytes / 1024d);
        }
        return String.format("%.1f MB", bytes / (1024d * 1024d));
    }

    private record Entry(Comment comment, TicketAttachment attachment) {
        @Override
        public String toString() {
            String kind = attachment.isImage() ? "图片" : "文件";
            return "[" + kind + "] " + safeFileName(attachment.getFileName())
                + "  ·  " + formatSize(attachment.getSize())
                + "  ·  用户 " + comment.getUserId()
                + "  ·  " + TimeFormatUtil.format(comment.getCreatedAt());
        }
    }
}
