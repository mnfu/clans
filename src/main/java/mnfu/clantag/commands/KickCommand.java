package mnfu.clantag.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import net.minecraft.server.players.PlayerList;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static mnfu.clantag.commands.CommandUtils.getUuid;

public class KickCommand {
    private final ClanManager clanManager;

    public KickCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("kick")
                .then(Commands.argument("playerName", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            ServerPlayer executor = context.getSource().getPlayer();
                            if (executor == null) return builder.buildFuture();

                            Clan clan = clanManager.getPlayerClan(executor.getUUID());
                            if (clan == null) return builder.buildFuture();

                            boolean executorIsLeader = clan.leader().equals(executor.getUUID());
                            boolean executorIsOfficer = clan.officers().contains(executor.getUUID());

                            CompletableFuture<?>[] nameFutures = clan.members().stream()
                                    .filter(targetUuid -> {
                                        // always allow self for self-kick
                                        if (targetUuid.equals(executor.getUUID())) return true;

                                        // leader can kick anyone
                                        if (executorIsLeader) return true;

                                        // officer can kick only members (not leader or other officers)
                                        return executorIsOfficer && !targetUuid.equals(clan.leader()) && !clan.officers().contains(targetUuid);
                                    })
                                    .map(uuid -> CommandUtils.getPlayerName(context, uuid)
                                            .thenAccept(optName -> optName.ifPresent(builder::suggest)))
                                    .toArray(CompletableFuture[]::new);

                            return CompletableFuture.allOf(nameFutures)
                                    .thenApply(v -> builder.build());
                        })
                        .executes(context -> {

                            ServerPlayer executor = context.getSource().getPlayer();
                            if (executor == null) {
                                context.getSource().sendFailure(Component.literal("Only players can kick members from clans!"));
                                return 0;
                            }

                            String targetName = StringArgumentType.getString(context, "playerName");
                            UUID executorUuid = executor.getUUID();
                            Clan playerClan = clanManager.getPlayerClan(executorUuid);

                            if (playerClan == null) {
                                context.getSource().sendFailure(Component.literal("You are not in a clan!"));
                                return 0;
                            }

                            boolean executorIsClanLeader = playerClan.leader().equals(executorUuid);
                            boolean executorIsClanOfficer = playerClan.officers().contains(executorUuid);

                            if (!executorIsClanLeader && !executorIsClanOfficer) {
                                context.getSource().sendFailure(Component.literal("You are not the leader or an officer of a clan!"));
                                return 0;
                            }

                            // async UUID lookup
                            getUuid(context, targetName).thenAccept(optUuid ->
                                    context.getSource().getServer().execute(() -> { // back on main thread
                                        if (optUuid.isEmpty()) {
                                            context.getSource().sendFailure(Component.literal("Player not found!"));
                                            return;
                                        }

                                        UUID targetUuid = optUuid.get();

                                        if (!playerClan.members().contains(targetUuid)) {
                                            context.getSource().sendFailure(Component.literal(targetName + " is not in clan " + playerClan.name() + "!"));
                                            return;
                                        }

                                        // kicking self logic
                                        if (targetUuid.equals(executorUuid)) {
                                            if (playerClan.members().size() == 1) {
                                                clanManager.deleteClan(playerClan.name());
                                                context.getSource().sendSystemMessage(Component.literal(
                                                        "You have kicked yourself from " + playerClan.name() +
                                                                "! Since you were the only member, the clan was disbanded."
                                                ));
                                                return;
                                            }
                                            if (executorIsClanLeader) {
                                                context.getSource().sendFailure(Component.literal(
                                                        "You must transfer ownership before leaving or disbanding the clan!"
                                                ));
                                                return;
                                            }
                                            clanManager.removeMember(playerClan.name(), targetUuid);
                                            PlayerList pm = context.getSource().getServer().getPlayerList();
                                            String executorName = executor.getName().getString();
                                            for (UUID member : playerClan.members()) {
                                                if (member.equals(targetUuid)) continue;
                                                ServerPlayer player = pm.getPlayer(member);
                                                if (player != null) {
                                                    player.sendSystemMessage(Component.literal(executorName + " was kicked from the clan by " + executorName));
                                                }
                                            }
                                            context.getSource().sendSystemMessage(Component.literal("You have kicked yourself from " + playerClan.name() + "!"));
                                            return;
                                        }

                                        boolean targetIsLeader = targetUuid.equals(playerClan.leader());
                                        boolean targetIsOfficer = playerClan.officers().contains(targetUuid);

                                        // officer trying to kick leader or officer
                                        if (executorIsClanOfficer && (targetIsLeader || targetIsOfficer)) {
                                            context.getSource().sendFailure(Component.literal(
                                                    "Officers cannot kick other officers or the leader!"
                                            ));
                                            return;
                                        }

                                        // leader can kick anyone, so no check needed

                                        // remove the member
                                        clanManager.removeMember(playerClan.name(), targetUuid);
                                        PlayerList pm = context.getSource().getServer().getPlayerList();
                                        String executorName = executor.getName().getString();
                                        for (UUID member : playerClan.members()) {
                                            if (member.equals(executorUuid) || member.equals(targetUuid)) continue;
                                            ServerPlayer player = pm.getPlayer(member);
                                            if (player != null) {
                                                player.sendSystemMessage(Component.literal(targetName + " was kicked from the clan by " + executorName));
                                            }
                                        }
                                        context.getSource().sendSystemMessage(Component.literal(
                                                "Kicked " + targetName + " from " + playerClan.name() + "!"
                                        ));
                                    })
                            );

                            return 1;
                        })
                );
    }
}
