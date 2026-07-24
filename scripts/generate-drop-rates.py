#!/usr/bin/env python3
"""
Generates drop-rates.json for CollectionLogPopupEnhanced from OSRS Wiki drop tables.

Fetches raw wikitext for each boss page listed below via
https://oldschool.runescape.wiki/index.php?title=<Page>&action=raw, extracts
{{DropsLine|name=...|rarity=X/Y|rolls=N}} templates found within a small set of
known "notable drop" wikitext section headers (===Uniques===, ===Tertiary===,
===Pre-roll===), and combines rarity+rolls into a single per-kill probability:

    p = 1 - (1 - numerator/denominator) ** rolls

Entries with a non-numeric rarity (nested templates like {{Brimstone rarity|...}},
"Always", or unparseable values) are skipped and logged to stderr for manual
review. The first DropsLine for a given item name on a page wins - later
duplicate listings (e.g. Wilderness/Deadman-mode variants of the same monster
page) are ignored, matching the main/default variant.

Scope is boss-only, deliberately: non-boss monsters were tried (see git history /
KC-loot-Todo.md) and reverted - too many edge cases per monster (custom section
headers, on-task/off-task rarity splits, "superior" variants with their own
distinct NPC name, drop tables driven by a combat-level-parameterized template
instead of a flat DropsLine) for the value of tracking them.

Output: src/main/resources/com/snakesteak/collectionlogpopupenhanced/drop-rates.json
Shape: { "Source name": { "Item name": probability (0-1), ... }, ... }

Re-run this whenever drop rates change:
    python scripts/generate-drop-rates.py
"""
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

WIKI_RAW_URL = "https://oldschool.runescape.wiki/index.php?title={}&action=raw"
USER_AGENT = "CollectionLogPopupEnhancedDataGen/1.0 (RuneLite plugin drop-rate dataset generator)"

OUTPUT_PATH = (
    Path(__file__).resolve().parent.parent
    / "src/main/resources/com/snakesteak/collectionlogpopupenhanced/drop-rates.json"
)

NOTABLE_SECTION_HEADERS = ("Uniques", "Unique", "Tertiary", "Pre-roll")

# KC-eligible bosses (produce the official "Your X kill count is: N" chat
# message) - wiki page titles. Cross-referenced against the "Bosses" tab of
# https://oldschool.runescape.wiki/w/Collection_log.
BOSSES = [
    "Abyssal Sire", "Alchemical Hydra", "Amoxliatl", "Araxxor",
    "Callisto", "Artio", "Cerberus", "Chaos Elemental", "Chaos Fanatic",
    "Commander Zilyana", "Corporeal Beast", "Crazy archaeologist",
    "Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme",
    "Deranged archaeologist", "Doom of Mokhaiotl", "Duke Sucellus",
    "General Graardor", "Giant Mole", "Grotesque Guardians", "Hespori",
    "The Hueycoatl", "Kalphite Queen", "King Black Dragon", "Kraken",
    "Kree'arra", "K'ril Tsutsaroth", "The Leviathan", "Nex", "The Nightmare",
    "Obor", "Bryophyta", "Phantom Muspah", "Sarachnis", "Scorpia", "Scurrius",
    "Skotizo", "Thermonuclear smoke devil", "Vardorvis", "Venenatis",
    "Spindel", "Vet'ion", "Calvar'ion", "Vorkath", "The Whisperer", "Yama",
    "Zalcano", "Zulrah",
    # Sol Heredit (Fortis Colosseum) deliberately excluded: its unique chance scales with how
    # many waves are cleared before banking, same as the raids excluded from this dataset - no
    # single flat per-kill probability applies.
]

RARITY_RE = re.compile(r"^(?P<num>\d+(?:\.\d+)?)\s*/\s*(?P<den>\d+(?:\.\d+)?)$")
# Some pages (e.g. Alchemical Hydra) give the denominator as a computed parser-function
# expression instead of a plain number, e.g. "1/{{#expr:180/(1999/2000*999/1000) round 1}}".
# The expression is restricted to digits/operators/parens before being evaluated.
EXPR_RARITY_RE = re.compile(r"^(?P<num>\d+(?:\.\d+)?)\s*/\s*\{\{#expr:\s*(?P<expr>[^}]+?)\s*(?:round\s+\d+)?\}\}$")
SAFE_EXPR_CHARS_RE = re.compile(r"^[\d\s.+\-*/()]+$")
DROPSLINE_RE = re.compile(r"\{\{DropsLine\|([^{}]*(?:\{\{[^{}]*\}\}[^{}]*)*)\}\}")
SECTION_RE = re.compile(r"^={3,4}\s*([^=]+?)\s*={3,4}\s*$", re.MULTILINE)


def combined_probability(num, den, rolls=1):
    """Combines a per-roll rarity (num/den) and roll count into a single per-kill/opening
    probability of getting the item at least once: p = 1 - (1 - num/den) ** rolls."""
    return 1 - (1 - num / den) ** rolls


