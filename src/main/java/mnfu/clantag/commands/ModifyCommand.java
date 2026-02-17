package mnfu.clantag.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

public class ModifyCommand {
    private final ClanManager clanManager;
    private final Collection<String> colorNames = Formatting.getNames(true, false);

    public ModifyCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("modify")
                .requires(source -> {
                    // this requires block means this command is restricted to clan leaders only
                    ServerPlayerEntity player = source.getPlayer();
                    if (player == null) return false;
                    UUID playerUuid = player.getUuid();
                    Clan clan = clanManager.getPlayerClan(playerUuid);
                    if (clan == null) return false;
                    return clan.leader().equals(playerUuid);
                })

                // color <newColorNameOrHex>
                .then(CommandManager.literal("color")
                        .then(CommandManager.argument("newColorNameOrHex", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    for (String c : colorNames) {
                                        builder.suggest(c);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(this::executeColor)
                        )
                )

                // default response
                .executes(context -> {
                    context.getSource().sendError(Text.literal("Valid subcommands: color"));
                    return 0;
                });

    }

    private int executeColor(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor == null) return 0;
        Clan clan = clanManager.getPlayerClan(executor.getUuid());
        if (clan == null) return 0;

        String newColor = StringArgumentType.getString(context, "newColorNameOrHex");

        if (newColor == null || newColor.isEmpty()) return 0;
        // try to interpret as formatting name first
        Formatting formatting = Formatting.byName(newColor);
        if (formatting != null && formatting.isColor()) {
            newColor = "#" + Integer.toHexString(formatting.getColorValue()).toUpperCase(Locale.ROOT);
        } else if (newColor.matches("(?i)^#?[0-9a-f]{1,6}$")) {
            newColor = "#" + newColor.replaceFirst("^#", "").toUpperCase(Locale.ROOT);
        } else {
            context.getSource().sendError(Text.literal(newColor + " is not a valid hex color or minecraft color."));
            return 0;
        }

        String oldColor = clan.hexColor();
        boolean success = clanManager.changeColor(clan.name(), newColor);
        if (success) {
            MutableText message = Text.empty();
            TextColor oldClanTextColor = TextColor.parse(oldColor).getOrThrow();
            TextColor newClanTextColor = TextColor.parse(newColor).getOrThrow();

            message.append(Text.literal("Clan color changed from ").formatted(Formatting.WHITE));
            message.append(Text.literal(colorDisplayName(oldColor))
                    .setStyle(Style.EMPTY.withColor(oldClanTextColor)));
            message.append(Text.literal(" to ").formatted(Formatting.WHITE));
            message.append(Text.literal(colorDisplayName(newColor)))
                    .setStyle(Style.EMPTY.withColor(newClanTextColor));
            message.append(Text.literal("!").formatted(Formatting.WHITE));
            context.getSource().sendMessage(message);
            return 1;
        } else {
            context.getSource().sendError(Text.literal("Failed to update the color for clan " + clan.name()));
            return 0;
        }
    }

    private String colorDisplayName (String colorString) {
        MinecraftColor color = MinecraftColor.fromColor(
                Integer.parseInt(colorString.substring(1), 16)
        );
        if (color != null) {
            return color.getDisplayName();
        } else {
            return colorString;
        }
    }
}
