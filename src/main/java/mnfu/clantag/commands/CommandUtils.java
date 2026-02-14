package mnfu.clantag.commands;

import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.Clan;
import mnfu.clantag.MojangApi;
import mnfu.clantag.PersistentPlayerCache;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

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
        if (player != null) return CompletableFuture.completedFuture(Optional.of(player.getName().getString()));

        PersistentPlayerCache cache = PersistentPlayerCache.getInstance();
        if (cache != null) {
            Optional<String> cached = cache.getUsername(uuid);
            if (cached.isPresent()) return CompletableFuture.completedFuture(cached);
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
        if (player != null) return CompletableFuture.completedFuture(Optional.of(player.getUuid()));

        PersistentPlayerCache cache = PersistentPlayerCache.getInstance();
        if (cache != null) {
            Optional<UUID> cached = cache.getUuid(playerName);
            if (cached.isPresent()) return CompletableFuture.completedFuture(cached);
        }

        return MojangApi.getUuid(playerName);
    }

    /**
     * gets a player's name, checking online players first, then delegating to {@link MojangApi} for offline resolution.
     *
     * @return an {@link Optional} containing the player's name if found, otherwise {@link Optional#empty()}
     */
    public static CompletableFuture<Optional<String>> getPlayerName(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) return CompletableFuture.completedFuture(Optional.of(player.getName().getString()));

        PersistentPlayerCache cache = PersistentPlayerCache.getInstance();
        if (cache != null) {
            Optional<String> cached = cache.getUsername(uuid);
            if (cached.isPresent()) return CompletableFuture.completedFuture(cached);
        }

        return MojangApi.getUsername(uuid);
    }

    /**
     * gets a player's uuid, checking online players first, then delegating to {@link MojangApi} for offline resolution.
     *
     * @return an {@link Optional} containing the UUID if found, otherwise {@link Optional#empty()}
     */
    public static CompletableFuture<Optional<UUID>> getUuid(MinecraftServer server, String playerName) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
        if (player != null) return CompletableFuture.completedFuture(Optional.of(player.getUuid()));

        PersistentPlayerCache cache = PersistentPlayerCache.getInstance();
        if (cache != null) {
            Optional<UUID> cached = cache.getUuid(playerName);
            if (cached.isPresent()) return CompletableFuture.completedFuture(cached);
        }

        return MojangApi.getUuid(playerName);
    }

    public static Text getColoredClanName(Clan clan) {
        TextColor textColor = TextColor.parse(clan.hexColor()).getOrThrow();
        return Text.literal(clan.name())
                .setStyle(Style.EMPTY.withColor(textColor));
    }
}
