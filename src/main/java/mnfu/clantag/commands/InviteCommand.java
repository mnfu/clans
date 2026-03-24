package mnfu.clantag.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import net.minecraft.server.players.PlayerList;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;

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

    public LiteralArgumentBuilder<CommandSourceStack> buildInvite() {
        return Commands.literal("invite")
                .then(Commands.argument("playerName", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            Collection<String> onlinePlayers = context.getSource().getOnlinePlayerNames();
                            onlinePlayers.forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(this::executeInvite));
    }

    public LiteralArgumentBuilder<CommandSourceStack> buildAccept() {
        return Commands.literal("accept")
                .then(Commands.argument("clanName", StringArgumentType.greedyString())
                        .suggests((context, suggestionsBuilder) -> {
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player != null) {
                                Set<String> invites = inviteManager.getInvites(player.getUUID());
                                for (String clanName : invites) {
                                    suggestionsBuilder.suggest(clanName);
                                }
                            }
                            return suggestionsBuilder.buildFuture();
                        })
                        .executes(this::executeAccept));
    }

    public LiteralArgumentBuilder<CommandSourceStack> buildDecline() {
        return Commands.literal("decline")
                .then(Commands.argument("clanName", StringArgumentType.greedyString())
                        .suggests((context, suggestionsBuilder) -> {
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player != null) {
                                Set<String> invites = inviteManager.getInvites(player.getUUID());
                                for (String clanName : invites) {
                                    suggestionsBuilder.suggest(clanName);
                                }
                            }
                            return suggestionsBuilder.buildFuture();
                        })
                        .executes(this::executeDecline));
    }

    public LiteralArgumentBuilder<CommandSourceStack> buildInvites() {
        return Commands.literal("invites")
                .executes(this::executeListInvites);
    }

    private int executeInvite(CommandContext<CommandSourceStack> context) {
        ServerPlayer executor = context.getSource().getPlayer();
        if (executor == null) {
            context.getSource().sendFailure(Component.literal("Only players can send invites!"));
            return 0;
        }

        UUID executorUuid = executor.getUUID();
        Clan executorClan = clanManager.getPlayerClan(executorUuid);

        if (executorClan == null) {
            context.getSource().sendFailure(Component.literal("You must be in a clan to invite players!"));
            return 0;
        }

        if (!executorClan.leader().equals(executorUuid) && !executorClan.officers().contains(executorUuid)) {
            context.getSource().sendFailure(Component.literal("You are not the leader or an officer of a clan!"));
            return 0;
        }

        String targetName = StringArgumentType.getString(context, "playerName");

        CommandUtils.getUuid(context, targetName).thenAccept(optUuid -> context.getSource().getServer().execute(() -> {
            if (optUuid.isEmpty()) {
                context.getSource().sendFailure(Component.literal("Player not found!"));
                return;
            }

            UUID targetUuid = optUuid.get();

            if (targetUuid.equals(executor.getUUID())) {
                context.getSource().sendFailure(Component.literal("You cannot invite yourself!"));
                return;
            }

            Clan targetClan = clanManager.getPlayerClan(targetUuid);
            if (targetClan != null) {
                context.getSource().sendFailure(Component.literal(targetName + " is already in a clan!"));
                return;
            }

            if (inviteManager.hasInvite(targetUuid, executorClan.name())) {
                context.getSource().sendFailure(Component.literal(targetName + " already has a pending invite to " + executorClan.name() + "!"));
                return;
            }

            inviteManager.addInvite(targetUuid, executorClan.name());

            context.getSource().sendSystemMessage(Component.literal("Invited " + targetName + " to " + executorClan.name() + "!"));

            ServerPlayer targetPlayer = context.getSource().getServer()
                    .getPlayerList()
                    .getPlayer(targetUuid);

            if (targetPlayer != null) {
                MutableComponent inviteMessage = Component.literal("You've been invited to join ")
                        .withStyle(ChatFormatting.YELLOW);

                inviteMessage.append(Component.literal(executorClan.name())
                        .setStyle(Style.EMPTY.withColor(TextColor.parseColor(executorClan.hexColor()).getOrThrow())));

                inviteMessage.append(Component.literal("! ").withStyle(ChatFormatting.YELLOW));

                MutableComponent acceptButton = Component.literal("[Accept]")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.GREEN)
                                .withClickEvent(new ClickEvent.RunCommand("/clan accept " + executorClan.name()))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to accept"))));

                inviteMessage.append(acceptButton);
                inviteMessage.append(Component.literal(" "));

                MutableComponent declineButton = Component.literal("[Decline]")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.RED)
                                .withClickEvent(new ClickEvent.RunCommand("/clan decline " + executorClan.name()))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to decline"))));

                inviteMessage.append(declineButton);

                targetPlayer.displayClientMessage(inviteMessage, false);
            }
        }));

        return 1;
    }

    private int executeAccept(CommandContext<CommandSourceStack> context) {
        ServerPlayer executor = context.getSource().getPlayer();
        if (executor == null) {
            context.getSource().sendFailure(Component.literal("Only players can accept invites!"));
            return 0;
        }

        String clanName = StringArgumentType.getString(context, "clanName");
        UUID executorUuid = executor.getUUID();

        Clan currentClan = clanManager.getPlayerClan(executorUuid);
        if (currentClan != null) {
            context.getSource().sendFailure(Component.literal("You are already in a clan! Leave your current clan first."));
            return 0;
        }

        Clan clan = clanManager.getClan(clanName);
        if (clan == null) {
            context.getSource().sendFailure(Component.literal("That clan no longer exists!"));
            inviteManager.removeInvite(executorUuid, clanName);
            return 0;
        }

        if (!inviteManager.hasInvite(executorUuid, clan.name())) {
            context.getSource().sendFailure(Component.literal("You don't have an invite to " + clanName + "!"));
            return 0;
        }

        clanManager.addMember(clan.name(), executorUuid);

        PlayerList pm = context.getSource().getServer().getPlayerList();
        for (UUID member : clan.members()) {
            ServerPlayer player = pm.getPlayer(member);
            if (player != null) {
                player.sendSystemMessage(Component.literal(executor.getName().getString() + " joined the clan!"));
            }
        }
        inviteManager.clearInvitesForPlayer(executorUuid);

        MutableComponent message = Component.literal("You've joined ")
                .withStyle(ChatFormatting.GREEN);
        message.append(Component.literal(clan.name())
                .setStyle(Style.EMPTY.withColor(TextColor.parseColor(clan.hexColor()).getOrThrow())));
        message.append(Component.literal("!").withStyle(ChatFormatting.GREEN));

        context.getSource().sendSystemMessage(message);

        return 1;
    }

    private int executeDecline(CommandContext<CommandSourceStack> context) {
        ServerPlayer executor = context.getSource().getPlayer();
        if (executor == null) {
            context.getSource().sendFailure(Component.literal("Only players can decline invites!"));
            return 0;
        }

        String clanName = StringArgumentType.getString(context, "clanName");
        UUID executorUuid = executor.getUUID();

        Clan clan = clanManager.getClan(clanName);
        if (clan == null) {
            // clean up stale invite even if the clan no longer exists
            inviteManager.removeInvite(executorUuid, clanName);
            context.getSource().sendFailure(Component.literal("That clan no longer exists!"));
            return 0;
        }

        if (!inviteManager.hasInvite(executorUuid, clan.name())) {
            context.getSource().sendFailure(Component.literal("You don't have an invite to " + clanName + "!"));
            return 0;
        }

        inviteManager.removeInvite(executorUuid, clan.name());

        context.getSource().sendSystemMessage(Component.literal("Declined invite to " + clanName + ".").withStyle(ChatFormatting.GRAY));

        return 1;
    }

    private int executeListInvites(CommandContext<CommandSourceStack> context) {
        ServerPlayer executor = context.getSource().getPlayer();
        if (executor == null) {
            context.getSource().sendFailure(Component.literal("Only players can view invites!"));
            return 0;
        }

        Set<String> invites = inviteManager.getInvites(executor.getUUID());

        if (invites.isEmpty()) {
            context.getSource().sendSystemMessage(Component.literal("You have no pending clan invites.").withStyle(ChatFormatting.GRAY));
            return 0;
        }

        MutableComponent message = Component.literal("Pending clan invites:").withStyle(ChatFormatting.YELLOW);
        message.append("\n");

        for (String clanName : invites) {
            Clan clan = clanManager.getClan(clanName);
            
            if (clan != null) {
                message.append(Component.literal("• "));
                message.append(Component.literal(clan.name())
                        .setStyle(Style.EMPTY.withColor(TextColor.parseColor(clan.hexColor()).getOrThrow())));
                
                message.append(Component.literal(" "));

                MutableComponent acceptButton = Component.literal("[Accept]")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.GREEN)
                                .withClickEvent(new ClickEvent.RunCommand("/clan accept " + clanName))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to accept"))));

                message.append(acceptButton);
                message.append(Component.literal(" "));

                MutableComponent declineButton = Component.literal("[Decline]")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.RED)
                                .withClickEvent(new ClickEvent.RunCommand("/clan decline " + clanName))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to decline"))));

                message.append(declineButton);
                message.append("\n");
            }
        }

        context.getSource().sendSystemMessage(message);
        return 1;
    }
}
