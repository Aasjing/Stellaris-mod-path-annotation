package com.stellaris.modmanager;

import java.awt.*;

public class WrapLayout extends FlowLayout {

    public WrapLayout() {
        super(LEFT, 8, 8);
    }

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getSize().width;

            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            int nmembers = target.getComponentCount();

            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (!m.isVisible()) {
                    continue;
                }
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                if (rowWidth + d.width > maxWidth) {
                    addRow(dim, rowWidth, rowHeight);
                    rowWidth = 0;
                    rowHeight = 0;
                }

                if (rowWidth != 0) {
                    rowWidth += hgap;
                }

                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }

            addRow(dim, rowWidth, rowHeight);

            dim.width += horizontalInsetsAndGap;
            dim.height += insets.top + insets.bottom + vgap * 2;

            Insets scrollBarInsets = getScrollBarInsets(target);
            if (scrollBarInsets != null) {
                dim.width += scrollBarInsets.left + scrollBarInsets.right;
                dim.height += scrollBarInsets.top + scrollBarInsets.bottom;
            }

            return dim;
        }
    }

    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);
        if (dim.height > 0) {
            dim.height += getVgap();
        }
        dim.height += rowHeight;
    }

    private Insets getScrollBarInsets(Container target) {
        Container parent = target.getParent();
        if (parent instanceof javax.swing.JViewport) {
            Container grandParent = parent.getParent();
            if (grandParent instanceof javax.swing.JScrollPane) {
                javax.swing.JScrollPane scrollPane = (javax.swing.JScrollPane) grandParent;
                javax.swing.JScrollBar vsb = scrollPane.getVerticalScrollBar();
                if (vsb != null && vsb.isVisible()) {
                    return new Insets(0, 0, 0, vsb.getWidth());
                }
            }
        }
        return null;
    }

    @Override
    public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getSize().width;

            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int maxWidth = targetWidth - (insets.left + insets.right + hgap * 2);
            int nmembers = target.getComponentCount();
            int x = 0;
            int y = insets.top + vgap;
            int rowHeight = 0;
            int start = 0;

            boolean ltr = target.getComponentOrientation().isLeftToRight();

            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (!m.isVisible()) {
                    continue;
                }
                Dimension d = m.getPreferredSize();

                if (x + d.width > maxWidth) {
                    layoutRow(target, start, i, y, rowHeight, insets.left + hgap, maxWidth, ltr);
                    x = 0;
                    y += vgap + rowHeight;
                    rowHeight = 0;
                    start = i;
                }

                if (x != 0) {
                    x += hgap;
                }

                x += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }

            layoutRow(target, start, nmembers, y, rowHeight, insets.left + hgap, maxWidth, ltr);
        }
    }

    private void layoutRow(Container target, int start, int end, int y, int rowHeight,
                           int leftOffset, int maxWidth, boolean ltr) {
        int hgap = getHgap();
        int x;

        int rowWidth = 0;
        int visibleCount = 0;
        for (int i = start; i < end; i++) {
            Component m = target.getComponent(i);
            if (m.isVisible()) {
                Dimension d = m.getPreferredSize();
                rowWidth += d.width;
                visibleCount++;
            }
        }
        rowWidth += (visibleCount - 1) * hgap;

        int alignment = getAlignment();
        if (alignment == RIGHT) {
            x = leftOffset + maxWidth - rowWidth;
        } else if (alignment == CENTER) {
            x = leftOffset + (maxWidth - rowWidth) / 2;
        } else {
            x = leftOffset;
        }

        for (int i = start; i < end; i++) {
            Component m = target.getComponent(i);
            if (m.isVisible()) {
                Dimension d = m.getPreferredSize();
                if (ltr) {
                    m.setBounds(x, y + (rowHeight - d.height) / 2, d.width, d.height);
                } else {
                    m.setBounds(target.getWidth() - x - d.width, y + (rowHeight - d.height) / 2, d.width, d.height);
                }
                x += d.width + hgap;
            }
        }
    }
}
