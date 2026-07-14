package com.ticket.ui;

import com.ticket.model.User;
import com.ticket.ui.admin.AdminWorkbenchPanel;
import com.ticket.ui.user.UserWorkbenchPanel;
import com.ticket.ui.theme.WindowIconUtil;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class MainFrame extends JFrame {
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel rootPanel = new JPanel(cardLayout);
    private final LoginPanel loginPanel;
    private final UserWorkbenchPanel userWorkbenchPanel = new UserWorkbenchPanel(this);
    private final AdminWorkbenchPanel adminWorkbenchPanel = new AdminWorkbenchPanel(this);

    public MainFrame() {
        super("工单管理系统");
        WindowIconUtil.apply(this);
        this.loginPanel = new LoginPanel(this);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1040, 680));
        getContentPane().setBackground(new Color(246, 248, 252));
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        rootPanel.add(loginPanel, "login");
        rootPanel.add(userWorkbenchPanel, "user");
        rootPanel.add(adminWorkbenchPanel, "admin");
        setContentPane(rootPanel);
        cardLayout.show(rootPanel, "login");
    }

    public void onLoginSuccess(User user) {
        if ("ADMIN".equals(user.getRole())) {
            adminWorkbenchPanel.bindUser(user);
            cardLayout.show(rootPanel, "admin");
        } else {
            userWorkbenchPanel.bindUser(user);
            cardLayout.show(rootPanel, "user");
        }
    }

    public boolean showPasswordChange(User user, boolean required) {
        return new PasswordChangeDialog(this, user, required).showDialog();
    }

    public void logout() {
        userWorkbenchPanel.clearSession();
        adminWorkbenchPanel.clearSession();
        loginPanel.prepareForLogin();
        cardLayout.show(rootPanel, "login");
    }
}
