package com.ticket.ui.admin;

import com.ticket.model.HandlingMacro;
import com.ticket.model.KnowledgeArticle;
import com.ticket.model.ReplyTemplate;
import com.ticket.model.User;
import com.ticket.service.KnowledgeService;
import com.ticket.ui.theme.AppTheme;
import com.ticket.ui.theme.WindowIconUtil;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.table.DefaultTableModel;

/** Knowledge articles and reusable service actions. All controls use the project theme on macOS. */
public class KnowledgeManagementPanel extends JPanel {
    private final User actor;
    private final KnowledgeService service;
    private final DefaultTableModel articleModel = model("ID", "标题", "状态", "分类 ID", "摘要", "更新时间");
    private final DefaultTableModel templateModel = model("ID", "模板名称", "分类 ID", "启用", "内容");
    private final DefaultTableModel macroModel = model("ID", "宏名称", "回复模板", "目标状态");

    public KnowledgeManagementPanel(User actor, KnowledgeService service) {
        this.actor = actor;
        this.service = service;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.PAGE);
        add(AppTheme.pageHeader("知识库、快捷回复与处理宏", "统一维护客户自助文章和管理员处理工具"), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.setUI(new BasicTabbedPaneUI());
        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 14f));
        tabs.setBackground(AppTheme.SURFACE);
        tabs.setForeground(AppTheme.TEXT);
        tabs.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        tabs.addTab("知识文章", articlePanel());
        tabs.addTab("快捷回复", templatePanel());
        tabs.addTab("处理宏", macroPanel());
        add(tabs, BorderLayout.CENTER);
        refresh();
    }

    private JPanel articlePanel() {
        JTable table = new JTable(articleModel);
        AppTheme.styleTable(table);
        JButton create = new JButton("新建文章");
        JButton refresh = new JButton("刷新");
        AppTheme.primary(create);
        AppTheme.secondary(refresh);
        create.addActionListener(event -> createArticle());
        refresh.addActionListener(event -> refresh());
        return withToolbar(table, create, refresh);
    }

    private JPanel templatePanel() {
        JTable table = new JTable(templateModel);
        AppTheme.styleTable(table);
        JButton create = new JButton("新建快捷回复");
        JButton refresh = new JButton("刷新");
        AppTheme.primary(create);
        AppTheme.secondary(refresh);
        create.addActionListener(event -> createTemplate());
        refresh.addActionListener(event -> refresh());
        return withToolbar(table, create, refresh);
    }

    private JPanel macroPanel() {
        JTable table = new JTable(macroModel);
        AppTheme.styleTable(table);
        JButton create = new JButton("新建处理宏");
        JButton refresh = new JButton("刷新");
        AppTheme.primary(create);
        AppTheme.secondary(refresh);
        create.addActionListener(event -> createMacro());
        refresh.addActionListener(event -> refresh());
        return withToolbar(table, create, refresh);
    }

    private JPanel withToolbar(JTable table, JButton... buttons) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(AppTheme.PAGE);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        JPanel toolbar = AppTheme.surface(new FlowLayout(FlowLayout.LEFT, 8, 2));
        for (JButton button : buttons) toolbar.add(button);
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(AppTheme.scroll(table), BorderLayout.CENTER);
        return panel;
    }

    private void refresh() {
        try {
            articleModel.setRowCount(0);
            for (KnowledgeArticle value : service.listArticles(actor)) articleModel.addRow(new Object[]{
                value.getArticleId(), value.getTitle(), value.getStatus(), value.getCategoryId(),
                value.getSummary(), value.getUpdatedAt()});
            templateModel.setRowCount(0);
            for (ReplyTemplate value : service.allTemplates(actor)) templateModel.addRow(new Object[]{
                value.getTemplateId(), value.getTemplateName(), value.getCategoryId(), value.isEnabled(),
                value.getContent()});
            macroModel.setRowCount(0);
            for (HandlingMacro value : service.macros(actor)) macroModel.addRow(new Object[]{value.getMacroId(),
                value.getMacroName(), value.getReplyTemplate() == null ? "" : value.getReplyTemplate().getTemplateName(),
                statusText(value.getTargetStatus())});
        } catch (Exception ex) {
            error("加载失败", ex);
        }
    }

    private void createArticle() {
        JTextField title = input(30);
        JTextField summary = input(30);
        JTextField keywords = input(30);
        JTextField category = input(10);
        JComboBox<String> status = new JComboBox<>(new String[]{"PUBLISHED", "DRAFT"});
        AppTheme.styleComboBox(status);
        JTextArea content = area(10, 42);
        JScrollPane contentScroll = AppTheme.scroll(content);
        contentScroll.setPreferredSize(new Dimension(500, 210));
        JPanel form = form(new Object[]{"标题", title, "摘要", summary, "关键词", keywords,
            "分类 ID（可空）", category, "状态", status, "正文", contentScroll});
        if (!showFormDialog("新建知识文章", "填写面向客户的解决说明", form, new Dimension(720, 610), title)) return;
        try {
            KnowledgeArticle article = new KnowledgeArticle();
            article.setTitle(title.getText());
            article.setSummary(summary.getText());
            article.setKeywords(keywords.getText());
            article.setCategoryId(number(category.getText()));
            article.setStatus(String.valueOf(status.getSelectedItem()));
            article.setContent(content.getText());
            service.createArticle(actor, article);
            refresh();
        } catch (Exception ex) {
            error("保存失败", ex);
        }
    }

    private void createTemplate() {
        JTextField name = input(30);
        JTextField category = input(10);
        JTextArea content = area(8, 42);
        JScrollPane contentScroll = AppTheme.scroll(content);
        contentScroll.setPreferredSize(new Dimension(500, 190));
        JPanel form = form(new Object[]{"模板名称", name, "分类 ID（可空）", category, "回复内容", contentScroll});
        if (!showFormDialog("新建快捷回复", "创建管理员可一键发送的标准回复", form,
                new Dimension(700, 500), name)) return;
        try {
            ReplyTemplate template = new ReplyTemplate();
            template.setTemplateName(name.getText());
            template.setCategoryId(number(category.getText()));
            template.setContent(content.getText());
            template.setEnabled(true);
            service.createTemplate(actor, template);
            refresh();
        } catch (Exception ex) {
            error("保存失败", ex);
        }
    }

    private void createMacro() {
        List<ReplyTemplate> templates = service.allTemplates(actor);
        JTextField name = input(30);
        JComboBox<Object> template = new JComboBox<>();
        template.addItem("无回复模板");
        for (ReplyTemplate value : templates) if (value.isEnabled()) template.addItem(value);
        AppTheme.styleComboBox(template);
        JComboBox<StatusChoice> target = new JComboBox<>(new StatusChoice[]{new StatusChoice(null, "不改变状态"),
            new StatusChoice(1, "处理中"), new StatusChoice(2, "已完成"), new StatusChoice(4, "已取消"),
            new StatusChoice(5, "等待客户回复"), new StatusChoice(6, "暂挂")});
        AppTheme.styleComboBox(target);
        JPanel form = form(new Object[]{"宏名称", name, "快捷回复", template, "目标状态", target});
        if (!showFormDialog("新建处理宏", "一次执行标准回复和工单状态流转", form,
                new Dimension(620, 390), name)) return;
        try {
            HandlingMacro macro = new HandlingMacro();
            macro.setMacroName(name.getText());
            macro.setEnabled(true);
            if (template.getSelectedItem() instanceof ReplyTemplate value) macro.setReplyTemplate(value);
            macro.setTargetStatus(((StatusChoice) target.getSelectedItem()).value());
            service.createMacro(actor, macro);
            refresh();
        } catch (Exception ex) {
            error("保存失败", ex);
        }
    }

    private JPanel form(Object[] fields) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        for (int i = 0; i < fields.length; i += 2) {
            int row = i / 2;
            Component field = (Component) fields[i + 1];
            boolean expanding = field instanceof JScrollPane;
            GridBagConstraints labelConstraints = new GridBagConstraints();
            labelConstraints.gridx = 0;
            labelConstraints.gridy = row;
            labelConstraints.anchor = GridBagConstraints.NORTHWEST;
            labelConstraints.insets = new Insets(9, 0, 9, 14);
            panel.add(new JLabel(String.valueOf(fields[i])), labelConstraints);

            GridBagConstraints fieldConstraints = new GridBagConstraints();
            fieldConstraints.gridx = 1;
            fieldConstraints.gridy = row;
            fieldConstraints.weightx = 1;
            fieldConstraints.weighty = expanding ? 1 : 0;
            fieldConstraints.fill = expanding ? GridBagConstraints.BOTH : GridBagConstraints.HORIZONTAL;
            fieldConstraints.anchor = GridBagConstraints.NORTHWEST;
            fieldConstraints.insets = new Insets(5, 0, 5, 0);
            panel.add(field, fieldConstraints);
        }
        return panel;
    }

    private boolean showFormDialog(String title, String description, JPanel form, Dimension size, Component focus) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = owner instanceof Frame frame
            ? new JDialog(frame, title, true)
            : new JDialog(owner instanceof Dialog existing ? existing : null, title, Dialog.ModalityType.APPLICATION_MODAL);
        WindowIconUtil.apply(dialog);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        boolean[] accepted = {false};

        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 20f));
        JPanel introduction = new JPanel(new BorderLayout(0, 5));
        introduction.setOpaque(false);
        introduction.add(heading, BorderLayout.NORTH);
        introduction.add(AppTheme.muted(description), BorderLayout.SOUTH);

        JPanel formCard = AppTheme.surface(new BorderLayout());
        formCard.add(form, BorderLayout.CENTER);
        JButton cancel = new JButton("取消");
        JButton save = new JButton("保存");
        AppTheme.secondary(cancel);
        AppTheme.primary(save);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancel);
        actions.add(save);
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            accepted[0] = true;
            dialog.dispose();
        });

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBackground(AppTheme.PAGE);
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 16, 18));
        content.add(introduction, BorderLayout.NORTH);
        content.add(formCard, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(save);
        AppTheme.closeOnEscape(dialog);
        dialog.setSize(size);
        dialog.setMinimumSize(size);
        dialog.setLocationRelativeTo(this);
        SwingUtilities.invokeLater(focus::requestFocusInWindow);
        dialog.setVisible(true);
        return accepted[0];
    }

    private JTextField input(int columns) {
        JTextField field = new JTextField(columns);
        AppTheme.styleInput(field);
        return field;
    }

    private JTextArea area(int rows, int columns) {
        JTextArea field = new JTextArea(rows, columns);
        field.setLineWrap(true);
        field.setWrapStyleWord(true);
        field.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return field;
    }

    private Long number(String value) {
        return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
    }

    private void error(String title, Exception ex) {
        javax.swing.JOptionPane.showMessageDialog(this, ex.getMessage(), title,
            javax.swing.JOptionPane.WARNING_MESSAGE);
    }

    private String statusText(Integer status) {
        if (status == null) return "不改变";
        return switch (status) {
            case 1 -> "处理中";
            case 2 -> "已完成";
            case 4 -> "已取消";
            case 5 -> "等待客户回复";
            case 6 -> "暂挂";
            default -> String.valueOf(status);
        };
    }

    private static DefaultTableModel model(Object... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private record StatusChoice(Integer value, String label) {
        @Override public String toString() { return label; }
    }
}
