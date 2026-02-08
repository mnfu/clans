package mnfu.clantag;

import com.mojang.brigadier.arguments.StringArgumentType;
import mnfu.clantag.commands.InfoCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
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

        // cache players when they join, reducing any offline player lookups
        ServerPlayConnectionEvents.JOIN.register(((serverPlayNetworkHandler, packetSender, minecraftServer) -> {
            ServerPlayerEntity player = serverPlayNetworkHandler.getPlayer();
            MojangApi.cachePlayer(player);
        }));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            var baseCommand = CommandManager.literal("clan");

            var reloadCommand = CommandManager.literal("reload")
                    .requires(CommandManager.requirePermissionLevel(CommandManager.OWNERS_CHECK)) // only lvl 4 ops / console
                    .executes(context -> {
                        clanManager.load();
                        context.getSource().sendFeedback(() -> Text.literal(FEEDBACK_PREFIX + "Reloaded clans.json!"), true);
                        return 1;
                    });

            var invalidateCacheCommand = CommandManager.literal("cache")
                    .requires(CommandManager.requirePermissionLevel(CommandManager.OWNERS_CHECK)) // only lvl 4 ops / console
                    .then(CommandManager.literal("clear")
                    .executes(context -> {
                        MojangApi.clearCache();
                        context.getSource().sendFeedback(() -> Text.literal(FEEDBACK_PREFIX + "Offline player cache cleared!"), true);
                        return 1;
                    }));

            var infoCommand = new InfoCommand(clanManager).build();

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
                    .then(invalidateCacheCommand)
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
