package mnfu.clantag.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import net.minecraft.server.players.PlayerList;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;

import java.util.Map;
import java.util.UUID;

public class JoinCommand {
    private final ClanManager clanManager;

    public JoinCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("join")
                .then(Commands.argument("clanName", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            for (Map.Entry<String, Clan> entry : clanManager.getClansMap().entrySet()) {
                                if (!entry.getValue().isClosed()) builder.suggest(entry.getKey());
                            }
                            return builder.buildFuture();
                        })
                        .executes(this::executeJoin)
                );
    }

    private int executeJoin(CommandContext<CommandSourceStack> context) {
        ServerPlayer executor = context.getSource().getPlayer();
        if (executor == null) return 0;
        Clan clan = clanManager.getPlayerClan(executor.getUUID());
        if (clan != null) {
            context.getSource().sendFailure(Component.literal("You must leave your current clan to join a new one!"));
            return 0;
        }
        String newClanName = StringArgumentType.getString(context, "clanName");
        Clan newClan = clanManager.getClan(newClanName);
        if (newClan == null) {
            context.getSource().sendFailure(Component.literal("Clan not found!"));
            return 0;
        }
        if (newClan.isClosed()) {
            context.getSource().sendFailure(Component.literal(newClan.name() + " is currently invite only!"));
            return 0;
        } else {
            clanManager.addMember(newClanName, executor.getUUID());

            PlayerList pm = context.getSource().getServer().getPlayerList();
            for (UUID member : newClan.members()) {
                ServerPlayer player = pm.getPlayer(member);
                if (player != null) {
                    player.sendSystemMessage(Component.literal(executor.getName().getString() + " joined the clan!"));
                }
            }

            MutableComponent message = Component.literal("You've joined ")
                    .withStyle(ChatFormatting.GREEN);
            message.append(Component.literal(newClan.name())
                    .setStyle(Style.EMPTY.withColor(TextColor.parseColor(newClan.hexColor()).getOrThrow())));
            message.append(Component.literal("!").withStyle(ChatFormatting.GREEN));
            context.getSource().sendSystemMessage(message);
            return 1;
        }
    }
}
