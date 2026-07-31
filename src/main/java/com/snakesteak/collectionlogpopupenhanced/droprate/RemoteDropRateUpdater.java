package com.snakesteak.collectionlogpopupenhanced.droprate;

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
 * Keeps {@link DropRateResolver}'s dataset fresh by periodically downloading drop-rates.json from
 * the osrs-collection-log-data repo's "data" branch, which a scheduled GitHub Actions workflow there
 * regenerates from the wiki roughly weekly. There's no bundled fallback copy - this is the only
 * source of the dataset, so {@link DropRateResolver} starts out empty until this first succeeds. See
 * {@link RemoteJsonDatasetSync} for the shared fetch/cache/fallback mechanics.
 */
@Singleton
public class RemoteDropRateUpdater
{
	private static final String DATA_URL =
		"https://raw.githubusercontent.com/TimHeessels/osrs-collection-log-data/data/drop-rates.json";

	// Data is only regenerated weekly - re-fetching more often than this would just hit the CDN for
	// an unchanged file.
	private static final Duration REFRESH_INTERVAL = Duration.ofDays(3);
	private static final long CHECK_INTERVAL_MINUTES = Duration.ofHours(6).toMinutes();

	private final ScheduledExecutorService executor;
	private final RemoteJsonDatasetSync<Map<String, Map<String, Double>>> sync;

	private ScheduledFuture<?> checkTask;

	@Inject
	public RemoteDropRateUpdater(DropRateResolver dropRateResolver, Gson gson, OkHttpClient okHttpClient,
		ScheduledExecutorService executor)
	{
		this.executor = executor;
		this.sync = new RemoteJsonDatasetSync<>(DATA_URL, cacheFilePath(), DropRateResolver.DATASET_TYPE,
			REFRESH_INTERVAL, gson, okHttpClient, raw -> !raw.isEmpty(), dropRateResolver::reload);
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
			.resolve("drop-rates.json");
	}
}
