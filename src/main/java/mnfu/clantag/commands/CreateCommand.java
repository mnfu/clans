package mnfu.clantag.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.ClanManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.UUID;

public class CreateCommand {
    private final ClanManager clanManager;


    public CreateCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("create")
                .then(Commands.argument("clanName", StringArgumentType.greedyString())
                        .executes(this::executeCreate)
                );
    }

    private int executeCreate(CommandContext<CommandSourceStack> context) {
        ServerPlayer executor = context.getSource().getPlayer();
        if (executor == null) {
            context.getSource().sendFailure(Component.literal("Only players can create clans!"));
            return 0;
        }

        UUID executorUuid = executor.getUUID();
        if (clanManager.playerInAClan(executorUuid)) {
            context.getSource().sendFailure(Component.literal(
                    "You must leave or disband your current clan before creating a new one."
            ));
            return 0;
        }

        String clanName = StringArgumentType.getString(context, "clanName");
        if (clanName.contains(" ")) {
            context.getSource().sendFailure(Component.literal("Clan names must not contain spaces!"));
            return 0;
        }
        if (clanName.length() < 3 || clanName.length() > 16) {
            context.getSource().sendFailure(Component.literal("Clan names must be 3-16 characters in length!"));
            return 0;
        }

        boolean clanCreated = clanManager.createClan(clanName, executorUuid);
        if (clanCreated) {
            context.getSource().sendSystemMessage(Component.literal("Clan " + clanName + " created!"));
        } else {
            context.getSource().sendFailure(Component.literal("Clan " + clanName + " already exists, or " + clanName + " isn't an allowed name!"));
        }

        return 1;
    }
}
