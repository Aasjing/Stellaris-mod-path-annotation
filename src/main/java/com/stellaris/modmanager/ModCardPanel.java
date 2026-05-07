package com.stellaris.modmanager;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.BiConsumer;
import javax.imageio.ImageIO;

public class ModCardPanel extends JPanel {

    private static final int CARD_WIDTH = 156;
    private static final int CARD_HEIGHT = 175;
    private static final int THUMB_SIZE = 128;

    private static final Color BG_COLOR = new Color(43, 43, 43);
    private static final Color BG_HOVER_COLOR = new Color(50, 50, 50);
    private static final Color BORDER_COLOR = new Color(60, 63, 65);
    private static final Color DROP_HIGHLIGHT_COLOR = new Color(100, 150, 200);

    private static Border normalBorder;
    private static Border dropHighlightBorder;

    static {
        normalBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(8, 8, 8, 8)
        );
        dropHighlightBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DROP_HIGHLIGHT_COLOR, 2),
                new EmptyBorder(7, 7, 7, 7)
        );
    }

    private Runnable onModClick;
    private final ModInfo currentMod;
    private BiConsumer<Integer, ModCardPanel> onDragDrop;
    private BufferedImage thumbnailImage;
    private JWindow hoverWindow;
    private Point dragStart;

    public ModCardPanel(ModInfo modInfo, Runnable onModClick,
                        BiConsumer<Integer, ModCardPanel> onDragDrop) {
        this.currentMod = modInfo;
        this.onModClick = onModClick;
        this.onDragDrop = onDragDrop;
        loadThumbnail();
        initializePanel();
    }

    public void updateCallbacks(Runnable onModClick,
                                BiConsumer<Integer, ModCardPanel> onDragDrop) {
        this.onModClick = onModClick;
        this.onDragDrop = onDragDrop;
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
        setBorder(normalBorder);
        setBackground(BG_COLOR);
        setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        setMaximumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        setMinimumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        setTransferHandler(new CardTransferHandler());

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
                setBackground(BG_HOVER_COLOR);
                repaint();
                showLargePreview();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setBackground(BG_COLOR);
                repaint();
                hideLargePreview();
            }
        });
    }

    public void setDropHighlight(boolean highlight) {
        setBorder(highlight ? dropHighlightBorder : normalBorder);
        revalidate();
        repaint();
    }

    public static void clearDropHighlights(Container parent) {
        for (int i = 0; i < parent.getComponentCount(); i++) {
            if (parent.getComponent(i) instanceof ModCardPanel panel) {
                panel.setDropHighlight(false);
            }
        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        hideLargePreview();
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
        panel.setCursor(new Cursor(Cursor.MOVE_CURSOR));

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                SwingUtilities.convertPointToScreen(dragStart, panel);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
            }
        });

        panel.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart == null) {
                    return;
                }
                Point current = e.getPoint();
                SwingUtilities.convertPointToScreen(current, panel);
                int dx = Math.abs(current.x - dragStart.x);
                int dy = Math.abs(current.y - dragStart.y);
                if (dx > 5 || dy > 5) {
                    dragStart = null;
                    TransferHandler th = getTransferHandler();
                    if (th != null) {
                        th.exportAsDrag(ModCardPanel.this, e, TransferHandler.MOVE);
                    }
                }
            }
        });

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

    private class CardTransferHandler extends TransferHandler {

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        public Image getDragImage() {
            int w = getWidth();
            int h = getHeight();
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
            paint(g2);
            g2.dispose();
            return img;
        }

        @Override
        public Point getDragImageOffset() {
            return new Point(getWidth() / 2, getHeight() / 2);
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            Container parent = getParent();
            if (parent != null) {
                for (int i = 0; i < parent.getComponentCount(); i++) {
                    if (parent.getComponent(i) == ModCardPanel.this) {
                        return new StringSelection(String.valueOf(i));
                    }
                }
            }
            return new StringSelection("-1");
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return false;
            }
            Container parent = getParent();
            if (parent != null) {
                clearDropHighlights(parent);
            }
            setDropHighlight(true);
            return true;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                int sourceIdx = Integer.parseInt(data);
                if (sourceIdx < 0) {
                    return false;
                }
                if (onDragDrop != null) {
                    onDragDrop.accept(sourceIdx, ModCardPanel.this);
                }
                return true;
            } catch (Exception ex) {
                return false;
            }
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            Container parent = source.getParent();
            if (parent != null) {
                clearDropHighlights(parent);
            }
        }
    }
}
