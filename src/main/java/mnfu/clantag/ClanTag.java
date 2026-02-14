package mnfu.clantag;

import eu.pb4.placeholders.api.PlaceholderResult;
import mnfu.clantag.commands.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import eu.pb4.placeholders.api.Placeholders;

import java.io.File;

import static mnfu.clantag.ClanUuidCacheBuilder.getInstance;
import static mnfu.clantag.ClanUuidCacheBuilder.init;

public class ClanTag implements ModInitializer {

    public static final String MOD_ID = "ClanTag";
    public static final String FEEDBACK_PREFIX = "[ClanTag] ";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private ClanManager clanManager;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing ClanTag");

        File file = new File("config/clans/clans.json");
        InviteManager inviteManager = new InviteManager();
        clanManager = new ClanManager(file, LOGGER, inviteManager);
        LOGGER.info("Successfully loaded {} clan(s)", clanManager.clanCount());
        registerLifecycleEvents();

        // cache players when they join, reducing any offline player lookups
        ServerPlayConnectionEvents.JOIN.register(((serverPlayNetworkHandler, packetSender, minecraftServer) -> {
            ServerPlayerEntity player = serverPlayNetworkHandler.getPlayer();
            MojangApi.cachePlayer(player);
        }));

        // register placeholders
        Placeholders.register(
                Identifier.of("clantag", "player_clan_name"),
                (ctx, arg) -> {
                    if (!ctx.hasPlayer() || ctx.player() == null) return PlaceholderResult.invalid();
                    Clan clan = clanManager.getPlayerClan(ctx.player().getUuid());
                    if (clan == null) return PlaceholderResult.value(Text.literal("Avience"));
                    return PlaceholderResult.value(Text.literal(clan.name()));
                }
        );
        Placeholders.register(
                Identifier.of("clantag", "player_clan_name_colored"),
                (ctx, arg) -> {
                    if (!ctx.hasPlayer() || ctx.player() == null) return PlaceholderResult.invalid();
                    Clan clan = clanManager.getPlayerClan(ctx.player().getUuid());
                    if (clan == null) return PlaceholderResult.value(Text.literal("Avience"));
                    return PlaceholderResult.value(Text.literal(clan.name()).withColor(Integer.parseInt(clan.hexColor().substring(1), 16)));
                }
        );

        // register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            var baseCommand = CommandManager.literal("clan");
            var adminCommand = new AdminCommand(clanManager).build();
            var infoCommand = new InfoCommand(clanManager).build();

            InviteCommand inviteCommand = new InviteCommand(clanManager, inviteManager);
            var inviteSubcommand = inviteCommand.buildInvite();
            var acceptCommand = inviteCommand.buildAccept();
            var declineCommand = inviteCommand.buildDecline();
            var invitesCommand = inviteCommand.buildInvites();

            var createCommand = new CreateCommand(clanManager).build();
            var disbandCommand = new DisbandCommand(clanManager).build();
            var kickCommand = new KickCommand(clanManager).build();
            var leaveCommand = new LeaveCommand(clanManager).build();
            var modifyCommand = new ModifyCommand(clanManager).build();

            dispatcher.register(baseCommand
                    .then(adminCommand)
                    .then(modifyCommand)
                    .then(infoCommand)
                    .then(inviteSubcommand)
                    .then(acceptCommand)
                    .then(declineCommand)
                    .then(invitesCommand)
                    .then(createCommand)
                    .then(disbandCommand)
                    .then(kickCommand)
                    .then(leaveCommand)
                    .executes(context -> {
                        context.getSource().sendFeedback(()->Text.literal("Command help message not implemented, WIP!"), false);
                        return 1;
                    })
            );
        });
    }

    private void registerLifecycleEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!clanManager.loadedSuccessfully()) {
                LOGGER.warn("Skipping UUID cache build due to failed load.");
                return;
            }

            init(clanManager, server, LOGGER);
            getInstance().start();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ClanUuidCacheBuilder cacheBuilder = getInstance();
            if (cacheBuilder != null) {
                cacheBuilder.shutdown();
            }
            clanManager.save();
        });
    }
}
