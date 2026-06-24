package com.kylian.smartvillagers.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatSessionManager {
    private static final Map<UUID, UUID> PLAYER_TO_VILLAGER = new ConcurrentHashMap<>();

    public static void startSession(UUID playerUuid, UUID villagerUuid) {
        PLAYER_TO_VILLAGER.put(playerUuid, villagerUuid);
    }

    public static void endSession(UUID playerUuid) {
        PLAYER_TO_VILLAGER.remove(playerUuid);
    }

    public static UUID getTalkingVillager(UUID playerUuid) {
        return PLAYER_TO_VILLAGER.get(playerUuid);
    }

    public static boolean isPlayerInSession(UUID playerUuid) {
        return PLAYER_TO_VILLAGER.containsKey(playerUuid);
    }

    public static Map<UUID, UUID> getActiveSessions() {
        return PLAYER_TO_VILLAGER;
    }
}
