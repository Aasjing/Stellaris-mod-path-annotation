package com.stellaris.modmanager;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class SortAction extends AnAction {

    private final ModManagerPanel panel;

    public SortAction(ModManagerPanel panel) {
        super("排序", "按名称/大小/时间排序", AllIcons.Actions.Find);
        this.panel = panel;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem nameItem = new JMenuItem("按名称排序");
        nameItem.addActionListener(ev -> panel.sortByName());
        menu.add(nameItem);

        JMenuItem sizeItem = new JMenuItem("按大小排序");
        sizeItem.addActionListener(ev -> panel.sortBySize());
        menu.add(sizeItem);

        JMenuItem timeItem = new JMenuItem("按修改时间排序");
        timeItem.addActionListener(ev -> panel.sortByTime());
        menu.add(timeItem);

        menu.addSeparator();

        JMenuItem refreshItem = new JMenuItem("刷新恢复原始顺序");
        refreshItem.addActionListener(ev -> panel.refreshMods());
        menu.add(refreshItem);

        if (e.getInputEvent() != null) {
            Component source = e.getInputEvent().getComponent();
            menu.show(source, 0, source.getHeight());
        }
    }
}
