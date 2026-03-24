package mnfu.clantag.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static mnfu.clantag.commands.CommandUtils.getPlayerName;
import static mnfu.clantag.commands.CommandUtils.getUuid;

public class InfoCommand {
    private final ClanManager clanManager;

    public InfoCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("info")
                .executes(this::executeForSelf)
                .then(Commands.literal("name")
                        .then(Commands.argument("clanName", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    for (String canonicalName : clanManager.getAllClansCanonicalNames()) {
                                        builder.suggest(canonicalName);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(this::executeForClanName)
                        )
                )
                .then(Commands.literal("player")
                        .then(Commands.argument("playerName", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    Collection<String> onlinePlayers = context.getSource().getOnlinePlayerNames();
                                    onlinePlayers.forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(this::executeForPlayer)
                        )
                );
    }

    private int executeForSelf(CommandContext<CommandSourceStack> context) {
        ServerPlayer executor = context.getSource().getPlayer();
        if (executor == null) { // not a player if null, assume console
            context.getSource().sendFailure(Component.literal("You are not a player, so you must specify a clan name."));
            return 0;
        }

        Clan clan = clanManager.getPlayerClan(executor.getUUID());

        if (clan == null) {
            context.getSource().sendFailure(Component.literal("Clan not found!"));
            return 0;
        }

        displayClanInfo(context, clan);
        return 1;
    }

    private int executeForClanName(CommandContext<CommandSourceStack> context) {
        String clanName = StringArgumentType.getString(context, "clanName");

        Clan clan = clanManager.getClan(clanName);
        if (clan == null) {
            context.getSource().sendFailure(Component.literal("Clan not found!"));
            return 0;
        }

        displayClanInfo(context, clan);
        return 1;
    }

    private int executeForPlayer(CommandContext<CommandSourceStack> context) {
        String playerName = StringArgumentType.getString(context, "playerName");

        getUuid(context, playerName).thenAccept(optUuid -> {
            UUID playerUuid = optUuid.orElse(null);
            if (playerUuid == null) {
                context.getSource().sendFailure(Component.literal("Player not found!"));
                return;
            }

            Clan clan = clanManager.getPlayerClan(playerUuid);
            if (clan == null) {
                context.getSource().sendFailure(Component.literal("This player is not in a clan."));
                return;
            }

            displayClanInfo(context, clan);
        });

        return 1;
    }

    private void displayClanInfo(CommandContext<CommandSourceStack> context, Clan clan) {
        MutableComponent message = Component.empty();
        TextColor clanColor = TextColor.parseColor(clan.hexColor()).getOrThrow();

        message.append(Component.literal(clan.name())
                .setStyle(Style.EMPTY.withColor(clanColor)));
        message.append(Component.literal(" (" + clan.name() + ")").withStyle(ChatFormatting.GRAY))
                .append("\n");

        getPlayerName(context, clan.leader()).thenAccept(optLeaderName -> {
            String leaderName = optLeaderName.orElse("Unknown Player");

            message.append(Component.literal("Leader: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(leaderName).withStyle(ChatFormatting.GOLD))
                    .append("\n");

            formatPlayerList(context, clan.officers(), clan.leader(), clan.officers()).thenAccept(officersText -> {
                if (!clan.officers().isEmpty()) {
                    message.append(Component.literal("Officers: ").withStyle(ChatFormatting.WHITE))
                            .append(officersText)
                            .append("\n");
                }

                formatPlayerList(context, clan.members(), clan.leader(), clan.officers()).thenAccept(membersText -> {
                    message.append(Component.literal("Members: ").withStyle(ChatFormatting.WHITE))
                            .append(membersText)
                            .append("\n");

                    message.append(Component.literal("Color: ").withStyle(ChatFormatting.WHITE));
                    MinecraftColor color = MinecraftColor.fromColor(
                            Integer.parseInt(clan.hexColor().substring(1), 16)
                    );
                    if (color != null) {
                        message.append(Component.literal(color.getDisplayName())
                                .setStyle(Style.EMPTY.withColor(clanColor)));
                    } else {
                        message.append(Component.literal(clan.hexColor())
                                .setStyle(Style.EMPTY.withColor(clanColor)));
                    }
                    message.append(", Access: ").withStyle(ChatFormatting.WHITE);
                    message.append(Component.literal(clan.isClosed() ? "Invite Only" : "Open")
                            .withStyle(clan.isClosed() ? ChatFormatting.RED : ChatFormatting.GREEN));

                    context.getSource().getServer().execute(() -> context.getSource().sendSystemMessage(message));
                });
            });
        });
    }

    private CompletableFuture<MutableComponent> formatPlayerList(CommandContext<CommandSourceStack> context,
                                                                 LinkedHashSet<UUID> uuids,
                                                                 UUID leaderUuid,
                                                                 LinkedHashSet<UUID> officerUuids) {
        List<CompletableFuture<MutableComponent>> futures = uuids.stream()
                .map(uuid ->
                        getPlayerName(context, uuid).thenApply(optName -> {
                            String name = optName.orElse("Unknown Player");
                            ChatFormatting format;
                            if (uuid.equals(leaderUuid)) {
                                format = ChatFormatting.GOLD;
                            } else if (officerUuids.contains(uuid)) {
                                format = ChatFormatting.YELLOW;
                            } else {
                                format = ChatFormatting.GRAY;
                            }
                            return Component.literal(name).withStyle(format);
                        })
                )
                .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(v -> {
                    MutableComponent list = Component.empty();
                    for (int i = 0; i < futures.size(); i++) {
                        list.append(futures.get(i).join());
                        if (i < futures.size() - 1) {
                            list.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                        }
                    }
                    return list;
                });
    }
}
