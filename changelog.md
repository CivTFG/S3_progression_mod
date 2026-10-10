# Changelog

## S3 Progression 0.9.0

### Research balance
- **Same time per tier for every team size:** a tier takes about 20 hours with all 5 science types, whether the team has 1 or 23 players. Three changes work together:
  - **New threshold curve:** the points needed now follow a curve instead of a straight line. The formula is `round(512 + 160.3 × (players − 1)^0.8375)`.

    | Players | 1 | 2 | 3 | 5 | 10 | 15 | 20 | 23 |
    |---|---|---|---|---|---|---|---|---|
    | Points needed | 512 | 672 | 798 | 1,024 | 1,522 | 1,974 | 2,400 | 2,646 |
    | Lab craft time | 40:00 | 30:29 | 25:40 | 20:00 | 13:28 | 10:23 | 8:32 | 7:45 |
    | Free points / 40 min | 1.00 | 1.31 | 1.56 | 2.00 | 2.97 | 3.86 | 4.69 | 5.17 |

  - **Lab craft time depends on team size:** a solo team's craft takes 40 minutes (was 20 for everyone), and bigger teams craft faster (40 min × 512 / points needed before discount). The lab countdown shows each team's own time.
  - **Free research scales with the threshold:** every 40 minutes of server uptime (was 20), a team with an active laboratory gets (points needed before discount) / 512 free points. Fractions carry over to the next interval, so a 2-player team gets 1.31 points per interval on average.
- **Catch-up discount:** for every other team that has already researched the tier you are working on, you need 10% fewer points, up to 40% at 4 teams. Example: you research the Bronze Age and one team has already reached the Iron Age, so you need 10% fewer points; a second team makes it 20%.
  - Solo teams and party teams count.
  - The discount never goes back down, even if a team disbands.
  - It only lowers the points needed. Lab craft time and free points stay the same, so the discount really makes you faster.
  - Your team gets a chat message when its discount rises. The lab and `/progression teams` show the discounted requirement and the discount.
  - If the discount brings your total above the requirement, the tier unlocks with your next lab craft.
- Unchanged: points per craft (1, 2, 4, 8, 16 for 1–5 science types), unlock on the next lab craft, permanent unlocks, the team-size rules (inactive players, one step down per midnight) and the lab tier check for free points.

### Admin notes
- **Upload to the live server:**
  - `mods/S3_progression_mod-0.9.0.jar` (delete the 0.8.0 jar)
  - `config/s3_progression_mod/progression.json` (format changed: `teamSize` uses `curveFactor`/`curveExponent` instead of `pointsPerPlayer`/`freePlayers`, the new `labCraft` and `discount` blocks, and `passiveResearch` uses `referenceThreshold` instead of `points`/`playersPerExtraPoint`)
  - `kubejs/startup_scripts/s3_progression_mod/progression_listener.js`
  - `kubejs/server_scripts/s3_progression_mod/progression_commands.js` (shows the discount)
- **Restart the server.** `/reload` isn't enough.
- An old 0.8.0 `progression.json` still loads: the missing values fall back to the new defaults and the log shows a warning. Your `inactiveAfterDays` setting is kept.
- **Existing teams:** totals and unlocks stay as they are. Solo teams need the same 512 points, teams of 2–9 players now need more; teams of 10 or more need fewer and unlock on their next lab craft if they are already above the new threshold.
- **Discount on day one:** teams that are already behind get their discount right away, counted from the teams that are already ahead.
- **Running lab crafts** keep their progress and finish once they reach the team's new craft time.

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
