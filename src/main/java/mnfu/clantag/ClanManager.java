package mnfu.clantag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

public class ClanManager {
    private final File file;
    private final Gson gson;
    private final Map<String, Clan> clans = new HashMap<>(); // key: clan name

    public ClanManager(File file) {
        this.file = file;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
    }

    public void createClan(String clanName, String leader, String hexColor) {
        clanName = forceFirstCharUppercase(clanName);
        if (clans.containsKey(clanName)) {
            System.out.println("Clan already exists!");
            return;
        }
        List<String> members = new ArrayList<>();
        members.add(leader);
        hexColor = hexColor.trim();
        if (!hexColor.startsWith("#")) {
            hexColor = "#" + hexColor;
        }
        Clan clan = new Clan(clanName, leader, members, hexColor);
        clans.put(clanName, clan);
        save();
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

    public Clan getClan(String clanName) {
        clanName = forceFirstCharUppercase(clanName);
        return clans.get(clanName);
    }

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
        if (!file.exists()) return;
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
            e.printStackTrace();
        }
    }

    // Save clans to JSON
    public void save() {
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(clans, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String forceFirstCharUppercase(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
