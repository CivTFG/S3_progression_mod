# Handoff: S3 Progression Mod

Diese Datei ist für eine neue Claude-Instanz gedacht, die die Entwicklung dieses Mods
fortsetzt. Sie enthält alles, was in mehreren Sessions an Fallstricken, Design-Entscheidungen
und Konfigurationsmöglichkeiten gefunden wurde — Dinge, die im `README.md` nicht (oder nicht
mit dieser Deutlichkeit) stehen. **Lies zuerst `README.md`** (Grundfunktion, Installation,
Rezept-Editor-Tool), diese Datei ergänzt es um die "warum ist das so" und "worauf musst du
aufpassen"-Ebene.

Stand: 2026-09-10, nach Commit `8a20e3f` ("Fixed exception when placing lab on server").

## 1. Was der Mod tut (Kurzfassung über README hinaus)

Ein **Laboratory**-Block, in den Teams bis zu 5 verschiedene "Science"-Items derselben Age
stecken. Jede Age hat 5 Kategorien (mining/farming/production/exploration/challenge) — wie
viele *unterschiedliche* Kategorien gleichzeitig drin liegen, bestimmt den Wert: 1, 2, 4, 8
oder 16 (`2^(n-1)`), nicht die Anzahl der Items. Ein Zwei-Kategorien-Rezept ist also mehr wert
als zwei Ein-Kategorie-Rezepte hintereinander — das ist Absicht (Diversität belohnen).

Fortschritt wird nicht pro Spieler, sondern pro **FTB Team** gespeichert (im Team-NBT unter
`s3_progression_mod:research`). Jede Age hat einen Schwellenwert; ist er überschritten, kriegt
jedes online Team-Mitglied eine GameStage (z.B. `bronze_unlocked`). Diese Stage schaltet dann
Gates frei (Blöcke, die man erst benutzen/platzieren/besitzen darf, wenn man die Stage hat).

## 2. Kritische Fallstricke (nicht offensichtlich, teils schon einmal Bugs verursacht)

### 2.1 `progression.json` ist NICHT die vollständige Single Source of Truth, wie das README suggeriert

Das README sagt: "rebalancing tiers/thresholds/gates needs no code changes". Das stimmt nur
für **Schwellenwerte, Stage-IDs, Anzeigenamen und Gates**. Die eigentliche **Menge** an Tiers
und Kategorien ist zusätzlich hart in Java kodiert:

- `ModScienceItems.Age` (Enum: BRONZE, IRON, STEEL, STEAM, LV, HV, EV, IV)
- `ModScienceItems.Category` (Enum: MINING, FARMING, PRODUCTION, EXPLORATION, CHALLENGE)

`ModScienceItems.register()` prüft beim Start, ob jeder Enum-Wert auch in `progression.json`
vorkommt, und **crasht den Mod-Start mit einer klaren Fehlermeldung**, falls nicht — das ist
gut (fail-fast), bedeutet aber: **Ein neues Tier oder eine neue Kategorie hinzufügen/entfernen
erfordert IMMER einen Java-Codeänderung + Rebuild**, nicht nur ein JSON-Edit. Nur
Schwellenwerte/Namen/Gates sind reines JSON.

### 2.2 Die gebaute Jar ist aktuell veraltet (Stand vor dieser Session) — MV-Reste

Commit `1d51200` ("Removed MV stage...") hat die MV-Age aus dem Enum entfernt, aber
`java_mod/build/libs/S3_progression_mod-0.1.0.jar` wurde seitdem nie sauber neu gebaut. Diese
Jar enthält noch generierte Ressourcen (Items/Rezepte/Modelle) für `mv_*_science` aus einem
alten `runData`-Lauf. Das hat in der Praxis schon zu Log-Spam geführt: `"ItemStack 'result'
can't be empty!"` beim Server-/Client-Start für 5 Rezepte (`mv_exploration_science` usw.),
weil die referenzierten Items nicht mehr existieren.

