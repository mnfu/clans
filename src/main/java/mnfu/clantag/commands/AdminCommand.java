package mnfu.clantag.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import mnfu.clantag.MojangApi;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;

import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.UUID;
import java.util.function.Predicate;

import static mnfu.clantag.commands.CommandUtils.getUuid;

public class AdminCommand {
    private final ClanManager clanManager;

    private final String addUsageMessage = "Usage: /clan admin add <playerName> <clanName>";
    private final String removeUsageMessage = "Usage: /clan admin remove <playerName> <clanName>";
    private final String transferUsageMessage = "Usage: /clan admin transfer <playerName> <clanName>";
    private final String renameUsageMessage = "Usage: /clan admin rename <\"clanName\"> <newClanName>";
    private final String deleteUsageMessage = "Usage: /clan admin delete <clanName>";
    private final String cacheUsageMessage = "Usage: /clan admin cache clear";

    public AdminCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("admin")
                // add <playerName> <clanName>
                .then(Commands.literal("add")
                        .requires(Permissions.require("clantag.admin.add", PermissionLevel.ADMINS))
                        .then(Commands.argument("playerName", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String n : context.getSource().getServer().getPlayerNames()) {
                                        builder.suggest(n);
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("clanName", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> {
                                            for (String canonicalName : clanManager.getAllClansCanonicalNames()) {
                                                builder.suggest(canonicalName);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(this::executeAdd)
                                )
                                .executes(context -> {
                                    context.getSource().sendFailure(Component.literal(addUsageMessage));
                                    return 0;
                                })
                        )
                        .executes(context -> {
                            context.getSource().sendFailure(Component.literal(addUsageMessage));
                            return 0;
                        })
                )

                // remove <playerName> <clanName>
                .then(Commands.literal("remove")
                        .requires(Permissions.require("clantag.admin.remove", PermissionLevel.ADMINS))
                        .then(Commands.argument("playerName", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String n : context.getSource().getServer().getPlayerNames()) {
                                        builder.suggest(n);
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("clanName", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> {
                                            for (String canonicalName : clanManager.getAllClansCanonicalNames()) {
                                                builder.suggest(canonicalName);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(this::executeRemove)
                                )
                                .executes(context -> {
                                    context.getSource().sendFailure(Component.literal(removeUsageMessage));
                                    return 0;
                                })
                        )
                        .executes(context -> {
                            context.getSource().sendFailure(Component.literal(removeUsageMessage));
                            return 0;
                        })
                )

                // transfer <playerName> <clanName>
                .then(Commands.literal("transfer")
                        .requires(Permissions.require("clantag.admin.transfer", PermissionLevel.ADMINS))
                        .then(Commands.argument("playerName", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String n : context.getSource().getServer().getPlayerNames()) {
                                        builder.suggest(n);
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("clanName", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> {
                                            for (String canonicalName : clanManager.getAllClansCanonicalNames()) {
                                                builder.suggest(canonicalName);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(this::executeTransfer)
                                )
                                .executes(context -> {
                                    context.getSource().sendFailure(Component.literal(transferUsageMessage));
                                    return 0;
                                })
                        )
                        .executes(context -> {
                            context.getSource().sendFailure(Component.literal(transferUsageMessage));
                            return 0;
                        })
                )

                .then(Commands.literal("rename")
                        .requires(Permissions.require("clantag.admin.rename", PermissionLevel.ADMINS))
                        .then(Commands.argument("clanName", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    for (String canonicalName : clanManager.getAllClansCanonicalNames()) {
                                        builder.suggest(canonicalName);
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("newClanName", StringArgumentType.greedyString())
                                        .executes(this::executeRename)
                                )
                                .executes(context -> {
                                    context.getSource().sendFailure(Component.literal(renameUsageMessage));
                                    return 0;
                                })
                        )
                        .executes(context -> {
                            context.getSource().sendFailure(Component.literal(renameUsageMessage));
                            return 0;
                        })
                )

                // delete <clanName> (confirm)
                .then(Commands.literal("delete")
                        .requires(Permissions.require("clantag.admin.delete", PermissionLevel.ADMINS))
                        .then(Commands.argument("clanName", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    for (String canonicalName : clanManager.getAllClansCanonicalNames()) {
                                        builder.suggest(canonicalName);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(this::executeDelete)
                        )
                        .executes(context -> {
                            context.getSource().sendFailure(Component.literal(deleteUsageMessage));
                            return 0;
                        })
                )

                // reload
                .then(Commands.literal("reload")
                        .requires(Permissions.require("clantag.admin.reload", PermissionLevel.ADMINS))
                        .executes(context -> {
                            boolean reloaded = clanManager.load();
                            if (reloaded) {
                                context.getSource().sendSuccess(() -> Component.literal("Reloaded clans.json!"), true);
                            } else {
                                context.getSource().sendSuccess(() -> Component.literal("Failed to load or partially loaded clans.json! Manually inputted malformed data?"), true);
                            }
                            return 1;
                        })
                )

                // cache clear
                .then(Commands.literal("cache")
                        .requires(Permissions.require("clantag.admin.cache", PermissionLevel.ADMINS))
                        .then(Commands.literal("clear")
                                .executes(context -> {
                                    MojangApi.clearCache();
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("MojangAPI player cache cleared!"), true
                                    );
                                    return 1;
                                })
                        )
                        .executes(context -> {
                            context.getSource().sendFailure(Component.literal(cacheUsageMessage));
                            return 0;
                        })
                )

                // default response
                .executes(context -> {
                    context.getSource().sendFailure(Component.literal("Valid subcommands: add, remove, transfer, rename, delete, cache, reload"));
                    return 0;
                });

    }

    private int executeAdd(CommandContext<CommandSourceStack> context) {
        String clanName = StringArgumentType.getString(context, "clanName");
        String playerName = StringArgumentType.getString(context, "playerName");

        if (clanName == null || playerName == null) return 0;

        Clan clan = clanManager.getClan(clanName);
        if (clan == null) {
            context.getSource().sendFailure(Component.literal("Clan not found!"));
            return 0;
        }

        getUuid(context, playerName).thenAccept(optPlayerName -> context.getSource().getServer().execute(() -> {
            if (optPlayerName.isEmpty()) {
                context.getSource().sendFailure(Component.literal("Player not found!"));
                return;
            }
            UUID playerUuid = optPlayerName.get();

            if (clanManager.playerInAClan(playerUuid)) {
                context.getSource().sendFailure(Component.literal("Player already in a clan!"));
                return;
            }

            clanManager.addMember(clanName, playerUuid);
            context.getSource().sendSuccess(() -> Component.literal("Added " + playerName + " to clan " + clanName + "!"), true);
        }));
        return 1;
    }

    private int executeRemove(CommandContext<CommandSourceStack> context) {
        String clanName = StringArgumentType.getString(context, "clanName");
        String playerName = StringArgumentType.getString(context, "playerName");

        if (clanName == null || playerName == null) return 0;
        Clan clan = clanManager.getClan(clanName);
        if (clan == null) {
            context.getSource().sendFailure(Component.literal("Clan not found!"));
            return 0;
        }

        getUuid(context, playerName).thenAccept(optPlayerUuid -> context.getSource().getServer().execute(() -> {
            if (optPlayerUuid.isEmpty()) {
                context.getSource().sendFailure(Component.literal("Player not found!"));
                return;
            }

            UUID playerUuid = optPlayerUuid.get();

            if (!clan.members().contains(playerUuid)) {
                context.getSource().sendFailure(Component.literal(playerName + " is not in clan " + clanName + "!"));
                return;
            }

            // prevent removing the leader accidentally
            if (playerUuid.equals(clan.leader())) {
                context.getSource().sendFailure(Component.literal("Cannot remove the leader from their own clan! You may transfer ownership, or delete the clan instead."));
                return;
            }

            clanManager.removeMember(clanName, playerUuid);
            context.getSource().sendSuccess(() -> Component.literal("Removed " + playerName + " from clan " + clanName + "!"), true);
        }));

        return 1;
    }

    private int executeTransfer(CommandContext<CommandSourceStack> context) {
        String playerName = StringArgumentType.getString(context, "playerName");
        String clanName = StringArgumentType.getString(context, "clanName");

        if (playerName == null || clanName == null) return 0;
        Clan clan = clanManager.getClan(clanName);
        if (clan == null) {
            context.getSource().sendFailure(Component.literal("Clan not found!"));
            return 0;
        }

        getUuid(context, playerName).thenAccept(optUuid ->
                context.getSource().getServer().execute(() -> {
                    if (optUuid.isEmpty()) {
                        context.getSource().sendFailure(Component.literal("Player not found!"));
                        return;
                    }

                    UUID targetUuid = optUuid.get();

                    if (targetUuid.equals(clan.leader())) {
                        context.getSource().sendFailure(Component.literal(playerName + " is already the leader of " + clanName + "!"));
                        return;
                    }

                    boolean success = clanManager.transferLeader(clanName, targetUuid);
                    if (!success) {
                        context.getSource().sendFailure(Component.literal(playerName + " is not in " + clanName + "!"));
                        return;
                    }

                    context.getSource().sendSuccess(() -> Component.literal(
                            "Successfully transferred leadership of " + clanName + " to " + playerName + "!"), true);
                })
        );

        return 1;
    }

    private int executeRename(CommandContext<CommandSourceStack> context) {
        String oldClanName = StringArgumentType.getString(context, "clanName");
        String newClanName = StringArgumentType.getString(context, "newClanName");

        if (oldClanName == null || newClanName == null) return 0;
        Clan oldClan = clanManager.getClan(oldClanName);
        if (oldClan == null) {
            context.getSource().sendFailure(Component.literal("Clan not found!"));
            return 0;
        }
        if (newClanName.contains(" ")) {
            context.getSource().sendFailure(Component.literal("Clan names must not contain spaces!"));
            return 0;
        }
        if (newClanName.length() < 3 || newClanName.length() > 16) {
            context.getSource().sendSystemMessage(Component.literal("Warning: Proposed new clan name will override length limits!").withStyle(ChatFormatting.YELLOW));
        }

        boolean clanRenamed = clanManager.changeName(oldClanName, newClanName);
        if (clanRenamed) {
            context.getSource().sendSuccess(() -> Component.literal("Successfully renamed " + oldClanName + " to " + newClanName), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Clan " + newClanName + " already exists, or " + newClanName + " isn't an allowed name!"));
            return 0;
        }
    }

    private int executeDelete(CommandContext<CommandSourceStack> context) {
        String clanName = StringArgumentType.getString(context, "clanName");
        if (clanName == null) {
            context.getSource().sendFailure(Component.literal("Clan not found!"));
            return 0;
        }
        boolean successful = clanManager.deleteClan(clanName);
        if (successful) {
            context.getSource().sendSuccess(() -> Component.literal("Deleted clan " + clanName + "!"), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Clan not found!"));
            return 0;
        }
    }
}
