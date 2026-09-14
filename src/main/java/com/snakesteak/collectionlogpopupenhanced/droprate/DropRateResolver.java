package com.snakesteak.collectionlogpopupenhanced.droprate;

import com.google.common.reflect.TypeToken;
import com.snakesteak.collectionlogpopupenhanced.CollectionLogSlotNames;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves how likely a collection log item was to drop from a given source, per kill, from a
 * dataset generated offline (see the osrs-collection-log-data repo's generate-drop-rates.py) and
 * fetched at runtime by {@link RemoteDropRateUpdater} (see its javadoc). Starts out empty until that
 * first fetch completes.
 */
@Slf4j
@Singleton
public class DropRateResolver
{
	static final Type DATASET_TYPE = new TypeToken<Map<String, Map<String, Double>>>()
	{
	}.getType();

	// A source stored as "X (max)" is not a separate source - it holds the other end of X's rate
	// range, for content whose rate varies with scale (raid party size, a Slayer superior, the
	// Sepulchre's deeper floors). Folded into X everywhere rather than listed on its own, which
	// would otherwise read as a second boss and suppress the single-rate case entirely.
	private static final String MAX_SOURCE_SUFFIX = " (max)";

	private volatile Map<String, Map<String, Double>> dropRatesBySource;

	@Inject
	public DropRateResolver()
	{
		this.dropRatesBySource = normalize(Map.of());
	}

	/**
	 * Replaces the dataset with a freshly fetched or cached copy - called by
	 * {@link RemoteDropRateUpdater} once it has a parsed, non-empty replacement. Safe to call from
	 * any thread; readers always see either the old or new dataset, never a partial one.
	 */
	void reload(Map<String, Map<String, Double>> raw)
	{
		dropRatesBySource = normalize(raw);
	}

	/**
	 * @param source the boss or monster name the kill count is being tracked against
	 * @param itemName the collection log item's display name
	 * @return the per-kill drop probability (0-1) of {@code itemName} from {@code source}, or null
	 *         if either isn't in the dataset
	 */
	public Double dropProbability(String source, String itemName)
	{
		Map<String, Double> itemRates = dropRatesBySource.get(source);
		if (itemRates == null)
		{
			return null;
		}

		return rateFor(itemRates, itemName);
	}

	/**
	 * @param source the boss or monster name the kill count is being tracked against
	 * @param itemName the collection log item's display name
	 * @return both ends of {@code itemName}'s rate range from {@code source} where the dataset gives
	 *         it a " (max)" twin, else the single rate, else null if either isn't in the dataset
	 */
	public SourceRate dropRateFor(String source, String itemName)
	{
		Double rate = dropProbability(source, itemName);
		if (rate == null)
		{
			return null;
		}
		return new SourceRate(source, rate, maxRateFor(source, itemName));
	}

	// The " (max)" twin's rate for the same item, or null where the source has no twin or the twin
	// doesn't list the item (most twins carry only the few items whose rate actually varies).
	private Double maxRateFor(String source, String itemName)
	{
		if (source.toLowerCase(Locale.ROOT).endsWith(MAX_SOURCE_SUFFIX))
		{
			return null;
		}
		Map<String, Double> maxRates = dropRatesBySource.get(source + MAX_SOURCE_SUFFIX);
		return maxRates != null ? rateFor(maxRates, itemName) : null;
	}

	/**
	 * @param itemName the collection log item's display name
	 * @return the per-kill drop probability (0-1) of {@code itemName}, searched across every source
	 *         in the dataset - used when there's no correlated kill to read a source from. Only
	 *         returns a value when the item maps to exactly one source; if it's a notable drop from
	 *         more than one at different rates, returns null rather than guessing - see
	 *         {@link #dropRatesByItemName(String)} to get all of them instead.
	 */
	public Double dropProbabilityByItemName(String itemName)
	{
		SourceRate only = soleSourceRate(itemName);
		// Null for a one-source item whose rate is a range, so the caller falls through to
		// dropRatesByItemName and renders both ends rather than silently showing only the base.
		return only != null && only.getMaxProbability() == null ? only.getProbability() : null;
	}

	/**
	 * @param itemName the collection log item's display name
	 * @return the single source's rate(s) where {@code itemName} is a notable drop from exactly one
	 *         source (a " (max)" twin counting as the same source), else null
	 */
	public SourceRate soleSourceRate(String itemName)
	{
		List<SourceRate> matches = dropRatesByItemName(itemName);
		return matches.size() == 1 ? matches.get(0) : null;
	}

	/**
	 * @param itemName the collection log item's display name
	 * @return every (source, drop probability) pair {@code itemName} is a notable drop for, across
	 *         the whole dataset - empty if none. Used to show all candidate rates for an item that's
	 *         ambiguous across sources, since {@link #dropProbabilityByItemName(String)} returns null
	 *         for those.
	 */
	public List<SourceRate> dropRatesByItemName(String itemName)
	{
		List<SourceRate> matches = new ArrayList<>();
		for (Map.Entry<String, Map<String, Double>> entry : dropRatesBySource.entrySet())
		{
			String source = entry.getKey();
			// Skipped rather than listed: it is the same source, and counting it separately would
			// make every scaling item look ambiguous across two bosses.
			if (source.toLowerCase(Locale.ROOT).endsWith(MAX_SOURCE_SUFFIX))
			{
				continue;
			}
			Double rate = rateFor(entry.getValue(), itemName);
			if (rate != null)
			{
				matches.add(new SourceRate(source, rate, maxRateFor(source, itemName)));
			}
		}
		return matches;
	}

	/**
	 * Keyed on the chat message's wording, which is the log's slot name - so a name the dataset
	 * stores without the slot's charge-state suffix needs a second try. See CollectionLogSlotNames.
	 */
	private static Double rateFor(Map<String, Double> itemRates, String itemName)
	{
		Double exact = itemRates.get(itemName);
		if (exact != null)
		{
			return exact;
		}
		String datasetName = CollectionLogSlotNames.datasetNameOrNull(itemName);
		return datasetName != null ? itemRates.get(datasetName) : null;
	}

	private static Map<String, Map<String, Double>> normalize(Map<String, Map<String, Double>> raw)
	{
		Map<String, Map<String, Double>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		if (raw != null)
		{
			raw.forEach((source, items) ->
			{
				Map<String, Double> itemRates = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
				itemRates.putAll(items);
				// Aliased to the log's slot name as well, since callers key on the chat message's
				// wording rather than the item name (see CollectionLogSlotNames).
				items.forEach((itemName, rate) ->
				{
					String slotName = CollectionLogSlotNames.slotNameOrNull(itemName);
					if (slotName != null)
					{
						itemRates.putIfAbsent(slotName, rate);
					}
				});
				result.put(source, itemRates);
			});
		}
		return result;
	}

	@Value
	public static class SourceRate
	{
		String source;
		double probability;
		// The other end of the rate range, from the source's " (max)" twin, or null where it has
		// none. See MAX_SOURCE_SUFFIX.
		Double maxProbability;

		public SourceRate(String source, double probability)
		{
			this(source, probability, null);
		}

		public SourceRate(String source, double probability, Double maxProbability)
		{
			this.source = source;
			this.probability = probability;
			this.maxProbability = maxProbability;
		}

		/**
		 * @return every rate this source gives the item - one value, or two where it has a
		 *         " (max)" twin. Not ordered: "max" is the max *scale*, which for a couple of
		 *         items (Little Nightmare, Zalcano shard) is the rarer end, not the commoner one.
		 */
		public List<Double> probabilities()
		{
			return maxProbability != null ? List.of(probability, maxProbability) : List.of(probability);
		}
	}
}
