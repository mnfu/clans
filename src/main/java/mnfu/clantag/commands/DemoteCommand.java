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

public class DemoteCommand {
    private final ClanManager clanManager;

    public DemoteCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("demote")
                .then(Commands.argument("playerName", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player == null) return builder.buildFuture();
                            Clan clan = clanManager.getPlayerClan(player.getUUID());
                            if (clan == null) return builder.buildFuture();
                            CompletableFuture<?>[] nameFutures = clan.members().stream()
                                    .filter(uuid -> !uuid.equals(clan.leader()))
                                    .filter(clan.officers()::contains)
                                    .map(uuid -> CommandUtils.getPlayerName(context, uuid)
                                            .thenAccept(optName -> optName.ifPresent(builder::suggest)))
                                    .toArray(CompletableFuture[]::new);
                            return CompletableFuture.allOf(nameFutures)
                                    .thenApply(v -> builder.build());
                        })
                        .executes(this::executeDemote))
                .executes(context -> {
                    context.getSource().sendFailure(Component.literal("Usage: /clan demote <playerName>"));
                    return 1;
                });
    }

    private int executeDemote(CommandContext<CommandSourceStack> context) {
        ServerPlayer executor = context.getSource().getPlayer();
        if (executor == null) return 0;
        Clan clan = clanManager.getPlayerClan(executor.getUUID());
        if (clan == null) {
            context.getSource().sendFailure(Component.literal("You are not in a clan!"));
            return 0;
        }
        if (!clan.leader().equals(executor.getUUID())) {
            context.getSource().sendFailure(Component.literal("You must be a clan leader to use this command!"));
            return 0;
        }

        String targetName = StringArgumentType.getString(context, "playerName");
        getUuid(context, targetName).thenAccept(optUuid -> context.getSource().getServer().execute(() -> {
            if (optUuid.isEmpty()) {
                context.getSource().sendFailure(Component.literal("Player not found!"));
                return;
            }
            UUID targetUuid = optUuid.get();
            if (targetUuid.equals(clan.leader())) {
                context.getSource().sendFailure(Component.literal("You cannot demote yourself!"));
                return;
            }
            if (!clan.members().contains(targetUuid)) {
                context.getSource().sendFailure(Component.literal(targetName + " is not in your clan!"));
                return;
            }
            if (!clan.officers().contains(targetUuid)) {
                context.getSource().sendFailure(Component.literal(targetName + " is not an Officer!"));
                return;
            }
            clanManager.removeOfficer(clan.name(), targetUuid);
            context.getSource().sendSystemMessage(Component.literal("Demoted " + targetName + " from Officer!"));
        }));
        return 1;
    }
}
