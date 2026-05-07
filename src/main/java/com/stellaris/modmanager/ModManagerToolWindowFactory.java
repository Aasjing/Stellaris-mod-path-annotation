package com.stellaris.modmanager;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

import java.util.List;

public class ModManagerToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        ModManagerPanel panel = new ModManagerPanel(project);

        toolWindow.setTitle("Stellaris mod 路径标注：");
        toolWindow.setTitleActions(List.of(new RefreshAction(panel), new ToggleViewAction(panel)));

        @SuppressWarnings("deprecation")
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
