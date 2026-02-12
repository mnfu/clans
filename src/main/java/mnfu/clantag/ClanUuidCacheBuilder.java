package mnfu.clantag;

import mnfu.clantag.commands.CommandUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

public class ClanUuidCacheBuilder {

    private static final long RESOLVE_INTERVAL_SECONDS = 15;
    private static ClanUuidCacheBuilder INSTANCE;

    private final ClanManager clanManager;
    private final MinecraftServer server;
    private final Logger logger;

    private final Queue<UUID> pending = new ConcurrentLinkedQueue<>();
    private ScheduledExecutorService executor;


    public static ClanUuidCacheBuilder getInstance() {
        return INSTANCE;
    }

    public static void init(ClanManager clanManager, MinecraftServer server, Logger logger) {
        if (INSTANCE == null) {
            INSTANCE = new ClanUuidCacheBuilder(clanManager, server, logger);
        }
    }

    private ClanUuidCacheBuilder(ClanManager clanManager, MinecraftServer server, Logger logger) {
        this.clanManager = clanManager;
        this.server = server;
        this.logger = logger;
    }

    public void start() {
        if (executor != null) return;

        Set<UUID> seen = new HashSet<>();

        for (Clan clan : clanManager.getAllClans()) {
            for (UUID uuid : clan.members()) {
                if (seen.add(uuid)) {
                    pending.add(uuid);
                }
            }
        }

        if (pending.isEmpty()) return;

        logger.info(
                "Starting clan UUID cache build ({} entries, {}s interval) Estimated completion in: {}s",
                pending.size(),
                RESOLVE_INTERVAL_SECONDS,
                pending.size()*RESOLVE_INTERVAL_SECONDS
        );

        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(
                this::resolveNext,
                0,
                RESOLVE_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void resolveNext() {
        UUID uuid = pending.poll();
        if (uuid == null) {
            logger.info("Clan UUID cache build complete.");
            shutdown();
            return;
        }

        CommandUtils.getPlayerName(server, uuid).thenAccept(optName -> {
            if (optName.isPresent()) {
                logger.debug("Cached UUID {} ({})", uuid, optName.get());
            } else {
                pending.add(uuid);
            }
        });
    }

    public void shutdown() {
        if (executor != null) {
            pending.clear();
            executor.shutdownNow();
            executor = null;
        }
    }
}
