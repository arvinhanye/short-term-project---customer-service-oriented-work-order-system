package com.ticket.ui.user;

import com.ticket.dto.CrossTicketDTO;
import com.ticket.dto.ItemDetailDTO;
import com.ticket.dto.PageResult;
import com.ticket.dto.RecommendationDTO;
import com.ticket.model.Category;
import com.ticket.model.Comment;
import com.ticket.model.ItemDetail;
import com.ticket.model.Profile;
import com.ticket.model.User;
import com.ticket.service.BusinessService;
import com.ticket.service.CategoryService;
import com.ticket.service.CrossDatabaseQueryService;
import com.ticket.service.RecommendService;
import com.ticket.service.UserService;
import com.ticket.ui.MainFrame;
import com.ticket.ui.table.OrderTableModel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

public class UserWorkbenchPanel extends JPanel {
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter BEIJING_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss '北京时间'");
    private final MainFrame mainFrame;
    private final BusinessService businessService = new BusinessService();
    private final CrossDatabaseQueryService crossDatabaseQueryService = new CrossDatabaseQueryService();
    private final UserService userService = new UserService();
    private final RecommendService recommendService = new RecommendService();
    private final CategoryService categoryService = new CategoryService();
    private final Map<Long, String> categoryNameById = new HashMap<>();
    private final JLabel headerLabel = new JLabel("未登录");
    private final OrderTableModel tableModel = new OrderTableModel();
    private final JTable table = new JTable(tableModel);
    private final JTextField titleField = new JTextField(24);
    private final JComboBox<CategoryOption> categoryBox = new JComboBox<>();
    private final JTextField amountField = new JTextField(8);
    private final JComboBox<PriorityOption> priorityBox = new JComboBox<>();
    private final JTextArea descriptionArea = new JTextArea(8, 48);
    private final JComboBox<StatusOption> statusFilterBox = new JComboBox<>();
    private final JTextField keywordField = new JTextField(18);
    private final JLabel pageInfoLabel = new JLabel("第 1 页 / 共 0 条");
    private final JButton previousPageButton = new JButton("上一页");
    private final JButton nextPageButton = new JButton("下一页");
    private final JTextField realNameField = new JTextField(20);
    private final JTextField emailField = new JTextField(20);
    private final JTextField phoneField = new JTextField(14);
    private final JTextField idCardField = new JTextField(20);
    private final JTextField addressField = new JTextField(20);
    private final JComboBox<String> preferredContactBox = new JComboBox<>(
        new String[] {"手机号", "邮箱", "站内消息"});
    private final JComboBox<String> notificationBox = new JComboBox<>(
        new String[] {"所有回复", "状态变更", "仅处理结果", "不通知"});
    private final JTextArea notesArea = new JTextArea(4, 20);
    private final JButton realNameAuthButton = new JButton("实名认证");
    private int currentPage = 1;
    private final int pageSize = 20;
    private long currentTotal = 0;
    private User currentUser;

