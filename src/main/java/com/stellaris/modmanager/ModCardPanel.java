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
import java.util.List;
import java.util.function.BiConsumer;

public class ModCardPanel extends JPanel {

    private static final int CARD_WIDTH = 156;
    private static final int CARD_BORDER = 1;
    private static final int CARD_PADDING = 8;
    private static final int IMAGE_SIZE = CARD_WIDTH - (CARD_BORDER + CARD_PADDING) * 2;
    private static final int DEFAULT_FRAME_DELAY_MS = 100;

    private static final Color BG_COLOR = new Color(43, 43, 43);
    private static final Color BG_HOVER_COLOR = new Color(50, 50, 50);
    private static final Color BORDER_COLOR = new Color(60, 63, 65);
    private static final Color DROP_HIGHLIGHT_COLOR = new Color(100, 150, 200);

    private static Border normalBorder;
    private static Border dropHighlightBorder;

    static {
        normalBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, CARD_BORDER),
                new EmptyBorder(CARD_PADDING, CARD_PADDING, CARD_PADDING, CARD_PADDING)
        );
        dropHighlightBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DROP_HIGHLIGHT_COLOR, 2),
                new EmptyBorder(CARD_PADDING - 1, CARD_PADDING - 1, CARD_PADDING - 1, CARD_PADDING - 1)
        );
    }

    private Runnable onModClick;
    private final ModInfo currentMod;
    private BiConsumer<Integer, ModCardPanel> onDragDrop;
    private BufferedImage thumbnailImage;
    private JPanel thumbnailPanelRef;
    private Point dragStart;
    private String thumbnailSource;
    private List<BufferedImage> animationFrames;
    private int[] animationDelays;
    private BufferedImage animationFrame;
    private int animationIndex;
    private Timer animationTimer;
    private boolean hovered;

    public ModCardPanel(ModInfo modInfo, Runnable onModClick,
                        BiConsumer<Integer, ModCardPanel> onDragDrop) {
        this.currentMod = modInfo;
        this.onModClick = onModClick;
        this.onDragDrop = onDragDrop;
        this.thumbnailSource = modInfo.thumbnailPath();
        initializePanel();
        loadThumbnailAsync();
    }

    public void updateCallbacks(Runnable onModClick,
                                BiConsumer<Integer, ModCardPanel> onDragDrop) {
        this.onModClick = onModClick;
        this.onDragDrop = onDragDrop;
    }

    private void loadThumbnailAsync() {
        if (thumbnailSource != null) {
            loadThumbnail(thumbnailSource);
            return;
        }
        WorkshopPreview.resolveAsync(currentMod.folderName(), path -> {
            if (path == null) {
                return;
            }
            thumbnailSource = path;
            loadThumbnail(path);
        });
    }

    private void loadThumbnail(String sourcePath) {
        ThumbnailCache.loadAsync(
                sourcePath,
                currentMod.folderName(),
                IMAGE_SIZE,
                image -> {
                    thumbnailImage = image;
                    repaintThumbnail();
                }
        );
    }

    private void initializePanel() {
        setLayout(new BorderLayout());
        setBorder(normalBorder);
        setBackground(BG_COLOR);
        setTransferHandler(new CardTransferHandler());

        JPanel thumbnailPanel = createThumbnailPanel();
        add(thumbnailPanel, BorderLayout.CENTER);

        JPanel infoPanel = createInfoPanel();
        add(infoPanel, BorderLayout.SOUTH);

        Dimension size = new Dimension(CARD_WIDTH,
                IMAGE_SIZE + infoPanel.getPreferredSize().height + (CARD_BORDER + CARD_PADDING) * 2);
        setPreferredSize(size);
        setMaximumSize(size);
        setMinimumSize(size);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (onModClick != null) {
                    onModClick.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                setBackground(BG_HOVER_COLOR);
                repaint();
                startAnimation();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                stopAnimation();
                setBackground(BG_COLOR);
                repaint();
            }
        });
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        hovered = false;
        stopAnimation();
    }

    private void startAnimation() {
        if (animationFrames != null) {
            startAnimationTimer();
            return;
        }
        if (thumbnailSource == null) {
            return;
        }
        ThumbnailCache.loadFramesAsync(thumbnailSource, currentMod.folderName(), IMAGE_SIZE, animation -> {
            if (animation == null || animation.frames().size() < 2) {
                return;
            }
            animationFrames = animation.frames();
            animationDelays = animation.delaysMs();
            if (hovered) {
                startAnimationTimer();
            }
        });
    }

    private void startAnimationTimer() {
        if (animationFrames == null || animationFrames.size() < 2 || animationTimer != null) {
            return;
        }
        animationTimer = new Timer(frameDelay(), e -> showNextFrame());
        animationTimer.setCoalesce(true);
        animationTimer.start();
    }

    private void showNextFrame() {
        animationIndex = (animationIndex + 1) % animationFrames.size();
        animationFrame = animationFrames.get(animationIndex);
        if (animationTimer != null) {
            animationTimer.setDelay(frameDelay());
        }
        repaintThumbnail();
    }

    private int frameDelay() {
        if (animationDelays == null || animationDelays.length == 0) {
            return DEFAULT_FRAME_DELAY_MS;
        }
        return animationDelays[Math.min(animationIndex, animationDelays.length - 1)];
    }

    private void stopAnimation() {
        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }
        animationFrame = null;
        animationIndex = 0;
        repaintThumbnail();
    }

    private void repaintThumbnail() {
        if (thumbnailPanelRef != null) {
            thumbnailPanelRef.repaint();
        }
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

    private JPanel createThumbnailPanel() {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                BufferedImage image = animationFrame != null ? animationFrame : thumbnailImage;
                if (image == null) {
                    paintPlaceholder(g);
                    return;
                }

                int boxWidth = getWidth();
                int boxHeight = getHeight();
                double scale = Math.min((double) boxWidth / image.getWidth(),
                        (double) boxHeight / image.getHeight());
                int drawWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
                int drawHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(image, (boxWidth - drawWidth) / 2, (boxHeight - drawHeight) / 2,
                        drawWidth, drawHeight, this);
                g2.dispose();
            }

            private void paintPlaceholder(Graphics g) {
                int size = Math.min(getWidth(), getHeight()) - 24;
                if (size <= 0) {
                    return;
                }
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;

                float hue = (currentMod.folderName().hashCode() & 0x7fffffff) % 360 / 360f;
                g.setColor(Color.getHSBColor(hue, 0.35f, 0.55f));
                g.fillRoundRect(x, y, size, size, 16, 16);

                String name = currentMod.modName();
                String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
                g.setFont(new Font("SansSerif", Font.BOLD, size / 2));
                g.setColor(new Color(235, 235, 235));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(initial, x + (size - fm.stringWidth(initial)) / 2,
                        y + (size + fm.getAscent() - fm.getDescent()) / 2);
            }
        };
        panel.setBackground(new Color(37, 37, 37));
        panel.setPreferredSize(new Dimension(IMAGE_SIZE, IMAGE_SIZE));
        panel.setCursor(new Cursor(Cursor.MOVE_CURSOR));

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                SwingUtilities.convertPointToScreen(dragStart, panel);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragStart != null && onModClick != null) {
                    onModClick.run();
                }
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

        thumbnailPanelRef = panel;

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
