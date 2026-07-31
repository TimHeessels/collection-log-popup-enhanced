package com.snakesteak.collectionlogpopupenhanced.rarity;

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
 * Loads {@link RarityResolver}'s collection log completion dataset from collection-log.json,
 * bundled in the plugin jar under /data. See that repo's generate-collection-log.py for how it's
 * produced from the wiki's Module:Collection_log/data.json and completion.json.
 */
@Slf4j
@Singleton
public class LocalRarityDatasetLoader
{
	private static final String RESOURCE_PATH = "/data/collection-log.json";

	private final RarityResolver rarityResolver;
	private final Gson gson;

	@Inject
	public LocalRarityDatasetLoader(RarityResolver rarityResolver, Gson gson)
	{
		this.rarityResolver = rarityResolver;
		this.gson = gson;
	}

	public void load()
	{
		try (InputStream stream = LocalRarityDatasetLoader.class.getResourceAsStream(RESOURCE_PATH))
		{
			if (stream == null)
			{
				log.warn("Bundled rarity dataset {} not found", RESOURCE_PATH);
				return;
			}

			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
			{
				Map<String, RarityResolver.CompletionEntry> parsed = gson.fromJson(reader, RarityResolver.DATASET_TYPE);
				if (parsed == null || parsed.isEmpty())
				{
					log.warn("Bundled rarity dataset {} was empty", RESOURCE_PATH);
					return;
				}
				rarityResolver.reload(parsed);
			}
		}
		catch (IOException e)
		{
			log.warn("Failed to load bundled rarity dataset {}", RESOURCE_PATH, e);
		}
	}
}
