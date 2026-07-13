package com.ticket.ui;

import com.ticket.exception.BusinessException;
import com.ticket.model.User;
import com.ticket.service.UserService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import com.ticket.ui.theme.AppTheme;

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
        setBackground(AppTheme.PAGE);
        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(AppTheme.PAGE);
        JPanel formPanel = AppTheme.surface(new GridBagLayout());
        formPanel.setPreferredSize(new Dimension(440, 360));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(7, 8, 7, 8);
        gbc.anchor = GridBagConstraints.WEST;

        JPanel titlePanel = new JPanel(new GridLayout(0, 1, 0, 4));
        titlePanel.setOpaque(false);
        JLabel title = new JLabel("工单管理系统");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 23f));
        JLabel subtitle = AppTheme.muted("集中提交、追踪与处理每一条服务请求");
        titlePanel.add(title);
        titlePanel.add(subtitle);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 8, 18, 8);
        formPanel.add(titlePanel, gbc);
        gbc.gridwidth = 1;
        gbc.insets = new Insets(7, 8, 7, 8);

        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(new JLabel("用户名"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        formPanel.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("密码"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        formPanel.add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(loginButton, gbc);
        gbc.gridx = 1;
        formPanel.add(registerButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(statusLabel, gbc);

        content.add(formPanel);
        add(content, BorderLayout.CENTER);
        AppTheme.primary(loginButton);
        AppTheme.secondary(registerButton);
        AppTheme.styleInput(usernameField);
        AppTheme.styleInput(passwordField);
        statusLabel.setForeground(AppTheme.MUTED);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "login");
        getActionMap().put("login", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                if (loginButton.isEnabled()) {
                    doLogin();
                }
            }
        });
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
                    statusLabel.setForeground(AppTheme.SUCCESS);
                    statusLabel.setText("登录成功");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    String message = cause instanceof BusinessException ? cause.getMessage() : "登录失败，请稍后重试";
                    statusLabel.setForeground(AppTheme.DANGER);
                    statusLabel.setText(message);
                    JOptionPane.showMessageDialog(LoginPanel.this, message, "提示", JOptionPane.WARNING_MESSAGE);
                } finally {
                    loginButton.setEnabled(true);
                    registerButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void setLoading(boolean loading, String text) {
        loginButton.setEnabled(!loading);
        registerButton.setEnabled(!loading);
        statusLabel.setForeground(loading ? AppTheme.PRIMARY : AppTheme.MUTED);
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
