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
    public enum JoinPolicy{OPEN, INVITE_ONLY}

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

        Clan clan = new Clan(clanName, leaderUuid, new LinkedHashSet<>(), members, "#FFFFFF", true);
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
        if (clan == null) return;
        LinkedHashSet<UUID> members = new LinkedHashSet<>(clan.members());
        if (members.contains(memberUuid)) return;
        members.add(memberUuid);

        Clan updatedClan = new Clan(clan.name(), clan.leader(), clan.officers(), members, clan.hexColor(), clan.isClosed());
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
        LinkedHashSet<UUID> officers = new LinkedHashSet<>(clan.officers());
        if (!members.contains(memberUUID)) return;
        members.remove(memberUUID);
        officers.remove(memberUUID);

        Clan updatedClan = new Clan(clan.name(), clan.leader(), officers, members, clan.hexColor(), clan.isClosed());
        clans.put(canonicalName, updatedClan);
        playerToClanName.remove(memberUUID);
        save();
    }

    // i think maybe in the future, we may allow custom roles, but this 95% duplicated code is fine for now.
    public void addOfficer(String clanName, UUID memberUuid) {
        String canonicalName = canonicalize(clanName);
        Clan clan = clans.get(canonicalName);
        if (clan == null) return;
        LinkedHashSet<UUID> officers = new LinkedHashSet<>(clan.officers());
        if (officers.contains(memberUuid)) return;
        officers.add(memberUuid);

        Clan updatedClan = new Clan(clan.name(), clan.leader(), officers, clan.members(), clan.hexColor(), clan.isClosed());
        clans.put(canonicalName, updatedClan);
        save();
    }

    public void removeOfficer(String clanName, UUID memberUuid) {
        String canonicalName = canonicalize(clanName);
        Clan clan = clans.get(canonicalName);
        if (clan == null) return;
        LinkedHashSet<UUID> officers = new LinkedHashSet<>(clan.officers());
        if (!officers.contains(memberUuid)) return;
        officers.remove(memberUuid);

        Clan updatedClan = new Clan(clan.name(), clan.leader(), officers, clan.members(), clan.hexColor(), clan.isClosed());
        clans.put(canonicalName, updatedClan);
        save();
    }

    /**
     * tries to change the leader of a clan to a specified player
     *
     * @return false if player is not currently in the clan, true if success
     */
    public boolean transferLeader(String clanName, UUID newLeaderUUID) {
        String canonicalName = canonicalize(clanName);
        Clan clan = clans.get(canonicalName);
        if (clan == null) return false; // could not find clan in memory or whatev
        if (!clan.members().contains(newLeaderUUID)) return false;
        LinkedHashSet<UUID> officers = new LinkedHashSet<>(clan.officers());
        officers.remove(newLeaderUUID);

        Clan updatedClan = new Clan(clan.name(), newLeaderUUID, officers, clan.members(), clan.hexColor(), clan.isClosed());
        clans.put(canonicalName, updatedClan);
        save();
        return true;
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
        Clan updatedClan = new Clan(clan.name(), clan.leader(), clan.officers(), clan.members(), hexColor, clan.isClosed());
        clans.put(canonicalName, updatedClan);
        save();
        return true;
    }

    public void changePolicy(String clanName, JoinPolicy joinPolicy) {
        String canonicalName = canonicalize(clanName);
        Clan clan = clans.get(canonicalName);
        if (clan == null) return;
        if (joinPolicy == null) return;
        boolean newPolicy = joinPolicy != JoinPolicy.OPEN;
        Clan updatedClan = new Clan(clan.name(), clan.leader(), clan.officers(), clan.members(), clan.hexColor(), newPolicy);
        clans.put(canonicalName, updatedClan);
        save();
    }

    public boolean changeName(String clanName, String newClanName) {
        String canonicalName = canonicalize(clanName);
        Clan clan = clans.get(canonicalName);
        if (clan == null) return false;
        if (!containsAllowedChars(newClanName)) return false;
        String canonicalNewClanName = canonicalize(newClanName);
        if (isABannedName(canonicalNewClanName)) return false;
        if (clans.containsKey(canonicalNewClanName)) return false;

        Clan updatedClan = new Clan(newClanName, clan.leader(), clan.officers(), clan.members(), clan.hexColor(), clan.isClosed());
        clans.remove(canonicalName);
        clans.put(canonicalNewClanName, updatedClan);
        for (UUID uuid : clan.members()) {
            playerToClanName.put(uuid, updatedClan.name());
        }
        save();
        inviteManager.clearInvitesForClan(clan.name());
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

        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Clan>>() {}.getType();
            Map<String, Clan> raw = gson.fromJson(reader, type);
            if (raw == null) return false;

            Map<String, Clan> tempClans = new HashMap<>();
            Map<UUID, String> tempPlayerToClan = new HashMap<>();

            for (Map.Entry<String, Clan> entry : raw.entrySet()) {
                try {
                    processRawClan(entry.getValue(), tempClans, tempPlayerToClan);
                } catch (Exception e) {
                    logger.error("Error processing clan {}. Skipping.", entry.getKey(), e);
                }
            }

            clans.clear();
            clans.putAll(tempClans);
            playerToClanName.clear();
            playerToClanName.putAll(tempPlayerToClan);
            ENABLE_SAVES = true;
            return true;

        } catch (IOException | JsonParseException e) {
            ENABLE_SAVES = e instanceof IOException;
            logger.error("Failed to load clans.json", e);
            return false;
        }
    }

    private void processRawClan(Clan raw, Map<String, Clan> tempClans, Map<UUID, String> tempPlayerToClan) {
        if (raw == null || raw.leader() == null) return;

        String clanName = raw.name();
        UUID leaderUuid = raw.leader();
        LinkedHashSet<UUID> cleanedMembers = new LinkedHashSet<>();
        LinkedHashSet<UUID> cleanedOfficers = new LinkedHashSet<>();

        if (raw.members() != null) {
            for (UUID uuid : raw.members()) {
                if (uuid == null) continue;
                ConflictResult result = resolveConflict(uuid, leaderUuid, tempClans, tempPlayerToClan);
                if (result == ConflictResult.SKIP_CLAN) return;
                if (result == ConflictResult.SKIP_MEMBER) continue;
                tempPlayerToClan.put(uuid, clanName);
                cleanedMembers.add(uuid);
            }
        }

        // leader must always be present
        if (!cleanedMembers.contains(leaderUuid)) {
            cleanedMembers.add(leaderUuid);
            tempPlayerToClan.put(leaderUuid, clanName);
        }

        if (raw.officers() != null) {
            for (UUID uuid : raw.officers()) {
                if (uuid == null) continue;
                ConflictResult result = resolveConflict(uuid, leaderUuid, tempClans, tempPlayerToClan);
                if (result == ConflictResult.SKIP_CLAN) return;
                if (result == ConflictResult.SKIP_MEMBER) continue;
                if (!cleanedMembers.contains(uuid)) {
                    cleanedMembers.add(uuid);
                    tempPlayerToClan.put(uuid, clanName);
                }
                cleanedOfficers.add(uuid);
            }
        }

        tempClans.put(canonicalize(clanName),
                new Clan(clanName, leaderUuid, cleanedOfficers, cleanedMembers, raw.hexColor(), raw.isClosed()));
    }

    private enum ConflictResult { OK, SKIP_MEMBER, SKIP_CLAN }

    private ConflictResult resolveConflict(UUID uuid, UUID currentLeader,
                                           Map<String, Clan> tempClans, Map<UUID, String> tempPlayerToClan) {
        String existingClanName = tempPlayerToClan.get(uuid);
        if (existingClanName == null) return ConflictResult.OK;

        Clan existingClan = tempClans.get(existingClanName);
        boolean isLeaderOfExisting = existingClan != null && uuid.equals(existingClan.leader());
        boolean isLeaderOfCurrent = uuid.equals(currentLeader);

        if (isLeaderOfExisting && isLeaderOfCurrent) return ConflictResult.SKIP_CLAN;
        if (isLeaderOfExisting) return ConflictResult.SKIP_MEMBER;
        return ConflictResult.OK; // current clan wins, player will be reassigned
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
