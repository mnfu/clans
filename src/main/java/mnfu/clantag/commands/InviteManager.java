package mnfu.clantag.commands;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InviteManager {
    // maps uuids -> clan's names they're currently invited to
    private final Map<UUID, Set<String>> pendingInvites = new ConcurrentHashMap<>();

    /**
     * Adds an invite for a player to join a clan.
     * @param playerUuid the UUID of the player being invited
     * @param clanName the name of the clan they're invited to
     */
    public void addInvite(UUID playerUuid, String clanName) {
        pendingInvites.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet()).add(clanName);
    }

    /**
     * Removes a specific invite for a player.
     * @return true if the invite existed and was removed, false otherwise
     */
    public boolean removeInvite(UUID playerUuid, String clanName) {
        Set<String> invites = pendingInvites.get(playerUuid);
        if (invites != null) {
            boolean removed = invites.remove(clanName);
            if (invites.isEmpty()) {
                pendingInvites.remove(playerUuid);
            }
            return removed;
        }
        return false;
    }

    /**
     * Gets all pending invites for a player.
     * @return an unmodifiable set of clan names, or empty set if no invites
     */
    public Set<String> getInvites(UUID playerUuid) {
        Set<String> invites = pendingInvites.get(playerUuid);
        return invites != null ? Collections.unmodifiableSet(new HashSet<>(invites)) : Collections.emptySet();
    }

    /**
     * Clears all invites for a specific player.
     * @param playerUuid the UUID of the player
     */
    public void clearInvitesForPlayer(UUID playerUuid) {
        pendingInvites.remove(playerUuid);
    }

    /**
     * Clears all invites for a specific clan.
     * @param clanName the name of the clan
     */
    public void clearInvitesForClan(String clanName) {
        pendingInvites.values().forEach(invites -> invites.remove(clanName));
        pendingInvites.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Checks if a player has a pending invite to a specific clan.
     * @return true if the player has a pending invite to the clan
     */
    public boolean hasInvite(UUID playerUuid, String clanName) {
        Set<String> invites = pendingInvites.get(playerUuid);
        return invites != null && invites.contains(clanName);
    }
}
