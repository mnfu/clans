package mnfu.clantag;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MojangCache {

    private static final long NAME_TTL = 24 * 60 * 60 * 1000L; // 24 hours, b/c if stale, no biggie. might display old name, and this is used more frequently.
    private static final long UUID_TTL = 15 * 60 * 1000L; // 15 minutes, b/c if stale, could be bad. inviting the wrong account to a clan for example.

    private final ConcurrentHashMap<UUID, CacheEntry<String>> uuidToName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<UUID>> nameToUuid = new ConcurrentHashMap<>();

    @Nullable
    public String getUsername(UUID uuid) {
        CacheEntry<String> entry = uuidToName.get(uuid);
        if (entry != null) {
            if (entry.isExpired()) {
                uuidToName.remove(uuid);
            } else {
                return entry.getValue();
            }
        }
        return null;
    }

    @Nullable
    public UUID getUUID(String name) {
        CacheEntry<UUID> entry = nameToUuid.get(name);
        if (entry != null) {
            if (entry.isExpired()) {
                nameToUuid.remove(name);
            } else {
                return entry.getValue();
            }
        }
        return null;
    }

    public boolean containsKey(UUID uuid) {
        return uuidToName.containsKey(uuid);
    }

    public boolean containsKey(String name) {
        return nameToUuid.containsKey(name);
    }

    public void put(UUID uuid, String name) {
        put(uuid, name, NAME_TTL, UUID_TTL);
    }

    public void put(UUID uuid, String name, long nameTTL, long uuidTTL) {
        // ensures we're not trying to put null keys into a map. null values inside cache entries are fine.
        if (uuid != null) {
            uuidToName.put(uuid, new CacheEntry<>(name, nameTTL));
        }
        if (name != null) {
            nameToUuid.put(name, new CacheEntry<>(uuid, uuidTTL));
        }
    }

    public void nuke() {
        uuidToName.clear();
        nameToUuid.clear();
    }
}
