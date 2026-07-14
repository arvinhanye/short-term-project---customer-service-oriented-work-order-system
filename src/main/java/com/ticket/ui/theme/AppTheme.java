package com.ticket.ui.theme;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.JTableHeader;
import javax.swing.table.DefaultTableCellRenderer;

/** Shared visual tokens for the Swing client. This class intentionally uses only JDK Swing APIs. */
public final class AppTheme {
    public static final Color PAGE = new Color(245, 246, 247);
    public static final Color SURFACE = Color.WHITE;
    public static final Color TEXT = new Color(31, 35, 41);
    public static final Color MUTED = new Color(100, 106, 115);
    public static final Color PRIMARY = new Color(51, 112, 255);
    public static final Color PRIMARY_DARK = new Color(36, 91, 219);
    public static final Color SUCCESS = new Color(22, 163, 74);
    public static final Color WARNING = new Color(217, 119, 6);
    public static final Color DANGER = new Color(220, 38, 38);
    public static final Color BORDER = new Color(222, 224, 227);
    public static final Color TABLE_ALTERNATE = new Color(250, 251, 252);
    public static final Color TABLE_HOVER = new Color(245, 248, 252);
    public static final Color TABLE_SELECTED = new Color(220, 233, 252);
    public static final int GAP = 12;
    private static final String TABLE_HOVER_ROW = "app-table-hover-row";

    private AppTheme() {
    }

