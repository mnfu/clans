package mnfu.clantag;

import java.util.List;

public record Clan(String name, String leader, List<String> members, String hexColor) {
}
