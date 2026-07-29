package com.snakesteak.collectionlogpopupenhanced.rarity;

import com.google.gson.Gson;
import com.snakesteak.collectionlogpopupenhanced.remote.RemoteJsonDatasetSync;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.RuneLite;
import okhttp3.OkHttpClient;

/**
 * Keeps {@link RarityResolver}'s collection log completion percentages fresher than the plugin's
 * own release cadence by periodically re-downloading collection-log.json from this repo's "data"
 * branch, which a scheduled GitHub Actions workflow (.github/workflows/update-plugin-data.yml)
 * regenerates from the wiki's own Module:Collection_log/data.json and completion.json roughly
 * weekly (see scripts/generate-collection-log.py). See {@link RemoteJsonDatasetSync} for the shared
 * fetch/cache/fallback mechanics.
 */
@Singleton
public class RemoteRarityOverridesUpdater
{
	private static final String DATA_URL =
		"https://raw.githubusercontent.com/TimHeessels/collection-log-popup-enhanced/data/collection-log.json";

	// Data is only regenerated weekly - re-fetching more often than this would just hit the CDN for
	// an unchanged file.
	private static final Duration REFRESH_INTERVAL = Duration.ofDays(3);
	private static final long CHECK_INTERVAL_MINUTES = Duration.ofHours(6).toMinutes();

	private final ScheduledExecutorService executor;
	private final RemoteJsonDatasetSync<Map<String, RarityResolver.CompletionEntry>> sync;

	private ScheduledFuture<?> checkTask;

	@Inject
	public RemoteRarityOverridesUpdater(RarityResolver rarityResolver, Gson gson, OkHttpClient okHttpClient,
		ScheduledExecutorService executor)
	{
		this.executor = executor;
		this.sync = new RemoteJsonDatasetSync<>(DATA_URL, cacheFilePath(), RarityResolver.DATASET_TYPE,
			REFRESH_INTERVAL, gson, okHttpClient, raw -> !raw.isEmpty(), rarityResolver::reload);
	}

	public void startUp()
	{
		checkTask = executor.scheduleWithFixedDelay(sync::checkForUpdate, 0, CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);
	}

	public void shutDown()
	{
		if (checkTask != null)
		{
			checkTask.cancel(false);
			checkTask = null;
		}
	}

	private static Path cacheFilePath()
	{
		return RuneLite.RUNELITE_DIR.toPath()
			.resolve("collection-log-popup-enhanced")
			.resolve("collection-log.json");
	}
}
