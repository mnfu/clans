package mnfu.clantag.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

public class DisbandCommand {
    private final ClanManager clanManager;

    public DisbandCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("disband")
                .then(CommandManager.literal("confirm")
                        .executes(context -> executeDisband(context, true))
                )
                .executes(context -> executeDisband(context, false));
    }

    private int executeDisband(CommandContext<ServerCommandSource> context, boolean confirm) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor == null) {
            context.getSource().sendError(Text.literal("Only players can disband clans!"));
            return 0;
        }

        UUID executorUuid = executor.getUuid();
        Clan clan = clanManager.getPlayerClan(executorUuid);
        if (clan == null) {
            context.getSource().sendError(Text.literal(
                    "You must be in a clan to disband one!"
            ));
            return 0;
        }

        if (!clan.leader().equals(executorUuid)) {
            context.getSource().sendError(Text.literal(
                    "You must be a clan leader to disband a clan!"
            ));
            return 0;
        }

        if (!confirm) {
            context.getSource().sendMessage(Text.literal(
                    "Are you sure you want to do this? If so, run: /clan disband confirm"
            ));
            return 1;
        }

        // now we know they're the leader of their clan, and they've confirmed.
        boolean success = clanManager.deleteClan(clan.name());
        if (success) {
            context.getSource().sendMessage(Text.literal(
                    "Clan " + clan.name() + " has been disbanded."
            ));
        } else {
            context.getSource().sendMessage(Text.literal(
                    "Clan " + clan.name() + " does not exist."
            ));
        }

        return 1;
    }
}
