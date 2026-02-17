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
        return getPlayerName(context.getSource().getServer(), uuid);
    }

    /**
     * gets a player's uuid, checking online players first, then delegating to {@link MojangApi} for offline resolution.
     *
     * @return an {@link Optional} containing the UUID if found, otherwise {@link Optional#empty()}
     */
    public static CompletableFuture<Optional<UUID>> getUuid(CommandContext<ServerCommandSource> context, String playerName) {
        return getUuid(context.getSource().getServer(), playerName);
    }

    /**
     * gets a player's name, checking online players first, then delegating to {@link MojangApi} for offline resolution.
     *
     * @return an {@link Optional} containing the player's name if found, otherwise {@link Optional#empty()}
     */
    public static CompletableFuture<Optional<String>> getPlayerName(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) return CompletableFuture.completedFuture(Optional.of(player.getName().getString()));

        return CompletableFuture.supplyAsync(() -> {
            PersistentPlayerCache cache = PersistentPlayerCache.getInstance();
            return (cache != null) ? cache.getUsername(uuid) : Optional.<String>empty();
        }).thenCompose(cachedOpt -> {
           if (cachedOpt.isPresent()) return CompletableFuture.completedFuture(cachedOpt);
           // if the following check passes, it's likely a bedrock UUID, and we shouldn't waste time with an API call.
           // if you're reading this, & curious as to why, Java UUIDs will have a nonzero version bit set in this range.
           if (uuid.getMostSignificantBits() == 0) return CompletableFuture.completedFuture(Optional.empty());
           return MojangApi.getUsername(uuid).thenApply(apiOpt -> {
               apiOpt.ifPresent(playerName -> {
                   PersistentPlayerCache cache = PersistentPlayerCache.getInstance();
                   if (cache != null) cache.updateIfChanged(uuid, playerName);
               });
               return apiOpt;
           });
        });
    }

    /**
     * gets a player's uuid, checking online players first, then delegating to {@link MojangApi} for offline resolution.
     *
     * @return an {@link Optional} containing the UUID if found, otherwise {@link Optional#empty()}
     */
    public static CompletableFuture<Optional<UUID>> getUuid(MinecraftServer server, String playerName) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
        if (player != null) return CompletableFuture.completedFuture(Optional.of(player.getUuid()));

        return CompletableFuture.supplyAsync(() -> {
            PersistentPlayerCache cache = PersistentPlayerCache.getInstance();
            return (cache != null) ? cache.getUuid(playerName) : Optional.<UUID>empty();
        }).thenCompose(cachedOpt -> {
            if (cachedOpt.isPresent()) return CompletableFuture.completedFuture(cachedOpt);
            return MojangApi.getUuid(playerName).thenApply(apiOpt -> {
                apiOpt.ifPresent(uuid -> {
                    PersistentPlayerCache cache = PersistentPlayerCache.getInstance();
                    if (cache != null) cache.updateIfChanged(uuid, playerName);
                });
                return apiOpt;
            });
        });
    }

    public static Text getColoredClanName(Clan clan) {
        TextColor textColor = TextColor.parse(clan.hexColor()).getOrThrow();
        return Text.literal(clan.name())
                .setStyle(Style.EMPTY.withColor(textColor));
    }
}
