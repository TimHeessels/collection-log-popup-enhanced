package com.snakesteak.collectionlogpopupenhanced.killcount;

import com.google.gson.Gson;
import java.io.File;
import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.NpcLootReceived;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TrackedKillCountManagerTest
{
	@Rule
	public final TemporaryFolder tempFolder = new TemporaryFolder();

	private Client client;
	private ConfigManager configManager;
	private File storeFile;
	private TrackedKillCountManager manager;

	@Before
	public void before()
	{
		client = mock(Client.class);
		when(client.getTickCount()).thenReturn(0);
		configManager = mock(ConfigManager.class);
		storeFile = new File(tempFolder.getRoot(), "kill-counts.json");
	}

	@After
	public void after()
	{
		if (manager != null)
		{
			manager.shutDown();
		}
	}

	private TrackedKillCountManager newManager() throws Exception
	{
		TrackedKillCountManager m = new TrackedKillCountManager(client, configManager, new Gson(), storeFile);
		m.startUp();
		m.awaitPendingWork();
		return m;
	}

	private void fireKill(TrackedKillCountManager m, String npcName) throws Exception
	{
		NPC npc = mock(NPC.class);
		when(npc.getName()).thenReturn(npcName);
		m.onNpcLootReceived(new NpcLootReceived(npc, Collections.emptyList()));
		m.awaitPendingWork();
	}

	@Test
	public void untrackedMonsterIsIgnored() throws Exception
	{
		manager = newManager();
		fireKill(manager, "Goblin");
		assertNull(manager.recentKill());
	}

	@Test
	public void trackedMonsterListIncludesCockatrice()
	{
		assertEquals(true, TrackedKillCountManager.TRACKED_MONSTERS.contains("Cockatrice"));
	}

	@Test
	public void firstKillOfUnseenMonsterWithNoLootTrackerDataStartsAtOne() throws Exception
	{
		manager = newManager();
		fireKill(manager, "Cockatrice");

		KillCountTracker.RecentKill kill = manager.recentKill();
		assertEquals("Cockatrice", kill.getSource());
		assertEquals(1, kill.getKillCount());
	}

	@Test
	public void recentKillExpiresAfterTickWindow() throws Exception
	{
		manager = newManager();
		fireKill(manager, "Cockatrice");
		assertEquals(1, manager.recentKill().getKillCount());

		when(client.getTickCount()).thenReturn(10);
		assertNull(manager.recentKill());
	}

	@Test
	public void persistsAcrossRestarts() throws Exception
	{
		manager = newManager();
		fireKill(manager, "Cockatrice");
		manager.shutDown();

		manager = newManager();
		fireKill(manager, "Cockatrice");

		assertEquals(2, manager.recentKill().getKillCount());
	}

	@Test
	public void seedsFromLootTrackerOnFirstSighting() throws Exception
	{
		when(configManager.getRSProfileKey()).thenReturn("profile1");
		when(configManager.getConfiguration("loottracker", "profile1", "drops_NPC_Cockatrice"))
			.thenReturn("{\"kills\":41,\"drops\":[]}");

		manager = newManager();
		fireKill(manager, "Cockatrice");

		assertEquals(42, manager.recentKill().getKillCount());
	}

	@Test
	public void malformedLootTrackerDataFallsBackToZeroInsteadOfCrashing() throws Exception
	{
		when(configManager.getRSProfileKey()).thenReturn("profile1");
		when(configManager.getConfiguration("loottracker", "profile1", "drops_NPC_Cockatrice"))
			.thenReturn("not valid json{{{");

		manager = newManager();
		fireKill(manager, "Cockatrice");

		assertEquals(1, manager.recentKill().getKillCount());
	}

	@Test
	public void missingKillsFieldFallsBackToZero() throws Exception
	{
		when(configManager.getRSProfileKey()).thenReturn("profile1");
		when(configManager.getConfiguration("loottracker", "profile1", "drops_NPC_Cockatrice"))
			.thenReturn("{\"drops\":[]}");

		manager = newManager();
		fireKill(manager, "Cockatrice");

		assertEquals(1, manager.recentKill().getKillCount());
	}
}
