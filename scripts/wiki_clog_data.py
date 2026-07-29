"""
Shared fetch helper for the OSRS Wiki's canonical collection log item list, used by both
generate-drop-rates.py (item names, to drive its per-item dropsline queries) and
generate-rarity-overrides.py (id/name/tabs, to build the combined rarity-overrides.json).

    https://oldschool.runescape.wiki/w/Module:Collection_log/data.json?action=raw

A flat array of every real collection log item: {"id": <int>, "name": <str>, "tabs": [<str>, ...]}.
"tabs" mixes specific page names (e.g. "Zulrah") with broader UI categories (e.g. "Slayer",
"Miscellaneous", "All Pets") - there's no field distinguishing which is which.
"""
import json
import urllib.request

USER_AGENT = "CollectionLogPopupEnhancedDataGen/1.0 (RuneLite plugin drop-rate dataset generator)"

CLOG_DATA_URL = "https://oldschool.runescape.wiki/w/Module:Collection_log/data.json?action=raw"


def fetch_json(url, timeout=30):
    """Fetches and parses a JSON document from url, with the shared User-Agent header."""
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_clog_data():
    """Returns the raw list of {"id", "name", "tabs"} entries from the wiki's canonical collection
    log item list."""
    return fetch_json(CLOG_DATA_URL)
