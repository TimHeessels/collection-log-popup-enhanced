package com.snakesteak.collectionlogpopupenhanced.remote;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Keeps one bundled JSON dataset (e.g. drop rates, collection log completion percentages) fresher
 * than the plugin's own release cadence, by periodically re-downloading it from a URL and caching
 * the result to disk. Shared by {@code RemoteDropRateUpdater} and
 * {@code RemoteRarityOverridesUpdater} - each owns one instance of this per dataset, since the URL,
 * cache file, JSON shape and "apply" callback all differ.
 *
 * All I/O happens off the client thread: {@link #checkForUpdate()} is meant to be invoked from a
 * caller-owned {@link java.util.concurrent.ScheduledExecutorService}, and the HTTP fetch itself
 * runs on OkHttp's own thread pool via {@code enqueue}. A failed or missing fetch/cache never
 * throws or blocks anything - the caller's {@code onLoaded} callback just doesn't run, and whatever
 * dataset it already applied (e.g. the one bundled in the jar) keeps being used.
 */
@Slf4j
public class RemoteJsonDatasetSync<T>
{
	private final String url;
	private final Path cachePath;
	private final Type type;
	private final Duration refreshInterval;
	private final Gson gson;
	private final OkHttpClient okHttpClient;
	private final Predicate<T> isValid;
	private final java.util.function.Consumer<T> onLoaded;

	/**
	 * @param refreshInterval how long a cached copy is trusted before re-fetching - should comfortably
	 *                        exceed how often the source data actually changes, so a normal run just
	 *                        re-applies the cache without hitting the network at all
	 * @param isValid         cheap sanity check on a parsed payload (e.g. "non-empty") before it's
	 *                        accepted - guards against caching/adopting a truncated or empty response
	 * @param onLoaded        applies a successfully parsed dataset - called from whichever thread
	 *                        {@link #checkForUpdate()} runs on (cache hit) or from an OkHttp callback
	 *                        thread (network fetch), never the client thread
	 */
	public RemoteJsonDatasetSync(String url, Path cachePath, Type type, Duration refreshInterval, Gson gson,
		OkHttpClient okHttpClient, Predicate<T> isValid, java.util.function.Consumer<T> onLoaded)
	{
		this.url = url;
		this.cachePath = cachePath;
		this.type = type;
		this.refreshInterval = refreshInterval;
		this.gson = gson;
		this.okHttpClient = okHttpClient;
		this.isValid = isValid;
		this.onLoaded = onLoaded;
	}

	/**
	 * Applies the on-disk cache (if present) and re-fetches from {@link #url} only if that cache is
	 * missing or older than {@link #refreshInterval}. Intended to be called periodically (e.g. every
	 * few hours) - most calls will find a fresh-enough cache and do no network I/O at all.
	 */
	public void checkForUpdate()
	{
		try
		{
			if (Files.exists(cachePath))
			{
				applyCacheFile();
				Instant lastModified = Files.getLastModifiedTime(cachePath).toInstant();
				if (Instant.now().isBefore(lastModified.plus(refreshInterval)))
				{
					return;
				}
			}
		}
		catch (IOException e)
		{
			log.debug("Failed to read cached dataset from {}", cachePath, e);
		}

		fetchRemote();
	}

	private void applyCacheFile()
	{
		try (Reader reader = Files.newBufferedReader(cachePath, StandardCharsets.UTF_8))
		{
			apply(gson.fromJson(reader, type));
		}
		catch (IOException e)
		{
			log.debug("Failed to parse cached dataset from {}", cachePath, e);
		}
	}

	private void fetchRemote()
	{
		Request request = new Request.Builder().url(url).build();
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Failed to fetch remote dataset from {}", url, e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (!r.isSuccessful() || r.body() == null)
					{
						log.debug("Remote dataset fetch from {} returned {}", url, r.code());
						return;
					}

					String body = r.body().string();
					if (apply(gson.fromJson(new StringReader(body), type)))
					{
						writeCache(body);
					}
				}
				catch (Exception e)
				{
					log.debug("Failed to process remote dataset from {}", url, e);
				}
			}
		});
	}

	private boolean apply(T parsed)
	{
		if (parsed == null || !isValid.test(parsed))
		{
			return false;
		}
		onLoaded.accept(parsed);
		return true;
	}

	private void writeCache(String body)
	{
		try
		{
			Files.createDirectories(cachePath.getParent());
			Files.write(cachePath, body.getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			log.debug("Failed to write dataset cache to {}", cachePath, e);
		}
	}
}
