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
import java.text.Normalizer;
import java.util.*;

public class ClanManager {
    private final File file;
    private final Gson gson;
    private final Map<String, Clan> clans = new HashMap<>(); // key: canonical clan name
    private final Map<UUID, String> playerToClanName = new HashMap<>(); // key: player uuid
    private final Logger logger;
    private final InviteManager inviteManager;
    private static boolean ENABLE_SAVES = true;
    private final boolean loadedSuccessfully;

    public ClanManager(File file, Logger logger, InviteManager inviteManager) {
        this.logger = logger;
        this.file = file;
        this.inviteManager = inviteManager;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadedSuccessfully = load();
    }

    public boolean loadedSuccessfully() {
        return loadedSuccessfully;
    }

    /**
     * tries to create a clan
     *
     * @return true if clan was created, false if clan was not created
     */
    public boolean createClan(String clanName, UUID leaderUuid) {
        if (!containsAllowedChars(clanName)) return false;
        String canonicalName = canonicalize(clanName);
        if (isABannedName(canonicalName)) return false;
        if (clans.containsKey(canonicalName)) return false;
        LinkedHashSet<UUID> members = new LinkedHashSet<>();
        members.add(leaderUuid);

        Clan clan = new Clan(clanName, leaderUuid, members, "#FFFFFF", true);
        clans.put(canonicalName, clan);
        playerToClanName.put(leaderUuid, clan.name());
        save();

        inviteManager.clearInvitesForPlayer(leaderUuid);
        return true;
    }

    public boolean deleteClan(String clanName) {
        String canonicalName = canonicalize(clanName);
        Clan clan = clans.get(canonicalName);
        if (clan == null) return false;
        for (UUID memberUuid : clan.members()) {
            playerToClanName.remove(memberUuid);
        }
        clans.remove(canonicalName);
        save();
        inviteManager.clearInvitesForClan(clanName);
        return true;
    }

    public void addMember(String clanName, UUID memberUuid) {
        String canonicalName = canonicalize(clanName);
        Clan clan = clans.get(canonicalName);
        if (clan == null) {
            return;
        }
        LinkedHashSet<UUID> members = new LinkedHashSet<>(clan.members());
        if (members.contains(memberUuid)) return;
        members.add(memberUuid);

        Clan updatedClan = new Clan(clan.name(), clan.leader(), members, clan.hexColor(), clan.isClosed());
        clans.put(canonicalName, updatedClan);
        playerToClanName.put(memberUuid, updatedClan.name());
        save();

        inviteManager.clearInvitesForPlayer(memberUuid);
    }

    public void removeMember(String clanName, UUID memberUUID) {
        String canonicalName = canonicalize(clanName);
        Clan clan = clans.get(canonicalName);
        if (clan == null) return;
        LinkedHashSet<UUID> members = new LinkedHashSet<>(clan.members());
        members.remove(memberUUID);

        Clan updatedClan = new Clan(clan.name(), clan.leader(), members, clan.hexColor(), clan.isClosed());
        clans.put(canonicalName, updatedClan);
        playerToClanName.remove(memberUUID);
        save();
    }

    public void changeLeader(String clanName, UUID newLeaderUUID) {
        String canonicalName = canonicalize(clanName);
        Clan clan = clans.get(canonicalName);
        if (clan == null) return;
        if (!clan.members().contains(newLeaderUUID)) return;

        Clan updatedClan = new Clan(clan.name(), newLeaderUUID, clan.members(), clan.hexColor(), clan.isClosed());
        clans.put(canonicalName, updatedClan);
        save();
    }

    /**
     *
     * @param clanName name of the clan to change the color of
     * @param hexColor the hex code of some color
     * @return true if successfully changed color, false if failed
     */
    public boolean changeColor(String clanName, String hexColor) {
        if (hexColor.charAt(0) != '#') hexColor = "#" + hexColor;
        String canonicalName = canonicalize(clanName);
        Clan clan = clans.get(canonicalName);
        if (clan == null) return false;
        Clan updatedClan = new Clan(clan.name(), clan.leader(), clan.members(), hexColor, clan.isClosed());
        clans.put(canonicalName, updatedClan);
        save();
        return true;
    }

    @Nullable
    public Clan getClan(String clanName) {
        return clans.get(canonicalize(clanName));
    }

    @Nullable
    public Clan getPlayerClan(UUID playerUUID) {
        String clanName = playerToClanName.get(playerUUID);
        if (clanName == null) return null;
        return getClan(clanName);
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

                    String clanName = readClan.name();
                    UUID leaderUuid = readClan.leader();
                    LinkedHashSet<UUID> cleanedMembers = new LinkedHashSet<>();

                    if (readClan.members() != null) {
                        for (UUID memberUUID : readClan.members()) {
                            if (memberUUID == null) {
                                logger.warn("Null UUID found in clan {}. Skipping.", clanName);
                                continue;
                            }

                            if (tempPlayerToClanName.containsKey(memberUUID)) {
                                String existingClanName = tempPlayerToClanName.get(memberUUID);
                                Clan existingClan = tempClans.get(existingClanName);

                                boolean isLeaderInExisting = existingClan != null && memberUUID.equals(existingClan.leader());
                                boolean isLeaderInCurrent = memberUUID.equals(leaderUuid);

                                if (isLeaderInExisting) {
                                    if (isLeaderInCurrent) {
                                        logger.warn(
                                                "Skipping clan {} because its leader {} is already leading another clan",
                                                clanName, memberUUID
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
                                            memberUUID, clanName, existingClanName
                                    );
                                } else {
                                    logger.warn(
                                            "UUID {} appears in multiple clans. Respecting first occurrence.",
                                            memberUUID
                                    );
                                    continue;
                                }
                            }

                            tempPlayerToClanName.put(memberUUID, clanName);
                            cleanedMembers.add(memberUUID);
                        }
                    }

                    if (readClan != null) {
                        // ensure the leader is always in the member list

                        if (!cleanedMembers.contains(leaderUuid)) {
                            cleanedMembers.add(leaderUuid);
                            logger.info(
                                    "Added leader {} to members of clan {}",
                                    leaderUuid, clanName
                            );
                        }

                        Clan clan = new Clan(
                                clanName, leaderUuid, cleanedMembers, readClan.hexColor(), readClan.isClosed()
                        );
                        tempClans.put(canonicalize(clanName), clan);
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

    private static String canonicalize(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD);
        normalized = normalized.replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAllowedChars(String input) {
        if (input == null || input.isBlank()) return false;

        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            boolean isLetter = Character.isAlphabetic(cp);
            boolean isLatin = Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN;
            if (!(isLetter && isLatin)) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }

    private static boolean isABannedName(String canonicalName) {
        //PLACEHOLDER. This will eventually be checking data loaded in from a file.
        return canonicalName.equals("avience");
    }
}
