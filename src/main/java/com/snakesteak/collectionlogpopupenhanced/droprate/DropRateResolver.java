package com.snakesteak.collectionlogpopupenhanced.droprate;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves how likely a collection log item was to drop from a given source (any real
 * collection-log-eligible NPC, chest, or activity - not just kill-count-tracked bosses) per kill,
 * from a bundled dataset generated offline by scripts/generate-drop-rates.py (see that script's
 * docstring for where the data comes from and its known gaps - it's a best-effort dataset covering
 * every source the wiki has structured drop-table data for, not a hand-picked or exhaustive one).
 */
@Slf4j
@Singleton
public class DropRateResolver
{
	private static final String DROP_RATES_RESOURCE = "/com/snakesteak/collectionlogpopupenhanced/drop-rates.json";

	private final Map<String, Map<String, Double>> dropRatesBySource;

	@Inject
	public DropRateResolver(Gson gson)
	{
		this.dropRatesBySource = load(gson);
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
	 *         in the dataset rather than one specific source - used when there's no correlated kill
	 *         to read a source from (see {@link com.snakesteak.collectionlogpopupenhanced.overlay.PanelStat#DROP_RATE}).
	 *         Only returns a value when the item name maps to exactly one source's entry; if it's a
	 *         notable drop from more than one tracked source at different rates, there's no single
	 *         rate to show without knowing which one it came from, so this returns null the same as
	 *         an unknown item rather than guessing - see {@link #dropRatesByItemName(String)} to get
	 *         all of them instead.
	 */
	public Double dropProbabilityByItemName(String itemName)
	{
		List<SourceRate> matches = dropRatesByItemName(itemName);
		return matches.size() == 1 ? matches.get(0).getProbability() : null;
	}

	/**
	 * @param itemName the collection log item's display name
	 * @return every (source, drop probability) pair {@code itemName} is a notable drop for, across
	 *         the whole dataset - empty if it's not a notable drop from any tracked source. Used to
	 *         show all the candidate rates for an item that's ambiguous across sources, since
	 *         {@link #dropProbabilityByItemName(String)} deliberately returns null for those.
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

	private static Map<String, Map<String, Double>> load(Gson gson)
	{
		Map<String, Map<String, Double>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		try (Reader reader = openResource())
		{
			Map<String, Map<String, Double>> raw = gson.fromJson(reader, new TypeToken<Map<String, Map<String, Double>>>()
			{
			}.getType());
			if (raw != null)
			{
				raw.forEach((source, items) ->
				{
					Map<String, Double> itemRates = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
					itemRates.putAll(items);
					result.put(source, itemRates);
				});
			}
		}
		catch (Exception e)
		{
			log.warn("Failed to load drop rate data", e);
		}
		return result;
	}

	private static Reader openResource()
	{
		return new InputStreamReader(DropRateResolver.class.getResourceAsStream(DROP_RATES_RESOURCE));
	}

	@Value
	public static class SourceRate
	{
		String source;
		double probability;
	}
}
