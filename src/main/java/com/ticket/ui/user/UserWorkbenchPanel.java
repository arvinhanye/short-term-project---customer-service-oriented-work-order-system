package com.ticket.ui.user;

import com.ticket.dto.PageResult;
import com.ticket.model.Order;
import com.ticket.model.Profile;
import com.ticket.model.User;
import com.ticket.dto.RecommendationDTO;
import com.ticket.service.BusinessService;
import com.ticket.service.RecommendService;
import com.ticket.service.UserService;
import com.ticket.ui.MainFrame;
import com.ticket.ui.table.OrderTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

public class UserWorkbenchPanel extends JPanel {
    private final MainFrame mainFrame;
    private final BusinessService businessService = new BusinessService();
    private final UserService userService = new UserService();
    private final RecommendService recommendService = new RecommendService();
    private final JLabel headerLabel = new JLabel("未登录");
    private final OrderTableModel tableModel = new OrderTableModel();
    private final JTable table = new JTable(tableModel);
    private final JTextField titleField = new JTextField(24);
    private final JTextField categoryField = new JTextField(8);
    private final JTextField amountField = new JTextField(8);
    private final JTextField priorityField = new JTextField("MEDIUM", 8);
    private final JTextArea descriptionArea = new JTextArea(8, 48);
    private final JTextField realNameField = new JTextField(20);
    private final JTextField idCardField = new JTextField(20);
    private final JTextField addressField = new JTextField(20);
    private final JTextArea notesArea = new JTextArea(4, 20);
    private User currentUser;

    public UserWorkbenchPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshButton = new JButton("刷新工单");
        JButton logoutButton = new JButton("退出登录");
        topBar.add(headerLabel);
        topBar.add(refreshButton);
        topBar.add(logoutButton);
        add(scrollableHeader(topBar), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("我的工单", new JScrollPane(table));
        tabs.addTab("创建工单", buildCreateTicketPanel());
        tabs.addTab("个人资料", buildProfilePanel());
        add(tabs, BorderLayout.CENTER);

        refreshButton.addActionListener(event -> loadOrders());
        logoutButton.addActionListener(event -> mainFrame.logout());
    }

    public void bindUser(User user) {
        this.currentUser = user;
        headerLabel.setText("当前用户：" + user.getUsername());
        loadOrders();
        loadProfile();
    }

    private JPanel buildCreateTicketPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton createButton = new JButton("提交工单");
        JButton recommendButton = new JButton("推荐分类");
        form.add(new JLabel("标题"));
        form.add(titleField);
        form.add(new JLabel("分类ID"));
        form.add(categoryField);
        form.add(new JLabel("金额"));
        form.add(amountField);
        form.add(new JLabel("优先级"));
        form.add(priorityField);
        form.add(createButton);
        form.add(recommendButton);
        panel.add(scrollableHeader(form), BorderLayout.NORTH);
        panel.add(new JScrollPane(descriptionArea), BorderLayout.CENTER);

        createButton.addActionListener(event -> {
            try {
                long itemId = businessService.createTicket(
                    currentUser,
                    titleField.getText(),
                    Long.parseLong(categoryField.getText()),
                    new BigDecimal(amountField.getText().isBlank() ? "0.00" : amountField.getText()),
                    descriptionArea.getText(),
                    priorityField.getText().trim()
                );
                JOptionPane.showMessageDialog(this, "工单创建成功，编号：" + itemId);
                loadOrders();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
            }
        });

        recommendButton.addActionListener(event -> {
            var recommendations = recommendService.recommendTickets(currentUser, 5);
            showRecommendations(recommendations);
        });
        return panel;
    }

    private JPanel buildProfilePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton saveButton = new JButton("保存资料");
        form.add(new JLabel("姓名"));
        form.add(realNameField);
        form.add(new JLabel("身份证"));
        form.add(idCardField);
        form.add(new JLabel("地址"));
        form.add(addressField);
        form.add(saveButton);
        panel.add(scrollableHeader(form), BorderLayout.NORTH);
        panel.add(new JScrollPane(notesArea), BorderLayout.CENTER);
        saveButton.addActionListener(event -> saveProfile());
        return panel;
    }

    private JScrollPane scrollableHeader(JPanel panel) {
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.setPreferredSize(new Dimension(0, panel.getPreferredSize().height + 18));
        return scrollPane;
    }

    private void showRecommendations(List<RecommendationDTO> recommendations) {
        JTextArea textArea = new JTextArea(formatRecommendations(recommendations), 12, 52);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(520, 280));
        JOptionPane.showMessageDialog(this, scrollPane, "推荐分类", JOptionPane.INFORMATION_MESSAGE);
    }

    private String formatRecommendations(List<RecommendationDTO> recommendations) {
        if (recommendations.isEmpty()) {
            return "暂无推荐";
        }
        StringBuilder builder = new StringBuilder("推荐工单：\n");
        for (int index = 0; index < recommendations.size(); index++) {
            RecommendationDTO recommendation = recommendations.get(index);
            builder.append(index + 1)
                .append(". ")
                .append(recommendation.getTitle())
                .append("\n   分类：")
                .append(recommendation.getCategoryName())
                .append("（ID ")
                .append(recommendation.getCategoryId())
                .append("）")
                .append("\n   推荐理由：")
                .append(recommendation.getReason())
                .append("\n   评分：")
                .append(recommendation.getScore())
                .append("\n\n");
        }
        return builder.toString().trim();
    }

    private void loadOrders() {
        new SwingWorker<PageResult<Order>, Void>() {
            @Override
            protected PageResult<Order> doInBackground() {
                return businessService.pageMyOrders(currentUser, null, 1, 20);
            }

            @Override
            protected void done() {
                try {
                    tableModel.setOrders(get().getRecords());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(UserWorkbenchPanel.this, "加载工单失败", "提示", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void loadProfile() {
        Profile profile = userService.getProfile(currentUser.getUserId());
        if (profile == null) {
            return;
        }
        realNameField.setText(profile.getRealName());
        idCardField.setText(profile.getIdCard());
        addressField.setText(profile.getAddress());
        notesArea.setText(profile.getNotes());
    }

    private void saveProfile() {
        try {
            Profile profile = new Profile();
            profile.setUserId(currentUser.getUserId());
            profile.setRealName(realNameField.getText());
            profile.setIdCard(idCardField.getText());
            profile.setAddress(addressField.getText());
            profile.setNotes(notesArea.getText());
            userService.saveProfile(currentUser, profile);
            JOptionPane.showMessageDialog(this, "资料已保存");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }
}
