package com.snakesteak.collectionlogpopupenhanced.killcount;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What the number attached to an unlock counts, and the panel label that says so. Derived from the
 * chat wording rather than a per-boss table (see {@link KillCountTracker}), so a new activity picks
 * up the right label as long as it phrases its message like an existing one.
 * <p>Labels carry their own trailing space, matching the other panel stats. Keep them short: the
 * panel draws them in roughly 129px (see {@code CollectionLogOverlay#cornerTextMaxWidth}), and
 * "Completions: " is already close to that budget.
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

	private final String label;
}
