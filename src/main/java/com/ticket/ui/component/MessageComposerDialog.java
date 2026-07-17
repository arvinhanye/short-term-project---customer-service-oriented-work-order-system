package com.ticket.ui.component;

import com.ticket.model.StickerCatalog;
import com.ticket.service.BusinessService;
import com.ticket.ui.theme.AppTheme;
import com.ticket.ui.theme.WindowIconUtil;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

/** Ticket message editor with inline emoji and click-or-drop file/image attachments. */
public final class MessageComposerDialog {
    private MessageComposerDialog() {
    }

    /** stickerCode remains for backward source compatibility; new emoji are stored inline in text. */
    public record Result(boolean accepted, String text, List<Path> files, String stickerCode) {
        public Result {
            files = files == null ? List.of() : List.copyOf(files);
        }
    }

    public static Result show(Component parent, String title, String prompt) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = owner instanceof Frame frame
            ? new JDialog(frame, title, true)
            : new JDialog(owner instanceof Dialog existing ? existing : null, title,
                Dialog.ModalityType.APPLICATION_MODAL);
        WindowIconUtil.apply(dialog);
        boolean[] accepted = {false};
        List<Path> selectedFiles = new ArrayList<>();
        JLabel validationLabel = new JLabel(" ");
        validationLabel.setForeground(AppTheme.DANGER);

        JTextArea textArea = new JTextArea(7, 48);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane textScroll = AppTheme.scroll(textArea);
        textScroll.setPreferredSize(new Dimension(640, 165));

        JButton emojiButton = new JButton("😊  表情");
        JButton addFileButton = new JButton("＋ 添加附件");
        AppTheme.secondary(emojiButton);
        AppTheme.secondary(addFileButton);
        JPopupMenu emojiPopup = buildEmojiPopup(textArea);
        emojiButton.addActionListener(event -> emojiPopup.show(emojiButton, 0, emojiButton.getHeight()));

        JPanel inputTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        inputTools.setOpaque(false);
        inputTools.add(emojiButton);
        inputTools.add(addFileButton);
        inputTools.add(AppTheme.muted("表情会插入当前光标位置"));
        JPanel messagePanel = section("消息", new BorderLayout(0, 2));
        messagePanel.add(textScroll, BorderLayout.CENTER);
        messagePanel.add(inputTools, BorderLayout.SOUTH);

