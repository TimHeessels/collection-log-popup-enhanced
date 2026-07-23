package com.snakesteak.collectionlogpopupenhanced.killcount;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KillCountTrackerTest
{
	private Client client;
	private KillCountTracker tracker;

	@Before
	public void before()
	{
		client = mock(Client.class);
		when(client.getTickCount()).thenReturn(0);
		tracker = new KillCountTracker(client);
	}

	private void fireMessage(String message)
	{
		ChatMessage chatMessage = new ChatMessage();
		chatMessage.setType(ChatMessageType.GAMEMESSAGE);
		chatMessage.setMessage(message);
		tracker.onChatMessage(chatMessage);
	}

	@Test
	public void parsesBossKillCount()
	{
		fireMessage("Your Zulrah kill count is: <col=ff0000>41</col>.");

		KillCountTracker.RecentKill kill = tracker.recentKill();
		assertEquals("Zulrah", kill.getSource());
		assertEquals(41, kill.getKillCount());
	}

	@Test
	public void parsesBarrowsChestCount()
	{
		fireMessage("Your Barrows chest count is: <col=ff0000>128</col>.");

		KillCountTracker.RecentKill kill = tracker.recentKill();
		assertEquals("Barrows chest", kill.getSource());
		assertEquals(128, kill.getKillCount());
	}

	@Test
	public void parsesLunarChestCount()
	{
		fireMessage("Your Lunar Chest count is: <col=ff0000>320</col>.");

		KillCountTracker.RecentKill kill = tracker.recentKill();
		assertEquals("Lunar Chest", kill.getSource());
		assertEquals(320, kill.getKillCount());
	}

	@Test
	public void ignoresUnrelatedMessages()
	{
		fireMessage("You have completed 1 out of 8 medium clue scrolls.");

		assertNull(tracker.recentKill());
	}
}
