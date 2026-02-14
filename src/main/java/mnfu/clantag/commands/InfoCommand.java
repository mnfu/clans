package mnfu.clantag.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static mnfu.clantag.commands.CommandUtils.getPlayerName;

public class InfoCommand {
    private final ClanManager clanManager;

    public InfoCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("info")
                .executes(this::executeForSelf)
                .then(CommandManager.argument("clanName", StringArgumentType.greedyString())
                        .suggests((commandContext, suggestionsBuilder) -> {
                            Collection<Clan> clans = clanManager.getAllClans();
                            for (Clan c : clans) {
                                suggestionsBuilder.suggest(c.name());
                            }
                            return suggestionsBuilder.buildFuture();
                        })
                        .executes(this::executeForClanName));
    }

    private int executeForSelf(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor == null) { // not a player if null, assume console
            context.getSource().sendError(Text.literal("You are not a player, so you must specify a clan name."));
            return 0;
        }

        Clan clan = clanManager.getPlayerClan(executor.getUuid());

        if (clan == null) {
            context.getSource().sendError(Text.literal("Clan not found!"));
            return 0;
        }

        displayClanInfo(context, clan);
        return 1;
    }

    private int executeForClanName(CommandContext<ServerCommandSource> context) {
        String clanName = StringArgumentType.getString(context, "clanName");

        Clan clan = clanManager.getClan(clanName);
        if (clan == null) {
            context.getSource().sendError(Text.literal("Clan not found!"));
            return 0;
        }

        displayClanInfo(context, clan);
        return 1;
    }

    private void displayClanInfo(CommandContext<ServerCommandSource> context, Clan clan) {
        MutableText message = Text.empty();

        TextColor clanColor = TextColor.parse(clan.hexColor()).getOrThrow();

        message.append(Text.literal(clan.name())
                .setStyle(Style.EMPTY.withColor(clanColor)));

        message.append(Text.literal(" (" + clan.name() + ")").formatted(Formatting.GRAY))
                .append("\n");

        // async leader name
        getPlayerName(context, clan.leader()).thenAccept(optLeaderName -> {
            String leaderName = optLeaderName.orElse("Unknown Player");

            message.append(Text.literal("Leader: ").formatted(Formatting.WHITE))
                    .append(Text.literal(leaderName).formatted(Formatting.YELLOW))
                    .append("\n");

            // async members
            formatMembersList(context, clan).thenAccept(membersText -> {
                message.append(Text.literal("Members: ").formatted(Formatting.WHITE))
                        .append(membersText)
                        .append("\n");

                // color
                message.append(Text.literal("Color: ").formatted(Formatting.WHITE));

                MinecraftColor color = MinecraftColor.fromColor(
                        Integer.parseInt(clan.hexColor().substring(1), 16)
                );
                if (color != null) {
                    message.append(Text.literal(color.getDisplayName())
                            .setStyle(Style.EMPTY.withColor(clanColor)));
                } else {
                    message.append(Text.literal(clan.hexColor())
                            .setStyle(Style.EMPTY.withColor(clanColor)));
                }

                // finally send the message on the main thread
                context.getSource().getServer().execute(() -> context.getSource().sendMessage(message));
            });
        });
    }

    private CompletableFuture<MutableText> formatMembersList(CommandContext<ServerCommandSource> context, Clan clan) {
        LinkedHashSet<UUID> memberUuids = clan.members();
        UUID leaderUuid = clan.leader();

        // create a future per member
        List<CompletableFuture<MutableText>> futures = memberUuids.stream()
                .map(memberUuid ->
                    getPlayerName(context, memberUuid).thenApply(optName -> {
                        String name = optName.orElse("Unknown Player");

                        Formatting format = memberUuid.equals(leaderUuid)
                                ? Formatting.YELLOW
                                : Formatting.GRAY;

                        return Text.literal(name).formatted(format);
                    })
                )
                .toList();

        CompletableFuture<?>[] futuresArray = futures.toArray(CompletableFuture[]::new);

        // combine all futures in order, append comma between members
        return CompletableFuture.allOf(futuresArray)
                .thenApply(v -> {
                    MutableText list = Text.empty();
                    for (int i = 0; i < futures.size(); i++) {
                        list.append(futures.get(i).join()); // safe, already completed
                        if (i < futures.size() - 1) {
                            list.append(Text.literal(", ").formatted(Formatting.GRAY));
                        }
                    }
                    return list;
                });
    }
}
