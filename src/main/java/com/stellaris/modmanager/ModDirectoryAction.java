package com.stellaris.modmanager;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

public class ModDirectoryAction extends AnAction {

    private static final int MENU_PATH_LENGTH = 46;

    private final ModManagerPanel panel;
    private final Project project;

    public ModDirectoryAction(ModManagerPanel panel, Project project) {
        super("模组目录", "添加或管理模组目录（自动识别不到时使用，可添加多个）", AllIcons.General.Settings);
        this.panel = panel;
        this.project = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        List<String> directories = ModPathSettings.getWorkshopDirectories();

        JPopupMenu menu = new JPopupMenu();

        JMenuItem addItem = new JMenuItem("添加模组目录…");
        addItem.addActionListener(event -> chooseAndAdd(project, panel));
        menu.add(addItem);

        menu.addSeparator();

        if (directories.isEmpty()) {
            JMenuItem autoItem = new JMenuItem("当前：自动识别");
            autoItem.setEnabled(false);
            menu.add(autoItem);
        } else {
            for (String directory : directories) {
                JMenuItem removeItem = new JMenuItem("移除 " + shorten(directory));
                removeItem.setToolTipText(directory);
                removeItem.addActionListener(event -> {
                    ModPathSettings.removeWorkshopDirectory(directory);
                    panel.refreshMods();
                });
                menu.add(removeItem);
            }
            menu.addSeparator();

            JMenuItem resetItem = new JMenuItem("恢复自动识别（清空 " + directories.size() + " 个目录）");
            resetItem.addActionListener(event -> {
                ModPathSettings.clearWorkshopDirectories();
                panel.refreshMods();
            });
            menu.add(resetItem);
        }

        if (e.getInputEvent() != null) {
            Component source = e.getInputEvent().getComponent();
            menu.show(source, 0, source.getHeight());
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        int count = ModPathSettings.getWorkshopDirectories().size();
        e.getPresentation().setDescription(count == 0
                ? "添加或管理模组目录（当前为自动识别）"
                : "添加或管理模组目录（已手动指定 " + count + " 个）");
    }

    public static void chooseAndAdd(Project project, ModManagerPanel panel) {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false)
                .withTitle("添加模组目录")
                .withDescription("可以选模组所在目录（steamapps\\workshop\\content\\281990）、"
                        + "Steam 根目录，或某个模组文件夹；可添加多个");

        VirtualFile selected = FileChooser.chooseFile(descriptor, project, null);
        if (selected == null) {
            return;
        }

        Path resolved = ModScanner.resolveWorkshopDirectory(selected.getPath());
        if (resolved == null) {
            JOptionPane.showMessageDialog(panel,
                    "这个目录里没有找到模组（需要包含带 descriptor.mod 的文件夹），也不是 Steam / workshop 目录。",
                    "目录无效", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!ModPathSettings.addWorkshopDirectory(resolved.toString())) {
            JOptionPane.showMessageDialog(panel,
                    "这个目录已经在列表里了：\n" + resolved,
                    "已存在", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        panel.refreshMods();
    }

    private static String shorten(String path) {
        return path.length() <= MENU_PATH_LENGTH
                ? path
                : "…" + path.substring(path.length() - MENU_PATH_LENGTH);
    }
}