    public static void install() {
        Font body = new Font("PingFang SC", Font.PLAIN, 14);
        Font heading = new Font("PingFang SC", Font.BOLD, 18);
        UIManager.put("Label.font", body);
        UIManager.put("Button.font", body);
        UIManager.put("TextField.font", body);
        UIManager.put("PasswordField.font", body);
        UIManager.put("TextArea.font", body);
        UIManager.put("ComboBox.font", body);
        UIManager.put("Table.font", body);
        UIManager.put("TableHeader.font", body.deriveFont(Font.BOLD));
        UIManager.put("TabbedPane.font", body.deriveFont(Font.BOLD));
        UIManager.put("OptionPane.messageFont", body);
        UIManager.put("Panel.background", PAGE);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Button.margin", new Insets(7, 13, 7, 13));
        UIManager.put("TextField.margin", new Insets(6, 8, 6, 8));
        UIManager.put("PasswordField.margin", new Insets(6, 8, 6, 8));
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("Table.showHorizontalLines", false);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.selectionBackground", new Color(232, 243, 255));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TitledBorder.titleColor", TEXT);
        UIManager.put("TitledBorder.font", heading.deriveFont(15f));
    }

    public static JPanel surface(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(SURFACE);
        panel.setBorder(cardBorder());
        return panel;
    }

    public static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(12, 14, 12, 14));
    }

    public static JPanel pageHeader(String title, String subtitle, Component... actions) {
        JPanel header = new JPanel(new BorderLayout(GAP, 0));
        header.setOpaque(true);
        header.setBackground(SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER), BorderFactory.createEmptyBorder(12, 18, 12, 18)));
        JPanel labels = new JPanel(new GridLayout(1, 1));
        labels.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        labels.add(titleLabel);
        header.add(labels, BorderLayout.CENTER);
        if (actions != null && actions.length > 0) {
            JPanel actionPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
            actionPanel.setOpaque(false);
            for (Component action : actions) {
                actionPanel.add(action);
            }
            header.add(actionPanel, BorderLayout.EAST);
        }
        return header;
    }

    public static void primary(JButton button) {
        styleButton(button, PRIMARY, Color.WHITE, PRIMARY_DARK);
    }

    public static void secondary(JButton button) {
        styleButton(button, SURFACE, TEXT, new Color(245, 246, 247));
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)));
    }

    public static void danger(JButton button) {
        styleButton(button, DANGER, Color.WHITE, new Color(185, 28, 28));
    }

    private static void styleButton(JButton button, Color background, Color foreground, Color hover) {
        button.setFocusPainted(false);
        button.setForeground(foreground);
        button.setBackground(background);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(7, 13, 7, 13));
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.getModel().addChangeListener(event -> button.setBackground(button.getModel().isRollover() ? hover : background));
    }

    public static void styleTable(JTable table) {
        table.setRowHeight(38);
        table.setRowMargin(0);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setSelectionBackground(TABLE_SELECTED);
        table.setSelectionForeground(TEXT);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable currentTable, Object value, boolean selected,
                                                            boolean focused, int row, int column) {
                super.getTableCellRendererComponent(currentTable, value, selected, false, row, column);
                setOpaque(true);
                setBackground(tableCellBackground(currentTable, selected, row));
                setForeground(TEXT);
                setBorder(tableCellBorder(selected, column));
                setVerticalAlignment(SwingConstants.CENTER);
                return this;
            }
        });
        table.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent event) {
                int row = table.rowAtPoint(event.getPoint());
                Object previous = table.getClientProperty(TABLE_HOVER_ROW);
                int previousRow = previous instanceof Integer value ? value : -1;
                if (row != previousRow) {
                    table.putClientProperty(TABLE_HOVER_ROW, row);
                    table.repaint();
                }
            }
        });
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent event) {
                table.putClientProperty(TABLE_HOVER_ROW, -1);
                table.repaint();
            }
        });
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setBackground(new Color(245, 246, 247));
        header.setForeground(MUTED);
        header.setPreferredSize(new Dimension(0, 34));
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
        headerRenderer.setOpaque(true);
        headerRenderer.setBackground(new Color(245, 246, 247));
        headerRenderer.setForeground(MUTED);
        headerRenderer.setFont(header.getFont().deriveFont(Font.BOLD));
        headerRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        headerRenderer.setVerticalAlignment(SwingConstants.CENTER);
        headerRenderer.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 1, BORDER),
            BorderFactory.createEmptyBorder(0, 8, 0, 8)));
        header.setDefaultRenderer(headerRenderer);
    }

    public static Color tableCellBackground(JTable table, boolean selected, int row) {
        if (selected) {
            return TABLE_SELECTED;
        }
        Object hoverValue = table.getClientProperty(TABLE_HOVER_ROW);
        if (hoverValue instanceof Integer hoverRow && hoverRow == row) {
            return TABLE_HOVER;
        }
        return row % 2 == 0 ? SURFACE : TABLE_ALTERNATE;
    }

    public static Border tableCellBorder(boolean selected, int column) {
        if (selected && column == 0) {
            return BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, PRIMARY),
                BorderFactory.createEmptyBorder(0, 5, 0, 8));
        }
        return BorderFactory.createEmptyBorder(0, 8, 0, 8);
    }

    public static void styleComboBox(JComboBox<?> comboBox) {
        Dimension preferred = comboBox.getPreferredSize();
        comboBox.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override
            public void paintCurrentValueBackground(java.awt.Graphics graphics, java.awt.Rectangle bounds,
                                                    boolean focused) {
                graphics.setColor(SURFACE);
                graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }

            @Override
            public void paintCurrentValue(java.awt.Graphics graphics, java.awt.Rectangle bounds,
                                          boolean focused) {
                super.paintCurrentValue(graphics, bounds, false);
            }
        });
        comboBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                           boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                if (index < 0) {
                    setBackground(SURFACE);
                    setForeground(TEXT);
                } else if (selected) {
                    setBackground(new Color(232, 243, 255));
                    setForeground(PRIMARY_DARK);
                } else {
                    setBackground(SURFACE);
                    setForeground(TEXT);
                }
                return this;
            }
        });
        comboBox.setOpaque(true);
        comboBox.setBackground(SURFACE);
        comboBox.setForeground(TEXT);
        comboBox.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(2, 8, 2, 6)));
        comboBox.setPreferredSize(new Dimension(Math.max(90, preferred.width), 36));
        comboBox.setMinimumSize(new Dimension(80, 36));
    }

    public static void styleInput(JComponent component) {
        Dimension preferred = component.getPreferredSize();
        component.setPreferredSize(new Dimension(preferred.width, 36));
        component.setMinimumSize(new Dimension(Math.min(preferred.width, 80), 36));
    }

    public static void segment(JToggleButton button) {
        button.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(8, 18, 8, 18)));
        button.getModel().addChangeListener(event -> {
            boolean selected = button.isSelected();
            button.setBackground(selected ? new Color(232, 243, 255) : SURFACE);
            button.setForeground(selected ? PRIMARY_DARK : TEXT);
        });
        button.setBackground(SURFACE);
        button.setForeground(TEXT);
    }

    public static JScrollPane scroll(JComponent component) {
        JScrollPane scrollPane = new JScrollPane(component);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
        scrollPane.getViewport().setBackground(SURFACE);
        return scrollPane;
    }

    public static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        return label;
    }

    public static JLabel centeredHint(String text) {
        JLabel label = muted(text);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    public static void closeOnEscape(JDialog dialog) {
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(javax.swing.KeyStroke.getKeyStroke("ESCAPE"), "close-dialog");
        dialog.getRootPane().getActionMap().put("close-dialog", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                dialog.dispose();
            }
        });
    }

    public static void toast(Component parent, String message, boolean error) {
        javax.swing.JRootPane rootPane = javax.swing.SwingUtilities.getRootPane(parent);
        if (rootPane == null || message == null || message.isBlank()) {
            return;
        }
        javax.swing.JLayeredPane layeredPane = rootPane.getLayeredPane();
        for (Component component : layeredPane.getComponents()) {
            if ("app-toast".equals(component.getName())) {
                layeredPane.remove(component);
            }
        }
        JPanel toast = new JPanel(new BorderLayout(8, 0));
        toast.setName("app-toast");
        toast.setBackground(error ? new Color(254, 242, 242) : new Color(240, 253, 244));
        toast.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(error ? DANGER : SUCCESS),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JLabel label = new JLabel(message);
        label.setForeground(error ? new Color(153, 27, 27) : new Color(22, 101, 52));
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        toast.add(label, BorderLayout.CENTER);
        Dimension size = toast.getPreferredSize();
        toast.setSize(size);
        int x = Math.max(16, (layeredPane.getWidth() - size.width) / 2);
        int y = 24;
        toast.setLocation(x, y);
        layeredPane.add(toast, javax.swing.JLayeredPane.POPUP_LAYER);
        layeredPane.repaint();
        javax.swing.Timer timer = new javax.swing.Timer(3600, event -> {
            layeredPane.remove(toast);
            layeredPane.repaint();
        });
        timer.setRepeats(false);
        timer.start();
    }

    public static void submitOnEnter(javax.swing.JTextArea textArea, Runnable submitAction) {
        String submitKey = "app-submit-on-enter";
        textArea.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), submitKey);
        textArea.getActionMap().put(submitKey, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                if (submitAction != null) {
                    submitAction.run();
                }
            }
        });
        textArea.getInputMap().put(
            javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK),
            javax.swing.text.DefaultEditorKit.insertBreakAction);
    }
}
