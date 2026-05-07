package com.stellaris.modmanager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class ModCardPanel extends JPanel {

    private static final int CARD_WIDTH = 180;
    private static final int CARD_HEIGHT = 200;
    private static final int THUMB_SIZE = 150;

    private final Runnable onModClick;
    private final ModInfo currentMod;
    private BufferedImage thumbnailImage;
    private JWindow hoverWindow;

    public ModCardPanel(ModInfo modInfo, Runnable onModClick) {
        this.currentMod = modInfo;
        this.onModClick = onModClick;
        loadThumbnail();
        initializePanel();
    }

    private void loadThumbnail() {
        if (currentMod.thumbnailPath() != null) {
            try {
                thumbnailImage = ImageIO.read(new File(currentMod.thumbnailPath()));
            } catch (Exception ignored) {
            }
        }
    }

    private void initializePanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 63, 65), 1),
                new EmptyBorder(8, 8, 8, 8)
        ));
        setBackground(new Color(43, 43, 43));
        setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        setMaximumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        setMinimumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));

        JPanel thumbnailPanel = createThumbnailPanel();
        add(thumbnailPanel, BorderLayout.CENTER);

        JPanel infoPanel = createInfoPanel();
        add(infoPanel, BorderLayout.SOUTH);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (onModClick != null) {
                    onModClick.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                setBackground(new Color(50, 50, 50));
                repaint();
                showLargePreview();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setBackground(new Color(43, 43, 43));
                repaint();
                hideLargePreview();
            }
        });
    }

    private JPanel createThumbnailPanel() {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (thumbnailImage != null) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                    int imgW = thumbnailImage.getWidth();
                    int imgH = thumbnailImage.getHeight();
                    double scale = Math.min((double) THUMB_SIZE / imgW, (double) THUMB_SIZE / imgH);
                    int drawW = (int) (imgW * scale);
                    int drawH = (int) (imgH * scale);
                    int x = (getWidth() - drawW) / 2;
                    int y = (getHeight() - drawH) / 2;

                    g2.drawImage(thumbnailImage, x, y, drawW, drawH, this);
                    g2.dispose();
                } else {
                    g.setColor(new Color(80, 80, 80));
                    int iconSize = 48;
                    int x = (getWidth() - iconSize) / 2;
                    int y = (getHeight() - iconSize) / 2;
                    g.fillRect(x, y, iconSize, iconSize);
                    g.setColor(new Color(120, 120, 120));
                    g.setFont(new Font("SansSerif", Font.PLAIN, 11));
                    String text = "N/A";
                    FontMetrics fm = g.getFontMetrics();
                    int tx = (getWidth() - fm.stringWidth(text)) / 2;
                    int ty = y + iconSize / 2 + fm.getAscent() / 2;
                    g.drawString(text, tx, ty);
                }
            }
        };
        panel.setBackground(new Color(37, 37, 37));
        panel.setPreferredSize(new Dimension(THUMB_SIZE, THUMB_SIZE));
        return panel;
    }

    private JPanel createInfoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(43, 43, 43));
        panel.setBorder(new EmptyBorder(4, 2, 0, 2));

        JLabel nameLabel = new JLabel(truncateText(currentMod.modName(), 20));
        nameLabel.setForeground(new Color(205, 133, 63));
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        nameLabel.setAlignmentX(CENTER_ALIGNMENT);

        JLabel versionLabel = new JLabel("v" + currentMod.version());
        versionLabel.setForeground(new Color(187, 187, 187));
        versionLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        versionLabel.setAlignmentX(CENTER_ALIGNMENT);

        panel.add(nameLabel);
        panel.add(Box.createVerticalStrut(2));
        panel.add(versionLabel);
        return panel;
    }

    private void showLargePreview() {
        if (thumbnailImage == null) {
            return;
        }
        hideLargePreview();

        hoverWindow = new JWindow(SwingUtilities.getWindowAncestor(this));
        hoverWindow.setBackground(new Color(0, 0, 0, 0));

        int maxWidth = 400;
        int imgW = thumbnailImage.getWidth();
        int imgH = thumbnailImage.getHeight();
        if (imgW > maxWidth) {
            double ratio = (double) maxWidth / imgW;
            imgW = maxWidth;
            imgH = (int) (imgH * ratio);
        }

        int finalW = imgW;
        int finalH = imgH;
        JPanel content = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                int x = (getWidth() - finalW) / 2;
                int y = (getHeight() - finalH) / 2;
                g2.drawImage(thumbnailImage, x, y, finalW, finalH, this);
                g2.dispose();
            }
        };
        content.setPreferredSize(new Dimension(imgW + 12, imgH + 12));
        content.setBackground(new Color(43, 43, 43));
        content.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80), 1));

        hoverWindow.setContentPane(content);
        hoverWindow.pack();

        Point loc = getLocationOnScreen();
        hoverWindow.setLocation(loc.x - hoverWindow.getWidth() - 10, loc.y);
        hoverWindow.setVisible(true);
    }

    private void hideLargePreview() {
        if (hoverWindow != null) {
            hoverWindow.dispose();
            hoverWindow = null;
        }
    }

    private String truncateText(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen - 3) + "...";
    }
}
