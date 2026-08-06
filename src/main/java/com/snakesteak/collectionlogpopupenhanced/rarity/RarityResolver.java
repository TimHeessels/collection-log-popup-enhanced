package com.snakesteak.collectionlogpopupenhanced.rarity;

import com.google.common.reflect.TypeToken;
import com.snakesteak.collectionlogpopupenhanced.CollectionLogPopupEnhancedConfig;
import com.snakesteak.collectionlogpopupenhanced.droprate.DropRateResolver;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;

/**
 * Resolves a collection log item's rarity tier from a composite of two signals:
 * - completion: how many WikiSync-synced players have obtained it (rarer = higher score)
 * - value: GE price, log-scaled since price spans orders of magnitude
 * The composite score is bucketed into tiers by percentile rank across the whole dataset, rather
 * than fixed cutoffs, so the tier distribution stays consistent as items get added over time.
 * For items missing a completion score (e.g. recently added, not yet scored by the wiki) with no
 * usable price either (untradeable, no GE listing, no alch value), a third fallback ranks against
 * per-kill drop probability instead - see {@link #resolve} - so a rare-but-unpriced item doesn't
 * silently default to the common tier just because no signal is populated yet.
 * Pets are matched by name ahead of all of this - see {@link #petIdForName}, derived from the same
 * dataset's "All Pets" tab rather than a hand-maintained list, since pets never enter the player's
 * inventory or land on the ground when unlocked (they attach directly as a follower NPC), so
 * {@link ItemIdResolver}'s inventory/ground/GE-search pipeline can never find them any other way.
 */
@Slf4j
@Singleton
public class RarityResolver
{
	private static final String PET_TAB = "All Pets";

	static final Type DATASET_TYPE = new TypeToken<Map<String, CompletionEntry>>()
	{
	}.getType();

	private static final double COMPLETION_WEIGHT = 0.6;
	private static final double VALUE_WEIGHT = 0.4;

	// Percentile cutoffs (not user-configurable - "percentile" isn't a meaningful knob for players
	// to tune on an already-composite score).
	private static final double UNCOMMON_PERCENTILE = 50;
	private static final double RARE_PERCENTILE = 80;
	private static final double VERY_RARE_PERCENTILE = 95;

	private final ItemManager itemManager;
	private final CollectionLogPopupEnhancedConfig config;
	private final DropRateResolver dropRateResolver;
	private volatile CompletionData completionData;

	@Inject
	public RarityResolver(ItemManager itemManager, CollectionLogPopupEnhancedConfig config, DropRateResolver dropRateResolver)
	{
		this.itemManager = itemManager;
		this.config = config;
		this.dropRateResolver = dropRateResolver;
		this.completionData = new CompletionData(Map.of());
	}

	/**
	 * Replaces the completion dataset with a freshly fetched or cached copy - called by
	 * {@code RemoteRarityOverridesUpdater} once it has a parsed, non-empty replacement. Safe to call
	 * from any thread; readers always see either the old or new dataset, never a partial one.
	 */
	void reload(Map<String, CompletionEntry> raw)
	{
		this.completionData = new CompletionData(raw);
	}

	/**
	 * @return the pet's item id, or {@code null} if {@code name} isn't a known pet (i.e. no dataset
	 *         entry has it tagged with the "All Pets" tab)
	 */
	Integer petIdForName(String name)
	{
		return completionData.petIdByName.get(name);
	}

	/**
	 * @return the collection log tab name(s) (i.e. boss/activity source(s)) that can drop {@code
	 *         itemName}, per the wiki dataset - empty if {@code itemName} isn't a known collection log
	 *         item or has no tab data.
	 */
	public List<String> tabsForItemName(String itemName)
	{
		return completionData.tabsByItemName.getOrDefault(itemName, List.of());
	}

	/**
	 * @param count how many distinct item ids to return
	 * @return up to {@code count} distinct, randomly chosen item ids from the collection log
	 *         completion dataset (fewer if the dataset is smaller than {@code count}, empty if it
	 *         failed to load). Used by the "::clogtest" dev command.
	 */
	public List<Integer> randomItemIds(int count)
	{
		CompletionData data = completionData;
		if (data.ids.isEmpty() || count <= 0)
		{
			return List.of();
		}
		List<Integer> shuffled = new ArrayList<>(data.ids);
		Collections.shuffle(shuffled, ThreadLocalRandom.current());
		return shuffled.subList(0, Math.min(count, shuffled.size()));
	}

