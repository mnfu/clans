package mnfu.clantag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

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
    private final Logger logger;

    public ClanManager(File file, Logger logger) {
        this.logger = logger;
        this.file = file;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
    }

    /**
     * tries to create a clan
     *
     * @return true if clan was created, false if clan was not created
     */
    public boolean createClan(String clanName, String leader, String hexColor) {
        clanName = forceFirstCharUppercase(clanName);
        if (clans.containsKey(clanName)) {
            return false;
        }
        List<String> members = new ArrayList<>();
        members.add(leader);
        hexColor = "#" + hexColor;

        Clan clan = new Clan(clanName, leader, members, hexColor);
        clans.put(clanName, clan);
        save();
        return true;
    }

    public void deleteClan(String clanName) {
        clanName = forceFirstCharUppercase(clanName);
        if (clans.containsKey(clanName)) {
            clans.remove(clanName);
            save();
        }
    }

    public void addMember(String clanName, String memberId) {
        clanName = forceFirstCharUppercase(clanName);
        Clan clan = clans.get(clanName);
        if (clan == null) {
            System.out.println("Clan not found!");
            return;
        }
        List<String> members = new ArrayList<>(clan.members());
        if (members.contains(memberId)) return;
        members.add(memberId);

        Clan updatedClan = new Clan(clan.name(), clan.leader(), members, clan.hexColor());
        clans.put(clanName, updatedClan);
        save();
    }

    public void removeMember(String clanName, String memberUUID) {
        Clan clan = clans.get(clanName);
        if (clan == null) return;
        List<String> members = new ArrayList<>(clan.members());
        members.remove(memberUUID);

        Clan updatedClan = new Clan(clan.name(), clan.leader(), members, clan.hexColor());
        clans.put(clanName, updatedClan);
        save();
    }

    public void changeLeader(String clanName, String newLeaderUUID) {
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
    public Clan getPlayerClan(String playerUUID) {
        for (Clan clan : clans.values()) {
            if (clan.members().contains(playerUUID)) return clan;
        }
        return null;
    }

    public Collection<Clan> getAllClans() {
        return clans.values();
    }

    public void load() {
        if (!file.exists()) return; // save() handles creating the files & directories. return early if it doesn't exist.
        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Clan>>(){}.getType();
            Map<String, Clan> loaded = gson.fromJson(reader, type);
            clans.clear();
            if (loaded != null) {
                for (Map.Entry<String, Clan> entry : loaded.entrySet()) {
                    Clan readClan = entry.getValue();
                    // enforce the standard of capital letter starting clan names even if file was manually edited
                    String adjustedClanName = forceFirstCharUppercase(readClan.name());
                    Clan clan = new Clan(adjustedClanName, readClan.leader(), readClan.members(), readClan.hexColor());
                    clans.put(adjustedClanName, clan);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load clans", e);
        }
    }

    public void save() {
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
