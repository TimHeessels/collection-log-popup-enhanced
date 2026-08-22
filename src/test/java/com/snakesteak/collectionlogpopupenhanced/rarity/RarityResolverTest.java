package com.snakesteak.collectionlogpopupenhanced.rarity;

import com.google.gson.Gson;
import com.snakesteak.collectionlogpopupenhanced.CollectionLogPopupEnhancedConfig;
import com.snakesteak.collectionlogpopupenhanced.droprate.DropRateResolver;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Expected percentiles/tiers below are derived by replaying the resolver's exact algorithm in a
 * throwaway script against the real dataset fixture below (1705 scored entries as of writing) -
 * see conversation history for the derivation. These aren't hand-picked to "look right"; they're
 * the actual output of the composite-score + percentile-rank math for these real dataset ids.
 *
 * The fixture is gitignored (see src/test/resources/local-only/README.md) - these tests are
 * skipped rather than failed if it isn't present locally.
 */
public class RarityResolverTest
{
	private static final String FIXTURE = "/local-only/collection-log.json";

	private ItemManager itemManager;
	private DropRateResolver dropRateResolver;
	private RarityResolver resolver;
	private TestConfig config;

	/**
	 * Defaults to the real config's values; individual tests override the basis and the gp cutoffs.
	 */
	private static final class TestConfig implements CollectionLogPopupEnhancedConfig
	{
		private RarityBasis basis = CollectionLogPopupEnhancedConfig.super.rarityBasis();
		private int uncommon = CollectionLogPopupEnhancedConfig.super.valueUncommonThreshold();
		private int rare = CollectionLogPopupEnhancedConfig.super.valueRareThreshold();
		private int veryRare = CollectionLogPopupEnhancedConfig.super.valueVeryRareThreshold();

		@Override
		public RarityBasis rarityBasis()
		{
			return basis;
		}

		@Override
		public int valueUncommonThreshold()
		{
			return uncommon;
		}

		@Override
		public int valueRareThreshold()
		{
			return rare;
		}

		@Override
		public int valueVeryRareThreshold()
		{
			return veryRare;
		}
	}

	@Before
	public void before()
	{
		itemManager = mock(ItemManager.class);
		// Default: no high alch value either, so unstubbed items behave as price-0 like before the
		// high alch fallback was added. Individual tests override this per-id where needed.
		ItemComposition defaultComposition = mock(ItemComposition.class);
		when(itemManager.getItemComposition(anyInt())).thenReturn(defaultComposition);
		// Default: no drop-rate data for any item, so unstubbed items behave as before the drop-rate
		// fallback was added. Individual tests override this per-name where needed.
		dropRateResolver = mock(DropRateResolver.class);
		when(dropRateResolver.dropProbabilityByItemName(anyString())).thenReturn(null);
		config = new TestConfig();
		resolver = new RarityResolver(itemManager, config, dropRateResolver);
		resolver.reload(loadFixture());
	}

