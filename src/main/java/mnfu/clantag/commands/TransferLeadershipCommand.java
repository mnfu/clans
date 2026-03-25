package mnfu.clantag.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static mnfu.clantag.commands.CommandUtils.getUuid;

public class TransferLeadershipCommand {
    private final ClanManager clanManager;

    public TransferLeadershipCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("transfer")
                .then(Commands.argument("playerName", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player == null) return builder.buildFuture();
                            Clan clan = clanManager.getPlayerClan(player.getUUID());
                            if (clan == null) return builder.buildFuture();
                            CompletableFuture<?>[] nameFutures = clan.members().stream()
                                    .map(uuid -> CommandUtils.getPlayerName(context, uuid)
                                            .thenAccept(optName -> optName.ifPresent(builder::suggest)))
                                    .toArray(CompletableFuture[]::new);
                            return CompletableFuture.allOf(nameFutures)
                                    .thenApply(v -> builder.build());
                        })
                        .executes(this::executeTransfer)
                );
    }

    private int executeTransfer(CommandContext<CommandSourceStack> context) {
        ServerPlayer executor = context.getSource().getPlayer();
        if (executor == null) {
            context.getSource().sendFailure(Component.literal("Only players can transfer leadership!"));
            return 0;
        }

        String targetName = StringArgumentType.getString(context, "playerName");
        UUID executorUuid = executor.getUUID();
        Clan playerClan = clanManager.getPlayerClan(executorUuid);

        if (playerClan == null) {
            context.getSource().sendFailure(Component.literal("You are not in a clan!"));
            return 0;
        }

        if (!playerClan.leader().equals(executorUuid)) {
            context.getSource().sendFailure(Component.literal("You are not the leader of a clan!"));
            return 0;
        }

        getUuid(context, targetName).thenAccept(optUuid ->
                context.getSource().getServer().execute(() -> {
                    if (optUuid.isEmpty()) {
                        context.getSource().sendFailure(Component.literal("Player not found!"));
                        return;
                    }

                    UUID targetUuid = optUuid.get();

                    if (targetUuid.equals(executorUuid)) {
                        context.getSource().sendFailure(Component.literal("You are already the leader of this clan!"));
                        return;
                    }

                    boolean success = clanManager.transferLeader(playerClan.name(), targetUuid);
                    if (!success) {
                        context.getSource().sendFailure(Component.literal(targetName + " is not in " + playerClan.name()));
                        return;
                    }

                    context.getSource().sendSystemMessage(Component.literal(
                            "Successfully transferred leadership of " + playerClan.name() + " to " + targetName + "!"));
                })
        );

        return 1;
    }
}