	/**
	 * @param itemId resolved item id, or -1 if it couldn't be resolved (see ItemIdResolver). Falls
	 *                back to a value-score-only composite using price 0 in that case, same as an
	 *                item with no GE price at all.
	 */
	public RarityResult resolve(int itemId, String itemName)
	{
		CompletionData data = completionData;
		int price = getPrice(itemId);
		boolean highAlch = isHighAlchPrice(itemId);
		int alchPrice = itemId >= 0 ? itemManager.getItemComposition(itemId).getHaPrice() : 0;

		if (data.petIdByName.containsKey(itemName))
		{
			return new RarityResult(RarityTier.PET, itemId, price, highAlch, compPercent(data, itemId), null, 0, 100, 0, 0, 0, alchPrice);
		}

		if (data.byId.isEmpty())
		{
			return new RarityResult(RarityTier.COMMON, itemId, price, highAlch, null, null, 0, 0, 0, 0, 0, alchPrice);
		}

		Dataset dataset = buildDataset(data);
		Double compPercent = compPercent(data, itemId);
		RarityBasis basis = config.rarityBasis();

		double score;
		double[] distribution;
		Double completionScore = compPercent != null ? 1 - (compPercent / 100.0) : null;
		double valueScore = valueScore(itemId, dataset);

		if (basis == RarityBasis.RARITY)
		{
			if (completionScore == null)
			{
				// No completion data for this item to rank rarity-only against - nothing to back a
				// tier with.
				return new RarityResult(RarityTier.COMMON, itemId, price, highAlch, null, null, valueScore, 0,
					dataset.compositeScores.length, dataset.logPriceMin, dataset.logPriceMax, alchPrice);
			}
			score = completionScore;
			distribution = dataset.completionScores;
		}
		else if (basis == RarityBasis.VALUE)
		{
			if (price <= 0 || dataset.positivePricedValueScores.length == 0)
			{
				Double dropRateScore = dropRarityScore(itemName);
				if (dropRateScore != null && dataset.dropRateScores.length > 0)
				{
					score = dropRateScore;
					distribution = dataset.dropRateScores;
				}
				else
				{
					return new RarityResult(RarityTier.COMMON, itemId, price, highAlch, compPercent, completionScore,
						valueScore, 0, dataset.compositeScores.length, dataset.logPriceMin, dataset.logPriceMax, alchPrice);
				}
			}
			else
			{
				score = valueScore;
				distribution = dataset.positivePricedValueScores;
			}
		}
		else if (completionScore != null)
		{
			score = COMPLETION_WEIGHT * completionScore + VALUE_WEIGHT * valueScore;
			distribution = dataset.compositeScores;
		}
		else if (price > 0 && dataset.positivePricedValueScores.length > 0)
		{
			score = valueScore;
			distribution = dataset.positivePricedValueScores;
		}
		else
		{
			// No completion data for this item, and no usable price either (untradeable, unlisted, or
			// the GE price cache hasn't finished loading yet) - fall back to per-kill drop probability,
			// the last signal available for a genuinely new/unpriced item. Only if that's ALSO
			// unavailable (not in drop-rates.json, or ambiguous across multiple sources) is there
			// nothing left to legitimately rank against.
			Double dropRateScore = dropRarityScore(itemName);
			if (dropRateScore != null && dataset.dropRateScores.length > 0)
			{
				score = dropRateScore;
				distribution = dataset.dropRateScores;
			}
			else
			{
				return new RarityResult(RarityTier.COMMON, itemId, price, highAlch, null, null, valueScore, 0,
					dataset.compositeScores.length, dataset.logPriceMin, dataset.logPriceMax, alchPrice);
			}
		}

		double percentile = percentileRank(distribution, score);
		RarityTier tier = bucketByPercentile(percentile);
		return new RarityResult(tier, itemId, price, highAlch, compPercent, completionScore, valueScore, percentile,
			dataset.compositeScores.length, dataset.logPriceMin, dataset.logPriceMax, alchPrice);
	}

	private static Double compPercent(CompletionData data, int itemId)
	{
		if (itemId < 0)
		{
			return null;
		}
		CompletionEntry entry = data.byId.get(itemId);
		return entry != null ? entry.comp : null;
	}

	/**
	 * GE price where available; falls back to high alch value for items with no GE price, since an
	 * alch value is still a better rarity signal than 0.
	 */
	private int getPrice(int itemId)
	{
		if (itemId < 0)
		{
			return 0;
		}
		int gePrice = itemManager.getItemPrice(itemId);
		if (gePrice > 0)
		{
			return gePrice;
		}
		return itemManager.getItemComposition(itemId).getHaPrice();
	}

