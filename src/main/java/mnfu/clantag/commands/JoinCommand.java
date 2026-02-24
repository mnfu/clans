package mnfu.clantag.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.UUID;

public class JoinCommand {
    private final ClanManager clanManager;

    public JoinCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("join")
                .then(CommandManager.argument("clanName", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            for (Clan c : clanManager.getAllClans()) {
                                if (!c.isClosed()) builder.suggest(c.name());
                            }
                            return builder.buildFuture();
                        })
                        .executes(this::executeJoin)
                );
    }

    private int executeJoin(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor == null) return 0;
        Clan clan = clanManager.getPlayerClan(executor.getUuid());
        if (clan != null) {
            context.getSource().sendError(Text.literal("You must leave your current clan to join a new one!"));
            return 0;
        }
        String newClanName = StringArgumentType.getString(context, "clanName");
        Clan newClan = clanManager.getClan(newClanName);
        if (newClan == null) {
            context.getSource().sendError(Text.literal("Clan not found!"));
            return 0;
        }
        if (newClan.isClosed()) {
            context.getSource().sendError(Text.literal(newClan.name() + " is currently invite only!"));
            return 0;
        } else {
            clanManager.addMember(newClanName, executor.getUuid());

            PlayerManager pm = context.getSource().getServer().getPlayerManager();
            for (UUID member : newClan.members()) {
                ServerPlayerEntity player = pm.getPlayer(member);
                if (player != null) {
                    player.sendMessage(Text.literal(executor.getName().getString() + " joined the clan!"));
                }
            }

            MutableText message = Text.literal("You've joined ")
                    .formatted(Formatting.GREEN);
            message.append(Text.literal(newClan.name())
                    .setStyle(Style.EMPTY.withColor(TextColor.parse(newClan.hexColor()).getOrThrow())));
            message.append(Text.literal("!").formatted(Formatting.GREEN));
            context.getSource().sendMessage(message);
            return 1;
        }
    }
}
