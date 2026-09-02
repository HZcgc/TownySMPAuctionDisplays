package org.bukkit.entity;

public interface Display extends Entity {
    enum Billboard { FIXED, VERTICAL, HORIZONTAL, CENTER }
    void setBillboard(Billboard billboard);
    void setShadowRadius(float radius);
    void setShadowStrength(float strength);
    void setViewRange(float range);
}
