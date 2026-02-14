package mnfu.clantag.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static mnfu.clantag.commands.CommandUtils.getUuid;

public class KickCommand {
    private final ClanManager clanManager;

    public KickCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("kick")
                .then(CommandManager.argument("playerName", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return builder.buildFuture();
                            Clan clan = clanManager.getPlayerClan(player.getUuid());
                            if (clan == null) return builder.buildFuture();
                            CompletableFuture<?>[] nameFutures = clan.members().stream()
                                    .map(uuid -> CommandUtils.getPlayerName(context, uuid)
                                            .thenAccept(optName -> optName.ifPresent(builder::suggest)))
                                    .toArray(CompletableFuture[]::new);
                            return CompletableFuture.allOf(nameFutures)
                                    .thenApply(v -> builder.build());
                        })
                        .executes(context -> {

                            ServerPlayerEntity executor = context.getSource().getPlayer();
                            if (executor == null) {
                                context.getSource().sendError(Text.literal("Only players can kick members from clans!"));
                                return 0;
                            }

                            String targetName = StringArgumentType.getString(context, "playerName");
                            UUID executorUuid = executor.getUuid();
                            Clan playerClan = clanManager.getPlayerClan(executorUuid);

                            if (playerClan == null) {
                                context.getSource().sendError(Text.literal("You are not in a clan!"));
                                return 0;
                            }

                            if (!playerClan.leader().equals(executorUuid)) {
                                context.getSource().sendError(Text.literal("You are not the leader of a clan!"));
                                return 0;
                            }

                            // async UUID lookup
                            getUuid(context, targetName).thenAccept(optUuid ->
                                    context.getSource().getServer().execute(() -> { // back on main thread
                                        if (optUuid.isEmpty()) {
                                            context.getSource().sendError(Text.literal("Player not found!"));
                                            return;
                                        }

                                        UUID targetUuid = optUuid.get();

                                        // kicking self logic
                                        if (targetUuid.equals(executorUuid)) {
                                            if (playerClan.members().size() == 1) {
                                                clanManager.deleteClan(playerClan.name());
                                                context.getSource().sendMessage(Text.literal(
                                                        "You have kicked yourself from " + playerClan.name() +
                                                                "! Since you were the only member, the clan was disbanded."
                                                ));
                                                return;
                                            }

                                            context.getSource().sendError(Text.literal(
                                                    "You must transfer ownership before leaving or disbanding the clan!"
                                            ));
                                            return;
                                        }

                                        // remove the member
                                        clanManager.removeMember(playerClan.name(), targetUuid);
                                        context.getSource().sendMessage(Text.literal(
                                                "Kicked " + targetName + " from clan " + playerClan.name() + "!"
                                        ));
                                    })
                            );

                            return 1;
                        })
                );
    }
}
