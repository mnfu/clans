package mnfu.clantag;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class ClanTag implements ModInitializer {

    public static final String MOD_ID = "Clans";
    public static final String FEEDBACK_PREFIX = "[Clans] ";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Clans");

        File file = new File("config/clans/clans.json");
        ClanManager clanManager = new ClanManager(file, LOGGER);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            var baseCommand = CommandManager.literal("clan");

            var reloadCommand = CommandManager.literal("reload")
                    .executes(context -> {
                        clanManager.load();
                        context.getSource().sendFeedback(()->Text.literal(FEEDBACK_PREFIX + "clans.json reloaded!"), true);
                        return 1;
                    });

            var infoCommand = CommandManager.literal("info")
                    .executes(context -> {
                        ServerPlayerEntity executor = context.getSource().getPlayer();
                        String executorUUIDString = executor.getUuidAsString();
                        Clan clan = clanManager.getPlayerClan(executorUUIDString);
                        if (clan == null) {
                            executor.sendMessage(Text.literal("Clan not found!"), false);
                            return 0;
                        }

                        StringBuilder membersList = new StringBuilder();
                        for (String id : clan.members()) {
                            ServerPlayerEntity member = context.getSource().getServer()
                                    .getPlayerManager().getPlayer(UUID.fromString(id));
                            String name = "Unknown Player";
                            if (member != null) {
                                name = member.getName().getString();
                            } else {
                                try {
                                    name = MojangApi.getUsernameFromUuid(id);
                                } catch (Exception ignored) {}
                            }
                            membersList.append(name).append(", ");
                        }

                        executor.sendMessage(Text.literal(clan.name()).setStyle(Style.EMPTY.withColor(TextColor.parse(clan.hexColor()).getOrThrow())));

                        ServerPlayerEntity leader = context.getSource().getServer()
                                .getPlayerManager().getPlayer(UUID.fromString(clan.leader()));
                        String leaderName = "Unknown Player";
                        if (leader != null) {
                            leaderName = leader.getName().getString();
                        } else {
                            try {
                                leaderName = MojangApi.getUsernameFromUuid(clan.leader());
                            } catch (Exception ignored) {}
                        }
                        executor.sendMessage(Text.literal("Leader: " + leaderName), false);

                        executor.sendMessage(Text.literal("Members: " +
                                (membersList.length() > 0 ? membersList.substring(0, membersList.length() - 2) : "None")), false);
                        executor.sendMessage(Text.literal("Color: " + clan.hexColor()), false);



                        return 1;
                    })
                    .then(CommandManager.argument("clanName", StringArgumentType.word())
                            .suggests((commandContext, suggestionsBuilder) -> {
                                // a collection is just a more general list object than an arraylist
                                Collection<Clan> clans = clanManager.getAllClans();
                                for (Clan c : clans) {
                                    suggestionsBuilder.suggest(c.name());
                                }
                                return suggestionsBuilder.buildFuture();
                            })
                            .executes(context -> {
                                ServerPlayerEntity executor = context.getSource().getPlayer();
                                String clanName = StringArgumentType.getString(context, "clanName");

                                var clan = clanManager.getClan(clanName);
                                if (clan == null) {
                                    executor.sendMessage(Text.literal("Clan not found!"), false);
                                    return 0;
                                }

                                StringBuilder membersList = new StringBuilder();
                                for (String id : clan.members()) {
                                    ServerPlayerEntity member = context.getSource().getServer()
                                            .getPlayerManager().getPlayer(UUID.fromString(id));
                                    String name = "Unknown Player";
                                    if (member != null) {
                                        name = member.getName().getString();
                                    } else {
                                        try {
                                            name = MojangApi.getUsernameFromUuid(id);
                                        } catch (Exception ignored) {}
                                    }
                                    membersList.append(name).append(", ");
                                }

                                executor.sendMessage(Text.literal(clan.name()).setStyle(Style.EMPTY.withColor(TextColor.parse(clan.hexColor()).getOrThrow())));

                                ServerPlayerEntity leader = context.getSource().getServer()
                                        .getPlayerManager().getPlayer(UUID.fromString(clan.leader()));
                                String leaderName = "Unknown Player";
                                if (leader != null) {
                                    leaderName = leader.getName().getString();
                                } else {
                                    try {
                                        leaderName = MojangApi.getUsernameFromUuid(clan.leader());
                                    } catch (Exception ignored) {}
                                }
                                executor.sendMessage(Text.literal("Leader: " + leaderName), false);

                                executor.sendMessage(Text.literal("Members: " +
                                        (membersList.length() > 0 ? membersList.substring(0, membersList.length() - 2) : "None")), false);
                                executor.sendMessage(Text.literal("Color: " + clan.hexColor()), false);

                                return 1;
                            }));

            var createClanCommand = CommandManager.literal("create")
                    .then(CommandManager.argument("clanName", StringArgumentType.word())
                            .then(CommandManager.argument("hexColor", StringArgumentType.word())
                                    .executes(context -> {
                                        ServerPlayerEntity player = context.getSource().getPlayer();
                                        String clanName = StringArgumentType.getString(context, "clanName");
                                        String hexColor = StringArgumentType.getString(context, "hexColor");

                                        clanManager.createClan(clanName, player.getUuid().toString(), hexColor);
                                        player.sendMessage(Text.literal("Clan " + clanName + " created!"), false);
                                        return 1;
                                    })));

            var addMemberCommand = CommandManager.literal("add")
                    .then(CommandManager.argument("clanName", StringArgumentType.word())
                            .then(CommandManager.argument("playerName", StringArgumentType.word())
                                    .executes(context -> {
                                        ServerPlayerEntity executor = context.getSource().getPlayer();
                                        String clanName = StringArgumentType.getString(context, "clanName");
                                        String targetName = StringArgumentType.getString(context, "playerName");

                                        ServerPlayerEntity target = context.getSource().getServer()
                                                .getPlayerManager().getPlayer(targetName);

                                        if (target == null) {
                                            executor.sendMessage(Text.literal("Player not found or not online!"), false);
                                            return 0;
                                        }

                                        UUID targetUUID = target.getUuid();
                                        clanManager.addMember(clanName, targetUUID.toString());
                                        executor.sendMessage(Text.literal("Added " + targetName + " to clan " + clanName + "!"), false);
                                        return 1;
                                    })));

            var removeMemberCommand = CommandManager.literal("kick")
                        .then(CommandManager.argument("playerName", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayerEntity executor = context.getSource().getPlayer();
                                    String targetName = StringArgumentType.getString(context, "playerName");

                                    // check that this player is actually the leader of a clan, if so, continue execution
                                    String executorUUIDString = executor.getUuidAsString();
                                    Clan playerClan = clanManager.getPlayerClan(executorUUIDString);

                                    if (playerClan != null && playerClan.leader().equals(executorUUIDString)) {
                                        ServerPlayerEntity target = context.getSource().getServer()
                                                .getPlayerManager().getPlayer(targetName);
                                        String targetUUIDString = target.getUuidAsString();

                                        if (target == null) {
                                            executor.sendMessage(Text.literal("Player not found!"), false);
                                            return 0;
                                        } else if (target.getUuid() == executor.getUuid()) {
                                            executor.sendMessage(Text.literal("You may not kick yourself!"), false);
                                            return 0;
                                        }
                                        clanManager.removeMember(playerClan.name(), targetUUIDString);
                                        executor.sendMessage(Text.literal("Kicked " + targetName + " from clan " + playerClan.name() + "!"), false);
                                        return 1;
                                    } else if (playerClan == null){
                                        executor.sendMessage(Text.literal("You are not in a clan!"), false);
                                        return 0;
                                    }
                                    executor.sendMessage(Text.literal("You are not the leader of a clan!"), false);
                                    return 0;
                                }));

            var leaveClanCommand = CommandManager.literal("leave")
                            .executes(context -> {
                                ServerPlayerEntity executor = context.getSource().getPlayer();
                                String executorUUIDString = executor.getUuidAsString();

                                Clan playerClan = clanManager.getPlayerClan(executorUUIDString);

                                // if player is in a clan
                                if (playerClan != null) {

                                    // if player is the leader of the clan
                                    if (playerClan.leader().equals(executorUUIDString)) {
                                        // if the player is the sole member of the clan
                                        if (playerClan.members().size() == 1) {
                                            clanManager.deleteClan(playerClan.name());
                                        }
                                        // possibly create a confirmation message with a click event
                                        executor.sendMessage(Text.literal("You have left, and deleted " + playerClan.name() + "!"), false);
                                        return 1;
                                    }

                                    clanManager.removeMember(playerClan.name(), executorUUIDString);
                                    executor.sendMessage(Text.literal("You have left " + playerClan.name() + "!"), false);
                                    return 1;
                                }
                                executor.sendMessage(Text.literal("You are not currently in a clan!"), false);
                                return 0;
                            });

            dispatcher.register(baseCommand
                    .then(reloadCommand)
                    .then(infoCommand)
                    .then(createClanCommand)
                    .then(addMemberCommand)
                    .then(removeMemberCommand)
                    .then(leaveClanCommand)
                    .executes(context -> {
                        context.getSource().sendFeedback(()->Text.literal(FEEDBACK_PREFIX + "valid subcommands: reload"), false);
                        return 1;
                    })
            );
        });
    }
}
