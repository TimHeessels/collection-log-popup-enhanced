package com.snakesteak.collectionlogpopupenhanced.killcount;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class KillCountKindTest
{
	// The tracker reconstructs the source from the casket message's difficulty word, which arrives in
	// the game's own casing - lowercase in practice. Each tier is asserted in that form.
	@Test
	public void namesEachClueCasketTier()
	{
		assertEquals("Beginner caskets: ", KillCountKind.COMPLETIONS.labelFor("beginner Treasure Trails"));
		assertEquals("Easy caskets: ", KillCountKind.COMPLETIONS.labelFor("easy Treasure Trails"));
		assertEquals("Medium caskets: ", KillCountKind.COMPLETIONS.labelFor("medium Treasure Trails"));
		assertEquals("Hard caskets: ", KillCountKind.COMPLETIONS.labelFor("hard Treasure Trails"));
		assertEquals("Elite caskets: ", KillCountKind.COMPLETIONS.labelFor("elite Treasure Trails"));
		assertEquals("Master caskets: ", KillCountKind.COMPLETIONS.labelFor("master Treasure Trails"));
	}

	@Test
	public void namesEachRaidAndItsDifficultyModes()
	{
		assertEquals("CoX completions: ", KillCountKind.COMPLETIONS.labelFor("Chambers of Xeric"));
		assertEquals("CoX CM: ", KillCountKind.COMPLETIONS.labelFor("Chambers of Xeric Challenge Mode"));
		assertEquals("ToB completions: ", KillCountKind.COMPLETIONS.labelFor("Theatre of Blood"));
		assertEquals("ToB Entry: ", KillCountKind.COMPLETIONS.labelFor("Theatre of Blood: Entry Mode"));
		assertEquals("ToB Hard Mode: ", KillCountKind.COMPLETIONS.labelFor("Theatre of Blood: Hard Mode"));
		assertEquals("ToA completions: ", KillCountKind.COMPLETIONS.labelFor("Tombs of Amascut"));
		assertEquals("ToA Entry: ", KillCountKind.COMPLETIONS.labelFor("Tombs of Amascut: Entry Mode"));
		assertEquals("ToA Expert: ", KillCountKind.COMPLETIONS.labelFor("Tombs of Amascut: Expert Mode"));
	}

	@Test
	public void fallsBackToTheGenericLabelForAnUnnamedSource()
	{
		// The Gauntlet is deliberately not in the table - see AGENTS.md.
		assertEquals("Completions: ", KillCountKind.COMPLETIONS.labelFor("Gauntlet"));
		assertEquals("Completions: ", KillCountKind.COMPLETIONS.labelFor("Corrupted Gauntlet"));
	}

	// An unlock with no correlated kill behind it renders no kill count stat at all, but the label
	// must still be well-defined for a caller that asks anyway.
	@Test
	public void fallsBackToTheGenericLabelWithNoSource()
	{
		assertEquals("Kills: ", KillCountKind.KILLS.labelFor(null));
		assertEquals("Completions: ", KillCountKind.COMPLETIONS.labelFor(null));
	}

	// Only COMPLETIONS is qualified, so a source that happens to key into a table can't re-label a
	// kind that already says what it counts.
	@Test
	public void doesNotQualifyOtherKinds()
	{
		assertEquals("Kills: ", KillCountKind.KILLS.labelFor("hard Treasure Trails"));
		assertEquals("Chests: ", KillCountKind.CHESTS.labelFor("Tombs of Amascut: Expert Mode"));
		assertEquals("Rumours: ", KillCountKind.RUMOURS.labelFor("Hunter Guild"));
	}

	// A tier the table doesn't know renders the generic label rather than a raw, unbudgeted one.
	@Test
	public void doesNotQualifyAnUnknownClueTier()
	{
		assertEquals("Completions: ", KillCountKind.COMPLETIONS.labelFor("legendary Treasure Trails"));
	}
}