	private static Map<String, RarityResolver.CompletionEntry> loadFixture()
	{
		InputStream stream = RarityResolverTest.class.getResourceAsStream(FIXTURE);
		Assume.assumeTrue("Local fixture " + FIXTURE + " not present - see src/test/resources/local-only/README.md", stream != null);
		Gson gson = new Gson();
		try (Reader reader = new InputStreamReader(stream))
		{
			return gson.fromJson(reader, RarityResolver.DATASET_TYPE);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
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

	// An item with a price but no wiki completion score is bucketed on the same absolute gp cutoffs
	// the VALUE basis uses, so an identical price can't tier one way here and another way there.
	// This used to rank the price against a synthetic distribution, which called 1000gp VERY_RARE
	// purely because it topped that distribution - now 1000gp is simply below every cutoff.
	@Test
	public void unknownItemWithPriceIsBucketedByThreshold()
	{
		when(itemManager.getItemPrice(anyInt())).thenAnswer(invocation ->
		{
			int id = invocation.getArgument(0);
			return (id % 1000) + 1;
		});

		RarityResult result = resolver.resolve(999_999, "Brand new item");
		assertEquals(RarityTier.COMMON, result.getTier());
		assertEquals(1000, result.getPrice());
	}

	@Test
	public void unknownItemWithHighPriceIsBucketedByThreshold()
	{
		when(itemManager.getItemPrice(999_999)).thenReturn(2_000_000);

		RarityResult result = resolver.resolve(999_999, "Brand new expensive item");
		assertEquals(RarityTier.RARE, result.getTier());
	}

	// itemId -1 with completely unstubbed (all-zero) prices means there's no usable price signal
	// anywhere in the dataset - this must not be reported as VERY_RARE (that was the actual bug:
	// a fully degenerate/all-tied value-score distribution used to rank as the 100th percentile).
	// The item name isn't in the drop-rate mock either, so there's genuinely nothing to rank against.
	@Test
	public void noUsablePriceSignalAnywhereDefaultsToCommonInsteadOfFalseVeryRare()
	{
		RarityResult result = resolver.resolve(-1, "Some untradeable unresolved item");
		assertEquals(RarityTier.COMMON, result.getTier());
	}

	// Reported live: a brand-new boss-exclusive drop (e.g. "Crimson kisten") has no wiki completion
	// score yet AND no GE/alch price (untradeable), so both existing fallback signals are empty -
	// it used to always resolve as COMMON regardless of how rare it actually is. Drop probability is
	// the last resort: a very low per-kill rate should still rank as rare.
	@Test
	public void fallsBackToDropRateWhenNoCompletionAndNoPriceAreAvailable()
	{
		when(dropRateResolver.dropProbabilityByItemName("Crimson kisten")).thenReturn(0.0019230769230769162);

		RarityResult result = resolver.resolve(-1, "Crimson kisten");
		assertEquals(0, result.getPrice());
		assertEquals(RarityTier.VERY_RARE, result.getTier());
	}

	// If the item genuinely isn't in the drop-rate dataset either (mock default: null for every
	// name), there's still truly nothing to rank against, so it must keep defaulting to COMMON
	// rather than erroring or fabricating a score.
	@Test
	public void unknownItemWithNoDropRateEitherStillDefaultsToCommon()
	{
		RarityResult result = resolver.resolve(-1, "Some item with genuinely no data anywhere");
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

	// --- Value basis: absolute gp thresholds (defaults 100k / 1m / 10m) ---

	private RarityTier tierForPrice(int price)
	{
		config.basis = RarityBasis.VALUE;
		when(itemManager.getItemPrice(4508)).thenReturn(price);
		return resolver.resolve(4508, "Not a pet").getTier();
	}

	@Test
	public void valueBasisBucketsEachBand()
	{
		assertEquals(RarityTier.COMMON, tierForPrice(99_999));
		assertEquals(RarityTier.UNCOMMON, tierForPrice(500_000));
		assertEquals(RarityTier.RARE, tierForPrice(5_000_000));
		assertEquals(RarityTier.VERY_RARE, tierForPrice(50_000_000));
	}

	// A price exactly on a cutoff belongs to the higher tier ("worth at least X").
	@Test
	public void valueBasisTreatsExactThresholdAsTheHigherTier()
	{
		assertEquals(RarityTier.UNCOMMON, tierForPrice(100_000));
		assertEquals(RarityTier.RARE, tierForPrice(1_000_000));
		assertEquals(RarityTier.VERY_RARE, tierForPrice(10_000_000));
	}

	// A zero price is simply below the lowest cutoff - there's no drop-rate fallback on this path,
	// so even an item with a very rare drop rate stays COMMON when it's worth nothing.
	@Test
	public void valueBasisTreatsUnpricedItemAsCommonWithoutDropRateFallback()
	{
		when(dropRateResolver.dropProbabilityByItemName("Crimson kisten")).thenReturn(0.0019230769230769162);
		config.basis = RarityBasis.VALUE;

		RarityResult result = resolver.resolve(-1, "Crimson kisten");
		assertEquals(0, result.getPrice());
		assertEquals(RarityTier.COMMON, result.getTier());
	}

	// The GE -> high alch fallback still feeds the threshold comparison.
	@Test
	public void valueBasisBucketsOnHighAlchWhenNoGePrice()
	{
		config.basis = RarityBasis.VALUE;
		when(itemManager.getItemPrice(4508)).thenReturn(0);
		ItemComposition composition = mock(ItemComposition.class);
		when(composition.getHaPrice()).thenReturn(2_000_000);
		when(itemManager.getItemComposition(4508)).thenReturn(composition);

		RarityResult result = resolver.resolve(4508, "Not a pet");
		assertEquals(RarityTier.RARE, result.getTier());
		assertEquals(true, result.isHighAlch());
	}

	@Test
	public void petStillWinsAheadOfThresholdsOnValueBasis()
	{
		config.basis = RarityBasis.VALUE;
		when(itemManager.getItemPrice(anyInt())).thenReturn(0);

		assertEquals(RarityTier.PET, resolver.resolve(11849, "Baby mole").getTier());
	}

	// A user who types Rare below Uncommon still gets monotonic tiers rather than an unreachable band.
	@Test
	public void outOfOrderThresholdsAreSortedIntoMonotonicBands()
	{
		config.uncommon = 5_000_000;
		config.rare = 1_000_000;
		config.veryRare = 10_000_000;

		assertEquals(RarityTier.COMMON, tierForPrice(999_999));
		assertEquals(RarityTier.UNCOMMON, tierForPrice(1_000_000));
		assertEquals(RarityTier.RARE, tierForPrice(5_000_000));
		assertEquals(RarityTier.VERY_RARE, tierForPrice(10_000_000));
	}

	@Test
	public void negativeThresholdsAreClampedToZero()
	{
		config.uncommon = -1;
		config.rare = 1_000_000;
		config.veryRare = 10_000_000;

		// Clamped to 0, so even a free item clears the lowest cutoff.
		assertEquals(RarityTier.UNCOMMON, tierForPrice(0));
	}

	// --- Combination basis: still percentile-ranked, but the value half honours the thresholds ---

	// The regression guard that proves the gp fields actually reach the DEFAULT basis: raising a
	// threshold past the item's price drops its value score, so its composite score falls too.
	@Test
	public void raisingThresholdPastPriceLowersCombinationValueScore()
	{
		when(itemManager.getItemPrice(4508)).thenReturn(2_000_000);

		double before = resolver.resolve(4508, "Not a pet").getValueScore();
		config.rare = 50_000_000;
		config.veryRare = 100_000_000;
		double after = resolver.resolve(4508, "Not a pet").getValueScore();

		assertEquals(2 / 3.0, before, 1e-9);
		assertEquals(1 / 3.0, after, 1e-9);
	}

	// The inconsistency this change closes: an item with a price but no comp% must tier the same way
	// whether the basis is VALUE or COMBINATION, since both are value-driven for such an item.
	@Test
	public void combinationAgreesWithValueForPricedItemWithNoCompPercent()
	{
		when(itemManager.getItemPrice(999_999)).thenReturn(2_000_000);

		config.basis = RarityBasis.COMBINATION;
		RarityTier combination = resolver.resolve(999_999, "Brand new item").getTier();
		config.basis = RarityBasis.VALUE;
		RarityTier value = resolver.resolve(999_999, "Brand new item").getTier();

		assertEquals(value, combination);
		assertEquals(RarityTier.RARE, combination);
	}

	// The RARITY basis ranks comp% only and must stay completely unaffected by the gp thresholds.
	@Test
	public void rarityBasisIsUnaffectedByThresholds()
	{
		config.basis = RarityBasis.RARITY;
		when(itemManager.getItemPrice(4508)).thenReturn(50_000_000);

		RarityTier expensive = resolver.resolve(4508, "Not a pet").getTier();
		config.uncommon = 1;
		config.rare = 2;
		config.veryRare = 3;
		RarityTier afterThresholdChange = resolver.resolve(4508, "Not a pet").getTier();

		assertEquals(RarityTier.RARE, expensive);
		assertEquals(expensive, afterThresholdChange);
	}
}