# Reward-chest sources whose unique items can't be scraped by parse_drops(): their wiki pages use
# {{DropsLineReward|...}} rather than {{DropsLine|...}}, and (for Barrows) the uniques live in
# per-brother "====" subsections rather than a single top-level "===Uniques===" section. Rates are
# transcribed by hand from the wiki instead, using the same rarity/rolls -> probability formula.
# Source name here must match the "boss" group KillCountTracker parses from the "Your X count is: N"
# chat message the chest prints (see KillCountTracker.KILL_COUNT_PATTERN).
MANUAL_ENTRIES = {
    # https://oldschool.runescape.wiki/w/Chest_(Barrows) "Pre-roll" section: 4 unique pieces per
    # brother, each {{DropsLineReward|rarity=1/2448|rolls=7}}. Rates assume all 6 brothers are slain
    # and full (1012) reward potential, matching the wiki's own stated assumption for these numbers.
    "Barrows chest": {
        name: combined_probability(1, 2448, 7)
        for name in [
            "Ahrim's hood", "Ahrim's robetop", "Ahrim's robeskirt", "Ahrim's staff",
            "Dharok's helm", "Dharok's platebody", "Dharok's platelegs", "Dharok's greataxe",
            "Guthan's helm", "Guthan's platebody", "Guthan's chainskirt", "Guthan's warspear",
            "Karil's coif", "Karil's leathertop", "Karil's leatherskirt", "Karil's crossbow",
            "Torag's helm", "Torag's platebody", "Torag's platelegs", "Torag's hammers",
            "Verac's helm", "Verac's brassard", "Verac's plateskirt", "Verac's flail",
        ]
    },
    # https://oldschool.runescape.wiki/w/Lunar_Chest "Uniques" section: each unique is
    # {{DropsLineReward|rarity=1/224}}. That figure assumes all 3 Moons of Peril are defeated before
    # opening the chest (the rate the wiki itself quotes) - actual odds are better than this for
    # players who fight all three moons per chest and worse for single-moon openings.
    "Lunar Chest": {
        name: combined_probability(1, 224)
        for name in [
            "Eclipse atlatl", "Eclipse moon helm", "Eclipse moon chestplate", "Eclipse moon tassets",
            "Dual macuahuitl",
            "Blood moon helm", "Blood moon chestplate", "Blood moon tassets",
            "Blue moon spear", "Blue moon helm", "Blue moon chestplate", "Blue moon tassets",
        ]
    },
}


def parse_rarity(rarity):
    """Returns (numerator, denominator) as floats, or None if unparseable."""
    m = RARITY_RE.match(rarity)
    if m:
        return float(m.group("num")), float(m.group("den"))

    m = EXPR_RARITY_RE.match(rarity)
    if m and SAFE_EXPR_CHARS_RE.match(m.group("expr")):
        try:
            den = eval(m.group("expr"), {"__builtins__": {}}, {})  # noqa: S307 - digits/operators only, checked above
        except Exception:
            return None
        return float(m.group("num")), float(den)

    return None


def fetch_wikitext(title):
    url = WIKI_RAW_URL.format(urllib.parse.quote(title.replace(" ", "_")))
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=15) as resp:
        return resp.read().decode("utf-8")


def parse_template_params(raw):
    """Splits a {{DropsLine|...}} inner string on top-level '|' (ignoring any
    nested {{...}} templates, e.g. raritynotes citations) into a dict."""
    params = {}
    depth = 0
    current = []
    parts = []
    for ch in raw:
        if ch == "{":
            depth += 1
            current.append(ch)
        elif ch == "}":
            depth -= 1
            current.append(ch)
        elif ch == "|" and depth == 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(ch)
    parts.append("".join(current))
    for part in parts:
        if "=" in part:
            key, _, value = part.partition("=")
            params[key.strip()] = value.strip()
    return params


def extract_notable_sections(wikitext):
    """Concatenates the wikitext of every section whose header is one of
    NOTABLE_SECTION_HEADERS, from that header to the next '===' header."""
    matches = list(SECTION_RE.finditer(wikitext))
    chunks = []
    for i, m in enumerate(matches):
        header = m.group(1).strip()
        if header not in NOTABLE_SECTION_HEADERS:
            continue
        start = m.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(wikitext)
        chunks.append(wikitext[start:end])
    return "\n".join(chunks)


def parse_drops(wikitext, source_name):
    section_text = extract_notable_sections(wikitext)
    drops = {}
    for m in DROPSLINE_RE.finditer(section_text):
        params = parse_template_params(m.group(1))
        name = params.get("name")
        rarity = params.get("rarity")
        if not name or not rarity:
            continue
        if name in drops:
            continue  # first occurrence wins (main variant, not a later Wilderness/Deadman dupe)

        parsed = parse_rarity(rarity)
        if parsed is None:
            print(f"  skip [{source_name}] {name!r}: unparseable rarity {rarity!r}", file=sys.stderr)
            continue
        num, den = parsed
        if den <= 0:
            continue

        rolls_raw = params.get("rolls")
        try:
            rolls = int(rolls_raw) if rolls_raw else 1
        except ValueError:
            rolls = 1

        drops[name] = combined_probability(num, den, rolls)
    return drops


def main():
    dataset = {}

    for title in BOSSES:
        print(f"Fetching boss '{title}'...", file=sys.stderr)
        try:
            wikitext = fetch_wikitext(title)
        except Exception as e:
            print(f"  FAILED to fetch {title!r}: {e}", file=sys.stderr)
            continue

        drops = parse_drops(wikitext, title)
        if not drops:
            print(f"  no notable drops parsed for {title!r}", file=sys.stderr)
            continue

        dataset[title] = drops
        time.sleep(0.3)  # be polite to the wiki

    dataset.update(MANUAL_ENTRIES)

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT_PATH.open("w", encoding="utf-8") as f:
        json.dump(dataset, f, indent="\t", sort_keys=True)
        f.write("\n")

    total_entries = sum(len(v) for v in dataset.values())
    print(f"Wrote {total_entries} drop entries across {len(dataset)} sources to {OUTPUT_PATH}", file=sys.stderr)


if __name__ == "__main__":
    main()
