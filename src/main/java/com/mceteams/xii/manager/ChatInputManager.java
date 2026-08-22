package com.mceteams.xii.manager;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class ChatInputManager {
    private final Map<UUID, Consumer<String>> pendingInputs = new HashMap<>();

    public void requestInput(Player player, String prompt, Consumer<String> callback) {
        pendingInputs.put(player.getUniqueId(), callback);
        player.sendMessage(prompt);
    }

    public boolean hasPending(Player player) {
        return pendingInputs.containsKey(player.getUniqueId());
    }

    public void submit(Player player, String input) {
        Consumer<String> callback = pendingInputs.remove(player.getUniqueId());
        if (callback != null) {
            callback.accept(input);
        }
    }

    public void cancel(Player player) {
        pendingInputs.remove(player.getUniqueId());
    }
}
