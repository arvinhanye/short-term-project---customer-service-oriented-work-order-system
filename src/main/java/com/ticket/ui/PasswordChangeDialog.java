package com.ticket.ui;

import com.ticket.exception.BusinessException;
import com.ticket.model.User;
import com.ticket.service.UserService;
import com.ticket.ui.theme.AppTheme;
import com.ticket.ui.theme.WindowIconUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

/** 修改密码窗口；强制换密模式下，修改成功前不会进入业务工作台。 */
public class PasswordChangeDialog extends JDialog {
    private final UserService userService = new UserService();
    private final User user;
    private final boolean required;
    private final JPasswordField currentPasswordField = new JPasswordField(22);
    private final JPasswordField newPasswordField = new JPasswordField(22);
    private final JPasswordField confirmPasswordField = new JPasswordField(22);
    private final JButton submitButton = new JButton("确认修改");
    private final JButton cancelButton = new JButton("取消");
    private boolean changed;

    public PasswordChangeDialog(Frame owner, User user, boolean required) {
        super(owner, required ? "首次登录修改密码" : "修改密码", true);
        this.user = user;
        this.required = required;
        WindowIconUtil.apply(this);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(500, 430));
        setSize(540, 470);
        setLocationRelativeTo(owner);
        AppTheme.closeOnEscape(this);
        buildContent();
    }

    public boolean showDialog() {
        setVisible(true);
        return changed;
    }

    private void buildContent() {
        getContentPane().setBackground(AppTheme.PAGE);
        setLayout(new BorderLayout(0, 12));
        String subtitle = required
            ? "当前账号使用临时或初始密码，修改成功后才能进入系统"
            : "验证当前密码后设置新密码";
        add(AppTheme.pageHeader(required ? "必须修改密码" : "修改登录密码", subtitle), BorderLayout.NORTH);

        JPanel formCard = AppTheme.surface(new GridBagLayout());
        formCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AppTheme.BORDER),
            BorderFactory.createEmptyBorder(18, 20, 18, 20)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(7, 7, 7, 7);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        addField(formCard, "当前密码", currentPasswordField, 0, gbc);
        addField(formCard, "新密码", newPasswordField, 1, gbc);
        addField(formCard, "确认新密码", confirmPasswordField, 2, gbc);
        AppTheme.styleInput(currentPasswordField);
        AppTheme.styleInput(newPasswordField);
        AppTheme.styleInput(confirmPasswordField);

        JTextArea rules = new JTextArea(
            "密码要求：12–64 位，包含大小写字母、数字和特殊字符；不能使用常见密码、系统名称或账号信息；新密码不能与当前密码相同。"
        );
        rules.setEditable(false);
        rules.setLineWrap(true);
        rules.setWrapStyleWord(true);
        rules.setOpaque(false);
        rules.setForeground(AppTheme.MUTED);
        rules.setFocusable(false);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.insets = new Insets(14, 7, 7, 7);
        formCard.add(rules, gbc);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        body.add(formCard, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        AppTheme.secondary(cancelButton);
        AppTheme.primary(submitButton);
        actions.add(cancelButton);
        actions.add(submitButton);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 16, 14, 16));
        footer.add(actions, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        cancelButton.addActionListener(event -> dispose());
        submitButton.addActionListener(event -> changePassword());
        getRootPane().setDefaultButton(submitButton);
    }

    private void addField(JPanel panel, String label, JPasswordField field, int row, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }

    private void changePassword() {
        char[] currentChars = currentPasswordField.getPassword();
        char[] newChars = newPasswordField.getPassword();
        char[] confirmChars = confirmPasswordField.getPassword();
        if (!Arrays.equals(newChars, confirmChars)) {
            clearChars(currentChars, newChars, confirmChars);
            confirmPasswordField.setText("");
            JOptionPane.showMessageDialog(this, "两次输入的新密码不一致", "提示", JOptionPane.WARNING_MESSAGE);
            confirmPasswordField.requestFocusInWindow();
            return;
        }
        String currentPassword = new String(currentChars);
        String newPassword = new String(newChars);
        clearChars(currentChars, newChars, confirmChars);
        setBusy(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                userService.changePassword(user, currentPassword, newPassword);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    changed = true;
                    currentPasswordField.setText("");
                    newPasswordField.setText("");
                    confirmPasswordField.setText("");
                    JOptionPane.showMessageDialog(PasswordChangeDialog.this,
                        required ? "密码已修改，现在可以进入系统。" : "密码修改成功。",
                        "修改成功", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    String message = cause instanceof BusinessException ? cause.getMessage() : "密码修改失败，请稍后重试";
                    JOptionPane.showMessageDialog(PasswordChangeDialog.this, message, "提示", JOptionPane.WARNING_MESSAGE);
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        submitButton.setEnabled(!busy);
        cancelButton.setEnabled(!busy);
        currentPasswordField.setEnabled(!busy);
        newPasswordField.setEnabled(!busy);
        confirmPasswordField.setEnabled(!busy);
        submitButton.setText(busy ? "正在修改…" : "确认修改");
    }

    private static void clearChars(char[]... values) {
        for (char[] value : values) {
            if (value != null) {
                Arrays.fill(value, '\0');
            }
        }
    }
}
