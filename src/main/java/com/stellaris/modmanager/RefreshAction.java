package com.stellaris.modmanager;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class RefreshAction extends AnAction {
    private final ModManagerPanel panel;

    public RefreshAction(ModManagerPanel panel) {
        super("刷新", "刷新模组列表", AllIcons.Actions.Refresh);
        this.panel = panel;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        panel.refreshMods();
    }
}
