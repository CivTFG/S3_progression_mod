"""Builds tools/item_index.json - a searchable id -> display-name index of items this
modpack defines, so tools/recipe_editor.py can offer a search box instead of hand-typed
item ids for recipe inputs.

Two different sources are combined, because they're each reliable for only half the job:

- The item's registry id (the thing recipes actually need) is read from each jar's
  assets/<modid>/models/item/**.json paths - Forge/vanilla always generate a default item
  model at a path matching the registry name exactly, slashes included, so this is
  authoritative. (Translation keys looked like a shortcut here, but keys can't represent
  "/" - TerraFirmaCraft registers e.g. "tfc:metal/ingot/copper", whose translation key is
  "item.tfc.metal.ingot.copper". Reconstructing the id from the dotted key would silently
  produce the wrong, non-existent id "tfc:metal.ingot.copper" - caught this while testing
  against the real TFC jar, so the id always comes from the model path, never the lang key.)
- The human-readable display name (for searching by e.g. "copper ingot" instead of the raw
  id) comes from assets/<modid>/lang/en_us.json, matched back to the model-derived id by
  converting its slashes to dots (undoing the same collapsing lang keys do). Falls back to
  a prettified last path segment when no lang entry matches.

Some mods (GTCEU is the big one here) generate their material items - ingots, plates,
dusts, etc. for every material - at runtime instead of shipping a model json per item, so
the model scan above misses them entirely. For those, an id is reconstructed directly from
the lang key as a fallback, but ONLY when no model-derived item already exists at that same
dotted path - otherwise a mod like TFC that nests real ids with "/" (whose correct id the
model scan already found) would also get a second, wrong entry with "/" replaced by "."
(there's no way to tell from a lang key alone whether a "." was originally a "/"). This
fallback still assumes no internal slashes, which holds for every mod actually used by this
pack's recipes so far, but isn't universally guaranteed - if a freshly-generated id looks
wrong, check the mod's real registry name before trusting it.

GTCEU's material items don't even have a lang fallback, though: material names ("Tin") and
item forms ("Ingot") are each localized separately (material.gtceu.tin, tagprefix.ingot)
and composed at runtime, so there's no "item.gtceu.tin_ingot" key to fall back to either -
confirmed by checking the raw lang json directly. The only static source left that lists
these items by their real, complete id is the modpack's own FTB Quests chapters (they
reference real items as quest requirements/rewards), so those .snbt files get scanned too,
purely for ids this pass hasn't already found - same technique used earlier to build this
mod's own mining-science recipe list from the LV/HV/EV/IV quest chapters.

This needs no running game - it's all static data already inside the mod jars and the
modpack's own quest files.

Run with:  python tools/build_item_index.py [path-to-mods-folder]
Re-run whenever mods are added/updated/removed to refresh the index.
"""
import json
import re
import sys
import zipfile
from pathlib import Path

DEFAULT_MODS_DIR = Path(r"C:\Users\erikp\curseforge\minecraft\Instances\TerraFirmaGreg-Modern\mods")
DEFAULT_QUESTS_DIR = Path(r"C:\Users\erikp\curseforge\minecraft\Instances\TerraFirmaGreg-Modern\config\ftbquests\quests")
OUTPUT = Path(__file__).resolve().parent / "item_index.json"

LANG_KEY_RE = re.compile(r"^(item|block)\.([^.]+)\.(.+)$")
ITEM_MODEL_RE = re.compile(r"^assets/([^/]+)/models/item/(.+)\.json$")
NAMESPACED_ID_RE = re.compile(r'"([a-z0-9_.]+:[a-z0-9_/.]+)"')


def scan_jar(jar_path):
    ids = set()  # {(modid, path-with-slashes)}
    lang = {}  # modid -> {dotted.path: display name}
    try:
        with zipfile.ZipFile(jar_path) as jar:
            for name in jar.namelist():
                model_match = ITEM_MODEL_RE.match(name)
                if model_match:
                    modid, path = model_match.groups()
                    ids.add((modid, path))
                    continue
                if not name.endswith("lang/en_us.json"):
                    continue
                try:
                    data = json.loads(jar.read(name).decode("utf-8"))
                except (json.JSONDecodeError, UnicodeDecodeError):
                    continue
                for key, value in data.items():
                    lang_match = LANG_KEY_RE.match(key)
                    if not lang_match or not isinstance(value, str) or "%" in value:
                        continue
                    _, modid, dotted_path = lang_match.groups()
                    lang.setdefault(modid, {})[dotted_path] = value
    except zipfile.BadZipFile:
        print(f"  (skipping {jar_path.name}: not a valid jar)")
    return ids, lang


def prettify(path):
    return path.rsplit("/", 1)[-1].replace("_", " ").title()


def scan_quests(quests_dir):
    """Every namespaced-id-looking quoted string in the pack's quest .snbt files. A blunt
    regex over quest files (not just item fields) picks up some non-item ids (advancement
    keys, loot table ids) too, but those are harmless as a few extra never-matched search
    results - the goal here is not missing real items, not perfect precision."""
    ids = set()
    for snbt_file in quests_dir.rglob("*.snbt"):
        text = snbt_file.read_text(encoding="utf-8", errors="ignore")
        for match in NAMESPACED_ID_RE.finditer(text):
            candidate = match.group(1)
            modid, _, path = candidate.partition(":")
            if modid and path:
                ids.add((modid, path))
    return ids


def main():
    mods_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_MODS_DIR
    quests_dir = Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_QUESTS_DIR
    if not mods_dir.exists():
        print(f"Mods folder not found: {mods_dir}")
        sys.exit(1)

    all_ids = set()
    all_lang = {}
    jars = sorted(mods_dir.glob("*.jar"))
    for i, jar_path in enumerate(jars, 1):
        print(f"[{i}/{len(jars)}] {jar_path.name}")
        ids, lang = scan_jar(jar_path)
        all_ids |= ids
        for modid, entries in lang.items():
            all_lang.setdefault(modid, {}).update(entries)

    model_dotted_ids = {(modid, path.replace("/", ".")) for modid, path in all_ids}

    by_id = {}
    for modid, path in sorted(all_ids):
        dotted_path = path.replace("/", ".")
        display_name = all_lang.get(modid, {}).get(dotted_path) or prettify(path)
        by_id[f"{modid}:{path}"] = display_name

    # Fallback for items with no static model file (e.g. GTCEU's per-material items) -
    # only when it wouldn't duplicate/shadow a model-derived id at the same dotted path.
    for modid, lang_entries in all_lang.items():
        for dotted_path, display_name in lang_entries.items():
            if (modid, dotted_path) in model_dotted_ids:
                continue
            by_id.setdefault(f"{modid}:{dotted_path}", display_name)

    # Second fallback: ids seen in the pack's own quest files but found nowhere above
    # (e.g. GTCEU material items, which have neither a model file nor a lang key at all).
    if quests_dir.exists():
        quest_ids = scan_quests(quests_dir)
        added = 0
        for modid, path in quest_ids:
            item_id = f"{modid}:{path}"
            if item_id in by_id:
                continue
            dotted_path = path.replace("/", ".")
            display_name = all_lang.get(modid, {}).get(dotted_path) or prettify(path)
            by_id[item_id] = display_name
            added += 1
        print(f"Quest files added {added} ids not found in any mod jar")
    else:
        print(f"Quests folder not found ({quests_dir}), skipping that fallback")

    result = sorted(by_id.items())

    OUTPUT.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"\nWrote {len(result)} items to {OUTPUT}")


if __name__ == "__main__":
    main()
