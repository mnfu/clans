package mnfu.clantag;

import org.slf4j.Logger;

import java.sql.*;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PersistentPlayerCache {

    private static PersistentPlayerCache INSTANCE;

    private final Map<UUID, String> uuidToName = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameToUuid = new ConcurrentHashMap<>();
    private final Logger logger;
    private Connection connection;

    public static PersistentPlayerCache getInstance() {
        return INSTANCE;
    }

    public static void init(Logger logger) {
        if (INSTANCE == null) {
            INSTANCE = new PersistentPlayerCache(logger);
        }
    }

    private PersistentPlayerCache(Logger logger) {
        this.logger = logger;
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:config/clans/player_cache.db");
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS player_names (
                            uuid TEXT PRIMARY KEY,
                            username TEXT NOT NULL
                        )
                        """);
            }
            loadIntoMemory();
        } catch (SQLException e) {
            logger.error("Failed to initialize persistent player cache", e);
        } catch (ClassNotFoundException e) {
            logger.error("SQLite JDBC driver not found, not included in jar?", e);
        }
    }

    private void loadIntoMemory() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT uuid, username FROM player_names")) {
            int count = 0;
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String username = rs.getString("username");
                uuidToName.put(uuid, username);
                nameToUuid.put(username.toLowerCase(), uuid);
                count++;
            }
            logger.info("Loaded {} entries from persistent player cache", count);
        }
    }

    public Optional<String> getUsername(UUID uuid) {
        return Optional.ofNullable(uuidToName.get(uuid));
    }

    public Optional<UUID> getUuid(String username) {
        return Optional.ofNullable(nameToUuid.get(username.toLowerCase()));
    }

    /**
     * Updates the cache if the username has changed or the UUID is new.
     * Returns true if an update was made.
     */
    public boolean updateIfChanged(UUID uuid, String username) {
        String existing = uuidToName.get(uuid);
        if (username.equals(existing)) return false;

        // remove stale reverse mapping
        if (existing != null) {
            nameToUuid.remove(existing.toLowerCase());
        }

        uuidToName.put(uuid, username);
        nameToUuid.put(username.toLowerCase(), uuid);

        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR REPLACE INTO player_names (uuid, username) VALUES (?, ?)")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to persist player cache entry for {}", uuid, e);
        }

        return true;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.error("Failed to close persistent player cache connection", e);
        }
    }
}