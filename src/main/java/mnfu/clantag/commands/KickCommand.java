package mnfu.clantag.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import net.minecraft.server.PlayerManager;
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
                            ServerPlayerEntity executor = context.getSource().getPlayer();
                            if (executor == null) return builder.buildFuture();

                            Clan clan = clanManager.getPlayerClan(executor.getUuid());
                            if (clan == null) return builder.buildFuture();

                            boolean executorIsLeader = clan.leader().equals(executor.getUuid());
                            boolean executorIsOfficer = clan.officers().contains(executor.getUuid());

                            CompletableFuture<?>[] nameFutures = clan.members().stream()
                                    .filter(targetUuid -> {
                                        // always allow self for self-kick
                                        if (targetUuid.equals(executor.getUuid())) return true;

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

                            boolean executorIsClanLeader = playerClan.leader().equals(executorUuid);
                            boolean executorIsClanOfficer = playerClan.officers().contains(executorUuid);

                            if (!executorIsClanLeader && !executorIsClanOfficer) {
                                context.getSource().sendError(Text.literal("You are not the leader or an officer of a clan!"));
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

                                        if (!playerClan.members().contains(targetUuid)) {
                                            context.getSource().sendError(Text.literal(targetName + " is not in clan " + playerClan.name() + "!"));
                                            return;
                                        }

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
                                            if (executorIsClanLeader) {
                                                context.getSource().sendError(Text.literal(
                                                        "You must transfer ownership before leaving or disbanding the clan!"
                                                ));
                                                return;
                                            }
                                            clanManager.removeMember(playerClan.name(), targetUuid);
                                            PlayerManager pm = context.getSource().getServer().getPlayerManager();
                                            String executorName = executor.getName().getString();
                                            for (UUID member : playerClan.members()) {
                                                if (member.equals(targetUuid)) continue;
                                                ServerPlayerEntity player = pm.getPlayer(member);
                                                if (player != null) {
                                                    player.sendMessage(Text.literal(executorName + " was kicked from the clan by " + executorName));
                                                }
                                            }
                                            context.getSource().sendMessage(Text.literal("You have kicked yourself from " + playerClan.name() + "!"));
                                            return;
                                        }

                                        boolean targetIsLeader = targetUuid.equals(playerClan.leader());
                                        boolean targetIsOfficer = playerClan.officers().contains(targetUuid);

                                        // officer trying to kick leader or officer
                                        if (executorIsClanOfficer && (targetIsLeader || targetIsOfficer)) {
                                            context.getSource().sendError(Text.literal(
                                                    "Officers cannot kick other officers or the leader!"
                                            ));
                                            return;
                                        }

                                        // leader can kick anyone, so no check needed

                                        // remove the member
                                        clanManager.removeMember(playerClan.name(), targetUuid);
                                        PlayerManager pm = context.getSource().getServer().getPlayerManager();
                                        String executorName = executor.getName().getString();
                                        for (UUID member : playerClan.members()) {
                                            if (member.equals(executorUuid) || member.equals(targetUuid)) continue;
                                            ServerPlayerEntity player = pm.getPlayer(member);
                                            if (player != null) {
                                                player.sendMessage(Text.literal(targetName + " was kicked from the clan by " + executorName));
                                            }
                                        }
                                        context.getSource().sendMessage(Text.literal(
                                                "Kicked " + targetName + " from " + playerClan.name() + "!"
                                        ));
                                    })
                            );

                            return 1;
                        })
                );
    }
}
