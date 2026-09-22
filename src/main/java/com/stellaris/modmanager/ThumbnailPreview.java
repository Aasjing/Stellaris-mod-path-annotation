package com.stellaris.modmanager;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public final class ThumbnailPreview {

    private static final Color BACKGROUND_COLOR = new Color(43, 43, 43);
    private static final Color BORDER_COLOR = new Color(80, 80, 80);
    private static final int GAP = 10;
    private static final int PADDING = 12;
    private static final int DEFAULT_DELAY_MS = 100;

    private static JWindow window;
    private static PreviewPanel panel;
    private static Timer timer;
    private static List<BufferedImage> frames;
    private static int[] delays;
    private static int frameIndex;
    private static String currentKey;
    private static Component currentAnchor;

    private ThumbnailPreview() {
    }

    public static void show(Component anchor, String sourcePath, String folderName, int maxSize,
                            BufferedImage placeholder) {
        hide();
        if (sourcePath == null || anchor == null) {
            return;
        }

        String key = folderName + "_" + maxSize;
        currentKey = key;
        currentAnchor = anchor;
        frames = placeholder == null ? List.of() : List.of(placeholder);
        frameIndex = 0;

        ensureWindow(anchor);
        panel.setImage(placeholder);
        updateBounds();
        window.setVisible(true);

        ThumbnailCache.loadFramesAsync(sourcePath, folderName, maxSize, animation -> {
            if (!key.equals(currentKey) || animation == null || animation.frames().isEmpty()) {
                return;
            }
            frames = animation.frames();
            delays = animation.delaysMs();
            frameIndex = 0;
            panel.setImage(frames.get(0));
            updateBounds();
            startTimer();
        });
    }

    public static void hide() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        frames = null;
        delays = null;
        frameIndex = 0;
        currentKey = null;
        currentAnchor = null;
        if (window != null) {
            window.setVisible(false);
        }
    }

    private static void ensureWindow(Component anchor) {
        if (window != null && window.isDisplayable()) {
            return;
        }
        window = new JWindow(SwingUtilities.getWindowAncestor(anchor));
        panel = new PreviewPanel();
        window.setContentPane(panel);
    }

    private static void updateBounds() {
        BufferedImage image = panel.getImage();
        int width = image == null ? 160 : image.getWidth();
        int height = image == null ? 90 : image.getHeight();
        panel.setPreferredSize(new Dimension(width + PADDING, height + PADDING));
        window.pack();
        position();
    }

    private static void position() {
        if (currentAnchor == null) {
            return;
        }
        try {
            Point location = currentAnchor.getLocationOnScreen();
            int x = location.x - window.getWidth() - GAP;
            if (x < 0) {
                x = location.x + currentAnchor.getWidth() + GAP;
            }
            window.setLocation(x, location.y);
        } catch (IllegalComponentStateException ignored) {
        }
    }

    private static void startTimer() {
        if (frames == null || frames.size() < 2) {
            return;
        }
        if (timer == null) {
            timer = new Timer(DEFAULT_DELAY_MS, e -> showNextFrame());
            timer.setCoalesce(true);
        }
        timer.setInitialDelay(frameDelay());
        timer.setDelay(frameDelay());
        timer.restart();
    }

    private static int frameDelay() {
        if (delays == null || delays.length == 0) {
            return DEFAULT_DELAY_MS;
        }
        return delays[Math.min(frameIndex, delays.length - 1)];
    }

    private static void showNextFrame() {
        if (frames == null || frames.size() < 2) {
            return;
        }
        if (window == null || !window.isVisible()) {
            hide();
            return;
        }
        frameIndex = (frameIndex + 1) % frames.size();
        panel.setImage(frames.get(frameIndex));
        if (timer != null) {
            timer.setDelay(frameDelay());
        }
    }

    private static class PreviewPanel extends JPanel {

        private BufferedImage image;

        PreviewPanel() {
            setBackground(BACKGROUND_COLOR);
            setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        }

        BufferedImage getImage() {
            return image;
        }

        void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image == null) {
                g.setColor(new Color(150, 150, 150));
                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                String text = "载入中...";
                FontMetrics fm = g.getFontMetrics();
                g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                return;
            }
            int width = Math.min(image.getWidth(), getWidth() - PADDING);
            int height = image.getHeight() * width / image.getWidth();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(image, (getWidth() - width) / 2, (getHeight() - height) / 2, width, height, this);
            g2.dispose();
        }
    }
}
