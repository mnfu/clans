package mnfu.clantag.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import mnfu.clantag.ClanTag;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static mnfu.clantag.commands.CommandUtils.getPlayerName;

public class InfoCommand {
    private final ClanManager clanManager;

    public InfoCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("info")
                .executes(this::executeForSelf)
                .then(CommandManager.argument("clanName", StringArgumentType.word())
                        .suggests((commandContext, suggestionsBuilder) -> {
                            Collection<Clan> clans = clanManager.getAllClans();
                            for (Clan c : clans) {
                                suggestionsBuilder.suggest(c.name());
                            }
                            return suggestionsBuilder.buildFuture();
                        })
                        .executes(this::executeForClanName));
    }

    private int executeForSelf(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity executor = context.getSource().getPlayer();
        if (executor == null) { // not a player if null, assume console
            context.getSource().sendError(Text.literal(ClanTag.FEEDBACK_PREFIX + "You are not a player, so you must specify a clan name."));
            return 0;
        }

        Clan clan = clanManager.getPlayerClan(executor.getUuidAsString());

        if (clan == null) {
            context.getSource().sendError(Text.literal("Clan not found!"));
            return 0;
        }

        displayClanInfo(context, clan);
        return 1;
    }

    private int executeForClanName(CommandContext<ServerCommandSource> context) {
        String clanName = StringArgumentType.getString(context, "clanName");

        Clan clan = clanManager.getClan(clanName);
        if (clan == null) {
            context.getSource().sendError(Text.literal("Clan not found!"));
            return 0;
        }

        displayClanInfo(context, clan);
        return 1;
    }

    private void displayClanInfo(CommandContext<ServerCommandSource> context, Clan clan) {
        MutableText message = Text.empty();

        message.append(Text.literal(clan.name())
                .setStyle(Style.EMPTY.withColor(TextColor.parse(clan.hexColor()).getOrThrow())));

        message.append(Text.literal(" (" + clan.name() + ")").formatted(Formatting.GRAY))
                .append("\n");

        UUID leaderUUID = UUID.fromString(clan.leader());
        String leaderName = getPlayerName(context, leaderUUID).orElse("Unknown Player");

        message.append(Text.literal("Leader: ").formatted(Formatting.WHITE))
                .append(Text.literal(leaderName).formatted(Formatting.YELLOW))
                .append("\n");

        message.append(Text.literal("Members: ").formatted(Formatting.WHITE))
                .append(formatMembersList(context, clan))
                .append("\n");

        message.append(Text.literal("Color: " + clan.hexColor()).formatted(Formatting.WHITE));

        context.getSource().sendMessage(message);
    }

    private MutableText formatMembersList(CommandContext<ServerCommandSource> context, Clan clan) {
        MutableText list = Text.empty();
        List<String> memberUuids = clan.members();

        boolean leaderFound = false;
        for (int i = 0; i < memberUuids.size(); i++) {
            String uuidStr = memberUuids.get(i);
            UUID uuid = UUID.fromString(uuidStr);
            String name = getPlayerName(context, uuid).orElse("Unknown Player");

            MutableText nameText = Text.literal(name);

            Formatting format = Formatting.WHITE; // default member color
            if (!leaderFound && uuidStr.equals(clan.leader())) {
                format = Formatting.YELLOW; // leader color
                leaderFound = true;
            }
            nameText.formatted(format);

            list.append(nameText);

            if (i < memberUuids.size() - 1) {
                list.append(Text.literal(", ").formatted(Formatting.GRAY));
            }
        }
        return list;
    }
}
