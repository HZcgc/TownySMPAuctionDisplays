package org.bukkit.event;

@FunctionalInterface
public interface EventExecutor {
    void execute(Listener listener, Event event);
}
