package mnfu.clantag.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class DisbandCommand {
    private final ClanManager clanManager;

    public DisbandCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("disband")
                .then(Commands.literal("confirm")
                        .executes(context -> executeDisband(context, true))
                )
                .executes(context -> executeDisband(context, false));
    }

    private int executeDisband(CommandContext<CommandSourceStack> context, boolean confirm) {
        ServerPlayer executor = context.getSource().getPlayer();
        if (executor == null) {
            context.getSource().sendFailure(Component.literal("Only players can disband clans!"));
            return 0;
        }

        UUID executorUuid = executor.getUUID();
        Clan clan = clanManager.getPlayerClan(executorUuid);
        if (clan == null) {
            context.getSource().sendFailure(Component.literal(
                    "You must be in a clan to disband one!"
            ));
            return 0;
        }

        if (!clan.leader().equals(executorUuid)) {
            context.getSource().sendFailure(Component.literal(
                    "You must be a clan leader to disband a clan!"
            ));
            return 0;
        }

        if (!confirm) {
            context.getSource().sendSystemMessage(Component.literal(
                    "Are you sure you want to do this? If so, run: /clan disband confirm"
            ));
            return 1;
        }

        // now we know they're the leader of their clan, and they've confirmed.
        boolean success = clanManager.deleteClan(clan.name());
        if (success) {
            context.getSource().sendSystemMessage(Component.literal(
                    "Clan " + clan.name() + " has been disbanded."
            ));
        } else {
            context.getSource().sendSystemMessage(Component.literal(
                    "Clan " + clan.name() + " does not exist."
            ));
        }

        return 1;
    }
}
