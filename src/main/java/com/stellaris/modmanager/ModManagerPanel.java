package com.stellaris.modmanager;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ModManagerPanel extends JPanel {

    private static final Logger LOG = Logger.getInstance(ModManagerPanel.class);

    private final Project project;
    private JPanel modListContainer;
    private JLabel statusLabel;
    private SwingWorker<List<ModInfo>, Void> currentWorker;
    private List<ModInfo> cachedMods;
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
        if (cachedMods != null) {
            rebuildModList(cachedMods);
        }
    }

    public void sortByName() {
        if (cachedMods == null || cachedMods.isEmpty()) {
            return;
        }
        cachedMods.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.modName(), b.modName()));
        rebuildModList(cachedMods);
        statusLabel.setText("找到 " + cachedMods.size() + " 个模组 (按名称排序)");
    }

    public void sortBySize() {
        if (cachedMods == null || cachedMods.isEmpty()) {
            return;
        }
        cachedMods.sort((a, b) -> Long.compare(b.folderSize(), a.folderSize()));
        rebuildModList(cachedMods);
        statusLabel.setText("找到 " + cachedMods.size() + " 个模组 (按大小排序)");
    }

    public void sortByTime() {
        if (cachedMods == null || cachedMods.isEmpty()) {
            return;
        }
        cachedMods.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        rebuildModList(cachedMods);
        statusLabel.setText("找到 " + cachedMods.size() + " 个模组 (按时间排序)");
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

        JScrollPane scrollPane = createScrollPane();
        add(scrollPane, BorderLayout.CENTER);

        statusLabel = new JLabel("准备就绪");
        statusLabel.setBorder(new EmptyBorder(5, 10, 5, 10));
        statusLabel.setForeground(new Color(187, 187, 187));
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JScrollPane createScrollPane() {
        modListContainer = new JPanel();
        modListContainer.setLayout(new BoxLayout(modListContainer, BoxLayout.Y_AXIS));
        modListContainer.setBackground(new Color(43, 43, 43));

        JScrollPane scrollPane = new JScrollPane(modListContainer);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        return scrollPane;
    }

    private void loadMods() {
        modListContainer.removeAll();
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

                    if (mods == null || mods.isEmpty()) {
                        showNoModsFound();
                        return;
                    }

                    cachedMods = mods;
                    rebuildModList(mods);

                    ConcurrentHashMap<String, ModCacheManager.CacheEntry> cache =
                            new ConcurrentHashMap<>();
                    for (ModInfo mod : mods) {
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
        modListContainer.removeAll();

        if (gridView) {
            modListContainer.setLayout(new WrapLayout(FlowLayout.LEFT, 8, 8));
            for (int i = 0; i < mods.size(); i++) {
                ModInfo mod = mods.get(i);
                int index = i;
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
            statusLabel.setText("找到 " + mods.size() + " 个模组 (图标视图)");
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
            statusLabel.setText("找到 " + mods.size() + " 个模组");
        }

        modListContainer.revalidate();
        modListContainer.repaint();
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
        if (fromIndex < 0 || toIndex < 0) {
            return;
        }
        int count = modListContainer.getComponentCount();
        if (fromIndex >= count || toIndex >= count) {
            return;
        }
        if (fromIndex == toIndex) {
            return;
        }

        Component comp = modListContainer.getComponent(fromIndex);
        modListContainer.remove(fromIndex);
        modListContainer.add(comp, toIndex);

        ModInfo moved = cachedMods.remove(fromIndex);
        cachedMods.add(toIndex, moved);

        refreshAllPanelCallbacks();
        modListContainer.revalidate();
        modListContainer.repaint();

        if (comp instanceof JComponent jc) {
            jc.scrollRectToVisible(jc.getBounds());
        }
    }

    private void refreshAllPanelCallbacks() {
        for (int i = 0; i < modListContainer.getComponentCount(); i++) {
            Component comp = modListContainer.getComponent(i);
            if (comp instanceof ModListPanel panel) {
                ModInfo mod = cachedMods.get(i);
                int index = i;
                panel.updateCallbacks(
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
            } else if (comp instanceof ModCardPanel card) {
                ModInfo mod = cachedMods.get(i);
                card.updateCallbacks(
                        () -> openModProject(mod),
                        (sourceIdx, targetPanel) -> {
                            int targetIdx = findPanelIndex(targetPanel);
                            if (targetIdx >= 0) {
                                moveMod(sourceIdx, targetIdx);
                            }
                        }
                );
            }
        }
    }

    private void showNoModsFound() {
        JLabel noModsLabel = new JLabel("未找到 Stellaris Workshop 目录或模组");
        noModsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        noModsLabel.setForeground(new Color(187, 187, 187));
        noModsLabel.setBorder(new EmptyBorder(20, 10, 20, 10));
        modListContainer.add(noModsLabel);
        statusLabel.setText("未找到模组");
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
