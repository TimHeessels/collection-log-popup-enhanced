package com.snakesteak.collectionlogpopupenhanced.droprate;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Loaded against the real dataset fixture below (see the osrs-collection-log-data repo's
 * generate-drop-rates.py). Abyssal Sire -> Unsired is a simple, verified 1/100 single-roll drop,
 * used here as a stable reference point.
 *
 * The fixture is gitignored (see src/test/resources/local-only/README.md) - these tests are
 * skipped rather than failed if it isn't present locally.
 */
public class DropRateResolverTest
{
	private static final String FIXTURE = "/local-only/drop-rates.json";

	private DropRateResolver resolver;

	@Before
	public void before()
	{
		resolver = new DropRateResolver();
		resolver.reload(loadFixture());
	}

	private static Map<String, Map<String, Double>> loadFixture()
	{
		InputStream stream = DropRateResolverTest.class.getResourceAsStream(FIXTURE);
		Assume.assumeTrue("Local fixture " + FIXTURE + " not present - see src/test/resources/local-only/README.md", stream != null);
		Gson gson = new Gson();
		try (Reader reader = new InputStreamReader(stream))
		{
			return gson.fromJson(reader, DropRateResolver.DATASET_TYPE);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	@Test
	public void unknownSourceReturnsNull()
	{
		assertNull(resolver.dropProbability("Not a real boss", "Some item"));
	}

	@Test
	public void knownSourceUnknownItemReturnsNull()
	{
		assertNull(resolver.dropProbability("Abyssal Sire", "Not a real item"));
	}

	@Test
	public void sourceLookupIsCaseInsensitive()
	{
		assertEquals(resolver.dropProbability("Abyssal Sire", "Unsired"),
			resolver.dropProbability("ABYSSAL SIRE", "Unsired"), 0.0001);
	}

	@Test
	public void itemLookupIsCaseInsensitive()
	{
		assertEquals(resolver.dropProbability("Abyssal Sire", "Unsired"),
			resolver.dropProbability("Abyssal Sire", "UNSIRED"), 0.0001);
	}

	@Test
	public void knownDropRateMatchesWikiFraction()
	{
		assertEquals(0.01, resolver.dropProbability("Abyssal Sire", "Unsired"), 0.0001);
	}

	@Test
	public void itemNameLookupResolvesAnUnambiguousItem()
	{
		// Jar of souls only drops from Cerberus - single source, so this should resolve the same
		// as looking it up by source directly.
		assertEquals(resolver.dropProbability("Cerberus", "Jar of souls"),
			resolver.dropProbabilityByItemName("Jar of souls"), 0.0001);
	}

	@Test
	public void itemNameLookupReturnsNullForAnAmbiguousItem()
	{
		// Hydra tail drops from more than one real source (Alchemical Hydra, Colossal Hydra, Hydra)
		// at different rates - there's no single correct rate to show without knowing which one, so
		// this must return null rather than guessing.
		assertNull(resolver.dropProbabilityByItemName("Hydra tail"));
	}

	@Test
	public void dropRatesByItemNameReturnsEveryCandidateForAnAmbiguousItem()
	{
		List<DropRateResolver.SourceRate> matches = resolver.dropRatesByItemName("Hydra tail");
		assertEquals(3, matches.size());
		assertTrue(matches.stream().anyMatch(match -> match.getSource().equalsIgnoreCase("Alchemical Hydra")));
	}

	@Test
	public void dropRatesByItemNameReturnsSingleEntryForAnUnambiguousItem()
	{
		List<DropRateResolver.SourceRate> matches = resolver.dropRatesByItemName("Jar of souls");
		assertEquals(1, matches.size());
		assertEquals("Cerberus", matches.get(0).getSource());
	}

	@Test
	public void dropRatesByItemNameReturnsEmptyForAnUnknownItem()
	{
		assertTrue(resolver.dropRatesByItemName("Not a real item").isEmpty());
	}
}
