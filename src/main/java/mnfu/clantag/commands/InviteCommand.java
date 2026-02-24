package mnfu.clantag.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public class InviteCommand {
    private final ClanManager clanManager;
    private final InviteManager inviteManager;

    public InviteCommand(ClanManager clanManager, InviteManager inviteManager) {
        this.clanManager = clanManager;
        this.inviteManager = inviteManager;
    }

    public LiteralArgumentBuilder<ServerCommandSource> buildInvite() {
        return CommandManager.literal("invite")
                .then(CommandManager.argument("playerName", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            Collection<String> onlinePlayers = context.getSource().getPlayerNames();
                            onlinePlayers.forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(this::executeInvite));
    }

    public LiteralArgumentBuilder<ServerCommandSource> buildAccept() {
        return CommandManager.literal("accept")
                .then(CommandManager.argument("clanName", StringArgumentType.greedyString())
                        .suggests((context, suggestionsBuilder) -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player != null) {
                                Set<String> invites = inviteManager.getInvites(player.getUuid());
                                for (String clanName : invites) {
                                    suggestionsBuilder.suggest(clanName);
                                }
                            }
                            return suggestionsBuilder.buildFuture();
                        })
                        .executes(this::executeAccept));
    }

    public LiteralArgumentBuilder<ServerCommandSource> buildDecline() {
        return CommandManager.literal("decline")
                .then(CommandManager.argument("clanName", StringArgumentType.greedyString())
                        .suggests((context, suggestionsBuilder) -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player != null) {
                                Set<String> invites = inviteManager.getInvites(player.getUuid());
                                for (String clanName : invites) {
                                    suggestionsBuilder.suggest(clanName);
                                }
                            }
                            return suggestionsBuilder.buildFuture();
                        })
                        .executes(this::executeDecline));
    }

    public LiteralArgumentBuilder<ServerCommandSource> buildInvites() {
        return CommandManager.literal("invites")
                .executes(this::executeListInvites);
    }

    private int executeInvite(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor == null) {
            context.getSource().sendError(Text.literal("Only players can send invites!"));
            return 0;
        }

        UUID executorUuid = executor.getUuid();
        Clan executorClan = clanManager.getPlayerClan(executorUuid);

        if (executorClan == null) {
            context.getSource().sendError(Text.literal("You must be in a clan to invite players!"));
            return 0;
        }

        if (!executorClan.leader().equals(executorUuid) && !executorClan.officers().contains(executorUuid)) {
            context.getSource().sendError(Text.literal("You are not the leader or an officer of a clan!"));
            return 0;
        }

        String targetName = StringArgumentType.getString(context, "playerName");

        CommandUtils.getUuid(context, targetName).thenAccept(optUuid -> context.getSource().getServer().execute(() -> {
            if (optUuid.isEmpty()) {
                context.getSource().sendError(Text.literal("Player not found!"));
                return;
            }

            UUID targetUuid = optUuid.get();

            if (targetUuid.equals(executor.getUuid())) {
                context.getSource().sendError(Text.literal("You cannot invite yourself!"));
                return;
            }

            Clan targetClan = clanManager.getPlayerClan(targetUuid);
            if (targetClan != null) {
                context.getSource().sendError(Text.literal(targetName + " is already in a clan!"));
                return;
            }

            if (inviteManager.hasInvite(targetUuid, executorClan.name())) {
                context.getSource().sendError(Text.literal(targetName + " already has a pending invite to " + executorClan.name() + "!"));
                return;
            }

            inviteManager.addInvite(targetUuid, executorClan.name());

            context.getSource().sendMessage(Text.literal("Invited " + targetName + " to " + executorClan.name() + "!"));

            ServerPlayerEntity targetPlayer = context.getSource().getServer()
                    .getPlayerManager()
                    .getPlayer(targetUuid);

            if (targetPlayer != null) {
                MutableText inviteMessage = Text.literal("You've been invited to join ")
                        .formatted(Formatting.YELLOW);

                inviteMessage.append(Text.literal(executorClan.name())
                        .setStyle(Style.EMPTY.withColor(TextColor.parse(executorClan.hexColor()).getOrThrow())));

                inviteMessage.append(Text.literal("! ").formatted(Formatting.YELLOW));

                MutableText acceptButton = Text.literal("[Accept]")
                        .styled(style -> style
                                .withColor(Formatting.GREEN)
                                .withClickEvent(new ClickEvent.RunCommand("/clan accept " + executorClan.name()))
                                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to accept"))));

                inviteMessage.append(acceptButton);
                inviteMessage.append(Text.literal(" "));

                MutableText declineButton = Text.literal("[Decline]")
                        .styled(style -> style
                                .withColor(Formatting.RED)
                                .withClickEvent(new ClickEvent.RunCommand("/clan decline " + executorClan.name()))
                                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to decline"))));

                inviteMessage.append(declineButton);

                targetPlayer.sendMessage(inviteMessage, false);
            }
        }));

        return 1;
    }

    private int executeAccept(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor == null) {
            context.getSource().sendError(Text.literal("Only players can accept invites!"));
            return 0;
        }

        String clanName = StringArgumentType.getString(context, "clanName");
        UUID executorUuid = executor.getUuid();

        Clan currentClan = clanManager.getPlayerClan(executorUuid);
        if (currentClan != null) {
            context.getSource().sendError(Text.literal("You are already in a clan! Leave your current clan first."));
            return 0;
        }

        if (!inviteManager.hasInvite(executorUuid, clanName)) {
            context.getSource().sendError(Text.literal("You don't have an invite to " + clanName + "!"));
            return 0;
        }

        Clan clan = clanManager.getClan(clanName);
        if (clan == null) {
            context.getSource().sendError(Text.literal("That clan no longer exists!"));
            inviteManager.removeInvite(executorUuid, clanName);
            return 0;
        }

        clanManager.addMember(clanName, executorUuid);

        inviteManager.clearInvitesForPlayer(executorUuid);

        MutableText message = Text.literal("You've joined ")
                .formatted(Formatting.GREEN);
        message.append(Text.literal(clan.name())
                .setStyle(Style.EMPTY.withColor(TextColor.parse(clan.hexColor()).getOrThrow())));
        message.append(Text.literal("!").formatted(Formatting.GREEN));

        context.getSource().sendMessage(message);

        return 1;
    }

    private int executeDecline(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor == null) {
            context.getSource().sendError(Text.literal("Only players can decline invites!"));
            return 0;
        }

        String clanName = StringArgumentType.getString(context, "clanName");
        UUID executorUuid = executor.getUuid();

        if (!inviteManager.hasInvite(executorUuid, clanName)) {
            context.getSource().sendError(Text.literal("You don't have an invite to " + clanName + "!"));
            return 0;
        }

        inviteManager.removeInvite(executorUuid, clanName);

        context.getSource().sendMessage(Text.literal("Declined invite to " + clanName + ".").formatted(Formatting.GRAY));

        return 1;
    }

    private int executeListInvites(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor == null) {
            context.getSource().sendError(Text.literal("Only players can view invites!"));
            return 0;
        }

        Set<String> invites = inviteManager.getInvites(executor.getUuid());

        if (invites.isEmpty()) {
            context.getSource().sendMessage(Text.literal("You have no pending clan invites.").formatted(Formatting.GRAY));
            return 0;
        }

        MutableText message = Text.literal("Pending clan invites:").formatted(Formatting.YELLOW);
        message.append("\n");

        for (String clanName : invites) {
            Clan clan = clanManager.getClan(clanName);
            
            if (clan != null) {
                message.append(Text.literal("• "));
                message.append(Text.literal(clan.name())
                        .setStyle(Style.EMPTY.withColor(TextColor.parse(clan.hexColor()).getOrThrow())));
                
                message.append(Text.literal(" "));

                MutableText acceptButton = Text.literal("[Accept]")
                        .styled(style -> style
                                .withColor(Formatting.GREEN)
                                .withClickEvent(new ClickEvent.RunCommand("/clan accept " + clanName))
                                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to accept"))));

                message.append(acceptButton);
                message.append(Text.literal(" "));

                MutableText declineButton = Text.literal("[Decline]")
                        .styled(style -> style
                                .withColor(Formatting.RED)
                                .withClickEvent(new ClickEvent.RunCommand("/clan decline " + clanName))
                                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to decline"))));

                message.append(declineButton);
                message.append("\n");
            }
        }

        context.getSource().sendMessage(message);
        return 1;
    }
}