	/**
	 * @return whether getPrice(itemId) fell back to the high alch value rather than a real GE price.
	 */
	private boolean isHighAlchPrice(int itemId)
	{
		return itemId >= 0 && itemManager.getItemPrice(itemId) <= 0;
	}

	/**
	 * @return a 0-1 rarity score (rarer = closer to 1) from {@code itemName}'s per-kill drop
	 *         probability, or {@code null} if it isn't in the drop-rate dataset, or is a notable drop
	 *         from more than one source at different rates (see
	 *         {@link DropRateResolver#dropProbabilityByItemName}). Unlike price, drop probabilities
	 *         are already bounded 0-1 so no log-scaling is needed before ranking them.
	 */
	private Double dropRarityScore(String itemName)
	{
		Double probability = dropRateResolver.dropProbabilityByItemName(itemName);
		return probability != null ? 1 - probability : null;
	}

	private double valueScore(int itemId, Dataset dataset)
	{
		double logPrice = logPrice(getPrice(itemId));
		if (dataset.logPriceMax <= dataset.logPriceMin)
		{
			return 0;
		}
		double normalized = (logPrice - dataset.logPriceMin) / (dataset.logPriceMax - dataset.logPriceMin);
		return Math.max(0, Math.min(1, normalized));
	}

	/**
	 * ItemManager.getItemPrice() sums bundled item prices with plain int arithmetic, which can
	 * overflow to a negative (or near-Integer.MAX_VALUE) number for at least one item in the dataset.
	 * Math.log() of a negative input is NaN, which poisons Math.min/Math.max and corrupts the
	 * min/max for every other item's percentile too - so prices are clamped to non-negative, and the
	 * "+1" is done in double arithmetic so a price near Integer.MAX_VALUE can't wrap back to negative.
	 */
	private static double logPrice(int price)
	{
		return Math.log(Math.max(0, price) + 1.0);
	}

	private static RarityTier bucketByPercentile(double percentile)
	{
		if (percentile >= VERY_RARE_PERCENTILE)
		{
			return RarityTier.VERY_RARE;
		}
		if (percentile >= RARE_PERCENTILE)
		{
			return RarityTier.RARE;
		}
		if (percentile >= UNCOMMON_PERCENTILE)
		{
			return RarityTier.UNCOMMON;
		}
		return RarityTier.COMMON;
	}

	private static double percentileRank(double[] sortedAscending, double value)
	{
		int idx = Arrays.binarySearch(sortedAscending, value);
		int countLessOrEqual;
		if (idx >= 0)
		{
			countLessOrEqual = idx + 1;
			while (countLessOrEqual < sortedAscending.length && sortedAscending[countLessOrEqual] == value)
			{
				countLessOrEqual++;
			}
		}
		else
		{
			countLessOrEqual = -idx - 1;
		}
		return 100.0 * countLessOrEqual / sortedAscending.length;
	}

