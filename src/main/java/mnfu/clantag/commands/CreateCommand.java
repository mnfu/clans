package mnfu.clantag.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.ClanManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

public class CreateCommand {
    private final ClanManager clanManager;
    private final Collection<String> colorNames = Formatting.getNames(true, false);


    public CreateCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("create")
                .then(CommandManager.argument("clanName", StringArgumentType.string())
                        .then(CommandManager.argument("hexColor", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String c : colorNames) {
                                        builder.suggest(c);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> executeCreate(context, true))
                        )
                        // execute if no color is provided
                        .executes(context -> executeCreate(context, false))
                );
    }

    private int executeCreate(CommandContext<ServerCommandSource> context, boolean colorProvided) {
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
        String hexColor = "FFFFFF"; // default white

        if (colorProvided) {
            String providedColor = StringArgumentType.getString(context, "hexColor");

            // try to interpret as formatting name first
            Formatting formatting = Formatting.byName(providedColor);
            if (formatting != null && formatting.isColor()) {
                hexColor = Integer.toHexString(formatting.getColorValue()).toUpperCase(Locale.ROOT);
            } else if (providedColor.matches("(?i)^[0-9a-f]{1,6}$")) {
                hexColor = providedColor.toUpperCase(Locale.ROOT);
            } else {
                context.getSource().sendError(Text.literal("Clan was not created. " + providedColor + " is not a valid hex color or minecraft color."));
                return 0;
            }
        }

        boolean clanCreated = clanManager.createClan(clanName, executorUuid, hexColor);
        if (clanCreated) {
            executor.sendMessage(Text.literal("Clan " + clanName + " created!"), false);
        } else {
            context.getSource().sendError(Text.literal("Clan " + clanName + " already exists, or " + clanName + " isn't an allowed name!"));
        }

        return 1;
    }
}
