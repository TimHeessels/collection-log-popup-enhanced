package com.snakesteak.collectionlogpopupenhanced.droprate;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;
import java.util.TreeMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves how likely a collection log item was to drop from a given source (boss or tracked
 * monster) per kill, from a bundled dataset generated offline by scripts/generate-drop-rates.py
 * (see that script's docstring for where the data comes from and its known gaps - it's a
 * best-effort, curated dataset, not an exhaustive one). Used to compute a {@link LuckTier}
 * against a kill count.
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
}
