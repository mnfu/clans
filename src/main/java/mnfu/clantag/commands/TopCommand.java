package mnfu.clantag.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mnfu.clantag.Clan;
import mnfu.clantag.ClanManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.*;

import java.util.*;

public class TopCommand {
    private final ClanManager clanManager;

    // before opening chat minecraft displays 10 lines, im reserving 1 for header, 1 for footer, so 8 content lines.
    private static final int PAGE_SIZE = 8;
    private static final long CACHE_TTL = 1000 * 60; // 1 minute

    private List<Clan> cachedRanking = List.of();
    private final Map<Integer, Component> cachedPages = new HashMap<>();
    private long timeOfCache = 0;

    private void refreshIfExpired() {
        long now = System.currentTimeMillis();
        if (now - timeOfCache > CACHE_TTL) {
            cachedRanking = clanManager.getAllClans().stream()
                    .sorted(Comparator.comparingInt(this::score).reversed())
                    .toList();
            cachedPages.clear();
            timeOfCache = now;
        }
    }

    private Component getPage(int page) {
        int totalPages = Math.max(1, (int) Math.ceil(cachedRanking.size() / (double) PAGE_SIZE));
        int clamped = Math.min(page, totalPages);
        return cachedPages.computeIfAbsent(clamped, p -> buildPage(cachedRanking, p, totalPages));
    }

    public TopCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build(){
        return Commands.literal("top")
                .executes(ctx -> execute(ctx, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx ->
                                execute(ctx, IntegerArgumentType.getInteger(ctx, "page"))));
    }

    public int execute(CommandContext<CommandSourceStack> context, int page){
        if (!context.getSource().isPlayer()) {
            context.getSource().sendFailure(Component.literal("Only players may execute this command"));
            return 0;
        }

        refreshIfExpired();
        context.getSource().sendSystemMessage(getPage(page));
        return 1;
    }

    private List<Clan> getRanking() {
        long now = System.currentTimeMillis();
        if (now - timeOfCache > CACHE_TTL) {
            cachedRanking = clanManager.getAllClans().stream()
                    .sorted(Comparator.comparingInt(this::score).reversed())
                    .toList();
            timeOfCache = now;
        }
        return cachedRanking;
    }

    private int score(Clan clan) {
        return clan.members().size();
    }

    private Component buildPage(List<Clan> ranking, int page, int totalPages) {
        MutableComponent message = Component.literal("Top Clans")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" - by member count").withStyle(ChatFormatting.GRAY));

        if (ranking.isEmpty()) {
            return message.append(Component.literal("\nNo clans yet.").withStyle(ChatFormatting.GRAY));
        }

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, ranking.size());

        for (int i = start; i < end; i++) {
            Clan clan = ranking.get(i);
            message.append(Component.literal("\n" + (i + 1) + ". ").withStyle(ChatFormatting.GRAY))
                    .append(clanName(clan))
                    .append(Component.literal(" (" + score(clan) + ")").withStyle(ChatFormatting.DARK_GRAY));
        }

        return message.append("\n").append(buildFooter(page, totalPages));
    }

    private Component buildFooter(int page, int totalPages) {
        MutableComponent footer = Component.empty();

        footer.append(page > 1
                ? button("◀", "/clan top " + (page - 1))
                : Component.literal("◀").withStyle(ChatFormatting.DARK_GRAY));

        footer.append(Component.literal(" Page " + page + "/" + totalPages + " ")
                .withStyle(ChatFormatting.YELLOW));

        footer.append(page < totalPages
                ? button("▶", "/clan top " + (page + 1))
                : Component.literal("▶").withStyle(ChatFormatting.DARK_GRAY));

        return footer;
    }

    private Component button(String label, String command) {
        return Component.literal(label).withStyle(
                Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Go to page"))));
    }

    private Component clanName(Clan clan) {
        String canonical = clanManager.getCanonicalName(clan);
        TextColor clanColor = TextColor.parseColor(clan.hexColor()).getOrThrow();

        return Component.literal(clan.name()).withStyle(
                Style.EMPTY
                        .withColor(clanColor)
                        .withClickEvent(new ClickEvent.RunCommand("/clan info name " + canonical))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to view clan info"))));
    }
}
