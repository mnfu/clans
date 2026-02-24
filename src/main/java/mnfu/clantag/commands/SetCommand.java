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
                )
                .then(CommandManager.literal("access")
                        .then(CommandManager.argument("joinPolicy", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (JoinPolicy policy : JoinPolicy.values()) {
                                        builder.suggest(policy.name().toLowerCase(Locale.ROOT));
                                    }
                                    builder.suggest("toggle");
                                    return builder.buildFuture();
                                })
                                .executes(this::executeAccess)
                        )
                        .executes(context -> {
                            context.getSource().sendError(Text.literal("Usage: /clan set access <open|invite_only|toggle>"));
                            return 0;
                        })
                )

                // default response
                .executes(context -> {
                    context.getSource().sendError(Text.literal("Valid subcommands: color, access"));
                    return 0;
                });
    }

    private int executeColor(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor == null) return 0;
        Clan clan = clanManager.getPlayerClan(executor.getUuid());
        if (clan == null) return 0;
        if (!clan.leader().equals(executor.getUuid())) {
            context.getSource().sendError(Text.literal("You must be a clan leader to use this command!"));
            return 0;
        }

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

            message.append(Text.literal("Updated clan color from ").formatted(Formatting.GRAY));
            message.append(Text.literal(colorDisplayName(oldColor))
                    .setStyle(Style.EMPTY.withColor(oldClanTextColor)));
            message.append(Text.literal(" to ").formatted(Formatting.GRAY));
            message.append(Text.literal(colorDisplayName(newColor)))
                    .setStyle(Style.EMPTY.withColor(newClanTextColor));
            message.append(Text.literal("!").formatted(Formatting.GRAY));
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

    private int executeAccess(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor == null) return 0;
        Clan clan = clanManager.getPlayerClan(executor.getUuid());
        if (clan == null) return 0;
        if (!clan.leader().equals(executor.getUuid())) {
            context.getSource().sendError(Text.literal("You must be a clan leader to use this command!"));
            return 0;
        }

        String clanName = clan.name();
        String rawPolicy = StringArgumentType.getString(context, "joinPolicy");
        String policy = rawPolicy.toLowerCase(Locale.ROOT).replace("_", "");

        JoinPolicy newPolicy;
        boolean toggle = policy.equals("toggle");
        if (toggle) {
            newPolicy = clan.isClosed() ? JoinPolicy.OPEN : JoinPolicy.INVITE_ONLY;
        } else if (policy.equals("open")) {
            newPolicy = JoinPolicy.OPEN;
        } else if (policy.equals("inviteonly") || policy.equals("invite")) {
            newPolicy = JoinPolicy.INVITE_ONLY;
        } else {
            context.getSource().sendError(Text.literal(policy + " is not a valid option. Usage: /clan set access <open|invite_only|toggle>"));
            return 0;
        }

        clanManager.changePolicy(clanName, newPolicy);

        MutableText message;
        if (toggle) {
            message = Text.literal("Toggled clan access from ")
                    .formatted(Formatting.GRAY)
                    .append(accessText(clan.isClosed()))
                    .append(Text.literal(" to ").formatted(Formatting.GRAY))
                    .append(accessText(newPolicy != JoinPolicy.OPEN))
                    .append(Text.literal("!").formatted(Formatting.GRAY));
        } else {
            message = Text.literal("Updated clan access from ")
                    .formatted(Formatting.GRAY)
                    .append(accessText(clan.isClosed()))
                    .append(Text.literal(" to ").formatted(Formatting.GRAY))
                    .append(accessText(newPolicy != JoinPolicy.OPEN))
                    .append(Text.literal("!").formatted(Formatting.GRAY));
        }

        context.getSource().sendMessage(message);
        return 1;
    }

    private MutableText accessText(boolean closed) {
        return Text.literal(closed ? "Invite Only" : "Open")
                .formatted(closed ? Formatting.RED : Formatting.GREEN);
    }
}
