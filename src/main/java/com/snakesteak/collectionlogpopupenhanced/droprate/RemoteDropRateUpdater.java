package com.snakesteak.collectionlogpopupenhanced.droprate;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Keeps {@link DropRateResolver}'s dataset fresher than the plugin's own release cadence by
 * periodically re-downloading drop-rates.json from this repo's "data" branch, which a scheduled
 * GitHub Actions workflow (.github/workflows/update-drop-rates.yml) regenerates from the wiki
 * roughly weekly. If a fetch or cache read ever fails, {@link DropRateResolver} just keeps using
 * whatever dataset it already has (initially the one bundled in the jar).
 *
 * All disk and network I/O here runs off the client thread: the periodic check runs on the
 * injected {@link ScheduledExecutorService}, and the actual fetch runs on OkHttp's own thread pool
 * via {@link OkHttpClient#newCall(Request)}'s {@code enqueue}. A failed or missing fetch/cache
 * never blocks anything - {@link DropRateResolver} simply keeps whatever dataset it already has.
 */
@Slf4j
@Singleton
public class RemoteDropRateUpdater
{
	private static final String DATA_URL =
		"https://raw.githubusercontent.com/TimHeessels/collection-log-popup-enhanced/data/drop-rates.json";

	// Data is only regenerated weekly - re-fetching more often than this would just hit the CDN for
	// an unchanged file.
	private static final Duration REFRESH_INTERVAL = Duration.ofDays(3);
	private static final long CHECK_INTERVAL_MINUTES = Duration.ofHours(6).toMinutes();

	private final DropRateResolver dropRateResolver;
	private final Gson gson;
	private final OkHttpClient okHttpClient;
	private final ScheduledExecutorService executor;

	private ScheduledFuture<?> checkTask;

	@Inject
	public RemoteDropRateUpdater(DropRateResolver dropRateResolver, Gson gson, OkHttpClient okHttpClient,
		ScheduledExecutorService executor)
	{
		this.dropRateResolver = dropRateResolver;
		this.gson = gson;
		this.okHttpClient = okHttpClient;
		this.executor = executor;
	}

	public void startUp()
	{
		checkTask = executor.scheduleWithFixedDelay(this::checkForUpdate, 0, CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);
	}

	public void shutDown()
	{
		if (checkTask != null)
		{
			checkTask.cancel(false);
			checkTask = null;
		}
	}

	private void checkForUpdate()
	{
		Path cachePath = cacheFilePath();
		try
		{
			if (Files.exists(cachePath))
			{
				applyCacheFile(cachePath);
				Instant lastModified = Files.getLastModifiedTime(cachePath).toInstant();
				if (Instant.now().isBefore(lastModified.plus(REFRESH_INTERVAL)))
				{
					return;
				}
			}
		}
		catch (IOException e)
		{
			log.debug("Failed to read cached drop rate data", e);
		}

		fetchRemote(cachePath);
	}

	private void applyCacheFile(Path cachePath)
	{
		try (Reader reader = Files.newBufferedReader(cachePath, StandardCharsets.UTF_8))
		{
			Map<String, Map<String, Double>> raw = gson.fromJson(reader, DropRateResolver.DATASET_TYPE);
			if (raw != null && !raw.isEmpty())
			{
				dropRateResolver.reload(raw);
			}
		}
		catch (IOException e)
		{
			log.debug("Failed to parse cached drop rate data", e);
		}
	}

	private void fetchRemote(Path cachePath)
	{
		Request request = new Request.Builder().url(DATA_URL).build();
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Failed to fetch remote drop rate data", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (!r.isSuccessful() || r.body() == null)
					{
						log.debug("Remote drop rate fetch returned {}", r.code());
						return;
					}

					String body = r.body().string();
					Map<String, Map<String, Double>> raw = gson.fromJson(new StringReader(body), DropRateResolver.DATASET_TYPE);
					if (raw == null || raw.isEmpty())
					{
						return;
					}

					dropRateResolver.reload(raw);
					writeCache(cachePath, body);
				}
				catch (Exception e)
				{
					log.debug("Failed to process remote drop rate data", e);
				}
			}
		});
	}

	private void writeCache(Path cachePath, String body)
	{
		try
		{
			Files.createDirectories(cachePath.getParent());
			Files.write(cachePath, body.getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			log.debug("Failed to write drop rate cache", e);
		}
	}

	private static Path cacheFilePath()
	{
		return RuneLite.RUNELITE_DIR.toPath()
			.resolve("collection-log-popup-enhanced")
			.resolve("drop-rates.json");
	}
}
