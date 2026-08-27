package com.snakesteak.collectionlogpopupenhanced.killcount;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What the number attached to an unlock counts, and the panel label that says so. Derived from the
 * chat wording rather than a per-boss table (see {@link KillCountTracker}), so a new activity picks
 * up the right label as long as it phrases its message like an existing one.
 * <p>Where the kind alone would leave the activity ambiguous, {@link #labelFor} names the source
 * instead - see AGENTS.md.
 * <p>Labels carry their own trailing space, matching the other panel stats. Keep them short: the
 * panel draws them in roughly 129px (see {@code CollectionLogOverlay#cornerTextMaxWidth}), which
 * "Beginner caskets: ", the widest, comes within 7px of. {@code KillCountLabelWidthTest} enforces
 * it.
 */
@Getter
@RequiredArgsConstructor
public enum KillCountKind
{
	/** The default, and the fallback for anything unrecognised. */
	KILLS("Kills: "),
	/** Chest openings, which name no verb at all ("Your Barrows chest count is:"). */
	CHESTS("Chests: "),
	/** Wintertodt and Zalcano ("Your subdued X count is:"). */
	SUBDUES("Subdues: "),
	/** The Gauntlet, and the raids' "Your completed X count is:" form. */
	COMPLETIONS("Completions: "),
	/** Hespori. */
	HARVESTS("Harvests: "),
	/** Yama, whose kills are tracked as a success count. */
	SUCCESSES("Successes: "),
	/** Barbarian Assault. */
	TICKETS("Tickets: "),
	/** Doom of Mokhaiotl - only delve 8+ is counted at all. */
	DEEP_DELVES("Deep delves: "),
	/** Guardians of the Rift. */
	RIFTS("Rifts: "),
	/** Hallowed Sepulchre floor completions. */
	FLOORS("Floors: "),
	/** Hallowed Sepulchre Grand Hallowed Coffin openings. */
	COFFINS("Coffins: "),
	/** Hunter Guild rumours. */
	RUMOURS("Rumours: ");

	private static final String TREASURE_TRAILS_SUFFIX = " treasure trails";

	// Keyed on the lowercased tier: the tracker stores the difficulty word verbatim from chat
	// ("hard Treasure Trails"), so the casing there is the game's, not ours.
	private static final Map<String, String> CLUE_CASKET_LABELS = Map.of(
		"beginner", "Beginner caskets: ",
		"easy", "Easy caskets: ",
		"medium", "Medium caskets: ",
		"hard", "Hard caskets: ",
		"elite", "Elite caskets: ",
		"master", "Master caskets: ");

	// A table rather than a suffix strip of the source name: the raw names are far over the label
	// budget ("Tombs of Amascut: Expert Mode: " measures 216px), so each abbreviation is written to
	// fit. See AGENTS.md.
	private static final Map<String, String> RAID_LABELS = Map.ofEntries(
		Map.entry("chambers of xeric", "CoX completions: "),
		Map.entry("chambers of xeric challenge mode", "CoX CM: "),
		Map.entry("theatre of blood", "ToB completions: "),
		Map.entry("theatre of blood: entry mode", "ToB Entry: "),
		Map.entry("theatre of blood: hard mode", "ToB Hard Mode: "),
		Map.entry("tombs of amascut", "ToA completions: "),
		Map.entry("tombs of amascut: entry mode", "ToA Entry: "),
		Map.entry("tombs of amascut: expert mode", "ToA Expert: "));

	private final String label;

	/**
	 * @param source the correlated kill's source name, or null when no kill was correlated
	 * @return a label naming {@code source} where the generic one would leave the activity
	 *         ambiguous - clue tiers and raid difficulty modes - else {@link #getLabel()}. See
	 *         AGENTS.md for why only those two, and why the label names the casket opened rather
	 *         than the item's own tabs.
	 */
	public String labelFor(String source)
	{
		// Every qualifiable source arrives as COMPLETIONS, so no other kind can be re-labelled by a
		// chance collision with the tables.
		if (source == null || this != COMPLETIONS)
		{
			return label;
		}

		String normalized = source.toLowerCase(Locale.ROOT);
		if (normalized.endsWith(TREASURE_TRAILS_SUFFIX))
		{
			String tier = normalized.substring(0, normalized.length() - TREASURE_TRAILS_SUFFIX.length());
			return CLUE_CASKET_LABELS.getOrDefault(tier, label);
		}
		return RAID_LABELS.getOrDefault(normalized, label);
	}

	/**
	 * @return every label the panel can draw, for the width test that keeps them inside the budget
	 *         documented above.
	 */
	static Collection<String> allLabels()
	{
		List<String> labels = new ArrayList<>();
		for (KillCountKind kind : values())
		{
			labels.add(kind.label);
		}
		labels.addAll(CLUE_CASKET_LABELS.values());
		labels.addAll(RAID_LABELS.values());
		return labels;
	}
}
