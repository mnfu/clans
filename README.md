# ClanTag

### What is it?
- A fabric mod designed to allow cosmetic groupings of people via "clans"

## Features
- Offline Java Player support for all commands
- Offline Bedrock Player support for all commands - bedrock players must join the server at least once for offline support
- Placeholders via https://github.com/Patbox/TextPlaceholderAPI

## Placeholders
Currently the default value is not configurable, as this was made for a specific server, but it may be in the future.
- `%clantag:player_clan_name%` - in the context of a player, this evaluates to their colorless clan name (White)
- `%clantag:player_clan_name_colored%` - in the context of a player, this evaluates to their colored clan name

## Commands
`/clan` - base command.

### Regular Commands

`/clan create <clanName>` - creates a clan if it doesn't already exist, the name is allowed, and you are not in a clan.

`/clan info <clanName>` - shows info about a certain clan. if clanName is left blank, instead attempts to use your clan.

`/clan invites` - views your current clan invites in a neat list to accept/deny.

`/clan accept <clanName>` - alternative to clicking in chat to accept an invite.

`/clan decline <clanName>` - alternative to clicking in chat to decline an invite.

`/clan leave` - leaves your current clan. If you are a leader of a clan with no other members, this just disbands the clan.

### Clan Leader Commands - Only work for clan leaders (some also only show up for clan leaders)

`/clan invite <playerName>` - invites a player to your clan.

`/clan kick <playerName>` - kicks a player from your clan.

`/clan disband` - sends a message to confirm deletion.

`/clan disband confirm` - deletes your clan (you can run this directly, there is no check that you executed the prior).

`/clan modify color <colorName/hexCode>` - changes the color code of your clan.

`/clan transfer <playerName>` - transfers clan ownership to player if they are a member of your clan.

### Admin Commands - Currently restricted to level 4 operators / console

`/clan admin` - base admin command.

`/clan admin add <playerName> <clanName>` - adds player to the clan if it exists and they are not in one already.

`/clan admin remove <playerName> <clanName>` - removes player from the clan if it exists and they are in it.

`/clan admin delete <clanName>` - deletes clan if it exists.

`/clan admin transfer <playerName> <clanName>` - transfers clan ownership to player if the clan exists and they are a member of it.

`/clan admin reload` - reloads the clans from disk (clans.json). usually useful for manual edits to that file (be careful).

`/clan admin cache clear` - clears the MojangAPI calls cache. usually not very useful outside of very specific situations.


