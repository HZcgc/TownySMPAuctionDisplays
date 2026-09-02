package org.bukkit.entity;

public interface TextDisplay extends Display {
    enum TextAlignment { CENTER, LEFT, RIGHT }
    void setText(String text);
    void setAlignment(TextAlignment alignment);
    void setSeeThrough(boolean seeThrough);
    void setShadowed(boolean shadowed);
    void setDefaultBackground(boolean defaultBackground);
    void setLineWidth(int width);
}
