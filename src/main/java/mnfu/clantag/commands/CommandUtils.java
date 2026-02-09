package mnfu.clantag.commands;

import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.MojangApi;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CommandUtils {

    private CommandUtils() {} // prevent instantiation

    /**
     * gets a player's name, checking online players first, then delegating to {@link MojangApi} for offline resolution.
     *
     * @return an {@link Optional} containing the player's name if found, otherwise {@link Optional#empty()}
     */
    public static CompletableFuture<Optional<String>> getPlayerName(CommandContext<ServerCommandSource> context, UUID uuid) {
        ServerPlayerEntity player = context.getSource().getServer()
                .getPlayerManager()
                .getPlayer(uuid);

        if (player != null) {
            return CompletableFuture.completedFuture(Optional.of(player.getName().getString()));
        }
        return MojangApi.getUsername(uuid);
    }

    /**
     * gets a player's uuid, checking online players first, then delegating to {@link MojangApi} for offline resolution.
     *
     * @return an {@link Optional} containing the UUID if found, otherwise {@link Optional#empty()}
     */
    public static CompletableFuture<Optional<UUID>> getUuid(CommandContext<ServerCommandSource> context, String playerName) {
        ServerPlayerEntity player = context.getSource().getServer()
                .getPlayerManager()
                .getPlayer(playerName);

        if (player != null) {
            return CompletableFuture.completedFuture(Optional.of(player.getUuid()));
        }
        return MojangApi.getUuid(playerName);
    }
}