**Fix (in dieser Session begonnen, siehe Abschnitt 5 — noch nicht fertig):** `./gradlew
runData build` muss einmal sauber durchlaufen, um `src/generated/resources` neu zu erzeugen
(der Ordner existiert aktuell gar nicht im Repo — reine Build-Zeit-Generierung, nicht
eingecheckt). Solange das nicht klappt, jede neu gebaute Jar auf `mv_`-Reste prüfen:
```
unzip -l java_mod/build/libs/S3_progression_mod-0.1.0.jar | grep -c "mv_"
```
0 = sauber.

### 2.3 Mehrere Labore pro Team: nur das erste ist funktional

Seit Commit `25b2331` kann ein Team beliebig viele Laboratory-Blöcke platzieren, aber nur der
**erste** wird `ACTIVE=true` (funktional: GUI, Ticking, Rezeptverarbeitung). Jedes weitere
Labor im selben Team ist eine reine Deko-Kopie: Rechtsklick zeigt nur "This laboratory is out
of order", kein GUI, kein Ticking. Beide Varianten droppen als dasselbe Item und lassen sich
neu platzieren. Das Flag liegt team-weit im NBT (`s3_progression_mod:has_laboratory`) und wird
beim Abbau des aktiven Labors zurückgesetzt, damit das nächste platzierte Labor wieder
funktional werden kann. **Das ist gewolltes Verhalten, kein Bug** — aber für Spieler
verwirrend, wenn nicht klar kommuniziert (die Out-of-order-Nachricht ist der einzige Hinweis).

### 2.4 `FTBChunksAPI`/`resolveTeam` darf NIEMALS client-seitig aufgerufen werden

`FTBChunksAPI.api().getManager()` wirft eine NPE auf dem Client (bereits einmal real
gecrasht, siehe `LaboratoryBlock#getStateForPlacement`, das serverseitig UND clientseitig
läuft). **Jeder** Aufrufer von `ProgressionTiers.resolveTeam(...)` muss vorher
`level.isClientSide()` prüfen. Das ist an allen aktuellen Call-Sites schon richtig gemacht —
aber ein leicht zu wiederholender Fehler bei neuem Code, der irgendwo Team-Infos braucht.

### 2.5 Fortschritt im Labor geht bei JEDER Slot-Änderung komplett verloren

`LaboratoryBlockEntity.serverTick` setzt `progress = 0`, sobald sich der Inhalt eines der 5
Slots ändert (Item rein oder raus) — auch aus Versehen. Es gibt **keinen** Teilfortschritt.
Ein Spieler, der während des 5-Sekunden-Craftvorgangs (100 Ticks) aus Versehen ein Item
verschiebt, verliert den kompletten Fortschritt (die Items bleiben aber erhalten — nur
Zeitverlust, kein Itemverlust).

### 2.6 Schwellenwert-Vergleich ist strikt `>`, nicht `>=`

`isUnlocked`/`canCraftTier` (Java) und der Threshold-Check in `progression_listener.js`/
`progression_commands.js` (JS) nutzen alle konsistent `total > threshold`. Bei
`threshold: 3` braucht man also **4**, nicht 3, um freizuschalten. Aktuell stehen in
`progression.json` **alle** Schwellenwerte testweise auf `3` — das ist mit ziemlicher
Sicherheit noch nicht die finale Balance, sondern ein Platzhalter zum Testen.

### 2.7 "possession"-Gate prüft nicht alles

`GatedItemEnforcer` scannt einmal pro Sekunde pro Spieler `inventory.items`, `.armor`,
`.offhand` — **nicht** Curios-Slots, nicht Rucksäcke (Sophisticated Backpacks), nicht die
Ender-Truhe, nicht Container in der Welt. Ein Spieler könnte ein gated Item theoretisch in
einem Rucksack oder Curios-Slot "verstecken" und es würde nicht entfernt werden. Das
gefundene Item wird außerdem **komplett gelöscht** (kein Drop, keine Entschädigung), sobald es
im gescannten Bereich auftaucht.

