package mnfu.clantag.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HelpCommand {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 150; // 0.15 second cooldown
    private static final long CLEANUP_MS = 60000; // 60 second cleanup

    private boolean cooldown(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return false;
        UUID playerId = player.getUuid();
        long now = System.currentTimeMillis();

        cooldowns.entrySet().removeIf(entry -> now - entry.getValue() > CLEANUP_MS);

        Long lastClick = cooldowns.get(playerId);
        if (lastClick != null && now - lastClick < COOLDOWN_MS) {
            source.sendError(Text.literal("Please wait before clicking again"));
            return true;
        }

        cooldowns.put(playerId, now);
        return false;
    }

    public LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("help")
                .executes(this::executeGeneral) // default to "general commands" page
                .then(CommandManager.literal("general").executes(this::executeGeneral))
                .then(CommandManager.literal("manage").executes(this::executeManage))
                .then(CommandManager.literal("admin").executes(this::executeAdmin));
    }

    // HELP PAGE FOR GENERAL COMMANDS
    public int executeGeneral(CommandContext<ServerCommandSource> context) {
        if (cooldown(context.getSource())) return 0;
        MutableText message = Text.empty();

        message.append(Text.literal("[ General Commands ]").formatted(Formatting.WHITE)).append("\n");
        message.append(Text.literal("/clan help [pageName]").formatted(Formatting.YELLOW)).append(" - Shows a help menu page (defaults to this page if [pageName] isn't chosen)").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan create <clanName>").formatted(Formatting.YELLOW)).append(" - Creates a clan if it doesn't already exist").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan info <clanName>").formatted(Formatting.YELLOW)).append(" - Shows info about a clan, and if <clanName> is blank, it shows your clan").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan invites").formatted(Formatting.YELLOW)).append(" - Displays your current clan invites to accept/decline").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan accept <clanName>").formatted(Formatting.YELLOW)).append(" - Accepts a clan invite").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan decline <clanName>").formatted(Formatting.YELLOW)).append(" - Declines a clan invite").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan join <clanName>").formatted(Formatting.YELLOW)).append(" - Joins a clan if it is open").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan leave").formatted(Formatting.YELLOW)).append(" - Leaves your current clan. If you are the last remaining member, it disbands the clan as well").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("[Management Help Page]")
                .styled(style -> style
                        .withColor(Formatting.GOLD)
                        .withClickEvent(new ClickEvent.RunCommand("/clan help manage"))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to view management commands"))))
        );
        message.append(Text.literal(" | ").formatted(Formatting.GRAY));
        message.append(Text.literal("[Admin Help Page]")
                .styled(style -> style
                        .withColor(Formatting.GOLD)
                        .withClickEvent(new ClickEvent.RunCommand("/clan help admin"))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to view admin commands"))))
        );
        message.append("\n");
        context.getSource().sendMessage(message);
        return 1;
    }

    // HELP PAGE FOR MANAGEMENT COMMANDS
    private int executeManage(CommandContext<ServerCommandSource> context) {
        if (cooldown(context.getSource())) return 0;
        MutableText message = Text.empty();

        message.append(Text.literal("[ Management Commands ]").formatted(Formatting.WHITE)).append("\n");
        message.append(Text.literal("/clan invite <playerName>").formatted(Formatting.YELLOW)).append(" - Invites <playerName> to your clan").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan kick <playerName>").formatted(Formatting.YELLOW)).append(" - Kicks <playerName> from your clan").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan promote <playerName>").formatted(Formatting.YELLOW)).append(" - Promotes a player in the clan rank hierarchy").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan demote <playerName>").formatted(Formatting.YELLOW)).append(" - Demotes a player in the clan rank hierarchy").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan disband [confirm]").formatted(Formatting.YELLOW)).append(" - Facilitates the deletion of your clan").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan set color <colorName|hexCode>").formatted(Formatting.YELLOW)).append(" - Sets your clan color (supports \"WHITE\" or #FFFFFF or FFFFFF formats)").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan set access <open|invite_only|toggle>").formatted(Formatting.YELLOW)).append(" - Sets your clan access to open or invite only").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan set name <newClanName>").formatted(Formatting.YELLOW)).append(" - Sets your clan name to <newClanName> if it is available").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan transfer <playerName>").formatted(Formatting.YELLOW)).append(" - Transfers clan ownership to <playerName>").formatted(Formatting.GRAY).append("\n");

        message.append(Text.literal("[General Help Page]")
                .styled(style -> style
                        .withColor(Formatting.GOLD)
                        .withClickEvent(new ClickEvent.RunCommand("/clan help general"))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to view general commands"))))
        );
        message.append(Text.literal(" | ").formatted(Formatting.GRAY));
        message.append(Text.literal("[Admin Help Page]")
                .styled(style -> style
                        .withColor(Formatting.GOLD)
                        .withClickEvent(new ClickEvent.RunCommand("/clan help admin"))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to view admin commands"))))
        );
        message.append("\n");
        context.getSource().sendMessage(message);
        return 1;
    }

    // HELP PAGE FOR ADMIN COMMANDS
    private int executeAdmin(CommandContext<ServerCommandSource> context) {
        if (cooldown(context.getSource())) return 0;
        MutableText message = Text.empty();

        message.append(Text.literal("[ Admin Commands ]").formatted(Formatting.WHITE)).append("\n");
        message.append(Text.literal("/clan admin add <playerName> <clanName>").formatted(Formatting.YELLOW)).append(" - Adds <playerName> to a clan").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan admin remove <playerName> <clanName>").formatted(Formatting.YELLOW)).append(" - Removes <playerName> from a clan").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan admin delete <clanName>").formatted(Formatting.YELLOW)).append(" - Deletes a clan").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan admin rename <\"clanName\"> <newClanName>").formatted(Formatting.YELLOW)).append(" - Renames a clan (Overrides length limitations)").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan admin transfer <playerName> <clanName>").formatted(Formatting.YELLOW)).append(" - Transfers clan ownership to <playerName>").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan admin reload").formatted(Formatting.YELLOW)).append(" - Reloads clans.json from disk").formatted(Formatting.GRAY).append("\n");
        message.append(Text.literal("/clan admin cache clear").formatted(Formatting.YELLOW)).append(" - Clears the MojangAPI Cache").formatted(Formatting.GRAY).append("\n");

        message.append(Text.literal("[General Help Page]")
                .styled(style -> style
                        .withColor(Formatting.GOLD)
                        .withClickEvent(new ClickEvent.RunCommand("/clan help general"))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to view general commands"))))
        );
        message.append(Text.literal(" | ").formatted(Formatting.GRAY));
        message.append(Text.literal("[Management Help Page]")
                .styled(style -> style
                        .withColor(Formatting.GOLD)
                        .withClickEvent(new ClickEvent.RunCommand("/clan help manage"))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to view management commands"))))
        );
        message.append("\n");
        context.getSource().sendMessage(message);
        return 1;
    }

}
