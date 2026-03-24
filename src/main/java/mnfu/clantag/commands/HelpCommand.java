package mnfu.clantag.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class HelpCommand {

    private final MutableComponent generalMessage;
    private final MutableComponent manageMessage;
    private final MutableComponent adminMessage;

    public HelpCommand() {
        generalMessage = buildGeneralMessage();
        manageMessage = buildManageMessage();
        adminMessage = buildAdminMessage();
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("help")
                .executes(this::executeGeneral) // default to "general commands" page
                .then(Commands.literal("general").executes(this::executeGeneral))
                .then(Commands.literal("manage").executes(this::executeManage))
                .then(Commands.literal("admin").executes(this::executeAdmin));
    }

    // HELP PAGE FOR GENERAL COMMANDS
    public int executeGeneral(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSystemMessage(generalMessage);
        return 1;
    }

    // HELP PAGE FOR MANAGEMENT COMMANDS
    private int executeManage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSystemMessage(manageMessage);
        return 1;
    }

    // HELP PAGE FOR ADMIN COMMANDS
    private int executeAdmin(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSystemMessage(adminMessage);
        return 1;
    }

    public MutableComponent buildGeneralMessage () {
        MutableComponent message = Component.empty();
        message.append(Component.literal("[ General Commands ]").withStyle(ChatFormatting.WHITE)).append("\n");
        message.append(Component.literal("/clan help [pageName]").withStyle(ChatFormatting.YELLOW)).append(" - Shows a help menu page (defaults to this page if [pageName] isn't chosen)").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan create <clanName>").withStyle(ChatFormatting.YELLOW)).append(" - Creates a clan if it doesn't already exist").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan info [name|player] <clanName|playerName>").withStyle(ChatFormatting.YELLOW)).append(" - Shows info about a clan, defaults to your clan if no arguments given").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan invites").withStyle(ChatFormatting.YELLOW)).append(" - Displays your current clan invites to accept/decline").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan accept <clanName>").withStyle(ChatFormatting.YELLOW)).append(" - Accepts a clan invite").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan decline <clanName>").withStyle(ChatFormatting.YELLOW)).append(" - Declines a clan invite").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan join <clanName>").withStyle(ChatFormatting.YELLOW)).append(" - Joins a clan if it is open").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan leave").withStyle(ChatFormatting.YELLOW)).append(" - Leaves your current clan. If you are the last remaining member, it disbands the clan as well").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("[Management Help Page]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent.RunCommand("/clan help manage"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to view management commands"))))
        );
        message.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
        message.append(Component.literal("[Admin Help Page]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent.RunCommand("/clan help admin"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to view admin commands"))))
        );
        message.append("\n");
        return message;
    }

    public MutableComponent buildManageMessage () {
        MutableComponent message = Component.empty();
        message.append(Component.literal("[ Management Commands ]").withStyle(ChatFormatting.WHITE)).append("\n");
        message.append(Component.literal("/clan invite <playerName>").withStyle(ChatFormatting.YELLOW)).append(" - Invites <playerName> to your clan").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan kick <playerName>").withStyle(ChatFormatting.YELLOW)).append(" - Kicks <playerName> from your clan").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan promote <playerName>").withStyle(ChatFormatting.YELLOW)).append(" - Promotes a player in the clan rank hierarchy").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan demote <playerName>").withStyle(ChatFormatting.YELLOW)).append(" - Demotes a player in the clan rank hierarchy").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan disband [confirm]").withStyle(ChatFormatting.YELLOW)).append(" - Facilitates the deletion of your clan").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan set color <colorName|hexCode>").withStyle(ChatFormatting.YELLOW)).append(" - Sets your clan color (supports \"WHITE\" or #FFFFFF or FFFFFF formats)").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan set access [open|invite_only|toggle]").withStyle(ChatFormatting.YELLOW)).append(" - Sets your clan access to open or invite only").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan set name <newClanName>").withStyle(ChatFormatting.YELLOW)).append(" - Sets your clan name to <newClanName> if it is available").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan transfer <playerName>").withStyle(ChatFormatting.YELLOW)).append(" - Transfers clan ownership to <playerName>").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("[General Help Page]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent.RunCommand("/clan help general"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to view general commands"))))
        );
        message.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
        message.append(Component.literal("[Admin Help Page]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent.RunCommand("/clan help admin"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to view admin commands"))))
        );
        message.append("\n");
        return message;
    }

    public MutableComponent buildAdminMessage () {
        MutableComponent message = Component.empty();
        message.append(Component.literal("[ Admin Commands ]").withStyle(ChatFormatting.WHITE)).append("\n");
        message.append(Component.literal("/clan admin add <playerName> <clanName>").withStyle(ChatFormatting.YELLOW)).append(" - Adds <playerName> to a clan").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan admin remove <playerName> <clanName>").withStyle(ChatFormatting.YELLOW)).append(" - Removes <playerName> from a clan").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan admin delete <clanName>").withStyle(ChatFormatting.YELLOW)).append(" - Deletes a clan").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan admin rename <\"clanName\"> <newClanName>").withStyle(ChatFormatting.YELLOW)).append(" - Renames a clan (Overrides length limitations)").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan admin transfer <playerName> <clanName>").withStyle(ChatFormatting.YELLOW)).append(" - Transfers clan ownership to <playerName>").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan admin reload").withStyle(ChatFormatting.YELLOW)).append(" - Reloads clans.json from disk").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("/clan admin cache clear").withStyle(ChatFormatting.YELLOW)).append(" - Clears the MojangAPI Cache").withStyle(ChatFormatting.GRAY).append("\n");
        message.append(Component.literal("[General Help Page]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent.RunCommand("/clan help general"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to view general commands"))))
        );
        message.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
        message.append(Component.literal("[Management Help Page]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent.RunCommand("/clan help manage"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to view management commands"))))
        );
        message.append("\n");
        return message;
    }
}
