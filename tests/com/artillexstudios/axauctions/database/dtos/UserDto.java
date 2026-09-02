package com.artillexstudios.axauctions.database.dtos;

import java.util.UUID;

public final class UserDto {
    private final String name;
    private final UUID uuid;

    public UserDto(String name, UUID uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public UUID getUUID() {
        return uuid;
    }
}
