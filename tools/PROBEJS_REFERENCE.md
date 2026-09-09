# ProbeJS & Item-Index — Referenz für andere Aufgaben

Dieses Dokument fasst alles zusammen, was beim Bau von `tools/build_item_index.py`
(Item-Suche für den Rezept-Editor, siehe Haupt-`README.md`) über den ProbeJS-Output dieses
Modpacks herausgefunden wurde. Gedacht für eine andere Claude-Instanz, die selbst mit dem
ProbeJS-Dump arbeitet — entweder durch **Wiederverwendung** von `tools/item_index.json`, oder
durch eigenes Parsen von `globals.d.ts` für andere Registrierungstypen (Blocks, Fluids,
Entities, ...).

## Was ProbeJS ist und wo der Dump liegt

[ProbeJS](https://github.com/Prunoideae/ProbeJS) ist in der TFG-Instanz installiert, um
TypeScript-Typdefinitionen für KubeJS-Autocomplete zu generieren. Befehl `/probejs dump`
im Spiel (Cheats müssen aktiv sein) schreibt nach:

```
C:\Users\erikp\curseforge\minecraft\Instances\TerraFirmaGreg-Modern\kubejs\probe\generated\
```

Dateien darin (Stand dieser Session):

| Datei | Größe | Inhalt |
|---|---|---|
| `globals.d.ts` | **~15,5 MB**, 22.506 Zeilen | Der eigentliche Schatz — siehe unten |
| `registries.d.ts` | ~18 KB | `Registry.*`-Klassen (welche `.create(id, type)`-Overloads pro Registrierungstyp existieren, z.B. alle Block-Typen die man selbst registrieren kann) |
| `constants.d.ts` | ~22 KB | Benannte Konstanten |
| `events.d.ts` | ~63 KB | KubeJS-Event-Typen |
| `tag_events.d.ts` | ~51 KB | Tag-Event-Typen |
| `names.d.ts` | winzig | ein paar Alias-Konstanten |

**⚠️ Wichtige Falle beim erneuten Dumpen:** `/probejs dump` braucht bei ~650+ GTCEU-Materialien
mehrere Minuten. Man muss **im Pause-/Weltauswahl-Menü warten, bis es wirklich fertig ist**,
bevor man das Spiel schließt — zu frühes Beenden lässt `kubejs/probe/` leer oder unvollständig
zurück. Ein erneuter Dump ist nur nach größeren Mod-Updates nötig; für die meisten Aufgaben
reicht der bereits vorhandene Dump auf der Platte, kein laufendes Spiel nötig.

## `globals.d.ts` enthält mehrere riesige, autoritative ID-Listen — nicht nur Items

Der für `item_index.json` genutzte Teil ist eine einzelne TypeScript-Union:
```typescript
type Item = "ad_astra:aeronos_cap" | "ad_astra:aeronos_door" | ... ;
```
Das ist die **komplette, echte Item-Registry** zur Dump-Zeit — keine Rekonstruktion, keine
Lücken. **Dasselbe Muster existiert für weitere Registrierungstypen**, alle nach demselben
Schema `type <Name> = "modid:pfad" | "modid:pfad2" | ... ;` extrahierbar:

| Type-Name | ~Anzahl Einträge | Beispiel-Eintrag |
|---|---|---|
| `Item` | 32.611 | `"gtceu:long_gold_rod"` |
| `Block` | 25.247 | (überlappt stark mit Item, viele Items sind Blockitems) |
| `Fluid` | 1.703 | `"ad_astra:cryo_fuel"` |
| `EntityType` | 856 | `"ad_astra:corrupted_lunarian"` |
| `RecipeType` | 204 | `"minecraft:smelting"`, `"crafting"` (beide Formen kommen vor!) |
| `MobEffect` | 123 | `"gtceu:weak_poison"` |
| `Potion` | 87 | `"minecraft:fire_resistance"` |
| `Enchantment` | 93 | `"create:capacity"` |
| `Attribute` | 37 | `"minecraft:generic.max_health"` |

**Extraktions-Regex (Python), für jeden beliebigen Type-Namen wiederverwendbar:**
```python
import re
text = open(globals_dts_path, encoding="utf-8", errors="ignore").read()
match = re.search(rf"type {type_name} = (.*?);", text, re.DOTALL)
ids = re.findall(r'"([a-z0-9_.]+:[a-z0-9_/.]+)"', match.group(1))
```
(Genau dieses Muster nutzt `scan_probejs_dump()` in `build_item_index.py` für `Item`.)

**Achtung bei `RecipeType` und ähnlichen:** manche Einträge kommen doppelt vor, einmal ohne
und einmal mit `minecraft:`-Präfix (`"crafting"` und `"minecraft:crafting"`) — beim Abgleich
gegen echte Rezept-IDs im Zweifel beide Schreibweisen berücksichtigen.

## `tools/item_index.json` — schon gebauter Item-Index, direkt wiederverwendbar

Falls die andere Aufgabe **auch** Item-IDs mit Anzeigenamen braucht (z.B. Suche, Autocomplete,
Validierung von Item-IDs gegen die echte Registry), **einfach diese Datei laden statt neu zu
bauen**:

- Pfad: `tools/item_index.json` (relativ zu diesem Repo-Root)
- Format: flache JSON-Liste von `[id, display_name]`-Paaren, alphabetisch nach `id` sortiert,
  keine Einrückung (kompakt, ~2,3 MB, 39.135 Einträge)
- Beispiel: `["gtceu:long_gold_rod", "Long Gold Rod"]`
- Enthält **alle** zur Bauzeit bekannten Items dieser Instanz, inklusive zur Laufzeit
  generierter GTCEU-Materialien (Ingots, Platten, Staub etc. pro Material), die in keiner
  einzelnen Jar als Modell-Datei existieren.
- Neu bauen mit: `python tools/build_item_index.py` (aus dem Repo-Root, keine Argumente nötig
  — Standardpfade zeigen auf die TFG-Instanz). Danach neu einlesen.

### Wie der Index gebaut wird (4 kombinierte Quellen, je nach Zuverlässigkeit gestaffelt)

Vollständig dokumentiert im Docstring von `tools/build_item_index.py`, hier die Kernpunkte:

1. **Jar-Modell-Dateien** (`assets/<modid>/models/item/**.json`) — liefert die **korrekte
   Registry-ID mit "/"**, da Forge/Vanilla immer ein Default-Item-Model exakt am
   Registrierungspfad ablegt. Die zugehörige `lang/en_us.json` liefert den Anzeigenamen.
2. **Lang-Key-Fallback** — für Items ohne Modell-Datei (v.a. GTCEU-Materialien) wird die ID
   aus dem Lang-Key rekonstruiert (`item.gtceu.foo` → `gtceu:foo`), aber **nur wenn dieser
   gepunktete Pfad noch nicht aus einer Modell-Datei bekannt ist** — sonst entstünde neben der
   echten `/`-ID eine zusätzliche falsche `.`-ID.
3. **FTB-Quests-`.snbt`-Dateien** — grobe Regex über alle Quoted-Strings mit `modid:pfad`-Form,
   fängt ein paar Nicht-Item-IDs (Advancements, Loot-Tables) als harmlosen Beifang mit ein.
4. **EMI's `emi.json`** (Lookup-Historie + Favoriten) — echte, live aufgelöste IDs, keine
   Punkt/Slash-Ambiguität.
5. **ProbeJS' `type Item = ...`** (siehe oben) — die eigentlich vollständigste Quelle, deckt
   praktisch alles ab, was die ersten drei verpassen.

**Wichtigste Falle (bereits einmal gegen die echte TFC-Jar getestet und bestätigt):**
Übersetzungsschlüssel können **kein** `/` darstellen. TFC registriert z.B.
`tfc:metal/ingot/copper`, dessen Lang-Key aber `item.tfc.metal.ingot.copper` lautet. Eine
ID-Rekonstruktion allein aus dem Lang-Key würde fälschlich `tfc:metal.ingot.copper` erzeugen
(mit Punkten statt Slashes) — eine nicht existierende ID. Deshalb kommt die ID **immer** aus
dem Modell-Pfad (oder einer der drei expliziten Fallback-Quellen), **nie** durch Zurückwandeln
eines Lang-Keys. Wer selbst IDs aus Lang-Keys ableitet, sollte dieselbe Falle im Hinterkopf
behalten.

**GTCEU-Sonderfall:** Materialnamen ("Tin") und Item-Form ("Ingot") werden getrennt lokalisiert
(`material.gtceu.tin`, `tagprefix.ingot`) und erst zur Laufzeit zusammengesetzt — es gibt
**keinen** `item.gtceu.tin_ingot`-Lang-Key zum Zurückfallen. Ohne ProbeJS-Dump wären solche
IDs praktisch nicht sauber auffindbar.

## Praktische Hinweise für eigene Auswertungen

- `globals.d.ts` ist mit 15,5 MB zu groß, um es komplett in den Kontext zu laden — gezielt mit
  `grep`/Regex nach dem gewünschten `type <Name> = ` suchen, nicht die ganze Datei lesen.
- Der Dump ist ein **Snapshot zum Dump-Zeitpunkt** (25. August 2026 laut Dateidatum). Nach
  Mod-Updates ist er potenziell veraltet — im Zweifel Änderungsdatum von `globals.d.ts` prüfen
  bzw. neuen Dump anfordern, bevor man ihm für aktuelle Fragen vertraut.
- Für reine Item-Suche/Validierung: `tools/item_index.json` verwenden, nicht `globals.d.ts`
  direkt parsen — spart Zeit und ist bereits mit Anzeigenamen anreichert.
- Für andere Registrierungstypen (Block/Fluid/EntityType/RecipeType/...): direkt aus
  `globals.d.ts` mit der oben gezeigten Regex extrahieren, es gibt dafür noch keinen
  vorgebauten Index wie bei Items.