### 2.8 Ein Gate ist NICHT in `progression.json` — hart in `blocked_blocks.js` kodiert

Der vanilla `minecraft:furnace` ist zusätzlich zu den JSON-Gates fest verdrahtet hinter der
LV-Stage gesperrt (`BlockEvents.rightClicked('minecraft:furnace', ...)` direkt am
Dateianfang). Wer das ändern/entfernen will, muss dieses Script editieren, nicht nur die JSON.

### 2.9 KubeJS-Skripte teilen sich EINEN globalen Scope — Namenskollisionen

Alle `server_scripts/*.js` laufen im selben globalen JS-Scope. `blocked_blocks.js` kapselt
sich bewusst in eine IIFE, um nur `BLOCKED_BLOCKS_LV_STAGE_ID` und `BLOCKED_BLOCKS_GATES`
global zu exponieren. **`progression_commands.js` tut das NICHT** — es exponiert `PROGRESSION`,
`RESEARCH_KEY`, `FTBTeamsAPI`, `loadProgressionConfig`, `tierByKey`, `getPlayerTeam` direkt im
globalen Scope. Jedes neue `server_scripts`-File (auch von anderen Mods/Packs, falls es in
denselben Namespace-Ordner kommt) darf diese Namen nicht wiederverwenden, sonst gibt es
`redeclaration of const`-Fehler beim Laden (schon einmal in einem anderen Kontext dieses Packs
aufgetreten — s. `s3_server`-Notizen). Bei neuen Skripten: entweder andere Namen wählen oder
nach `blocked_blocks.js`-Vorbild in eine IIFE packen.

### 2.10 KubeJS-Skripte können `progression.json` nicht direkt lesen

KubeJS' `ClassFilter` blockiert `java.io`/`java.nio` komplett — `Java.loadClass('java.nio.file.Files')`
wirft `"Class is not allowed by class filter!"`. Der Workaround (`ProgressionTiers.rawJson()`,
eine eigene Mod-Klasse, die vom Filter nicht betroffen ist) ist bereits überall etabliert —
aber jeder neue Skript-Zugriff auf die Config muss denselben Umweg nehmen, nicht versuchen,
die Datei selbst zu öffnen.

### 2.11 Rhino (KubeJS' JS-Engine) unterstützt kein Object-Spread

`{...obj}` in Objektliteralen funktioniert nicht. `blocked_blocks.js` mutiert Objekte deshalb
direkt statt sie zu spreaden — bei neuem Code dasselbe Muster übernehmen oder
`Object.assign`/manuelles Kopieren nutzen.

### 2.12 `/progression reset <tier>` hat keine Berechtigungsprüfung

Der Command in `progression_commands.js` prüft nur, ob der Aufrufer ein Spieler ist (nicht
Konsole) und in einem Team — **keine OP-Prüfung**. Jeder Spieler kann den Fortschritt seines
eigenen Teams für ein beliebiges Tier auf 0 zurücksetzen. Falls das nicht gewollt ist, fehlt
hier eine `.requires(src -> src.hasPermission(2))`-Ähnliche Absicherung.

### 2.13 Kleinere Doku-Inkonsistenzen (harmlos, aber verwirrend beim Lesen)

- `ProgressionEvent.java`s Javadoc-Beispiel nennt noch `"LV", "MV", "HV"` als Beispielwerte —
  MV existiert nicht mehr.
- `ProgressionMod.java`s Log-Zeile sagt `"adding 45 science items"`, tatsächlich sind es
  `8 × 5 = 40` (45 war korrekt, solange es noch 9 Ages inkl. MV gab).
- Diese sind rein kosmetisch (falsche Kommentare/Logs), aber beim nächsten Vorbeikommen ruhig
  mitkorrigieren.

## 3. Konfigurationsmöglichkeiten im Detail

### 3.1 `config_files/s3_progression_mod/progression.json` (Repo-Kopie; echte Instanz-Datei: `config/s3_progression_mod/progression.json`)

