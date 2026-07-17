package com.ticket.ui;

import com.ticket.exception.BusinessException;
import com.ticket.model.User;
import com.ticket.service.UserService;
import com.ticket.ui.theme.AppTheme;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.Arrays;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.JToggleButton;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;
import javax.swing.plaf.basic.BasicPasswordFieldUI;
import javax.swing.plaf.basic.BasicTextFieldUI;

public class LoginPanel extends JPanel {
    private static final String PASSWORD_TOGGLE_PROPERTY = "login-password-toggle";
    private static final Color AUTH_PAGE = new Color(226, 233, 244);
    private static final Color AUTH_WORKSPACE = new Color(247, 249, 252);
    private static final Color AUTH_BORDER = new Color(196, 207, 223);
    private final MainFrame mainFrame;
    private final UserService userService = new UserService();
    private final CardLayout authLayout = new CardLayout();
    private final JPanel authCards = new JPanel(authLayout);
    private boolean registering;

    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton loginButton = new JButton("登录");
    private final JButton registerButton = new JButton("创建账号");

    private final JTextField registerUsernameField = new JTextField(20);
    private final JPasswordField registerPasswordField = new JPasswordField(20);
    private final JPasswordField confirmPasswordField = new JPasswordField(20);
    private final JTextField emailField = new JTextField(20);
    private final JTextField phoneField = new JTextField(20);
    private final JLabel registerStatusLabel = new JLabel(" ");
    private final JButton submitRegisterButton = new JButton("提交注册");
    private final JButton backToLoginButton = new JButton("返回登录");

