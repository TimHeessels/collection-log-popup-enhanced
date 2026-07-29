package com.snakesteak.collectionlogpopupenhanced.progress;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.snakesteak.collectionlogpopupenhanced.rarity.RarityResolver;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

/**
 * Tracks which collection log items this account has obtained, entirely locally - no network calls
 * ever. Two ways items get recorded:
 * <p>
 * 1. A full resync, mirroring the exact technique the OSRS Wiki's own WikiSync plugin uses (see its
 * SyncButtonManager): programmatically triggering the collection log's search feature causes the
 * game itself to fire {@link #SCRIPT_ID_SEARCH_RESULT_ITEM} once per obtained item across the WHOLE
 * log, not just whatever page happens to be open. This runs once automatically the first time the
 * log is opened with no local progress yet tracked (see {@link #onScriptPostFired}), and otherwise
 * only on demand via {@link #triggerFullResync()} (the "Resync page progress" config item).
 * <p>
 * 2. {@link #recordObtained(int)} - called by the plugin the moment a new item is resolved, keeping
 * the store live between resyncs without needing another full search pass.
 * <p>
 * Page name -> total item count comes from {@link RarityResolver#totalItemsOnPage(String)} (the
 * wiki-sourced dataset) rather than being stored here - only the flat obtained-id set is persisted.
 */
@Slf4j
@Singleton
public class CollectionLogProgressTracker
{
	// No named RuneLite constants exist for these - same as WikiSync's own code, which also uses bare
	// literals for all three.
	private static final int SCRIPT_ID_COLLECTION_LOG_SETUP = 7797;
	private static final int SCRIPT_ID_RUN_SEARCH = 2240;
	private static final int SCRIPT_ID_SEARCH_RESULT_ITEM = 4100;

	// Matches WikiSync's own settle window - how long to wait after the last obtained-item firing
	// before considering a full resync complete.
	private static final int SYNC_COMPLETE_AFTER_TICKS = 2;

	private static final Type OBTAINED_IDS_TYPE = new TypeToken<List<Integer>>()
	{
	}.getType();

	@Value
	public static class PageProgress
	{
		int obtained;
		int total;
	}

	private final Client client;
	private final RarityResolver rarityResolver;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final ClientThread clientThread;

	private final Set<Integer> obtainedItemIds = ConcurrentHashMap.newKeySet();

	// Only ever read/written on the client thread (every handler below is client-thread-only, per
	// RuneLite's @Subscribe contract) - which account the in-memory set above currently belongs to.
	private String loadedForUsername;
	private boolean loadedFromDisk;
	private boolean baselineResyncAttempted;
	private boolean syncInProgress;
	private int lastSyncItemTick = -1;

	@Inject
	public CollectionLogProgressTracker(Client client, RarityResolver rarityResolver, Gson gson,
		ScheduledExecutorService executor, ClientThread clientThread)
	{
		this.client = client;
		this.rarityResolver = rarityResolver;
		this.gson = gson;
		this.executor = executor;
		this.clientThread = clientThread;
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != SCRIPT_ID_COLLECTION_LOG_SETUP)
		{
			return;
		}

