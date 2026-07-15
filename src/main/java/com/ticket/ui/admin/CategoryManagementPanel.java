package com.ticket.ui.admin;

import com.ticket.dto.CategoryOverviewDTO;
import com.ticket.model.User;
import com.ticket.service.CategoryService;
import com.ticket.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/** Tree-oriented category management view for the strict two-level taxonomy. */
final class CategoryManagementPanel extends JPanel {
    private final User actor;
    private final CategoryService categoryService;
    private final Runnable onCategoriesChanged;
    private final Consumer<String> statusConsumer;
    private final List<CategoryOverviewDTO> categories = new ArrayList<>();

    private final DefaultMutableTreeNode invisibleRoot = new DefaultMutableTreeNode("分类");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(invisibleRoot);
    private final JTree categoryTree = new JTree(treeModel);
    private final JTextField searchField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JComboBox<ParentOption> parentBox = new JComboBox<>();
    private final JLabel totalValue = metricValue();
    private final JLabel firstLevelValue = metricValue();
    private final JLabel secondLevelValue = metricValue();
    private final JLabel attentionValue = metricValue();
    private final JLabel breadcrumbLabel = new JLabel("请选择一个分类");
    private final JLabel modeLabel = AppTheme.muted("从左侧分类树选择分类后可查看和修改");
    private final JLabel levelValue = new JLabel("—");
    private final JLabel childValue = new JLabel("—");
    private final JLabel ticketValue = new JLabel("—");
    private final JLabel warningLabel = new JLabel(" ");
    private final JLabel feedbackLabel = new JLabel("正在加载分类…");
    private final JButton newRootButton = new JButton("新建一级分类");
    private final JButton newChildButton = new JButton("新增子分类");
    private final JButton refreshButton = new JButton("刷新");
    private final JButton saveButton = new JButton("保存修改");
    private final JButton cancelButton = new JButton("取消");
    private final JButton deleteButton = new JButton("删除分类");

    private Long selectedCategoryId;
    private Long previousCategoryId;
    private boolean creating;
    private boolean busy;
    private int loadVersion;

    CategoryManagementPanel(User actor, CategoryService categoryService,
                            Runnable onCategoriesChanged, Consumer<String> statusConsumer) {
        this.actor = actor;
        this.categoryService = categoryService;
        this.onCategoriesChanged = onCategoriesChanged == null ? () -> { } : onCategoriesChanged;
        this.statusConsumer = statusConsumer == null ? message -> { } : statusConsumer;
        buildUi();
        bindActions();
        loadCategories(null);
    }

