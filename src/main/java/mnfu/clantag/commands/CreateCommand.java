package mnfu.clantag.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.ClanManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.UUID;

public class CreateCommand {
    private final ClanManager clanManager;


    public CreateCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("create")
                .then(CommandManager.argument("clanName", StringArgumentType.greedyString())
                        .executes(this::executeCreate)
                );
    }

    private int executeCreate(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor == null) {
            context.getSource().sendError(Text.literal("Only players can create clans!"));
            return 0;
        }

        UUID executorUuid = executor.getUuid();
        if (clanManager.playerInAClan(executorUuid)) {
            context.getSource().sendError(Text.literal(
                    "You must leave or disband your current clan before creating a new one."
            ));
            return 0;
        }

        String clanName = StringArgumentType.getString(context, "clanName");
        if (clanName.contains(" ")) {
            context.getSource().sendError(Text.literal("Clan names must not contain spaces!"));
            return 0;
        }
        if (clanName.length() < 3 || clanName.length() > 16) {
            context.getSource().sendError(Text.literal("Clan names must be 3-16 characters in length!"));
            return 0;
        }

        boolean clanCreated = clanManager.createClan(clanName, executorUuid);
        if (clanCreated) {
            executor.sendMessage(Text.literal("Clan " + clanName + " created!"), false);
        } else {
            context.getSource().sendError(Text.literal("Clan " + clanName + " already exists, or " + clanName + " isn't an allowed name!"));
        }

        return 1;
    }
}
