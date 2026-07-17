package com.ticket.ui.component;

import com.ticket.ui.theme.AppTheme;
import com.ticket.ui.theme.WindowIconUtil;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/** Centered, themed read-only content dialog used instead of platform JOptionPane layouts. */
public final class ContentViewerDialog {
    private ContentViewerDialog() {
    }

    public static void show(Component parent, String title, String heading, String body) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = owner instanceof Frame frame
            ? new JDialog(frame, title, true)
            : new JDialog(owner instanceof Dialog existing ? existing : null,
                title, Dialog.ModalityType.APPLICATION_MODAL);
        WindowIconUtil.apply(dialog);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JLabel headingLabel = new JLabel(heading);
        headingLabel.setFont(headingLabel.getFont().deriveFont(Font.BOLD, 20f));
        JTextArea content = new JTextArea(body == null ? "" : body);
        content.setEditable(false);
        content.setLineWrap(true);
        content.setWrapStyleWord(true);
        content.setCaretPosition(0);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JButton close = new JButton("关闭");
        AppTheme.primary(close);
        close.addActionListener(event -> dialog.dispose());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        actions.add(close);

        JPanel contentPanel = new JPanel(new BorderLayout(0, 14));
        contentPanel.setBackground(AppTheme.PAGE);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(18, 18, 16, 18));
        contentPanel.add(headingLabel, BorderLayout.NORTH);
        contentPanel.add(AppTheme.scroll(content), BorderLayout.CENTER);
        contentPanel.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(contentPanel);
        AppTheme.closeOnEscape(dialog);
        dialog.getRootPane().setDefaultButton(close);
        dialog.setSize(new Dimension(720, 520));
        dialog.setMinimumSize(new Dimension(560, 400));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}
