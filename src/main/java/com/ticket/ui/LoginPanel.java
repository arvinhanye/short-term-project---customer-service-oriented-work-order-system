package com.ticket.ui;

import com.ticket.exception.BusinessException;
import com.ticket.model.User;
import com.ticket.service.UserService;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;

public class LoginPanel extends JPanel {
    private final MainFrame mainFrame;
    private final UserService userService = new UserService();
    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton loginButton = new JButton("登录");
    private final JButton registerButton = new JButton("注册");

    public LoginPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("用户名"), gbc);
        gbc.gridx = 1;
        formPanel.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(new JLabel("密码"), gbc);
        gbc.gridx = 1;
        formPanel.add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        formPanel.add(loginButton, gbc);
        gbc.gridx = 1;
        formPanel.add(registerButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        formPanel.add(statusLabel, gbc);

        add(formPanel, BorderLayout.CENTER);
        loginButton.addActionListener(event -> doLogin());
        registerButton.addActionListener(event -> new RegisterDialog(mainFrame).setVisible(true));
    }

    private void doLogin() {
        setLoading(true, "登录中...");
        new SwingWorker<User, Void>() {
            @Override
            protected User doInBackground() {
                return userService.login(usernameField.getText(), new String(passwordField.getPassword()));
            }

            @Override
            protected void done() {
                try {
                    mainFrame.onLoginSuccess(get());
                    statusLabel.setText("登录成功");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    String message = cause instanceof BusinessException ? cause.getMessage() : "登录失败，请稍后重试";
                    statusLabel.setText(message);
                    JOptionPane.showMessageDialog(LoginPanel.this, message, "提示", JOptionPane.WARNING_MESSAGE);
                } finally {
                    setLoading(false, " ");
                }
            }
        }.execute();
    }

    private void setLoading(boolean loading, String text) {
        loginButton.setEnabled(!loading);
        registerButton.setEnabled(!loading);
        statusLabel.setText(text);
    }

    /** 清空上一次会话的凭据，供工作台退出后切换账号。 */
    public void prepareForLogin() {
        usernameField.setText("");
        passwordField.setText("");
        statusLabel.setText(" ");
        setLoading(false, " ");
        SwingUtilities.invokeLater(usernameField::requestFocusInWindow);
    }
}