```jsonc
{
  "researchKey": "s3_progression_mod:research",   // NBT-Key im Team-Extra-Data
  "categories": ["mining", "farming", "production", "exploration", "challenge"],
  // ^ MUSS exakt (lowercase) den Namen von ModScienceItems.Category entsprechen
  "tiers": [
    { "key": "BRONZE", "displayName": "Bronze Age", "stageId": "bronze_unlocked", "threshold": 3 }
    // key MUSS exakt einem ModScienceItems.Age-Enum-Wert entsprechen (uppercase)
    // Reihenfolge im Array = Freischalt-Reihenfolge (Index i braucht Index i-1)
  ],
  "gates": [
    {
      "requiresTier": "BRONZE",       // welches Tier-Key muss unlocked sein
      "mechanism": "interaction",      // "interaction" | "placement" | "possession"
      "entity": false,                 // true = Ziel ist eine Entity (z.B. Rakete), nicht ein Block
      "blocks": ["tfc:bloomery"],       // Block-/Entity-IDs
      "message": "..."                  // Nachricht an den Spieler bei Verstoß
    }
  ]
}
```

Nach jeder Änderung: **volles Spiel-Neustart nötig** (Datei wird nur einmal beim Start in
statische Felder geladen, kein Hot-Reload — anders als reine `.js`-Dateien).

### 3.2 `kubejs_scripts/server_scripts/science_recipes.js` — Rezepte

Siehe README für die Grundmechanik (Datenliste + Generator, drei Zielsysteme:
`crafting_table`, GTCEU-Maschinentyp, Create-Maschinentyp). Ergänzend:

- Aktuell sind **nur `MINING`-Rezepte** befüllt (alle 8 Ages). Die anderen 4 Kategorien
  (farming/production/exploration/challenge) haben zwar registrierte Items (aus 2.1), aber
  **keine Crafting-Möglichkeit** — die Items existieren, sind aber für Spieler nicht
  erreichbar, solange hier nichts ergänzt wird. Das ist vermutlich der nächste große
  Arbeitsblock für diesen Mod.
- Nutze `tools/recipe_editor.py` statt Hand-Edits, wenn möglich (siehe README) — Rhino-JS
  ohne Object-Spread macht Handarbeit fehleranfälliger als nötig.
- Nur `/kubejs reload_server` nötig nach Änderungen hier, kein Full-Restart (reines
  `server_scripts`, kein Jar/Config).

### 3.3 Java-seitige Konstanten (Rebuild nötig)

- `LaboratoryBlockEntity.SLOT_COUNT` (5), `MAX_PROGRESS` (100 Ticks = 5 Sekunden pro Craft,
  unabhängig von der Anzahl Kategorien).
- `GatedItemEnforcer.CHECK_INTERVAL_TICKS` (20 = 1×/Sekunde pro Spieler).
- `ModScienceItems.Age`/`Category`-Enums (siehe 2.1).

## 4. Deployment — es gibt KEIN automatisches Deploy

Jede Änderung muss manuell in die Ziel-Instanz kopiert werden (README-Tabelle). Es gibt
mindestens zwei bekannte Ziel-Instanzen außerhalb dieses Repos, die synchron gehalten werden
müssen:
- den lokalen Dedicated-Server-Ordner (`s3_server`/`s3_server_13.10` im übergeordneten
  `Civ TFG`-Verzeichnis — der eigentliche Live-Server läuft aber remote auf PebbleHost, der
  lokale Ordner ist nur die Referenzkopie)
- eine oder mehrere CurseForge-Client-Instanzen (`TerraFirmaGreg-Modern (1)` o.ä.)

