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
import java.util.function.BiConsumer;

public class ModListPanel extends JPanel {

    private static final Color BG_COLOR = new Color(43, 43, 43);
    private static final Color BG_HOVER_COLOR = new Color(50, 50, 50);
    private static final Color BORDER_COLOR = new Color(60, 63, 65);
    private static final Color DROP_HIGHLIGHT_COLOR = new Color(100, 150, 200);

    private static Border normalBorder;
    private static Border dropHighlightBorder;

    static {
        normalBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(5, 10, 5, 10)
        );
        dropHighlightBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(3, 0, 0, 0, DROP_HIGHLIGHT_COLOR),
                normalBorder
        );
    }

    private Runnable onModClick;
    private Runnable onMoveUp;
    private Runnable onMoveDown;
    private BiConsumer<Integer, ModListPanel> onDragDrop;
    private final ModInfo currentMod;
    private BufferedImage thumbnailImage;
    private JWindow hoverWindow;
    private Point dragStart;
    private javax.swing.Timer previewTimer;

    public ModListPanel(ModInfo modInfo, Runnable onModClick,
                        Runnable onMoveUp, Runnable onMoveDown,
                        BiConsumer<Integer, ModListPanel> onDragDrop) {
        this.currentMod = modInfo;
        this.onModClick = onModClick;
        this.onMoveUp = onMoveUp;
        this.onMoveDown = onMoveDown;
        this.onDragDrop = onDragDrop;
        initializePanel();
        loadThumbnailAsync();
    }

    public void updateCallbacks(Runnable onModClick,
                                Runnable onMoveUp, Runnable onMoveDown,
                                BiConsumer<Integer, ModListPanel> onDragDrop) {
        this.onModClick = onModClick;
        this.onMoveUp = onMoveUp;
        this.onMoveDown = onMoveDown;
        this.onDragDrop = onDragDrop;
    }

    private void loadThumbnailAsync() {
        if (currentMod.thumbnailPath() != null) {
            ThumbnailCache.loadAsync(
                    currentMod.thumbnailPath(),
                    currentMod.folderName(),
                    320,
                    image -> {
                        thumbnailImage = image;
                    }
            );
        }
    }

    private void initializePanel() {
        setLayout(new BorderLayout());
        setBorder(normalBorder);
        setBackground(BG_COLOR);
        setTransferHandler(new ModPanelTransferHandler());

        add(createDragHandle(), BorderLayout.WEST);
        add(createModLabel(), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.EAST);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getSource() instanceof ModListPanel && !isClickOnButton(e)) {
                    if (onModClick != null) {
                        onModClick.run();
                    }
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                setBackground(BG_HOVER_COLOR);
                repaint();
                showThumbnailPopup();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setBackground(BG_COLOR);
                repaint();
                hideThumbnailPopup();
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
            if (parent.getComponent(i) instanceof ModListPanel panel) {
                panel.setDropHighlight(false);
            }
        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (previewTimer != null) {
            previewTimer.stop();
            previewTimer = null;
        }
        if (hoverWindow != null) {
            hoverWindow.dispose();
            hoverWindow = null;
        }
    }

    private JLabel createDragHandle() {
        JLabel handle = new JLabel("\u2630");
        handle.setForeground(new Color(100, 100, 100));
        handle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        handle.setCursor(new Cursor(Cursor.MOVE_CURSOR));
        handle.setBorder(new EmptyBorder(0, 4, 0, 8));
        handle.setToolTipText("拖动以排序");

        handle.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                SwingUtilities.convertPointToScreen(dragStart, handle);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
            }
        });

        handle.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart == null) {
                    return;
                }
                Point current = e.getPoint();
                SwingUtilities.convertPointToScreen(current, handle);
                int dx = Math.abs(current.x - dragStart.x);
                int dy = Math.abs(current.y - dragStart.y);
                if (dx > 5 || dy > 5) {
                    dragStart = null;
                    TransferHandler th = getTransferHandler();
                    if (th != null) {
                        th.exportAsDrag(ModListPanel.this, e, TransferHandler.MOVE);
                    }
                }
            }
        });

        return handle;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_COLOR);
        panel.setBorder(new EmptyBorder(0, 4, 0, 2));

        JButton upBtn = createArrowButton("\u25B2", "上移", onMoveUp);
        JButton downBtn = createArrowButton("\u25BC", "下移", onMoveDown);

        panel.add(upBtn);
        panel.add(Box.createVerticalStrut(1));
        panel.add(downBtn);

        return panel;
    }

    private JButton createArrowButton(String text, String tooltip, Runnable action) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 9));
        btn.setForeground(new Color(160, 160, 160));
        btn.setBackground(new Color(55, 55, 55));
        btn.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(22, 16));
        btn.setMaximumSize(new Dimension(22, 16));
        btn.setMinimumSize(new Dimension(22, 16));
        btn.setToolTipText(tooltip);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> {
            if (action != null) {
                action.run();
            }
        });
        return btn;
    }

    private void showThumbnailPopup() {
        if (thumbnailImage == null) {
            return;
        }
        if (previewTimer != null) {
            previewTimer.stop();
        }
        previewTimer = new javax.swing.Timer(150, e -> {
            if (hoverWindow != null && hoverWindow.isVisible()) {
                return;
            }
            doShowThumbnailPopup();
        });
        previewTimer.setRepeats(false);
        previewTimer.start();
    }

    private void doShowThumbnailPopup() {
        if (thumbnailImage == null) {
            return;
        }

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

        if (hoverWindow == null || !hoverWindow.isDisplayable()) {
            hoverWindow = new JWindow(SwingUtilities.getWindowAncestor(this));
            hoverWindow.setBackground(new Color(0, 0, 0, 0));
        }
        hoverWindow.setContentPane(content);
        hoverWindow.pack();

        Point loc = getLocationOnScreen();
        hoverWindow.setLocation(loc.x - hoverWindow.getWidth() - 10, loc.y);
        hoverWindow.setVisible(true);
    }

    private void hideThumbnailPopup() {
        if (previewTimer != null) {
            previewTimer.stop();
            previewTimer = null;
        }
        if (hoverWindow != null) {
            hoverWindow.setVisible(false);
        }
    }

    private boolean isClickOnButton(MouseEvent e) {
        Component child = getComponentAt(e.getPoint());
        return child instanceof JButton;
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

    private class ModPanelTransferHandler extends TransferHandler {

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
                    if (parent.getComponent(i) == ModListPanel.this) {
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
                    onDragDrop.accept(sourceIdx, ModListPanel.this);
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
