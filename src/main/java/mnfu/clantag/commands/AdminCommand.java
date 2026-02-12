package mnfu.clantag.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import mnfu.clantag.ClanUuidCacheBuilder;
import mnfu.clantag.MojangApi;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import net.minecraft.text.Text;

import java.util.UUID;

import static mnfu.clantag.commands.CommandUtils.getUuid;

public class AdminCommand {
    private final ClanManager clanManager;

    public AdminCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("admin")
                .requires(CommandManager.requirePermissionLevel(CommandManager.OWNERS_CHECK))

                // add <playerName> <clanName>
                .then(CommandManager.literal("add")
                        .then(CommandManager.argument("playerName", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String n : context.getSource().getServer().getPlayerNames()) {
                                        builder.suggest(n);
                                    }
                                    return builder.buildFuture();
                                })
                                .then(CommandManager.argument("clanName", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (Clan c : clanManager.getAllClans()) {
                                                builder.suggest(c.name());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(this::executeAdd)
                                )
                        )
                )

                // remove <playerName> <clanName>
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("playerName", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String n : context.getSource().getServer().getPlayerNames()) {
                                        builder.suggest(n);
                                    }
                                    return builder.buildFuture();
                                })
                                .then(CommandManager.argument("clanName", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (Clan c : clanManager.getAllClans()) {
                                                builder.suggest(c.name());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(this::executeRemove)
                                )
                        )
                )

                // delete <clanName> (confirm)
                .then(CommandManager.literal("delete")
                        .then(CommandManager.argument("clanName", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (Clan c : clanManager.getAllClans()) {
                                        builder.suggest(c.name());
                                    }
                                    return builder.buildFuture();
                                })
                                .then(CommandManager.literal("confirm")
                                        .executes(context -> executeDelete(context, true))
                                )
                                .executes(context -> executeDelete(context, false))
                        )
                )

                // reload
                .then(CommandManager.literal("reload")
                        .executes(context -> {
                            boolean reloaded = clanManager.load();
                            if (reloaded) {
                                context.getSource().sendFeedback(() -> Text.literal("Reloaded clans.json!"), true);
                            } else {
                                context.getSource().sendFeedback(() -> Text.literal("Failed to load or partially loaded clans.json! Manually inputted malformed data?"), true);
                            }
                            return 1;
                        })
                )

                // cache clear
                .then(CommandManager.literal("cache")
                        .then(CommandManager.literal("clear")
                                .executes(context -> {
                                    MojangApi.clearCache();
                                    ClanUuidCacheBuilder builder = ClanUuidCacheBuilder.getInstance();
                                    if (builder != null) {
                                        builder.shutdown();
                                    }
                                    context.getSource().sendFeedback(
                                            () -> Text.literal("Offline player cache cleared!"), true
                                    );
                                    return 1;
                                })
                        )
                        .then(CommandManager.literal("build")
                                .executes(context -> {
                                    ClanUuidCacheBuilder builder = ClanUuidCacheBuilder.getInstance();
                                    if (builder == null) {
                                        context.getSource().sendError(Text.literal(
                                                "Clan UUID cache builder has not been initialized yet!"
                                        ));
                                        return 0;
                                    }
                                    builder.start();
                                    context.getSource().sendFeedback(
                                            () -> Text.literal("Started building the clan UUID cache!"),
                                            true
                                    );
                                    return 1;
                                })
                        )
                )

                // default response
                .executes(context -> {
                    context.getSource().sendError(Text.literal("Valid subcommands: add, remove, transfer, disband"));
                    return 0;
                });

    }

    private int executeAdd(CommandContext<ServerCommandSource> context) {
        String clanName = StringArgumentType.getString(context, "clanName");
        String playerName = StringArgumentType.getString(context, "playerName");

        if (clanName == null || playerName == null) {
            context.getSource().sendError(Text.literal("Usage: /clan admin add <clanName> <playerName>"));
            return 0;
        }

        Clan clan = clanManager.getClan(clanName);
        if (clan == null) {
            context.getSource().sendError(Text.literal("Clan not found!"));
            return 0;
        }

        getUuid(context, playerName).thenAccept(optPlayerName -> context.getSource().getServer().execute(() -> {
            if (optPlayerName.isEmpty()) {
                context.getSource().sendError(Text.literal("Player not found!"));
                return;
            }
            UUID playerUuid = optPlayerName.get();

            if (clanManager.playerInAClan(playerUuid)) {
                context.getSource().sendError(Text.literal("Player already in a clan!"));
                return;
            }

            clanManager.addMember(clanName, playerUuid);
            context.getSource().sendFeedback(() -> Text.literal("Added " + playerName + " to clan " + clanName + "!"), true);
        }));
        return 1;
    }

    private int executeRemove(CommandContext<ServerCommandSource> context) {
        String clanName = StringArgumentType.getString(context, "clanName");
        String playerName = StringArgumentType.getString(context, "playerName");

        if (clanName == null || playerName == null) {
            context.getSource().sendError(Text.literal("Usage: /clan admin remove <clanName> <playerName>"));
            return 0;
        }

        Clan clan = clanManager.getClan(clanName);
        if (clan == null) {
            context.getSource().sendError(Text.literal("Clan not found!"));
            return 0;
        }

        getUuid(context, playerName).thenAccept(optPlayerUuid -> context.getSource().getServer().execute(() -> {
            if (optPlayerUuid.isEmpty()) {
                context.getSource().sendError(Text.literal("Player not found!"));
                return;
            }

            UUID playerUuid = optPlayerUuid.get();

            if (!clan.members().contains(playerUuid)) {
                context.getSource().sendError(Text.literal(playerName + " is not in clan " + clanName + "!"));
                return;
            }

            // prevent removing the leader accidentally
            if (playerUuid.equals(clan.leader())) {
                context.getSource().sendError(Text.literal("Cannot remove the leader from their own clan! You may transfer ownership, or delete the clan instead."));
                return;
            }

            clanManager.removeMember(clanName, playerUuid);
            context.getSource().sendFeedback(() -> Text.literal("Removed " + playerName + " from clan " + clanName + "!"), true);
        }));

        return 1;
    }

    private int executeDelete(CommandContext<ServerCommandSource> context, boolean confirm) {
        if (!confirm) {
            context.getSource().sendMessage(Text.literal(
                    "Are you sure you want to do this? This action cannot be undone. If so, run: /clan disband confirm"
            ));
            return 1;
        }
        String clanName = StringArgumentType.getString(context, "clanName");
        if (clanName == null) {
            context.getSource().sendError(Text.literal("Clan not found!"));
            return 0;
        }
        clanManager.deleteClan(clanName);
        context.getSource().sendFeedback(() -> Text.literal("Deleted " + clanName + "!"), true);
        return 1;
    }
}
