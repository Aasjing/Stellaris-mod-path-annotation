package com.stellaris.modmanager;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Toggleable;
import org.jetbrains.annotations.NotNull;

public class ToggleViewAction extends AnAction implements Toggleable {

    private final ModManagerPanel panel;
    private boolean gridMode = false;

    public ToggleViewAction(ModManagerPanel panel) {
        super("切换视图", "切换列表/图标视图", AllIcons.Actions.GroupBy);
        this.panel = panel;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        gridMode = !gridMode;
        panel.setGridView(gridMode);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setText(gridMode ? "列表视图" : "图标视图");
        e.getPresentation().setDescription(gridMode ? "切换为列表视图" : "切换为图标视图");
    }
}
