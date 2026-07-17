package com.ticket.ui;

import com.ticket.model.User;
import com.ticket.ui.admin.AdminWorkbenchPanel;
import com.ticket.ui.user.UserWorkbenchPanel;
import com.ticket.ui.theme.WindowIconUtil;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.util.concurrent.TimeUnit;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class MainFrame extends JFrame {
    private static final long IDLE_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(15);
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel rootPanel = new JPanel(cardLayout);
    private final LoginPanel loginPanel;
    private final UserWorkbenchPanel userWorkbenchPanel = new UserWorkbenchPanel(this);
    private final AdminWorkbenchPanel adminWorkbenchPanel = new AdminWorkbenchPanel(this);
    private User currentUser;
    private long lastActivityNanos = System.nanoTime();
    private final javax.swing.Timer idleTimer = new javax.swing.Timer(30_000, event -> checkIdleTimeout());

    public MainFrame() {
        super(windowTitle());
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
        installActivityTracking();
        idleTimer.start();
    }

    private static String windowTitle() {
        String instanceId = System.getenv("TICKET_INSTANCE_ID");
        if (instanceId == null || instanceId.isBlank()) {
            return "工单管理系统";
        }
        return "工单管理系统 · 窗口 " + instanceId.trim();
    }

    public void onLoginSuccess(User user) {
        currentUser = user;
        lastActivityNanos = System.nanoTime();
        if ("ROOT".equals(user.getRole()) || "ADMIN".equals(user.getRole())) {
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
        currentUser = null;
        userWorkbenchPanel.clearSession();
        adminWorkbenchPanel.clearSession();
        loginPanel.prepareForLogin();
        cardLayout.show(rootPanel, "login");
    }

    private void installActivityTracking() {
        long mask = AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK
            | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK;
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            Object source = event.getSource();
            if (source instanceof Component component && javax.swing.SwingUtilities.isDescendingFrom(component, this)) {
                lastActivityNanos = System.nanoTime();
            }
        }, mask);
    }

    private void checkIdleTimeout() {
        if (currentUser == null || System.nanoTime() - lastActivityNanos < IDLE_TIMEOUT_NANOS) return;
        logout();
        javax.swing.JOptionPane.showMessageDialog(this, "已连续 15 分钟无操作，系统已自动退出登录。",
            "会话已过期", javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }
}
