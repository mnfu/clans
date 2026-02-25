# ClanTag

### What is it?
- A fabric mod designed to allow cosmetic groupings of people via "clans"

## Features
- Offline Java Player support for all commands
- Offline Bedrock Player support for all commands - bedrock players must join the server at least once for offline support
- Placeholders via https://github.com/Patbox/TextPlaceholderAPI
- Permissions via https://github.com/LuckPerms/LuckPerms

## Requires
- LuckPerms: https://luckperms.net/download

---

## Placeholders
Currently the default value is not configurable, as this was made for a specific server, but it may be in the future.

| Placeholder | Description |
|-------------|-------------|
| `%clantag:player_clan_name%` | Evaluates to a player's colorless clan name (white) |
| `%clantag:player_clan_name_colored%` | Evaluates to a player's colored clan name |

---

## Commands - `Updated as of 1.1.0-dev`

### Regular Commands

| Command | Description |
|---------|-------------|
| `/clan` | Base command. |
| `/clan create <clanName>` | Creates a clan if it doesn't already exist, the name is allowed, and you are not in a clan. |
| `/clan info <clanName>` | Shows info about a certain clan. If blank, attempts to use your clan. |
| `/clan invites` | Views your current clan invites in a neat list to accept/decline. |
| `/clan accept <clanName>` | Alternative to clicking in chat to accept an invite. |
| `/clan decline <clanName>` | Alternative to clicking in chat to decline an invite. |
| `/clan join <clanName>` | Joins a clan if it is open to joins. |
| `/clan leave` | Leaves your current clan. If you are the leader with no other members, disbands the clan. |

### Clan Officer Commands - Restricted to Officers and Above

| Command | Description |
|---------|-------------|
| `/clan invite <playerName>` | Invites a player to your clan. |
| `/clan kick <playerName>` | Kicks a player from your clan. |

### Clan Leader Commands - Restricted to Leaders

| Command                                        | Description                                             |
|------------------------------------------------|---------------------------------------------------------|
| `/clan promote <playerName>`                   | Promotes a player to Officer rank.                      |
| `/clan demote <playerName>`                    | Demotes a player to Member rank.                        |
| `/clan disband`                                | Sends a confirmation message for deletion.              |
| `/clan disband confirm`                        | Deletes your clan directly.                             |
| `/clan set color <colorName/hexCode>`          | Changes the color code of your clan.                    |
| `/clan set access <open\|invite_only\|toggle>` | Changes your clan’s access state. |
| `/clan set name <newClanName>`                 | Changes the name of your clan if allowed and available. |
| `/clan transfer <playerName>`                  | Transfers clan ownership to a member.                   |

### Admin Commands - Restricted to OP/Console

| Command                                         | Description                                                          | Permission Node |
|-------------------------------------------------|----------------------------------------------------------------------|------|
| `/clan admin`                                   | Base admin command.| `N/A`|
| `/clan admin *`                                 | Grants access to **all admin subcommands**. Does **not** require `/clan admin` itself.| `clantag.admin.*` |
| `/clan admin add <playerName> <clanName>`       | Adds a player to a clan if it exists and they are not in one already. | `clantag.admin.add`|
| `/clan admin remove <playerName> <clanName>`    | Removes a player from a clan if they are in it.| `clantag.admin.remove`|
| `/clan admin delete <clanName>`                 | Deletes a clan if it exists.| `clantag.admin.delete`|
| `/clan admin rename <"clanName"> <newClanName>` | Renames a clan if allowed and not taken.| `clantag.admin.rename`|
| `/clan admin transfer <playerName> <clanName>`  | Transfers clan ownership to a member.| `clantag.admin.transfer`|
| `/clan admin reload`                            | Reloads clans from disk (`clans.json`).| `clantag.admin.reload`|
| `/clan admin cache clear`                       | Clears the MojangAPI cache.| `clantag.admin.cache`|
