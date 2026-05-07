package com.stellaris.modmanager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class ModListPanel extends JPanel {
    
    private Runnable onModClick;
    private ModInfo currentMod;
    
    public ModListPanel(ModInfo modInfo, Runnable onModClick) {
        this.currentMod = modInfo;
        this.onModClick = onModClick;
        initializePanel();
    }
    
    private void initializePanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 63, 65), 1),
            new EmptyBorder(5, 10, 5, 10)
        ));
        setBackground(new Color(43, 43, 43));
        
        JLabel modLabel = createModLabel();
        add(modLabel, BorderLayout.CENTER);
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (onModClick != null) {
                    onModClick.run();
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                setBackground(new Color(50, 50, 50));
                repaint();
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                setBackground(new Color(43, 43, 43));
                repaint();
            }
        });
    }
    
    private JLabel createModLabel() {
        StringBuilder html = new StringBuilder("<html><body style='font-family: Consolas, monospace;'>");
        
        html.append("<span style='color: #FFFFFF;'>")
            .append(currentMod.getFolderName())
            .append("</span>: ");
        
        html.append("<span style='color: #CD853F;'>")
            .append(escapeHtml(currentMod.getModName()))
            .append("</span>, ");
        
        html.append("<span style='color: #D3D3D3;'>")
            .append(currentMod.getVersion())
            .append("</span>, ");
        
        html.append("<span style='color: #90EE90;'>")
            .append(currentMod.getSupportedVersion())
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
}
