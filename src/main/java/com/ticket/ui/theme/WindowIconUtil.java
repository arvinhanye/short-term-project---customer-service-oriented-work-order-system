package com.ticket.ui.theme;

import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Replaces the Java runtime icon for the application and every top-level window. */
public final class WindowIconUtil {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final List<Image> ICONS = List.of(
        createTicketIcon(16), createTicketIcon(32), createTicketIcon(64),
        createTicketIcon(128), createTicketIcon(256));

    private WindowIconUtil() {
    }

    public static void installForApplication() {
        if (GraphicsEnvironment.isHeadless() || !INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
                if (event instanceof WindowEvent windowEvent
                    && windowEvent.getID() == WindowEvent.WINDOW_OPENED) {
                    apply(windowEvent.getWindow());
                }
            }, AWTEvent.WINDOW_EVENT_MASK);
        } catch (RuntimeException ignored) {
            // Individual windows still receive the icon through apply(Window).
        }
        try {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.setIconImage(ICONS.get(ICONS.size() - 1));
            }
        } catch (RuntimeException ignored) {
            // Some desktop environments do not allow changing the taskbar icon.
        }
    }

    public static void apply(Window window) {
        if (window == null || GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            window.setIconImages(ICONS);
        } catch (RuntimeException ignored) {
            // Icon support is platform dependent and must never block a dialog.
        }
    }

    private static BufferedImage createTicketIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(37, 99, 235));
            graphics.fillRoundRect(0, 0, size, size, Math.max(4, size / 4), Math.max(4, size / 4));

            float scale = size / 64f;
            graphics.setColor(Color.WHITE);
            graphics.setStroke(new BasicStroke(Math.max(1.4f, 4f * scale),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.drawRoundRect(Math.round(15 * scale), Math.round(14 * scale),
                Math.round(34 * scale), Math.round(36 * scale), Math.round(6 * scale), Math.round(6 * scale));
            graphics.drawLine(Math.round(23 * scale), Math.round(25 * scale),
                Math.round(41 * scale), Math.round(25 * scale));
            graphics.drawLine(Math.round(23 * scale), Math.round(33 * scale),
                Math.round(38 * scale), Math.round(33 * scale));
            graphics.drawLine(Math.round(23 * scale), Math.round(41 * scale),
                Math.round(34 * scale), Math.round(41 * scale));
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
