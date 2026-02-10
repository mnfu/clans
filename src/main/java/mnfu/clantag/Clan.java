package mnfu.clantag;

import java.util.LinkedHashSet;

public record Clan(String name, String leader, LinkedHashSet<String> members, String hexColor) {
}
