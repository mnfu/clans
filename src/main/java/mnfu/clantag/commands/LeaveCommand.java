package mnfu.clantag.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

public class LeaveCommand {
    private final ClanManager clanManager;

    public LeaveCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("leave")
                .executes(context -> {
                    ServerPlayerEntity executor = context.getSource().getPlayer();
                    if (executor == null) {
                        context.getSource().sendError(Text.literal("Only players can leave clans!"));
                        return 0;
                    }

                    UUID executorUuid = executor.getUuid();
                    Clan playerClan = clanManager.getPlayerClan(executorUuid);

                    if (playerClan == null) {
                        context.getSource().sendError(Text.literal("You are not in a clan!"));
                        return 0;
                    }

                    // if executor is the leader
                    if (playerClan.leader().equals(executorUuid)) {
                        // if sole member, disband clan
                        if (playerClan.members().size() == 1) {
                            clanManager.deleteClan(playerClan.name());
                            context.getSource().sendMessage(Text.literal(
                                    "You have left " + playerClan.name() +
                                            "! Since you were the only member remaining, the clan was disbanded."
                            ));
                            return 1;
                        }

                        // must transfer ownership first
                        context.getSource().sendError(Text.literal(
                                "You must transfer ownership before leaving or disbanding the clan!"
                        ));
                        return 0;
                    }

                    // normal member leaving
                    clanManager.removeMember(playerClan.name(), executorUuid);
                    PlayerManager pm = context.getSource().getServer().getPlayerManager();
                    for (UUID member : playerClan.members()) {
                        if (member.equals(executorUuid)) continue;
                        ServerPlayerEntity player = pm.getPlayer(member);
                        if (player != null) {
                            player.sendMessage(Text.literal(executor.getName().getString() + " left the clan!"));
                        }
                    }
                    context.getSource().sendMessage(Text.literal(
                            "You have left " + playerClan.name() + "!"
                    ));
                    return 1;
                });
    }
}
