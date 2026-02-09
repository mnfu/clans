# TODO:
- [ ] Allow the changing of clan hex color, currently you can only set this upon clan creation
- [ ] Integrate with Placeholder API, we don't want to mess with chat or tablist shenanigans ourselves. https://placeholders.pb4.eu/#for-developers
- [ ] Restrict /cl reload to operators only
- [ ] Add check to prevent non-leaders from changing things in the clan, like color, members, etc.
- [ ] Decide what to do when a leader tries to remove themselves, it could be funny to outright delete the clan in this scenario, but it should probably just fail. They should be required to run /c delete or something.
- [ ] Implement the clan transfer command, it has backend support already in ClanManager.java
- [ ] Prevent players from joining multiple clans, and from creating clans while they're in a clan


# Planned Commands
### If a command is currently in-game and not listed here, it is a WIP or development command.

- **Player Commands** - no restriction
- [ ] /clan create \<clanName> \<hexColor> - create a new clan. If hexcolor is left blank, set to #FFFFFF. Can use Minecraft's default color names in place of hex
- [x] /clan leave - leave a clan. If executor is not in a clan, do nothing. If in a clan as a member, leave. If a leader, do nothing.
- [ ] /clan or /clan help - displays all available commands

- **Clan Leader Commands** - restricted to clan leaders
- [ ] /clan invite \<playerName> - send an invite to another online player to join a clan
- [ ] /clan color \<hexColor> - change the hexcolor of a clan if the executor is the leader of one
- [x] /clan kick \<playerName> - kicks a player from the clan (assuming the executor is the leader of one)
- [ ] /clan disband - delete a clan entry if the executor is the leader of one.
- [ ] /clan transfer - transfer leadership of a clan to an existing member of the clan. Possibly send a confirmation, but not required.
- [ ] /clan info - prints info about the clan
  - TODO reduce api calls to mojang by keeping responses we asked for recently
- **Operator Commands** - restricted to server operators
- [x] /clan reload - reload the clans.yml for reloading manual changes live