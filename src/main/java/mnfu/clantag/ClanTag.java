package mnfu.clantag;

import com.mojang.brigadier.arguments.StringArgumentType;
import mnfu.clantag.commands.CommandUtils;
import mnfu.clantag.commands.InfoCommand;
import mnfu.clantag.commands.InviteCommand;
import mnfu.clantag.commands.InviteManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class ClanTag implements ModInitializer {

    public static final String MOD_ID = "Clans";
    public static final String FEEDBACK_PREFIX = "[Clans] ";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Collection<String> colorNames = Formatting.getNames(true, false);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Clans");

        File file = new File("config/clans/clans.json");
        InviteManager inviteManager = new InviteManager();
        ClanManager clanManager = new ClanManager(file, LOGGER, inviteManager);

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

            InviteCommand inviteCommand = new InviteCommand(clanManager, inviteManager);
            var inviteSubcommand = inviteCommand.buildInvite();
            var acceptCommand = inviteCommand.buildAccept();
            var declineCommand = inviteCommand.buildDecline();
            var invitesCommand = inviteCommand.buildInvites();

            var createClanCommand = CommandManager.literal("create")
                    .then(CommandManager.argument("clanName", StringArgumentType.word())
                            .then(CommandManager.argument("hexColor", StringArgumentType.word())
                                    .suggests((commandContext, suggestionsBuilder) -> {
                                        for (String c : colorNames) {
                                            suggestionsBuilder.suggest(c);
                                        }
                                        return suggestionsBuilder.buildFuture();
                                    })
                                    .executes(context -> {
                                        ServerPlayerEntity executor = context.getSource().getPlayer();
                                        if (executor == null) {
                                            context.getSource().sendError(Text.literal("Only players can create clans!"));
                                            return 0;
                                        }
                                        String clanName = StringArgumentType.getString(context, "clanName");
                                        String hexColor = StringArgumentType.getString(context, "hexColor");

                                        Formatting formatting = Formatting.byName(hexColor);
                                        if (formatting != null && formatting.isColor()) { // this complaint is because it could be a formatting code, but we know it's a color, so it has a color.
                                            hexColor = Integer.toHexString(formatting.getColorValue());
                                        }

                                        if (!hexColor.matches("(?i)^[0-9a-f]{1,6}$")) {
                                            hexColor = "FFFFFF";
                                        }

                                        boolean clanCreated = clanManager.createClan(clanName, executor.getUuid().toString(), hexColor);
                                        if (clanCreated) {
                                            executor.sendMessage(Text.literal("Clan " + clanName + " created!"), false);
                                        } else {
                                            context.getSource().sendError(Text.literal("Clan " + clanName + " already exists!"));
                                        }

                                        return 1;
                                    })));

            var addMemberCommand = CommandManager.literal("add")
                    .then(CommandManager.argument("clanName", StringArgumentType.word())
                            .then(CommandManager.argument("playerName", StringArgumentType.word())
                                    .executes(context -> {
                                        String clanName = StringArgumentType.getString(context, "clanName");
                                        String targetName = StringArgumentType.getString(context, "playerName");

                                        // async UUID lookup
                                        CommandUtils.getUuid(context, targetName).thenAccept(optUuid -> context.getSource().getServer().execute(() -> {
                                            if (optUuid.isEmpty()) {
                                                context.getSource().sendError(Text.literal("Player not found!"));
                                                return;
                                            }
                                            clanManager.addMember(clanName, optUuid.get().toString());
                                            context.getSource().sendMessage(Text.literal("Added " + targetName + " to clan " + clanName + "!"));
                                        }));
                                        return 1;
                                    })));

            var removeMemberCommand = CommandManager.literal("kick")
                        .then(CommandManager.argument("playerName", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayerEntity executor = context.getSource().getPlayer();
                                    if (executor == null) {
                                        context.getSource().sendError(Text.literal("Only players can kick people from clans!"));
                                        return 0;
                                    }
                                    String targetName = StringArgumentType.getString(context, "playerName");

                                    String executorUUIDString = executor.getUuidAsString();
                                    Clan playerClan = clanManager.getPlayerClan(executorUUIDString);

                                    if (playerClan == null) {
                                        context.getSource().sendError(Text.literal("You are not in a clan!"));
                                        return 0;
                                    }

                                    if (!playerClan.leader().equals(executorUUIDString)) {
                                        context.getSource().sendError(Text.literal("You are not the leader of a clan!"));
                                        return 0;
                                    }

                                    // async UUID lookup
                                    CommandUtils.getUuid(context, targetName).thenAccept(optUuid -> context.getSource().getServer().execute(() -> { // back on main thread
                                        if (optUuid.isEmpty()) {
                                            context.getSource().sendError(Text.literal("Player not found!"));
                                            return;
                                        }

                                        UUID targetUuid = optUuid.get();
                                        if (targetUuid.equals(executor.getUuid())) {
                                            context.getSource().sendMessage(Text.literal("You may not kick yourself!"));
                                            return;
                                        }

                                        clanManager.removeMember(playerClan.name(), targetUuid.toString());
                                        context.getSource().sendMessage(Text.literal("Kicked " + targetName + " from clan " + playerClan.name() + "!"));
                                    }));

                                    return 1;
                                }));

            var leaveClanCommand = CommandManager.literal("leave")
                            .executes(context -> {
                                ServerPlayerEntity executor = context.getSource().getPlayer();
                                if (executor == null) {
                                    context.getSource().sendError(Text.literal("Only players can leave clans!"));
                                    return 0;
                                }
                                String executorUUIDString = executor.getUuidAsString();

                                Clan playerClan = clanManager.getPlayerClan(executorUUIDString);

                                // if player is in a clan
                                if (playerClan != null) {

                                    // if player is the leader of the clan
                                    if (playerClan.leader().equals(executorUUIDString)) {
                                        // if the player is the sole member of the clan
                                        if (playerClan.members().size() == 1) {
                                            clanManager.deleteClan(playerClan.name());
                                            context.getSource().sendMessage(Text.literal("You have left, and deleted " + playerClan.name() + "!"));
                                            return 1;
                                        }
                                        context.getSource().sendError(Text.literal("You must transfer ownership before leaving or delete the clan!"));
                                        return 0;
                                    }

                                    clanManager.removeMember(playerClan.name(), executorUUIDString);
                                    context.getSource().sendMessage(Text.literal("You have left " + playerClan.name() + "!"));
                                    return 1;
                                }
                                context.getSource().sendError(Text.literal("You are not in a clan!"));
                                return 0;
                            });

            dispatcher.register(baseCommand
                    .then(reloadCommand)
                    .then(invalidateCacheCommand)
                    .then(infoCommand)
                    .then(inviteSubcommand)
                    .then(acceptCommand)
                    .then(declineCommand)
                    .then(invitesCommand)
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
