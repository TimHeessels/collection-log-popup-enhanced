package com.snakesteak.collectionlogpopupenhanced.rarity;

import com.google.gson.Gson;
import com.snakesteak.collectionlogpopupenhanced.CollectionLogPopupEnhancedConfig;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Expected percentiles/tiers below are derived by replaying the resolver's exact algorithm in a
 * throwaway script against the real bundled rarity-overrides.json (1705 entries as of writing) -
 * see conversation history for the derivation. These aren't hand-picked to "look right"; they're
 * the actual output of the composite-score + percentile-rank math for these real dataset ids.
 */
public class RarityResolverTest
{
	private ItemManager itemManager;
	private RarityResolver resolver;

	@Before
	public void before()
	{
		itemManager = mock(ItemManager.class);
		// Default: no high alch value either, so unstubbed items behave as price-0 like before the
		// high alch fallback was added. Individual tests override this per-id where needed.
		ItemComposition defaultComposition = mock(ItemComposition.class);
		when(itemManager.getItemComposition(anyInt())).thenReturn(defaultComposition);
		CollectionLogPopupEnhancedConfig config = new CollectionLogPopupEnhancedConfig()
		{
		};
		resolver = new RarityResolver(itemManager, config, new Gson());
	}

	@Test
	public void petNameWinsRegardlessOfIdOrPrice()
	{
		assertEquals(RarityTier.PET, resolver.resolve(11849, "Baby mole").getTier());
	}

	@Test
	public void petNameMatchIsCaseInsensitive()
	{
		assertEquals(RarityTier.PET, resolver.resolve(-1, "baby MOLE").getTier());
	}

	// Dataset id 20011: comp_percent 0, tied with 9 other items for the rarest slot in the whole
	// dataset -> percentile 100 under default (unstubbed = zero) prices.
	@Test
	public void tiedRarestCompPercentIsVeryRare()
	{
		assertEquals(RarityTier.VERY_RARE, resolver.resolve(20011, "Not a pet").getTier());
	}

	// Dataset id 22100: comp_percent 0.3 -> percentile ~97.8
	@Test
	public void veryLowCompPercentIsVeryRare()
	{
		assertEquals(RarityTier.VERY_RARE, resolver.resolve(22100, "Not a pet").getTier());
	}

	// Dataset id 4508: comp_percent 1.3 -> percentile ~90.0
	@Test
	public void lowCompPercentIsRare()
	{
		assertEquals(RarityTier.RARE, resolver.resolve(4508, "Not a pet").getTier());
	}

	// Dataset id 4068: comp_percent 4.3 -> percentile ~70.2
	@Test
	public void midCompPercentIsUncommon()
	{
		assertEquals(RarityTier.UNCOMMON, resolver.resolve(4068, "Not a pet").getTier());
	}

	// Dataset id 2615: comp_percent 10.9 -> percentile ~30.5
	@Test
	public void higherCompPercentIsCommon()
	{
		assertEquals(RarityTier.COMMON, resolver.resolve(2615, "Not a pet").getTier());
	}

	// Dataset id 11849: comp_percent 96.8, the unique highest (least rare) value in the dataset.
	@Test
	public void highestCompPercentInDatasetIsCommon()
	{
		assertEquals(RarityTier.COMMON, resolver.resolve(11849, "Not a pet").getTier());
	}

	@Test
	public void unknownItemFallsBackToValueScoreOnly()
	{
		// Synthetic, varying price per id so the value-score distribution isn't degenerate;
		// id 999999 lands on the max synthetic price (1000), so it should rank at the top
		// of the value-score-only distribution used when an item has no completion data.
		when(itemManager.getItemPrice(anyInt())).thenAnswer(invocation ->
		{
			int id = invocation.getArgument(0);
			return (id % 1000) + 1;
		});

		RarityResult result = resolver.resolve(999_999, "Brand new item");
		assertEquals(RarityTier.VERY_RARE, result.getTier());
		assertEquals(1000, result.getPrice());
	}

	// itemId -1 with completely unstubbed (all-zero) prices means there's no usable price signal
	// anywhere in the dataset - this must not be reported as VERY_RARE (that was the actual bug:
	// a fully degenerate/all-tied value-score distribution used to rank as the 100th percentile).
	@Test
	public void noUsablePriceSignalAnywhereDefaultsToCommonInsteadOfFalseVeryRare()
	{
		RarityResult result = resolver.resolve(-1, "Some untradeable unresolved item");
		assertEquals(RarityTier.COMMON, result.getTier());
	}

