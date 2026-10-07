# Changelog

## S3 Progression 0.8.0

### Research
- **Research needed depends on team size:** each tier needs 512 points for 1–2 players, plus 128 for every further player, up to 3200 points at 23 players.
  - Owner, officers and members count; allies and invites don't.
  - A player joining raises the requirement immediately.
  - A player leaving lowers it by at most one step per real-time midnight. If 7 players leave, it takes 7 days to settle.
- **Inactive players:** players who haven't been online for 14 days no longer count towards their team's size. The requirement then drops by one step per midnight, like when a player leaves. They count again as soon as they log in.
- **A tier unlocks on exactly the required point** (512/512), not one point later.
- **Unlocked tiers stay unlocked**, even if the requirement rises later.
- **Free research:** every 20 minutes of server uptime, each team with an active laboratory gets 1 research point, plus 1 more per 10 players in the team. The lab has to be able to research the team's current tier. If free points (or a shrinking team) reach the requirement, the tier unlocks with your next lab craft.

### Laboratories
- **Upgrading labs:** placing a higher-tier laboratory makes it your active lab and switches the old one to "out of order". Same-tier or lower-tier labs are still decorative.
- **Better "out of order" messages:** the message now names the dimension of your active lab. It also tells you when your team has no active lab anymore (break the lab and place it again).
- **Lost labs fix themselves:** if your recorded active lab no longer exists, the next lab you place becomes active.
- **Breaking:** laboratories and the Primitive Assembler can be broken by hand like wood, faster with a GregTech wrench.
- **Bug fix:** laboratories now drop themselves when broken. Before, they dropped nothing.
- **Recipe changes:**
  - Industrial Laboratory: a glass tube and 2 small brass gears replace the precision mechanism, so it can be built right after the Iron research.
  - Elite Laboratory: titanium plates replace tungstensteel plates, so it no longer needs EV equipment.

### Science recipes
- **New science recipe set:** 110 recipes, the Empty Science recipe plus 2 recipes per category for each tier. The item value per science item follows the ratio Mining 1 : Farming 5 : Exploration 25 : Production 50 : Challenge 100.
- New item tags let recipes accept any of several equivalent items, for example any cheese or any sandwich.
- Every Primitive Assembler and GT assembler recipe fits into the machine's 9 slots, based on the pack's real stack sizes.

### Commands (OP)
- **New `/progression members <team>`:** shows each member's last online time and whether they count as inactive.
- **New `/progression clearlab <team>`:** removes a stale active-lab record.
- **`/progression teams`** (open to everyone) now shows points, the required points, the counted team size and inactive players.
- **`/progression set`** works with the new unlock system.
- **Team names** with `#` work without quotes, and trailing spaces are ignored.

### Admin notes
- **Upload to the live server:**
  - `mods/S3_progression_mod-0.8.0.jar` (delete the 0.7.5 jar)
  - `config/s3_progression_mod/progression.json` (the format changed: per-tier `threshold` removed, `teamSize` and `passiveResearch` added)
  - `kubejs/startup_scripts/s3_progression_mod/progression_listener.js`
  - `kubejs/server_scripts/s3_progression_mod/progression_commands.js`
  - `kubejs/server_scripts/s3_progression_mod/science_recipes.js`
- **Restart the server.** `/reload` isn't enough.
- **First start:** last-online times are imported once from FTB Essentials. Players without data there start their 14 days at that moment.
- **Existing teams:** a team's unlocks are taken over once from its current points (more than 1024 = unlocked). Teams from 0.3 still need `/progression set`.
- **Free research on older labs:** labs placed before 0.8.0 only start earning free points after their chunk has been loaded once.
- **Pack update warning:** after a pack update, re-apply the four commented-out blocks in the pack scripts (see `tfg_tweaks.js`).
