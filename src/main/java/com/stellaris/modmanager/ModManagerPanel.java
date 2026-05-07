package com.stellaris.modmanager;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class ModManagerPanel extends JPanel {
    
    private Project project;
    private JPanel modListContainer;
    private JLabel statusLabel;
    
    public ModManagerPanel(Project project) {
        this.project = project;
        initializePanel();
        loadMods();
    }
    
    public void refreshMods() {
        loadMods();
    }

    private void initializePanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(43, 43, 43));
        
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
        
        return scrollPane;
    }
    
    private void loadMods() {
        modListContainer.removeAll();
        statusLabel.setText("正在扫描模组...");
        
        SwingWorker<List<ModInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<ModInfo> doInBackground() throws Exception {
                List<String> workshopPaths = ModScanner.findWorkshopDirectories();
                
                if (workshopPaths.isEmpty()) {
                    return null;
                }
                
                List<ModInfo> allMods = new java.util.ArrayList<>();
                for (String path : workshopPaths) {
                    allMods.addAll(ModScanner.scanMods(path));
                }
                
                return allMods;
            }
            
            @Override
            protected void done() {
                try {
                    List<ModInfo> mods = get();
                    
                    if (mods == null || mods.isEmpty()) {
                        showNoModsFound();
                        return;
                    }
                    
                    for (ModInfo mod : mods) {
                        ModListPanel modPanel = new ModListPanel(mod, () -> openModProject(mod));
                        modListContainer.add(modPanel);
                        
                        JSeparator separator = new JSeparator();
                        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                        separator.setBackground(new Color(60, 63, 65));
                        modListContainer.add(separator);
                    }
                    
                    statusLabel.setText("找到 " + mods.size() + " 个模组");
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    showError("加载模组失败: " + e.getMessage());
                }
                
                modListContainer.revalidate();
                modListContainer.repaint();
            }
        };
        
        worker.execute();
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
            ProjectUtil.openOrImport(modInfo.getModPath(), project, false);
        } catch (Exception e) {
            e.printStackTrace();
            showError("无法打开模组项目: " + e.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "错误", JOptionPane.ERROR_MESSAGE);
        statusLabel.setText("错误: " + message);
    }
}
