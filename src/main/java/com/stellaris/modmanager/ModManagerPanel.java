package com.stellaris.modmanager;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ModManagerPanel extends JPanel {

    private static final Logger LOG = Logger.getInstance(ModManagerPanel.class);

    private static final String SEARCH_PLACEHOLDER = "搜索模组（名称 / ID / 版本）";
    private static final int FILTER_DELAY_MS = 200;

    private final Project project;
    private JPanel modListContainer;
    private JLabel statusLabel;
    private JTextField searchField;
    private SwingWorker<List<ModInfo>, Void> currentWorker;
    private List<ModInfo> cachedMods;
    private List<ModInfo> visibleMods;
    private String statusSuffix = "图标视图";
    private Timer filterTimer;
    private boolean gridView = true;

    public ModManagerPanel(Project project) {
        this.project = project;
        initializePanel();
        loadMods();
    }

    public void refreshMods() {
        cancelCurrentWorker();
        loadMods();
    }

    public void setGridView(boolean grid) {
        this.gridView = grid;
        statusSuffix = grid ? "图标视图" : "列表视图";
        if (visibleMods != null) {
            rebuildModList(visibleMods);
        }
    }

    public void sortByName() {
        sortMods(Comparator.comparing(ModInfo::modName, String.CASE_INSENSITIVE_ORDER), "按名称排序");
    }

    public void sortBySize() {
        sortMods(Comparator.comparingLong(ModInfo::folderSize).reversed(), "按大小排序");
    }

    public void sortByTime() {
        sortMods(Comparator.comparingLong(ModInfo::lastModified).reversed(), "按时间排序");
    }

    private void sortMods(Comparator<ModInfo> comparator, String suffix) {
        if (cachedMods == null || cachedMods.isEmpty()) {
            return;
        }
        cachedMods.sort(comparator);
        statusSuffix = suffix;
        applyFilter();
    }

    private void cancelCurrentWorker() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
        }
    }

    private void initializePanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(43, 43, 43));
        setPreferredSize(new Dimension(520, 400));

        add(createSearchPanel(), BorderLayout.NORTH);
        add(createScrollPane(), BorderLayout.CENTER);

        statusLabel = new JLabel("准备就绪");
        statusLabel.setBorder(new EmptyBorder(5, 10, 5, 10));
        statusLabel.setForeground(new Color(187, 187, 187));
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JPanel createSearchPanel() {
        searchField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (!getText().isEmpty() || isFocusOwner()) {
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(new Color(120, 120, 120));
                g2.setFont(getFont());
                FontMetrics metrics = g2.getFontMetrics();
                g2.drawString(SEARCH_PLACEHOLDER, getInsets().left,
                        (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2);
                g2.dispose();
            }
        };
        searchField.setBackground(new Color(55, 55, 55));
        searchField.setForeground(new Color(220, 220, 220));
        searchField.setCaretColor(new Color(220, 220, 220));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70), 1),
                new EmptyBorder(4, 6, 4, 6)));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleFilter();
            }
        });
        searchField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearFilter");
        searchField.getActionMap().put("clearFilter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchField.setText("");
            }
        });

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(43, 43, 43));
        panel.setBorder(new EmptyBorder(6, 8, 4, 8));
        panel.add(searchField, BorderLayout.CENTER);
        return panel;
    }

    private JScrollPane createScrollPane() {
        modListContainer = new ScrollablePanel();
        modListContainer.setLayout(new BoxLayout(modListContainer, BoxLayout.Y_AXIS));
        modListContainer.setBackground(new Color(43, 43, 43));

        JScrollPane scrollPane = new JScrollPane(modListContainer);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        return scrollPane;
    }

    private void scheduleFilter() {
        if (filterTimer == null) {
            filterTimer = new Timer(FILTER_DELAY_MS, e -> applyFilter());
            filterTimer.setRepeats(false);
        }
        filterTimer.restart();
    }

    private void applyFilter() {
        if (cachedMods == null || searchField == null) {
            return;
        }
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            visibleMods = cachedMods;
        } else {
            visibleMods = cachedMods.stream()
                    .filter(mod -> matches(mod, query))
                    .collect(Collectors.toList());
        }
        rebuildModList(visibleMods);
    }

    private boolean matches(ModInfo mod, String query) {
        return mod.modName().toLowerCase(Locale.ROOT).contains(query)
                || mod.folderName().toLowerCase(Locale.ROOT).contains(query)
                || mod.version().toLowerCase(Locale.ROOT).contains(query)
                || mod.supportedVersion().toLowerCase(Locale.ROOT).contains(query);
    }

    private void loadMods() {
        modListContainer.removeAll();
        modListContainer.revalidate();
        modListContainer.repaint();
        statusLabel.setText("正在扫描模组...");

        currentWorker = new SwingWorker<>() {
            @Override
            protected List<ModInfo> doInBackground() throws Exception {
                List<String> workshopPaths = ModScanner.findWorkshopDirectories();

                if (workshopPaths.isEmpty()) {
                    return null;
                }

                ConcurrentHashMap<String, ModCacheManager.CacheEntry> cache =
                        ModCacheManager.load();

                if (workshopPaths.size() == 1) {
                    return ModScanner.scanMods(workshopPaths.get(0), cache);
                }

                ExecutorService executor = Executors.newFixedThreadPool(
                        Math.min(workshopPaths.size(), 4));
                List<Future<List<ModInfo>>> futures = new ArrayList<>();

                for (String path : workshopPaths) {
                    futures.add(executor.submit(() ->
                            ModScanner.scanMods(path, cache)));
                }
                executor.shutdown();

                List<ModInfo> allMods = new ArrayList<>();
                for (Future<List<ModInfo>> future : futures) {
                    if (isCancelled()) {
                        executor.shutdownNow();
                        return null;
                    }
                    try {
                        allMods.addAll(future.get());
                    } catch (Exception ignored) {
                    }
                }

                return allMods;
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    List<ModInfo> mods = get();
                    cachedMods = mods == null ? new ArrayList<>() : mods;
                    applyFilter();

                    ConcurrentHashMap<String, ModCacheManager.CacheEntry> cache =
                            new ConcurrentHashMap<>();
                    for (ModInfo mod : cachedMods) {
                        cache.put(mod.folderName(),
                                new ModCacheManager.CacheEntry(mod));
                    }
                    ModCacheManager.save(cache);

                } catch (Exception e) {
                    LOG.warn("Failed to load mods", e);
                    showError("加载模组失败: " + e.getMessage());
                }
            }
        };

        currentWorker.execute();
    }

    private void rebuildModList(List<ModInfo> mods) {
        ThumbnailPreview.hide();
        modListContainer.removeAll();

        if (mods == null || mods.isEmpty()) {
            JLabel emptyLabel = new JLabel(cachedMods == null || cachedMods.isEmpty()
                    ? "未找到 Stellaris Workshop 目录或模组"
                    : "没有匹配的模组");
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            emptyLabel.setForeground(new Color(187, 187, 187));
            emptyLabel.setBorder(new EmptyBorder(20, 10, 20, 10));
            modListContainer.add(emptyLabel);
        } else if (gridView) {
            modListContainer.setLayout(new WrapLayout(FlowLayout.LEFT, 8, 8));
            for (ModInfo mod : mods) {
                ModCardPanel card = new ModCardPanel(mod,
                        () -> openModProject(mod),
                        (sourceIdx, targetPanel) -> {
                            int targetIdx = findPanelIndex(targetPanel);
                            if (targetIdx >= 0) {
                                moveMod(sourceIdx, targetIdx);
                            }
                        }
                );
                modListContainer.add(card);
            }
        } else {
            modListContainer.setLayout(new BoxLayout(modListContainer, BoxLayout.Y_AXIS));
            for (int i = 0; i < mods.size(); i++) {
                ModInfo mod = mods.get(i);
                int index = i;
                ModListPanel modPanel = new ModListPanel(mod,
                        () -> openModProject(mod),
                        () -> moveMod(index, index - 1),
                        () -> moveMod(index, index + 1),
                        (sourceIdx, targetPanel) -> {
                            int targetIdx = findPanelIndex(targetPanel);
                            if (targetIdx >= 0) {
                                moveMod(sourceIdx, targetIdx);
                            }
                        }
                );
                modListContainer.add(modPanel);
            }
        }

        modListContainer.revalidate();
        modListContainer.repaint();
        updateStatus(mods == null ? 0 : mods.size());
    }

    private void updateStatus(int shown) {
        if (cachedMods == null || cachedMods.isEmpty()) {
            statusLabel.setText("未找到模组");
            return;
        }
        StringBuilder text = new StringBuilder("找到 ").append(shown).append(" 个模组");
        if (shown != cachedMods.size()) {
            text.append(" / 共 ").append(cachedMods.size());
        }
        if (!statusSuffix.isEmpty()) {
            text.append(" (").append(statusSuffix).append(')');
        }
        statusLabel.setText(text.toString());
    }

    private int findPanelIndex(Component target) {
        for (int i = 0; i < modListContainer.getComponentCount(); i++) {
            if (modListContainer.getComponent(i) == target) {
                return i;
            }
        }
        return -1;
    }

    private void moveMod(int fromIndex, int toIndex) {
        if (visibleMods == null || cachedMods == null) {
            return;
        }
        int count = visibleMods.size();
        if (fromIndex < 0 || toIndex < 0 || fromIndex >= count || toIndex >= count
                || fromIndex == toIndex) {
            return;
        }

        ModInfo moved = visibleMods.get(fromIndex);
        ModInfo target = visibleMods.get(toIndex);
        int from = cachedMods.indexOf(moved);
        if (from < 0) {
            return;
        }

        cachedMods.remove(from);
        int to = cachedMods.indexOf(target);
        if (to < 0) {
            cachedMods.add(moved);
        } else {
            cachedMods.add(toIndex > fromIndex ? to + 1 : to, moved);
        }

        applyFilter();
        scrollToIndex(toIndex);
    }

    private void scrollToIndex(int index) {
        if (index < 0 || index >= modListContainer.getComponentCount()) {
            return;
        }
        Component component = modListContainer.getComponent(index);
        if (component instanceof JComponent jc) {
            jc.scrollRectToVisible(jc.getBounds());
        }
    }

    private void openModProject(ModInfo modInfo) {
        try {
            ProjectUtil.openOrImport(modInfo.modPath(), project, false);
        } catch (Exception e) {
            LOG.warn("Failed to open mod project: " + modInfo.modPath(), e);
            showError("无法打开模组项目: " + e.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "错误", JOptionPane.ERROR_MESSAGE);
        statusLabel.setText("错误: " + message);
    }
}