**Nach Jar- oder `progression.json`-Änderungen ist ein voller Client/Server-Neustart nötig**,
kein Hot-Reload. Vergiss beim Kopieren nicht: Jar (`mods/`), beide `kubejs_scripts`-Ordner,
UND `progression.json` (`config/s3_progression_mod/`) — alle drei fehlten in dieser Session
schon einmal einzeln auf verschiedenen Instanzen und haben zu Abstürzen geführt (fehlende
`progression.json` → `IllegalStateException` beim Mod-Start, siehe 2.2-ähnlicher Fehlerpfad in
`ProgressionTiers`' static initializer).

## 5. Aktueller Stand der Dev-Umgebung (Build/Datagen) — offener Punkt

`./gradlew runData` (nötig, um `src/generated/resources` neu zu erzeugen und damit die
MV-Reste aus 2.2 loszuwerden) schlug zu Beginn dieser Session mit 5 fehlenden
Mandatory-Dependencies fehl. Zwei Ursachen gefunden und behoben:

1. **`build.gradle` Zeile ~171**: referenzierte `libs/architectury-forge-9.2.14.jar`, die
   tatsächliche Datei heißt aber `libs/architectury-9.2.14-forge.jar` (Wortreihenfolge
   vertauscht). `files(...)` mit falschem Pfad scheitert still (leere Collection, kein
   Fehler) — dadurch fehlte `architectury` zur Laufzeit komplett, was `ftbchunks`, `kubejs`,
   `ftblibrary` und `ftbteams` allesamt als "missing dependency" markierte. **Behoben** —
   Pfad korrigiert.
2. **`bookshelf` fehlte komplett** (`gamestages`' eigene Hard-Dependency, `[20,)`). **Behoben**
   — `Bookshelf-Forge-1.20.1-20.2.15.jar` von Modrinth (Projekt-Slug `bookshelf-lib`)
   heruntergeladen, in `java_mod/libs/` abgelegt und in `build.gradle` deklariert.

Nach beiden Fixes kommt `runData` weiter, scheitert aber jetzt an einem **anderen, tieferen
Problem**:
```
InvalidMixinException: @Shadow method m_130946_ in kubejs-common.mixins.json:components.MutableComponentMixin
was not located in the target class net.minecraft.network.chat.MutableComponent.
```
Das ist ein Mixin-Refmap/Mappings-Konflikt zwischen der eingebundenen KubeJS-Version
(`2001.6.5-build.26`) und der in `gradle.properties` konfigurierten Toolchain
(`mapping_channel=official`, `mapping_version=1.20.1`, `forge_version=47.4.10`). Nicht in
dieser Session gelöst — vermutlich muss entweder eine andere KubeJS-Buildnummer verwendet
werden, oder der Mapping-Kanal/-Version angepasst werden, oder dies ist ein bekanntes
Kompatibilitätsproblem dieser spezifischen KubeJS-Version mit `official`-Mappings (parchment
wäre ein Kandidat zum Ausprobieren). **Nächster Schritt für den Datagen-Fix.** Bis das gelöst
ist: neue Jars manuell auf `mv_`-Reste prüfen (Befehl siehe 2.2), da `runData` nicht
zuverlässig sauber durchläuft.

`run-data/` (von `runData` erzeugter Arbeitsordner) wurde zu `.gitignore` hinzugefügt.

## 6. Repository-Struktur (Ergänzung zum README)

- `java_mod/libs/` — Drittanbieter-Jars werden **direkt im Repo eingecheckt** (kein Maven-Repo
  für sie), inkl. der neu hinzugefügten `Bookshelf-Forge-1.20.1-20.2.15.jar`. Bei neuen
  Abhängigkeiten genauso verfahren: Jar in `libs/`, `fg.deobf(files("libs/..."))` in
  `build.gradle`.
- `java_mod/src/generated/` existiert aktuell **nicht** im Repo (wird nur zur Build-Zeit
  erzeugt, nicht eingecheckt) — das ist auch der Grund, warum die MV-Reste nur in der bereits
  gebauten Jar stecken, nicht im Quellcode selbst.
- Texturen für den "aktiven" Zustand des Labors (`laboratory_*_active.png` +
  `.mcmeta`-Dateien für Animation) sind bereits vorhanden (aus Commit `25b2331`/`8a20e3f`) —
  passend zum in 2.3 beschriebenen `ACTIVE`-Blockstate.
