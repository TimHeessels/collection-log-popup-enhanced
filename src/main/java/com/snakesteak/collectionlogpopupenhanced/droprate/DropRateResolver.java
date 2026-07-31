package com.snakesteak.collectionlogpopupenhanced.droprate;

import com.google.common.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
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

		return itemRates.get(itemName);
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
		List<SourceRate> matches = dropRatesByItemName(itemName);
		return matches.size() == 1 ? matches.get(0).getProbability() : null;
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
			Double rate = entry.getValue().get(itemName);
			if (rate != null)
			{
				matches.add(new SourceRate(entry.getKey(), rate));
			}
		}
		return matches;
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
	}
}
