package com.snakesteak.collectionlogpopupenhanced.droprate;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads {@link DropRateResolver}'s dataset from drop-rates.json, bundled in the plugin jar under
 * /data. See the osrs-collection-log-data repo's generate-drop-rates.py for how it's produced.
 */
@Slf4j
@Singleton
public class LocalDropRateDatasetLoader
{
	private static final String RESOURCE_PATH = "/data/drop-rates.json";

	private final DropRateResolver dropRateResolver;
	private final Gson gson;

	@Inject
	public LocalDropRateDatasetLoader(DropRateResolver dropRateResolver, Gson gson)
	{
		this.dropRateResolver = dropRateResolver;
		this.gson = gson;
	}

	public void load()
	{
		try (InputStream stream = LocalDropRateDatasetLoader.class.getResourceAsStream(RESOURCE_PATH))
		{
			if (stream == null)
			{
				log.warn("Bundled drop rate dataset {} not found", RESOURCE_PATH);
				return;
			}

			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
			{
				Map<String, Map<String, Double>> parsed = gson.fromJson(reader, DropRateResolver.DATASET_TYPE);
				if (parsed == null || parsed.isEmpty())
				{
					log.warn("Bundled drop rate dataset {} was empty", RESOURCE_PATH);
					return;
				}
				dropRateResolver.reload(parsed);
			}
		}
		catch (IOException e)
		{
			log.warn("Failed to load bundled drop rate dataset {}", RESOURCE_PATH, e);
		}
	}
}