    public UserWorkbenchPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());
        initOptionModels();
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshButton = new JButton("刷新工单");
        JButton logoutButton = new JButton("退出登录");
        topBar.add(headerLabel);
        topBar.add(refreshButton);
        topBar.add(logoutButton);
        add(scrollableHeader(topBar), BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(buildTabContent(), BorderLayout.CENTER);

        refreshButton.addActionListener(event -> loadOrders());
        previousPageButton.addActionListener(event -> {
            if (currentPage > 1) {
                currentPage--;
                loadOrders();
            }
        });
        nextPageButton.addActionListener(event -> {
            if (currentPage < totalPages()) {
                currentPage++;
                loadOrders();
            }
        });
        logoutButton.addActionListener(event -> mainFrame.logout());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) {
                    showSelectedTicketDetail();
                }
            }
        });
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                    event.consume();
                    showSelectedTicketDetail();
                }
            }
        });
    }

    private JPanel buildTabContent() {
        JPanel panel = new JPanel(new BorderLayout());
        CardLayout cardLayout = new CardLayout();
        JPanel cards = new JPanel(cardLayout);
        cards.add(buildMyTicketsPanel(), "orders");
        cards.add(buildCreateTicketPanel(), "create");
        cards.add(buildProfilePanel(), "profile");

        JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        tabBar.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
        ButtonGroup group = new ButtonGroup();
        JToggleButton ordersButton = new JToggleButton("我的工单");
        JToggleButton createButton = new JToggleButton("创建工单");
        JToggleButton profileButton = new JToggleButton("联系信息");
        group.add(ordersButton);
        group.add(createButton);
        group.add(profileButton);
        tabBar.add(ordersButton);
        tabBar.add(createButton);
        tabBar.add(profileButton);
        ordersButton.setSelected(true);
        ordersButton.addActionListener(event -> cardLayout.show(cards, "orders"));
        createButton.addActionListener(event -> cardLayout.show(cards, "create"));
        profileButton.addActionListener(event -> cardLayout.show(cards, "profile"));

        panel.add(tabBar, BorderLayout.NORTH);
        panel.add(cards, BorderLayout.CENTER);
        return panel;
    }

    public void bindUser(User user) {
        this.currentUser = user;
        headerLabel.setText("当前用户：" + user.getUsername());
        loadCategories();
        loadOrders();
        loadProfile();
    }

    private void initOptionModels() {
        statusFilterBox.addItem(new StatusOption(null, "全部状态"));
        statusFilterBox.addItem(new StatusOption(0, "待处理"));
        statusFilterBox.addItem(new StatusOption(1, "处理中"));
        statusFilterBox.addItem(new StatusOption(2, "已完成"));
        statusFilterBox.addItem(new StatusOption(3, "已关闭"));
        statusFilterBox.addItem(new StatusOption(4, "已取消"));

        priorityBox.addItem(new PriorityOption("LOW", "低"));
        priorityBox.addItem(new PriorityOption("MEDIUM", "中"));
        priorityBox.addItem(new PriorityOption("HIGH", "高"));
        priorityBox.addItem(new PriorityOption("URGENT", "紧急"));
        priorityBox.setSelectedIndex(1);
    }

    private JPanel buildMyTicketsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton searchButton = new JButton("搜索");
        JButton resetButton = new JButton("重置");
        filters.add(new JLabel("状态"));
        filters.add(statusFilterBox);
        filters.add(new JLabel("关键词"));
        filters.add(keywordField);
        filters.add(searchButton);
        filters.add(resetButton);
        filters.add(previousPageButton);
        filters.add(nextPageButton);
        filters.add(pageInfoLabel);
        panel.add(scrollableHeader(filters), BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        searchButton.addActionListener(event -> {
            currentPage = 1;
            loadOrders();
        });
        resetButton.addActionListener(event -> {
            statusFilterBox.setSelectedIndex(0);
            keywordField.setText("");
            currentPage = 1;
            loadOrders();
        });
        keywordField.addActionListener(event -> {
            currentPage = 1;
            loadOrders();
        });
        statusFilterBox.addActionListener(event -> {
            currentPage = 1;
            loadOrders();
        });
        updatePageControls();
        return panel;
    }

    private JPanel buildCreateTicketPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton createButton = new JButton("提交工单");
        JButton recommendButton = new JButton("推荐分类");
        form.add(new JLabel("标题"));
        form.add(titleField);
        form.add(new JLabel("分类"));
        form.add(categoryBox);
        form.add(new JLabel("金额"));
        form.add(amountField);
        form.add(new JLabel("优先级"));
        form.add(priorityBox);
        form.add(createButton);
        form.add(recommendButton);
        panel.add(scrollableHeader(form), BorderLayout.NORTH);
        panel.add(new JScrollPane(descriptionArea), BorderLayout.CENTER);

        createButton.addActionListener(event -> {
            try {
                CategoryOption categoryOption = (CategoryOption) categoryBox.getSelectedItem();
                PriorityOption priorityOption = (PriorityOption) priorityBox.getSelectedItem();
                if (categoryOption == null || categoryOption.categoryId() == null) {
                    JOptionPane.showMessageDialog(this, "请选择工单分类。", "提示", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                long itemId = businessService.createTicket(
                    currentUser,
                    titleField.getText(),
                    categoryOption.categoryId(),
                    new BigDecimal(amountField.getText().isBlank() ? "0.00" : amountField.getText()),
                    descriptionArea.getText(),
                    priorityOption == null ? "MEDIUM" : priorityOption.value()
                );
                JOptionPane.showMessageDialog(this, "工单创建成功，编号：" + itemId);
                titleField.setText("");
                amountField.setText("");
                descriptionArea.setText("");
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
        JButton saveButton = new JButton("保存资料");

        JPanel blocks = new JPanel(new GridLayout(2, 2, 12, 12));
        blocks.setBorder(BorderFactory.createEmptyBorder(14, 14, 10, 14));

        JPanel contactBlock = buildProfileBlock("基础联系");
        addFormRow(contactBlock, 0, "姓名/称呼", realNameField);
        addFormRow(contactBlock, 1, "邮箱", emailField);
        addFormRow(contactBlock, 2, "手机号", phoneField);

        JPanel preferenceBlock = buildProfileBlock("联系偏好");
        addFormRow(preferenceBlock, 0, "首选联系方式", preferredContactBox);
        addFormRow(preferenceBlock, 1, "通知偏好", notificationBox);
        addFormRow(preferenceBlock, 2, "服务地址", addressField);

        JPanel verificationBlock = buildProfileBlock("实名认证");
        JLabel verificationHint = new JLabel("证件号码仅在实名认证弹窗中填写。");
        addFormRow(verificationBlock, 0, "认证状态", realNameAuthButton);
        addFormRow(verificationBlock, 1, "说明", verificationHint);

        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        JPanel notesBlock = buildProfileBlock("补充说明");
        JScrollPane notesScrollPane = new JScrollPane(notesArea);
        notesScrollPane.setPreferredSize(new Dimension(320, 110));
        GridBagConstraints noteConstraints = new GridBagConstraints();
        noteConstraints.gridx = 0;
        noteConstraints.gridy = 0;
        noteConstraints.weightx = 1;
        noteConstraints.weighty = 1;
        noteConstraints.fill = GridBagConstraints.BOTH;
        noteConstraints.insets = new Insets(6, 8, 8, 8);
        notesBlock.add(notesScrollPane, noteConstraints);

        blocks.add(contactBlock);
        blocks.add(preferenceBlock);
        blocks.add(verificationBlock);
        blocks.add(notesBlock);
        panel.add(blocks, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 14, 12, 14));
        actions.add(saveButton);
        panel.add(actions, BorderLayout.SOUTH);

        saveButton.addActionListener(event -> saveProfile());
        realNameAuthButton.addActionListener(event -> showRealNameAuthDialog());
        return panel;
    }

    private JPanel buildProfileBlock(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
    }

    private void addFormRow(JPanel panel, int row, String label, JComponent component) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(7, 8, 7, 8);
        panel.add(new JLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(7, 0, 7, 8);
        panel.add(component, fieldConstraints);
    }

    private JComponent scrollableHeader(JPanel panel) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        wrapper.add(panel, BorderLayout.WEST);
        return wrapper;
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

    private void loadCategories() {
        User actor = currentUser;
        categoryBox.removeAllItems();
        categoryNameById.clear();
        new SwingWorker<List<Category>, Void>() {
            @Override
            protected List<Category> doInBackground() {
                return categoryService.listAvailableCategories(actor);
            }

            @Override
            protected void done() {
                try {
                    for (Category category : get()) {
                        categoryBox.addItem(new CategoryOption(category.getCategoryId(), category.getName()));
                        categoryNameById.put(category.getCategoryId(), category.getName());
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(UserWorkbenchPanel.this, "加载分类失败", "提示", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void loadOrders() {
        StatusOption statusOption = (StatusOption) statusFilterBox.getSelectedItem();
        Integer status = statusOption == null ? null : statusOption.status();
        String keyword = keywordField.getText();
        new SwingWorker<PageResult<CrossTicketDTO>, Void>() {
            @Override
            protected PageResult<CrossTicketDTO> doInBackground() {
                return crossDatabaseQueryService.pageMyTickets(currentUser, status, keyword, currentPage, pageSize);
            }

            @Override
            protected void done() {
                try {
                    PageResult<CrossTicketDTO> result = get();
                    currentPage = result.getPage();
                    currentTotal = result.getTotal();
                    tableModel.setTickets(result.getRecords());
                    updatePageControls();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(UserWorkbenchPanel.this, "加载工单失败", "提示", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void updatePageControls() {
        int totalPages = totalPages();
        pageInfoLabel.setText("第 " + currentPage + " 页 / 共 " + currentTotal + " 条");
        previousPageButton.setEnabled(currentPage > 1);
        nextPageButton.setEnabled(currentPage < totalPages);
    }

    private int totalPages() {
        if (currentTotal <= 0) {
            return 1;
        }
        return (int) Math.ceil(currentTotal / (double) pageSize);
    }

    private void loadProfile() {
        realNameField.setText("");
        emailField.setText(currentUser == null ? "" : nullToEmpty(currentUser.getEmail()));
        phoneField.setText(currentUser == null ? "" : nullToEmpty(currentUser.getPhone()));
        idCardField.setText("");
        addressField.setText("");
        notesArea.setText("");
        preferredContactBox.setSelectedIndex(0);
        notificationBox.setSelectedIndex(0);
        Profile profile = userService.getProfile(currentUser.getUserId());
        if (profile == null) {
            updateRealNameAuthButton();
            return;
        }
        realNameField.setText(profile.getRealName());
        idCardField.setText(profile.getIdCard());
        addressField.setText(profile.getAddress());
        loadProfileNotes(profile.getNotes());
        updateRealNameAuthButton();
    }

    private void saveProfile() {
        saveProfile(true);
    }

    private boolean saveProfile(boolean showMessage) {
        try {
            Profile profile = new Profile();
            profile.setUserId(currentUser.getUserId());
            profile.setRealName(realNameField.getText());
            profile.setIdCard(idCardField.getText());
            profile.setAddress(addressField.getText());
            profile.setNotes(formatProfileNotes());
            User updatedUser = new User();
            updatedUser.setUserId(currentUser.getUserId());
            updatedUser.setEmail(emailField.getText());
            updatedUser.setPhone(phoneField.getText());
            userService.updateUser(currentUser, updatedUser);
            userService.saveProfile(currentUser, profile);
            currentUser.setEmail(updatedUser.getEmail());
            currentUser.setPhone(updatedUser.getPhone());
            updateRealNameAuthButton();
            if (showMessage) {
                JOptionPane.showMessageDialog(this, "资料已保存");
            }
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
            return false;
        }
    }

    private void loadProfileNotes(String notes) {
        notesArea.setText("");
        if (notes == null || notes.isBlank()) {
            return;
        }
        for (String line : notes.split("\\R")) {
            if (line.startsWith("首选联系方式：")) {
                selectComboValue(preferredContactBox, line.substring("首选联系方式：".length()));
            } else if (line.startsWith("通知偏好：")) {
                selectComboValue(notificationBox, line.substring("通知偏好：".length()));
            } else if (line.startsWith("备注：")) {
                notesArea.append(line.substring("备注：".length()));
            } else {
                if (!notesArea.getText().isBlank()) {
                    notesArea.append("\n");
                }
                notesArea.append(line);
            }
        }
    }

    private void selectComboValue(JComboBox<String> comboBox, String value) {
        for (int index = 0; index < comboBox.getItemCount(); index++) {
            if (comboBox.getItemAt(index).equals(value)) {
                comboBox.setSelectedIndex(index);
                return;
            }
        }
    }

    private String formatProfileNotes() {
        return "首选联系方式：" + preferredContactBox.getSelectedItem()
            + "\n通知偏好：" + notificationBox.getSelectedItem()
            + "\n备注：" + notesArea.getText().trim();
    }

    private void showRealNameAuthDialog() {
        JTextField authNameField = new JTextField(realNameField.getText(), 20);
        JTextField authIdCardField = new JTextField(idCardField.getText(), 24);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.add(new JLabel("真实姓名"));
        form.add(authNameField);
        form.add(new JLabel("证件号码"));
        form.add(authIdCardField);
        panel.add(form, BorderLayout.CENTER);
        int option = JOptionPane.showConfirmDialog(this, panel,
            "实名认证", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return;
        }
        if (authNameField.getText().isBlank() || authIdCardField.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "真实姓名和证件号码不能为空。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        realNameField.setText(authNameField.getText().trim());
        idCardField.setText(authIdCardField.getText().trim());
        if (saveProfile(false)) {
            JOptionPane.showMessageDialog(this, "实名认证已完成");
        }
    }

    private void updateRealNameAuthButton() {
        boolean verified = idCardField.getText() != null && !idCardField.getText().isBlank();
        realNameAuthButton.setText(verified ? "已实名认证" : "实名认证");
        realNameAuthButton.setEnabled(!verified);
    }

    private void showSelectedTicketDetail() {
        CrossTicketDTO ticket = selectedTicket();
        if (ticket == null || ticket.getItem() == null) {
            JOptionPane.showMessageDialog(this, "请先选择一条工单。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        showTicketDetailDialog(ticket.getItem().getItemId());
    }

    private CrossTicketDTO selectedTicket() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(selectedRow);
        return tableModel.getTicketAt(modelRow);
    }

    private void showTicketDetailDialog(Long itemId) {
        JDialog dialog = new JDialog(mainFrame, "工单详情 #" + itemId, true);
        dialog.setLayout(new BorderLayout());
        JTextArea detailArea = new JTextArea(22, 72);
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        dialog.add(new JScrollPane(detailArea), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refreshButton = new JButton("刷新");
        JButton replyButton = new JButton("追加回复");
        JButton rateButton = new JButton("评价");
        JButton closeButton = new JButton("关闭");
        actions.add(refreshButton);
        actions.add(replyButton);
        actions.add(rateButton);
        actions.add(closeButton);
        dialog.add(actions, BorderLayout.SOUTH);

        Runnable refreshDetail = () -> loadTicketDetail(itemId, detailArea);
        refreshButton.addActionListener(event -> refreshDetail.run());
        replyButton.addActionListener(event -> {
            if (addCustomerReply(itemId)) {
                refreshDetail.run();
                loadOrders();
            }
        });
        rateButton.addActionListener(event -> {
            if (rateTicket(itemId)) {
                refreshDetail.run();
                loadOrders();
            }
        });
        closeButton.addActionListener(event -> dialog.dispose());

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        refreshDetail.run();
        dialog.setVisible(true);
    }

    private void loadTicketDetail(Long itemId, JTextArea detailArea) {
        detailArea.setText("正在加载工单详情...");
        new SwingWorker<ItemDetailDTO, Void>() {
            @Override
            protected ItemDetailDTO doInBackground() {
                return businessService.getTicketDetail(currentUser, itemId);
            }

            @Override
            protected void done() {
                try {
                    detailArea.setText(formatTicketDetail(get()));
                    detailArea.setCaretPosition(0);
                } catch (Exception ex) {
                    detailArea.setText("加载工单详情失败：" + ex.getMessage());
                }
            }
        }.execute();
    }

    private boolean addCustomerReply(Long itemId) {
        JTextArea replyArea = new JTextArea(6, 42);
        replyArea.setLineWrap(true);
        replyArea.setWrapStyleWord(true);
        int option = JOptionPane.showConfirmDialog(this, new JScrollPane(replyArea),
            "追加回复", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return false;
        }
        try {
            businessService.addCustomerReply(currentUser, itemId, replyArea.getText());
            JOptionPane.showMessageDialog(this, "回复已提交");
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
            return false;
        }
    }

    private boolean rateTicket(Long itemId) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JComboBox<Integer> ratingBox = new JComboBox<>(new Integer[] {5, 4, 3, 2, 1});
        JTextArea contentArea = new JTextArea(5, 42);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        JPanel ratingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ratingPanel.add(new JLabel("评分"));
        ratingPanel.add(ratingBox);
        panel.add(ratingPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(contentArea), BorderLayout.CENTER);
        int option = JOptionPane.showConfirmDialog(this, panel,
            "评价工单", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return false;
        }
        try {
            businessService.rateTicket(currentUser, itemId, (Integer) ratingBox.getSelectedItem(), contentArea.getText());
            JOptionPane.showMessageDialog(this, "评价已提交");
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
            return false;
        }
    }

    private String formatTicketDetail(ItemDetailDTO ticket) {
        StringBuilder builder = new StringBuilder();
        if (ticket.getItem() != null) {
            builder.append("工单编号：").append(ticket.getItem().getItemId()).append('\n');
            builder.append("标题：").append(nullToEmpty(ticket.getItem().getTitle())).append('\n');
            builder.append("分类：").append(categoryText(ticket.getItem().getCategoryId())).append('\n');
            builder.append("更新时间：").append(ticket.getItem().getUpdatedAt()).append('\n');
        }
        if (ticket.getOrder() != null) {
            builder.append("记录编号：").append(ticket.getOrder().getOrderId()).append('\n');
            builder.append("状态：").append(statusText(ticket.getOrder().getStatus())).append('\n');
            builder.append("金额：").append(ticket.getOrder().getAmount()).append('\n');
            builder.append("创建时间：").append(ticket.getOrder().getCreatedAt()).append('\n');
        }
        if (ticket.getUser() != null) {
            builder.append("提交人：").append(nullToEmpty(ticket.getUser().getUsername()))
                .append(" / ").append(nullToEmpty(ticket.getUser().getPhone()))
                .append(" / ").append(nullToEmpty(ticket.getUser().getEmail())).append('\n');
        }
        if (ticket.getProfile() != null) {
            builder.append("联系信息：")
                .append(nullToEmpty(ticket.getProfile().getRealName()))
                .append("，")
                .append(nullToEmpty(ticket.getProfile().getAddress()))
                .append('\n');
        }

        ItemDetail detail = ticket.getItemDetail();
        if (detail != null) {
            ItemDetail.Metadata metadata = detail.getMetadata();
            builder.append("优先级：")
                .append(metadata == null ? "" : nullToEmpty(metadata.getPriority()))
                .append('\n');
            builder.append("\n描述：\n")
                .append(nullToEmpty(detail.getDescription()))
                .append('\n');
        }

        builder.append("\n沟通记录：\n");
        if (ticket.getComments() == null || ticket.getComments().isEmpty()) {
            builder.append("暂无沟通记录。\n");
        } else {
            for (Comment comment : ticket.getComments()) {
                builder.append("- ")
                    .append(formatBeijingTime(comment.getCreatedAt()))
                    .append(" 用户 ")
                    .append(nullToEmpty(comment.getUserId()))
                    .append(" ")
                    .append(commentTypeText(comment))
                    .append('\n')
                    .append("  ")
                    .append(nullToEmpty(comment.getContent()))
                    .append('\n');
                if (comment.getRating() != null && !comment.getRating().isBlank()) {
                    builder.append("  评分：").append(comment.getRating()).append('\n');
                }
            }
        }
        return builder.toString();
    }

    private String commentTypeText(Comment comment) {
        if (comment.getTags() == null || comment.getTags().isEmpty()) {
            return "";
        }
        String tag = comment.getTags().get(0);
        return switch (tag) {
            case "CUSTOMER_REPLY" -> "客户回复";
            case "AGENT_REPLY" -> "客服回复";
            case "CUSTOMER_RATING" -> "客户评价";
            default -> tag;
        };
    }

    private String categoryText(Long categoryId) {
        if (categoryId == null) {
            return "";
        }
        return categoryNameById.getOrDefault(categoryId, String.valueOf(categoryId));
    }

    private String formatBeijingTime(Instant instant) {
        if (instant == null) {
            return "";
        }
        return BEIJING_TIME_FORMATTER.format(instant.atZone(BEIJING_ZONE));
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case 0 -> "待处理";
            case 1 -> "处理中";
            case 2 -> "已完成";
            case 3 -> "已关闭";
            case 4 -> "已取消";
            default -> "未知(" + status + ")";
        };
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record CategoryOption(Long categoryId, String name) {
        @Override
        public String toString() {
            return name == null ? "" : name;
        }
    }

    private record PriorityOption(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record StatusOption(Integer status, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
