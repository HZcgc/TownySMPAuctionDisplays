package org.bukkit.entity;

import java.util.UUID;

public interface Entity {
    UUID getUniqueId();
    void remove();
    boolean isValid();
    boolean isDead();
    void setPersistent(boolean persistent);
    void setInvulnerable(boolean invulnerable);
    void setGravity(boolean gravity);
    void setSilent(boolean silent);
    void setRotation(float yaw, float pitch);
}
