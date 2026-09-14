package com.snakesteak.collectionlogpopupenhanced.droprate;

import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * A source stored as "X (max)" holds the other end of X's rate range rather than being a source of
 * its own. Built inline rather than from the local-only fixture, which predates the convention.
 */
public class DropRateMaxSourceTest
{
	private DropRateResolver resolver;

	@Before
	public void before()
	{
		resolver = new DropRateResolver();
		resolver.reload(Map.of(
			"Doom of Mokhaiotl", Map.of("Avernic treads", 1 / 1350.0, "Demon tear", 1 / 15.0),
			"Doom of Mokhaiotl (max)", Map.of("Avernic treads", 1 / 540.0),
			// The inverted case: the max-scale rate is the rarer end, not the commoner one.
			"The Nightmare", Map.of("Little Nightmare", 1 / 800.0),
			"The Nightmare (max)", Map.of("Little Nightmare", 1 / 4000.0),
			// A second, unrelated source for one item, to keep the genuinely-ambiguous path covered.
			"Tekton", Map.of("Shared item", 1 / 100.0),
			"Zalcano", Map.of("Shared item", 1 / 200.0)));
	}

	@Test
	public void sourceScopedLookupCarriesBothEndsOfTheRange()
	{
		DropRateResolver.SourceRate rate = resolver.dropRateFor("Doom of Mokhaiotl", "Avernic treads");
		assertEquals(1 / 1350.0, rate.getProbability(), 1e-9);
		assertEquals(Double.valueOf(1 / 540.0), rate.getMaxProbability());
		assertEquals(List.of(1 / 1350.0, 1 / 540.0), rate.probabilities());
	}

	@Test
	public void anItemWithNoMaxTwinHasNoRange()
	{
		DropRateResolver.SourceRate rate = resolver.dropRateFor("Doom of Mokhaiotl", "Demon tear");
		assertNull(rate.getMaxProbability());
		assertEquals(List.of(1 / 15.0), rate.probabilities());
	}

	// The whole point of folding: without it the item looks like a drop from two different bosses,
	// which suppresses the single-rate case and names "Doom of Mokhaiotl (max)" as a source.
	@Test
	public void maxSourceIsNotListedAsASourceOfItsOwn()
	{
		List<DropRateResolver.SourceRate> rates = resolver.dropRatesByItemName("Avernic treads");
		assertEquals(1, rates.size());
		assertEquals("Doom of Mokhaiotl", rates.get(0).getSource());
		assertEquals(Double.valueOf(1 / 540.0), rates.get(0).getMaxProbability());
	}

	// A one-source item whose rate is a range must not collapse to its base rate - the caller needs
	// null here so it falls through to the range-rendering path.
	@Test
	public void singleRateIsNullForAnItemWhoseRateIsARange()
	{
		assertNull(resolver.dropProbabilityByItemName("Avernic treads"));
		assertEquals(1 / 15.0, resolver.dropProbabilityByItemName("Demon tear"), 1e-9);
	}

	@Test
	public void soleSourceRateStillReportsAGenuinelyAmbiguousItemAsAmbiguous()
	{
		assertNull(resolver.soleSourceRate("Shared item"));
		assertEquals(2, resolver.dropRatesByItemName("Shared item").size());
	}

	// "max" is the max scale, not the better rate - Little Nightmare is rarer at max scale.
	@Test
	public void anInvertedMaxRateIsStillCarried()
	{
		DropRateResolver.SourceRate rate = resolver.dropRateFor("The Nightmare", "Little Nightmare");
		assertEquals(Double.valueOf(1 / 4000.0), rate.getMaxProbability());
	}

	@Test
	public void queryingTheMaxSourceDirectlyDoesNotRecurse()
	{
		DropRateResolver.SourceRate rate = resolver.dropRateFor("Doom of Mokhaiotl (max)", "Avernic treads");
		assertEquals(1 / 540.0, rate.getProbability(), 1e-9);
		assertNull(rate.getMaxProbability());
	}
}