        DefaultListModel<String> fileModel = new DefaultListModel<>();
        JList<String> fileList = new JList<>(fileModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane fileScroll = AppTheme.scroll(fileList);
        fileScroll.setPreferredSize(new Dimension(500, 78));
        JLabel dropLabel = new JLabel(
            "<html><div style='text-align:center'><b>将文件或图片拖到这里</b><br>"
                + "<span style='color:#64748b'>也可以点击添加附件，仅支持文件</span></div></html>",
            SwingConstants.CENTER);
        dropLabel.setOpaque(true);
        dropLabel.setBackground(new java.awt.Color(248, 250, 252));
        dropLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createDashedBorder(AppTheme.PRIMARY, 1f, 5f, 4f, true),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        dropLabel.setPreferredSize(new Dimension(640, 72));

        JButton removeFileButton = new JButton("移除选中附件");
        AppTheme.secondary(removeFileButton);
        removeFileButton.setEnabled(false);
        JLabel fileHint = AppTheme.muted("最多 5 个；单个 10 MB；合计 25 MB");
        JPanel fileActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        fileActions.setOpaque(false);
        fileActions.add(removeFileButton);
        fileActions.add(fileHint);

        JPanel attachmentBody = new JPanel(new BorderLayout(0, 8));
        attachmentBody.setOpaque(false);
        attachmentBody.add(dropLabel, BorderLayout.NORTH);
        attachmentBody.add(fileScroll, BorderLayout.CENTER);
        JPanel filePanel = section("附件", new BorderLayout(0, 8));
        filePanel.add(attachmentBody, BorderLayout.CENTER);
        filePanel.add(fileActions, BorderLayout.SOUTH);

        Consumer<List<File>> receiveFiles = files -> addSelectedFiles(
            files, selectedFiles, fileModel, fileHint, validationLabel);
        TransferHandler dropHandler = createFileDropHandler(receiveFiles);
        dropLabel.setTransferHandler(dropHandler);
        attachmentBody.setTransferHandler(dropHandler);
        filePanel.setTransferHandler(dropHandler);
        fileList.setTransferHandler(dropHandler);
        fileScroll.setTransferHandler(dropHandler);
        fileScroll.getViewport().setTransferHandler(dropHandler);

        addFileButton.addActionListener(event -> chooseFiles(dialog, receiveFiles));
        dropLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                addFileButton.doClick();
            }
        });
        fileList.addListSelectionListener(event -> removeFileButton.setEnabled(fileList.getSelectedIndex() >= 0));
        removeFileButton.addActionListener(event -> {
            int index = fileList.getSelectedIndex();
            if (index >= 0) {
                selectedFiles.remove(index);
                refreshFiles(selectedFiles, fileModel, fileHint);
                validationLabel.setText(" ");
            }
        });

        JPanel editor = new JPanel();
        editor.setLayout(new javax.swing.BoxLayout(editor, javax.swing.BoxLayout.Y_AXIS));
        editor.setOpaque(false);
        messagePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        filePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        editor.add(messagePanel);
        editor.add(javax.swing.Box.createVerticalStrut(10));
        editor.add(filePanel);

        JButton submitButton = new JButton("发送");
        JButton cancelButton = new JButton("取消");
        AppTheme.primary(submitButton);
        AppTheme.secondary(cancelButton);
        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        actions.add(validationLabel, BorderLayout.CENTER);
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionButtons.setOpaque(false);
        actionButtons.add(cancelButton);
        actionButtons.add(submitButton);
        actions.add(actionButtons, BorderLayout.EAST);

        Runnable submit = () -> {
            if (textArea.getText().isBlank() && selectedFiles.isEmpty()) {
                validationLabel.setText("请输入消息或添加附件");
                return;
            }
            accepted[0] = true;
            dialog.dispose();
        };
        submitButton.addActionListener(event -> submit.run());
        cancelButton.addActionListener(event -> dialog.dispose());
        AppTheme.submitOnEnter(textArea, submit);

        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(java.awt.Font.BOLD, 19f));
        header.add(titleLabel, BorderLayout.NORTH);
        header.add(AppTheme.muted(prompt + "（Enter 发送，Shift+Enter 换行）"), BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(AppTheme.PAGE);
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 14, 16));
        content.add(header, BorderLayout.NORTH);
        content.add(editor, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        AppTheme.closeOnEscape(dialog);
        dialog.getRootPane().setDefaultButton(submitButton);
        dialog.setSize(new Dimension(720, 610));
        dialog.setMinimumSize(new Dimension(660, 570));
        dialog.setLocationRelativeTo(parent);
        SwingUtilities.invokeLater(textArea::requestFocusInWindow);
        dialog.setVisible(true);
        return new Result(accepted[0], textArea.getText(), selectedFiles, null);
    }

    private static JPopupMenu buildEmojiPopup(JTextArea textArea) {
        JPopupMenu popup = new JPopupMenu();
        JPanel grid = new JPanel(new GridLayout(4, 4, 4, 4));
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (StickerCatalog.Sticker sticker : StickerCatalog.all()) {
            JButton button = new JButton(sticker.emoji());
            button.setToolTipText(sticker.label());
            button.setFont(button.getFont().deriveFont(21f));
            button.setPreferredSize(new Dimension(46, 42));
            button.setFocusable(false);
            button.addActionListener(event -> {
                int caret = textArea.getCaretPosition();
                textArea.insert(sticker.emoji(), caret);
                textArea.setCaretPosition(caret + sticker.emoji().length());
                popup.setVisible(false);
                textArea.requestFocusInWindow();
            });
            grid.add(button);
        }
        popup.add(grid);
        return popup;
    }

    private static void chooseFiles(Component parent, Consumer<List<File>> receiveFiles) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        FileDialog chooser;
        if (owner instanceof Frame frame) {
            chooser = new FileDialog(frame, "选择文件或图片", FileDialog.LOAD);
        } else if (owner instanceof Dialog dialog) {
            chooser = new FileDialog(dialog, "选择文件或图片", FileDialog.LOAD);
        } else {
            chooser = new FileDialog((Frame) null, "选择文件或图片", FileDialog.LOAD);
        }
        chooser.setMultipleMode(true);
        chooser.setVisible(true);
        File[] selected = chooser.getFiles();
        chooser.dispose();
        if (selected != null && selected.length > 0) {
            receiveFiles.accept(List.of(selected));
        }
    }

    private static TransferHandler createFileDropHandler(Consumer<List<File>> receiveFiles) {
        return new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    Object value = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    receiveFiles.accept((List<File>) value);
                    return true;
                } catch (Exception ignored) {
                    return false;
                }
            }
        };
    }

    private static void addSelectedFiles(List<File> incoming, List<Path> selectedFiles,
                                         DefaultListModel<String> model, JLabel hint, JLabel validation) {
        String error = null;
        long totalBytes = totalSize(selectedFiles);
        for (File file : incoming) {
            Path path = file.toPath().toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                error = "暂不支持文件夹，请拖入具体文件";
                continue;
            }
            if (!Files.isReadable(path)) {
                error = "文件无法读取：" + path.getFileName();
                continue;
            }
            if (selectedFiles.contains(path)) {
                continue;
            }
            long size = safeSize(path);
            if (size <= 0) {
                error = "不能添加空文件：" + path.getFileName();
                continue;
            }
            if (size > BusinessService.MAX_ATTACHMENT_BYTES) {
                error = "单个附件不能超过 10 MB：" + path.getFileName();
                continue;
            }
            if (selectedFiles.size() >= BusinessService.MAX_ATTACHMENTS_PER_MESSAGE) {
                error = "单条消息最多添加 5 个附件";
                break;
            }
            if (totalBytes + size > BusinessService.MAX_TOTAL_ATTACHMENT_BYTES) {
                error = "附件合计不能超过 25 MB";
                continue;
            }
            selectedFiles.add(path);
            totalBytes += size;
        }
        refreshFiles(selectedFiles, model, hint);
        validation.setText(error == null ? " " : error);
    }

    private static JPanel section(String title, java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
    }

    private static void refreshFiles(List<Path> files, DefaultListModel<String> model, JLabel hint) {
        model.clear();
        long total = 0L;
        for (Path file : files) {
            long size = safeSize(file);
            total += size;
            model.addElement(fileKind(file) + "  " + file.getFileName() + "  ·  " + formatSize(size));
        }
        hint.setText(files.isEmpty() ? "最多 5 个；单个 10 MB；合计 25 MB"
            : files.size() + " 个附件，合计 " + formatSize(total));
    }

    private static String fileKind(Path path) {
        try {
            String type = Files.probeContentType(path);
            return type != null && type.startsWith("image/") ? "[图片]" : "[文件]";
        } catch (Exception ignored) {
            return "[文件]";
        }
    }

    private static long totalSize(List<Path> paths) {
        long total = 0L;
        for (Path path : paths) {
            total += safeSize(path);
        }
        return total;
    }

    private static long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ignored) {
            return 0L;
        }
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
}
