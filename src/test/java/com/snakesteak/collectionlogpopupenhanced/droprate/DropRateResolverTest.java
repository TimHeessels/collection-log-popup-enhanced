package com.snakesteak.collectionlogpopupenhanced.droprate;

import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Loaded against the real bundled drop-rates.json (see scripts/generate-drop-rates.py). Cockatrice
 * -> Cockatrice head is a simple, verified 1/1000 single-roll drop, used here as a stable
 * reference point.
 */
public class DropRateResolverTest
{
	private DropRateResolver resolver;

	@Before
	public void before()
	{
		resolver = new DropRateResolver(new Gson());
	}

	@Test
	public void unknownSourceReturnsNull()
	{
		assertNull(resolver.dropProbability("Not a real boss", "Some item"));
	}

	@Test
	public void knownSourceUnknownItemReturnsNull()
	{
		assertNull(resolver.dropProbability("Cockatrice", "Not a real item"));
	}

	@Test
	public void sourceLookupIsCaseInsensitive()
	{
		assertEquals(resolver.dropProbability("Cockatrice", "Cockatrice head"),
			resolver.dropProbability("COCKATRICE", "Cockatrice head"), 0.0001);
	}

	@Test
	public void itemLookupIsCaseInsensitive()
	{
		assertEquals(resolver.dropProbability("Cockatrice", "Cockatrice head"),
			resolver.dropProbability("Cockatrice", "cockatrice HEAD"), 0.0001);
	}

	@Test
	public void knownDropRateMatchesWikiFraction()
	{
		assertEquals(0.001, resolver.dropProbability("Cockatrice", "Cockatrice head"), 0.0001);
	}
}
