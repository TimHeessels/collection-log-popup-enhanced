package com.snakesteak.collectionlogpopupenhanced.killcount;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.eventbus.Subscribe;

/**
 * Tracks kills of curated non-boss monsters that don't send an official "kill count" chat
 * message (see {@link KillCountTracker}), fed by the same {@link NpcLootReceived} signal
 * RuneLite's own Loot Tracker plugin uses to detect a kill - no custom death-detection heuristics
 * of our own. Unlike KillCountTracker, this can only ever count kills seen since it started
 * tracking, never a lifetime total, so it must stay clearly distinct from the official kill count
 * (see CollectionLogPopupEnhancedPlugin/CollectionLogOverlay, which mark it with a trailing "*").
 *
 * If Loot Tracker is enabled and already has a kill count for a monster, the first time this
 * class sees that monster it opportunistically seeds its own counter from Loot Tracker's value
 * instead of starting at zero - Loot Tracker persists one per NPC via ConfigManager (group
 * "loottracker", key "drops_NPC_&lt;name&gt;", RSProfile-scoped). That's another plugin's
 * internal, unversioned data format, so it's read defensively: any shape mismatch just means no
 * seed, never a crash.
 */
@Slf4j
@Singleton
public class TrackedKillCountManager
{
	static final Set<String> TRACKED_MONSTERS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
	static
	{
		TRACKED_MONSTERS.add("Cockatrice");
		TRACKED_MONSTERS.add("Greater abyssal demon");
		TRACKED_MONSTERS.add("Dark beast");
		TRACKED_MONSTERS.add("Skeletal Wyvern");
		TRACKED_MONSTERS.add("Mithril dragon");
		TRACKED_MONSTERS.add("Black dragon");
		TRACKED_MONSTERS.add("Iron dragon");
		TRACKED_MONSTERS.add("Steel dragon");
		TRACKED_MONSTERS.add("Brutal black dragon");
	}

	private static final String STORE_DIRECTORY_NAME = "collection-log-popup-enhanced";
	private static final String STORE_FILE_NAME = "kill-counts.json";
	private static final String LOOT_TRACKER_CONFIG_GROUP = "loottracker";

	// Same correlation window as KillCountTracker.
	private static final int MAX_TICK_AGE = 2;

	private final Client client;
	private final ConfigManager configManager;
	private final Gson gson;
	private final File storeFile;

	private ExecutorService executor;
	private final AtomicReference<Map<String, Integer>> killCounts = new AtomicReference<>();

	private String lastMonster;
	private int lastKillCount;
	private int lastTick = -1;

	@Inject
	public TrackedKillCountManager(Client client, ConfigManager configManager, Gson gson)
	{
		this(client, configManager, gson, new File(new File(RuneLite.RUNELITE_DIR, STORE_DIRECTORY_NAME), STORE_FILE_NAME));
	}

	// Package-private: lets tests point the store at a temp directory instead of ~/.runelite.
	TrackedKillCountManager(Client client, ConfigManager configManager, Gson gson, File storeFile)
	{
		this.client = client;
		this.configManager = configManager;
		this.gson = gson;
		this.storeFile = storeFile;
	}

	public void startUp()
	{
		executor = Executors.newSingleThreadExecutor();
		executor.submit(this::loadFromDisk);
	}

	public void shutDown()
	{
		if (executor != null)
		{
			executor.shutdownNow();
		}
	}

	// Test-only: blocks until every task queued on the executor so far (the initial load, and any
	// persistToDisk() from a kill already processed) has finished, without sleeping/polling.
	void awaitPendingWork() throws Exception
	{
		executor.submit(() -> null).get();
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived npcLootReceived)
	{
		NPC npc = npcLootReceived.getNpc();
		String name = npc.getName();
		if (name == null || !TRACKED_MONSTERS.contains(name))
		{
			return;
		}

		Map<String, Integer> counts = killCounts.get();
		if (counts == null)
		{
			// Store hasn't finished loading from disk yet - drop this kill rather than risk
			// clobbering it with a fresh empty map once the load completes. Rare (only possible
			// in the first moment after startUp()) and already an accepted imprecision.
			return;
		}

		Integer existing = counts.get(name);
		int newCount = (existing != null ? existing : seedFromLootTracker(name)) + 1;
		counts.put(name, newCount);

		lastMonster = name;
		lastKillCount = newCount;
		lastTick = client.getTickCount();

		Map<String, Integer> snapshot = new TreeMap<>(counts);
		executor.submit(() -> persistToDisk(snapshot));
	}

	/**
	 * @return the monster name and kill count from the most recently tracked kill, if one landed
	 *         within the last {@link #MAX_TICK_AGE} ticks - null otherwise. Mirrors
	 *         {@link KillCountTracker#recentKill()}'s correlation window.
	 */
	public KillCountTracker.RecentKill recentKill()
	{
		if (lastTick == -1 || client.getTickCount() - lastTick > MAX_TICK_AGE)
		{
			return null;
		}
		return new KillCountTracker.RecentKill(lastMonster, lastKillCount);
	}

	private int seedFromLootTracker(String name)
	{
		try
		{
			String profile = configManager.getRSProfileKey();
			if (profile == null)
			{
				return 0;
			}

			String json = configManager.getConfiguration(LOOT_TRACKER_CONFIG_GROUP, profile, "drops_NPC_" + name);
			if (json == null)
			{
				return 0;
			}

			JsonObject obj = gson.fromJson(json, JsonObject.class);
			if (obj != null && obj.has("kills"))
			{
				return Math.max(0, obj.get("kills").getAsInt());
			}
		}
		catch (JsonSyntaxException | ClassCastException | IllegalStateException | NumberFormatException e)
		{
			log.debug("Couldn't read Loot Tracker's kill count for {}, starting from 0", name, e);
		}
		return 0;
	}

	private void loadFromDisk()
	{
		Map<String, Integer> loaded = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		if (storeFile.exists())
		{
			try (Reader reader = new FileReader(storeFile))
			{
				Map<String, Integer> raw = gson.fromJson(reader, new TypeToken<Map<String, Integer>>()
				{
				}.getType());
				if (raw != null)
				{
					loaded.putAll(raw);
				}
			}
			catch (IOException | JsonSyntaxException e)
			{
				log.warn("Failed to load tracked kill counts, starting fresh", e);
			}
		}
		killCounts.set(loaded);
	}

	private void persistToDisk(Map<String, Integer> counts)
	{
		File directory = storeFile.getParentFile();
		if (!directory.exists() && !directory.mkdirs())
		{
			log.warn("Failed to create {}", directory);
			return;
		}

		try (Writer writer = new FileWriter(storeFile))
		{
			gson.toJson(counts, writer);
		}
		catch (IOException e)
		{
			log.warn("Failed to persist tracked kill counts", e);
		}
	}
}
