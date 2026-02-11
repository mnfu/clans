package mnfu.clantag;

import java.util.LinkedHashSet;
import java.util.UUID;

public record Clan(String name, UUID leader, LinkedHashSet<UUID> members, String hexColor) {
}
