package com.ticket.ui;

import com.ticket.exception.BusinessException;
import com.ticket.service.UserService;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import com.ticket.ui.theme.AppTheme;
import com.ticket.ui.theme.WindowIconUtil;

public class RegisterDialog extends JDialog {
    private final UserService userService = new UserService();
    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JPasswordField confirmPasswordField = new JPasswordField(20);
    private final JTextField emailField = new JTextField(20);
    private final JTextField phoneField = new JTextField(20);
    private final JButton submitButton = new JButton("提交注册");

    public RegisterDialog(java.awt.Frame owner) {
        super(owner, "注册用户", true);
        WindowIconUtil.apply(this);
        setSize(500, 430);
        setLocationRelativeTo(owner);
        AppTheme.closeOnEscape(this);
        getContentPane().setBackground(AppTheme.PAGE);
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel title = new JLabel("创建普通用户账号");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 19f));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(14, 8, 12, 8);
        add(title, gbc);
        gbc.gridwidth = 1;
        gbc.insets = new Insets(8, 8, 8, 8);

        addField("用户名", usernameField, 1, gbc);
        addField("密码", passwordField, 2, gbc);
        addField("确认密码", confirmPasswordField, 3, gbc);
        addField("邮箱", emailField, 4, gbc);
        addField("手机号", phoneField, 5, gbc);
        AppTheme.styleInput(usernameField);
        AppTheme.styleInput(passwordField);
        AppTheme.styleInput(confirmPasswordField);
        AppTheme.styleInput(emailField);
        AppTheme.styleInput(phoneField);

        JLabel passwordHint = AppTheme.muted("12–64 位，包含大小写字母、数字和特殊字符");
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        add(passwordHint, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 1;
        gbc.gridy = 7;
        add(submitButton, gbc);
        AppTheme.primary(submitButton);
        getRootPane().setDefaultButton(submitButton);
        submitButton.addActionListener(event -> doRegister());
    }

    private void addField(String label, java.awt.Component field, int row, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = row;
        add(new JLabel(label), gbc);
        gbc.gridx = 1;
        add(field, gbc);
    }

    private void doRegister() {
        char[] passwordChars = passwordField.getPassword();
        char[] confirmChars = confirmPasswordField.getPassword();
        if (!Arrays.equals(passwordChars, confirmChars)) {
            Arrays.fill(passwordChars, '\0');
            Arrays.fill(confirmChars, '\0');
            confirmPasswordField.setText("");
            JOptionPane.showMessageDialog(this, "两次输入的密码不一致", "提示", JOptionPane.WARNING_MESSAGE);
            confirmPasswordField.requestFocusInWindow();
            return;
        }
        String password = new String(passwordChars);
        Arrays.fill(passwordChars, '\0');
        Arrays.fill(confirmChars, '\0');
        submitButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                userService.register(
                    usernameField.getText(),
                    password,
                    emailField.getText(),
                    phoneField.getText()
                );
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(RegisterDialog.this, "注册成功，请返回登录");
                    passwordField.setText("");
                    confirmPasswordField.setText("");
                    dispose();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    String message = cause instanceof BusinessException ? cause.getMessage() : "注册失败";
                    JOptionPane.showMessageDialog(RegisterDialog.this, message, "提示", JOptionPane.WARNING_MESSAGE);
                } finally {
                    submitButton.setEnabled(true);
                }
            }
        }.execute();
    }
}
