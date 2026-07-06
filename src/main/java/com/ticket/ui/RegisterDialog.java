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

public class RegisterDialog extends JDialog {
    private final UserService userService = new UserService();
    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JTextField emailField = new JTextField(20);
    private final JTextField phoneField = new JTextField(20);
    private final JButton submitButton = new JButton("提交注册");

    public RegisterDialog(java.awt.Frame owner) {
        super(owner, "注册用户", true);
        setSize(420, 280);
        setLocationRelativeTo(owner);
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        addField("用户名", usernameField, 0, gbc);
        addField("密码", passwordField, 1, gbc);
        addField("邮箱", emailField, 2, gbc);
        addField("手机号", phoneField, 3, gbc);

        gbc.gridx = 1;
        gbc.gridy = 4;
        add(submitButton, gbc);
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
