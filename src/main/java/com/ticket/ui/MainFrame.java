package com.ticket.ui;

import com.ticket.model.User;
import com.ticket.ui.admin.AdminWorkbenchPanel;
import com.ticket.ui.user.UserWorkbenchPanel;
import java.awt.CardLayout;
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
        this.loginPanel = new LoginPanel(this);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setLocationRelativeTo(null);
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

    public void logout() {
        cardLayout.show(rootPanel, "login");
    }
}
