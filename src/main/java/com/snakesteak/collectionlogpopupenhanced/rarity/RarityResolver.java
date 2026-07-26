package com.snakesteak.collectionlogpopupenhanced.rarity;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.snakesteak.collectionlogpopupenhanced.CollectionLogPopupEnhancedConfig;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
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
 * Pets are matched by name ahead of all of this (see {@link PetItems}).
 */
@Slf4j
@Singleton
public class RarityResolver
{
	private static final String COMPLETION_RESOURCE = "/com/snakesteak/collectionlogpopupenhanced/rarity-overrides.json";

	private static final double COMPLETION_WEIGHT = 0.6;
	private static final double VALUE_WEIGHT = 0.4;

	// Percentile cutoffs (not user-configurable - "percentile" isn't a meaningful knob for players
	// to tune on an already-composite score).
	private static final double UNCOMMON_PERCENTILE = 50;
	private static final double RARE_PERCENTILE = 80;
	private static final double VERY_RARE_PERCENTILE = 95;

	private final ItemManager itemManager;
	private final CollectionLogPopupEnhancedConfig config;
	private final Map<Integer, Double> compPercentById;
	private final List<Integer> itemIds;

	@Inject
	public RarityResolver(ItemManager itemManager, CollectionLogPopupEnhancedConfig config, Gson gson)
	{
		this.itemManager = itemManager;
		this.config = config;
		this.compPercentById = loadCompPercentById(gson);
		this.itemIds = List.copyOf(compPercentById.keySet());
	}

	/**
	 * @param count how many distinct item ids to return
	 * @return up to {@code count} distinct, randomly chosen item ids from the collection log
	 *         completion dataset (fewer if the dataset is smaller than {@code count}, empty if it
	 *         failed to load). Used by the "::clogtest" dev command.
	 */
	public List<Integer> randomItemIds(int count)
	{
		if (itemIds.isEmpty() || count <= 0)
		{
			return List.of();
		}
		List<Integer> shuffled = new ArrayList<>(itemIds);
		Collections.shuffle(shuffled, ThreadLocalRandom.current());
		return shuffled.subList(0, Math.min(count, shuffled.size()));
	}

	private static Map<Integer, Double> loadCompPercentById(Gson gson)
	{
		Map<Integer, Double> result = new TreeMap<>();
		try (Reader reader = openResource(COMPLETION_RESOURCE))
		{
			Map<String, Double> raw = gson.fromJson(reader, new TypeToken<Map<String, Double>>()
			{
			}.getType());
			if (raw != null)
			{
				raw.forEach((id, percent) -> result.put(Integer.parseInt(id), percent));
			}
		}
		catch (Exception e)
		{
			log.warn("Failed to load collection log completion data", e);
		}
		return Collections.unmodifiableMap(result);
	}

	private static Reader openResource(String path)
	{
		return new InputStreamReader(RarityResolver.class.getResourceAsStream(path));
	}

	/**
	 * @param itemId resolved item id, or -1 if it couldn't be resolved (see ItemIdResolver). Falls
	 *                back to a value-score-only composite using price 0 in that case, same as an
	 *                item with no GE price at all.
	 */
	public RarityResult resolve(int itemId, String itemName)
	{
		int price = getPrice(itemId);
		boolean highAlch = isHighAlchPrice(itemId);
		int alchPrice = itemId >= 0 ? itemManager.getItemComposition(itemId).getHaPrice() : 0;

		if (PetItems.names().contains(itemName))
		{
			return new RarityResult(RarityTier.PET, itemId, price, highAlch, compPercentById.get(itemId), null, 0, 100, 0, 0, 0, alchPrice);
		}

		if (compPercentById.isEmpty())
		{
			return new RarityResult(RarityTier.COMMON, itemId, price, highAlch, null, null, 0, 0, 0, 0, 0, alchPrice);
		}

		Dataset dataset = buildDataset();
		Double compPercent = itemId >= 0 ? compPercentById.get(itemId) : null;
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
			if (dataset.positivePricedValueScores.length == 0)
			{
				return new RarityResult(RarityTier.COMMON, itemId, price, highAlch, compPercent, completionScore,
					valueScore, 0, dataset.compositeScores.length, dataset.logPriceMin, dataset.logPriceMax, alchPrice);
			}
			score = valueScore;
			distribution = dataset.positivePricedValueScores;
		}
		else if (completionScore != null)
		{
			score = COMPLETION_WEIGHT * completionScore + VALUE_WEIGHT * valueScore;
			distribution = dataset.compositeScores;
		}
		else if (dataset.positivePricedValueScores.length == 0)
		{
			// No completion data for this item, and nothing in the dataset has a usable positive
			// price to rank it against (in practice, ItemManager's GE price cache hasn't finished
			// loading yet). Nothing to legitimately rank against.
			return new RarityResult(RarityTier.COMMON, itemId, price, highAlch, null, null, valueScore, 0,
				dataset.compositeScores.length, dataset.logPriceMin, dataset.logPriceMax, alchPrice);
		}
		else
		{
			score = valueScore;
			distribution = dataset.positivePricedValueScores;
		}

		double percentile = percentileRank(distribution, score);
		RarityTier tier = bucketByPercentile(percentile);
		return new RarityResult(tier, itemId, price, highAlch, compPercent, completionScore, valueScore, percentile,
			dataset.compositeScores.length, dataset.logPriceMin, dataset.logPriceMax, alchPrice);
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
	private Dataset buildDataset()
	{
		int n = compPercentById.size();
		double[] logPrices = new double[n];
		double[] compPercents = new double[n];
		boolean[] hasPositivePrice = new boolean[n];
		double logPriceMin = Double.POSITIVE_INFINITY;
		double logPriceMax = Double.NEGATIVE_INFINITY;

		int i = 0;
		for (Map.Entry<Integer, Double> entry : compPercentById.entrySet())
		{
			int price = getPrice(entry.getKey());
			double logPrice = logPrice(price);
			logPrices[i] = logPrice;
			compPercents[i] = entry.getValue();
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

		return new Dataset(compositeScores, completionScores, positivePricedValueScores, logPriceMin, logPriceMax);
	}

	private static final class Dataset
	{
		private final double[] compositeScores;
		private final double[] completionScores;
		private final double[] positivePricedValueScores;
		private final double logPriceMin;
		private final double logPriceMax;

		private Dataset(double[] compositeScores, double[] completionScores, double[] positivePricedValueScores, double logPriceMin, double logPriceMax)
		{
			this.compositeScores = compositeScores;
			this.completionScores = completionScores;
			this.positivePricedValueScores = positivePricedValueScores;
			this.logPriceMin = logPriceMin;
			this.logPriceMax = logPriceMax;
		}
	}
}