		ensureLoadedForCurrentPlayer();
		resyncIfNoLocalProgress();
	}

	/**
	 * No local progress tracked at all yet for this account - do a one-time full resync so there's a
	 * real baseline, rather than only ever growing via new unlocks from here on. Called both right
	 * after the collection log window opens and (in case that fired before the disk load finished)
	 * again once the load completes - see {@link #loadFromDisk}.
	 */
	private void resyncIfNoLocalProgress()
	{
		// The disk load is asynchronous, so obtainedItemIds may just be empty because it hasn't
		// finished yet rather than because there's genuinely no local progress.
		if (!loadedFromDisk || baselineResyncAttempted || !obtainedItemIds.isEmpty())
		{
			return;
		}

		baselineResyncAttempted = true;
		triggerFullResync();
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (!syncInProgress || event.getScriptId() != SCRIPT_ID_SEARCH_RESULT_ITEM)
		{
			return;
		}

		Object[] args = event.getScriptEvent().getArguments();
		if (args.length < 2 || !(args[1] instanceof Integer))
		{
			return;
		}

		obtainedItemIds.add((Integer) args[1]);
		lastSyncItemTick = client.getTickCount();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (syncInProgress && client.getTickCount() - lastSyncItemTick >= SYNC_COMPLETE_AFTER_TICKS)
		{
			syncInProgress = false;
			persist();
		}
	}

	/**
	 * Replicates WikiSync's own sync trigger: programmatically opens the collection log's search
	 * feature and runs a full search pass, which fires {@link #SCRIPT_ID_SEARCH_RESULT_ITEM} once per
	 * obtained item across the whole log. Also the manual "Resync page progress" config item's entry
	 * point.
	 */
	public void triggerFullResync()
	{
		if (client.getLocalPlayer() == null)
		{
			return;
		}

		ensureLoadedForCurrentPlayer();

		// Callers can be inside the collection log's own SCRIPT_ID_COLLECTION_LOG_SETUP callback (see
		// onScriptPostFired) - the client's script VM isn't reentrant, so calling client.runScript()
		// straight from within that callback hangs the client. Deferring to tick-end runs it once the
		// VM is done with the current script entirely.
		clientThread.invokeAtTickEnd(() ->
		{
			syncInProgress = true;
			lastSyncItemTick = client.getTickCount();
			client.menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
			client.runScript(SCRIPT_ID_RUN_SEARCH);
		});
	}

	/**
	 * @param itemId a newly-resolved collection log item's id - recorded as obtained immediately,
	 *               independent of any resync (keeps the store live between full resyncs).
	 */
	public void recordObtained(int itemId)
	{
		ensureLoadedForCurrentPlayer();
		if (obtainedItemIds.add(itemId))
		{
			persist();
		}
	}

	/**
	 * @param pageName a collection log page name (e.g. "Zulrah")
	 * @return this account's tracked progress on that page, or null if the page's total item count
	 *         isn't known (see {@link RarityResolver#totalItemsOnPage(String)})
	 */
	public PageProgress progressFor(String pageName)
	{
		int total = rarityResolver.totalItemsOnPage(pageName);
		if (total == 0)
		{
			return null;
		}

		// Reading progress doesn't otherwise imply a load has happened - only opening the real
		// collection log, a resync, or a recorded unlock do (see ensureLoadedForCurrentPlayer's other
		// callers). Without this, querying progress before any of those has fired this session (e.g.
		// via ::clogtest) would show an empty in-memory set despite real data sitting on disk.
		ensureLoadedForCurrentPlayer();

		int obtained = 0;
		for (int itemId : obtainedItemIds)
		{
			if (rarityResolver.tabsFor(itemId).stream().anyMatch(tab -> tab.equalsIgnoreCase(pageName)))
			{
				obtained++;
			}
		}
		return new PageProgress(obtained, total);
	}

	/**
	 * @return this account's tracked progress across the entire collection log, all pages combined.
	 */
	public PageProgress overallProgress()
	{
		ensureLoadedForCurrentPlayer();
		return new PageProgress(obtainedItemIds.size(), rarityResolver.totalItems());
	}

	private void ensureLoadedForCurrentPlayer()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		String username = localPlayer.getName();
		if (username == null || username.equals(loadedForUsername))
		{
			return;
		}

		loadedForUsername = username;
		loadedFromDisk = false;
		baselineResyncAttempted = false;
		obtainedItemIds.clear();
		executor.execute(() -> loadFromDisk(username));
	}

	private void loadFromDisk(String username)
	{
		Path path = progressFilePath(username);
		try
		{
			if (Files.exists(path))
			{
				try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
				{
					List<Integer> ids = gson.fromJson(reader, OBTAINED_IDS_TYPE);
					if (ids != null)
					{
						obtainedItemIds.addAll(ids);
					}
				}
			}
		}
		catch (IOException e)
		{
			log.debug("Failed to read local collection log progress for {}", username, e);
		}
		finally
		{
			// obtainedItemIds.isEmpty() only reliably means "no local progress" once this flips true -
			// flip it back on the client thread, since it (and the set) are otherwise only ever
			// touched there.
			clientThread.invoke(() ->
			{
				if (username.equals(loadedForUsername))
				{
					loadedFromDisk = true;
					resyncIfNoLocalProgress();
				}
			});
		}
	}

	private void persist()
	{
		String username = loadedForUsername;
		if (username == null)
		{
			return;
		}

		List<Integer> snapshot = new ArrayList<>(obtainedItemIds);
		executor.execute(() -> writeToDisk(username, snapshot));
	}

	private void writeToDisk(String username, List<Integer> ids)
	{
		Path path = progressFilePath(username);
		try
		{
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8))
			{
				gson.toJson(ids, OBTAINED_IDS_TYPE, writer);
			}
		}
		catch (IOException e)
		{
			log.debug("Failed to write local collection log progress for {}", username, e);
		}
	}

	private static Path progressFilePath(String username)
	{
		return RuneLite.RUNELITE_DIR.toPath()
			.resolve("collection-log-popup-enhanced")
			.resolve("progress")
			.resolve(username + ".json");
	}
}
