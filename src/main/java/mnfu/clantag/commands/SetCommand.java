package mnfu.clantag.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import mnfu.clantag.ClanManager.JoinPolicy;
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

public class SetCommand {
    private final ClanManager clanManager;
    private final Collection<String> colorNames = Formatting.getNames(true, false);

    public SetCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("set")
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
                        .executes(context -> {
                            context.getSource().sendError(Text.literal("Usage: /clan set color <colorName | hexCode>"));
                            return 0;
                        })
                )
                // access subcommands
                .then(CommandManager.literal("access")
                        .then(CommandManager.literal("open").executes(ctx -> executeAccess(ctx, JoinPolicy.OPEN)))
                        .then(CommandManager.literal("invite_only").executes(ctx -> executeAccess(ctx, JoinPolicy.INVITE_ONLY)))
                        .then(CommandManager.literal("toggle").executes(this::executeAccessToggle))
                )
                // name <newClanName>
                .then(CommandManager.literal("name")
                        .then(CommandManager.argument("newClanName", StringArgumentType.greedyString())
                                .executes(this::executeName)
                        )
                        .executes(context -> {
                            context.getSource().sendError(Text.literal("Usage: /clan set name <newClanName>"));
                            return 0;
                        })
                )
                // default response
                .executes(context -> {
                    context.getSource().sendError(Text.literal("Valid subcommands: color, access, name"));
                    return 0;
                });
    }

    private int executeColor(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor == null) return 0;
        Clan clan = clanManager.getPlayerClan(executor.getUuid());
        if (!checkClanLeader(executor, clan, context)) return 0;
        assert clan != null; // checkClanLeader handles this case

        String newColor = StringArgumentType.getString(context, "newColorNameOrHex");
        if (newColor == null || newColor.isEmpty()) return 0;

        Formatting formatting = Formatting.byName(newColor);
        if (formatting != null && formatting.isColor()) {
            newColor = "#" + Integer.toHexString(formatting.getColorValue()).toUpperCase(Locale.ROOT);
        } else if (newColor.matches("(?i)^#?[0-9a-f]{1,6}$")) {
            newColor = "#" + newColor.replaceFirst("^#", "").toUpperCase(Locale.ROOT);
        } else if ("reset".equalsIgnoreCase(newColor)) {
            newColor = "#" + Integer.toHexString(Formatting.WHITE.getColorValue()).toUpperCase(Locale.ROOT);
        } else {
            context.getSource().sendError(Text.literal(newColor + " is not a valid hex color or minecraft color."));
            return 0;
        }

        String oldColor = clan.hexColor();
        boolean success = clanManager.changeColor(clan.name(), newColor);
        if (!success) {
            context.getSource().sendError(Text.literal("Failed to update the color for clan " + clan.name()));
            return 0;
        }

        MutableText message = Text.empty();
        TextColor oldClanTextColor = TextColor.parse(oldColor).getOrThrow();
        TextColor newClanTextColor = TextColor.parse(newColor).getOrThrow();

        message.append(Text.literal("Updated clan color from ").formatted(Formatting.GRAY))
                .append(Text.literal(colorDisplayName(oldColor)).setStyle(Style.EMPTY.withColor(oldClanTextColor)))
                .append(Text.literal(" to ").formatted(Formatting.GRAY))
                .append(Text.literal(colorDisplayName(newColor)).setStyle(Style.EMPTY.withColor(newClanTextColor)))
                .append(Text.literal("!").formatted(Formatting.GRAY));

        context.getSource().sendMessage(message);
        return 1;
    }

    private String colorDisplayName(String colorString) {
        MinecraftColor color = MinecraftColor.fromColor(Integer.parseInt(colorString.substring(1), 16));
        return color != null ? color.getDisplayName() : colorString;
    }

    private int executeAccess(CommandContext<ServerCommandSource> context, JoinPolicy newPolicy) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor == null) return 0;
        Clan clan = clanManager.getPlayerClan(executor.getUuid());
        if (!checkClanLeader(executor, clan, context)) return 0;
        assert clan != null; // checkClanLeader handles this case

        JoinPolicy oldPolicy = clan.isClosed() ? JoinPolicy.INVITE_ONLY : JoinPolicy.OPEN;
        clanManager.changePolicy(clan.name(), newPolicy);

        MutableText message = Text.empty()
                .append(Text.literal("Updated clan access from ").formatted(Formatting.GRAY))
                .append(accessText(oldPolicy != JoinPolicy.OPEN))
                .append(Text.literal(" to ").formatted(Formatting.GRAY))
                .append(accessText(newPolicy != JoinPolicy.OPEN))
                .append(Text.literal("!").formatted(Formatting.GRAY));

        context.getSource().sendMessage(message);
        return 1;
    }

    private int executeAccessToggle(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor == null) return 0;
        Clan clan = clanManager.getPlayerClan(executor.getUuid());
        if (!checkClanLeader(executor, clan, context)) return 0;
        assert clan != null; // checkClanLeader handles this case

        JoinPolicy newPolicy = clan.isClosed() ? JoinPolicy.OPEN : JoinPolicy.INVITE_ONLY;
        return executeAccess(context, newPolicy);
    }

    private MutableText accessText(boolean closed) {
        return Text.literal(closed ? "Invite Only" : "Open")
                .formatted(closed ? Formatting.RED : Formatting.GREEN);
    }

    private int executeName(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor == null) return 0;
        Clan clan = clanManager.getPlayerClan(executor.getUuid());
        if (!checkClanLeader(executor, clan, context)) return 0;
        assert clan != null; // checkClanLeader handles this case

        String newClanName = StringArgumentType.getString(context, "newClanName");
        if (newClanName.contains(" ")) {
            context.getSource().sendError(Text.literal("Clan names must not contain spaces!"));
            return 0;
        }
        if (newClanName.length() < 3 || newClanName.length() > 16) {
            context.getSource().sendError(Text.literal("Clan names must be 3-16 characters in length!"));
            return 0;
        }

        boolean clanRenamed = clanManager.changeName(clan.name(), newClanName);
        if (!clanRenamed) {
            context.getSource().sendError(Text.literal("Clan " + newClanName + " already exists, or " + newClanName + " isn't an allowed name!"));
            return 0;
        }

        TextColor clanTextColor = TextColor.parse(clan.hexColor()).getOrThrow();
        MutableText message = Text.literal("Updated clan name from ").formatted(Formatting.GRAY)
                .append(Text.literal(clan.name()).setStyle(Style.EMPTY.withColor(clanTextColor)))
                .append(Text.literal(" to ").formatted(Formatting.GRAY))
                .append(Text.literal(newClanName).setStyle(Style.EMPTY.withColor(clanTextColor)))
                .append(Text.literal("!").formatted(Formatting.GRAY));

        context.getSource().sendMessage(message);
        return 1;
    }

    private boolean checkClanLeader(ServerPlayerEntity executor, Clan clan, CommandContext<ServerCommandSource> context) {
        if (clan == null) {
            context.getSource().sendError(Text.literal("You are not in a clan!"));
            return false;
        }
        if (!clan.leader().equals(executor.getUuid())) {
            context.getSource().sendError(Text.literal("You must be a clan leader to use this command!"));
            return false;
        }
        return true;
    }
}