    private void buildUi() {
        setLayout(new BorderLayout());
        setBackground(AppTheme.PAGE);
        add(AppTheme.pageHeader("分类管理", "按业务层级查看分类、影响工单与异常数据",
            refreshButton, newRootButton), BorderLayout.NORTH);

        AppTheme.secondary(refreshButton);
        AppTheme.primary(newRootButton);
        AppTheme.secondary(newChildButton);
        AppTheme.primary(saveButton);
        AppTheme.secondary(cancelButton);
        AppTheme.danger(deleteButton);
        AppTheme.styleInput(searchField);
        AppTheme.styleInput(nameField);
        AppTheme.styleComboBox(parentBox);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(AppTheme.PAGE);
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JPanel metrics = new JPanel(new GridLayout(1, 4, 10, 0));
        metrics.setOpaque(false);
        metrics.setPreferredSize(new Dimension(0, 88));
        metrics.add(metricCard("全部分类", totalValue));
        metrics.add(metricCard("一级分类", firstLevelValue));
        metrics.add(metricCard("二级分类", secondLevelValue));
        metrics.add(metricCard("需治理", attentionValue));
        content.add(metrics, BorderLayout.NORTH);

        JPanel treeCard = AppTheme.surface(new BorderLayout(0, 10));
        JPanel treeToolbar = new JPanel(new BorderLayout(8, 0));
        treeToolbar.setOpaque(false);
        searchField.putClientProperty("JTextField.placeholderText", "搜索分类");
        treeToolbar.add(new JLabel("搜索"), BorderLayout.WEST);
        treeToolbar.add(searchField, BorderLayout.CENTER);
        treeToolbar.add(newChildButton, BorderLayout.EAST);
        treeCard.add(treeToolbar, BorderLayout.NORTH);
        configureTree();
        JScrollPane treeScroll = AppTheme.scroll(categoryTree);
        treeScroll.setBorder(BorderFactory.createLineBorder(AppTheme.BORDER));
        treeCard.add(treeScroll, BorderLayout.CENTER);

        JPanel detailCard = buildDetailCard();
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeCard, detailCard);
        splitPane.setResizeWeight(0.54);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);
        content.add(splitPane, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
    }

    private JPanel buildDetailCard() {
        JPanel card = AppTheme.surface(new BorderLayout(0, 14));
        JPanel heading = new JPanel(new BorderLayout(0, 5));
        heading.setOpaque(false);
        breadcrumbLabel.setFont(breadcrumbLabel.getFont().deriveFont(Font.BOLD, 18f));
        heading.add(breadcrumbLabel, BorderLayout.NORTH);
        heading.add(modeLabel, BorderLayout.CENTER);
        card.add(heading, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 10, 12);
        addField(form, constraints, "分类层级", levelValue);
        addField(form, constraints, "子分类数量", childValue);
        addField(form, constraints, "关联工单", ticketValue);
        addField(form, constraints, "分类名称", nameField);
        addField(form, constraints, "所属一级分类", parentBox);
        warningLabel.setForeground(AppTheme.WARNING);
        constraints.gridx = 0;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.insets = new Insets(2, 0, 0, 0);
        form.add(warningLabel, constraints);
        card.add(form, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);
        feedbackLabel.setForeground(AppTheme.MUTED);
        footer.add(feedbackLabel, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(deleteButton);
        actions.add(cancelButton);
        actions.add(saveButton);
        footer.add(actions, BorderLayout.SOUTH);
        card.add(footer, BorderLayout.SOUTH);
        setFormEnabled(false);
        return card;
    }

    private void addField(JPanel form, GridBagConstraints constraints, String label, Component component) {
        constraints.gridx = 0;
        constraints.gridwidth = 1;
        constraints.weightx = 0;
        constraints.insets = new Insets(0, 0, 10, 12);
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setForeground(AppTheme.MUTED);
        form.add(fieldLabel, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.insets = new Insets(0, 0, 10, 0);
        if (component instanceof javax.swing.JComponent swingComponent) {
            swingComponent.setPreferredSize(new Dimension(260, 36));
        }
        form.add(component, constraints);
        constraints.gridy++;
    }

    private void configureTree() {
        categoryTree.setRootVisible(false);
        categoryTree.setShowsRootHandles(true);
        categoryTree.setRowHeight(36);
        categoryTree.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        categoryTree.setCellRenderer(new CategoryTreeRenderer());
        categoryTree.getSelectionModel().setSelectionMode(
            javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION);
    }

    private void bindActions() {
        refreshButton.addActionListener(event -> loadCategories(selectedCategoryId));
        newRootButton.addActionListener(event -> enterCreateMode(null));
        newChildButton.addActionListener(event -> {
            CategoryOverviewDTO selected = find(selectedCategoryId);
            if (selected == null) {
                showFeedback("请先选择一个一级分类或其子分类", true);
                return;
            }
            Long parentId = selected.getParentId() == null ? selected.getCategoryId() : selected.getParentId();
            enterCreateMode(parentId);
        });
        cancelButton.addActionListener(event -> {
            if (previousCategoryId != null) selectTreeNode(previousCategoryId);
            else clearDetail();
        });
        saveButton.addActionListener(event -> saveCategory());
        deleteButton.addActionListener(event -> deleteCategory());
        nameField.addActionListener(event -> saveButton.doClick());
        categoryTree.addTreeSelectionListener(event -> {
            DefaultMutableTreeNode node = selectedNode();
            if (node != null && node.getUserObject() instanceof CategoryOverviewDTO category) {
                showCategory(category);
            }
        });
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { rebuildTree(selectedCategoryId); }
            @Override public void removeUpdate(DocumentEvent event) { rebuildTree(selectedCategoryId); }
            @Override public void changedUpdate(DocumentEvent event) { rebuildTree(selectedCategoryId); }
        });
    }

    private void loadCategories(Long preferredSelection) {
        int requestVersion = ++loadVersion;
        setBusy(true, "正在加载分类结构…");
        new SwingWorker<List<CategoryOverviewDTO>, Void>() {
            @Override
            protected List<CategoryOverviewDTO> doInBackground() {
                return categoryService.listCategoryOverview(actor);
            }

            @Override
            protected void done() {
                if (requestVersion != loadVersion) return;
                try {
                    categories.clear();
                    categories.addAll(get());
                    updateMetrics();
                    rebuildTree(preferredSelection);
                    showFeedback("已加载 " + categories.size() + " 个分类", false);
                    statusConsumer.accept("分类管理：共 " + categories.size() + " 个分类");
                } catch (Exception ex) {
                    showFeedback("加载失败：" + rootMessage(ex), true);
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void updateMetrics() {
        long first = categories.stream().filter(category -> category.getLevel() == 1).count();
        long second = categories.stream().filter(category -> category.getLevel() == 2).count();
        long attention = categories.stream().filter(CategoryOverviewDTO::requiresAttention).count();
        totalValue.setText(String.valueOf(categories.size()));
        firstLevelValue.setText(String.valueOf(first));
        secondLevelValue.setText(String.valueOf(second));
        attentionValue.setText(String.valueOf(attention));
        attentionValue.setForeground(attention > 0 ? AppTheme.WARNING : AppTheme.SUCCESS);
    }

    private void rebuildTree(Long preferredSelection) {
        String keyword = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        invisibleRoot.removeAllChildren();
        Map<Long, CategoryOverviewDTO> byId = new LinkedHashMap<>();
        categories.forEach(category -> byId.put(category.getCategoryId(), category));
        List<CategoryOverviewDTO> anomalies = new ArrayList<>();
        for (CategoryOverviewDTO rootCategory : categories) {
            if (rootCategory.getParentId() != null) continue;
            List<CategoryOverviewDTO> children = categories.stream()
                .filter(category -> Objects.equals(rootCategory.getCategoryId(), category.getParentId()))
                .filter(category -> category.getLevel() == 2)
                .toList();
            boolean rootMatches = matches(rootCategory, keyword);
            List<CategoryOverviewDTO> visibleChildren = rootMatches || keyword.isBlank()
                ? children : children.stream().filter(category -> matches(category, keyword)).toList();
            if (rootMatches || !visibleChildren.isEmpty() || keyword.isBlank()) {
                DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(rootCategory);
                visibleChildren.forEach(category -> rootNode.add(new DefaultMutableTreeNode(category)));
                invisibleRoot.add(rootNode);
            }
        }
        categories.stream().filter(category -> category.isHierarchyAnomaly()
            || (category.getParentId() != null && !byId.containsKey(category.getParentId())))
            .filter(category -> matches(category, keyword)).forEach(anomalies::add);
        if (!anomalies.isEmpty()) {
            DefaultMutableTreeNode anomalyGroup = new DefaultMutableTreeNode("⚠ 层级异常");
            anomalies.forEach(category -> anomalyGroup.add(new DefaultMutableTreeNode(category)));
            invisibleRoot.add(anomalyGroup);
        }
        treeModel.reload();
        expandAll();
        if (preferredSelection != null && selectTreeNode(preferredSelection)) return;
        if (selectedCategoryId != null && selectTreeNode(selectedCategoryId)) return;
        selectFirstCategory();
    }

    private boolean matches(CategoryOverviewDTO category, String keyword) {
        return keyword.isBlank() || category.getName().toLowerCase(Locale.ROOT).contains(keyword)
            || String.valueOf(category.getCategoryId()).contains(keyword)
            || (category.getParentName() != null && category.getParentName().toLowerCase(Locale.ROOT).contains(keyword));
    }

    private void showCategory(CategoryOverviewDTO category) {
        creating = false;
        selectedCategoryId = category.getCategoryId();
        previousCategoryId = category.getCategoryId();
        breadcrumbLabel.setText(category.getParentName() == null ? category.getName()
            : category.getParentName() + "  /  " + category.getName());
        modeLabel.setText("正在编辑分类 #" + category.getCategoryId());
        levelValue.setText(category.getLevel() == 1 ? "一级分类" : category.getLevel() == 2 ? "二级分类" : "层级异常");
        childValue.setText(category.getChildCount() + " 个");
        ticketValue.setText(category.getDirectTicketCount() + " 张直接关联 · "
            + category.getTotalTicketCount() + " 张总影响");
        nameField.setText(category.getName());
        rebuildParentOptions(category.getCategoryId(), category.getParentId());
        boolean canMove = category.getParentId() != null || category.getChildCount() == 0;
        parentBox.setEnabled(canMove && !busy);
        parentBox.setToolTipText(canMove ? "选择一级分类；不选择表示一级分类"
            : "该一级分类仍有子分类，不能直接改为二级分类");
        warningLabel.setText(category.isHierarchyAnomaly() ? "⚠ 当前分类超过两级，需要移动或合并"
            : category.isDuplicateName() ? "⚠ 同一层级存在重名分类，建议合并或重命名" : " ");
        saveButton.setText("保存修改");
        setFormEnabled(true);
        updateDeleteState(category);
        showFeedback(category.getChildCount() > 0 || category.getDirectTicketCount() > 0
            ? "该分类仍有关联数据，不能直接删除" : "可以修改或删除该分类", false);
    }

    private void enterCreateMode(Long parentId) {
        creating = true;
        previousCategoryId = selectedCategoryId;
        selectedCategoryId = null;
        categoryTree.clearSelection();
        breadcrumbLabel.setText(parentId == null ? "新建一级分类" : "新建二级分类");
        modeLabel.setText(parentId == null ? "一级分类用于业务归组" : "二级分类用于具体问题细分");
        levelValue.setText(parentId == null ? "一级分类" : "二级分类");
        childValue.setText("0 个");
        ticketValue.setText("0 张");
        nameField.setText("");
        rebuildParentOptions(null, parentId);
        parentBox.setEnabled(parentId != null && !busy);
        warningLabel.setText(" ");
        saveButton.setText("创建分类");
        deleteButton.setEnabled(false);
        deleteButton.setToolTipText("新分类尚未保存");
        setFormEnabled(true);
        showFeedback("填写名称后保存；失败时内容会保留", false);
        SwingUtilities.invokeLater(nameField::requestFocusInWindow);
    }

    private void rebuildParentOptions(Long excludedCategoryId, Long selectedParentId) {
        parentBox.removeAllItems();
        parentBox.addItem(new ParentOption(null, "不选择（一级分类）"));
        int selectedIndex = 0;
        for (CategoryOverviewDTO category : categories) {
            if (category.getLevel() != 1 || Objects.equals(category.getCategoryId(), excludedCategoryId)) continue;
            parentBox.addItem(new ParentOption(category.getCategoryId(), category.getName()));
            if (Objects.equals(category.getCategoryId(), selectedParentId)) {
                selectedIndex = parentBox.getItemCount() - 1;
            }
        }
        parentBox.setSelectedIndex(selectedIndex);
    }

    private void saveCategory() {
        if (busy) return;
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            showFeedback("分类名称不能为空", true);
            nameField.requestFocusInWindow();
            return;
        }
        Long categoryId = selectedCategoryId;
        ParentOption option = (ParentOption) parentBox.getSelectedItem();
        Long parentId = option == null ? null : option.categoryId();
        setBusy(true, creating ? "正在创建分类…" : "正在保存修改…");
        new SwingWorker<Long, Void>() {
            @Override
            protected Long doInBackground() {
                if (categoryId == null) return categoryService.createCategory(actor, name, parentId);
                categoryService.updateCategory(actor, categoryId, name, parentId);
                return categoryId;
            }

            @Override
            protected void done() {
                try {
                    Long savedId = get();
                    creating = false;
                    selectedCategoryId = savedId;
                    previousCategoryId = savedId;
                    onCategoriesChanged.run();
                    showFeedback("分类已保存", false);
                    loadCategories(savedId);
                } catch (Exception ex) {
                    showFeedback("保存失败：" + rootMessage(ex), true);
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void deleteCategory() {
        CategoryOverviewDTO category = find(selectedCategoryId);
        if (busy || category == null) return;
        if (category.getChildCount() > 0 || category.getDirectTicketCount() > 0) {
            showFeedback("该分类有子分类或关联工单，不能删除", true);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
            "确认删除“" + category.getName() + "” (#" + category.getCategoryId() + ")？\n"
                + "子分类：0 · 关联工单：0",
            "删除分类", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        setBusy(true, "正在删除分类…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                categoryService.deleteCategory(actor, category.getCategoryId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    selectedCategoryId = null;
                    previousCategoryId = null;
                    onCategoriesChanged.run();
                    showFeedback("分类已删除", false);
                    loadCategories(null);
                } catch (Exception ex) {
                    showFeedback("删除失败：" + rootMessage(ex), true);
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void updateDeleteState(CategoryOverviewDTO category) {
        boolean canDelete = category.getChildCount() == 0 && category.getDirectTicketCount() == 0 && !busy;
        deleteButton.setEnabled(canDelete);
        deleteButton.setToolTipText(canDelete ? "删除空分类"
            : "存在子分类或关联工单，不能删除");
    }

    private void setFormEnabled(boolean enabled) {
        nameField.setEnabled(enabled && !busy);
        saveButton.setEnabled(enabled && !busy);
        cancelButton.setEnabled(enabled && !busy);
        newChildButton.setEnabled(!busy && !categories.isEmpty());
        newRootButton.setEnabled(!busy);
        refreshButton.setEnabled(!busy);
        searchField.setEnabled(!busy);
        if (!enabled) {
            parentBox.setEnabled(false);
            deleteButton.setEnabled(false);
        }
    }

    private void setBusy(boolean busy, String message) {
        this.busy = busy;
        CategoryOverviewDTO selected = find(selectedCategoryId);
        setFormEnabled(creating || selected != null);
        if (!busy && selected != null) {
            parentBox.setEnabled(selected.getParentId() != null || selected.getChildCount() == 0);
            updateDeleteState(selected);
        } else if (busy) {
            parentBox.setEnabled(false);
            deleteButton.setEnabled(false);
        }
        if (message != null) showFeedback(message, false);
    }

    private void clearDetail() {
        creating = false;
        selectedCategoryId = null;
        previousCategoryId = null;
        categoryTree.clearSelection();
        breadcrumbLabel.setText("请选择一个分类");
        modeLabel.setText("从左侧分类树选择分类后可查看和修改");
        levelValue.setText("—");
        childValue.setText("—");
        ticketValue.setText("—");
        nameField.setText("");
        parentBox.removeAllItems();
        warningLabel.setText(" ");
        setFormEnabled(false);
    }

    private CategoryOverviewDTO find(Long categoryId) {
        if (categoryId == null) return null;
        return categories.stream().filter(category -> categoryId.equals(category.getCategoryId()))
            .findFirst().orElse(null);
    }

    private DefaultMutableTreeNode selectedNode() {
        Object component = categoryTree.getLastSelectedPathComponent();
        return component instanceof DefaultMutableTreeNode node ? node : null;
    }

    private boolean selectTreeNode(Long categoryId) {
        Enumeration<?> nodes = invisibleRoot.depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            Object next = nodes.nextElement();
            if (next instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof CategoryOverviewDTO category
                    && categoryId.equals(category.getCategoryId())) {
                TreePath path = new TreePath(node.getPath());
                categoryTree.setSelectionPath(path);
                categoryTree.scrollPathToVisible(path);
                return true;
            }
        }
        return false;
    }

    private void selectFirstCategory() {
        Enumeration<?> nodes = invisibleRoot.preorderEnumeration();
        while (nodes.hasMoreElements()) {
            Object next = nodes.nextElement();
            if (next instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof CategoryOverviewDTO category) {
                selectTreeNode(category.getCategoryId());
                return;
            }
        }
        clearDetail();
    }

    private void expandAll() {
        for (int row = 0; row < categoryTree.getRowCount(); row++) categoryTree.expandRow(row);
    }

    private void showFeedback(String message, boolean error) {
        feedbackLabel.setText(message == null ? " " : message);
        feedbackLabel.setForeground(error ? AppTheme.DANGER : AppTheme.MUTED);
    }

    private static JLabel metricValue() {
        JLabel label = new JLabel("—");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 23f));
        return label;
    }

    private static JPanel metricCard(String title, JLabel value) {
        JPanel card = AppTheme.surface(new BorderLayout(0, 5));
        card.add(AppTheme.muted(title), BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record ParentOption(Long categoryId, String label) {
        @Override public String toString() { return label; }
    }

    private static final class CategoryTreeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                       boolean expanded, boolean leaf, int row, boolean focused) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, false);
            setBorder(BorderFactory.createEmptyBorder(0, 7, 0, 7));
            setIcon(null);
            setOpenIcon(null);
            setClosedIcon(null);
            setLeafIcon(null);
            if (value instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof CategoryOverviewDTO category) {
                String prefix = category.requiresAttention() ? "⚠  " : "";
                setText(prefix + category.getName() + "   ·   " + category.getTotalTicketCount() + " 张工单");
                setFont(getFont().deriveFont(category.getLevel() == 1 ? Font.BOLD : Font.PLAIN));
                if (!selected && category.requiresAttention()) setForeground(AppTheme.WARNING);
            } else if (value instanceof DefaultMutableTreeNode node) {
                setText(String.valueOf(node.getUserObject()));
                setForeground(AppTheme.WARNING);
                setFont(getFont().deriveFont(Font.BOLD));
            }
            if (selected) {
                setBackgroundSelectionColor(AppTheme.TABLE_SELECTED);
                setTextSelectionColor(AppTheme.TEXT);
            }
            return this;
        }
    }
}