    public LoginPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        installWhitespaceIgnoringFilter(usernameField);
        installWhitespaceIgnoringFilter(passwordField);
        setLayout(new BorderLayout());
        setBackground(AUTH_PAGE);
        authCards.setBackground(AUTH_WORKSPACE);
        authCards.add(centered(buildLoginForm()), "login");
        authCards.add(centered(buildRegisterForm()), "register");
        JPanel shell = new JPanel(new BorderLayout());
        shell.setPreferredSize(new Dimension(940, 570));
        shell.setBorder(BorderFactory.createLineBorder(new Color(183, 195, 213)));
        shell.add(buildBrandPanel(), BorderLayout.WEST);
        shell.add(authCards, BorderLayout.CENTER);
        add(buildAuthPage(shell), BorderLayout.CENTER);
        installKeyboardActions();
        showLogin();
    }

    private void installWhitespaceIgnoringFilter(JTextComponent field) {
        if (field.getDocument() instanceof AbstractDocument document) {
            document.setDocumentFilter(new DocumentFilter() {
                @Override
                public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes)
                        throws BadLocationException {
                    String filtered = removeWhitespace(text);
                    if (!filtered.isEmpty()) {
                        super.insertString(bypass, offset, filtered, attributes);
                    }
                }

                @Override
                public void replace(FilterBypass bypass, int offset, int length, String text,
                                    AttributeSet attributes) throws BadLocationException {
                    if (text == null) {
                        super.replace(bypass, offset, length, null, attributes);
                        return;
                    }
                    if (text.isEmpty()) {
                        super.replace(bypass, offset, length, "", attributes);
                        return;
                    }
                    String filtered = removeWhitespace(text);
                    if (!filtered.isEmpty()) {
                        super.replace(bypass, offset, length, filtered, attributes);
                    }
                }
            });
        }
    }

    private String removeWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder filtered = new StringBuilder(text.length());
        text.codePoints()
            .filter(codePoint -> !Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint))
            .forEach(filtered::appendCodePoint);
        return filtered.toString();
    }

    private JPanel buildAuthPage(JPanel shell) {
        JPanel page = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D graphics2D = (Graphics2D) graphics.create();
                try {
                    graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    graphics2D.setPaint(new GradientPaint(0, 0, new Color(218, 228, 244),
                        getWidth(), getHeight(), new Color(239, 243, 249)));
                    graphics2D.fillRect(0, 0, getWidth(), getHeight());

                    graphics2D.setColor(new Color(59, 130, 246, 18));
                    int glowSize = Math.max(240, Math.min(getWidth(), getHeight()) / 2);
                    graphics2D.fillOval(-glowSize / 3, -glowSize / 2, glowSize, glowSize);
                    graphics2D.setColor(new Color(37, 99, 235, 12));
                    graphics2D.fillOval(getWidth() - glowSize / 2, getHeight() - glowSize / 2,
                        glowSize, glowSize);

                    graphics2D.setColor(new Color(71, 96, 135, 24));
                    int spacing = 26;
                    for (int x = 18; x < getWidth(); x += spacing) {
                        for (int y = 18; y < getHeight(); y += spacing) {
                            if (x < 150 || x > getWidth() - 150 || y < 90 || y > getHeight() - 90) {
                                graphics2D.fillOval(x, y, 2, 2);
                            }
                        }
                    }
                } finally {
                    graphics2D.dispose();
                }
            }
        };
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel shadow = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D graphics2D = (Graphics2D) graphics.create();
                try {
                    graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics2D.setColor(new Color(35, 54, 86, 22));
                    graphics2D.fillRoundRect(7, 8, Math.max(0, getWidth() - 14),
                        Math.max(0, getHeight() - 14), 10, 10);
                } finally {
                    graphics2D.dispose();
                }
            }
        };
        shadow.setOpaque(false);
        shadow.setBorder(BorderFactory.createEmptyBorder(6, 6, 8, 6));
        shadow.add(shell, BorderLayout.CENTER);
        page.add(shadow);
        return page;
    }

    private JPanel centered(JPanel form) {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(AUTH_WORKSPACE);
        wrapper.add(form);
        return wrapper;
    }

    private JPanel buildBrandPanel() {
        JPanel brand = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D graphics2D = (Graphics2D) graphics.create();
                try {
                    graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    graphics2D.setPaint(new GradientPaint(0, 0, new Color(24, 63, 141),
                        getWidth(), getHeight(), new Color(37, 99, 235)));
                    graphics2D.fillRect(0, 0, getWidth(), getHeight());
                    graphics2D.setColor(new Color(255, 255, 255, 20));
                    graphics2D.fillOval(-90, getHeight() - 190, 280, 280);
                    graphics2D.fillOval(getWidth() - 130, -70, 230, 230);
                } finally {
                    graphics2D.dispose();
                }
            }
        };
        brand.setOpaque(false);
        brand.setPreferredSize(new Dimension(330, 570));
        brand.setBorder(BorderFactory.createEmptyBorder(48, 38, 48, 38));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        JLabel badge = new JLabel("TICKET SERVICE");
        badge.setForeground(new Color(191, 219, 254));
        badge.setFont(badge.getFont().deriveFont(java.awt.Font.BOLD, 12f));
        badge.setAlignmentX(LEFT_ALIGNMENT);
        JLabel title = new JLabel("工单管理系统");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 29f));
        title.setAlignmentX(LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("连接需求与服务进度");
        subtitle.setForeground(new Color(219, 234, 254));
        subtitle.setFont(subtitle.getFont().deriveFont(15f));
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        content.add(badge);
        content.add(Box.createVerticalStrut(18));
        content.add(title);
        content.add(Box.createVerticalStrut(9));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(42));
        content.add(createBrandFeature("统一提交服务请求"));
        content.add(Box.createVerticalStrut(16));
        content.add(createBrandFeature("随时跟踪处理进度"));
        content.add(Box.createVerticalStrut(16));
        content.add(createBrandFeature("完整保留沟通记录"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        brand.add(content, gbc);
        return brand;
    }

    private JLabel createBrandFeature(String text) {
        JLabel label = new JLabel("●  " + text);
        label.setForeground(new Color(239, 246, 255));
        label.setFont(label.getFont().deriveFont(14f));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JPanel buildLoginForm() {
        JPanel formPanel = AppTheme.surface(new GridBagLayout());
        formPanel.setPreferredSize(new Dimension(440, 350));
        formPanel.setBorder(authCardBorder());
        GridBagConstraints gbc = constraints();

        JPanel titlePanel = new JPanel(new GridLayout(0, 1, 0, 4));
        titlePanel.setOpaque(false);
        JLabel title = new JLabel("登录账号");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 23f));
        titlePanel.add(title);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 8, 18, 8);
        formPanel.add(titlePanel, gbc);
        gbc.gridwidth = 1;
        gbc.insets = new Insets(7, 8, 7, 8);

        styleAuthInput(usernameField);
        styleAuthInput(passwordField);
        addField(formPanel, "用户名", usernameField, 1, gbc);
        addField(formPanel, "密码", passwordInput(passwordField), 2, gbc);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        AppTheme.secondary(registerButton);
        AppTheme.primary(loginButton);
        actions.add(registerButton);
        actions.add(loginButton);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(actions, gbc);

        statusLabel.setForeground(AppTheme.MUTED);
        gbc.gridy = 4;
        formPanel.add(statusLabel, gbc);

        loginButton.addActionListener(event -> doLogin());
        registerButton.addActionListener(event -> showRegister());
        return formPanel;
    }

    private JPanel buildRegisterForm() {
        JPanel formPanel = AppTheme.surface(new GridBagLayout());
        formPanel.setPreferredSize(new Dimension(520, 500));
        formPanel.setBorder(authCardBorder());
        GridBagConstraints gbc = constraints();

        JLabel title = new JLabel("创建普通用户账号");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 21f));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 8, 16, 8);
        formPanel.add(title, gbc);
        gbc.gridwidth = 1;
        gbc.insets = new Insets(7, 8, 7, 8);

        for (JComponent component : new JComponent[]{registerUsernameField,
            registerPasswordField, confirmPasswordField, emailField, phoneField}) {
            styleAuthInput(component);
        }
        addField(formPanel, "用户名", registerUsernameField, 1, gbc);
        addField(formPanel, "密码", passwordInput(registerPasswordField), 2, gbc);
        addField(formPanel, "确认密码", passwordInput(confirmPasswordField), 3, gbc);
        addField(formPanel, "邮箱", emailField, 4, gbc);
        addField(formPanel, "手机号", phoneField, 5, gbc);

        JLabel passwordHint = AppTheme.muted("12–64 位，包含大小写字母、数字和特殊字符");
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(passwordHint, gbc);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        AppTheme.secondary(backToLoginButton);
        AppTheme.primary(submitRegisterButton);
        actions.add(backToLoginButton);
        actions.add(submitRegisterButton);
        gbc.gridy = 7;
        gbc.insets = new Insets(14, 8, 7, 8);
        formPanel.add(actions, gbc);

        registerStatusLabel.setForeground(AppTheme.MUTED);
        gbc.gridy = 8;
        gbc.insets = new Insets(7, 8, 4, 8);
        formPanel.add(registerStatusLabel, gbc);

        backToLoginButton.addActionListener(event -> showLogin());
        submitRegisterButton.addActionListener(event -> doRegister());
        return formPanel;
    }

    private GridBagConstraints constraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(7, 8, 7, 8);
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }

    private javax.swing.border.Border authCardBorder() {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AUTH_BORDER),
            BorderFactory.createEmptyBorder(22, 26, 22, 26));
    }

    private void styleAuthInput(JComponent component) {
        if (component instanceof JPasswordField password) {
            password.setUI(new BasicPasswordFieldUI());
        } else if (component instanceof JTextField textField) {
            textField.setUI(new BasicTextFieldUI());
        }
        AppTheme.styleInput(component);
        component.setBackground(Color.WHITE);
        component.setForeground(AppTheme.TEXT);
        if (component instanceof JTextComponent text) {
            text.setCaretColor(AppTheme.TEXT);
            text.setSelectionColor(new Color(191, 219, 254));
            text.setSelectedTextColor(AppTheme.TEXT);
            text.setMargin(new Insets(0, 0, 0, 0));
        }
        component.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(160, 174, 194)),
            BorderFactory.createEmptyBorder(6, 9, 6, 9)));
    }

    private JComponent passwordInput(JPasswordField field) {
        char hiddenEchoChar = field.getEchoChar();
        field.setBorder(BorderFactory.createEmptyBorder(6, 9, 6, 4));

        JToggleButton toggle = new JToggleButton();
        toggle.setIcon(new EyeIcon(false));
        toggle.setSelectedIcon(new EyeIcon(true));
        toggle.setToolTipText("显示密码");
        toggle.setPreferredSize(new Dimension(38, 36));
        toggle.setBorder(BorderFactory.createEmptyBorder());
        toggle.setContentAreaFilled(false);
        toggle.setFocusPainted(false);
        toggle.setFocusable(false);
        toggle.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        toggle.getAccessibleContext().setAccessibleName("显示密码");
        toggle.addActionListener(event -> {
            boolean visible = toggle.isSelected();
            field.setEchoChar(visible ? (char) 0 : hiddenEchoChar);
            String action = visible ? "隐藏密码" : "显示密码";
            toggle.setToolTipText(action);
            toggle.getAccessibleContext().setAccessibleName(action);
        });
        toggle.setEnabled(field.isEnabled());
        field.addPropertyChangeListener("enabled", event -> toggle.setEnabled(field.isEnabled()));
        field.putClientProperty(PASSWORD_TOGGLE_PROPERTY, toggle);

        JPanel input = new JPanel(new BorderLayout());
        input.setBackground(Color.WHITE);
        input.setBorder(BorderFactory.createLineBorder(new Color(160, 174, 194)));
        input.add(field, BorderLayout.CENTER);
        input.add(toggle, BorderLayout.EAST);
        return input;
    }

    private void hidePassword(JPasswordField field) {
        Object value = field.getClientProperty(PASSWORD_TOGGLE_PROPERTY);
        if (value instanceof JToggleButton toggle && toggle.isSelected()) {
            toggle.doClick(0);
        }
    }

    private static final class EyeIcon implements Icon {
        private final boolean crossed;

        private EyeIcon(boolean crossed) {
            this.crossed = crossed;
        }

        @Override
        public int getIconWidth() {
            return 20;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }

        @Override
        public void paintIcon(java.awt.Component component, Graphics graphics, int x, int y) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setColor(component.isEnabled() ? AppTheme.MUTED : new Color(170, 174, 181));
                graphics2D.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics2D.drawArc(x + 1, y + 2, 18, 12, 18, 144);
                graphics2D.drawArc(x + 1, y + 2, 18, 12, 198, 144);
                graphics2D.fillOval(x + 8, y + 6, 4, 4);
                if (crossed) {
                    graphics2D.setColor(AppTheme.MUTED);
                    graphics2D.drawLine(x + 2, y + 1, x + 18, y + 15);
                }
            } finally {
                graphics2D.dispose();
            }
        }
    }

    private void addField(JPanel panel, String label, java.awt.Component field, int row,
                          GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, gbc);
    }

    private void installKeyboardActions() {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ENTER"), "submit-auth");
        getActionMap().put("submit-auth", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                if (registering && submitRegisterButton.isEnabled()) {
                    doRegister();
                } else if (!registering && loginButton.isEnabled()) {
                    doLogin();
                }
            }
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "back-to-login");
        getActionMap().put("back-to-login", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                if (registering && backToLoginButton.isEnabled()) {
                    showLogin();
                }
            }
        });
    }

    private void showLogin() {
        if (registering) {
            clearRegisterForm();
        }
        registering = false;
        authLayout.show(authCards, "login");
        SwingUtilities.invokeLater(usernameField::requestFocusInWindow);
    }

    private void showRegister() {
        clearRegisterForm();
        registering = true;
        authLayout.show(authCards, "register");
        SwingUtilities.invokeLater(registerUsernameField::requestFocusInWindow);
    }

    private void doLogin() {
        setLoginLoading(true, "登录中...");
        new SwingWorker<User, Void>() {
            @Override
            protected User doInBackground() {
                char[] passwordChars = passwordField.getPassword();
                try {
                    return userService.login(usernameField.getText(), new String(passwordChars));
                } finally {
                    Arrays.fill(passwordChars, '\0');
                }
            }

            @Override
            protected void done() {
                try {
                    User user = get();
                    passwordField.setText("");
                    if (Integer.valueOf(1).equals(user.getMustChangePassword())
                            && !mainFrame.showPasswordChange(user, true)) {
                        statusLabel.setForeground(AppTheme.WARNING);
                        statusLabel.setText("必须修改密码后才能进入系统");
                        return;
                    }
                    mainFrame.onLoginSuccess(user);
                    statusLabel.setForeground(AppTheme.SUCCESS);
                    statusLabel.setText("登录成功");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    String message = cause instanceof BusinessException ? cause.getMessage() : "登录失败，请稍后重试";
                    statusLabel.setForeground(AppTheme.DANGER);
                    statusLabel.setText(message);
                } finally {
                    setLoginLoading(false, statusLabel.getText());
                }
            }
        }.execute();
    }

    private void doRegister() {
        char[] passwordChars = registerPasswordField.getPassword();
        char[] confirmChars = confirmPasswordField.getPassword();
        if (!Arrays.equals(passwordChars, confirmChars)) {
            Arrays.fill(passwordChars, '\0');
            Arrays.fill(confirmChars, '\0');
            registerStatusLabel.setForeground(AppTheme.DANGER);
            registerStatusLabel.setText("两次输入的密码不一致");
            confirmPasswordField.requestFocusInWindow();
            return;
        }
        String username = registerUsernameField.getText().trim();
        String password = new String(passwordChars);
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        Arrays.fill(passwordChars, '\0');
        Arrays.fill(confirmChars, '\0');
        setRegisterLoading(true, "正在创建账号…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                userService.register(username, password, email, phone);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    clearRegisterForm();
                    showLogin();
                    usernameField.setText(username);
                    passwordField.setText("");
                    statusLabel.setForeground(AppTheme.SUCCESS);
                    statusLabel.setText("账号创建成功，请输入密码登录");
                    SwingUtilities.invokeLater(passwordField::requestFocusInWindow);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    String message = cause instanceof BusinessException ? cause.getMessage() : "注册失败，请稍后重试";
                    registerStatusLabel.setForeground(AppTheme.DANGER);
                    registerStatusLabel.setText(message);
                } finally {
                    setRegisterLoading(false, registerStatusLabel.getText());
                }
            }
        }.execute();
    }

    private void setLoginLoading(boolean loading, String text) {
        loginButton.setEnabled(!loading);
        registerButton.setEnabled(!loading);
        usernameField.setEnabled(!loading);
        passwordField.setEnabled(!loading);
        if (loading) {
            statusLabel.setForeground(AppTheme.PRIMARY);
        }
        statusLabel.setText(text == null ? " " : text);
    }

    private void setRegisterLoading(boolean loading, String text) {
        submitRegisterButton.setEnabled(!loading);
        backToLoginButton.setEnabled(!loading);
        registerUsernameField.setEnabled(!loading);
        registerPasswordField.setEnabled(!loading);
        confirmPasswordField.setEnabled(!loading);
        emailField.setEnabled(!loading);
        phoneField.setEnabled(!loading);
        submitRegisterButton.setText(loading ? "注册中…" : "提交注册");
        if (loading) {
            registerStatusLabel.setForeground(AppTheme.PRIMARY);
        }
        registerStatusLabel.setText(text == null || text.isBlank() ? " " : text);
    }

    private void clearRegisterForm() {
        hidePassword(registerPasswordField);
        hidePassword(confirmPasswordField);
        registerUsernameField.setText("");
        registerPasswordField.setText("");
        confirmPasswordField.setText("");
        emailField.setText("");
        phoneField.setText("");
        registerStatusLabel.setText(" ");
    }

    /** 清空上一次会话的凭据，供工作台退出后切换账号。 */
    public void prepareForLogin() {
        hidePassword(passwordField);
        usernameField.setText("");
        passwordField.setText("");
        statusLabel.setText(" ");
        clearRegisterForm();
        setLoginLoading(false, " ");
        setRegisterLoading(false, " ");
        showLogin();
    }
}
