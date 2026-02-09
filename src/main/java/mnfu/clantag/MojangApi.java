package mnfu.clantag;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.network.ServerPlayerEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class MojangApi {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final MojangCache CACHE = new MojangCache();

    /**
     * Resolves the current Minecraft username associated with the given UUID.
     *
     * <p>The returned {@link CompletableFuture} completes with an {@link Optional} containing
     * the username if found. If the UUID is invalid, the request fails, or the result was
     * previously cached as unresolved, the {@link Optional} will be empty.</p>
     *
     * <p>The result is cached in {@link MojangCache} for future lookups. Rate limiting,
     * invalid UUIDs, or API errors may temporarily cache an empty result to avoid repeated
     * failed requests.</p>
     *
     * @param uuid the UUID of the player
     * @return a {@link CompletableFuture} that completes with an {@link Optional} containing
     *         the username if found, or empty if not
     */
    public static CompletableFuture<Optional<String>> getUsername(UUID uuid) {
        String username = CACHE.getUsername(uuid);
        if (username != null) return CompletableFuture.completedFuture(Optional.of(username));
        if (CACHE.containsKey(uuid)) {
            return CompletableFuture.completedFuture(Optional.empty()); // data was cached as null (meaning it was a bad request previously)
        }
        try {
            String strippedUUID = uuid.toString().replaceAll("-", "");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "https://api.minecraftservices.com/minecraft/profile/lookup/" + strippedUUID
                    ))
                    .GET()
                    .build();
            return CLIENT.sendAsync(
                    request,
                    HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> handleUsernameResponse(response, uuid))
                    .exceptionally(ex -> Optional.empty());
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private static Optional<String> handleUsernameResponse(HttpResponse<String> response, UUID uuid) {
        int statusCode = response.statusCode();
        if (statusCode == 200) {
            try {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                String username = json.get("name").getAsString();
                CACHE.put(uuid, username);
                return Optional.of(username);
            } catch (Exception e) {
                return Optional.empty();
            }
        } else if (statusCode == 429) { // we were rate limited
            long ttl = 10 * 60 * 1000L; // 10 mins, because that's the rate limit timeout from mojang
            CACHE.put(uuid, null, ttl, ttl);
            return Optional.empty();
        }
        else if (statusCode >=400 && statusCode < 500) { // assume bad request, cache that it was bad
            CACHE.put(uuid, null);
            return Optional.empty();
        } else { // assume server error, don't cache
            return Optional.empty();
        }
    }

    /**
     * Resolves the UUID associated with the given Minecraft username.
     *
     * <p>The returned {@link CompletableFuture} completes with an {@link Optional} containing
     * the UUID if found. If the username is invalid, the request fails, or the result was
     * previously cached as unresolved, the {@link Optional} will be empty.</p>
     *
     * <p>The result is cached in {@link MojangCache} for future lookups. Rate limiting,
     * invalid usernames, or API errors may temporarily cache an empty result to avoid repeated
     * failed requests.</p>
     *
     * @param username the Minecraft username
     * @return a {@link CompletableFuture} that completes with an {@link Optional} containing
     *         the UUID if found, or empty if not
     */
    public static CompletableFuture<Optional<UUID>> getUuid(String username) {
        UUID uuid = CACHE.getUUID(username);
        if (uuid != null) return CompletableFuture.completedFuture(Optional.of(uuid));
        if (CACHE.containsKey(username)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "https://api.minecraftservices.com/minecraft/profile/lookup/name/" + username
                    ))
                    .GET()
                    .build();
            return CLIENT.sendAsync(
                    request, HttpResponse.BodyHandlers.ofString())
                        .thenApply(response -> handleUuidResponse(response, username))
                        .exceptionally(ex -> Optional.empty());
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private static Optional<UUID> handleUuidResponse(HttpResponse<String> response, String username) {
        int statusCode = response.statusCode();
        if (statusCode == 200) {
            try {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                String uuidString = json.get("id").getAsString();
                // we need to add back the dashes to the uuid since mojang gives non-dashed uuids back
                if (uuidString.length() != 32) return Optional.empty();
                // it should pretty much always be of 32 length, if this is no longer the contract we have bigger problems lol
                String dashedUuidString =
                        uuidString.substring(0, 8) + "-" +
                        uuidString.substring(8, 12) + "-" +
                        uuidString.substring(12, 16) + "-" +
                        uuidString.substring(16, 20) + "-" +
                        uuidString.substring(20);
                UUID uuid = UUID.fromString(dashedUuidString);
                CACHE.put(uuid, username);
                return Optional.of(uuid);
            } catch (Exception e) {
                return Optional.empty();
            }
        } else if (statusCode == 429) { // we were rate limited
            long ttl = 10 * 60 * 1000L; // 10 mins, because that's the rate limit timeout from mojang
            CACHE.put(null, username, ttl, ttl);
            return Optional.empty();
        } else if (statusCode >= 400 && statusCode < 500) {
            CACHE.put(null, username);
            return Optional.empty();
        } else {
            return Optional.empty();
        }
    }

    /**
     * Caches a currently online player’s UUID and username in {@link MojangCache}.
     *
     * @param player the online {@link ServerPlayerEntity} to cache
     */
    public static void cachePlayer(ServerPlayerEntity player) {
        CACHE.put(player.getUuid(), player.getName().getString());
    }

    /**
     * Clears the entire {@link MojangCache}, removing all cached UUID → username and
     * username → UUID mappings.
     */
    public static void clearCache() {
        CACHE.nuke();
    }
}

