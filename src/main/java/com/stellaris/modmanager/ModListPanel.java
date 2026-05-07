package com.stellaris.modmanager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class ModListPanel extends JPanel {

    private final Runnable onModClick;
    private final ModInfo currentMod;
    private BufferedImage thumbnailImage;
    private JWindow hoverWindow;

    public ModListPanel(ModInfo modInfo, Runnable onModClick) {
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
                new EmptyBorder(5, 10, 5, 10)
        ));
        setBackground(new Color(43, 43, 43));

        JLabel modLabel = createModLabel();
        add(modLabel, BorderLayout.CENTER);

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
                showThumbnailPopup();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setBackground(new Color(43, 43, 43));
                repaint();
                hideThumbnailPopup();
            }
        });
    }

    private void showThumbnailPopup() {
        if (thumbnailImage == null) {
            return;
        }
        hideThumbnailPopup();

        hoverWindow = new JWindow(SwingUtilities.getWindowAncestor(this));
        hoverWindow.setBackground(new Color(0, 0, 0, 0));

        int maxWidth = 320;
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

    private void hideThumbnailPopup() {
        if (hoverWindow != null) {
            hoverWindow.dispose();
            hoverWindow = null;
        }
    }

    private JLabel createModLabel() {
        StringBuilder html = new StringBuilder("<html><body style='font-family: Consolas, monospace;'>");

        html.append("<span style='color: #FFFFFF;'>")
                .append(currentMod.folderName())
                .append("</span>: ");

        html.append("<span style='color: #CD853F;'>")
                .append(escapeHtml(currentMod.modName()))
                .append("</span>, ");

        html.append("<span style='color: #D3D3D3;'>")
                .append(currentMod.version())
                .append("</span>, ");

        html.append("<span style='color: #90EE90;'>")
                .append(currentMod.supportedVersion())
                .append("</span>");

        html.append("</body></html>");

        JLabel label = new JLabel(html.toString());
        label.setForeground(Color.WHITE);
        label.setCursor(new Cursor(Cursor.HAND_CURSOR));

        return label;
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