	@Test
	public void resultExposesCompPercentAndPriceForDisplay()
	{
		when(itemManager.getItemPrice(4508)).thenReturn(509);

		RarityResult result = resolver.resolve(4508, "Not a pet");
		assertEquals(Double.valueOf(1.3), result.getCompPercent());
		assertEquals(509, result.getPrice());
	}

	// Many collection log items have no GE price (untradeable, or tradeable but unlisted) but still
	// have a high alch value, which is a better rarity signal than reporting price 0.
	@Test
	public void fallsBackToHighAlchValueWhenNoGePrice()
	{
		when(itemManager.getItemPrice(4508)).thenReturn(0);
		ItemComposition composition = mock(ItemComposition.class);
		when(composition.getHaPrice()).thenReturn(1000);
		when(itemManager.getItemComposition(4508)).thenReturn(composition);

		RarityResult result = resolver.resolve(4508, "Not a pet");
		assertEquals(1000, result.getPrice());
		assertEquals(true, result.isHighAlch());
	}

	@Test
	public void doesNotFallBackToHighAlchWhenGePriceIsPositive()
	{
		when(itemManager.getItemPrice(4508)).thenReturn(509);
		ItemComposition composition = mock(ItemComposition.class);
		when(composition.getHaPrice()).thenReturn(1000);
		when(itemManager.getItemComposition(4508)).thenReturn(composition);

		RarityResult result = resolver.resolve(4508, "Not a pet");
		assertEquals(509, result.getPrice());
		assertEquals(false, result.isHighAlch());
	}

	@Test
	public void unresolvedItemIdDoesNotFallBackToHighAlch()
	{
		// itemId -1 must short-circuit to price 0 without ever calling getItemComposition(-1)
		RarityResult result = resolver.resolve(-1, "Some untradeable unresolved item");
		assertEquals(0, result.getPrice());
		assertEquals(false, result.isHighAlch());
	}

	// Regression test for the actual reported bug: ItemManager.getItemPrice() sums mapped/bundled item
	// prices with plain int arithmetic and can overflow to a negative number for at least one item.
	// Math.log(negative) is NaN, which used to poison the shared logPriceMin/logPriceMax (Math.min/max
	// propagate NaN forever once it appears) - after that, EVERY item's value score came out NaN, and
	// Arrays.binarySearch treats NaN as "greater than everything", so every single item rated as the
	// 100th percentile regardless of its actual comp% or price. Dataset id 2583 is an arbitrary stand-in
	// for "the one poisoned entry" here; id 11849 (comp 96.8%, the dataset's least-rare item) is the
	// control - it must still come back COMMON even with a NaN-inducing price elsewhere in the dataset.
	@Test
	public void negativePriceOnOneDatasetItemDoesNotPoisonEveryoneElsesPercentile()
	{
		when(itemManager.getItemPrice(2583)).thenReturn(-2);

		RarityResult result = resolver.resolve(11849, "Not a pet");
		assertEquals(RarityTier.COMMON, result.getTier());
	}

	// Same failure mode, but from the other side of the overflow: a price at/near Integer.MAX_VALUE
	// wraps back to negative when "+1" is done in int arithmetic (2147483647 + 1 == Integer.MIN_VALUE),
	// which still feeds Math.log() a negative number -> NaN -> same dataset-wide poisoning as above.
	@Test
	public void maxIntPriceOnOneDatasetItemDoesNotPoisonEveryoneElsesPercentile()
	{
		when(itemManager.getItemPrice(2583)).thenReturn(Integer.MAX_VALUE);

		RarityResult result = resolver.resolve(11849, "Not a pet");
		assertEquals(RarityTier.COMMON, result.getTier());
	}

	// Reported live: an item with NO completion data at all (compPercent null) still came back
	// VERY_RARE with logPriceMin/logPriceMax both NaN - proving the poisoning happens in buildDataset()
	// itself (shared across every resolve() call), not just in the compPercent-known branch.
	@Test
	public void unknownItemStillResolvesSanelyWhenSomeDatasetPriceOverflows()
	{
		when(itemManager.getItemPrice(2583)).thenReturn(Integer.MAX_VALUE);

		RarityResult result = resolver.resolve(-1, "Cupric sulfate (Members)");
		assertEquals(RarityTier.COMMON, result.getTier());
	}
}
