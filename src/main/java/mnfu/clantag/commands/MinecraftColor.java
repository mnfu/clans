package mnfu.clantag.commands;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public enum MinecraftColor {
    BLACK(0x000000),
    DARK_BLUE(170),
    DARK_GREEN(43520),
    DARK_AQUA(43690),
    DARK_RED(0xAA0000),
    DARK_PURPLE(0xAA00AA),
    GOLD(0xFFAA00),
    GRAY(0xAAAAAA),
    DARK_GRAY(0x555555),
    BLUE(0x5555FF),
    GREEN(0x55FF55),
    AQUA(0x55FFFF),
    RED(0xFF5555),
    LIGHT_PURPLE(0xFF55FF),
    YELLOW(0xFFFF55),
    WHITE(0xFFFFFF);

    private final int color;

    MinecraftColor(int color) {
        this.color = color;
    }

    /** Raw RGB value (0xRRGGBB) */
    public int getColor() {
        return color;
    }

    /** "Dark Aqua", "Light Purple", etc */
    public String getDisplayName() {
        String[] parts = name().toLowerCase(Locale.ROOT).split("_");
        return Arrays.stream(parts)
                .map(p -> Character.toUpperCase(p.charAt(0)) + p.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static final Map<Integer, MinecraftColor> BY_COLOR =
            Arrays.stream(values())
                    .collect(Collectors.toMap(MinecraftColor::getColor, c -> c));

    public static MinecraftColor fromColor(int color) {
        return BY_COLOR.get(color);
    }
}