	/**
	 * Rebuilt on every call rather than cached: it's ~1700 in-memory map lookups, collection log
	 * unlocks are rare, and recomputing avoids serving stale percentile cutoffs from a price
	 * snapshot taken before ItemManager finished its own background price refresh.
	 */
	private Dataset buildDataset(CompletionData data)
	{
		// Only entries with a real completion score - items pending a wiki score (comp == null) have
		// nothing to rank a completion-based percentile against.
		List<Map.Entry<Integer, CompletionEntry>> scored = data.byId.entrySet().stream()
			.filter(entry -> entry.getValue().comp != null)
			.collect(Collectors.toList());

		int n = scored.size();
		double[] logPrices = new double[n];
		double[] compPercents = new double[n];
		boolean[] hasPositivePrice = new boolean[n];
		double logPriceMin = Double.POSITIVE_INFINITY;
		double logPriceMax = Double.NEGATIVE_INFINITY;

		int i = 0;
		for (Map.Entry<Integer, CompletionEntry> entry : scored)
		{
			int price = getPrice(entry.getKey());
			double logPrice = logPrice(price);
			logPrices[i] = logPrice;
			compPercents[i] = entry.getValue().comp;
			hasPositivePrice[i] = price > 0;
			logPriceMin = Math.min(logPriceMin, logPrice);
			logPriceMax = Math.max(logPriceMax, logPrice);
			i++;
		}

		double[] compositeScores = new double[n];
		double[] completionScores = new double[n];
		// Only items with a genuine positive price - unpriced items (price 0, common in this dataset)
		// would all tie at the minimum value score, and since percentile = "% of population at or
		// below you", tying with a huge chunk of the population perversely ranks near the TOP. The
		// main composite path avoids this because completion score still breaks the tie; this
		// fallback (no completion data either) has nothing else to break it with.
		double[] positivePricedValueScores = new double[n];
		int positivePricedCount = 0;
		double range = logPriceMax - logPriceMin;
		for (int j = 0; j < n; j++)
		{
			double valueScore = range > 0 ? Math.max(0, Math.min(1, (logPrices[j] - logPriceMin) / range)) : 0;
			double completionScore = 1 - (compPercents[j] / 100.0);
			compositeScores[j] = COMPLETION_WEIGHT * completionScore + VALUE_WEIGHT * valueScore;
			completionScores[j] = completionScore;
			if (hasPositivePrice[j])
			{
				positivePricedValueScores[positivePricedCount++] = valueScore;
			}
		}
		positivePricedValueScores = Arrays.copyOf(positivePricedValueScores, positivePricedCount);

		Arrays.sort(compositeScores);
		Arrays.sort(completionScores);
		Arrays.sort(positivePricedValueScores);

		// Comp-less items (comp == null) are exactly the ones the drop-rate fallback exists for, so
		// the ranking distribution is built from that same population's drop rates rather than from
		// "scored" - an item with a completion score never needs this fallback and would just dilute
		// the distribution's rarity range.
		double[] dropRateScores = data.byId.values().stream()
			.filter(entry -> entry.comp == null && entry.name != null)
			.map(entry -> dropRarityScore(entry.name))
			.filter(Objects::nonNull)
			.mapToDouble(Double::doubleValue)
			.sorted()
			.toArray();

		return new Dataset(compositeScores, completionScores, positivePricedValueScores, dropRateScores, logPriceMin, logPriceMax);
	}

	/**
	 * One item's entry in collection-log.json. name/tabs come from the wiki's canonical item list,
	 * comp from its completion-percentage dataset (see the osrs-collection-log-data repo's
	 * generate-collection-log.py). comp is null for items the wiki hasn't gathered a completion score
	 * for yet (e.g. very recently added).
	 */
	static final class CompletionEntry
	{
		String name;
		List<String> tabs;
		Double comp;
	}

	/**
	 * Bundles the parsed per-item entries with the item id list and pet name index derived from
	 * them, so {@link #reload(Map)} can swap all three atomically via a single volatile write -
	 * readers never see one already updated while the others are still the old dataset.
	 */
	private static final class CompletionData
	{
		private final Map<Integer, CompletionEntry> byId;
		private final List<Integer> ids;
		private final Map<String, Integer> petIdByName;
		private final Map<String, List<String>> tabsByItemName;

		private CompletionData(Map<String, CompletionEntry> raw)
		{
			Map<Integer, CompletionEntry> parsed = new TreeMap<>();
			if (raw != null)
			{
				raw.forEach((id, entry) -> parsed.put(Integer.parseInt(id), entry));
			}
			this.byId = Collections.unmodifiableMap(parsed);
			// Only ids with a real completion score - randomItemIds() (the "::clogtest" dev command)
			// should only ever test items that actually have one, same as before this class also
			// started carrying comp-less entries (for their name/tabs data).
			this.ids = parsed.entrySet().stream()
				.filter(entry -> entry.getValue().comp != null)
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());

			Map<String, Integer> pets = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			Map<String, List<String>> tabs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			parsed.forEach((id, entry) ->
			{
				if (entry.name == null)
				{
					return;
				}
				if (entry.tabs != null && entry.tabs.stream().anyMatch(tab -> tab.equalsIgnoreCase(PET_TAB)))
				{
					pets.put(entry.name, id);
				}
				if (entry.tabs != null)
				{
					tabs.put(entry.name, entry.tabs);
				}
			});
			this.petIdByName = Collections.unmodifiableMap(pets);
			this.tabsByItemName = Collections.unmodifiableMap(tabs);
		}
	}

	private static final class Dataset
	{
		private final double[] compositeScores;
		private final double[] completionScores;
		private final double[] positivePricedValueScores;
		private final double[] dropRateScores;
		private final double logPriceMin;
		private final double logPriceMax;

		private Dataset(double[] compositeScores, double[] completionScores, double[] positivePricedValueScores,
			double[] dropRateScores, double logPriceMin, double logPriceMax)
		{
			this.compositeScores = compositeScores;
			this.completionScores = completionScores;
			this.positivePricedValueScores = positivePricedValueScores;
			this.dropRateScores = dropRateScores;
			this.logPriceMin = logPriceMin;
			this.logPriceMax = logPriceMax;
		}
	}
}
