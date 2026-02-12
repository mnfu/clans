package mnfu.clantag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import mnfu.clantag.commands.InviteManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class ClanManager {
    private final File file;
    private final Gson gson;
    private final Map<String, Clan> clans = new HashMap<>(); // key: clan name
    private final Map<UUID, String> playerToClanName = new HashMap<>(); // key: player uuid
    private final Logger logger;
    private final InviteManager inviteManager;
    private static boolean ENABLE_SAVES = true;

    public ClanManager(File file, Logger logger, InviteManager inviteManager) {
        this.logger = logger;
        this.file = file;
        this.inviteManager = inviteManager;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
    }

    /**
     * tries to create a clan
     *
     * @return true if clan was created, false if clan was not created
     */
    public boolean createClan(String clanName, UUID leaderUuid, String hexColor) {
        clanName = forceFirstCharUppercase(clanName);
        if (clans.containsKey(clanName)) {
            return false;
        }
        LinkedHashSet<UUID> members = new LinkedHashSet<>();
        members.add(leaderUuid);
        hexColor = "#" + hexColor;

        Clan clan = new Clan(clanName, leaderUuid, members, hexColor);
        clans.put(clanName, clan);
        playerToClanName.put(leaderUuid, clan.name());
        save();

        inviteManager.clearInvitesForPlayer(leaderUuid);
        return true;
    }

    public void deleteClan(String clanName) {
        clanName = forceFirstCharUppercase(clanName);
        if (clans.containsKey(clanName)) {
            Clan clan = clans.get(clanName);
            for (UUID memberUuid : clan.members()) {
                playerToClanName.remove(memberUuid);
            }
            clans.remove(clanName);
            save();
            inviteManager.clearInvitesForClan(clanName);
        }
    }

    public void addMember(String clanName, UUID memberUuid) {
        clanName = forceFirstCharUppercase(clanName);
        Clan clan = clans.get(clanName);
        if (clan == null) {
            System.out.println("Clan not found!");
            return;
        }
        LinkedHashSet<UUID> members = new LinkedHashSet<>(clan.members());
        if (members.contains(memberUuid)) return;
        members.add(memberUuid);

        Clan updatedClan = new Clan(clan.name(), clan.leader(), members, clan.hexColor());
        clans.put(clanName, updatedClan);
        playerToClanName.put(memberUuid, updatedClan.name());
        save();

        inviteManager.clearInvitesForPlayer(memberUuid);
    }

    public void removeMember(String clanName, UUID memberUUID) {
        Clan clan = clans.get(clanName);
        if (clan == null) return;
        LinkedHashSet<UUID> members = new LinkedHashSet<>(clan.members());
        members.remove(memberUUID);

        Clan updatedClan = new Clan(clan.name(), clan.leader(), members, clan.hexColor());
        clans.put(clanName, updatedClan);
        playerToClanName.remove(memberUUID);
        save();
    }

    public void changeLeader(String clanName, UUID newLeaderUUID) {
        Clan clan = clans.get(clanName);
        if (clan == null) return;
        if (!clan.members().contains(newLeaderUUID)) return;

        Clan updatedClan = new Clan(clan.name(), newLeaderUUID, clan.members(), clan.hexColor());
        clans.put(clanName, updatedClan);
        save();
    }

    @Nullable
    public Clan getClan(String clanName) {
        clanName = forceFirstCharUppercase(clanName);
        if (clans.containsKey(clanName)) return clans.get(clanName);
        return null;
    }

    @Nullable
    public Clan getPlayerClan(UUID playerUUID) {
        String clanName = playerToClanName.get(playerUUID);
        if (clanName == null) return null;
        return clans.get(clanName);
    }

    public boolean playerInAClan(UUID playerUUID) {
        String clanName = playerToClanName.get(playerUUID);
        return clanName != null;
    }

    public int clanCount() {
        return clans.size();
    }

    public Collection<Clan> getAllClans() {
        return clans.values();
    }

    public boolean load() {
        if (!file.exists()) return false;

        // temporary maps to avoid overwriting current state if something fails catastrophically
        Map<String, Clan> tempClans = new HashMap<>();
        Map<UUID, String> tempPlayerToClanName = new HashMap<>();

        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Clan>>() {}.getType();
            Map<String, Clan> loaded = gson.fromJson(reader, type);

            if (loaded == null) return false;

            for (Map.Entry<String, Clan> entry : loaded.entrySet()) {
                try {
                    Clan readClan = entry.getValue();
                    if (readClan == null) {
                        logger.warn("Encountered null clan entry for key: {}", entry.getKey());
                        continue;
                    }

                    if (readClan.leader() == null) {
                        logger.warn("Clan {} has no leader. Skipping.", readClan.name());
                        continue;
                    }

                    String adjustedClanName = forceFirstCharUppercase(readClan.name());
                    LinkedHashSet<UUID> cleanedMembers = new LinkedHashSet<>();

                    if (readClan.members() != null) {
                        for (UUID memberUUID : readClan.members()) {
                            if (memberUUID == null) {
                                logger.warn("Null UUID found in clan {}. Skipping.", adjustedClanName);
                                continue;
                            }

                            if (tempPlayerToClanName.containsKey(memberUUID)) {
                                String existingClanName = tempPlayerToClanName.get(memberUUID);
                                Clan existingClan = tempClans.get(existingClanName);

                                boolean isLeaderInExisting = existingClan != null && memberUUID.equals(existingClan.leader());
                                boolean isLeaderInCurrent = memberUUID.equals(readClan.leader());

                                if (isLeaderInExisting) {
                                    if (isLeaderInCurrent) {
                                        logger.warn(
                                                "Skipping clan {} because its leader {} is already leading another clan",
                                                adjustedClanName, memberUUID
                                        );
                                        readClan = null;
                                        break;
                                    }
                                    logger.warn(
                                            "UUID {} is a leader in clan {}. Respecting first occurrence.",
                                            memberUUID, existingClanName
                                    );
                                    continue;
                                }

                                if (isLeaderInCurrent) {
                                    logger.warn(
                                            "UUID {} is the leader of clan {}. Overriding previous clan {}.",
                                            memberUUID, adjustedClanName, existingClanName
                                    );
                                } else {
                                    logger.warn(
                                            "UUID {} appears in multiple clans. Respecting first occurrence.",
                                            memberUUID
                                    );
                                    continue;
                                }
                            }

                            tempPlayerToClanName.put(memberUUID, adjustedClanName);
                            cleanedMembers.add(memberUUID);
                        }
                    }

                    if (readClan != null) {
                        // ensure the leader is always in the member list
                        if (!cleanedMembers.contains(readClan.leader())) {
                            cleanedMembers.add(readClan.leader());
                            logger.info(
                                    "Added leader {} to members of clan {}",
                                    readClan.leader(), adjustedClanName
                            );
                        }

                        Clan clan = new Clan(
                                adjustedClanName, readClan.leader(), cleanedMembers, readClan.hexColor()
                        );
                        tempClans.put(adjustedClanName, clan);
                    }

                } catch (Exception e) {
                    logger.error("Error processing clan {}. Skipping this clan.", entry.getKey(), e);
                }
            }

            // Swap in temp maps only if parsing succeeded
            clans.clear();
            clans.putAll(tempClans);
            playerToClanName.clear();
            playerToClanName.putAll(tempPlayerToClanName);

            ENABLE_SAVES = true;
            return true;

        } catch (IOException e) {
            logger.error("Failed to read clans.json", e);
            return false;
        } catch (JsonParseException e) {
            logger.error(
                    "Failed to parse clans.json. Was it manually edited incorrectly? Disabling writes to clans.json until this is resolved.",
                    e
            );
            ENABLE_SAVES = false;
            return false;
        }
    }

    public void save() {
        if (!ENABLE_SAVES) {
            logger.warn("Attempted write to clans.json was prevented due to broken clans.json file.");
            return;
        }
        Path temp = null;
        try {
            Path target = file.toPath();
            Path parent = target.getParent() != null ? target.getParent() : Paths.get(".");
            Files.createDirectories(parent);

            temp = Files.createTempFile(parent, "clans", ".tmp");

            try (FileOutputStream fos = new FileOutputStream(temp.toFile());
                 FileChannel channel = fos.getChannel();
                 Writer writer = new BufferedWriter(
                         new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {

                gson.toJson(clans, writer);
                writer.flush();
                channel.force(true);
            }
            try { // attempt atomic move
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                logger.warn("Atomic move not supported on this filesystem, falling back to non-atomic move");
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.error("Failed to save clans", e);
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {}
            }
        }
    }

    private String forceFirstCharUppercase(String str) {
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
