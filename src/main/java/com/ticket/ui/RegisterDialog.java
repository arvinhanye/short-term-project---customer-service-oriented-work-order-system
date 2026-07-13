package com.ticket.ui;

import com.ticket.exception.BusinessException;
import com.ticket.service.UserService;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import com.ticket.ui.theme.AppTheme;

public class RegisterDialog extends JDialog {
    private final UserService userService = new UserService();
    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JTextField emailField = new JTextField(20);
    private final JTextField phoneField = new JTextField(20);
    private final JButton submitButton = new JButton("提交注册");

    public RegisterDialog(java.awt.Frame owner) {
        super(owner, "注册用户", true);
        setSize(450, 320);
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
        addField("邮箱", emailField, 3, gbc);
        addField("手机号", phoneField, 4, gbc);
        AppTheme.styleInput(usernameField);
        AppTheme.styleInput(passwordField);
        AppTheme.styleInput(emailField);
        AppTheme.styleInput(phoneField);

        gbc.gridx = 1;
        gbc.gridy = 5;
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
        submitButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                userService.register(
                    usernameField.getText(),
                    new String(passwordField.getPassword()),
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
