#!/usr/bin/env python3
"""
Generates rarity-overrides.json for CollectionLogPopupEnhanced by mirroring the OSRS Wiki's own
per-item collection log completion percentages:

    https://oldschool.runescape.wiki/w/Module:Collection_log/completion.json?action=raw

This is the same data the wiki's {{Collection log list}} template reads via Module:Collection_log
(which loads it with mw.loadJsonData("Module:Collection_log/completion.json")) - already a flat
{item id: completion percent} map, so this script is a straight passthrough (re-serialized for
consistent formatting) rather than a scrape.

Output: src/main/resources/com/snakesteak/collectionlogpopupenhanced/rarity-overrides.json

Re-run this whenever completion percentages should be refreshed:
    python scripts/generate-rarity-overrides.py
"""
import json
import sys
import urllib.request
from pathlib import Path

COMPLETION_URL = "https://oldschool.runescape.wiki/w/Module:Collection_log/completion.json?action=raw"
USER_AGENT = "CollectionLogPopupEnhancedDataGen/1.0 (RuneLite plugin drop-rate dataset generator)"

OUTPUT_PATH = (
    Path(__file__).resolve().parent.parent
    / "src/main/resources/com/snakesteak/collectionlogpopupenhanced/rarity-overrides.json"
)


def main():
    print("Fetching collection log completion percentages...", file=sys.stderr)
    req = urllib.request.Request(COMPLETION_URL, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.loads(resp.read().decode("utf-8"))

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT_PATH.open("w", encoding="utf-8") as f:
        json.dump(data, f, indent="\t")
        f.write("\n")

    print(f"Wrote {len(data)} completion entries to {OUTPUT_PATH}", file=sys.stderr)


if __name__ == "__main__":
    main()
