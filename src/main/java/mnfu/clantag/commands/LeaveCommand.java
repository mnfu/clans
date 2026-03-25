package mnfu.clantag.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import net.minecraft.server.players.PlayerList;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class LeaveCommand {
    private final ClanManager clanManager;

    public LeaveCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("leave")
                .executes(context -> {
                    ServerPlayer executor = context.getSource().getPlayer();
                    if (executor == null) {
                        context.getSource().sendFailure(Component.literal("Only players can leave clans!"));
                        return 0;
                    }

                    UUID executorUuid = executor.getUUID();
                    Clan playerClan = clanManager.getPlayerClan(executorUuid);

                    if (playerClan == null) {
                        context.getSource().sendFailure(Component.literal("You are not in a clan!"));
                        return 0;
                    }

                    // if executor is the leader
                    if (playerClan.leader().equals(executorUuid)) {
                        // if sole member, disband clan
                        if (playerClan.members().size() == 1) {
                            clanManager.deleteClan(playerClan.name());
                            context.getSource().sendSystemMessage(Component.literal(
                                    "You have left " + playerClan.name() +
                                            "! Since you were the only member remaining, the clan was disbanded."
                            ));
                            return 1;
                        }

                        // must transfer ownership first
                        context.getSource().sendFailure(Component.literal(
                                "You must transfer ownership before leaving or disbanding the clan!"
                        ));
                        return 0;
                    }

                    // normal member leaving
                    clanManager.removeMember(playerClan.name(), executorUuid);
                    PlayerList pm = context.getSource().getServer().getPlayerList();
                    for (UUID member : playerClan.members()) {
                        if (member.equals(executorUuid)) continue;
                        ServerPlayer player = pm.getPlayer(member);
                        if (player != null) {
                            player.sendSystemMessage(Component.literal(executor.getName().getString() + " left the clan!"));
                        }
                    }
                    context.getSource().sendSystemMessage(Component.literal(
                            "You have left " + playerClan.name() + "!"
                    ));
                    return 1;
                });
    }
}
