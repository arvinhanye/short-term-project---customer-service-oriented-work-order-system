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
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Compact, themed modal for selecting one business option. */
public final class ChoiceDialog {
    private ChoiceDialog() {
    }

    public record Result<T>(boolean accepted, T value) {
    }

    public static <T> Result<T> show(Component parent, String title, String heading, String description,
                                     String fieldLabel, JComboBox<T> choiceBox, String confirmText) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = owner instanceof Frame frame
            ? new JDialog(frame, title, true)
            : new JDialog(owner instanceof Dialog existing ? existing : null,
                title, Dialog.ModalityType.APPLICATION_MODAL);
        WindowIconUtil.apply(dialog);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        boolean[] accepted = {false};
        AppTheme.styleComboBox(choiceBox);
        choiceBox.setMaximumRowCount(8);
        choiceBox.setPreferredSize(new Dimension(390, 38));

        JLabel headingLabel = new JLabel(heading);
        headingLabel.setFont(headingLabel.getFont().deriveFont(Font.BOLD, 19f));
        JLabel descriptionLabel = AppTheme.muted(description);

        JPanel introduction = new JPanel(new BorderLayout(0, 5));
        introduction.setOpaque(false);
        introduction.add(headingLabel, BorderLayout.NORTH);
        introduction.add(descriptionLabel, BorderLayout.CENTER);

        JLabel fieldLabelComponent = new JLabel(fieldLabel);
        fieldLabelComponent.setFont(fieldLabelComponent.getFont().deriveFont(Font.BOLD, 13f));
        JPanel fieldCard = AppTheme.surface(new BorderLayout(0, 8));
        fieldCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AppTheme.BORDER),
            BorderFactory.createEmptyBorder(13, 14, 14, 14)));
        fieldCard.add(fieldLabelComponent, BorderLayout.NORTH);
        fieldCard.add(choiceBox, BorderLayout.CENTER);

        JButton cancelButton = new JButton("取消");
        JButton confirmButton = new JButton(confirmText == null || confirmText.isBlank() ? "确认" : confirmText);
        AppTheme.secondary(cancelButton);
        AppTheme.primary(confirmButton);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancelButton);
        actions.add(confirmButton);

        Runnable confirm = () -> {
            if (choiceBox.getSelectedItem() == null) {
                return;
            }
            accepted[0] = true;
            dialog.dispose();
        };
        cancelButton.addActionListener(event -> dialog.dispose());
        confirmButton.addActionListener(event -> confirm.run());
        choiceBox.addActionListener(event -> confirmButton.setEnabled(choiceBox.getSelectedItem() != null));
        confirmButton.setEnabled(choiceBox.getSelectedItem() != null);

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(AppTheme.PAGE);
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 16, 18));
        content.add(introduction, BorderLayout.NORTH);
        content.add(fieldCard, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        AppTheme.closeOnEscape(dialog);
        dialog.getRootPane().setDefaultButton(confirmButton);
        dialog.pack();
        dialog.setSize(new Dimension(480, 260));
        dialog.setLocationRelativeTo(parent);
        SwingUtilities.invokeLater(choiceBox::requestFocusInWindow);
        dialog.setVisible(true);

        @SuppressWarnings("unchecked")
        T selected = accepted[0] ? (T) choiceBox.getSelectedItem() : null;
        return new Result<>(accepted[0], selected);
    }
}
