package com.ticket.ui.component;

import com.ticket.ui.theme.AppTheme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Lightweight interactive donut chart implemented with Java2D only. */
public final class DonutChartPanel extends JPanel {
    public record Segment(String label, long value, Color color, int key) {
    }

    private static final int LEGEND_HEIGHT = 58;
    private static final float RING_WIDTH = 34f;
    private List<Segment> segments = List.of();
    private float[] hoverAnimation = new float[0];
    private int hoveredIndex = -1;
    private String centerTitle = "我的工单";
    private IntConsumer selectionListener = ignored -> { };
    private final Timer animationTimer = new Timer(16, event -> animateHover());

    public DonutChartPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(420, 300));
        setToolTipText("");
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                updateHoveredIndex(segmentAt(event.getX(), event.getY()));
            }

            @Override
            public void mouseExited(MouseEvent event) {
                updateHoveredIndex(-1);
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (hoveredIndex >= 0 && hoveredIndex < segments.size()
                        && segments.get(hoveredIndex).value() > 0) {
                    selectionListener.accept(segments.get(hoveredIndex).key());
                }
            }
        };
        addMouseMotionListener(mouseHandler);
        addMouseListener(mouseHandler);
    }

    public void setSegments(List<Segment> segments) {
        this.segments = segments == null ? List.of() : List.copyOf(segments);
        this.hoverAnimation = new float[this.segments.size()];
        this.hoveredIndex = -1;
        repaint();
    }

    public void setCenterTitle(String centerTitle) {
        this.centerTitle = centerTitle == null || centerTitle.isBlank() ? "我的工单" : centerTitle;
        repaint();
    }

    public void setSelectionListener(IntConsumer selectionListener) {
        this.selectionListener = selectionListener == null ? ignored -> { } : selectionListener;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int index = segmentAt(event.getX(), event.getY());
        if (index < 0 || index >= segments.size()) {
            return null;
        }
        Segment segment = segments.get(index);
        long total = total();
        double percentage = total == 0 ? 0 : segment.value() * 100.0 / total;
        return segment.label() + "：" + segment.value() + " 条（" + String.format("%.1f", percentage) + "%）";
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int availableHeight = Math.max(120, getHeight() - LEGEND_HEIGHT);
            int chartSize = Math.max(100, Math.min(getWidth() - 44, availableHeight - 14));
            double centerX = getWidth() / 2.0;
            double centerY = Math.max(chartSize / 2.0 + 8, (availableHeight - 4) / 2.0);
            double radius = chartSize / 2.0 - RING_WIDTH / 2.0 - 8;
            long total = total();

            if (total == 0) {
                g2.setColor(new Color(231, 234, 239));
                g2.setStroke(new BasicStroke(RING_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawOval((int) Math.round(centerX - radius), (int) Math.round(centerY - radius),
                    (int) Math.round(radius * 2), (int) Math.round(radius * 2));
            } else {
                double startAngle = 90.0;
                for (int index = 0; index < segments.size(); index++) {
                    Segment segment = segments.get(index);
                    if (segment.value() <= 0) {
                        continue;
                    }
                    double sweep = segment.value() * 360.0 / total;
                    float progress = index < hoverAnimation.length ? hoverAnimation[index] : 0f;
                    double animatedRadius = radius + progress * 4.0;
                    float animatedWidth = RING_WIDTH + progress * 7f;
                    g2.setColor(segment.color());
                    g2.setStroke(new BasicStroke(animatedWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                    double gap = Math.min(1.6, sweep / 5.0);
                    g2.draw(new Arc2D.Double(centerX - animatedRadius, centerY - animatedRadius,
                        animatedRadius * 2, animatedRadius * 2, startAngle - gap / 2,
                        -(sweep - gap), Arc2D.OPEN));
                    startAngle -= sweep;
                }
            }

            drawCenter(g2, centerX, centerY, total);
            drawLegend(g2, total);
        } finally {
            g2.dispose();
        }
    }

    private void drawCenter(Graphics2D g2, double centerX, double centerY, long total) {
        String title = centerTitle;
        String value = String.valueOf(total);
        String suffix = "全部状态";
        if (hoveredIndex >= 0 && hoveredIndex < segments.size()) {
            Segment segment = segments.get(hoveredIndex);
            title = segment.label();
            value = String.valueOf(segment.value());
            suffix = total == 0 ? "0%" : String.format("%.1f%%", segment.value() * 100.0 / total);
        }
        drawCentered(g2, title, centerX, centerY - 18, getFont().deriveFont(Font.PLAIN, 13f), AppTheme.MUTED);
        drawCentered(g2, value, centerX, centerY + 9, getFont().deriveFont(Font.BOLD, 28f), AppTheme.TEXT);
        drawCentered(g2, suffix, centerX, centerY + 31, getFont().deriveFont(Font.PLAIN, 12f), AppTheme.MUTED);
    }

    private void drawLegend(Graphics2D g2, long total) {
        if (segments.isEmpty()) {
            return;
        }
        int itemWidth = Math.max(76, getWidth() / segments.size());
        int y = getHeight() - 30;
        g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        for (int index = 0; index < segments.size(); index++) {
            Segment segment = segments.get(index);
            int center = itemWidth * index + itemWidth / 2;
            String label = segment.label() + " " + segment.value();
            int textWidth = g2.getFontMetrics().stringWidth(label);
            int x = center - (textWidth + 14) / 2;
            g2.setColor(segment.color());
            g2.fillOval(x, y - 8, 8, 8);
            g2.setColor(AppTheme.MUTED);
            g2.drawString(label, x + 14, y);
        }
    }

    private void drawCentered(Graphics2D g2, String text, double centerX, double baseline,
                              Font font, Color color) {
        g2.setFont(font);
        g2.setColor(color);
        int width = g2.getFontMetrics().stringWidth(text);
        g2.drawString(text, (float) (centerX - width / 2.0), (float) baseline);
    }

    private int segmentAt(int x, int y) {
        int availableHeight = Math.max(120, getHeight() - LEGEND_HEIGHT);
        int chartSize = Math.max(100, Math.min(getWidth() - 44, availableHeight - 14));
        double centerX = getWidth() / 2.0;
        double centerY = Math.max(chartSize / 2.0 + 8, (availableHeight - 4) / 2.0);
        double outerRadius = chartSize / 2.0 + 2;
        double innerRadius = outerRadius - RING_WIDTH - 14;
        double dx = x - centerX;
        double dy = y - centerY;
        double distance = Math.hypot(dx, dy);
        if (distance < innerRadius || distance > outerRadius) {
            return -1;
        }
        long total = total();
        if (total == 0) {
            return -1;
        }
        double clockwiseFromTop = Math.toDegrees(Math.atan2(dx, -dy));
        if (clockwiseFromTop < 0) {
            clockwiseFromTop += 360;
        }
        double cursor = 0;
        for (int index = 0; index < segments.size(); index++) {
            Segment segment = segments.get(index);
            double sweep = segment.value() * 360.0 / total;
            if (segment.value() > 0 && clockwiseFromTop >= cursor && clockwiseFromTop < cursor + sweep) {
                return index;
            }
            cursor += sweep;
        }
        return -1;
    }

    private long total() {
        return segments.stream().mapToLong(Segment::value).sum();
    }

    private void updateHoveredIndex(int index) {
        if (hoveredIndex == index) {
            return;
        }
        hoveredIndex = index;
        setCursor(index >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        if (!animationTimer.isRunning()) {
            animationTimer.start();
        }
        repaint();
    }

    private void animateHover() {
        boolean moving = false;
        for (int index = 0; index < hoverAnimation.length; index++) {
            float target = index == hoveredIndex ? 1f : 0f;
            float current = hoverAnimation[index];
            float next = current + (target - current) * 0.24f;
            if (Math.abs(next - target) < 0.02f) {
                next = target;
            } else {
                moving = true;
            }
            hoverAnimation[index] = next;
        }
        repaint();
        if (!moving) {
            animationTimer.stop();
        }
    }
}
