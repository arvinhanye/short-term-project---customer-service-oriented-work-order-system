package com.ticket.ui.component;

import com.ticket.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/** Modal multiline editor where Enter submits and Shift+Enter inserts a line break. */
public final class TextEntryDialog {
    private TextEntryDialog() {
    }

    public record Result(boolean accepted, String text) {
    }

    public static Result show(Component parent, String title, String prompt, JComponent header, int rows, int columns) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = owner instanceof Frame frame
            ? new JDialog(frame, title, true)
            : new JDialog(owner instanceof Dialog existing ? existing : null, title, Dialog.ModalityType.APPLICATION_MODAL);
        boolean[] accepted = {false};
        JTextArea textArea = new JTextArea(rows, columns);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton submitButton = new JButton("提交");
        JButton cancelButton = new JButton("取消");
        AppTheme.primary(submitButton);
        AppTheme.secondary(cancelButton);
        Runnable submit = () -> {
            accepted[0] = true;
            dialog.dispose();
        };
        submitButton.addActionListener(event -> submit.run());
        cancelButton.addActionListener(event -> dialog.dispose());
        AppTheme.submitOnEnter(textArea, submit);

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        if (prompt != null && !prompt.isBlank()) {
            top.add(new JLabel(prompt + "（Enter 提交，Shift+Enter 换行）"), BorderLayout.NORTH);
        }
        if (header != null) {
            top.add(header, BorderLayout.CENTER);
        }
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancelButton);
        actions.add(submitButton);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(AppTheme.PAGE);
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        content.add(top, BorderLayout.NORTH);
        content.add(AppTheme.scroll(textArea), BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        AppTheme.closeOnEscape(dialog);
        dialog.getRootPane().setDefaultButton(submitButton);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(520, 330));
        dialog.setLocationRelativeTo(parent);
        SwingUtilities.invokeLater(textArea::requestFocusInWindow);
        dialog.setVisible(true);
        return new Result(accepted[0], textArea.getText());
    }
}
