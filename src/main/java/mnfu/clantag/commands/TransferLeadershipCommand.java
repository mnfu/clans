package mnfu.clantag.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

import static mnfu.clantag.commands.CommandUtils.getUuid;

public class TransferLeadershipCommand {
    private final ClanManager clanManager;

    public TransferLeadershipCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("transfer")
                .then(CommandManager.argument("playerName", StringArgumentType.word())
                        .executes(context -> {

                            ServerPlayerEntity executor = context.getSource().getPlayer();
                            if (executor == null) {
                                context.getSource().sendError(Text.literal("Only leaders can transfer leadership!"));
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
                                            context.getSource().sendError(Text.literal(
                                                    "You are already the leader of this clan!"
                                            ));
                                            return;
                                        }

                                        boolean transferReturnMessage = clanManager.transferLeader(playerClan.name(), targetUuid);

                                        if (!transferReturnMessage) { // player not in this clan
                                            context.getSource().sendError(Text.literal("You are not in this clan!"));
                                            // ^^^ how can i find out what the name of the clan is
                                        }

                                        context.getSource().sendMessage(Text.literal(
                                                "Successfully transferred leadership of " + playerClan.name() +
                                                        " to " + targetName + "!"));

                                    })
                            );

                            return 1;
                        })
                );
    }
}
