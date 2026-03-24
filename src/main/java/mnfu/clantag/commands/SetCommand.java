package mnfu.clantag.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import mnfu.clantag.ClanManager.JoinPolicy;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;

import java.util.Collection;
import java.util.Locale;

public class SetCommand {
    private final ClanManager clanManager;
    private final Collection<String> colorNames = ChatFormatting.getNames(true, false);

    public SetCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("set")
                // color <newColorNameOrHex>
                .then(Commands.literal("color")
                        .then(Commands.argument("newColorNameOrHex", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    for (String c : colorNames) {
                                        builder.suggest(c);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(this::executeColor)
                        )
                        .executes(context -> {
                            context.getSource().sendFailure(Component.literal("Usage: /clan set color <colorName | hexCode>"));
                            return 0;
                        })
                )
                // access subcommands
                .then(Commands.literal("access")
                        .then(Commands.literal("open").executes(ctx -> executeAccess(ctx, JoinPolicy.OPEN)))
                        .then(Commands.literal("invite_only").executes(ctx -> executeAccess(ctx, JoinPolicy.INVITE_ONLY)))
                        .then(Commands.literal("toggle").executes(this::executeAccessToggle))
                )
                // name <newClanName>
                .then(Commands.literal("name")
                        .then(Commands.argument("newClanName", StringArgumentType.greedyString())
                                .executes(this::executeName)
                        )
                        .executes(context -> {
                            context.getSource().sendFailure(Component.literal("Usage: /clan set name <newClanName>"));
                            return 0;
                        })
                )
                // default response
                .executes(context -> {
                    context.getSource().sendFailure(Component.literal("Valid subcommands: color, access, name"));
                    return 0;
                });
    }

    private int executeColor(CommandContext<CommandSourceStack> context) {
        ServerPlayer executor = context.getSource().getPlayer();
        if (executor == null) return 0;
        Clan clan = clanManager.getPlayerClan(executor.getUUID());
        if (!checkClanLeader(executor, clan, context)) return 0;
        assert clan != null; // checkClanLeader handles this case

        String newColor = StringArgumentType.getString(context, "newColorNameOrHex");
        if (newColor == null || newColor.isEmpty()) return 0;

        ChatFormatting formatting = ChatFormatting.getByName(newColor);
        if (formatting != null && formatting.isColor()) {
            newColor = "#" + Integer.toHexString(formatting.getColor()).toUpperCase(Locale.ROOT);
        } else if (newColor.matches("(?i)^#?[0-9a-f]{1,6}$")) {
            newColor = "#" + newColor.replaceFirst("^#", "").toUpperCase(Locale.ROOT);
        } else if ("reset".equalsIgnoreCase(newColor)) {
            newColor = "#" + Integer.toHexString(ChatFormatting.WHITE.getColor()).toUpperCase(Locale.ROOT);
        } else {
            context.getSource().sendFailure(Component.literal(newColor + " is not a valid hex color or minecraft color."));
            return 0;
        }

        String oldColor = clan.hexColor();
        boolean success = clanManager.changeColor(clan.name(), newColor);
        if (!success) {
            context.getSource().sendFailure(Component.literal("Failed to update the color for clan " + clan.name()));
            return 0;
        }

        MutableComponent message = Component.empty();
        TextColor oldClanTextColor = TextColor.parseColor(oldColor).getOrThrow();
        TextColor newClanTextColor = TextColor.parseColor(newColor).getOrThrow();

        message.append(Component.literal("Updated clan color from ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(colorDisplayName(oldColor)).setStyle(Style.EMPTY.withColor(oldClanTextColor)))
                .append(Component.literal(" to ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(colorDisplayName(newColor)).setStyle(Style.EMPTY.withColor(newClanTextColor)))
                .append(Component.literal("!").withStyle(ChatFormatting.GRAY));

        context.getSource().sendSystemMessage(message);
        return 1;
    }

    private String colorDisplayName(String colorString) {
        MinecraftColor color = MinecraftColor.fromColor(Integer.parseInt(colorString.substring(1), 16));
        return color != null ? color.getDisplayName() : colorString;
    }

    private int executeAccess(CommandContext<CommandSourceStack> context, JoinPolicy newPolicy) {
        ServerPlayer executor = context.getSource().getPlayer();
        if (executor == null) return 0;
        Clan clan = clanManager.getPlayerClan(executor.getUUID());
        if (!checkClanLeader(executor, clan, context)) return 0;
        assert clan != null; // checkClanLeader handles this case

        JoinPolicy oldPolicy = clan.isClosed() ? JoinPolicy.INVITE_ONLY : JoinPolicy.OPEN;
        clanManager.changePolicy(clan.name(), newPolicy);

        MutableComponent message = Component.empty()
                .append(Component.literal("Updated clan access from ").withStyle(ChatFormatting.GRAY))
                .append(accessText(oldPolicy != JoinPolicy.OPEN))
                .append(Component.literal(" to ").withStyle(ChatFormatting.GRAY))
                .append(accessText(newPolicy != JoinPolicy.OPEN))
                .append(Component.literal("!").withStyle(ChatFormatting.GRAY));

        context.getSource().sendSystemMessage(message);
        return 1;
    }

    private int executeAccessToggle(CommandContext<CommandSourceStack> context) {
        ServerPlayer executor = context.getSource().getPlayer();
        if (executor == null) return 0;
        Clan clan = clanManager.getPlayerClan(executor.getUUID());
        if (!checkClanLeader(executor, clan, context)) return 0;
        assert clan != null; // checkClanLeader handles this case

        JoinPolicy newPolicy = clan.isClosed() ? JoinPolicy.OPEN : JoinPolicy.INVITE_ONLY;
        return executeAccess(context, newPolicy);
    }

    private MutableComponent accessText(boolean closed) {
        return Component.literal(closed ? "Invite Only" : "Open")
                .withStyle(closed ? ChatFormatting.RED : ChatFormatting.GREEN);
    }

    private int executeName(CommandContext<CommandSourceStack> context) {
        ServerPlayer executor = context.getSource().getPlayer();
        if (executor == null) return 0;
        Clan clan = clanManager.getPlayerClan(executor.getUUID());
        if (!checkClanLeader(executor, clan, context)) return 0;
        assert clan != null; // checkClanLeader handles this case

        String newClanName = StringArgumentType.getString(context, "newClanName");
        if (newClanName.contains(" ")) {
            context.getSource().sendFailure(Component.literal("Clan names must not contain spaces!"));
            return 0;
        }
        if (newClanName.length() < 3 || newClanName.length() > 16) {
            context.getSource().sendFailure(Component.literal("Clan names must be 3-16 characters in length!"));
            return 0;
        }

        boolean clanRenamed = clanManager.changeName(clan.name(), newClanName);
        if (!clanRenamed) {
            context.getSource().sendFailure(Component.literal("Clan " + newClanName + " already exists, or " + newClanName + " isn't an allowed name!"));
            return 0;
        }

        TextColor clanTextColor = TextColor.parseColor(clan.hexColor()).getOrThrow();
        MutableComponent message = Component.literal("Updated clan name from ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(clan.name()).setStyle(Style.EMPTY.withColor(clanTextColor)))
                .append(Component.literal(" to ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(newClanName).setStyle(Style.EMPTY.withColor(clanTextColor)))
                .append(Component.literal("!").withStyle(ChatFormatting.GRAY));

        context.getSource().sendSystemMessage(message);
        return 1;
    }

    private boolean checkClanLeader(ServerPlayer executor, Clan clan, CommandContext<CommandSourceStack> context) {
        if (clan == null) {
            context.getSource().sendFailure(Component.literal("You are not in a clan!"));
            return false;
        }
        if (!clan.leader().equals(executor.getUUID())) {
            context.getSource().sendFailure(Component.literal("You must be a clan leader to use this command!"));
            return false;
        }
        return true;
    }
}